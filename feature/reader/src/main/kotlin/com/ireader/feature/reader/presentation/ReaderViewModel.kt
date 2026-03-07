package com.ireader.feature.reader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRecord
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.BookSourceResolver
import com.ireader.core.files.source.DocumentSource
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.web.ExternalLinkPolicy
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.Locator
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.model.BookFormat
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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

    private val intents = Channel<ReaderIntent>(capacity = Channel.BUFFERED)
    private var currentStartArgs: StartArgs? = null
    private var searchJob: Job? = null
    private var searchGeneration: Long = 0L
    private var finalRenderJob: Job? = null
    private var pendingUndoTurn: PendingUndoTurn? = null
    private var appliedLayoutConstraints: LayoutConstraints? = null
    private var pendingTextLayouterFactory: TextLayouterFactory? = null
    private var appliedTextLayouterFactoryKey: String? = null
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            for (intent in intents) {
                try {
                    handleIntent(intent)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (throwable: Throwable) {
                    recoverFromUnexpectedIntentFailure(throwable)
                }
            }
        }
        viewModelScope.launch {
            settingsStore.displayPrefs
                .distinctUntilChanged()
                .collect { prefs ->
                    ui.update { current ->
                        current.copy(
                            displayPrefs = prefs,
                            isNightMode = prefs.nightMode,
                            chromeVisible = current.chromeVisible
                        )
                    }
                }
        }
    }

    fun dispatch(intent: ReaderIntent) {
        val sent = intents.trySend(intent)
        if (sent.isSuccess) return
        if (shouldDropWhenQueueBusy(intent)) return
        viewModelScope.launch {
            intents.send(intent)
        }
    }

    private fun shouldDropWhenQueueBusy(intent: ReaderIntent): Boolean {
        return intent is ReaderIntent.LayoutChanged || intent is ReaderIntent.SearchQueryChanged
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
                ui.emitGuaranteed(ReaderEffect.Back)
            }

            is ReaderIntent.LayoutChanged -> applyLayout(intent.constraints)
            is ReaderIntent.TextLayouterFactoryChanged -> applyTextLayouterFactory(intent.factory)
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
                    ui.emitGuaranteed(ReaderEffect.OpenAnnotations(bookId))
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
            ReaderIntent.ShareBook -> ui.emitGuaranteed(ReaderEffect.ShareText(buildShareText(ui.state.value)))
            ReaderIntent.CreateAnnotation -> createAnnotation()
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
            is ReaderIntent.SetVolumeKeyPaging -> updateDisplayPrefs { prefs ->
                prefs.copy(volumeKeyPagingEnabled = intent.enabled)
            }
            ReaderIntent.CloseSheet -> closeLayerToReading()

            ReaderIntent.Next -> if (canHandlePageTurn()) {
                navigate(direction = PageTurnDirection.NEXT) { controller, policy ->
                    pendingUndoTurn = null
                    controller.next(policy)
                }
            } else {
                false
            }
            ReaderIntent.Prev -> if (canHandlePageTurn()) {
                navigate(direction = PageTurnDirection.PREV) { controller, policy ->
                    pendingUndoTurn = null
                    controller.prev(policy)
                }
            } else {
                false
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
            is ReaderIntent.SelectionStart -> updateSelection { controller ->
                controller.start(intent.locator)
            }
            is ReaderIntent.SelectionUpdate -> updateSelection { controller ->
                controller.update(intent.locator)
            }
            ReaderIntent.SelectionFinish -> updateSelection { controller ->
                controller.finish()
            }
            ReaderIntent.ClearSelection -> updateSelection { controller ->
                controller.clear()
            }

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
        pendingUndoTurn = null
        appliedLayoutConstraints = null
        appliedTextLayouterFactoryKey = null

        ui.update {
            it.copy(
                bookId = args.bookId,
                isOpening = true,
                title = null,
                layerState = ReaderLayerState.Reading,
                chromeVisible = false,
                page = null,
                controller = null,
                resources = null,
                capabilities = null,
                renderState = null,
                currentConfig = null,
                gestureProfile = ReaderGestureProfile.REFLOW,
                passwordPrompt = null,
                error = null,
                activeMenuTab = ReaderMenuTab.Toc,
                toc = TocState(),
                search = SearchState()
            )
        }

        val preparation = withContext(Dispatchers.IO) {
            val book = bookRepo.getRecordById(args.bookId) ?: return@withContext OpenPreparation.BookNotFound
            val source = sourceResolver.resolve(book)
            if (source == null) {
                bookRepo.setIndexState(book.bookId, IndexState.MISSING, "File not found")
                return@withContext OpenPreparation.MissingSource
            }
            val routeLocator = args.locatorArg?.let(locatorCodec::decode)
            val historyLocator = runCatching {
                progressRepo.getByBookId(book.bookId)?.locatorJson?.let(locatorCodec::decode)
            }.getOrNull()
            OpenPreparation.Ready(
                book = book,
                source = source,
                initialLocator = routeLocator ?: historyLocator
            )
        }

        when (preparation) {
            OpenPreparation.BookNotFound -> {
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

            OpenPreparation.MissingSource -> {
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

            is OpenPreparation.Ready -> Unit
        }

        val ready = preparation
        val book = ready.book

        when (
            val result = openReaderSession(
                source = ready.source,
                options = OpenOptions(
                    hintFormat = book.format,
                    password = password
                ),
                initialLocator = ready.initialLocator
            )
        ) {
            is ReaderResult.Err -> {
                handleOpenError(result.error, password)
            }

            is ReaderResult.Ok -> {
                session.attach(bookId = book.bookId, handle = result.value)
                withContext(Dispatchers.IO) {
                    bookRepo.touchLastOpened(book.bookId)
                }

                ui.update {
                    it.copy(
                        isOpening = false,
                        title = book.title?.takeIf(String::isNotBlank) ?: book.fileName,
                        controller = result.value.controller,
                        resources = result.value.resources,
                        capabilities = result.value.document.capabilities,
                        currentConfig = result.value.controller.state.value.config,
                        pageTurnMode = resolvePageTurnMode(result.value.controller.state.value.config),
                        gestureProfile = resolveGestureProfile(result.value.document.capabilities),
                        passwordPrompt = null,
                        error = null
                    )
                }

                startSessionCollectors(result.value, book.bookId)
                if (!ensureTextLayouterFactoryReady(result.value.controller)) {
                    return
                }
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
                .collect { event ->
                    when (event) {
                        is ReaderEvent.BackgroundTap -> {
                            handleTap(
                                ReaderIntent.HandleTap(
                                    xPx = event.xPx,
                                    yPx = event.yPx,
                                    viewportWidthPx = event.viewportWidthPx,
                                    viewportHeightPx = event.viewportHeightPx
                                )
                            )
                        }

                        is ReaderEvent.Error -> {
                            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Render error")))
                        }

                        is ReaderEvent.PageChanged,
                        is ReaderEvent.Rendered -> Unit
                    }
                }
        }

        val settingsJob = viewModelScope.launch {
            observeEffectiveConfig(sessionHandle.document.capabilities)
                .distinctUntilChanged()
                .collect { config ->
                    val effectiveConfig = normalizeConfigForSession(
                        config = config,
                        sessionHandle = sessionHandle
                    )
                    if (sessionHandle.controller.state.value.config == effectiveConfig) {
                        ui.update {
                            it.copy(
                                currentConfig = effectiveConfig,
                                pageTurnMode = resolvePageTurnMode(effectiveConfig)
                            )
                        }
                        return@collect
                    }
                    when (render.withNavigationLock { sessionHandle.controller.setConfig(effectiveConfig) }) {
                        is ReaderResult.Ok -> {
                            ui.update {
                                it.copy(
                                    currentConfig = effectiveConfig,
                                    pageTurnMode = resolvePageTurnMode(effectiveConfig)
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
        if (appliedLayoutConstraints == constraints) return
        when (val result = render.withNavigationLock { sessionHandle.controller.setLayoutConstraints(constraints) }) {
            is ReaderResult.Ok -> {
                appliedLayoutConstraints = constraints
                render.requestRender(RenderRequest.LAYOUT)
            }
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
        }
    }

    private suspend fun applyTextLayouterFactory(factory: TextLayouterFactory) {
        pendingTextLayouterFactory = factory
        val sessionHandle = session.currentHandle() ?: return
        if (!ensureTextLayouterFactoryReady(sessionHandle.controller)) {
            return
        }
        if (render.currentLayout() != null) {
            render.requestRender(RenderRequest.LAYOUT)
        }
    }

    private suspend fun ensureTextLayouterFactoryReady(controller: ReaderController): Boolean {
        val factory = pendingTextLayouterFactory ?: return false
        if (appliedTextLayouterFactoryKey == factory.environmentKey) {
            return true
        }
        return when (val result = render.withNavigationLock { controller.setTextLayouterFactory(factory) }) {
            is ReaderResult.Ok -> {
                appliedTextLayouterFactoryKey = factory.environmentKey
                true
            }

            is ReaderResult.Err -> {
                ui.update { it.copy(error = errorMapper.map(result.error)) }
                false
            }
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

        val sessionHandle = session.currentHandle()
        val effectiveConfig = if (sessionHandle != null) {
            normalizeConfigForSession(
                config = config,
                sessionHandle = sessionHandle
            )
        } else {
            config
        }

        if (sessionHandle == null) {
            ui.update {
                it.copy(
                    currentConfig = effectiveConfig,
                    pageTurnMode = resolvePageTurnMode(effectiveConfig)
                )
            }
            return
        }

        if (persist) {
            ui.update {
                it.copy(
                    currentConfig = effectiveConfig,
                    pageTurnMode = resolvePageTurnMode(effectiveConfig)
                )
            }
            return
        }

        if (sessionHandle.controller.state.value.config == effectiveConfig) {
            ui.update {
                it.copy(
                    currentConfig = effectiveConfig,
                    pageTurnMode = resolvePageTurnMode(effectiveConfig)
                )
            }
            return
        }

        when (val result = render.withNavigationLock { sessionHandle.controller.setConfig(effectiveConfig) }) {
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
            is ReaderResult.Ok -> {
                ui.update {
                    it.copy(
                        currentConfig = effectiveConfig,
                        pageTurnMode = resolvePageTurnMode(effectiveConfig)
                    )
                }
                render.requestRender(RenderRequest.CONFIG)
            }
        }
    }

    private suspend fun renderCurrentPageImmediate() {
        val sessionHandle = session.currentHandle() ?: return
        if (render.currentLayout() == null) return
        if (!ensureTextLayouterFactoryReady(sessionHandle.controller)) return
        cancelFinalRender()
        val fixedLayout = sessionHandle.document.capabilities.fixedLayout
        val result = render.withNavigationLock {
            if (fixedLayout) {
                sessionHandle.controller.render(RenderPolicy(quality = RenderPolicy.Quality.DRAFT))
            } else {
                sessionHandle.controller.render()
            }
        }
        when (result) {
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
            is ReaderResult.Ok -> {
                replacePage(result.value, direction = null)
                if (fixedLayout) {
                    launchFinalRender(sessionHandle = sessionHandle, expectedPageId = result.value.id)
                }
            }
        }
    }

    private suspend fun navigate(
        direction: PageTurnDirection? = null,
        block: suspend (ReaderController, RenderPolicy) -> ReaderResult<RenderPage>
    ): Boolean {
        val sessionHandle = session.currentHandle() ?: return false
        if (!ensureTextLayouterFactoryReady(sessionHandle.controller)) return false
        cancelFinalRender()
        val fixedLayout = sessionHandle.document.capabilities.fixedLayout
        val actionPolicy = if (fixedLayout) {
            RenderPolicy(quality = RenderPolicy.Quality.DRAFT)
        } else {
            RenderPolicy.Default
        }
        return when (val result = render.withNavigationLock { block(sessionHandle.controller, actionPolicy) }) {
            is ReaderResult.Err -> {
                ui.update { it.copy(error = errorMapper.map(result.error)) }
                false
            }

            is ReaderResult.Ok -> {
                replacePage(result.value, direction)
                if (fixedLayout) {
                    launchFinalRender(sessionHandle = sessionHandle, expectedPageId = result.value.id)
                }
                true
            }
        }
    }

    private fun cancelFinalRender() {
        finalRenderJob?.cancel()
        finalRenderJob = null
        ui.update { it.copy(isRenderingFinal = false) }
    }

    private fun launchFinalRender(
        sessionHandle: ReaderSessionHandle,
        expectedPageId: PageId
    ) {
        cancelFinalRender()
        finalRenderJob = viewModelScope.launch {
            val runningJob = coroutineContext[Job]
            ui.update { it.copy(isRenderingFinal = true) }
            try {
                delay(FINAL_RENDER_DEBOUNCE_MS)
                if (session.currentHandle() !== sessionHandle) return@launch
                val finalResult = render.withNavigationLock {
                    sessionHandle.controller.render(RenderPolicy(quality = RenderPolicy.Quality.FINAL))
                }
                when (finalResult) {
                    is ReaderResult.Ok -> {
                        val activeHandle = session.currentHandle()
                        val activePageId = ui.state.value.page?.id
                        if (activeHandle === sessionHandle && activePageId == expectedPageId) {
                            replacePage(finalResult.value, direction = null)
                        }
                    }

                    is ReaderResult.Err -> {
                        if (session.currentHandle() === sessionHandle && ui.state.value.page?.id == expectedPageId) {
                            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Final render failed")))
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                if (finalRenderJob === runningJob) {
                    finalRenderJob = null
                    ui.update { it.copy(isRenderingFinal = false) }
                }
            }
        }
    }

    private fun resolvePageTurnMode(config: RenderConfig?): PageTurnMode {
        val reflow = config as? RenderConfig.ReflowText ?: return PageTurnMode.COVER_HORIZONTAL
        return reflow.pageTurnMode()
    }

    private fun resolveGestureProfile(capabilities: DocumentCapabilities?): ReaderGestureProfile {
        return if (capabilities?.fixedLayout == true) {
            ReaderGestureProfile.FIXED
        } else {
            ReaderGestureProfile.REFLOW
        }
    }

    private fun normalizeConfigForSession(
        config: RenderConfig,
        sessionHandle: ReaderSessionHandle
    ): RenderConfig {
        if (!isEpubReflowSession(sessionHandle)) return config
        val requested = config as? RenderConfig.ReflowText ?: return config
        val current = sessionHandle.controller.state.value.config as? RenderConfig.ReflowText ?: return config
        return normalizeEpubEffectiveReflowConfig(
            requested = requested,
            current = current
        )
    }

    private fun isEpubReflowSession(sessionHandle: ReaderSessionHandle): Boolean {
        return sessionHandle.document.format == BookFormat.EPUB &&
            !sessionHandle.document.capabilities.fixedLayout
    }

    private fun canHandlePageTurn(current: ReaderUiState = ui.state.value): Boolean {
        return current.layerState == ReaderLayerState.Reading &&
            current.passwordPrompt == null &&
            !current.isOpening &&
            current.error == null &&
            current.controller != null
    }

    private suspend fun cancelActiveSearch() {
        val runningJob = searchJob ?: return
        searchGeneration += 1L
        searchJob = null
        runningJob.cancelAndJoin()
        ui.update { current ->
            current.copy(
                search = current.search.copy(isSearching = false)
            )
        }
    }

    private suspend fun recoverFromUnexpectedIntentFailure(throwable: Throwable) {
        ui.update { current ->
            current.copy(
                isOpening = false,
                search = current.search.copy(isSearching = false),
                error = if (current.controller == null) {
                    ReaderUiError(
                        message = UiText.Dynamic("Unexpected reader error"),
                        actionLabel = UiText.Dynamic("Back"),
                        action = ReaderErrorAction.Back,
                        debugCode = throwable::class.simpleName
                    )
                } else {
                    current.error
                }
            )
        }
        ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Unexpected reader error")))
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
            state.copy(
                displayPrefs = updated,
                isNightMode = updated.nightMode,
                chromeVisible = state.chromeVisible
            )
        }
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

    private suspend fun updateSelection(
        block: suspend (SelectionController) -> ReaderResult<Unit>
    ) {
        val sessionHandle = session.currentHandle() ?: return
        val selectionController = sessionHandle.selectionController ?: return
        when (block(selectionController)) {
            is ReaderResult.Ok -> Unit
            is ReaderResult.Err -> ui.emit(
                ReaderEffect.Snackbar(UiText.Dynamic("选区操作失败"))
            )
        }
    }

    private suspend fun createAnnotation() {
        val sessionHandle = session.currentHandle() ?: return
        val annotationProvider = sessionHandle.annotations
        if (annotationProvider == null) {
            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("当前文档不支持批注")))
            return
        }

        val selection = when (val selectionProvider = sessionHandle.selection) {
            null -> null
            else -> {
                when (val result = selectionProvider.currentSelection()) {
                    is ReaderResult.Ok -> result.value
                    is ReaderResult.Err -> {
                        ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("获取选区失败，已使用当前位置创建批注")))
                        null
                    }
                }
            }
        }

        val draft = when (
            val result = ReaderAnnotationDraftFactory.create(
                selection = selection,
                fallbackLocator = ui.state.value.renderState?.locator
            )
        ) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> {
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("无法创建批注：缺少定位信息")))
                return
            }
        }

        when (annotationProvider.create(draft)) {
            is ReaderResult.Err -> {
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("创建批注失败")))
            }

            is ReaderResult.Ok -> {
                sessionHandle.selection?.clearSelection()
                sessionHandle.selectionController?.clear()
                when (sessionHandle.controller.invalidate(InvalidateReason.CONTENT_CHANGED)) {
                    is ReaderResult.Ok -> Unit
                    is ReaderResult.Err -> ui.emit(
                        ReaderEffect.Snackbar(UiText.Dynamic("批注已保存，但页面刷新失败"))
                    )
                }
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("已添加批注")))
            }
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
                    ui.emitGuaranteed(ReaderEffect.OpenExternalUrl(decision.url))
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

        cancelActiveSearch()
        val generation = searchGeneration + 1L
        searchGeneration = generation
        searchJob = viewModelScope.launch {
            val runningJob = coroutineContext[Job]

            fun isCurrentSearch(): Boolean {
                return searchGeneration == generation &&
                    session.currentHandle() === sessionHandle
            }

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
                        if (!isCurrentSearch()) return@collect
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
                if (isCurrentSearch() && coroutineContext.isActive) {
                    accumulator.flush()
                    if (searchJob === runningJob) {
                        searchJob = null
                    }
                    ui.update {
                        it.copy(
                            search = it.search.copy(isSearching = false)
                        )
                    }
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
            val extras = node.locator.extras
            out += TocItem(
                title = node.title.ifBlank { "(untitled)" },
                locatorEncoded = locatorCodec.encode(node.locator),
                depth = depth,
                locatorValue = node.locator.value,
                href = extras[TOC_EXTRA_HREF],
                position = extras[TOC_EXTRA_POSITION]?.toIntOrNull(),
                progression = extras[TOC_EXTRA_TOTAL_PROGRESSION]?.toDoubleOrNull()
                    ?: extras[TOC_EXTRA_PROGRESSION]?.toDoubleOrNull()
            )

            val children = node.children
            for (index in children.lastIndex downTo 0) {
                stack.addLast(OutlineEntry(children[index], depth + 1))
            }
        }
        return out
    }

    private suspend fun closeSession() {
        cancelActiveSearch()
        pendingUndoTurn = null
        cancelFinalRender()
        appliedLayoutConstraints = null
        appliedTextLayouterFactoryKey = null
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
            current.copy(chromeVisible = !current.chromeVisible)
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

    private suspend fun handleBackPressed() {
        val current = ui.state.value
        when (val layer = current.layerState) {
            ReaderLayerState.Reading -> {
                if (!current.chromeVisible) {
                    ui.update { it.copy(chromeVisible = true) }
                } else {
                    ui.emitGuaranteed(ReaderEffect.Back)
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

        if (current.chromeVisible) {
            ui.update { it.copy(chromeVisible = false) }
            return
        }

        if (current.displayPrefs.preventAccidentalTurn) {
            val height = intent.viewportHeightPx.coerceAtLeast(1).toFloat()
            val y = intent.yPx.coerceIn(0f, height)
            val verticalGuard = height * 0.04f
            if (y <= verticalGuard || y >= height - verticalGuard) {
                return
            }
        }

        val tapAction = gestureInterpreter.resolveTapAction(
            xPx = intent.xPx,
            viewportWidthPx = intent.viewportWidthPx,
            prefs = current.displayPrefs
        )

        if (!intent.allowPageTurn) {
            if (tapAction == ReaderTapAction.CENTER) {
                ui.update { it.copy(chromeVisible = true) }
                interactionTracker.track(ReaderInteractionEvent.CenterTapToggleChrome)
            }
            return
        }

        when (tapAction) {
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
                ui.update { it.copy(chromeVisible = true) }
                interactionTracker.track(ReaderInteractionEvent.CenterTapToggleChrome)
            }

            ReaderTapAction.NONE -> Unit
        }
    }

    private suspend fun handleDragEnd(intent: ReaderIntent.HandleDragEnd) {
        val current = ui.state.value
        if (current.layerState != ReaderLayerState.Reading) return
        if (current.gestureProfile != ReaderGestureProfile.REFLOW) return
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
        return true
    }

    private fun openSubSheet(sheet: ReaderSheet) {
        if (!sheet.isSettingsSubSheet()) return
        openSheet(sheet)
    }

    override fun onCleared() {
        render.cancel()
        cancelFinalRender()
        cleanupScope.launch {
            try {
                closeSession()
            } finally {
                cleanupScope.cancel()
            }
        }
        super.onCleared()
    }

    private fun replacePage(
        nextPage: RenderPage,
        direction: PageTurnDirection?
    ) {
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

    private sealed interface OpenPreparation {
        data object BookNotFound : OpenPreparation
        data object MissingSource : OpenPreparation
        data class Ready(
            val book: BookRecord,
            val source: DocumentSource,
            val initialLocator: Locator?
        ) : OpenPreparation
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
        const val FINAL_RENDER_DEBOUNCE_MS = 120L
        const val UNDO_WINDOW_MS = 800L
    }
}

private fun ReaderSheet.isSettingsSubSheet(): Boolean {
    return this == ReaderSheet.SettingsFont ||
        this == ReaderSheet.SettingsSpacing ||
        this == ReaderSheet.SettingsPageTurn ||
        this == ReaderSheet.SettingsMoreBackground
}
