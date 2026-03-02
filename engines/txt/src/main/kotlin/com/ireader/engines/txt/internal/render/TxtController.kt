@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)

package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.link.LinkDetector
import com.ireader.engines.txt.internal.pagination.PageSlice
import com.ireader.engines.txt.internal.pagination.PageMap
import com.ireader.engines.txt.internal.pagination.TxtPaginator
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.getOrNull
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
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
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.Progression
import java.io.File
import java.security.MessageDigest
import java.util.Locale
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

internal class TxtController(
    private val documentKey: String,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    initialOffset: Long,
    initialConfig: RenderConfig.ReflowText,
    maxPageCache: Int,
    private val persistPagination: Boolean,
    private val files: TxtBookFiles,
    private val annotationProvider: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher,
    defaultDispatcher: CoroutineDispatcher
) : ReaderController {

    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val mutex = Mutex()
    private var softBreakIndex: SoftBreakIndex? = SoftBreakIndex.openIfValid(files.softBreakIdx, meta)
    private val paginator = TxtPaginator(store, meta, softBreakIndex)
    private val pageCache = PageCache(maxPageCache)
    private var pageCompletionJob: Job? = null

    private val eventsMutable = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    override val events: Flow<ReaderEvent> = eventsMutable.asSharedFlow()

    private val initialStart = initialOffset.coerceIn(0L, store.lengthChars)
    private val stateMutable = MutableStateFlow(
        RenderState(
            locator = locatorFor(initialStart),
            progression = progressionFor(initialStart),
            nav = NavigationAvailability(
                canGoPrev = initialStart > 0L,
                canGoNext = initialStart < store.lengthChars
            ),
            config = initialConfig
        )
    )
    override val state: StateFlow<RenderState> = stateMutable.asStateFlow()

    private var constraints: LayoutConstraints? = null
    private var currentConfig: RenderConfig.ReflowText = initialConfig
    private var currentStart: Long = initialStart
    private var currentEnd: Long = initialStart
    private var avgCharsPerPage: Int = 1800

    private var profileKey: String? = null
    private var pageStarts = sortedSetOf<Long>()
    private var pageIndexDirty = false
    private val paginationDir: File = files.paginationDir

    init {
        if (meta.hardWrapLikely && softBreakIndex == null) {
            scope.launch {
                runCatching {
                    SoftBreakIndexBuilder.buildIfNeeded(files, meta, ioDispatcher)
                    val loaded = SoftBreakIndex.openIfValid(files.softBreakIdx, meta)
                    mutex.withLock {
                        softBreakIndex?.close()
                        softBreakIndex = loaded
                        paginator.setSoftBreakIndex(loaded)
                        pageCache.clear()
                    }
                }
            }
        }
    }

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
            pageCache.clear()
            reloadPaginationIndexIfNeededLocked()
            updateStateLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val reflow = config as? RenderConfig.ReflowText
            ?: return ReaderResult.Err(ReaderError.Internal("TXT requires ReflowText config"))
        return mutex.withLock {
            currentConfig = reflow
            stateMutable.value = stateMutable.value.copy(config = reflow)
            pageCache.clear()
            reloadPaginationIndexIfNeededLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val renderResult = mutex.withLock {
            renderLocked(policy)
        }
        if (renderResult is ReaderResult.Ok && policy.prefetchNeighbors > 0) {
            scope.launch {
                runCatching { prefetchNeighbors(policy.prefetchNeighbors) }
            }
        }
        return renderResult
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val constraintsLocal = constraints
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            val current = getOrBuildSliceLocked(currentStart, constraintsLocal, allowCache = true)
            if (current.endOffset >= store.lengthChars) {
                return@withLock buildPageResultLocked(
                    slice = current,
                    renderTimeMs = 0L,
                    cacheHit = true
                )
            }
            currentStart = current.endOffset
            renderLocked(policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val constraintsLocal = constraints
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            if (currentStart <= 0L) {
                return@withLock renderLocked(policy)
            }
            val target = findPreviousStartLocked(currentStart, constraintsLocal)
            currentStart = target
            renderLocked(policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) {
            return ReaderResult.Err(ReaderError.Internal("Unsupported locator for TXT: ${locator.scheme}"))
        }
        val offset = locator.value.toLongOrNull()
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid TXT offset: ${locator.value}"))
        return mutex.withLock {
            currentStart = offset.coerceIn(0L, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val clamped = percent.coerceIn(0.0, 1.0)
        return mutex.withLock {
            val target = if (pageStarts.size >= 8) {
                val index = ((pageStarts.size - 1) * clamped).roundToInt().coerceIn(0, pageStarts.size - 1)
                pageStarts.elementAt(index)
            } else {
                (store.lengthChars * clamped).toLong()
            }
            currentStart = target.coerceIn(0L, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) {
            return ReaderResult.Ok(Unit)
        }
        return mutex.withLock {
            val constraintsLocal = constraints ?: return@withLock ReaderResult.Ok(Unit)
            val currentSlice = getOrBuildSliceLocked(currentStart, constraintsLocal, allowCache = true)

            var forwardStart = currentSlice.endOffset
            repeat(count) {
                if (forwardStart >= store.lengthChars) return@repeat
                val next = getOrBuildSliceLocked(forwardStart, constraintsLocal, allowCache = true)
                if (next.endOffset <= forwardStart) {
                    return@repeat
                }
                forwardStart = next.endOffset
            }

            var backwardStart = currentSlice.startOffset
            repeat(count) {
                if (backwardStart <= 0L) return@repeat
                val prevStart = findPreviousStartLocked(backwardStart, constraintsLocal)
                if (prevStart >= backwardStart) {
                    return@repeat
                }
                getOrBuildSliceLocked(prevStart, constraintsLocal, allowCache = true)
                backwardStart = prevStart
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            pageCache.clear()
            if (reason == InvalidateReason.CONFIG_CHANGED || reason == InvalidateReason.LAYOUT_CHANGED) {
                profileKey = null
            }
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        runCatching {
            savePaginationIndexLocked()
        }
        pageCompletionJob?.cancel()
        runCatching {
            softBreakIndex?.close()
        }
        scope.cancel()
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val constraintsLocal = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        var builtSlice: PageSlice? = null
        var cacheHit = false
        val elapsed = measureTimeMillis {
            if (policy.allowCache) {
                builtSlice = pageCache[currentStart]
                if (builtSlice != null) {
                    cacheHit = true
                }
            }
            if (builtSlice == null) {
                val computed = paginator.pageAt(
                    startOffset = currentStart,
                    config = currentConfig,
                    constraints = constraintsLocal
                )
                builtSlice = computed
                pageCache[currentStart] = computed
            }
        }
        val slice = builtSlice ?: return ReaderResult.Err(ReaderError.Internal("Failed to paginate TXT page"))

        val page = buildPageResultLocked(
            slice = slice,
            renderTimeMs = elapsed,
            cacheHit = cacheHit
        )
        if (page is ReaderResult.Ok) {
            recordPageStartLocked(slice.startOffset)
            maybeSchedulePageCompletionLocked()
        }
        return page
    }

    private suspend fun buildPageResultLocked(
        slice: PageSlice,
        renderTimeMs: Long,
        cacheHit: Boolean
    ): ReaderResult<RenderPage> {
        currentStart = slice.startOffset
        currentEnd = slice.endOffset
        val consumed = (slice.endOffset - slice.startOffset).toInt().coerceAtLeast(1)
        avgCharsPerPage = ((avgCharsPerPage * 3) + consumed) / 4

        updateStateLocked()
        val pageRange = LocatorRange(
            start = Locator(LocatorSchemes.TXT_OFFSET, slice.startOffset.toString()),
            end = Locator(LocatorSchemes.TXT_OFFSET, slice.endOffset.toString())
        )
        val decorations = annotationProvider
            ?.decorationsFor(AnnotationQuery(range = pageRange))
            ?.getOrNull()
            ?: emptyList()
        val links = LinkDetector.detect(slice.text)

        val page = RenderPage(
            id = PageId("${slice.startOffset}-${slice.endOffset}"),
            locator = locatorFor(slice.startOffset),
            content = RenderContent.Text(
                text = slice.text,
                mapping = TxtTextMapping(slice.startOffset, slice.endOffset)
            ),
            links = links,
            decorations = decorations,
            metrics = RenderMetrics(
                renderTimeMs = renderTimeMs,
                cacheHit = cacheHit
            )
        )
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        eventsMutable.tryEmit(ReaderEvent.PageChanged(page.locator))
        return ReaderResult.Ok(page)
    }

    private suspend fun getOrBuildSliceLocked(
        start: Long,
        constraints: LayoutConstraints,
        allowCache: Boolean
    ): PageSlice {
        val normalizedStart = start.coerceIn(0L, store.lengthChars)
        if (allowCache) {
            val cached = pageCache[normalizedStart]
            if (cached != null) {
                return cached
            }
        }
        val computed = paginator.pageAt(
            startOffset = normalizedStart,
            config = currentConfig,
            constraints = constraints
        )
        pageCache[normalizedStart] = computed
        return computed
    }

    private suspend fun findPreviousStartLocked(
        currentStart: Long,
        constraints: LayoutConstraints
    ): Long {
        if (currentStart <= 0L) {
            return 0L
        }
        val estimateDistance = (avgCharsPerPage * 2L).coerceAtLeast(1_200L)
        var cursor = (currentStart - estimateDistance).coerceAtLeast(0L)
        var previousStart = 0L
        var safety = 0

        while (cursor < currentStart && safety < 256) {
            val slice = getOrBuildSliceLocked(cursor, constraints, allowCache = true)
            if (slice.endOffset >= currentStart) {
                return previousStart.coerceAtMost(currentStart)
            }
            previousStart = slice.startOffset
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            safety++
        }
        return cursor.coerceAtMost(currentStart).coerceAtLeast(0L)
    }

    private fun updateStateLocked() {
        val canGoPrev = currentStart > 0L
        val canGoNext = currentEnd < store.lengthChars
        stateMutable.value = stateMutable.value.copy(
            locator = locatorFor(currentStart),
            progression = progressionFor(currentStart),
            nav = NavigationAvailability(
                canGoPrev = canGoPrev,
                canGoNext = canGoNext
            ),
            config = currentConfig
        )
    }

    private fun progressionFor(offset: Long): Progression {
        val percent = if (store.lengthChars == 0L) {
            0.0
        } else {
            offset.toDouble() / store.lengthChars.toDouble()
        }.coerceIn(0.0, 1.0)
        val label = "${(percent * 100.0).roundToInt()}%"
        return Progression(percent = percent, label = label)
    }

    private fun locatorFor(offset: Long): Locator {
        val percent = if (store.lengthChars == 0L) {
            0.0
        } else {
            offset.toDouble() / store.lengthChars.toDouble()
        }.coerceIn(0.0, 1.0)
        return Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = offset.coerceIn(0L, store.lengthChars).toString(),
            extras = mapOf("progression" to String.format(Locale.US, "%.6f", percent))
        )
    }

    private fun reloadPaginationIndexIfNeededLocked() {
        if (!persistPagination) {
            profileKey = null
            pageStarts.clear()
            pageIndexDirty = false
            pageCompletionJob?.cancel()
            return
        }
        val constraintsLocal = constraints ?: return
        val nextProfile = computeProfileKey(constraintsLocal, currentConfig)
        if (nextProfile == profileKey) {
            return
        }
        savePaginationIndexLocked()
        profileKey = nextProfile
        pageStarts = loadPageStarts(nextProfile)
        pageIndexDirty = false
        pageCompletionJob?.cancel()
    }

    private fun recordPageStartLocked(start: Long) {
        if (!persistPagination || profileKey.isNullOrBlank()) {
            return
        }
        if (pageStarts.add(start)) {
            pageIndexDirty = true
            if (pageStarts.size % 16 == 0) {
                savePaginationIndexLocked()
            }
        }
    }

    private fun savePaginationIndexLocked() {
        if (!persistPagination || profileKey.isNullOrBlank() || !pageIndexDirty) {
            return
        }
        val file = profileFile(profileKey!!)
        file.parentFile?.mkdirs()
        PageMap.save(file, pageStarts)
        pageIndexDirty = false
    }

    private fun loadPageStarts(profile: String): java.util.TreeSet<Long> {
        return PageMap.load(
            binaryFile = profileFile(profile),
            legacyTextFile = legacyProfileFile(profile)
        )
    }

    private fun profileFile(profile: String): File {
        return File(paginationDir, "pagemap_$profile.bin")
    }

    private fun legacyProfileFile(profile: String): File {
        return File(paginationDir, "pagemap_$profile.txt")
    }

    private fun maybeSchedulePageCompletionLocked() {
        if (!persistPagination || profileKey.isNullOrBlank()) {
            return
        }
        if (pageCompletionJob?.isActive == true) {
            return
        }
        val constraintsLocal = constraints ?: return
        pageCompletionJob = scope.launch {
            runCatching {
                mutex.withLock {
                    completePageMapForwardLocked(constraintsLocal, maxPages = 24)
                }
            }
        }
    }

    private suspend fun completePageMapForwardLocked(
        constraints: LayoutConstraints,
        maxPages: Int
    ) {
        if (maxPages <= 0 || store.lengthChars <= 0L) {
            return
        }
        var cursor = pageStarts.lastOrNull() ?: currentStart
        cursor = cursor.coerceIn(0L, store.lengthChars)
        var steps = 0
        while (cursor < store.lengthChars && steps < maxPages) {
            val slice = getOrBuildSliceLocked(cursor, constraints, allowCache = true)
            recordPageStartLocked(slice.startOffset)
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            steps++
        }
        savePaginationIndexLocked()
    }

    private fun computeProfileKey(
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): String {
        val raw = buildString {
            append(documentKey)
            append('|')
            append(constraints.viewportWidthPx)
            append('x')
            append(constraints.viewportHeightPx)
            append('|')
            append(constraints.density)
            append('|')
            append(constraints.fontScale)
            append('|')
            append(config.fontSizeSp)
            append('|')
            append(config.lineHeightMult)
            append('|')
            append(config.paragraphSpacingDp)
            append('|')
            append(config.pagePaddingDp)
            append('|')
            append(config.fontFamilyName.orEmpty())
            append('|')
            append(config.hyphenation)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.toHex().take(16)
    }

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        var index = 0
        for (b in this) {
            val value = b.toInt() and 0xFF
            chars[index++] = HEX[value ushr 4]
            chars[index++] = HEX[value and 0x0F]
        }
        return String(chars)
    }

    private class PageCache(maxEntries: Int) {
        private val maxSize = maxEntries.coerceAtLeast(1)
        private val map = object : LinkedHashMap<Long, PageSlice>(maxSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PageSlice>): Boolean {
                return size > maxSize
            }
        }

        operator fun get(key: Long): PageSlice? = map[key]

        operator fun set(key: Long, value: PageSlice) {
            map[key] = value
        }

        fun clear() {
            map.clear()
        }
    }

    private companion object {
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
