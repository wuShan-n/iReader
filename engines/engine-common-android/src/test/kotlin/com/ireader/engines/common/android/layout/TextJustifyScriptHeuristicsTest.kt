package com.ireader.engines.common.android.layout

import com.ireader.core.common.android.typography.prefersInterCharacterJustify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextJustifyScriptHeuristicsTest {

    @Test
    fun `prefers inter-character for cjk text`() {
        val text = "这是用于测试两端对齐策略的中文段落，应当走字间拉伸。".repeat(4)

        assertTrue(text.prefersInterCharacterJustify())
    }

    @Test
    fun `prefers inter-word for latin text`() {
        val text = "This paragraph verifies that english prose keeps inter-word justification. ".repeat(4)

        assertFalse(text.prefersInterCharacterJustify())
    }

    @Test
    fun `prefers inter-character for cjk dominant mixed text`() {
        val text = "第一章 The Hero 在凌晨三点醒来，然后继续向前，直到天亮。".repeat(4)

        assertTrue(text.prefersInterCharacterJustify())
    }

    @Test
    fun `falls back to inter-word when script signal is weak`() {
        val text = "  12345 -- ... !!!  "

        assertFalse(text.prefersInterCharacterJustify())
    }
}
