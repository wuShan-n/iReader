package com.ireader.feature.reader.presentation

import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.BookRecord
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState as DbIndexState
import com.ireader.core.database.book.LibraryBookRow
import com.ireader.core.database.book.ReadingStatus as DbReadingStatus
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.BookSourceResolver
import com.ireader.core.files.source.DocumentSource
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.testing.MainDispatcherRule
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression
import com.ireader.reader.model.SessionId
import android.net.Uri
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.ArrayDeque

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `start should show book not found when repository returns null`() = runTest {
        val vm = newViewModel(bookById = emptyMap())

        vm.dispatch(ReaderIntent.Start(bookId = 42L, locatorArg = null))

        val state = vm.state.first { it.error != null }
        val error = state.error
        assertFalse(state.isOpening)
        assertNotNull(error)
        assertEquals("Book not found", (error?.message as UiText.Dynamic).value)
    }

    @Test
    fun `update config with persist should write settings and update state`() = runTest {
        val settings = FakeReaderSettingsStore()
        val vm = newViewModel(
            bookById = emptyMap(),
            settingsStore = settings
        )
        val config = RenderConfig.ReflowText(fontSizeSp = 22f)

        vm.dispatch(ReaderIntent.UpdateConfig(config = config, persist = true))
        advanceUntilIdle()

        assertEquals(config, settings.lastReflow)
        assertEquals(config, vm.state.value.currentConfig)
    }

    @Test
    fun `open menu should activate dock tab and keep sheet closed`() = runTest {
        val vm = newViewModel(bookById = emptyMap())

        vm.dispatch(ReaderIntent.OpenMenu)
        advanceUntilIdle()

        assertEquals(ReaderLayerState.Dock(ReaderDockTab.Menu), vm.state.value.layerState)
        assertEquals(ReaderSheet.None, vm.state.value.sheet)
        assertEquals(ReaderDockTab.Menu, vm.state.value.activeDockTab)
    }

    @Test
    fun `toggle dock tab should collapse when tapping same tab again`() = runTest {
        val vm = newViewModel(bookById = emptyMap())

        vm.dispatch(ReaderIntent.ToggleDockTab(ReaderDockTab.Menu))
        vm.dispatch(ReaderIntent.ToggleDockTab(ReaderDockTab.Menu))
        advanceUntilIdle()

        assertEquals(ReaderLayerState.Reading, vm.state.value.layerState)
        assertEquals(null, vm.state.value.activeDockTab)
    }

    @Test
    fun `set menu tab should update active tab`() = runTest {
        val vm = newViewModel(bookById = emptyMap())

        vm.dispatch(ReaderIntent.SetMenuTab(ReaderMenuTab.Notes))
        advanceUntilIdle()

        assertEquals(ReaderMenuTab.Notes, vm.state.value.activeMenuTab)
    }

    @Test
    fun `back pressed should move full settings to settings dock then reading`() = runTest {
        val vm = newViewModel(bookById = emptyMap())

        vm.dispatch(ReaderIntent.OpenFullSettings)
        vm.dispatch(ReaderIntent.BackPressed)
        advanceUntilIdle()
        assertEquals(ReaderLayerState.Dock(ReaderDockTab.Settings), vm.state.value.layerState)

        vm.dispatch(ReaderIntent.BackPressed)
        advanceUntilIdle()
        assertEquals(ReaderLayerState.Reading, vm.state.value.layerState)
    }

    @Test
    fun `tap should close dock panel before any navigation action`() = runTest {
        val vm = newViewModel(bookById = emptyMap())
        vm.dispatch(ReaderIntent.OpenMenu)

        vm.dispatch(
            ReaderIntent.HandleTap(
                xPx = 20f,
                yPx = 200f,
                viewportWidthPx = 1080,
                viewportHeightPx = 1920
            )
        )
        advanceUntilIdle()

        assertEquals(ReaderLayerState.Reading, vm.state.value.layerState)
    }

    @Test
    fun `center tap shows chrome and next tap hides chrome`() = runTest {
        val vm = newViewModel(bookById = emptyMap())
        assertFalse(vm.state.value.chromeVisible)

        vm.dispatch(
            ReaderIntent.HandleTap(
                xPx = 540f,
                yPx = 960f,
                viewportWidthPx = 1080,
                viewportHeightPx = 1920
            )
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.chromeVisible)

        vm.dispatch(
            ReaderIntent.HandleTap(
                xPx = 120f,
                yPx = 960f,
                viewportWidthPx = 1080,
                viewportHeightPx = 1920
            )
        )
        advanceUntilIdle()

        assertFalse(vm.state.value.chromeVisible)
    }

    @Test
    fun `edge tap should not navigate when page turn is disallowed`() = runTest {
        val controller = RecordingReaderController()
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerSessionHandle(controller = controller)
            )

            vm.dispatch(
                ReaderIntent.HandleTap(
                    xPx = 980f,
                    yPx = 960f,
                    viewportWidthPx = 1080,
                    viewportHeightPx = 1920,
                    allowPageTurn = false
                )
            )
            advanceUntilIdle()

            assertEquals(0, controller.nextCalls)
            assertFalse(vm.state.value.chromeVisible)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `center tap should still show chrome when page turn is disallowed`() = runTest {
        val controller = RecordingReaderController()
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerSessionHandle(controller = controller)
            )

            vm.dispatch(
                ReaderIntent.HandleTap(
                    xPx = 540f,
                    yPx = 960f,
                    viewportWidthPx = 1080,
                    viewportHeightPx = 1920,
                    allowPageTurn = false
                )
            )
            advanceUntilIdle()

            assertTrue(vm.state.value.chromeVisible)
            assertEquals(0, controller.nextCalls)
            assertEquals(0, controller.prevCalls)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `drag should be ignored for fixed gesture profile`() = runTest {
        val controller = RecordingReaderController()
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerSessionHandle(
                    controller = controller,
                    fixedLayout = true
                )
            )

            vm.dispatch(
                ReaderIntent.HandleDragEnd(
                    axis = GestureAxis.HORIZONTAL,
                    deltaPx = -260f,
                    viewportMainAxisPx = 1080
                )
            )
            advanceUntilIdle()

            assertEquals(ReaderGestureProfile.FIXED, vm.state.value.gestureProfile)
            assertEquals(0, controller.nextCalls)
            assertEquals(0, controller.prevCalls)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `set volume key paging should persist display prefs`() = runTest {
        val settings = FakeReaderSettingsStore()
        val vm = newViewModel(
            bookById = emptyMap(),
            settingsStore = settings
        )

        vm.dispatch(ReaderIntent.SetVolumeKeyPaging(false))
        advanceUntilIdle()

        assertEquals(false, settings.lastDisplayPrefs?.volumeKeyPagingEnabled)
    }

    @Test
    fun `starting a new search should not leak canceled pending results`() = runTest {
        val controller = RecordingReaderController()
        val searchProvider = FakeSearchProvider(
            scriptedExcerpts = mapOf(
                "first" to listOf("first", "stale-pending"),
                "second" to listOf("fresh")
            )
        )
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerSessionHandle(
                    controller = controller,
                    search = searchProvider
                )
            )

            vm.dispatch(ReaderIntent.SearchQueryChanged("first"))
            vm.dispatch(ReaderIntent.ExecuteSearch)
            vm.state.first { it.search.results.isNotEmpty() }
            assertTrue(vm.state.value.search.results.isNotEmpty())

            vm.dispatch(ReaderIntent.SearchQueryChanged("second"))
            vm.dispatch(ReaderIntent.ExecuteSearch)
            vm.state.first { state ->
                state.search.results.isNotEmpty() &&
                    state.search.results.all { it.excerpt == "fresh" }
            }

            assertEquals(listOf("fresh"), vm.state.value.search.results.map { it.excerpt })
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `next page intent should be ignored while search sheet is open`() = runTest {
        val controller = RecordingReaderController()
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerSessionHandle(controller = controller)
            )
            val nextCallsBefore = controller.nextCalls

            vm.dispatch(ReaderIntent.OpenSearch)
            advanceUntilIdle()
            vm.dispatch(ReaderIntent.Next)
            vm.dispatch(ReaderIntent.Prev)
            advanceUntilIdle()

            assertEquals(nextCallsBefore, controller.nextCalls)
            assertEquals(0, controller.prevCalls)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `persisted config update should not call controller directly`() = runTest {
        val controller = RecordingReaderController()
        val settings = FakeReaderSettingsStore(emitUpdates = false)
        val vm = newViewModel(
            bookById = emptyMap(),
            settingsStore = settings
        )
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerSessionHandle(controller = controller)
            )
            val setConfigCallsBefore = controller.setConfigCalls

            vm.dispatch(
                ReaderIntent.UpdateConfig(
                    config = RenderConfig.ReflowText(fontSizeSp = 42f),
                    persist = true
                )
            )
            advanceUntilIdle()

            assertEquals(setConfigCallsBefore, controller.setConfigCalls)
            assertEquals(42f, settings.lastReflow?.fontSizeSp)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `unexpected start exception should not stop later intents`() = runTest {
        val vm = newViewModel(
            bookById = mapOf(1L to bookEntity(1L)),
            runtime = QueueReaderRuntime(readerSessionHandle()),
            resolveSource = { FakeDocumentSource() },
            throwOnUpdateLastOpened = true
        )
        try {
            vm.dispatch(ReaderIntent.Start(bookId = 1L, locatorArg = null))
            awaitStoppedOpening(vm)
            assertFalse(vm.state.value.isOpening)
            assertNotNull(vm.state.value.error)

            vm.dispatch(ReaderIntent.OpenMenu)
            advanceUntilIdle()

            assertEquals(ReaderLayerState.Dock(ReaderDockTab.Menu), vm.state.value.layerState)
        } finally {
            disposeViewModel(vm)
        }
    }

    private suspend fun awaitStoppedOpening(vm: ReaderViewModel) {
        vm.state.first { !it.isOpening }
    }

    private fun attachSession(
        vm: ReaderViewModel,
        bookId: Long,
        handle: ReaderSessionHandle
    ) {
        val sessionField = ReaderViewModel::class.java.getDeclaredField("session")
        sessionField.isAccessible = true
        val sessionCoordinator = sessionField.get(vm)
        val attachMethod = sessionCoordinator.javaClass.getDeclaredMethod(
            "attach",
            Long::class.javaPrimitiveType,
            ReaderSessionHandle::class.java
        )
        attachMethod.isAccessible = true
        attachMethod.invoke(sessionCoordinator, bookId, handle)

        val stateField = ReaderViewModel::class.java.getDeclaredField("stateStore")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateStore = stateField.get(vm) as MutableStateFlow<ReaderUiState>
        stateStore.value = stateStore.value.copy(
            bookId = bookId,
            controller = handle.controller,
            capabilities = handle.document.capabilities,
            currentConfig = handle.controller.state.value.config,
            gestureProfile = if (handle.document.capabilities.fixedLayout) {
                ReaderGestureProfile.FIXED
            } else {
                ReaderGestureProfile.REFLOW
            }
        )
    }

    private fun disposeViewModel(vm: ReaderViewModel) {
        vm.viewModelScope.cancel()
    }

    private fun newViewModel(
        bookById: Map<Long, BookEntity>,
        settingsStore: FakeReaderSettingsStore = FakeReaderSettingsStore(),
        runtime: ReaderRuntime = failingRuntime(),
        resolveSource: (BookRecord) -> DocumentSource? = { null },
        throwOnUpdateLastOpened: Boolean = false
    ): ReaderViewModel {
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(bookById, throwOnUpdateLastOpened),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val progressRepo = ProgressRepo(fakeProgressDao())
        val locatorCodec = object : LocatorCodec {
            override fun encode(locator: Locator): String = "${locator.scheme}:${locator.value}"
            override fun decode(raw: String): Locator? = null
        }

        return ReaderViewModel(
            bookRepo = bookRepo,
            progressRepo = progressRepo,
            settingsStore = settingsStore,
            sourceResolver = object : BookSourceResolver {
                override fun resolve(book: BookRecord) = resolveSource(book)
            },
            locatorCodec = locatorCodec,
            openReaderSession = OpenReaderSession(runtime = runtime, settings = settingsStore),
            observeEffectiveConfig = ObserveEffectiveConfig(settingsStore = settingsStore),
            saveReadingProgress = SaveReadingProgress(progressRepo = progressRepo, locatorCodec = locatorCodec),
            errorMapper = ReaderUiErrorMapper()
        )
    }

    private fun fakeBookDao(
        bookById: Map<Long, BookEntity>,
        throwOnUpdateLastOpened: Boolean
    ): BookDao {
        return proxyInterface(BookDao::class.java) { method, args ->
            when (method.name) {
                "getById" -> bookById[args?.get(0) as Long]
                "observeById" -> flowOf(bookById[args?.get(0) as Long])
                "observeMissing" -> flowOf(emptyList<BookEntity>())
                "observeLibrary" -> flowOf(emptyList<LibraryBookRow>())
                "listAll" -> bookById.values.toList()
                "findByFingerprint", "getByDocumentId" -> null
                "upsert" -> 1L
                "updateLastOpened" -> {
                    if (throwOnUpdateLastOpened) {
                        error("boom")
                    }
                    Unit
                }
                "updateIndexState",
                "updateFavorite",
                "updateReadingStatus",
                "updateSource",
                "updateMetadata",
                "deleteById" -> Unit
                else -> error("Unexpected BookDao call: ${method.name}")
            }
        }
    }

    private fun fakeCollectionDao(): CollectionDao {
        return proxyInterface(CollectionDao::class.java) { method, _ ->
            when (method.name) {
                "observeAll" -> flowOf(emptyList<CollectionEntity>())
                "upsert" -> 1L
                "getById", "getByName" -> null
                "deleteById" -> Unit
                else -> error("Unexpected CollectionDao call: ${method.name}")
            }
        }
    }

    private fun fakeBookCollectionDao(): BookCollectionDao {
        return proxyInterface(BookCollectionDao::class.java) { method, _ ->
            when (method.name) {
                "insert" -> 1L
                "delete", "deleteAllForBook" -> Unit
                "listCollectionIdsForBook" -> emptyList<Long>()
                else -> error("Unexpected BookCollectionDao call: ${method.name}")
            }
        }
    }

    private fun fakeProgressDao(): ProgressDao {
        return proxyInterface(ProgressDao::class.java) { method, _ ->
            when (method.name) {
                "upsert", "deleteByBookId" -> Unit
                "getByBookId" -> null
                "observeByBookId" -> flowOf(null)
                else -> error("Unexpected ProgressDao call: ${method.name}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxyInterface(
        clazz: Class<T>,
        handler: (method: java.lang.reflect.Method, args: Array<Any?>?) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz)
        ) { _, method, args ->
            handler(method, args)
        } as T
    }
}

