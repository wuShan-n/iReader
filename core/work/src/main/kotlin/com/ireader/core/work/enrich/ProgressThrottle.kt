package com.ireader.core.work.enrich

class ProgressThrottle(
    private val minIntervalMs: Long = 500L
) {
    private var lastUpdateMs: Long = 0L

    fun shouldUpdate(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - lastUpdateMs < minIntervalMs) {
            return false
        }
        lastUpdateMs = nowMs
        return true
    }

    fun force(nowMs: Long = System.currentTimeMillis()) {
        lastUpdateMs = nowMs
    }
}
