# epub 模块 .kt 代码（不含测试）

## engines/epub\src\main\kotlin\com\ireader\engines\epub\di\EpubEngineModule.kt

```kotlin
package com.ireader.engines.epub.di

import android.content.Context
import com.ireader.engines.epub.EpubEngine
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.provider.AnnotationStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EpubEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun provideEpubEngine(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): ReaderEngine {
        return EpubEngine(
            context = context,
            annotationStore = annotationStore
        )
    }
}

```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\EpubEngine.kt

```kotlin
package com.ireader.engines.epub

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.epub.internal.open.EpubOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.BookFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubEngine(
    context: Context,
    private val annotationStore: AnnotationStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReaderEngine {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.EPUB)

    private val opener = EpubOpener(
        context = context,
        annotationStore = annotationStore
    )

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        if (source.uri.toString().isBlank()) {
            return@withContext ReaderResult.Err(
                ReaderError.NotFound("EPUB source uri is empty")
            )
        }

        opener.open(source, options)
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\controller\EpubController.kt

```kotlin
package com.ireader.engines.epub.internal.controller

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentManager
import com.ireader.core.common.android.surface.FragmentRenderSurface
import com.ireader.engines.common.android.error.toReaderError
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
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import java.util.concurrent.atomic.AtomicReference
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
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
            }
        }
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return withContext(mainDispatcher) {
            try {
                locatorCollectionJob?.cancel()
                locatorCollectionJob = null
                decorationsHost.unbind()
                surface?.let { fragmentSurface ->
                    val fragment = fragmentRef.getAndSet(null)
                    removeNavigatorFragment(
                        fm = fragmentSurface.fragmentManager,
                        fragment = fragment
                    )
                } ?: fragmentRef.set(null)
                surface = null
                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
            }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        layoutConstraints = constraints
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val sanitized = when (config) {
            is RenderConfig.ReflowText -> config.sanitized()
            is RenderConfig.FixedPage -> config.sanitized()
        }
        _state.value = _state.value.copy(config = sanitized)
        val fragment = fragmentRef.get() ?: return ReaderResult.Ok(Unit)

        return withContext(mainDispatcher) {
            try {
                applyConfig(fragment, sanitized)
                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
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
            val likelyBoundary = !_state.value.nav.canGoNext
            val fragment = fragmentRef.get()
                ?: return ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val previous = _state.value.locator.value
            withContext(mainDispatcher) { fragment.goForward(animated = true) }
            if (!likelyBoundary) {
                awaitLocatorChange(previous)
            }
            render(policy)
        }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            val likelyBoundary = !_state.value.nav.canGoPrev
            val fragment = fragmentRef.get()
                ?: return ReaderResult.Err(ReaderError.Internal("EPUB surface is not bound"))

            val previous = _state.value.locator.value
            withContext(mainDispatcher) { fragment.goBackward(animated = true) }
            if (!likelyBoundary) {
                awaitLocatorChange(previous)
            }
            render(policy)
        }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> =
        navMutex.withLock {
            if (_state.value.locator.value == locator.value) {
                return render(policy)
            }
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
                removeNavigatorFragment(
                    fm = currentSurface.fragmentManager,
                    fragment = fragment
                )
            }
        } else if (currentSurface != null) {
            Handler(Looper.getMainLooper()).post {
                removeNavigatorFragment(
                    fm = currentSurface.fragmentManager,
                    fragment = null
                )
            }
        }
        surface = null

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
                    nav = NavigationAvailability(
                        canGoPrev = progression > NAV_EPSILON,
                        canGoNext = progression < (1.0 - NAV_EPSILON)
                    ),
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

    private suspend fun awaitLocatorChange(previousLocatorValue: String): Boolean {
        return withTimeoutOrNull(NAVIGATION_WAIT_TIMEOUT_MS) {
            state.filter { it.locator.value != previousLocatorValue }.first()
        } != null
    }

    private fun removeNavigatorFragment(
        fm: FragmentManager,
        fragment: EpubNavigatorFragment?
    ) {
        val target = fragment ?: (fm.findFragmentByTag(navigatorFragmentTag()) as? EpubNavigatorFragment)
        if (target == null || fm.isDestroyed) {
            return
        }

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
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\locator\ReadiumLocatorMapper.kt

```kotlin
package com.ireader.engines.epub.internal.locator

import com.ireader.reader.model.Locator
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator as ReadiumLocator

internal object ReadiumLocatorSchemes {
    const val READIUM_LOCATOR_JSON = "readium.locator.json"
}

internal fun ReadiumLocator.toAppLocator(): Locator =
    Locator(
        scheme = ReadiumLocatorSchemes.READIUM_LOCATOR_JSON,
        value = toJSON().toString(),
        extras = buildMap {
            put("href", href.toString())
            locations.progression?.let { put("progression", it.toString()) }
            locations.totalProgression?.let { put("totalProgression", it.toString()) }
            locations.position?.let { put("position", it.toString()) }
            title?.let { put("title", it) }
            locations.fragments.firstOrNull()?.let { put("fragment", it) }
        }
    )

internal fun Locator.toReadiumLocatorOrNull(): ReadiumLocator? {
    if (scheme != ReadiumLocatorSchemes.READIUM_LOCATOR_JSON) {
        return null
    }

    return runCatching {
        ReadiumLocator.fromJSON(JSONObject(value))
    }.getOrNull()
}

internal fun Locator.withReadiumFragments(fragments: List<String>): Locator {
    if (scheme != ReadiumLocatorSchemes.READIUM_LOCATOR_JSON) {
        return this
    }
    val normalized = fragments.map { it.trim() }.filter { it.isNotEmpty() }
    return runCatching {
        val root = JSONObject(value)
        val locations = root.optJSONObject("locations") ?: JSONObject().also { root.put("locations", it) }
        val fragmentArray = JSONArray()
        normalized.forEach(fragmentArray::put)
        locations.put("fragments", fragmentArray)
        copy(
            value = root.toString(),
            extras = buildMap {
                putAll(this@withReadiumFragments.extras)
                val first = normalized.firstOrNull()
                if (first == null) {
                    remove("fragment")
                } else {
                    put("fragment", first)
                }
            }
        )
    }.getOrElse { this }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\open\EpubDocument.kt

```kotlin
package com.ireader.engines.epub.internal.open

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.epub.internal.session.EpubSession
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.util.UUID
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset

internal class EpubDocument(
    override val id: DocumentId,
    override val format: BookFormat,
    override val capabilities: DocumentCapabilities,
    override val openOptions: OpenOptions,
    internal val publication: Publication,
    private val asset: Asset,
    private val annotationStore: AnnotationStore?
) : ReaderDocument {

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        return try {
            val md = publication.metadata
            ReaderResult.Ok(
                DocumentMetadata(
                    title = md.title,
                    author = md.authors.firstOrNull()?.name,
                    language = md.languages.firstOrNull(),
                    identifier = md.identifier
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
        }
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        return try {
            val sessionId = SessionId(UUID.randomUUID().toString())
            ReaderResult.Ok(
                EpubSession.create(
                    id = sessionId,
                    documentId = id,
                    publication = publication,
                    initialLocator = initialLocator,
                    initialConfig = initialConfig,
                    annotationStore = annotationStore
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
        }
    }

    override fun close() {
        runCatching { publication.close() }
        runCatching { asset.close() }
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\open\EpubLayoutDetector.kt

```kotlin
package com.ireader.engines.epub.internal.open

import java.util.Locale
import org.readium.r2.shared.publication.Publication

internal fun Publication.isFixedLayoutPublication(): Boolean {
    return detectEpubFixedLayout(
        metadataOther = metadata.otherMetadata,
        readingOrderLayoutHints = readingOrder.map { link ->
            link.properties["layout"] as? String
        }
    )
}

internal fun detectEpubFixedLayout(
    metadataOther: Map<String, Any>,
    readingOrderLayoutHints: List<String?>
): Boolean {
    val metadataHint = metadataLayoutHint(metadataOther)
    if (metadataHint != null) {
        return metadataHint == LAYOUT_FIXED || metadataHint == LAYOUT_PRE_PAGINATED
    }

    if (readingOrderLayoutHints.isEmpty()) {
        return false
    }

    var hasFixedResource = false
    for (raw in readingOrderLayoutHints) {
        when (normalizeLayout(raw)) {
            LAYOUT_FIXED,
            LAYOUT_PRE_PAGINATED -> hasFixedResource = true
            LAYOUT_REFLOWABLE -> return false
            null -> return false
            else -> return false
        }
    }
    return hasFixedResource
}

private fun metadataLayoutHint(metadataOther: Map<String, Any>): String? {
    val presentation = metadataOther["presentation"] as? Map<*, *>
    val presentationLayout = presentation?.get("layout") as? String
    val legacyRenditionLayout = metadataOther["rendition:layout"] as? String
    val legacyLayout = metadataOther["layout"] as? String
    return normalizeLayout(presentationLayout ?: legacyRenditionLayout ?: legacyLayout)
}

private fun normalizeLayout(raw: String?): String? {
    return raw?.trim()?.lowercase(Locale.ROOT)
}

private const val LAYOUT_FIXED = "fixed"
private const val LAYOUT_PRE_PAGINATED = "pre-paginated"
private const val LAYOUT_REFLOWABLE = "reflowable"
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\open\EpubOpener.kt

```kotlin
package com.ireader.engines.epub.internal.open

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.android.id.SourceDocumentIds
import com.ireader.engines.epub.internal.readium.ReadiumEpubToolkit
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.toAbsoluteUrl

internal class EpubOpener(
    context: Context,
    private val annotationStore: AnnotationStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val toolkit: ReadiumEpubToolkit = ReadiumEpubToolkit(context)
) {

    @OptIn(ExperimentalReadiumApi::class)
    suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<EpubDocument> = withContext(ioDispatcher) {
        try {
            val url = source.uri.toAbsoluteUrl()
                ?: return@withContext ReaderResult.Err(
                    ReaderError.CorruptOrInvalid("Cannot convert source uri to AbsoluteUrl: ${source.uri}")
                )

            val mediaType = source.mimeType
                ?.let { MediaType(it) }
                ?: MediaType.EPUB

            val asset = toolkit.assetRetriever
                .retrieve(url, mediaType)
                .getOrElse { retrieveError ->
                    return@withContext ReaderResult.Err(
                        ReaderError.Io("Failed to retrieve EPUB asset: ${retrieveError.message}")
                    )
                }

            val publication: Publication = toolkit.publicationOpener
                .open(
                    asset = asset,
                    credentials = options.password,
                    allowUserInteraction = true
                )
                .getOrElse { openError ->
                    runCatching { asset.close() }
                    return@withContext ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Failed to open EPUB publication: ${openError.message}")
                    )
                }

            if (publication.isRestricted) {
                runCatching { publication.close() }
                runCatching { asset.close() }
                return@withContext ReaderResult.Err(
                    ReaderError.DrmRestricted("EPUB publication is restricted")
                )
            }

            val docId: DocumentId = SourceDocumentIds.fromSourceSha1(
                prefix = "epub",
                source = source
            )
            val fixedLayout = publication.isFixedLayoutPublication()

            val capabilities = DocumentCapabilities(
                reflowable = !fixedLayout,
                fixedLayout = fixedLayout,
                outline = publication.tableOfContents.isNotEmpty(),
                search = publication.isSearchable,
                textExtraction = true,
                annotations = true,
                selection = true,
                links = true
            )

            ReaderResult.Ok(
                EpubDocument(
                    id = docId,
                    format = BookFormat.EPUB,
                    capabilities = capabilities,
                    openOptions = options,
                    publication = publication,
                    asset = asset,
                    annotationStore = annotationStore
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
        }
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\provider\EpubAnnotationProvider.kt

```kotlin
package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toReadiumLocatorOrNull
import com.ireader.engines.epub.internal.locator.withReadiumFragments
import com.ireader.engines.epub.internal.render.EpubDecorationsHost
import com.ireader.reader.api.annotation.Decoration as AppDecoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration

internal class EpubAnnotationProvider(
    private val documentId: DocumentId,
    private val store: AnnotationStore,
    private val decorationsHost: EpubDecorationsHost,
    private val scope: CoroutineScope
) : AnnotationProvider {

    companion object {
        private const val GROUP_ANNOTATION_PREFIX = "user.annotation."
        private const val DEFAULT_HIGHLIGHT: Int = 0x66FFF59D
    }

    private var renderedDecorations: Map<String, Decoration> = emptyMap()

    private val observeJob: Job = scope.launch {
        store.observe(documentId)
            .distinctUntilChanged()
            .catch { emit(emptyList()) }
            .collectLatest { annotations ->
                val next = annotations.associateNotNull { annotation ->
                    annotation.toReadiumDecorationOrNull()?.let { decoration ->
                        annotation.id.value to decoration
                    }
                }
                applyIncrementalDecorations(next)
            }
    }

    override fun observeAll() = store.observe(documentId)

    override suspend fun listAll(): ReaderResult<List<Annotation>> =
        store.list(documentId)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> =
        store.query(documentId, query)

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> =
        store.create(documentId, draft)

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> =
        store.update(documentId, annotation)

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> =
        store.delete(documentId, id)

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<AppDecoration>> {
        return when (val result = store.query(documentId, query)) {
            is ReaderResult.Err -> result
            is ReaderResult.Ok -> {
                val mapped = result.value.map { annotation ->
                    when (val anchor = annotation.anchor) {
                        is AnnotationAnchor.ReflowRange -> {
                            AppDecoration.Reflow(
                                range = anchor.range,
                                style = annotation.style
                            )
                        }

                        is AnnotationAnchor.FixedRects -> {
                            AppDecoration.Fixed(
                                page = anchor.page,
                                rects = anchor.rects,
                                style = annotation.style
                            )
                        }
                    }
                }
                ReaderResult.Ok(mapped)
            }
        }
    }

    fun closeInternal() {
        observeJob.cancel()
        renderedDecorations = emptyMap()
    }

    private fun Annotation.toReadiumDecorationOrNull(): Decoration? {
        val reflow = anchor as? AnnotationAnchor.ReflowRange ?: return null
        val locator = rangeToReadiumLocatorOrNull(reflow.range) ?: return null

        val tint = applyOpacity(
            colorArgb = style.colorArgb ?: DEFAULT_HIGHLIGHT,
            opacity = style.opacity
        )

        val decorationStyle = when (type) {
            AnnotationType.HIGHLIGHT,
            AnnotationType.NOTE,
            AnnotationType.BOOKMARK -> Decoration.Style.Highlight(tint = tint)

            AnnotationType.UNDERLINE -> Decoration.Style.Underline(tint = tint)
        }

        return Decoration(
            id = id.value,
            locator = locator,
            style = decorationStyle,
            extras = mapOf(
                "type" to type.name
            )
        )
    }

    private suspend fun applyIncrementalDecorations(next: Map<String, Decoration>) {
        val previous = renderedDecorations
        val removedIds = previous.keys - next.keys
        for (id in removedIds) {
            decorationsHost.apply(groupFor(id), emptyList())
        }
        for ((id, decoration) in next) {
            val previousDecoration = previous[id]
            if (previousDecoration != decoration) {
                decorationsHost.apply(groupFor(id), listOf(decoration))
            }
        }
        renderedDecorations = next
    }

    private fun groupFor(annotationId: String): String = GROUP_ANNOTATION_PREFIX + annotationId

    private fun rangeToReadiumLocatorOrNull(range: com.ireader.reader.model.LocatorRange): org.readium.r2.shared.publication.Locator? {
        val start = range.start.toReadiumLocatorOrNull() ?: return null
        val end = range.end.toReadiumLocatorOrNull()
        val startFragments = start.locations.fragments.filter { it.isNotBlank() }
        val endFragments = end?.locations?.fragments?.filter { it.isNotBlank() }.orEmpty()
        val mergedFragments = when {
            startFragments.isEmpty() && endFragments.isEmpty() -> emptyList()
            startFragments.isEmpty() -> listOf(endFragments.first())
            endFragments.isEmpty() -> listOf(startFragments.first())
            else -> listOf(startFragments.first(), endFragments.last())
        }
        if (mergedFragments.isEmpty()) {
            return start
        }
        return range.start
            .withReadiumFragments(mergedFragments)
            .toReadiumLocatorOrNull()
    }

    private fun applyOpacity(colorArgb: Int, opacity: Float?): Int {
        if (opacity == null) return colorArgb
        val alpha = ((opacity.coerceIn(0f, 1f) * 255f).toInt() and 0xFF) shl 24
        return (colorArgb and 0x00FFFFFF) or alpha
    }
}

private inline fun <K, V, R> Iterable<V>.associateNotNull(transform: (V) -> Pair<K, R>?): Map<K, R> {
    val out = LinkedHashMap<K, R>()
    for (item in this) {
        val pair = transform(item) ?: continue
        out[pair.first] = pair.second
    }
    return out
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\provider\EpubOutlineProvider.kt

```kotlin
package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

internal class EpubOutlineProvider(
    private val publication: Publication
) : OutlineProvider {

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return try {
            ReaderResult.Ok(publication.tableOfContents.mapNotNull { it.toOutlineNodeOrNull() })
        } catch (t: Throwable) {
            ReaderResult.Err(ReaderError.Internal(cause = t))
        }
    }

    private fun Link.toOutlineNodeOrNull(): OutlineNode? {
        val locator = publication.locatorFromLink(this) ?: return null
        return OutlineNode(
            title = title ?: "Untitled",
            locator = locator.toAppLocator(),
            children = children.mapNotNull { it.toOutlineNodeOrNull() }
        )
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\provider\EpubResourceProvider.kt

```kotlin
package com.ireader.engines.epub.internal.provider

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.BlockingResourceProvider
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.asInputStream
import org.readium.r2.shared.util.fromLegacyHref

internal class EpubResourceProvider(
    private val publication: Publication,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlockingResourceProvider {

    override suspend fun openResource(path: String): ReaderResult<InputStream> {
        return withContext(ioDispatcher) {
            openResourceBlocking(path)
        }
    }

    override suspend fun getMimeType(path: String): ReaderResult<String?> {
        return withContext(ioDispatcher) {
            getMimeTypeBlocking(path)
        }
    }

    override fun openResourceBlocking(path: String): ReaderResult<InputStream> {
        return try {
            val href = parseHref(path)
                ?: return ReaderResult.Err(
                    ReaderError.CorruptOrInvalid("Invalid EPUB href: $path")
                )

            val resource = publication.get(href)
                ?: return ReaderResult.Err(
                    ReaderError.NotFound("Resource not found: $path")
                )

            ReaderResult.Ok(resource.asInputStream())
        } catch (t: Throwable) {
            ReaderResult.Err(ReaderError.Io(cause = t))
        }
    }

    override fun getMimeTypeBlocking(path: String): ReaderResult<String?> {
        return try {
            val href = parseHref(path) ?: return ReaderResult.Ok(null)
            ReaderResult.Ok(publication.linkWithHref(href)?.mediaType?.toString())
        } catch (t: Throwable) {
            ReaderResult.Err(ReaderError.Internal(cause = t))
        }
    }

    @OptIn(DelicateReadiumApi::class)
    private fun parseHref(path: String): Url? {
        val normalized = path.trim()
        if (normalized.isEmpty()) return null
        return Url(normalized) ?: Url.fromLegacyHref(normalized)
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\provider\EpubSearchProvider.kt

```kotlin
package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

@OptIn(ExperimentalReadiumApi::class)
internal class EpubSearchProvider(
    private val publication: Publication
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank() || options.maxHits <= 0) {
            return@flow
        }

        val iterator = publication.search(
            query = query,
            options = options.toReadiumSearchOptions()
        ) ?: throw IllegalStateException("EPUB search service is unavailable")

        var emitted = 0
        var passedStart = options.startFrom == null
        try {
            while (true) {
                val page = when (val result = iterator.next()) {
                    is Try.Success -> result.value
                    is Try.Failure -> throw IllegalStateException("EPUB search failed: $result")
                } ?: break

                for (locator in page.locators) {
                    val appLocator = locator.toAppLocator()
                    if (!passedStart) {
                        val startFrom = options.startFrom
                        if (startFrom != null && !isAtOrAfter(appLocator, startFrom)) {
                            continue
                        }
                        passedStart = true
                    }
                    val excerpt = buildString {
                        append(locator.text.before.orEmpty())
                        append(locator.text.highlight.orEmpty())
                        append(locator.text.after.orEmpty())
                    }.ifBlank {
                        locator.title ?: ""
                    }.take(240)

                    emit(
                        SearchHit(
                            range = LocatorRange(start = appLocator, end = appLocator),
                            excerpt = excerpt,
                            sectionTitle = locator.title
                        )
                    )

                    emitted++
                    if (emitted >= options.maxHits) {
                        return@flow
                    }
                }
            }
        } finally {
            runCatching { iterator.close() }
        }
    }

    private fun SearchOptions.toReadiumSearchOptions(): SearchService.Options =
        SearchService.Options(
            caseSensitive = caseSensitive,
            wholeWord = wholeWord
        )
}

internal fun isAtOrAfter(candidate: Locator, startFrom: Locator): Boolean {
    if (candidate.scheme != startFrom.scheme) {
        return true
    }
    if (candidate.value == startFrom.value) {
        return true
    }

    val candidatePosition = candidate.extras["position"]?.toIntOrNull()
    val startPosition = startFrom.extras["position"]?.toIntOrNull()
    if (candidatePosition != null && startPosition != null) {
        return candidatePosition >= startPosition
    }

    val candidateProgression = candidate.extras["totalProgression"]?.toDoubleOrNull()
        ?: candidate.extras["progression"]?.toDoubleOrNull()
    val startProgression = startFrom.extras["totalProgression"]?.toDoubleOrNull()
        ?: startFrom.extras["progression"]?.toDoubleOrNull()
    if (candidateProgression != null && startProgression != null) {
        return candidateProgression >= startProgression
    }

    return true
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\provider\EpubSelectionProvider.kt

```kotlin
package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.engines.epub.internal.locator.withReadiumFragments
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.NormalizedRect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal class EpubSelectionProvider(
    private val navigatorProvider: () -> EpubNavigatorFragment?,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : SelectionProvider {

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return withContext(mainDispatcher) {
            try {
                val navigator = navigatorProvider() ?: return@withContext ReaderResult.Ok(null)
                val selection = navigator.currentSelection() ?: return@withContext ReaderResult.Ok(null)
                val readiumLocator = selection.locator
                val locator = readiumLocator.toAppLocator()

                val view = navigator.view
                val rect = selection.rect
                val bounds = if (view != null && rect != null && view.width > 0 && view.height > 0) {
                    NormalizedRect(
                        left = (rect.left / view.width).coerceIn(0f, 1f),
                        top = (rect.top / view.height).coerceIn(0f, 1f),
                        right = (rect.right / view.width).coerceIn(0f, 1f),
                        bottom = (rect.bottom / view.height).coerceIn(0f, 1f)
                    )
                } else {
                    null
                }
                val text = readiumLocator.text.highlight?.takeIf { it.isNotBlank() }
                val rects = listOfNotNull(bounds)
                val fragments = readiumLocator.locations.fragments.filter { it.isNotBlank() }
                val startLocator = if (fragments.isEmpty()) {
                    locator
                } else {
                    locator.withReadiumFragments(listOf(fragments.first()))
                }
                val endLocator = if (fragments.isEmpty()) {
                    locator
                } else {
                    locator.withReadiumFragments(listOf(fragments.last()))
                }

                ReaderResult.Ok(
                    SelectionProvider.Selection(
                        locator = locator,
                        bounds = bounds,
                        start = startLocator,
                        end = endLocator,
                        selectedText = text,
                        rects = rects,
                        extras = buildMap {
                            fragments.firstOrNull()?.let { put("fragment", it) }
                            fragments.firstOrNull()?.let { put("startFragment", it) }
                            fragments.lastOrNull()?.let { put("endFragment", it) }
                        }
                    )
                )
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Internal(cause = t))
            }
        }
    }

    override suspend fun clearSelection(): ReaderResult<Unit> {
        return withContext(mainDispatcher) {
            try {
                navigatorProvider()?.clearSelection()
                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Internal(cause = t))
            }
        }
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\provider\EpubTextProvider.kt

```kotlin
package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toReadiumLocatorOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

@OptIn(ExperimentalReadiumApi::class)
internal class EpubTextProvider(
    private val publication: Publication,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val start = range.start.toReadiumLocatorOrNull()
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Unsupported locator scheme: ${range.start.scheme}")
                    )
                val end = range.end.toReadiumLocatorOrNull()

                start.text.highlight
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return@withContext ReaderResult.Ok(it) }

                val content = publication.content(start)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Content service unavailable")
                    )

                val iterator = content.iterator()
                val endPosition = end?.locations?.position
                val maxLength = 8_192
                val builder = StringBuilder()

                while (builder.length < maxLength) {
                    val element = iterator.nextOrNull() ?: break
                    val position = element.locator.locations.position
                    if (endPosition != null && position != null && position > endPosition) {
                        break
                    }

                    val text = (element as? Content.TextualElement)?.text
                        ?.trim()
                        .orEmpty()
                    if (text.isEmpty()) continue

                    if (builder.isNotEmpty()) {
                        builder.append('\n')
                    }
                    builder.append(text)
                }

                ReaderResult.Ok(builder.toString())
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Io(cause = t))
            }
        }
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val readium = locator.toReadiumLocatorOrNull()
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Unsupported locator scheme: ${locator.scheme}")
                    )

                val rich = buildString {
                    append(readium.text.before.orEmpty())
                    append(readium.text.highlight.orEmpty())
                    append(readium.text.after.orEmpty())
                }.trim()

                val max = maxChars.coerceAtLeast(1)
                if (rich.isNotEmpty()) {
                    return@withContext ReaderResult.Ok(rich.take(max))
                }

                val content = publication.content(readium)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Content service unavailable")
                    )

                val iterator = content.iterator()
                val builder = StringBuilder()
                while (builder.length < max) {
                    val element = iterator.nextOrNull() ?: break
                    val text = (element as? Content.TextualElement)?.text
                        ?.trim()
                        .orEmpty()
                    if (text.isEmpty()) continue
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(text)
                }

                ReaderResult.Ok(builder.toString().take(max))
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Io(cause = t))
            }
        }
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\readium\ReadiumEpubToolkit.kt

