package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TileInflight(
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val inflight = mutableMapOf<TileCacheKey, Deferred<Bitmap>>()

    suspend fun getOrAwait(key: TileCacheKey, block: suspend () -> Bitmap): Bitmap {
        val existing = mutex.withLock { inflight[key] }
        if (existing != null) return existing.await()

        val created = scope.async { block() }
        val actual = mutex.withLock {
            val already = inflight[key]
            if (already != null) {
                created.cancel()
                already
            } else {
                inflight[key] = created
                created
            }
        }

        return try {
            actual.await()
        } finally {
            mutex.withLock {
                if (inflight[key] == actual) {
                    inflight.remove(key)
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            inflight.values.forEach { deferred -> deferred.cancel() }
            inflight.clear()
        }
    }
}

