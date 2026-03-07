package com.ireader.feature.reader.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RenderCoordinatorTest {

    @Test
    fun `render requests should be coalesced`() = runTest {
        var renderCount = 0
        val coordinator = RenderCoordinator(scope = this) {
            renderCount++
        }

        runCurrent()
        coordinator.requestRender(RenderRequest.OPEN)
        coordinator.requestRender(RenderRequest.CONFIG)
        coordinator.requestRender(RenderRequest.REFRESH)

        advanceTimeBy(30L)
        runCurrent()

        assertEquals(1, renderCount)
        coordinator.cancel()
    }

    @Test
    fun `immediate render request should bypass debounce`() = runTest {
        var renderCount = 0
        val coordinator = RenderCoordinator(scope = this) {
            renderCount++
        }

        runCurrent()
        coordinator.requestImmediateRender(RenderRequest.OPEN)
        runCurrent()

        assertEquals(1, renderCount)
        coordinator.cancel()
    }

    @Test
    fun `navigation lock should serialize blocks`() = runTest {
        val coordinator = RenderCoordinator(scope = this) {}
        val calls = mutableListOf<String>()

        val first = launch {
            coordinator.withNavigationLock {
                calls += "first-start"
                calls += "first-end"
            }
        }
        val second = launch {
            coordinator.withNavigationLock {
                calls += "second-start"
                calls += "second-end"
            }
        }

        runCurrent()
        first.join()
        second.join()

        assertEquals(
            listOf("first-start", "first-end", "second-start", "second-end"),
            calls
        )
        coordinator.cancel()
    }
}
