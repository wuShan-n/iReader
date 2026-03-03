package com.ireader.engines.common.android.layout

import android.text.Layout
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StaticLayoutMeasurerTest {

    @Test
    fun `maps break strategy modes to android constants`() {
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, StaticLayoutMeasurer.breakStrategy(BreakStrategyMode.SIMPLE))
        assertEquals(Layout.BREAK_STRATEGY_BALANCED, StaticLayoutMeasurer.breakStrategy(BreakStrategyMode.BALANCED))
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, StaticLayoutMeasurer.breakStrategy(BreakStrategyMode.HIGH_QUALITY))
    }

    @Test
    fun `maps hyphenation mode to android constants`() {
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, StaticLayoutMeasurer.hyphenationFrequency(HyphenationMode.NONE))
        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, StaticLayoutMeasurer.hyphenationFrequency(HyphenationMode.NORMAL))
        assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, StaticLayoutMeasurer.hyphenationFrequency(HyphenationMode.FULL))
    }

    @Test
    fun `maps text alignment to justification mode`() {
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, StaticLayoutMeasurer.justificationMode(TextAlignMode.START))
        assertEquals(
            Layout.JUSTIFICATION_MODE_INTER_WORD,
            StaticLayoutMeasurer.justificationMode(TextAlignMode.JUSTIFY)
        )
    }
}
