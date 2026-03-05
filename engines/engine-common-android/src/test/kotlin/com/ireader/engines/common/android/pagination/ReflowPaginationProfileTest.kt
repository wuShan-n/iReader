package com.ireader.engines.common.android.pagination

import com.ireader.engines.common.android.reflow.SOFT_BREAK_PROFILE_EXTRA_KEY
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class ReflowPaginationProfileTest {

    private val constraints = LayoutConstraints(
        viewportWidthPx = 1080,
        viewportHeightPx = 2400,
        density = 3f,
        fontScale = 1f
    )

    @Test
    fun `same config should produce stable key`() {
        val config = RenderConfig.ReflowText()
        val left = ReflowPaginationProfile.keyFor("doc-a", constraints, config)
        val right = ReflowPaginationProfile.keyFor("doc-a", constraints, config)
        assertEquals(left, right)
    }

    @Test
    fun `typography toggles should change key`() {
        val base = RenderConfig.ReflowText()
        val baseKey = ReflowPaginationProfile.keyFor("doc-a", constraints, base)

        val breakStrategyKey = ReflowPaginationProfile.keyFor(
            "doc-a",
            constraints,
            base.copy(breakStrategy = BreakStrategyMode.HIGH_QUALITY)
        )
        val hyphenationKey = ReflowPaginationProfile.keyFor(
            "doc-a",
            constraints,
            base.copy(hyphenationMode = HyphenationMode.NONE)
        )

        assertNotEquals(baseKey, breakStrategyKey)
        assertNotEquals(baseKey, hyphenationKey)
    }

    @Test
    fun `soft break profile should change key`() {
        val balanced = RenderConfig.ReflowText(
            extra = mapOf(SOFT_BREAK_PROFILE_EXTRA_KEY to "balanced")
        )
        val strict = balanced.copy(
            extra = mapOf(SOFT_BREAK_PROFILE_EXTRA_KEY to "strict")
        )

        val balancedKey = ReflowPaginationProfile.keyFor("doc-a", constraints, balanced)
        val strictKey = ReflowPaginationProfile.keyFor("doc-a", constraints, strict)

        assertNotEquals(balancedKey, strictKey)
    }

    @Test
    fun `locale and tiny float noise should keep key stable`() {
        val original = Locale.getDefault()
        val base = RenderConfig.ReflowText(
            fontSizeSp = 18.0000f,
            extra = mapOf(SOFT_BREAK_PROFILE_EXTRA_KEY to "AGGRESSIVE")
        )
        val noisy = base.copy(fontSizeSp = 18.0003f)
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val trKey = ReflowPaginationProfile.keyFor("doc-a", constraints, base)
            val noisyKey = ReflowPaginationProfile.keyFor("doc-a", constraints, noisy)
            Locale.setDefault(Locale.US)
            val usKey = ReflowPaginationProfile.keyFor("doc-a", constraints, base)
            assertEquals(usKey, trKey)
            assertEquals(usKey, noisyKey)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `schema version marker should invalidate legacy profile key`() {
        val base = RenderConfig.ReflowText()
        val currentKey = ReflowPaginationProfile.keyFor("doc-a", constraints, base)
        val legacyRaw = buildString {
            append("doc-a")
            append('|')
            append(constraints.viewportWidthPx)
            append('x')
            append(constraints.viewportHeightPx)
            append('|')
            append(constraints.density)
            append('|')
            append(constraints.fontScale)
            append('|')
            append(base.fontSizeSp)
            append('|')
            append(base.lineHeightMult)
            append('|')
            append(base.paragraphSpacingDp)
            append('|')
            append(base.pagePaddingDp)
            append('|')
            append(base.fontFamilyName.orEmpty())
            append('|')
            append(base.breakStrategy)
            append('|')
            append(base.hyphenationMode)
            append('|')
            append(base.includeFontPadding)
            append('|')
            append(base.pageInsetMode)
        }
        val legacyKey = com.ireader.engines.common.hash.Hashing.sha256Hex(legacyRaw).take(16)
        assertNotEquals(legacyKey, currentKey)
    }
}