private class FakeReaderSettingsStore(
    private val emitUpdates: Boolean = true
) : ReaderSettingsStore {
    var lastReflow: RenderConfig.ReflowText? = null
    var lastFixed: RenderConfig.FixedPage? = null
    var lastDisplayPrefs: ReaderDisplayPrefs? = null

    private val reflowState = MutableStateFlow(RenderConfig.ReflowText())
    private val fixedState = MutableStateFlow(RenderConfig.FixedPage())
    private val displayState = MutableStateFlow(ReaderDisplayPrefs())

    override val reflowConfig: Flow<RenderConfig.ReflowText> = reflowState
    override val fixedConfig: Flow<RenderConfig.FixedPage> = fixedState
    override val displayPrefs: Flow<ReaderDisplayPrefs> = displayState

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = reflowState.value

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = fixedState.value

    override suspend fun getDisplayPrefs(): ReaderDisplayPrefs = displayState.value

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) {
        lastReflow = config
        if (emitUpdates) {
            reflowState.value = config
        }
    }

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) {
        lastFixed = config
        if (emitUpdates) {
            fixedState.value = config
        }
    }

    override suspend fun setDisplayPrefs(prefs: ReaderDisplayPrefs) {
        lastDisplayPrefs = prefs
        if (emitUpdates) {
            displayState.value = prefs
        }
    }
}