```kotlin
package com.ireader.engines.epub.internal.readium

import android.content.Context
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal class ReadiumEpubToolkit(
    context: Context,
    contentProtections: List<ContentProtection> = emptyList(),
    pdfFactory: PdfDocumentFactory<*>? = null
) {
    private val appContext = context.applicationContext

    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = pdfFactory
    )
    val publicationOpener = PublicationOpener(
        publicationParser = publicationParser,
        contentProtections = contentProtections
    )
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\render\EpubDecorationsHost.kt

```kotlin
package com.ireader.engines.epub.internal.render

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration

internal class EpubDecorationsHost(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private var navigator: DecorableNavigator? = null
    private val pending: MutableMap<String, List<Decoration>> = linkedMapOf()

    suspend fun bind(navigator: DecorableNavigator) {
        this.navigator = navigator
        val snapshot = pending.toMap()
        pending.clear()
        withContext(mainDispatcher) {
            snapshot.forEach { (group, decorations) ->
                navigator.applyDecorations(decorations, group)
            }
        }
    }

    fun unbind() {
        navigator = null
    }

    suspend fun apply(group: String, decorations: List<Decoration>) {
        val current = navigator
        if (current == null) {
            pending[group] = decorations
            return
        }

        withContext(mainDispatcher) {
            current.applyDecorations(decorations, group)
        }
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\render\EpubPreferencesMapper.kt

```kotlin
package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.toTypographySpec
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign

@OptIn(ExperimentalReadiumApi::class)
internal fun RenderConfig.toEpubPreferences(): EpubPreferences =
    when (this) {
        is RenderConfig.ReflowText -> {
            val typography = toTypographySpec()
            val baseSp = 16f
            val fontScale = (typography.fontSizeSp / baseSp)
                .toDouble()
                .coerceIn(0.5, 4.0)
            val advanced = !respectPublisherStyles
            val textAlign = if (advanced) {
                when (typography.textAlign) {
                    com.ireader.reader.api.render.TextAlignMode.START -> ReadiumTextAlign.START
                    com.ireader.reader.api.render.TextAlignMode.JUSTIFY -> ReadiumTextAlign.JUSTIFY
                }
            } else {
                null
            }

            EpubPreferences(
                fontSize = fontScale,
                scroll = false,
                pageMargins = (typography.pagePaddingDp / 16f).toDouble().coerceIn(0.0, 4.0),
                publisherStyles = respectPublisherStyles,
                lineHeight = if (advanced) typography.lineHeightMult.toDouble().coerceIn(1.0, 2.0) else null,
                paragraphSpacing = if (advanced) (typography.paragraphSpacingDp / 16f).toDouble().coerceIn(0.0, 2.0) else null,
                paragraphIndent = if (advanced) typography.paragraphIndentEm.toDouble().coerceIn(0.0, 3.0) else null,
                textAlign = textAlign,
                hyphens = if (advanced) typography.hyphenationMode != HyphenationMode.NONE else null,
                // Readium does not expose a direct equivalent for includeFontPadding/pageInsetMode.
                // Keep those settings as explicit no-op on EPUB to avoid implicit behavior drift.
                fontFamily = typography.fontFamilyName?.toReadiumFontFamilyOrNull()
            )
        }

        is RenderConfig.FixedPage -> EpubPreferences()
    }

private fun String.toReadiumFontFamilyOrNull(): FontFamily? {
    val key = lowercase().trim()
    if (key.isBlank() || key == "系统字体") {
        return null
    }
    return when (key) {
        "serif" -> FontFamily.SERIF
        "sans-serif", "sans" -> FontFamily.SANS_SERIF
        "monospace", "mono" -> FontFamily.MONOSPACE
        "cursive" -> FontFamily.CURSIVE
        // Map common Chinese names from settings panel to generic stacks.
        "思源宋体", "方正新楷体", "霞鹜文楷" -> FontFamily.SERIF
        else -> FontFamily(key)
    }
}
```

## engines/epub\src\main\kotlin\com\ireader\engines\epub\internal\session\EpubSession.kt

```kotlin
package com.ireader.engines.epub.internal.session

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.epub.internal.controller.EpubController
import com.ireader.engines.epub.internal.provider.EpubAnnotationProvider
import com.ireader.engines.epub.internal.provider.EpubOutlineProvider
import com.ireader.engines.epub.internal.provider.EpubResourceProvider
import com.ireader.engines.epub.internal.provider.EpubSearchProvider
import com.ireader.engines.epub.internal.provider.EpubSelectionProvider
import com.ireader.engines.epub.internal.provider.EpubTextProvider
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.readium.r2.shared.publication.Publication

internal class EpubSession private constructor(
    id: SessionId,
    documentId: DocumentId,
    private val parts: SessionParts
) : BaseReaderSession(
    id = id,
    controller = parts.controller,
    outline = parts.outline,
    search = parts.search,
    text = parts.text,
    resources = parts.resources,
    selection = parts.selection,
    annotations = parts.annotations
) {

    override fun closeExtras() {
        parts.annotations?.closeInternal()
        parts.scope.cancel()
    }

    companion object {
        fun create(
            id: SessionId,
            documentId: DocumentId,
            publication: Publication,
            initialLocator: Locator?,
            initialConfig: RenderConfig,
            annotationStore: AnnotationStore?
        ): EpubSession {
            val parts = buildParts(
                sessionId = id,
                documentId = documentId,
                publication = publication,
                initialLocator = initialLocator,
                initialConfig = initialConfig,
                annotationStore = annotationStore
            )
            return EpubSession(
                id = id,
                documentId = documentId,
                parts = parts
            )
        }
    }
}

private data class SessionParts(
    val scope: CoroutineScope,
    val controller: EpubController,
    val outline: EpubOutlineProvider,
    val search: EpubSearchProvider,
    val text: EpubTextProvider,
    val resources: EpubResourceProvider,
    val selection: EpubSelectionProvider,
    val annotations: EpubAnnotationProvider?
)

private fun buildParts(
    sessionId: SessionId,
    documentId: DocumentId,
    publication: Publication,
    initialLocator: Locator?,
    initialConfig: RenderConfig,
    annotationStore: AnnotationStore?
): SessionParts {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller = EpubController(
        publication = publication,
        sessionTag = sessionId.value,
        initialLocator = initialLocator,
        initialConfig = initialConfig
    )
    val annotations = annotationStore?.let { store ->
        EpubAnnotationProvider(
            documentId = documentId,
            store = store,
            decorationsHost = controller.decorationsHost,
            scope = scope
        )
    }
    return SessionParts(
        scope = scope,
        controller = controller,
        outline = EpubOutlineProvider(publication),
        search = EpubSearchProvider(publication),
        text = EpubTextProvider(publication),
        resources = EpubResourceProvider(publication),
        selection = EpubSelectionProvider(
            navigatorProvider = { controller.navigatorOrNull() }
        ),
        annotations = annotations
    )
}
```

