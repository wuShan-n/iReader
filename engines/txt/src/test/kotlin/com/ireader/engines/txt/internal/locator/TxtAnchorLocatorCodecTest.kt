package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtAnchorLocatorCodecTest {

    @Test
    fun `locatorForOffset should encode txt anchor`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(8_192), "anchor-codec-encode", Dispatchers.IO)
        try {
            val locator = TxtAnchorLocatorCodec.locatorForOffset(
                offset = 2_121L,
                blockIndex = fixture.blockIndex,
                revision = fixture.meta.contentRevision
            )
            assertEquals(LocatorSchemes.TXT_ANCHOR, locator.scheme)
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
                TxtAnchorLocatorCodec.parseOffset(
                    locator = locator,
                    blockIndex = fixture.blockIndex,
                    expectedRevision = fixture.meta.contentRevision
                )
            )
            assertEquals(
                2_000L,
                TxtAnchorLocatorCodec.parseOffset(
                    locator = locator,
                    blockIndex = fixture.blockIndex,
                    expectedRevision = fixture.meta.contentRevision,
                    maxOffset = 2_000L
                )
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `parseOffset should reject invalid values`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(8_192), "anchor-codec-invalid", Dispatchers.IO)
        try {
            assertNull(
                TxtAnchorLocatorCodec.parseOffset(
                    locator = Locator(LocatorSchemes.TXT_ANCHOR, "2048:999:f:1"),
                    blockIndex = fixture.blockIndex,
                    expectedRevision = fixture.meta.contentRevision
                )
            )
            assertNull(
                TxtAnchorLocatorCodec.parseOffset(
                    locator = Locator(LocatorSchemes.TXT_ANCHOR, "-1:0:f:1"),
                    blockIndex = fixture.blockIndex,
                    expectedRevision = fixture.meta.contentRevision
                )
            )
        } finally {
            fixture.close()
        }
    }
}