private fun failingRuntime(): ReaderRuntime {
    return object : ReaderRuntime {
        override suspend fun openDocument(source: DocumentSource, options: OpenOptions): ReaderResult<ReaderDocument> {
            return ReaderResult.Err(ReaderError.NotFound())
        }

        override suspend fun openSession(
            source: DocumentSource,
            options: OpenOptions,
            initialLocator: Locator?,
            initialConfig: RenderConfig?,
            resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
        ): ReaderResult<ReaderSessionHandle> = ReaderResult.Err(ReaderError.NotFound())

        override suspend fun probe(source: DocumentSource, options: OpenOptions): ReaderResult<BookProbeResult> {
            return ReaderResult.Err(ReaderError.NotFound())
        }
    }
}

private fun bookEntity(
    bookId: Long,
    title: String = "Book $bookId",
    format: com.ireader.reader.model.BookFormat = com.ireader.reader.model.BookFormat.TXT
): BookEntity {
    return BookEntity(
        bookId = bookId,
        documentId = "doc-$bookId",
        sourceUri = "content://book/$bookId",
        sourceType = BookSourceType.IMPORTED_COPY,
        format = format,
        fileName = "book-$bookId.txt",
        mimeType = "text/plain",
        fileSizeBytes = 1L,
        lastModifiedEpochMs = 1L,
        canonicalPath = "/books/$bookId.txt",
        fingerprintSha256 = "fingerprint-$bookId",
        title = title,
        author = null,
        language = null,
        identifier = null,
        series = null,
        description = null,
        coverPath = null,
        favorite = false,
        readingStatus = DbReadingStatus.UNREAD,
        indexState = DbIndexState.PENDING,
        indexError = null,
        capabilitiesJson = null,
        addedAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        lastOpenedAtEpochMs = null
    )
}

