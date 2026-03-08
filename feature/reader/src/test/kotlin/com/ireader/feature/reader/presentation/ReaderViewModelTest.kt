package com.ireader.feature.reader.presentation

import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.BookRecord
import com.ireader.core.data.book.BookSourceResolver
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.data.reader.ReaderLaunchRepository
import com.ireader.core.data.reader.ReaderPreferencesRepository
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
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.testing.MainDispatcherRule
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
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
import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayoutMeasureResult
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression
import android.net.Uri
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderHandle
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.testkit.fake.DelayedRenderController
import com.ireader.reader.testkit.fake.FakeAnnotationSupport
import com.ireader.reader.testkit.fake.FakeTextBreakPatchSupport
import com.ireader.reader.testkit.fake.QueueReaderRuntime
import com.ireader.reader.testkit.fake.RecordingReaderController
import com.ireader.reader.testkit.fake.readerHandle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
                handle = readerHandle(controller = controller)
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
                handle = readerHandle(controller = controller)
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
    fun `apply text break patch intent should replace page and clear search results`() = runTest {
        val controller = RecordingReaderController()
        val patchSupport = FakeTextBreakPatchSupport(
            page = RenderPage(
                id = PageId("patched-page"),
                locator = Locator(scheme = "txt.stable.anchor", value = "8:0"),
                content = RenderContent.Text(text = "patched text")
            )
        )
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerHandle(
                    controller = controller,
                    textBreakPatchSupport = patchSupport
                )
            )
            setState(
                vm = vm,
                transform = {
                    it.copy(
                        search = it.search.copy(
                            query = "needle",
                            results = listOf(
                                SearchResultItem(
                                    title = null,
                                    excerpt = "needle",
                                    locatorEncoded = "x"
                                )
                            )
                        )
                    )
                }
            )

            vm.dispatch(
                ReaderIntent.ApplyTextBreakPatch(
                    direction = TextBreakPatchDirection.NEXT,
                    state = TextBreakPatchState.SOFT_SPACE
                )
            )
            advanceUntilIdle()

            assertEquals(1, patchSupport.applyCalls)
            assertEquals("patched-page", vm.state.value.page?.id?.value)
            assertTrue(vm.state.value.search.results.isEmpty())
            assertTrue(vm.state.value.supportsTextBreakPatches)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `create annotation should invalidate and rerender current page`() = runTest {
        val controller = RecordingReaderController(requireLayoutBeforeRender = true)
        val annotationSupport = FakeAnnotationSupport(
            selection = SelectionProvider.Selection(
                locator = Locator(scheme = "txt.stable.anchor", value = "0:0"),
                selectedText = "selected text"
            )
        )
        val vm = newViewModel(bookById = emptyMap())
        try {
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerHandle(
                    controller = controller,
                    annotationSupport = annotationSupport
                )
            )
            vm.dispatch(ReaderIntent.LayoutChanged(defaultLayoutConstraints()))
            vm.dispatch(ReaderIntent.TextLayouterFactoryChanged(TestTextLayouterFactory))
            advanceUntilIdle()

            val renderCallsBefore = controller.renderCalls
            val invalidateCallsBefore = controller.invalidateCalls

            vm.dispatch(ReaderIntent.CreateAnnotation)
            advanceUntilIdle()

            assertEquals(1, annotationSupport.createAnnotationCalls)
            assertEquals(1, annotationSupport.clearSelectionCalls)
            assertEquals(invalidateCallsBefore + 1, controller.invalidateCalls)
            assertTrue(controller.renderCalls > renderCallsBefore)
            assertEquals("render", vm.state.value.page?.id?.value)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `open should apply stored layout before late text layouter factory arrives`() = runTest {
        val controller = RecordingReaderController(requireLayoutBeforeRender = true)
        val vm = newViewModel(bookById = emptyMap())
        try {
            vm.dispatch(
                ReaderIntent.LayoutChanged(
                    LayoutConstraints(
                        viewportWidthPx = 1080,
                        viewportHeightPx = 1920,
                        density = 3f,
                        fontScale = 1f
                    )
                )
            )
            advanceUntilIdle()
            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerHandle(controller = controller)
            )

            vm.dispatch(ReaderIntent.TextLayouterFactoryChanged(TestTextLayouterFactory))
            advanceUntilIdle()

            assertTrue(controller.setLayoutConstraintsCalls > 0)
            assertTrue(controller.renderCalls > 0)
            assertEquals(null, vm.state.value.error)
            assertEquals("render", vm.state.value.page?.id?.value)
        } finally {
            disposeViewModel(vm)
        }
    }

    @Test
    fun `stale render result should be dropped after session replacement`() = runTest {
        val delayedController = DelayedRenderController(
            delayMs = 100L,
            pageId = "stale-page"
        )
        val freshController = RecordingReaderController(requireLayoutBeforeRender = true)
        val vm = newViewModel(bookById = emptyMap())
        val constraints = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )
        try {
            vm.dispatch(ReaderIntent.LayoutChanged(constraints))
            advanceUntilIdle()

            attachSession(
                vm = vm,
                bookId = 1L,
                handle = readerHandle(controller = delayedController)
            )
            vm.dispatch(ReaderIntent.TextLayouterFactoryChanged(TestTextLayouterFactory))
            runCurrent()

            attachSession(
                vm = vm,
                bookId = 2L,
                handle = readerHandle(controller = freshController)
            )
            vm.dispatch(ReaderIntent.LayoutChanged(constraints))
            vm.dispatch(ReaderIntent.TextLayouterFactoryChanged(TestTextLayouterFactory))
            advanceUntilIdle()

            assertEquals("render", vm.state.value.page?.id?.value)
            assertTrue(delayedController.renderCalls > 0)
            assertTrue(freshController.renderCalls > 0)
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
                handle = readerHandle(
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
                handle = readerHandle(
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
                handle = readerHandle(controller = controller)
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
                handle = readerHandle(controller = controller)
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
            runtime = QueueReaderRuntime(readerHandle()),
            resolveSource = { FakeDocumentSource() },
            throwOnUpdateLastOpened = true
        )
        try {
            vm.dispatch(ReaderIntent.Start(bookId = 1L, locatorArg = null))
            awaitStoppedOpening(vm)
            assertFalse(vm.state.value.isOpening)
            assertEquals(null, vm.state.value.error)

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

    private fun defaultLayoutConstraints(): LayoutConstraints {
        return LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )
    }

    private fun attachSession(
        vm: ReaderViewModel,
        bookId: Long,
        handle: ReaderHandle
    ) {
        val sessionField = ReaderViewModel::class.java.getDeclaredField("sessionFacade")
        sessionField.isAccessible = true
        val sessionCoordinator = sessionField.get(vm)
        val openEpochField = ReaderViewModel::class.java.getDeclaredField("openEpoch")
        openEpochField.isAccessible = true
        val attachMethod = sessionCoordinator.javaClass.getDeclaredMethod(
            "attach",
            Long::class.javaPrimitiveType,
            ReaderHandle::class.java,
            Long::class.javaPrimitiveType
        )
        attachMethod.isAccessible = true
        attachMethod.invoke(sessionCoordinator, bookId, handle, openEpochField.getLong(vm))

        val stateField = ReaderViewModel::class.java.getDeclaredField("stateStore")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateStore = stateField.get(vm) as MutableStateFlow<ReaderUiState>
        stateStore.value = stateStore.value.copy(
            bookId = bookId,
            resources = handle.resources,
            capabilities = handle.capabilities,
            renderState = handle.state.value,
            currentConfig = handle.state.value.config,
            gestureProfile = if (handle.capabilities.fixedLayout) {
                ReaderGestureProfile.FIXED
            } else {
                ReaderGestureProfile.REFLOW
            },
            supportsTextBreakPatches = handle.supportsTextBreakPatches
        )
    }

    private fun setState(
        vm: ReaderViewModel,
        transform: (ReaderUiState) -> ReaderUiState
    ) {
        val stateField = ReaderViewModel::class.java.getDeclaredField("stateStore")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateStore = stateField.get(vm) as MutableStateFlow<ReaderUiState>
        stateStore.value = transform(stateStore.value)
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
        val preferencesRepository = ReaderPreferencesRepository(settingsStore)
        val locatorCodec = object : LocatorCodec {
            override fun encode(locator: Locator): String = "${locator.scheme}:${locator.value}"
            override fun decode(raw: String): Locator? = null
        }
        val launchRepository = ReaderLaunchRepository(
            bookRepo = bookRepo,
            progressRepo = progressRepo,
            locatorCodec = locatorCodec,
            sourceResolver = object : BookSourceResolver {
                override fun resolve(book: BookRecord) = resolveSource(book)
            },
            preferencesRepository = preferencesRepository,
            runtime = runtime
        )

        return ReaderViewModel(
            locatorCodec = locatorCodec,
            launchRepository = launchRepository,
            preferencesRepository = preferencesRepository,
            observeEffectiveConfig = ObserveEffectiveConfig(preferencesRepository = preferencesRepository),
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
        override suspend fun openDocument(
            source: DocumentSource,
            options: OpenOptions
        ): ReaderResult<com.ireader.reader.api.engine.ReaderDocument> {
            return ReaderResult.Err(ReaderError.NotFound())
        }

        override suspend fun openSession(
            source: DocumentSource,
            options: OpenOptions,
            initialLocator: Locator?,
            initialConfig: RenderConfig?,
            resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
        ): ReaderResult<ReaderHandle> = ReaderResult.Err(ReaderError.NotFound())

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

private object TestTextLayouterFactory : TextLayouterFactory {
    override val environmentKey: String = "reader-vm-test"

    override fun create(cacheSize: Int): TextLayouter {
        return object : TextLayouter {
            override fun measure(
                text: CharSequence,
                input: TextLayoutInput
            ): TextLayoutMeasureResult {
                return TextLayoutMeasureResult(
                    endChar = text.length,
                    lineCount = 1,
                    lastVisibleLine = 0
                )
            }
        }
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
        val locator = Locator(scheme = "epub.cfi", value = excerpt)
        return SearchHit(
            range = LocatorRange(start = locator, end = locator),
            excerpt = excerpt,
            sectionTitle = "chapter"
        )
    }
}

private class FakeDocumentSource : DocumentSource {
    override val uri: Uri
        get() = throw UnsupportedOperationException("unused")
    override val displayName: String? = "book"
    override val mimeType: String? = "text/plain"
    override val sizeBytes: Long? = 1L

    override suspend fun openInputStream() = java.io.ByteArrayInputStream(ByteArray(0))

    override suspend fun openFileDescriptor(mode: String) = null
}
