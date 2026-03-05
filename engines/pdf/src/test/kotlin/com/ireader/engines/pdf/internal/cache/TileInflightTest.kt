package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileInflightTest {

    @Test
    fun `getOrAwait deduplicates same key`() = runTest {
        val inflight = TileInflight()
        val key = key()
        val calls = AtomicInteger(0)
        val shared = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        val d1 = async {
            inflight.getOrAwait(key, this@runTest) {
                calls.incrementAndGet()
                shared
            }
        }
        val d2 = async {
            inflight.getOrAwait(key, this@runTest) {
                calls.incrementAndGet()
                shared
            }
        }

        val r1 = d1.await()
        val r2 = d2.await()

        assertEquals(1, calls.get())
        assertSame(shared, r1)
        assertSame(r1, r2)
    }

    @Test
    fun `clear cancels inflight jobs`() = runTest {
        val inflight = TileInflight()
        val key = key()
        val started = AtomicInteger(0)

        val waiter = async {
            runCatching {
                inflight.getOrAwait(key, this@runTest) {
                    started.incrementAndGet()
                    delay(10_000)
                    error("unreachable")
                }
            }
        }

        while (started.get() == 0) {
            delay(1)
        }
        inflight.clear()

        val result = waiter.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    private fun key(): TileCacheKey {
        return TileCacheKey(
            pageIndex = 1,
            leftPx = 0,
            topPx = 0,
            widthPx = 128,
            heightPx = 128,
            scaleMilli = 1000,
            quality = RenderPolicy.Quality.DRAFT,
            rotationDegrees = 0,
            zoomBucketMilli = 1000
        )
    }
}
