package com.ireader.reader.api.render

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderConfigDefaultsTest {

    @Test
    fun `reflow default break strategy should be simple`() {
        assertEquals(BreakStrategyMode.SIMPLE, RenderConfig.ReflowText().breakStrategy)
    }
}
