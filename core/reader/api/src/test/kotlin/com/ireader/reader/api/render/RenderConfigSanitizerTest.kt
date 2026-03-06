package com.ireader.reader.api.render

import com.ireader.reader.model.DocumentCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderConfigSanitizerTest {

    @Test
    fun `sanitize should choose fixed defaults for fixed-layout document`() {
        val capabilities = fixedCapabilities()
        val config = RenderConfig.ReflowText(fontSizeSp = 40f)

        val sanitized = RenderConfigSanitizer.sanitize(config, capabilities)

        assertTrue(sanitized is RenderConfig.FixedPage)
        assertEquals(RenderConfig.FixedPage(), sanitized)
    }

    @Test
    fun `sanitize should choose reflow defaults for reflowable document`() {
        val capabilities = reflowCapabilities()
        val config = RenderConfig.FixedPage(zoom = 4f)

        val sanitized = RenderConfigSanitizer.sanitize(config, capabilities)

        assertTrue(sanitized is RenderConfig.ReflowText)
        assertEquals(RenderConfig.ReflowText(), sanitized)
    }

    @Test
    fun `reflow sanitized should clamp bounds and recover non-finite values`() {
        val dirty = RenderConfig.ReflowText(
            fontSizeSp = Float.NaN,
            lineHeightMult = 9f,
            paragraphSpacingDp = -5f,
            pagePaddingDp = Float.POSITIVE_INFINITY,
            extra = mapOf(
                PAGE_PADDING_TOP_DP_EXTRA_KEY to "999",
                PAGE_PADDING_BOTTOM_DP_EXTRA_KEY to "-5"
            )
        )

        val sanitized = dirty.sanitized()
        val defaults = RenderConfig.ReflowText()

        assertEquals(defaults.fontSizeSp, sanitized.fontSizeSp, 0f)
        assertEquals(REFLOW_LINE_HEIGHT_MAX, sanitized.lineHeightMult, 0f)
        assertEquals(0f, sanitized.paragraphSpacingDp, 0f)
        assertEquals(defaults.pagePaddingDp, sanitized.pagePaddingDp, 0f)
        assertEquals(
            REFLOW_PAGE_PADDING_VERTICAL_MAX_DP.toString(),
            sanitized.extra[PAGE_PADDING_TOP_DP_EXTRA_KEY]
        )
        assertEquals(
            REFLOW_PAGE_PADDING_VERTICAL_MIN_DP.toString(),
            sanitized.extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY]
        )
    }

    @Test
    fun `fixed sanitized should normalize rotation and clamp zoom`() {
        val dirty = RenderConfig.FixedPage(zoom = -1f, rotationDegrees = -450)

        val sanitized = dirty.sanitized()

        assertEquals(0.25f, sanitized.zoom, 0f)
        assertEquals(270, sanitized.rotationDegrees)
    }

    @Test
    fun `fixed sanitized should recover non-finite zoom with default`() {
        val dirty = RenderConfig.FixedPage(zoom = Float.NaN, rotationDegrees = 1080)

        val sanitized = dirty.sanitized()

        assertEquals(RenderConfig.FixedPage().zoom, sanitized.zoom, 0f)
        assertEquals(0, sanitized.rotationDegrees)
    }

    private fun fixedCapabilities(): DocumentCapabilities =
        DocumentCapabilities(
            reflowable = false,
            fixedLayout = true,
            outline = true,
            search = true,
            textExtraction = true,
            annotations = true,
            selection = true,
            links = true
        )

    private fun reflowCapabilities(): DocumentCapabilities =
        DocumentCapabilities(
            reflowable = true,
            fixedLayout = false,
            outline = true,
            search = true,
            textExtraction = true,
            annotations = true,
            selection = true,
            links = true
        )
}
