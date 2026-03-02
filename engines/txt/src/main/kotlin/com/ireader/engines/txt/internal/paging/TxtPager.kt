package com.ireader.engines.txt.internal.paging

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.engines.txt.internal.storage.TxtTextStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PageSlice(
    val startChar: Int,
    val endChar: Int,
    val text: String
)

internal class TxtPager(
    private val store: TxtTextStore,
    private val chunkSizeChars: Int = 32 * 1024
) {
    private companion object {
        private const val DEFAULT_CHUNK_CACHE_ENTRIES = 10
        private const val MIN_ESTIMATED_CHARS = 256
        private const val MIN_CHUNK_CHARS = 2_000
        private const val MIN_ALIGNMENT_CHARS = 2_048
    }

    private data class ChunkLayoutKey(
        val chunkStart: Int,
        val widthPx: Int,
        val heightPx: Int,
        val densityBits: Int,
        val fontScaleBits: Int,
        val fontSizeBits: Int,
        val lineHeightBits: Int,
        val paddingBits: Int,
        val hyphenation: Boolean,
        val fontFamily: String?
    )

    private data class ChunkLayoutResult(
        val chunkStart: Int,
        val chunkText: String,
        val pageStarts: IntArray
    ) {
        val chunkEnd: Int
            get() = chunkStart + chunkText.length
    }

    private val chunkCache = object : LinkedHashMap<ChunkLayoutKey, ChunkLayoutResult>(
        DEFAULT_CHUNK_CACHE_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChunkLayoutKey, ChunkLayoutResult>?): Boolean {
            return size > DEFAULT_CHUNK_CACHE_ENTRIES
        }
    }

    suspend fun pageAt(
        startChar: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): PageSlice {
        val total = store.totalChars().coerceAtLeast(0)
        if (total == 0) return PageSlice(0, 0, "")

        val start = startChar.coerceIn(0, total - 1)
        val paddingPx = dpToPx(config.pagePaddingDp, constraints.density)
        val contentWidth = max(1, constraints.viewportWidthPx - (paddingPx * 2))
        val contentHeight = max(1, constraints.viewportHeightPx - (paddingPx * 2))

        val estimated = estimateCharsPerPage(constraints, config).coerceAtLeast(MIN_ESTIMATED_CHARS)
        val desiredChunk = max(chunkSizeChars.coerceAtLeast(MIN_CHUNK_CHARS), estimated * 4)
        val alignment = (estimated * 2)
            .coerceIn(MIN_ALIGNMENT_CHARS, chunkSizeChars.coerceAtLeast(MIN_ALIGNMENT_CHARS))
        val chunkStart = alignDown(start, alignment)
        val distanceIntoChunk = (start - chunkStart).coerceAtLeast(0)
        val candidateMax = min(total - chunkStart, desiredChunk + distanceIntoChunk + estimated)
        if (candidateMax <= 0) return PageSlice(start, start, "")

        val key = ChunkLayoutKey(
            chunkStart = chunkStart,
            widthPx = contentWidth,
            heightPx = contentHeight,
            densityBits = constraints.density.toBits(),
            fontScaleBits = constraints.fontScale.toBits(),
            fontSizeBits = config.fontSizeSp.toBits(),
            lineHeightBits = config.lineHeightMult.toBits(),
            paddingBits = config.pagePaddingDp.toBits(),
            hyphenation = config.hyphenation,
            fontFamily = config.fontFamilyName
        )

        val chunk = getOrBuildChunkLayout(
            key = key,
            readLen = candidateMax,
            widthPx = contentWidth,
            heightPx = contentHeight,
            constraints = constraints,
            config = config
        )
        if (chunk.chunkText.isEmpty()) return PageSlice(start, start, "")

        val starts = chunk.pageStarts
        val pageIndex = floorIndex(starts, start).coerceAtLeast(0)
        val pageStart = starts.getOrElse(pageIndex) { start }.coerceIn(0, total)
        val pageEnd = resolvePageEnd(starts, pageIndex, chunk.chunkEnd, total, pageStart)
        if (pageEnd <= pageStart) {
            return buildSingleCharFallback(start = start, total = total)
        }
        val localStart = (pageStart - chunk.chunkStart).coerceIn(0, chunk.chunkText.length)
        val localEnd = (pageEnd - chunk.chunkStart).coerceIn(localStart, chunk.chunkText.length)
        val text = chunk.chunkText.substring(localStart, localEnd)
        if (text.isEmpty()) {
            return buildSingleCharFallback(start = start, total = total)
        }
        return PageSlice(startChar = pageStart, endChar = pageEnd, text = text)
    }

    fun estimateCharsPerPage(
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): Int {
        val paddingPx = dpToPx(config.pagePaddingDp, constraints.density)
        val contentWidth = max(1, constraints.viewportWidthPx - (paddingPx * 2))
        val contentHeight = max(1, constraints.viewportHeightPx - (paddingPx * 2))

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spToPx(config.fontSizeSp, constraints.density, constraints.fontScale)
        }

        val metrics = textPaint.fontMetrics
        val lineHeightPx = max(1f, (metrics.descent - metrics.ascent) * config.lineHeightMult)
        val linesPerPage = max(1, (contentHeight / lineHeightPx).toInt())

        val avgCharWidth = max(1f, textPaint.measureText("中"))
        val charsPerLine = max(4, (contentWidth / avgCharWidth).toInt())

        return (linesPerPage * charsPerLine).coerceIn(200, 50_000)
    }

    private suspend fun getOrBuildChunkLayout(
        key: ChunkLayoutKey,
        readLen: Int,
        widthPx: Int,
        heightPx: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): ChunkLayoutResult {
        val cached = synchronized(chunkCache) { chunkCache[key] }
        if (cached != null) return cached

        val built = buildChunkLayout(
            key = key,
            readLen = readLen,
            widthPx = widthPx,
            heightPx = heightPx,
            constraints = constraints,
            config = config
        )
        synchronized(chunkCache) { chunkCache[key] = built }
        return built
    }

    private suspend fun buildChunkLayout(
        key: ChunkLayoutKey,
        readLen: Int,
        widthPx: Int,
        heightPx: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): ChunkLayoutResult {
        val chunkText = store.readChars(key.chunkStart, readLen)
        if (chunkText.isEmpty()) {
            return ChunkLayoutResult(
                chunkStart = key.chunkStart,
                chunkText = chunkText,
                pageStarts = intArrayOf(key.chunkStart)
            )
        }

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spToPx(config.fontSizeSp, constraints.density, constraints.fontScale)
        }
        val layout = StaticLayout.Builder
            .obtain(chunkText, 0, chunkText.length, textPaint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, config.lineHeightMult)
            .apply {
                if (config.hyphenation) {
                    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                }
            }
            .build()

        val starts = IntArrayList(24)
        starts.addSortedUnique(key.chunkStart)

        var pageStartLine = 0
        for (line in 0 until layout.lineCount) {
            val top = layout.getLineTop(pageStartLine)
            val bottom = layout.getLineBottom(line)
            if (bottom - top > heightPx) {
                val localStart = layout.getLineStart(line).coerceAtLeast(0)
                val absoluteStart = (key.chunkStart + localStart)
                    .coerceIn(key.chunkStart, key.chunkStart + chunkText.length)
                if (!starts.addSortedUnique(absoluteStart)) {
                    starts.addSortedUnique((absoluteStart + 1).coerceAtMost(key.chunkStart + chunkText.length))
                }
                pageStartLine = line
            }
        }

        return ChunkLayoutResult(
            chunkStart = key.chunkStart,
            chunkText = chunkText,
            pageStarts = starts.toIntArrayCopy()
        )
    }

    private fun dpToPx(dp: Float, density: Float): Int = (dp * density).roundToInt()

    private fun spToPx(sp: Float, density: Float, fontScale: Float): Float = sp * density * fontScale

    private fun alignDown(value: Int, quantum: Int): Int {
        if (quantum <= 1) return value.coerceAtLeast(0)
        return ((value.coerceAtLeast(0) / quantum) * quantum).coerceAtLeast(0)
    }

    private suspend fun buildSingleCharFallback(start: Int, total: Int): PageSlice {
        val safeStart = start.coerceIn(0, total - 1)
        val end = (safeStart + 1).coerceAtMost(total)
        val text = store.readRange(safeStart, end)
        return PageSlice(
            startChar = safeStart,
            endChar = end,
            text = text.ifEmpty { " " }
        )
    }

    private fun resolvePageEnd(
        starts: IntArray,
        pageIndex: Int,
        chunkEnd: Int,
        totalChars: Int,
        pageStart: Int
    ): Int {
        val rawEnd = if (pageIndex + 1 < starts.size) {
            starts[pageIndex + 1].coerceIn(pageStart, totalChars)
        } else {
            chunkEnd.coerceIn(pageStart, totalChars)
        }
        return if (rawEnd <= pageStart) {
            min(totalChars, pageStart + 1)
        } else {
            rawEnd
        }
    }

    private fun floorIndex(values: IntArray, target: Int): Int {
        var lo = 0
        var hi = values.lastIndex
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val candidate = values[mid]
            if (candidate <= target) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
