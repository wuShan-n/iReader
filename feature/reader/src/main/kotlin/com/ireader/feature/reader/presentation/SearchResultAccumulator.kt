package com.ireader.feature.reader.presentation

internal class SearchResultAccumulator(
    private val flushBatchSize: Int = 20,
    private val flushIntervalMs: Long = 120,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val onFlush: (List<SearchResultItem>) -> Unit
) {
    private val pending = ArrayList<SearchResultItem>(flushBatchSize)
    private var lastFlushAtMs: Long = 0L

    fun add(item: SearchResultItem) {
        pending += item
        val now = nowMs()
        if (pending.size >= flushBatchSize || now - lastFlushAtMs >= flushIntervalMs) {
            flush(now)
        }
    }

    fun flush() {
        flush(nowMs())
    }

    private fun flush(now: Long) {
        if (pending.isEmpty()) return
        onFlush(pending.toList())
        pending.clear()
        lastFlushAtMs = now
    }
}

