package com.ireader.engines.epub.internal.controller

import android.os.SystemClock
import com.ireader.engines.epub.internal.anchor.AnchorIndexer
import com.ireader.engines.epub.internal.link.LinkExtractor
import com.ireader.engines.epub.internal.locator.EpubLocator
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.pagination.GlobalPageMapper
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
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.Progression
import kotlin.math.max
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

internal class EpubController(
    private val container: EpubContainer,
    initialLocator: Locator?,
    initialConfig: RenderConfig.ReflowText,
    private val annotations: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher
) : ReaderController {

    private val mutex = Mutex()

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.ReflowText = initialConfig

    private val paginator = ReflowPaginator(container)
    private val globalPageMapper = GlobalPageMapper(container, paginator)
    private val anchorIndexer = AnchorIndexer(container)
    private val linkExtractor = LinkExtractor(container)

    private var spineIndex: Int = EpubLocator.spineIndexOf(container, initialLocator)
        ?.coerceIn(0, max(0, container.spineCount - 1))
        ?: 0
    private var pageIndex: Int = 0
    private var pendingAnchorId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var prefetchJob: Job? = null

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(buildState())
    override val state: StateFlow<RenderState> = _state

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
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
            when (locator.scheme) {
                LocatorSchemes.REFLOW_PAGE -> {
                    val parsed = EpubLocator.parseReflowPage(locator.value)
                        ?: return@withLock ReaderResult.Err(
                            ReaderError.CorruptOrInvalid("Invalid reflow locator: ${locator.value}")
                        )
                    spineIndex = parsed.first.coerceIn(0, container.spineCount - 1)
                    pageIndex = parsed.second.coerceIn(0, currentPageCount() - 1)
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
            val (targetSpine, targetPage) = globalPageMapper.locateByPercent(
                percent = percent,
                constraints = constraints,
                config = config
            )
            spineIndex = targetSpine.coerceIn(0, container.spineCount - 1)
            pageIndex = targetPage.coerceIn(0, currentPageCount() - 1)
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

            runCatching {
                for (index in start..end) {
                    HtmlComposer.compose(
                        container = container,
                        spineIndex = index,
                        config = config,
                        constraints = constraints,
                        pageIndex = if (index == spineIndex) pageIndex else 0,
                        baseRelPath = container.spinePath(index),
                        anchorId = null
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
            _state.value = buildState()
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        scope.cancel()
    }

    private fun estimatePageFromAnchor(anchor: String?): Int {
        if (anchor.isNullOrBlank()) return 0
        val offset = anchorIndexer.charOffsetFor(spineIndex, anchor) ?: return 0
        val charsPerPage = paginator.charsPerPage(
            constraints = constraints,
            config = config,
            isCjk = true
        ).coerceAtLeast(300)
        return (offset / charsPerPage).coerceIn(0, currentPageCount() - 1)
    }

    private fun currentPageCount(): Int {
        return paginator.pageCount(
            spineIndex = spineIndex,
            constraints = constraints,
            config = config
        ).coerceAtLeast(1)
    }

    private fun cfgHash(): Int = config.hashCode()

    private fun buildState(): RenderState {
        val chapterTotal = container.spineCount.coerceAtLeast(1)
        val chapterCurrent = spineIndex.coerceIn(0, chapterTotal - 1) + 1

        val pageTotal = currentPageCount().coerceAtLeast(1)
        val pageCurrent = pageIndex.coerceIn(0, pageTotal - 1) + 1

        val percent = (
            (spineIndex.toDouble() + (pageCurrent - 1).toDouble() / pageTotal.toDouble()) /
                chapterTotal.toDouble()
            ).coerceIn(0.0, 1.0)

        return RenderState(
            locator = Locator(
                scheme = LocatorSchemes.REFLOW_PAGE,
                value = "$spineIndex:$pageIndex:${cfgHash()}"
            ),
            progression = Progression(
                percent = percent,
                label = "Chapter $chapterCurrent/$chapterTotal · Page $pageCurrent/$pageTotal",
                current = pageCurrent,
                total = pageTotal
            ),
            nav = NavigationAvailability(
                canGoPrev = !(spineIndex == 0 && pageIndex == 0),
                canGoNext = !(spineIndex == chapterTotal - 1 && pageIndex == pageTotal - 1)
            ),
            titleInView = container.titleForSpine(spineIndex),
            config = config
        )
    }

    private suspend fun renderCurrent(policy: RenderPolicy): ReaderResult<RenderPage> {
        return try {
            val startedAt = SystemClock.elapsedRealtime()
            val baseRelPath = container.spinePath(spineIndex)
            val html = HtmlComposer.compose(
                container = container,
                spineIndex = spineIndex,
                config = config,
                constraints = constraints,
                pageIndex = pageIndex,
                baseRelPath = baseRelPath,
                anchorId = pendingAnchorId
            )
            pendingAnchorId = null

            val links = linkExtractor.linksForSpine(spineIndex)
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
                    value = "$spineIndex:$pageIndex:${cfgHash()}"
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
}
