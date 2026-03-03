package com.ireader.feature.reader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.feature.reader.domain.LocatorCodec
import com.ireader.feature.reader.domain.ReaderBookInfo
import com.ireader.feature.reader.domain.ReaderBookRepository
import com.ireader.feature.reader.domain.ReaderProgressRepository
import com.ireader.feature.reader.domain.ReaderSettingsRepository
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.web.ReaderWebViewLinkRouter
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.Locator
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
@OptIn(FlowPreview::class)
class ReaderViewModel @Inject constructor(
    private val bookRepository: ReaderBookRepository,
    private val progressRepository: ReaderProgressRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val locatorCodec: LocatorCodec,
    private val openReaderSession: OpenReaderSession,
    private val observeEffectiveConfig: ObserveEffectiveConfig,
    private val saveReadingProgress: SaveReadingProgress,
    private val errorMapper: ReaderUiErrorMapper
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val intents = Channel<ReaderIntent>(capacity = Channel.UNLIMITED)
    private val navigationMutex = Mutex()

    private var handle: ReaderSessionHandle? = null
    private var currentBook: ReaderBookInfo? = null
    private var currentStartArgs: StartArgs? = null
    private var layoutConstraints: LayoutConstraints? = null

    private var progressJob: Job? = null
    private var stateJob: Job? = null
    private var eventJob: Job? = null
    private var settingsJob: Job? = null
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
        val controller = handle?.controller ?: return false
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
                _state.update { it.copy(passwordPrompt = null) }
                _effects.tryEmit(ReaderEffect.Back)
            }

            is ReaderIntent.LayoutChanged -> applyLayout(intent.constraints)
            ReaderIntent.RefreshPage -> renderCurrentPage()
            ReaderIntent.ToggleChrome -> _state.update { it.copy(chromeVisible = !it.chromeVisible) }

            ReaderIntent.OpenAnnotations -> {
                val bookId = _state.value.bookId
                if (bookId.isNotBlank()) {
                    _effects.tryEmit(ReaderEffect.OpenAnnotations(bookId))
                }
            }

            ReaderIntent.OpenToc -> {
                _state.update { it.copy(sheet = ReaderSheet.Toc) }
                loadTocIfNeeded()
            }

            ReaderIntent.OpenSearch -> _state.update { it.copy(sheet = ReaderSheet.Search) }
            ReaderIntent.OpenSettings -> _state.update { it.copy(sheet = ReaderSheet.Settings) }
            ReaderIntent.CloseSheet -> _state.update { it.copy(sheet = ReaderSheet.None) }

            ReaderIntent.Next -> navigate { controller, policy -> controller.next(policy) }
            ReaderIntent.Prev -> navigate { controller, policy -> controller.prev(policy) }
            is ReaderIntent.GoTo -> navigate { controller, policy -> controller.goTo(intent.locator, policy) }
            is ReaderIntent.GoToProgress -> navigate { controller, policy ->
                controller.goToProgress(intent.percent, policy)
            }

            is ReaderIntent.SearchQueryChanged -> {
                _state.update { current ->
                    current.copy(search = current.search.copy(query = intent.query))
                }
            }

            ReaderIntent.ExecuteSearch -> executeSearch()

            is ReaderIntent.UpdateConfig -> {
                applyConfig(config = intent.config, persist = intent.persist)
            }
        }
    }

    private suspend fun open(args: StartArgs, password: String?) {
        closeSession()
        searchJob?.cancel()
        searchJob = null

        _state.update {
            it.copy(
                bookId = args.bookId,
                isOpening = true,
                title = null,
                page = null,
                controller = null,
                capabilities = null,
                renderState = null,
                currentConfig = null,
                passwordPrompt = null,
                error = null,
                toc = TocState(),
                search = SearchState()
            )
        }

        val book = bookRepository.resolveBook(args.bookId)
        if (book == null) {
            _state.update {
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
        currentBook = book

        val routeLocator = args.locatorArg?.let(locatorCodec::decode)
        val historyLocator = runCatching { progressRepository.getLastLocator(book.bookId) }.getOrNull()
        val initialLocator = routeLocator ?: historyLocator

        when (val result = openReaderSession(book, initialLocator, password)) {
            is ReaderResult.Err -> {
                handleOpenError(result.error, password)
            }

            is ReaderResult.Ok -> {
                bookRepository.markOpened(book.bookId)
                handle = result.value

                _state.update {
                    it.copy(
                        isOpening = false,
                        title = book.title,
                        controller = result.value.controller,
                        capabilities = result.value.document.capabilities,
                        currentConfig = result.value.controller.state.value.config,
                        passwordPrompt = null,
                        error = null
                    )
                }

                startSessionCollectors(result.value, book.bookId)
                val constraints = layoutConstraints
                if (constraints != null) {
                    applyLayout(constraints)
                } else {
                    renderCurrentPage()
                }
            }
        }
    }

    private suspend fun handleOpenError(error: ReaderError, lastTriedPassword: String?) {
        if (error is ReaderError.InvalidPassword) {
            _state.update {
                it.copy(
                    isOpening = false,
                    passwordPrompt = PasswordPrompt(lastTried = lastTriedPassword),
                    error = null
                )
            }
            return
        }

        _state.update {
            it.copy(
                isOpening = false,
                passwordPrompt = null,
                error = errorMapper.map(error)
            )
        }
    }

    private fun startSessionCollectors(sessionHandle: ReaderSessionHandle, bookId: Long) {
        cancelSessionCollectors()

        stateJob = viewModelScope.launch {
            sessionHandle.controller.state.collect { renderState ->
                _state.update {
                    it.copy(
                        renderState = renderState,
                        currentConfig = renderState.config,
                        title = renderState.titleInView ?: it.title
                    )
                }
            }
        }

        progressJob = viewModelScope.launch {
            sessionHandle.controller.state
                .map { state -> state.locator to state.progression.percent }
                .distinctUntilChanged()
                .debounce(800L)
                .collect { (locator, progression) ->
                    runCatching { saveReadingProgress(bookId, locator, progression) }
                }
        }

        eventJob = viewModelScope.launch {
            sessionHandle.controller.events
                .filterIsInstance<ReaderEvent.Error>()
                .collect {
                    _effects.tryEmit(ReaderEffect.Snackbar(UiText.Dynamic("Render error")))
                }
        }

        settingsJob = viewModelScope.launch {
            observeEffectiveConfig(sessionHandle.document.capabilities)
                .distinctUntilChanged()
                .collect { config ->
                    when (sessionHandle.controller.setConfig(config)) {
                        is ReaderResult.Ok -> {
                            _state.update { it.copy(currentConfig = config) }
                            renderCurrentPage()
                        }

                        is ReaderResult.Err -> {
                            _effects.tryEmit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to apply settings")))
                        }
                    }
                }
        }
    }

    private suspend fun applyLayout(constraints: LayoutConstraints) {
        layoutConstraints = constraints
        val sessionHandle = handle ?: return
        when (val result = sessionHandle.controller.setLayoutConstraints(constraints)) {
            is ReaderResult.Ok -> renderCurrentPage()
            is ReaderResult.Err -> _state.update { it.copy(error = errorMapper.map(result.error)) }
        }
    }

    private suspend fun applyConfig(config: RenderConfig, persist: Boolean) {
        val sessionHandle = handle ?: return
        when (val result = sessionHandle.controller.setConfig(config)) {
            is ReaderResult.Err -> _state.update { it.copy(error = errorMapper.map(result.error)) }
            is ReaderResult.Ok -> {
                if (persist) {
                    runCatching {
                        when (config) {
                            is RenderConfig.FixedPage -> settingsRepository.updateFixedConfig(config)
                            is RenderConfig.ReflowText -> settingsRepository.updateReflowConfig(config)
                        }
                    }.onFailure {
                        _effects.tryEmit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to save settings")))
                    }
                }
                _state.update { it.copy(currentConfig = config) }
                renderCurrentPage()
            }
        }
    }

    private suspend fun renderCurrentPage() {
        val sessionHandle = handle ?: return
        if (layoutConstraints == null) return
        val controller = sessionHandle.controller
        val twoPass = sessionHandle.document.capabilities.fixedLayout

        if (!twoPass) {
            when (val result = controller.render()) {
                is ReaderResult.Ok -> _state.update { it.copy(page = result.value, error = null) }
                is ReaderResult.Err -> _state.update { it.copy(error = errorMapper.map(result.error)) }
            }
            return
        }

        when (val draft = controller.render(RenderPolicy(quality = RenderPolicy.Quality.DRAFT))) {
            is ReaderResult.Ok -> _state.update { it.copy(page = draft.value, error = null) }
            is ReaderResult.Err -> {
                _state.update { it.copy(error = errorMapper.map(draft.error)) }
                return
            }
        }

        _state.update { it.copy(isRenderingFinal = true) }
        when (val final = controller.render(RenderPolicy(quality = RenderPolicy.Quality.FINAL))) {
            is ReaderResult.Ok -> _state.update {
                it.copy(
                    page = final.value,
                    error = null,
                    isRenderingFinal = false
                )
            }

            is ReaderResult.Err -> _state.update {
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
        val sessionHandle = handle ?: return
        navigationMutex.withLock {
            val fixed = sessionHandle.document.capabilities.fixedLayout
            val actionPolicy = if (fixed) {
                RenderPolicy(quality = RenderPolicy.Quality.DRAFT)
            } else {
                RenderPolicy.Default
            }

            when (val result = block(sessionHandle.controller, actionPolicy)) {
                is ReaderResult.Err -> _state.update { it.copy(error = errorMapper.map(result.error)) }
                is ReaderResult.Ok -> {
                    _state.update { it.copy(page = result.value, error = null) }
                    if (fixed) {
                        _state.update { it.copy(isRenderingFinal = true) }
                        when (val final = sessionHandle.controller.render(RenderPolicy(quality = RenderPolicy.Quality.FINAL))) {
                            is ReaderResult.Ok -> _state.update {
                                it.copy(
                                    page = final.value,
                                    error = null,
                                    isRenderingFinal = false
                                )
                            }

                            is ReaderResult.Err -> _state.update {
                                it.copy(
                                    error = errorMapper.map(final.error),
                                    isRenderingFinal = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadTocIfNeeded() {
        val sessionHandle = handle ?: return
        val outline = sessionHandle.outline
        if (outline == null) {
            _state.update {
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
        if (_state.value.toc.items.isNotEmpty()) return

        _state.update { it.copy(toc = it.toc.copy(isLoading = true, error = null)) }
        when (val result = outline.getOutline()) {
            is ReaderResult.Err -> {
                _state.update {
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
                val flat = mutableListOf<TocItem>()
                flattenOutline(result.value, depth = 0, out = flat)
                _state.update { it.copy(toc = TocState(isLoading = false, items = flat)) }
            }
        }
    }

    private suspend fun executeSearch() {
        val sessionHandle = handle ?: return
        val query = _state.value.search.query.trim()
        if (query.isBlank()) {
            _state.update { it.copy(search = it.search.copy(results = emptyList(), error = null)) }
            return
        }

        val provider = sessionHandle.search
        if (provider == null) {
            _state.update {
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
            _state.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = true,
                        results = emptyList(),
                        error = null
                    )
                )
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
                                _state.update {
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
                                val encoded = locatorCodec.encode(hit.range.start)
                                _state.update { current ->
                                    current.copy(
                                        search = current.search.copy(
                                            results = current.search.results + SearchResultItem(
                                                title = hit.sectionTitle,
                                                excerpt = hit.excerpt,
                                                locatorEncoded = encoded
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                _state.update {
                    it.copy(
                        search = it.search.copy(isSearching = false)
                    )
                }
            }
        }
    }

    private fun flattenOutline(nodes: List<OutlineNode>, depth: Int, out: MutableList<TocItem>) {
        nodes.forEach { node ->
            out += TocItem(
                title = node.title.ifBlank { "(untitled)" },
                locatorEncoded = locatorCodec.encode(node.locator),
                depth = depth
            )
            if (node.children.isNotEmpty()) {
                flattenOutline(node.children, depth + 1, out)
            }
        }
    }

    private fun cancelSessionCollectors() {
        progressJob?.cancel()
        progressJob = null
        stateJob?.cancel()
        stateJob = null
        eventJob?.cancel()
        eventJob = null
        settingsJob?.cancel()
        settingsJob = null
    }

    private suspend fun closeSession() {
        searchJob?.cancel()
        searchJob = null
        cancelSessionCollectors()

        val sessionHandle = handle
        val book = currentBook
        if (sessionHandle != null && book != null) {
            runCatching {
                val renderState = sessionHandle.controller.state.value
                saveReadingProgress(
                    bookId = book.bookId,
                    locator = renderState.locator,
                    progression = renderState.progression.percent
                )
            }
        }

        runCatching { sessionHandle?.close() }
        handle = null
    }

    override fun onCleared() {
        runBlocking {
            closeSession()
        }
        super.onCleared()
    }

    private data class StartArgs(
        val bookId: String,
        val locatorArg: String?
    )
}
