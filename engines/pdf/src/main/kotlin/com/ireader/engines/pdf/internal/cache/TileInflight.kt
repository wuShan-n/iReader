package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal class TileInflight {
    private val map: ConcurrentHashMap<String, Deferred<Bitmap>> = ConcurrentHashMap()

    suspend fun getOrAwait(key: String, block: suspend () -> Bitmap): Bitmap {
        val existing = map[key]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<Bitmap>()
        val previous = map.putIfAbsent(key, deferred)
        if (previous != null) return previous.await()

        return try {
            val value = block()
            deferred.complete(value)
            value
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            map.remove(key, deferred)
        }
    }

    fun clear() {
        map.clear()
    }
}
