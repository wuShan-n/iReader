package com.ireader.engines.epub.internal.controller

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentManager
import com.ireader.core.common.android.surface.FragmentRenderSurface
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.epub.internal.locator.ReadiumLocatorExtras
import com.ireader.engines.epub.internal.locator.ReadiumLocatorSchemes
import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.engines.epub.internal.locator.toReadiumLocatorOrNull
import com.ireader.engines.epub.internal.render.EpubDecorationsHost
import com.ireader.engines.epub.internal.render.toEpubPreferences
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression

internal class EpubController(
    private val publication: Publication,
    private val sessionTag: String,
    initialLocator: Locator?,
    initialConfig: RenderConfig,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ReaderController {

    override val state: StateFlow<RenderState> get() = _state
    override val events: Flow<ReaderEvent> get() = _events

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)

    private val sanitizedInitialConfig: RenderConfig = sanitizeConfig(initialConfig)
    private val sanitizedInitialLocator: Locator? =
        initialLocator?.takeIf { it.scheme == ReadiumLocatorSchemes.READIUM_LOCATOR_JSON }

    private val initialAppLocator: Locator = sanitizedInitialLocator ?: fallbackLocator()
    private val initialProgression: Progression = progressionFromLocator(initialAppLocator)

    private val _state = MutableStateFlow(
        RenderState(
            locator = initialAppLocator,
            progression = initialProgression,
            nav = navFromPercent(initialProgression.percent),
            titleInView = initialAppLocator.extras[ReadiumLocatorExtras.TITLE],
            config = sanitizedInitialConfig
        )
    )

    private val navMutex = Mutex()
    private val fragmentRef = AtomicReference<EpubNavigatorFragment?>(null)

    internal val decorationsHost = EpubDecorationsHost(mainDispatcher = mainDispatcher)

    private var surface: FragmentRenderSurface? = null

    /**
     * 用于 surface 尚未绑定时保存目标位置；也用于 unbind -> rebind 的“恢复当前位置”。
     * 仅保存 EPUB 自己支持的 locator scheme。
     */
    private var pendingLocator: Locator? = sanitizedInitialLocator

    @Suppress("unused")
    private var layoutConstraints: LayoutConstraints? = null

    private var locatorCollectionJob: Job? = null
    private val backgroundTapListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            return emitBackgroundTap(event)
        }
    }

    override suspend fun bindSurface(surface: com.ireader.reader.api.render.RenderSurface): ReaderResult<Unit> {
        val fragmentSurface = surface as? FragmentRenderSurface
            ?: return ReaderResult.Err(
                ReaderError.Internal("EPUB controller requires FragmentRenderSurface")
            )

        return navMutex.withLock {
            withContext(mainDispatcher) {
                var createdHere = false
                var fragment: EpubNavigatorFragment? = null
                val fm = fragmentSurface.fragmentManager

                try {
                    if (fm.isStateSaved) {
                        return@withContext ReaderResult.Err(
                            ReaderError.Internal("FragmentManager state is already saved")
                        )
                    }

                    val fragmentTag = navigatorFragmentTag()
                    val existing = fm.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment

                    fragment = existing ?: createAndAttachNavigator(
                        fm = fm,
                        containerId = fragmentSurface.containerViewId,
                        tag = fragmentTag
                    ).also { createdHere = true }

                    this@EpubController.surface = fragmentSurface
                    fragmentRef.set(fragment)

                    fragment.removeInputListener(backgroundTapListener)
                    fragment.addInputListener(backgroundTapListener)
                    decorationsHost.bind(fragment)
                    applyConfig(fragment, _state.value.config)
                    collectCurrentLocator(fragment)

                    // 只有“复用现有 fragment”时才需要再 go 一次；
                    // 新建 fragment 已经在 factory 里用 initialLocator 初始化过。
                    if (existing != null) {
                        pendingLocator?.toReadiumLocatorOrNull()?.let { locator ->
                            fragment.go(locator, animated = false)
                        }
                    }
                    pendingLocator = null

                    ReaderResult.Ok(Unit)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t

                    // 回滚：尽量别留下半绑定状态
                    locatorCollectionJob?.cancel()
                    locatorCollectionJob = null
                    decorationsHost.unbind()
                    fragmentRef.set(null)
                    this@EpubController.surface = null

                    if (createdHere) {
                        runCatching { removeNavigatorFragment(fm = fm, fragment = fragment) }
                    }

                    ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
                }
            }
        }
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return navMutex.withLock {
            withContext(mainDispatcher) {
                try {
                    // 记录当前位置，以便后续 rebind（如果发生）。
                    pendingLocator = _state.value.locator
                        .takeIf { it.scheme == ReadiumLocatorSchemes.READIUM_LOCATOR_JSON }

                    locatorCollectionJob?.cancel()
                    locatorCollectionJob = null

                    decorationsHost.unbind()

                    val fragmentSurface = surface
                    surface = null

                    val fragment = fragmentRef.getAndSet(null)
                    fragment?.removeInputListener(backgroundTapListener)
                    if (fragmentSurface != null) {
                        removeNavigatorFragment(
                            fm = fragmentSurface.fragmentManager,
                            fragment = fragment
                        )
                    }

                    ReaderResult.Ok(Unit)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
                }
            }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        layoutConstraints = constraints
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setTextLayouterFactory(factory: TextLayouterFactory): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val sanitized = sanitizeConfig(config)

        return navMutex.withLock {
            _state.update { it.copy(config = sanitized) }

            val fragment = fragmentRef.get() ?: return@withLock ReaderResult.Ok(Unit)

            withContext(mainDispatcher) {
                try {
                    applyConfig(fragment, sanitized)
                    ReaderResult.Ok(Unit)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
                }
            }
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val page = currentEmbeddedPage()
        _events.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        return ReaderResult.Ok(page)
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val fragment = fragmentRef.get()
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val likelyBoundary = !_state.value.nav.canGoNext
            val previous = _state.value.locator.value

            try {
                withContext(mainDispatcher) { fragment.goForward(animated = true) }
                if (!likelyBoundary) {
                    awaitLocatorChange(previous)
                }
                render(policy)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
            }
        }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val fragment = fragmentRef.get()
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val likelyBoundary = !_state.value.nav.canGoPrev
            val previous = _state.value.locator.value

            try {
                withContext(mainDispatcher) { fragment.goBackward(animated = true) }
                if (!likelyBoundary) {
                    awaitLocatorChange(previous)
                }
                render(policy)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
            }
        }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            if (_state.value.locator.value == locator.value) {
                return@withLock render(policy)
            }

            // 无论是否绑定 surface，都先校验 scheme（避免写入不支持的 locator 到 state/pending）
            val readiumLocator = locator.toReadiumLocatorOrNull()
                ?: return@withLock ReaderResult.Err(
                    ReaderError.CorruptOrInvalid("Unsupported EPUB locator scheme: ${locator.scheme}")
                )

            val fragment = fragmentRef.get()
            if (fragment == null) {
                pendingLocator = locator
                _state.update { it.copy(locator = locator) }
                return@withLock render(policy)
            }

            val previous = _state.value.locator.value
            try {
                withContext(mainDispatcher) { fragment.go(readiumLocator, animated = false) }
                awaitLocatorChange(previous)
                render(policy)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
            }
        }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val fragment = fragmentRef.get()
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val clamped = percent.coerceIn(0.0, 1.0)
            val target = publication.locateProgression(clamped)
                ?: return@withLock ReaderResult.Err(
                    ReaderError.Internal("Cannot locate progression=$clamped")
                )

            val previous = _state.value.locator.value
            try {
                withContext(mainDispatcher) { fragment.go(target, animated = false) }
                awaitLocatorChange(previous)
                render(policy)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
            }
        }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override fun close() {
        locatorCollectionJob?.cancel()
        locatorCollectionJob = null

        decorationsHost.unbind()

        val currentSurface = surface
        val fragment = fragmentRef.getAndSet(null)
        surface = null
        fragment?.removeInputListener(backgroundTapListener)

        if (currentSurface != null) {
            mainHandler.post {
                removeNavigatorFragment(
                    fm = currentSurface.fragmentManager,
                    fragment = fragment
                )
            }
        }

        scope.coroutineContext[Job]?.cancel()
    }

    internal fun navigatorOrNull(): EpubNavigatorFragment? = fragmentRef.get()

    private fun sanitizeConfig(config: RenderConfig): RenderConfig =
        when (config) {
            is RenderConfig.ReflowText -> config.sanitized()
            is RenderConfig.FixedPage -> config.sanitized()
        }

    private fun progressionFromLocator(locator: Locator): Progression {
        val percent = locator.extras[ReadiumLocatorExtras.TOTAL_PROGRESSION]?.toDoubleOrNull()
            ?: locator.extras[ReadiumLocatorExtras.PROGRESSION]?.toDoubleOrNull()
            ?: 0.0
        val pos = locator.extras[ReadiumLocatorExtras.POSITION]?.toIntOrNull()
        return Progression(
            percent = percent.coerceIn(0.0, 1.0),
            label = pos?.toString(),
            current = pos
        )
    }

    private fun navFromPercent(percent: Double): NavigationAvailability =
        NavigationAvailability(
            canGoPrev = percent > NAV_EPSILON,
            canGoNext = percent < (1.0 - NAV_EPSILON)
        )

    private fun fallbackLocator(): Locator {
        val first = publication.readingOrder.firstOrNull()
            ?.let(publication::locatorFromLink)
            ?.toAppLocator()

        return first ?: Locator(
            scheme = ReadiumLocatorSchemes.READIUM_LOCATOR_JSON,
            value = "{}"
        )
    }

    private fun navigatorFragmentTag(): String = "epub-navigator-$sessionTag"

    private fun createAndAttachNavigator(
        fm: FragmentManager,
        containerId: Int,
        tag: String
    ): EpubNavigatorFragment {
        val initial = pendingLocator?.toReadiumLocatorOrNull()
        val preferences = _state.value.config.toEpubPreferences()

        val fragmentFactory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initial,
            initialPreferences = preferences
        )

        val fragment = fragmentFactory.instantiate(
            publication::class.java.classLoader ?: javaClass.classLoader!!,
            EpubNavigatorFragment::class.java.name
        ) as EpubNavigatorFragment

        fm.beginTransaction()
            .replace(containerId, fragment, tag)
            .commitNow()

        return fragment
    }

    private fun applyConfig(fragment: EpubNavigatorFragment, config: RenderConfig) {
        fragment.submitPreferences(config.toEpubPreferences())
    }

    private fun collectCurrentLocator(fragment: EpubNavigatorFragment) {
        locatorCollectionJob?.cancel()
        locatorCollectionJob = scope.launch {
            fragment.currentLocator.collect { locator ->
                val appLocator = locator.toAppLocator()

                val percent = locator.locations.totalProgression
                    ?: locator.locations.progression
                    ?: 0.0
                val position = locator.locations.position

                _state.update { current ->
                    current.copy(
                        locator = appLocator,
                        progression = Progression(
                            percent = percent.coerceIn(0.0, 1.0),
                            label = position?.toString(),
                            current = position
                        ),
                        nav = navFromPercent(percent),
                        titleInView = locator.title ?: appLocator.extras[ReadiumLocatorExtras.TITLE]
                    )
                }

                _events.tryEmit(ReaderEvent.PageChanged(appLocator))
            }
        }
    }

    private fun currentEmbeddedPage(): RenderPage {
        val current = _state.value.locator
        return RenderPage(
            id = PageId("epub:${current.value.hashCode()}"),
            locator = current,
            content = RenderContent.Embedded,
            links = emptyList(),
            decorations = emptyList(),
            metrics = null
        )
    }

    private fun emitBackgroundTap(event: TapEvent): Boolean {
        val viewport = resolveTapViewport() ?: return false
        val maxX = viewport.first.toFloat().coerceAtLeast(1f)
        val maxY = viewport.second.toFloat().coerceAtLeast(1f)
        _events.tryEmit(
            ReaderEvent.BackgroundTap(
                xPx = event.point.x.coerceIn(0f, maxX),
                yPx = event.point.y.coerceIn(0f, maxY),
                viewportWidthPx = viewport.first,
                viewportHeightPx = viewport.second
            )
        )
        return true
    }

    private fun resolveTapViewport(): Pair<Int, Int>? {
        layoutConstraints?.let { constraints ->
            val width = constraints.viewportWidthPx
            val height = constraints.viewportHeightPx
            if (width > 0 && height > 0) {
                return width to height
            }
        }

        val fragment = fragmentRef.get() ?: return null
        val publicationView = runCatching { fragment.publicationView }.getOrNull()
        val publicationWidth = publicationView?.width ?: 0
        val publicationHeight = publicationView?.height ?: 0
        if (publicationWidth > 0 && publicationHeight > 0) {
            return publicationWidth to publicationHeight
        }

        val rootWidth = fragment.view?.width ?: 0
        val rootHeight = fragment.view?.height ?: 0
        if (rootWidth > 0 && rootHeight > 0) {
            return rootWidth to rootHeight
        }

        return null
    }

    private suspend fun awaitLocatorChange(previousLocatorValue: String): Boolean {
        return withTimeoutOrNull(NAVIGATION_WAIT_TIMEOUT_MS) {
            state.filter { it.locator.value != previousLocatorValue }.first()
        } != null
    }

    private fun removeNavigatorFragment(
        fm: FragmentManager,
        fragment: EpubNavigatorFragment?
    ) {
        if (fm.isDestroyed) return

        val target = fragment
            ?: (fm.findFragmentByTag(navigatorFragmentTag()) as? EpubNavigatorFragment)
            ?: return

        if (fm.isStateSaved) {
            runCatching {
                fm.beginTransaction()
                    .remove(target)
                    .commitAllowingStateLoss()
            }
            return
        }

        runCatching {
            fm.beginTransaction()
                .remove(target)
                .commitNowAllowingStateLoss()
        }.onFailure {
            runCatching {
                fm.beginTransaction()
                    .remove(target)
                    .commitAllowingStateLoss()
            }
        }
    }

    private companion object {
        private const val NAVIGATION_WAIT_TIMEOUT_MS = 400L
        private const val NAV_EPSILON = 0.0005
    }
}
