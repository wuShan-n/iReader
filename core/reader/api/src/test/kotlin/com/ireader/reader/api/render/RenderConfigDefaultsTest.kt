package com.ireader.reader.api.render

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderConfigDefaultsTest {

    @Test
    fun `reflow default break strategy should be balanced`() {
        assertEquals(BreakStrategyMode.BALANCED, RenderConfig.ReflowText().breakStrategy)
    }
}
