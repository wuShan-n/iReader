package com.ireader.engines.epub.internal.controller

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentManager
import com.ireader.core.common.android.surface.FragmentRenderSurface
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
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression

internal class EpubController(
    private val publication: Publication,
    private val sessionTag: String,
    initialLocator: Locator?,
    initialConfig: RenderConfig,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ReaderController {

    override val state: StateFlow<RenderState>
        get() = _state

    override val events: Flow<ReaderEvent>
        get() = _events

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    private val _state = MutableStateFlow(
        RenderState(
            locator = initialLocator ?: fallbackLocator(),
            progression = Progression(percent = 0.0),
            nav = NavigationAvailability(canGoPrev = false, canGoNext = false),
            titleInView = null,
            config = initialConfig
        )
    )
    private val navMutex = Mutex()
    private val fragmentRef = AtomicReference<EpubNavigatorFragment?>(null)
    internal val decorationsHost = EpubDecorationsHost(mainDispatcher = mainDispatcher)
    private var surface: FragmentRenderSurface? = null
    private var pendingLocator: Locator? = initialLocator
    private var layoutConstraints: LayoutConstraints? = null
    private var locatorCollectionJob: Job? = null

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> {
        val fragmentSurface = surface as? FragmentRenderSurface
            ?: return ReaderResult.Err(
                ReaderError.Internal("EPUB controller requires FragmentRenderSurface")
            )

        return withContext(mainDispatcher) {
            try {
                this@EpubController.surface = fragmentSurface
                val fm = fragmentSurface.fragmentManager

                if (fm.isStateSaved) {
                    return@withContext ReaderResult.Err(
                        ReaderError.Internal("FragmentManager state is already saved")
                    )
                }

                val fragmentTag = navigatorFragmentTag()
                val fragment = (fm.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment)
                    ?: createAndAttachNavigator(
                        fm = fm,
                        containerId = fragmentSurface.containerViewId,
                        tag = fragmentTag
                    )

                fragmentRef.set(fragment)
                decorationsHost.bind(fragment)
                applyConfig(fragment, _state.value.config)
                collectCurrentLocator(fragment)

                pendingLocator?.toReadiumLocatorOrNull()?.let { locator ->
                    fragment.go(locator, animated = false)
                    pendingLocator = null
                }

                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(mapThrowable(t))
            }
        }
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return withContext(mainDispatcher) {
            try {
                locatorCollectionJob?.cancel()
                locatorCollectionJob = null
                decorationsHost.unbind()
                surface = null
                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(mapThrowable(t))
            }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        layoutConstraints = constraints
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        _state.value = _state.value.copy(config = config)
        val fragment = fragmentRef.get() ?: return ReaderResult.Ok(Unit)

        return withContext(mainDispatcher) {
            try {
                applyConfig(fragment, config)
                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(mapThrowable(t))
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
                ?: return ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val previous = _state.value.locator.value
            withContext(mainDispatcher) { fragment.goForward(animated = true) }
            awaitLocatorChange(previous)
            render(policy)
        }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val fragment = fragmentRef.get()
                ?: return ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val previous = _state.value.locator.value
            withContext(mainDispatcher) { fragment.goBackward(animated = true) }
            awaitLocatorChange(previous)
            render(policy)
        }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val fragment = fragmentRef.get()
            if (fragment == null) {
                pendingLocator = locator
                _state.value = _state.value.copy(locator = locator)
                return render(policy)
            }

            val readiumLocator = locator.toReadiumLocatorOrNull()
                ?: return ReaderResult.Err(
                    ReaderError.CorruptOrInvalid("Unsupported EPUB locator scheme: ${locator.scheme}")
                )

            val previous = _state.value.locator.value
            withContext(mainDispatcher) { fragment.go(readiumLocator, animated = false) }
            awaitLocatorChange(previous)
            render(policy)
        }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val fragment = fragmentRef.get()
                ?: return ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val target = publication.locateProgression(percent.coerceIn(0.0, 1.0))
                ?: return ReaderResult.Err(
                    ReaderError.Internal("Cannot locate progression=${percent.coerceIn(0.0, 1.0)}")
                )

            val previous = _state.value.locator.value
            withContext(mainDispatcher) { fragment.go(target, animated = false) }
            awaitLocatorChange(previous)
            render(policy)
        }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override fun close() {
        locatorCollectionJob?.cancel()
        locatorCollectionJob = null
        decorationsHost.unbind()

        val currentSurface = surface
        val fragment = fragmentRef.getAndSet(null)
        if (currentSurface != null && fragment != null) {
            Handler(Looper.getMainLooper()).post {
                val fm = currentSurface.fragmentManager
                if (!fm.isStateSaved) {
                    runCatching {
                        fm.beginTransaction()
                            .remove(fragment)
                            .commitNowAllowingStateLoss()
                    }
                }
            }
        }

        scope.coroutineContext[Job]?.cancel()
    }

    internal fun navigatorOrNull(): EpubNavigatorFragment? = fragmentRef.get()

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
                val progression = locator.locations.totalProgression
                    ?: locator.locations.progression
                    ?: 0.0
                val position = locator.locations.position

                _state.value = _state.value.copy(
                    locator = appLocator,
                    progression = Progression(
                        percent = progression.coerceIn(0.0, 1.0),
                        label = position?.toString(),
                        current = position
                    ),
                    nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
                    titleInView = locator.title ?: appLocator.extras["title"]
                )

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

    private suspend fun awaitLocatorChange(previousLocatorValue: String) {
        withTimeoutOrNull(1_500L) {
            state.filter { it.locator.value != previousLocatorValue }.first()
        }
    }

    private fun mapThrowable(t: Throwable): ReaderError =
        when (t) {
            is ReaderError -> t
            is SecurityException -> ReaderError.PermissionDenied(cause = t)
            is FileNotFoundException -> ReaderError.NotFound(cause = t)
            is ZipException -> ReaderError.CorruptOrInvalid(cause = t)
            is IOException -> ReaderError.Io(cause = t)
            else -> ReaderError.Internal(cause = t)
        }
}
