package com.ireader.core.work.enrich

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {

    @Test
    fun `shouldUpdate should throttle within interval`() {
        val throttle = ProgressThrottle(minIntervalMs = 500L)

        assertTrue(throttle.shouldUpdate(nowMs = 1000L))
        assertFalse(throttle.shouldUpdate(nowMs = 1200L))
        assertTrue(throttle.shouldUpdate(nowMs = 1601L))
    }

    @Test
    fun `force should reset baseline time`() {
        val throttle = ProgressThrottle(minIntervalMs = 300L)

        assertTrue(throttle.shouldUpdate(nowMs = 1000L))
        throttle.force(nowMs = 1300L)
        assertFalse(throttle.shouldUpdate(nowMs = 1400L))
        assertTrue(throttle.shouldUpdate(nowMs = 1701L))
    }
}
