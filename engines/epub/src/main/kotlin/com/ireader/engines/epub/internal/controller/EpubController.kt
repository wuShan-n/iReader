package com.ireader.engines.epub.internal.controller

import android.os.SystemClock
import com.ireader.engines.epub.internal.anchor.AnchorIndexer
import com.ireader.engines.epub.internal.cache.SimpleLruCache
import com.ireader.engines.epub.internal.link.LinkExtractor
import com.ireader.engines.epub.internal.locator.EpubLocator
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.pagination.EpubPageMetricsStore
import com.ireader.engines.epub.internal.pagination.GlobalPages
import com.ireader.engines.epub.internal.pagination.PaginationSignature
import com.ireader.engines.epub.internal.pagination.ReflowPaginator
import com.ireader.engines.epub.internal.render.HtmlComposer
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.error.fold
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.Progression
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

internal class EpubController(
    private val container: EpubContainer,
    initialLocator: Locator?,
    initialConfig: RenderConfig.ReflowText,
    private val annotations: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher,
    private val metricsStore: EpubPageMetricsStore
) : ReaderController {

    private data class ReflowKey(val spine: Int, val page: Int, val sig: Int?)

    private data class PageKey(val spine: Int, val sig: Int, val page: Int)

    private data class ProgressValues(
        val percent: Double,
        val label: String,
        val current: Int,
        val total: Int
    )

    private val mutex = Mutex()

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.ReflowText = initialConfig

    private val paginator = ReflowPaginator(container)
    private val anchorIndexer = AnchorIndexer(container)
    private val linkExtractor = LinkExtractor(container)

    private var spineIndex: Int = EpubLocator.spineIndexOf(container, initialLocator)
        ?.coerceIn(0, max(0, container.spineCount - 1))
        ?: 0
    private var pageIndex: Int = 0
    private var pendingAnchorId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var prefetchJob: Job? = null
    private var lastRendered: RenderPage? = null

    @Volatile
    private var globalPages: GlobalPages? = null

    @Volatile
    private var globalDirty: Boolean = true

    private val linkBoundsCache = SimpleLruCache<PageKey, Map<String, List<NormalizedRect>>>(maxSize = 96)

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(buildState())
    override val state: StateFlow<RenderState> = _state

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
            markGlobalDirty()
            pageIndex = pageIndex.coerceIn(0, currentPageCount() - 1)
            _state.value = buildState()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return mutex.withLock {
            val reflow = (config as? RenderConfig.ReflowText)
                ?: return@withLock ReaderResult.Err(
                    ReaderError.Internal("EPUB only supports ReflowText config")
                )

            this.config = reflow
            markGlobalDirty()
            pageIndex = pageIndex.coerceIn(0, currentPageCount() - 1)
            _state.value = buildState()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            renderCurrent(policy)
        }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val count = currentPageCount()
            if (pageIndex < count - 1) {
                pageIndex += 1
            } else if (spineIndex < container.spineCount - 1) {
                spineIndex += 1
                pageIndex = 0
            }
            pendingAnchorId = null
            _state.value = buildState()
            _events.tryEmit(ReaderEvent.PageChanged(_state.value.locator))
            renderCurrent(policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            if (pageIndex > 0) {
                pageIndex -= 1
            } else if (spineIndex > 0) {
                spineIndex -= 1
                pageIndex = currentPageCount() - 1
            }
            pendingAnchorId = null
            _state.value = buildState()
            _events.tryEmit(ReaderEvent.PageChanged(_state.value.locator))
            renderCurrent(policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val metricsOnly = locator.extras["metricsOnly"] == "1"
            val parsedReflow = if (locator.scheme == LocatorSchemes.REFLOW_PAGE) {
                parseReflowPageFull(locator.value)
            } else {
                null
            }
            val spineFromValue = parsedReflow?.spine
            val pageFromValue = parsedReflow?.page
            val sigFromValue = parsedReflow?.sig
            val sigFromExtras = locator.extras["sig"]?.toIntOrNull()
            val sigForExtras = sigFromExtras ?: sigFromValue
            val pagesExtra = locator.extras["pages"]?.toIntOrNull()

            if (pagesExtra != null && spineFromValue != null && sigForExtras != null) {
                metricsStore.putPages(spine = spineFromValue, sig = sigForExtras, pages = pagesExtra)
                if (sigForExtras == sig()) {
                    markGlobalDirty()
                }
            }

            val linkBoundsJson = locator.extras["linkBounds"]
            if (!linkBoundsJson.isNullOrBlank() &&
                spineFromValue != null &&
                pageFromValue != null &&
                sigForExtras != null
            ) {
                val parsedBounds = parseLinkBounds(linkBoundsJson)
                if (parsedBounds.isNotEmpty()) {
                    linkBoundsCache.put(PageKey(spineFromValue, sigForExtras, pageFromValue), parsedBounds)
                    val rendered = lastRendered
                    if (rendered != null &&
                        rendered.locator.scheme == LocatorSchemes.REFLOW_PAGE &&
                        rendered.locator.value.startsWith("${spineFromValue}:${pageFromValue}")
                    ) {
                        lastRendered = rendered.copy(links = applyBounds(rendered.links, parsedBounds))
                    }
                }
            }

            if (metricsOnly) {
                if (spineFromValue != null) {
                    spineIndex = spineFromValue.coerceIn(0, container.spineCount - 1)
                }
                if (pageFromValue != null) {
                    val upperBound = when {
                        pagesExtra != null -> pagesExtra.coerceAtLeast(1) - 1
                        else -> currentPageCount() - 1
                    }.coerceAtLeast(0)
                    pageIndex = pageFromValue.coerceIn(0, upperBound)
                } else {
                    pageIndex = pageIndex.coerceIn(0, currentPageCount() - 1)
                }
                pendingAnchorId = null
                _state.value = buildState()
                return@withLock lastRendered?.let { ReaderResult.Ok(it) } ?: renderCurrent(policy)
            }

            when (locator.scheme) {
                LocatorSchemes.REFLOW_PAGE -> {
                    val parsed = parsedReflow
                        ?: return@withLock ReaderResult.Err(
                            ReaderError.CorruptOrInvalid("Invalid reflow locator: ${locator.value}")
                        )
                    spineIndex = parsed.spine.coerceIn(0, container.spineCount - 1)

                    val currentSig = sig()
                    val targetPageCount = currentPageCount().coerceAtLeast(1)
                    val mappedPage = if (parsed.sig != null && parsed.sig != currentSig) {
                        val oldCount = metricsStore.getPages(spineIndex, parsed.sig) ?: targetPageCount
                        if (oldCount <= 1 || targetPageCount <= 1) {
                            0
                        } else {
                            val ratio = parsed.page.toDouble() / (oldCount - 1).toDouble()
                            (ratio * (targetPageCount - 1).toDouble()).roundToInt()
                        }
                    } else {
                        parsed.page
                    }

                    pageIndex = mappedPage.coerceIn(0, targetPageCount - 1)
                    pendingAnchorId = null
                }

                LocatorSchemes.EPUB_CFI -> {
                    val targetSpine = EpubLocator.spineIndexFromEpubValue(container, locator.value)
                        ?: return@withLock ReaderResult.Err(
                            ReaderError.CorruptOrInvalid("Invalid epub locator: ${locator.value}")
                        )

                    spineIndex = targetSpine.coerceIn(0, container.spineCount - 1)
                    val anchor = EpubLocator.anchorFromEpubValue(locator.value)
                    pageIndex = estimatePageFromAnchor(anchor)
                    pendingAnchorId = anchor
                }

                else -> {
                    return@withLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Unsupported locator scheme: ${locator.scheme}")
                    )
                }
            }

            _state.value = buildState()
            _events.tryEmit(ReaderEvent.PageChanged(_state.value.locator))
            renderCurrent(policy)
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            ensureGlobalPages()

            val pages = globalPages
            val safePercent = percent.coerceIn(0.0, 1.0)
            val total = pages?.total ?: totalEstimatedPages()
            val targetGlobalPage = (safePercent * (total - 1)).toInt().coerceIn(0, total - 1)

            if (pages != null && pages.sig == sig()) {
                for (spine in 0 until container.spineCount) {
                    val start = pages.prefix[spine]
                    val endExclusive = pages.prefix[spine + 1]
                    if (targetGlobalPage < endExclusive) {
                        spineIndex = spine
                        pageIndex = (targetGlobalPage - start).coerceAtLeast(0)
                        break
                    }
                }
            } else {
                var acc = 0
                for (spine in 0 until container.spineCount) {
                    val pageCount = paginator.pageCount(spine, constraints, config)
                    if (targetGlobalPage < acc + pageCount) {
                        spineIndex = spine
                        pageIndex = (targetGlobalPage - acc).coerceAtLeast(0)
                        break
                    }
                    acc += pageCount
                }
            }

            pageIndex = pageIndex.coerceIn(0, currentPageCount() - 1)
            pendingAnchorId = null

            _state.value = buildState()
            _events.tryEmit(ReaderEvent.PageChanged(_state.value.locator))
            renderCurrent(policy)
        }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        return mutex.withLock {
            val safeCount = count.coerceIn(0, 4)
            if (safeCount == 0) {
                return@withLock ReaderResult.Ok(Unit)
            }

            val start = (spineIndex - safeCount).coerceAtLeast(0)
            val end = (spineIndex + safeCount).coerceAtMost(container.spineCount - 1)
            val currentSig = sig()

            runCatching {
                for (index in start..end) {
                    HtmlComposer.compose(
                        container = container,
                        spineIndex = index,
                        config = config,
                        constraints = constraints,
                        pageIndex = if (index == spineIndex) pageIndex else 0,
                        baseRelPath = container.spinePath(index),
                        anchorId = null,
                        sig = currentSig,
                        spineIndexForMetrics = index
                    )
                }
            }.fold(
                onSuccess = { ReaderResult.Ok(Unit) },
                onFailure = { ReaderResult.Err(ReaderError.Internal(cause = it)) }
            )
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            if (reason == InvalidateReason.CONFIG_CHANGED || reason == InvalidateReason.LAYOUT_CHANGED) {
                markGlobalDirty()
            }
            _state.value = buildState()
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        runCatching { metricsStore.flush() }
        scope.cancel()
    }

    private fun sig(): Int = PaginationSignature.of(config, constraints)

    private fun markGlobalDirty() {
        globalDirty = true
    }

    private suspend fun ensureGlobalPages() {
        val currentSig = sig()
        val cached = globalPages
        if (!globalDirty && cached != null && cached.sig == currentSig) {
            return
        }

        val spineCount = container.spineCount.coerceAtLeast(1)
        val pagesBySpine = IntArray(spineCount)
        for (index in 0 until spineCount) {
            pagesBySpine[index] = metricsStore.getPages(index, currentSig)
                ?: paginator.pageCount(index, constraints, config)
        }

        val prefix = IntArray(spineCount + 1)
        var acc = 0
        for (index in 0 until spineCount) {
            prefix[index] = acc
            acc += pagesBySpine[index].coerceAtLeast(1)
        }
        prefix[spineCount] = acc.coerceAtLeast(1)

        globalPages = GlobalPages(
            sig = currentSig,
            pagesBySpine = pagesBySpine,
            prefix = prefix,
            total = prefix[spineCount]
        )
        globalDirty = false
    }

    private fun totalEstimatedPages(): Int {
        var total = 0
        for (index in 0 until container.spineCount) {
            total += paginator.pageCount(index, constraints, config)
        }
        return total.coerceAtLeast(1)
    }

    private fun estimatePageFromAnchor(anchor: String?): Int {
        val cleanAnchor = anchor?.takeIf { it.isNotBlank() } ?: return 0
        if (constraints == null) return 0
        val offset = anchorIndexer.charOffsetFor(spineIndex, cleanAnchor) ?: return 0
        val pageCount = currentPageCount().coerceAtLeast(1)
        val textLength = paginator.textLength(spineIndex).coerceAtLeast(1)
        return ((offset.toDouble() / textLength.toDouble()) * pageCount.toDouble())
            .toInt()
            .coerceIn(0, pageCount - 1)
    }

    private fun currentPageCount(): Int {
        val currentSig = sig()
        val fromStore = metricsStore.getPages(spineIndex, currentSig)
        if (fromStore != null) {
            return fromStore
        }
        return paginator.pageCount(
            spineIndex = spineIndex,
            constraints = constraints,
            config = config
        ).coerceAtLeast(1)
    }

    private fun buildState(): RenderState {
        val chapterTotal = container.spineCount.coerceAtLeast(1)
        val chapterCurrent = spineIndex.coerceIn(0, chapterTotal - 1) + 1

        val localPageTotal = currentPageCount().coerceAtLeast(1)
        val localPageCurrent = pageIndex.coerceIn(0, localPageTotal - 1) + 1

        val currentSig = sig()
        val pages = globalPages?.takeIf { !globalDirty && it.sig == currentSig }
        val progress = if (pages != null && pages.total > 1) {
            val globalIndex = (pages.prefix.getOrNull(spineIndex) ?: 0) + (localPageCurrent - 1)
            val safeGlobalCurrent = (globalIndex + 1).coerceIn(1, pages.total)
            ProgressValues(
                percent = (globalIndex.toDouble() / (pages.total - 1).toDouble()).coerceIn(0.0, 1.0),
                label = "Page $safeGlobalCurrent/${pages.total}",
                current = safeGlobalCurrent,
                total = pages.total
            )
        } else {
            ProgressValues(
                percent = (
                    (spineIndex.toDouble() + (localPageCurrent - 1).toDouble() / localPageTotal.toDouble()) /
                        chapterTotal.toDouble()
                    ).coerceIn(0.0, 1.0),
                label = "Chapter $chapterCurrent/$chapterTotal · Page $localPageCurrent/$localPageTotal",
                current = localPageCurrent,
                total = localPageTotal
            )
        }

        return RenderState(
            locator = Locator(
                scheme = LocatorSchemes.REFLOW_PAGE,
                value = "$spineIndex:$pageIndex:$currentSig"
            ),
            progression = Progression(
                percent = progress.percent,
                label = progress.label,
                current = progress.current,
                total = progress.total
            ),
            nav = NavigationAvailability(
                canGoPrev = !(spineIndex == 0 && pageIndex == 0),
                canGoNext = !(spineIndex == chapterTotal - 1 && pageIndex == localPageTotal - 1)
            ),
            titleInView = container.titleForSpine(spineIndex),
            config = config
        )
    }

    private suspend fun renderCurrent(policy: RenderPolicy): ReaderResult<RenderPage> {
        return try {
            val startedAt = SystemClock.elapsedRealtime()
            val currentSig = sig()
            val baseRelPath = container.spinePath(spineIndex)
            val html = HtmlComposer.compose(
                container = container,
                spineIndex = spineIndex,
                config = config,
                constraints = constraints,
                pageIndex = pageIndex,
                baseRelPath = baseRelPath,
                anchorId = pendingAnchorId,
                sig = currentSig,
                spineIndexForMetrics = spineIndex
            )
            pendingAnchorId = null

            val linksBase = linkExtractor.linksForSpine(spineIndex)
            val bounds = linkBoundsCache.get(PageKey(spineIndex, currentSig, pageIndex))
            val links = if (bounds != null) applyBounds(linksBase, bounds) else linksBase

            val decorations = annotations
                ?.decorationsFor(
                    AnnotationQuery(
                        range = LocatorRange(
                            start = Locator(LocatorSchemes.EPUB_CFI, "spine:$spineIndex"),
                            end = Locator(LocatorSchemes.EPUB_CFI, "spine:$spineIndex")
                        )
                    )
                )
                ?.fold(
                    onOk = { it },
                    onErr = { emptyList() }
                )
                .orEmpty()

            val metrics = RenderMetrics(
                renderTimeMs = SystemClock.elapsedRealtime() - startedAt,
                cacheHit = policy.allowCache
            )

            val page = RenderPage(
                id = PageId("epub:${container.id.value}:spine:$spineIndex:page:$pageIndex"),
                locator = Locator(
                    scheme = LocatorSchemes.REFLOW_PAGE,
                    value = "$spineIndex:$pageIndex:$currentSig"
                ),
                content = RenderContent.Html(
                    inlineHtml = html,
                    baseUri = container.spineUri(spineIndex),
                    contentUri = null
                ),
                links = links,
                decorations = decorations,
                metrics = metrics
            )

            lastRendered = page
            ensureGlobalPages()
            _state.value = buildState()
            _events.tryEmit(ReaderEvent.Rendered(page.id, metrics))

            schedulePrefetch(policy.prefetchNeighbors)
            ReaderResult.Ok(page)
        } catch (throwable: Throwable) {
            _events.tryEmit(ReaderEvent.Error(throwable))
            ReaderResult.Err(ReaderError.Internal(cause = throwable))
        }
    }

    private fun schedulePrefetch(prefetchNeighbors: Int) {
        val safeCount = prefetchNeighbors.coerceIn(0, 4)
        if (safeCount == 0) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            runCatching {
                prefetchNeighbors(safeCount)
            }
        }
    }

    private fun parseReflowPageFull(value: String): ReflowKey? {
        val parts = value.split(':')
        if (parts.size < 2) return null
        val spine = parts[0].toIntOrNull() ?: return null
        val page = parts[1].toIntOrNull() ?: return null
        val signature = parts.getOrNull(2)?.toIntOrNull()
        return ReflowKey(spine = spine, page = page, sig = signature)
    }

    private fun parseLinkBounds(json: String): Map<String, List<NormalizedRect>> {
        return runCatching {
            val array = JSONArray(json)
            val out = HashMap<String, MutableList<NormalizedRect>>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val key = item.optString("k")
                if (key.isNullOrBlank()) continue

                val left = item.optDouble("l", -1.0).toFloat().coerceIn(0f, 1f)
                val top = item.optDouble("t", -1.0).toFloat().coerceIn(0f, 1f)
                val right = item.optDouble("r", -1.0).toFloat().coerceIn(0f, 1f)
                val bottom = item.optDouble("b", -1.0).toFloat().coerceIn(0f, 1f)
                if (right <= left || bottom <= top) continue

                out.getOrPut(key) { mutableListOf() }.add(
                    NormalizedRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom
                    )
                )
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun applyBounds(
        links: List<DocumentLink>,
        boundsByKey: Map<String, List<NormalizedRect>>
    ): List<DocumentLink> {
        if (boundsByKey.isEmpty()) return links
        return links.map { link ->
            val bounds = boundsByKey[linkKeyOf(link)]
            if (bounds != null) {
                link.copy(bounds = bounds)
            } else {
                link
            }
        }
    }

    private fun linkKeyOf(link: DocumentLink): String {
        return when (val target = link.target) {
            is LinkTarget.Internal -> "I|${target.locator.value}"
            is LinkTarget.External -> "E|${target.url}"
        }
    }
}
