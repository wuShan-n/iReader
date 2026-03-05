package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

internal class TileInflight {
    private val lock = ReentrantLock()
    private val inflight = HashMap<TileCacheKey, Deferred<Bitmap>>()

    suspend fun getOrAwait(
        key: TileCacheKey,
        scope: CoroutineScope,
        block: suspend () -> Bitmap
    ): Bitmap {
        val deferred = lock.withLock {
            inflight[key] ?: run {
                val created = scope.async(start = CoroutineStart.LAZY) { block() }
                inflight[key] = created

                created
                    .invokeOnCompletion {
                        lock.withLock {
                            if (inflight[key] === created) {
                                inflight.remove(key)
                            }
                        }
                    }

                created
            }
        }

        deferred.start()
        return deferred.await()
    }

    fun clear() {
        val toCancel = lock.withLock {
            val list = inflight.values.toList()
            inflight.clear()
            list
        }
        toCancel.forEach { it.cancel() }
    }
}
