package com.ireader.engines.epub.internal.open

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object EpubLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withDocLock(docId: String, block: suspend () -> T): T {
        val mutex = locks.getOrPut(docId) { Mutex() }
        return mutex.withLock { block() }
    }
}
