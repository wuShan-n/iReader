package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.model.NormalizedRect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

internal class PdfHighlightStore(
    private val maxRectsPerPage: Int = 128
) {
    private val generation = AtomicLong(0L)
    private val map = ConcurrentHashMap<Int, List<NormalizedRect>>()

    private val _updates = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val updates: SharedFlow<Int> = _updates

    fun newSearch(): Long {
        val gen = generation.incrementAndGet()
        map.clear()
        _updates.tryEmit(ALL_PAGES)
        return gen
    }

    fun clearHighlights() {
        generation.incrementAndGet()
        map.clear()
        _updates.tryEmit(ALL_PAGES)
    }

    fun add(pageIndex: Int, rects: List<NormalizedRect>, gen: Long) {
        if (gen != generation.get() || rects.isEmpty()) return

        val merged = (map[pageIndex].orEmpty() + rects)
            .distinctBy { "${it.left},${it.top},${it.right},${it.bottom}" }
            .take(maxRectsPerPage)

        map[pageIndex] = merged
        _updates.tryEmit(pageIndex)
    }

    fun rectsFor(pageIndex: Int): List<NormalizedRect> = map[pageIndex].orEmpty()

    companion object {
        const val ALL_PAGES: Int = -1
    }
}
