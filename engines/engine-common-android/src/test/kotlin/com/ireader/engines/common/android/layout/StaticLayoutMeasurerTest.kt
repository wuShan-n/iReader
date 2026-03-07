package com.ireader.engines.common.android.layout

import android.graphics.text.LineBreakConfig
import android.text.Layout
import com.ireader.core.common.android.typography.AndroidTextLayoutKind
import com.ireader.core.common.android.typography.resolveAndroidTextLayoutProfile
import com.ireader.core.common.android.typography.toEffectiveTxtBreakStrategy
import com.ireader.core.common.android.typography.txtAndroidLineBreakConfig
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.core.common.android.typography.toTxtAndroidJustificationMode
import com.ireader.core.common.android.typography.toAndroidLayoutAlignment
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StaticLayoutMeasurerTest {

    @Test
    fun `maps break strategy modes to android constants`() {
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, BreakStrategyMode.SIMPLE.toAndroidBreakStrategy())
        assertEquals(Layout.BREAK_STRATEGY_BALANCED, BreakStrategyMode.BALANCED.toAndroidBreakStrategy())
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, BreakStrategyMode.HIGH_QUALITY.toAndroidBreakStrategy())
    }

    @Test
    fun `maps hyphenation mode to android constants`() {
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, HyphenationMode.NONE.toAndroidHyphenationFrequency())
        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, HyphenationMode.NORMAL.toAndroidHyphenationFrequency())
        assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, HyphenationMode.FULL.toAndroidHyphenationFrequency())
    }

    @Test
    fun `maps text alignment to android layout and justification modes`() {
        assertEquals(Layout.Alignment.ALIGN_NORMAL, TextAlignMode.START.toAndroidLayoutAlignment())
        assertEquals(Layout.Alignment.ALIGN_NORMAL, TextAlignMode.JUSTIFY.toAndroidLayoutAlignment())
        assertEquals(
            Layout.JUSTIFICATION_MODE_NONE,
            TextAlignMode.START.toAndroidJustificationMode()
        )
        assertEquals(
            Layout.JUSTIFICATION_MODE_INTER_WORD,
            TextAlignMode.JUSTIFY.toAndroidJustificationMode()
        )
        assertEquals(
            Layout.JUSTIFICATION_MODE_INTER_CHARACTER,
            TextAlignMode.JUSTIFY.toTxtAndroidJustificationMode()
        )
    }

    @Test
    fun `txt line break config should disable strict cjk overrides`() {
        val config = txtAndroidLineBreakConfig()
        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_NONE, config.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, config.lineBreakWordStyle)
    }

    @Test
    fun `txt effective break strategy should downgrade balanced to simple`() {
        assertEquals(BreakStrategyMode.SIMPLE, BreakStrategyMode.BALANCED.toEffectiveTxtBreakStrategy())
        assertEquals(BreakStrategyMode.HIGH_QUALITY, BreakStrategyMode.HIGH_QUALITY.toEffectiveTxtBreakStrategy())
    }

    @Test
    fun `text layout profile should separate generic and txt behavior`() {
        val generic = resolveAndroidTextLayoutProfile(
            kind = AndroidTextLayoutKind.GENERIC,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = BreakStrategyMode.BALANCED,
            hyphenationMode = HyphenationMode.NORMAL
        )
        val txt = resolveAndroidTextLayoutProfile(
            kind = AndroidTextLayoutKind.TXT,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = BreakStrategyMode.BALANCED,
            hyphenationMode = HyphenationMode.NORMAL
        )

        assertEquals(BreakStrategyMode.BALANCED, generic.breakStrategy)
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_WORD, generic.justificationMode)
        assertEquals(BreakStrategyMode.SIMPLE, txt.breakStrategy)
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_CHARACTER, txt.justificationMode)
        assertEquals(LineBreakConfig.LINE_BREAK_STYLE_NONE, txt.lineBreakConfig.lineBreakStyle)
        assertEquals(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, txt.lineBreakConfig.lineBreakWordStyle)
    }
}
