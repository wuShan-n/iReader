package com.ireader.feature.reader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.BookSourceResolver
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.web.ExternalLinkPolicy
import com.ireader.feature.reader.web.ReaderWebViewLinkRouter
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.runtime.ReaderSessionHandle
import com.ireader.reader.runtime.flow.asReaderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val gestureInterpreter = ReaderGestureInterpreter()
    private val interactionTracker: ReaderInteractionTracker = ReaderInteractionTracker.None

    private val intents = Channel<ReaderIntent>(capacity = Channel.UNLIMITED)
    private var currentStartArgs: StartArgs? = null
    private var searchJob: Job? = null
    private var pendingUndoTurn: PendingUndoTurn? = null
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            for (intent in intents) {
                handleIntent(intent)
            }
        }
        viewModelScope.launch {
            settingsStore.displayPrefs
                .distinctUntilChanged()
                .collect { prefs ->
                    ui.update { current ->
                        val chromeVisible = if (prefs.fullScreenMode) {
                            current.chromeVisible
                        } else {
                            true
                        }
                        current.copy(
                            displayPrefs = prefs,
                            isNightMode = prefs.nightMode,
                            chromeVisible = chromeVisible
                        )
                    }
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
            ReaderIntent.ToggleChrome,
            ReaderIntent.ToggleImmersiveChrome -> toggleImmersiveChrome()
            ReaderIntent.BackPressed,
            ReaderIntent.BackInSheetHierarchy -> handleBackPressed()
            is ReaderIntent.HandleTap -> handleTap(intent)
            is ReaderIntent.HandleDragEnd -> handleDragEnd(intent)

            ReaderIntent.OpenAnnotations -> {
                val bookId = ui.state.value.bookId
                if (bookId > 0L) {
                    ui.emit(ReaderEffect.OpenAnnotations(bookId))
                }
            }

            ReaderIntent.OpenToc,
            ReaderIntent.OpenMenu -> {
                val opened = toggleDockTab(ReaderDockTab.Menu)
                if (opened) loadTocIfNeeded()
            }
            is ReaderIntent.ToggleDockTab -> {
                val opened = toggleDockTab(intent.tab)
                if (opened && intent.tab == ReaderDockTab.Menu) {
                    loadTocIfNeeded()
                }
            }
            ReaderIntent.CloseDockPanel -> closeLayerToReading()
            is ReaderIntent.SetMenuTab -> {
                ui.update { it.copy(activeMenuTab = intent.tab) }
                if (intent.tab == ReaderMenuTab.Toc) {
                    loadTocIfNeeded()
                }
            }

            ReaderIntent.OpenSearch -> openSheet(ReaderSheet.Search)
            ReaderIntent.OpenBrightness -> openDock(ReaderDockTab.Brightness)
            ReaderIntent.OpenSettings -> openDock(ReaderDockTab.Settings)
            is ReaderIntent.OpenSettingsSub -> openSubSheet(intent.sheet)
            ReaderIntent.OpenReaderMore -> openSheet(ReaderSheet.ReaderMore)
            ReaderIntent.OpenFullSettings -> openFullSettings()
            ReaderIntent.ShareBook -> ui.emit(ReaderEffect.ShareText(buildShareText(ui.state.value)))
            ReaderIntent.ToggleNightMode -> updateDisplayPrefs { prefs ->
                prefs.copy(nightMode = !prefs.nightMode)
            }
            is ReaderIntent.UpdateBrightness -> updateDisplayPrefs { prefs ->
                prefs.copy(brightness = intent.value.coerceIn(0f, 1f))
            }
            is ReaderIntent.SetUseSystemBrightness -> updateDisplayPrefs { prefs ->
                prefs.copy(useSystemBrightness = intent.enabled)
            }
            is ReaderIntent.SetEyeProtection -> updateDisplayPrefs { prefs ->
                prefs.copy(eyeProtection = intent.enabled)
            }
            is ReaderIntent.SelectBackground -> updateDisplayPrefs { prefs ->
                prefs.copy(backgroundPreset = intent.preset)
            }
            is ReaderIntent.SetReadingProgressVisible -> updateDisplayPrefs { prefs ->
                prefs.copy(showReadingProgress = intent.visible)
            }
            is ReaderIntent.SetFullScreenMode -> updateDisplayPrefs { prefs ->
                prefs.copy(fullScreenMode = intent.enabled)
            }
            is ReaderIntent.SetVerticalPaging -> updateVerticalPaging(intent.enabled)
            ReaderIntent.CloseSheet -> closeLayerToReading()

            ReaderIntent.Next -> navigate(direction = PageTurnDirection.NEXT) { controller, policy ->
                pendingUndoTurn = null
                controller.next(policy)
            }
            ReaderIntent.Prev -> navigate(direction = PageTurnDirection.PREV) { controller, policy ->
                pendingUndoTurn = null
                controller.prev(policy)
            }
            is ReaderIntent.GoTo -> navigate { controller, policy ->
                pendingUndoTurn = null
                controller.goTo(intent.locator, policy)
            }
            is ReaderIntent.GoToProgress -> navigate { controller, policy ->
                pendingUndoTurn = null
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
        pendingUndoTurn = null

        ui.update {
            it.copy(
                bookId = args.bookId,
                isOpening = true,
                title = null,
                layerState = ReaderLayerState.Reading,
                chromeVisible = true,
                page = null,
                controller = null,
                resources = null,
                capabilities = null,
                renderState = null,
                currentConfig = null,
                passwordPrompt = null,
                error = null,
                activeMenuTab = ReaderMenuTab.Toc,
                toc = TocState(),
                search = SearchState()
            )
        }

        val book = bookRepo.getRecordById(args.bookId)
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
                        pageTurnMode = resolvePageTurnMode(result.value.controller.state.value.config),
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
                        pageTurnMode = resolvePageTurnMode(renderState.config),
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
                            ui.update {
                                it.copy(
                                    currentConfig = config,
                                    pageTurnMode = resolvePageTurnMode(config)
                                )
                            }
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
            ui.update {
                it.copy(
                    currentConfig = config,
                    pageTurnMode = resolvePageTurnMode(config)
                )
            }
            return
        }

        when (val result = sessionHandle.controller.setConfig(config)) {
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
            is ReaderResult.Ok -> {
                ui.update {
                    it.copy(
                        currentConfig = config,
                        pageTurnMode = resolvePageTurnMode(config)
                    )
                }
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
            applyRenderResult(controller.render())
            return
        }

        renderWithFinalPass(
            controller = controller,
            draft = controller.render(RenderPolicy(quality = RenderPolicy.Quality.DRAFT))
        )
    }

    private suspend fun navigate(
        direction: PageTurnDirection? = null,
        block: suspend (ReaderController, RenderPolicy) -> ReaderResult<RenderPage>
    ): Boolean {
        val sessionHandle = session.currentHandle() ?: return false
        var success = false
        render.withNavigationLock {
            val fixed = sessionHandle.document.capabilities.fixedLayout
            val actionPolicy = if (fixed) {
                RenderPolicy(quality = RenderPolicy.Quality.DRAFT)
            } else {
                RenderPolicy.Default
            }

            when (val result = block(sessionHandle.controller, actionPolicy)) {
                is ReaderResult.Err -> {
                    ui.update { it.copy(error = errorMapper.map(result.error)) }
                    success = false
                }
                is ReaderResult.Ok -> if (fixed) {
                    renderWithFinalPass(
                        controller = sessionHandle.controller,
                        draft = result,
                        direction = direction
                    )
                    success = true
                } else {
                    applyRenderResult(result, direction = direction)
                    success = true
                }
            }

        }
        return success
    }

    private fun applyRenderResult(
        result: ReaderResult<RenderPage>,
        direction: PageTurnDirection? = null
    ) {
        when (result) {
            is ReaderResult.Ok -> replacePage(result.value, direction)
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
        }
    }

    private suspend fun renderWithFinalPass(
        controller: ReaderController,
        draft: ReaderResult<RenderPage>,
        direction: PageTurnDirection? = null
    ) {
        when (draft) {
            is ReaderResult.Ok -> replacePage(draft.value, direction)
            is ReaderResult.Err -> {
                ui.update { it.copy(error = errorMapper.map(draft.error), isRenderingFinal = false) }
                return
            }
        }

        ui.update { it.copy(isRenderingFinal = true) }
        when (val final = controller.render(RenderPolicy(quality = RenderPolicy.Quality.FINAL))) {
            is ReaderResult.Ok -> {
                replacePage(final.value, direction = null)
                ui.update { it.copy(isRenderingFinal = false) }
            }

            is ReaderResult.Err -> ui.update {
                it.copy(
                    error = errorMapper.map(final.error),
                    isRenderingFinal = false
                )
            }
        }
    }

    private fun resolvePageTurnMode(config: RenderConfig?): PageTurnMode {
        val reflow = config as? RenderConfig.ReflowText ?: return PageTurnMode.COVER_HORIZONTAL
        return reflow.pageTurnMode()
    }

    private suspend fun updateDisplayPrefs(
        transform: (ReaderDisplayPrefs) -> ReaderDisplayPrefs
    ) {
        val current = ui.state.value.displayPrefs
        val updated = transform(current)
        if (updated == current) return

        runCatching {
            settingsStore.setDisplayPrefs(updated)
        }.onFailure {
            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to save display settings")))
            return
        }

        ui.update { state ->
            val chromeVisible = if (updated.fullScreenMode) state.chromeVisible else true
            state.copy(
                displayPrefs = updated,
                isNightMode = updated.nightMode,
                chromeVisible = chromeVisible
            )
        }
    }

    private suspend fun updateVerticalPaging(enabled: Boolean) {
        val mode = if (enabled) PageTurnMode.SCROLL_VERTICAL else PageTurnMode.COVER_HORIZONTAL
        val currentReflow = ui.state.value.currentConfig as? RenderConfig.ReflowText ?: return
        if (currentReflow.pageTurnMode() == mode) return
        applyConfig(
            config = currentReflow.withPageTurnMode(mode),
            persist = true
        )
    }

    private fun buildShareText(state: ReaderUiState): String {
        val title = state.title?.takeIf { it.isNotBlank() } ?: "正在阅读"
        val progression = state.renderState?.progression?.percent
            ?.coerceIn(0.0, 1.0)
            ?.let { "${(it * 100).toInt()}%" }
        return if (progression == null) {
            title
        } else {
            "$title · $progression"
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
                val decision = ExternalLinkPolicy.evaluate(target.url)
                if (decision is ExternalLinkPolicy.Decision.Allow) {
                    ui.emit(ReaderEffect.OpenExternalUrl(decision.url))
                } else {
                    ui.emit(
                        ReaderEffect.Snackbar(
                            UiText.Dynamic("Blocked unsafe external link")
                        )
                    )
                }
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
        pendingUndoTurn = null
        releasePageContent(ui.state.value.page)
        ui.update { it.copy(page = null, isRenderingFinal = false) }
        session.closeCurrent { bookId, locator, progression ->
            saveReadingProgress(
                bookId = bookId,
                locator = locator,
                progression = progression
            )
        }
    }

    private fun toggleImmersiveChrome() {
        ui.update { current ->
            if (!current.displayPrefs.fullScreenMode) {
                current.copy(chromeVisible = true)
            } else {
                current.copy(chromeVisible = !current.chromeVisible)
            }
        }
    }

    private fun openDock(tab: ReaderDockTab) {
        ui.update {
            it.copy(layerState = ReaderLayerState.Dock(tab))
        }
    }

    private fun openSheet(sheet: ReaderSheet) {
        ui.update { current ->
            when (sheet) {
                ReaderSheet.None -> current.copy(layerState = ReaderLayerState.Reading)
                ReaderSheet.FullSettings -> current.copy(layerState = ReaderLayerState.FullSettings)
                else -> current.copy(layerState = ReaderLayerState.Sheet(sheet))
            }
        }
    }

    private fun openFullSettings() {
        ui.update {
            it.copy(layerState = ReaderLayerState.FullSettings)
        }
    }

    private fun toggleDockTab(tab: ReaderDockTab): Boolean {
        val opened = ui.state.value.layerState != ReaderLayerState.Dock(tab)
        ui.update { current ->
            current.copy(
                layerState = if (opened) {
                    ReaderLayerState.Dock(tab)
                } else {
                    ReaderLayerState.Reading
                }
            )
        }
        return opened
    }

    private fun closeLayerToReading() {
        ui.update {
            it.copy(layerState = ReaderLayerState.Reading)
        }
    }

    private fun handleBackPressed() {
        val current = ui.state.value
        when (val layer = current.layerState) {
            ReaderLayerState.Reading -> {
                if (current.displayPrefs.fullScreenMode && !current.chromeVisible) {
                    ui.update { it.copy(chromeVisible = true) }
                } else {
                    ui.emit(ReaderEffect.Back)
                }
            }

            is ReaderLayerState.Dock -> {
                ui.update { it.copy(layerState = ReaderLayerState.Reading) }
            }

            is ReaderLayerState.Sheet -> {
                val nextLayer = if (layer.sheet.isSettingsSubSheet()) {
                    ReaderLayerState.Dock(ReaderDockTab.Settings)
                } else {
                    ReaderLayerState.Reading
                }
                ui.update { it.copy(layerState = nextLayer) }
            }

            ReaderLayerState.FullSettings -> {
                ui.update { it.copy(layerState = ReaderLayerState.Dock(ReaderDockTab.Settings)) }
            }
        }
    }

    private suspend fun handleTap(intent: ReaderIntent.HandleTap) {
        val current = ui.state.value
        when (current.layerState) {
            is ReaderLayerState.Dock,
            is ReaderLayerState.Sheet -> {
                interactionTracker.track(ReaderInteractionEvent.ClosePanelByTap)
                closeLayerToReading()
                return
            }

            ReaderLayerState.FullSettings -> return
            ReaderLayerState.Reading -> Unit
        }

        if (current.displayPrefs.preventAccidentalTurn) {
            val height = intent.viewportHeightPx.coerceAtLeast(1).toFloat()
            val y = intent.yPx.coerceIn(0f, height)
            val verticalGuard = height * 0.04f
            if (y <= verticalGuard || y >= height - verticalGuard) {
                return
            }
        }

        when (
            gestureInterpreter.resolveTapAction(
                xPx = intent.xPx,
                viewportWidthPx = intent.viewportWidthPx,
                prefs = current.displayPrefs
            )
        ) {
            ReaderTapAction.PREV -> performGestureTurn(
                direction = PageTurnDirection.PREV,
                event = ReaderInteractionEvent.TapPrev
            )

            ReaderTapAction.NEXT -> performGestureTurn(
                direction = PageTurnDirection.NEXT,
                event = ReaderInteractionEvent.TapNext
            )

            ReaderTapAction.CENTER -> {
                if (tryUndoPageTurn()) return
                if (current.displayPrefs.fullScreenMode) {
                    toggleImmersiveChrome()
                    interactionTracker.track(ReaderInteractionEvent.CenterTapToggleChrome)
                }
            }

            ReaderTapAction.NONE -> Unit
        }
    }

    private suspend fun handleDragEnd(intent: ReaderIntent.HandleDragEnd) {
        val current = ui.state.value
        if (current.layerState != ReaderLayerState.Reading) return
        val direction = gestureInterpreter.resolveDragDirection(
            axis = intent.axis,
            deltaPx = intent.deltaPx,
            viewportMainAxisPx = intent.viewportMainAxisPx,
            pageTurnMode = current.pageTurnMode,
            prefs = current.displayPrefs
        ) ?: return

        val event = when (direction) {
            PageTurnDirection.NEXT -> ReaderInteractionEvent.DragNext
            PageTurnDirection.PREV -> ReaderInteractionEvent.DragPrev
        }
        performGestureTurn(direction = direction, event = event)
    }

    private suspend fun performGestureTurn(
        direction: PageTurnDirection,
        event: ReaderInteractionEvent
    ) {
        val succeeded = when (direction) {
            PageTurnDirection.NEXT -> navigate(direction = direction) { controller, policy ->
                controller.next(policy)
            }

            PageTurnDirection.PREV -> navigate(direction = direction) { controller, policy ->
                controller.prev(policy)
            }
        }
        if (!succeeded) return
        interactionTracker.track(event)

        val prefs = ui.state.value.displayPrefs
        if (!prefs.preventAccidentalTurn) {
            pendingUndoTurn = null
            return
        }
        pendingUndoTurn = PendingUndoTurn(
            direction = direction,
            expiresAtMs = System.currentTimeMillis() + UNDO_WINDOW_MS
        )
        ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("翻页完成，800ms 内点击中间区域可撤销")))
    }

    private suspend fun tryUndoPageTurn(): Boolean {
        val pending = pendingUndoTurn ?: return false
        if (System.currentTimeMillis() > pending.expiresAtMs) {
            pendingUndoTurn = null
            return false
        }
        pendingUndoTurn = null
        val direction = when (pending.direction) {
            PageTurnDirection.NEXT -> PageTurnDirection.PREV
            PageTurnDirection.PREV -> PageTurnDirection.NEXT
        }
        val succeeded = when (direction) {
            PageTurnDirection.NEXT -> navigate(direction = direction) { controller, policy ->
                controller.next(policy)
            }

            PageTurnDirection.PREV -> navigate(direction = direction) { controller, policy ->
                controller.prev(policy)
            }
        }
        if (!succeeded) return false
        interactionTracker.track(ReaderInteractionEvent.UndoPageTurn)
        ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("已撤销上一次翻页")))
        return true
    }

    private fun openSubSheet(sheet: ReaderSheet) {
        if (!sheet.isSettingsSubSheet()) return
        openSheet(sheet)
    }

    override fun onCleared() {
        render.cancel()
        releasePageContent(ui.state.value.page)
        cleanupScope.launch {
            closeSession()
        }
        super.onCleared()
    }

    private fun replacePage(
        nextPage: RenderPage,
        direction: PageTurnDirection?
    ) {
        val previous = ui.state.value.page
        if (previous != null && previous !== nextPage) {
            releasePageContent(previous)
        }
        ui.update { current ->
            val turnDirection = direction
            val shouldAnimate = turnDirection != null && current.page?.id != nextPage.id
            current.copy(
                page = nextPage,
                error = null,
                pageTransition = if (shouldAnimate) {
                    current.pageTransition.next(turnDirection)
                } else {
                    current.pageTransition
                }
            )
        }
    }

    private fun releasePageContent(page: RenderPage?) {
        when (val content = page?.content) {
            is RenderContent.BitmapPage -> {
                if (!content.bitmap.isRecycled) {
                    content.bitmap.recycle()
                }
            }

            is RenderContent.Tiles -> {
                runCatching { content.tileProvider.close() }
            }

            else -> Unit
        }
    }

    private data class StartArgs(
        val bookId: Long,
        val locatorArg: String?
    )

    private data class OutlineEntry(
        val node: OutlineNode,
        val depth: Int
    )

    private data class PendingUndoTurn(
        val direction: PageTurnDirection,
        val expiresAtMs: Long
    )

    private companion object {
        const val UNDO_WINDOW_MS = 800L
    }
}

private fun ReaderSheet.isSettingsSubSheet(): Boolean {
    return this == ReaderSheet.SettingsFont ||
        this == ReaderSheet.SettingsSpacing ||
        this == ReaderSheet.SettingsPageTurn ||
        this == ReaderSheet.SettingsMoreBackground
}
