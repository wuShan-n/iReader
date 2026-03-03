package com.ireader.feature.reader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.book.IndexState
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.BookSourceResolver
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.web.ReaderWebViewLinkRouter
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.runtime.ReaderSessionHandle
import com.ireader.reader.runtime.flow.asReaderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
@OptIn(FlowPreview::class)
class ReaderViewModel @Inject constructor(
    private val bookRepo: BookRepo,
    private val progressRepo: ProgressRepo,
    private val settingsStore: ReaderSettingsStore,
    private val sourceResolver: BookSourceResolver,
    private val locatorCodec: LocatorCodec,
    private val openReaderSession: OpenReaderSession,
    private val observeEffectiveConfig: ObserveEffectiveConfig,
    private val saveReadingProgress: SaveReadingProgress,
    private val errorMapper: ReaderUiErrorMapper
) : ViewModel() {

    private val stateStore = MutableStateFlow(ReaderUiState())
    val state = stateStore.asStateFlow()

    private val effectStore = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 16)
    val effects = effectStore.asSharedFlow()

    private val ui = ReaderUiReducer(stateStore = stateStore, effectStore = effectStore)
    private val session = SessionCoordinator()
    private val render = RenderCoordinator(scope = viewModelScope) {
        renderCurrentPageImmediate()
    }

    private val intents = Channel<ReaderIntent>(capacity = Channel.UNLIMITED)
    private var currentStartArgs: StartArgs? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            for (intent in intents) {
                handleIntent(intent)
            }
        }
    }

    fun dispatch(intent: ReaderIntent) {
        intents.trySend(intent)
    }

    fun onWebSchemeUrl(url: String): Boolean {
        val controller = session.currentHandle()?.controller ?: return false
        val handled = ReaderWebViewLinkRouter.tryHandle(
            url = url,
            controller = controller,
            scope = viewModelScope
        )
        if (handled) {
            viewModelScope.launch {
                delay(40L)
                dispatch(ReaderIntent.RefreshPage)
            }
        }
        return handled
    }

    fun openLocator(encoded: String) {
        val locator = locatorCodec.decode(encoded) ?: return
        dispatch(ReaderIntent.GoTo(locator))
    }

    private suspend fun handleIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.Start -> {
                val args = StartArgs(intent.bookId, intent.locatorArg)
                currentStartArgs = args
                open(args, password = null)
            }

            ReaderIntent.RetryOpen -> {
                val args = currentStartArgs ?: return
                open(args, password = null)
            }

            is ReaderIntent.SubmitPassword -> {
                val args = currentStartArgs ?: return
                open(args, password = intent.password)
            }

            ReaderIntent.CancelPassword -> {
                ui.update { it.copy(passwordPrompt = null) }
                ui.emit(ReaderEffect.Back)
            }

            is ReaderIntent.LayoutChanged -> applyLayout(intent.constraints)
            ReaderIntent.RefreshPage -> render.requestRender(RenderRequest.REFRESH)
            ReaderIntent.ToggleChrome -> ui.update { it.copy(chromeVisible = !it.chromeVisible) }

            ReaderIntent.OpenAnnotations -> {
                val bookId = ui.state.value.bookId
                if (bookId > 0L) {
                    ui.emit(ReaderEffect.OpenAnnotations(bookId))
                }
            }

            ReaderIntent.OpenToc -> {
                ui.update { it.copy(sheet = ReaderSheet.Toc) }
                loadTocIfNeeded()
            }

            ReaderIntent.OpenSearch -> ui.update { it.copy(sheet = ReaderSheet.Search) }
            ReaderIntent.OpenSettings -> ui.update { it.copy(sheet = ReaderSheet.Settings) }
            ReaderIntent.CloseSheet -> ui.update { it.copy(sheet = ReaderSheet.None) }

            ReaderIntent.Next -> navigate { controller, policy -> controller.next(policy) }
            ReaderIntent.Prev -> navigate { controller, policy -> controller.prev(policy) }
            is ReaderIntent.GoTo -> navigate { controller, policy -> controller.goTo(intent.locator, policy) }
            is ReaderIntent.GoToProgress -> navigate { controller, policy ->
                controller.goToProgress(intent.percent, policy)
            }
            is ReaderIntent.ActivateLink -> handleLink(intent.link.target)

            is ReaderIntent.SearchQueryChanged -> {
                ui.update { current ->
                    current.copy(search = current.search.copy(query = intent.query))
                }
            }

            ReaderIntent.ExecuteSearch -> executeSearch()

            is ReaderIntent.UpdateConfig -> applyConfig(
                config = intent.config,
                persist = intent.persist
            )
        }
    }

    private suspend fun open(args: StartArgs, password: String?) {
        closeSession()
        searchJob?.cancel()
        searchJob = null

        ui.update {
            it.copy(
                bookId = args.bookId,
                isOpening = true,
                title = null,
                page = null,
                controller = null,
                resources = null,
                capabilities = null,
                renderState = null,
                currentConfig = null,
                passwordPrompt = null,
                error = null,
                toc = TocState(),
                search = SearchState()
            )
        }

        val book = bookRepo.getById(args.bookId)
        if (book == null) {
            ui.update {
                it.copy(
                    isOpening = false,
                    error = ReaderUiError(
                        message = UiText.Dynamic("Book not found"),
                        actionLabel = UiText.Dynamic("Back"),
                        action = ReaderErrorAction.Back
                    )
                )
            }
            return
        }

        val source = sourceResolver.resolve(book)
        if (source == null) {
            bookRepo.setIndexState(book.bookId, IndexState.MISSING, "File not found")
            ui.update {
                it.copy(
                    isOpening = false,
                    error = ReaderUiError(
                        message = UiText.Dynamic("The book file is missing"),
                        actionLabel = UiText.Dynamic("Back"),
                        action = ReaderErrorAction.Back
                    )
                )
            }
            return
        }

        val routeLocator = args.locatorArg?.let(locatorCodec::decode)
        val historyLocator = runCatching {
            progressRepo.getByBookId(book.bookId)?.locatorJson?.let(locatorCodec::decode)
        }.getOrNull()
        val initialLocator = routeLocator ?: historyLocator

        when (
            val result = openReaderSession(
                source = source,
                options = OpenOptions(
                    hintFormat = book.format,
                    password = password
                ),
                initialLocator = initialLocator
            )
        ) {
            is ReaderResult.Err -> {
                handleOpenError(result.error, password)
            }

            is ReaderResult.Ok -> {
                session.attach(bookId = book.bookId, handle = result.value)
                bookRepo.touchLastOpened(book.bookId)

                ui.update {
                    it.copy(
                        isOpening = false,
                        title = book.title?.takeIf(String::isNotBlank) ?: book.fileName,
                        controller = result.value.controller,
                        resources = result.value.resources,
                        capabilities = result.value.document.capabilities,
                        currentConfig = result.value.controller.state.value.config,
                        passwordPrompt = null,
                        error = null
                    )
                }

                startSessionCollectors(result.value, book.bookId)
                val constraints = render.currentLayout()
                if (constraints != null) {
                    applyLayout(constraints)
                } else {
                    render.requestRender(RenderRequest.OPEN)
                }
            }
        }
    }

    private suspend fun handleOpenError(error: ReaderError, lastTriedPassword: String?) {
        if (error is ReaderError.InvalidPassword) {
            ui.update {
                it.copy(
                    isOpening = false,
                    passwordPrompt = PasswordPrompt(lastTried = lastTriedPassword),
                    error = null
                )
            }
            return
        }

        ui.update {
            it.copy(
                isOpening = false,
                passwordPrompt = null,
                error = errorMapper.map(error)
            )
        }
    }

    private fun startSessionCollectors(sessionHandle: ReaderSessionHandle, bookId: Long) {
        val stateJob = viewModelScope.launch {
            sessionHandle.controller.state.collect { renderState ->
                ui.update {
                    it.copy(
                        renderState = renderState,
                        currentConfig = renderState.config,
                        title = renderState.titleInView ?: it.title
                    )
                }
            }
        }

        val progressJob = viewModelScope.launch {
            sessionHandle.controller.state
                .map { renderState -> renderState.locator to renderState.progression.percent }
                .distinctUntilChanged()
                .debounce(800L)
                .collect { (locator, progression) ->
                    runCatching { saveReadingProgress(bookId, locator, progression) }
                }
        }

        val eventJob = viewModelScope.launch {
            sessionHandle.controller.events
                .filterIsInstance<ReaderEvent.Error>()
                .collect {
                    ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Render error")))
                }
        }

        val settingsJob = viewModelScope.launch {
            observeEffectiveConfig(sessionHandle.document.capabilities)
                .distinctUntilChanged()
                .collect { config ->
                    when (sessionHandle.controller.setConfig(config)) {
                        is ReaderResult.Ok -> {
                            ui.update { it.copy(currentConfig = config) }
                            render.requestRender(RenderRequest.SETTINGS)
                        }

                        is ReaderResult.Err -> {
                            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to apply settings")))
                        }
                    }
                }
        }

        session.bindCollectors(
            progressJob = progressJob,
            stateJob = stateJob,
            eventJob = eventJob,
            settingsJob = settingsJob
        )
    }

    private suspend fun applyLayout(constraints: LayoutConstraints) {
        render.updateLayout(constraints)
        val sessionHandle = session.currentHandle() ?: return
        when (val result = sessionHandle.controller.setLayoutConstraints(constraints)) {
            is ReaderResult.Ok -> render.requestRender(RenderRequest.LAYOUT)
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
        }
    }

    private suspend fun applyConfig(config: RenderConfig, persist: Boolean) {
        if (persist) {
            runCatching {
                when (config) {
                    is RenderConfig.FixedPage -> settingsStore.setFixedConfig(config)
                    is RenderConfig.ReflowText -> settingsStore.setReflowConfig(config)
                }
            }.onFailure {
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to save settings")))
            }
        }

        val sessionHandle = session.currentHandle() ?: run {
            ui.update { it.copy(currentConfig = config) }
            return
        }

        when (val result = sessionHandle.controller.setConfig(config)) {
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
            is ReaderResult.Ok -> {
                ui.update { it.copy(currentConfig = config) }
                render.requestRender(RenderRequest.CONFIG)
            }
        }
    }

    private suspend fun renderCurrentPageImmediate() {
        val sessionHandle = session.currentHandle() ?: return
        if (render.currentLayout() == null) return
        val controller = sessionHandle.controller
        val twoPass = sessionHandle.document.capabilities.fixedLayout

        if (!twoPass) {
            when (val result = controller.render()) {
                is ReaderResult.Ok -> ui.update { it.copy(page = result.value, error = null) }
                is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
            }
            return
        }

        when (val draft = controller.render(RenderPolicy(quality = RenderPolicy.Quality.DRAFT))) {
            is ReaderResult.Ok -> ui.update { it.copy(page = draft.value, error = null) }
            is ReaderResult.Err -> {
                ui.update { it.copy(error = errorMapper.map(draft.error)) }
                return
            }
        }

        ui.update { it.copy(isRenderingFinal = true) }
        when (val final = controller.render(RenderPolicy(quality = RenderPolicy.Quality.FINAL))) {
            is ReaderResult.Ok -> ui.update {
                it.copy(
                    page = final.value,
                    error = null,
                    isRenderingFinal = false
                )
            }

            is ReaderResult.Err -> ui.update {
                it.copy(
                    error = errorMapper.map(final.error),
                    isRenderingFinal = false
                )
            }
        }
    }

    private suspend fun navigate(
        block: suspend (ReaderController, RenderPolicy) -> ReaderResult<RenderPage>
    ) {
        val sessionHandle = session.currentHandle() ?: return
        render.withNavigationLock {
            val fixed = sessionHandle.document.capabilities.fixedLayout
            val actionPolicy = if (fixed) {
                RenderPolicy(quality = RenderPolicy.Quality.DRAFT)
            } else {
                RenderPolicy.Default
            }

            when (val result = block(sessionHandle.controller, actionPolicy)) {
                is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
                is ReaderResult.Ok -> {
                    ui.update { it.copy(page = result.value, error = null) }
                    if (fixed) {
                        ui.update { it.copy(isRenderingFinal = true) }
                        when (
                            val final = sessionHandle.controller.render(
                                RenderPolicy(quality = RenderPolicy.Quality.FINAL)
                            )
                        ) {
                            is ReaderResult.Ok -> ui.update {
                                it.copy(
                                    page = final.value,
                                    error = null,
                                    isRenderingFinal = false
                                )
                            }

                            is ReaderResult.Err -> ui.update {
                                it.copy(
                                    error = errorMapper.map(final.error),
                                    isRenderingFinal = false
                                )
                            }
                        }
                    }
                }
            }

            runCatching { sessionHandle.controller.prefetchNeighbors(count = 1) }
        }
    }

    private suspend fun handleLink(target: LinkTarget) {
        when (target) {
            is LinkTarget.Internal -> {
                navigate { controller, policy ->
                    controller.goTo(target.locator, policy)
                }
            }

            is LinkTarget.External -> {
                ui.emit(ReaderEffect.OpenExternalUrl(target.url))
            }
        }
    }

    private suspend fun loadTocIfNeeded() {
        val sessionHandle = session.currentHandle() ?: return
        val outline = sessionHandle.outline
        if (outline == null) {
            ui.update {
                it.copy(
                    toc = TocState(
                        isLoading = false,
                        items = emptyList(),
                        error = UiText.Dynamic("Outline is not available")
                    )
                )
            }
            return
        }
        if (ui.state.value.toc.items.isNotEmpty()) return

        ui.update { it.copy(toc = it.toc.copy(isLoading = true, error = null)) }
        when (val result = outline.getOutline()) {
            is ReaderResult.Err -> {
                ui.update {
                    it.copy(
                        toc = TocState(
                            isLoading = false,
                            items = emptyList(),
                            error = errorMapper.map(result.error).message
                        )
                    )
                }
            }

            is ReaderResult.Ok -> {
                val flat = flattenOutlineIterative(result.value)
                ui.update { it.copy(toc = TocState(isLoading = false, items = flat)) }
            }
        }
    }

    private suspend fun executeSearch() {
        val sessionHandle = session.currentHandle() ?: return
        val query = ui.state.value.search.query.trim()
        if (query.isBlank()) {
            ui.update { it.copy(search = it.search.copy(results = emptyList(), error = null)) }
            return
        }

        val provider = sessionHandle.search
        if (provider == null) {
            ui.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = false,
                        results = emptyList(),
                        error = UiText.Dynamic("Search is not available")
                    )
                )
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            ui.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = true,
                        results = emptyList(),
                        error = null
                    )
                )
            }

            val accumulator = SearchResultAccumulator { batch ->
                ui.update { current ->
                    current.copy(
                        search = current.search.copy(
                            results = current.search.results + batch
                        )
                    )
                }
            }

            try {
                provider.search(
                    query = query,
                    options = SearchOptions(maxHits = 300)
                )
                    .asReaderResult()
                    .collect { result ->
                        when (result) {
                            is ReaderResult.Err -> {
                                ui.update {
                                    it.copy(
                                        search = it.search.copy(
                                            isSearching = false,
                                            error = errorMapper.map(result.error).message
                                        )
                                    )
                                }
                            }

                            is ReaderResult.Ok -> {
                                val hit = result.value
                                accumulator.add(
                                    SearchResultItem(
                                        title = hit.sectionTitle,
                                        excerpt = hit.excerpt,
                                        locatorEncoded = locatorCodec.encode(hit.range.start)
                                    )
                                )
                            }
                        }
                    }
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                accumulator.flush()
                ui.update {
                    it.copy(
                        search = it.search.copy(isSearching = false)
                    )
                }
            }
        }
    }

    private fun flattenOutlineIterative(nodes: List<OutlineNode>): List<TocItem> {
        if (nodes.isEmpty()) return emptyList()
        val out = ArrayList<TocItem>(nodes.size)
        val stack = ArrayDeque<OutlineEntry>(nodes.size)
        for (index in nodes.lastIndex downTo 0) {
            stack.addLast(OutlineEntry(nodes[index], depth = 0))
        }

        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            out += TocItem(
                title = node.title.ifBlank { "(untitled)" },
                locatorEncoded = locatorCodec.encode(node.locator),
                depth = depth
            )

            val children = node.children
            for (index in children.lastIndex downTo 0) {
                stack.addLast(OutlineEntry(children[index], depth + 1))
            }
        }
        return out
    }

    private suspend fun closeSession() {
        searchJob?.cancel()
        searchJob = null
        session.closeCurrent { bookId, locator, progression ->
            saveReadingProgress(
                bookId = bookId,
                locator = locator,
                progression = progression
            )
        }
    }

    override fun onCleared() {
        render.cancel()
        runBlocking {
            closeSession()
        }
        super.onCleared()
    }

    private data class StartArgs(
        val bookId: Long,
        val locatorArg: String?
    )

    private data class OutlineEntry(
        val node: OutlineNode,
        val depth: Int
    )
}