private class QueueReaderRuntime(
    vararg sessionHandles: ReaderSessionHandle
) : ReaderRuntime {
    private val sessions = ArrayDeque(sessionHandles.toList())

    override suspend fun openDocument(source: DocumentSource, options: OpenOptions): ReaderResult<ReaderDocument> {
        return ReaderResult.Err(ReaderError.Internal("unused"))
    }

    override suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
        initialConfig: RenderConfig?,
        resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
    ): ReaderResult<ReaderSessionHandle> {
        if (sessions.isEmpty()) {
            return ReaderResult.Err(ReaderError.NotFound())
        }
        return ReaderResult.Ok(sessions.removeFirst())
    }

    override suspend fun probe(source: DocumentSource, options: OpenOptions): ReaderResult<BookProbeResult> {
        return ReaderResult.Err(ReaderError.Internal("unused"))
    }
}

private fun readerSessionHandle(
    controller: RecordingReaderController = RecordingReaderController(),
    search: SearchProvider? = null,
    fixedLayout: Boolean = false,
    documentFormat: com.ireader.reader.model.BookFormat = if (fixedLayout) {
        com.ireader.reader.model.BookFormat.PDF
    } else {
        com.ireader.reader.model.BookFormat.TXT
    }
): ReaderSessionHandle {
    val capabilities = DocumentCapabilities(
        reflowable = !fixedLayout,
        fixedLayout = fixedLayout,
        outline = false,
        search = search != null,
        textExtraction = false,
        annotations = false,
        selection = false,
        links = false
    )
    val document = object : ReaderDocument {
        override val id: DocumentId = DocumentId("doc")
        override val format = documentFormat
        override val capabilities = capabilities
        override val openOptions: OpenOptions = OpenOptions()

        override suspend fun metadata(): ReaderResult<com.ireader.reader.model.DocumentMetadata> {
            return ReaderResult.Ok(com.ireader.reader.model.DocumentMetadata())
        }

        override suspend fun createSession(
            initialLocator: Locator?,
            initialConfig: RenderConfig
        ): ReaderResult<ReaderSession> = ReaderResult.Err(ReaderError.Internal("unused"))

        override fun close() = Unit
    }
    val session = object : ReaderSession {
        override val id: SessionId = SessionId("session")
        override val controller: ReaderController = controller
        override val outline: OutlineProvider? = null
        override val search: SearchProvider? = search
        override val text: TextProvider? = null
        override val annotations: AnnotationProvider? = null
        override val resources: ResourceProvider? = null
        override val selection: SelectionProvider? = null
        override val selectionController: SelectionController? = null
        override fun close() = Unit
    }
    return ReaderSessionHandle(document = document, session = session)
}

