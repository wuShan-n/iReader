package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtStableLocatorCodecTest {

    @Test
    fun `locatorForOffset should encode txt anchor`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(8_192), "anchor-codec-encode", Dispatchers.IO)
        try {
            val locator = TxtStableLocatorCodec.locatorForOffset(
                offset = 2_121L,
                blockIndex = fixture.blockIndex,
                contentFingerprint = fixture.meta.contentFingerprint,
                maxOffset = fixture.blockIndex.lengthCodeUnits,
                projectionEngine = fixture.projectionEngine
            )
            assertEquals(LocatorSchemes.TXT_STABLE_ANCHOR, locator.scheme)
            assertEquals(2_121L, fixture.parseOffset(locator))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `parseOffset should decode txt anchor and clamp by max`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(8_192), "anchor-codec-parse", Dispatchers.IO)
        try {
            val locator = fixture.locatorFor(2_121L)
            assertEquals(
                2_121L,
                TxtStableLocatorCodec.parseOffset(
                    locator = locator,
                    blockIndex = fixture.blockIndex,
                    contentFingerprint = fixture.meta.contentFingerprint,
                    maxOffset = fixture.blockIndex.lengthCodeUnits,
                    projectionEngine = fixture.projectionEngine
                )
            )
            val clamped = TxtStableLocatorCodec.parseOffset(
                locator = locator,
                blockIndex = fixture.blockIndex,
                contentFingerprint = fixture.meta.contentFingerprint,
                maxOffset = 2_000L,
                projectionEngine = fixture.projectionEngine
            )
            assertTrue(clamped != null && clamped in 0L..2_000L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `parseOffset should reject invalid values`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(8_192), "anchor-codec-invalid", Dispatchers.IO)
        try {
            assertNull(
                TxtStableLocatorCodec.parseOffset(
                    locator = Locator(LocatorSchemes.TXT_STABLE_ANCHOR, "2048"),
                    blockIndex = fixture.blockIndex,
                    contentFingerprint = fixture.meta.contentFingerprint,
                    maxOffset = fixture.blockIndex.lengthCodeUnits,
                    projectionEngine = fixture.projectionEngine
                )
            )
            assertNull(
                TxtStableLocatorCodec.parseOffset(
                    locator = Locator(LocatorSchemes.TXT_STABLE_ANCHOR, "-1:0"),
                    blockIndex = fixture.blockIndex,
                    contentFingerprint = fixture.meta.contentFingerprint,
                    maxOffset = fixture.blockIndex.lengthCodeUnits,
                    projectionEngine = fixture.projectionEngine
                )
            )
        } finally {
            fixture.close()
        }
    }
}