private class RecordingReaderController(
    initialConfig: RenderConfig = RenderConfig.ReflowText()
) : ReaderController {
    var nextCalls: Int = 0
    var prevCalls: Int = 0
    var setConfigCalls: Int = 0

    private val stateStore = MutableStateFlow(
        RenderState(
            locator = Locator(scheme = "txt.offset", value = "0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            config = initialConfig
        )
    )

    override val state = stateStore
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setTextLayouterFactory(
        factory: com.ireader.reader.api.render.TextLayouterFactory
    ): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        setConfigCalls += 1
        stateStore.value = stateStore.value.copy(config = config)
        return ReaderResult.Ok(Unit)
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Ok(renderPage("render"))

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        nextCalls += 1
        return ReaderResult.Ok(renderPage("next-$nextCalls"))
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        prevCalls += 1
        return ReaderResult.Ok(renderPage("prev-$prevCalls"))
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        stateStore.value = stateStore.value.copy(locator = locator)
        return ReaderResult.Ok(renderPage("goto-${locator.value}", locator))
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        stateStore.value = stateStore.value.copy(progression = Progression(percent))
        return ReaderResult.Ok(renderPage("progress-$percent"))
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override fun close() = Unit

    private fun renderPage(id: String, locator: Locator = stateStore.value.locator): RenderPage {
        return RenderPage(
            id = PageId(id),
            locator = locator,
            content = RenderContent.Text(text = id)
        )
    }
}

private class FakeSearchProvider(
    private val cannedExcerpts: List<String> = emptyList(),
    private val scriptedExcerpts: Map<String, List<String>> = emptyMap()
) : SearchProvider {
    private val hits = MutableSharedFlow<SearchHit>()

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> {
        val excerpts = scriptedExcerpts[query] ?: cannedExcerpts
        if (excerpts.isEmpty()) {
            return hits
        }
        return flow {
            excerpts.forEach { excerpt ->
                emit(searchHit(excerpt))
            }
            awaitCancellation()
        }
    }

    suspend fun emitHit(excerpt: String) {
        hits.emit(searchHit(excerpt))
    }

    private fun searchHit(excerpt: String): SearchHit {
        val locator = Locator(scheme = "txt.offset", value = excerpt)
        return SearchHit(
            range = LocatorRange(start = locator, end = locator),
            excerpt = excerpt,
            sectionTitle = "chapter"
        )
    }
}

private class FakeDocumentSource : DocumentSource {
    override val uri: Uri = Uri.EMPTY
    override val displayName: String? = "book"
    override val mimeType: String? = "text/plain"
    override val sizeBytes: Long? = 1L

    override suspend fun openInputStream() = java.io.ByteArrayInputStream(ByteArray(0))

    override suspend fun openFileDescriptor(mode: String) = null
}
