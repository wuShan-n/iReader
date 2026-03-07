package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TxtTextMappingTest {

    @Test
    fun `locatorAt maps page local index to global offset`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(1_024), "mapping-locator-at", Dispatchers.IO)
        try {
            val mapping = TxtTextMapping(
                pageStart = 100L,
                pageEnd = 150L,
                blockIndex = fixture.blockIndex,
                revision = fixture.meta.contentRevision
            )
            val locator = mapping.locatorAt(12)
            assertEquals(LocatorSchemes.TXT_ANCHOR, locator.scheme)
            assertEquals(112L, fixture.parseOffset(locator))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `charRangeFor returns local range when locator range is on page`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(1_024), "mapping-on-page", Dispatchers.IO)
        try {
            val mapping = TxtTextMapping(
                pageStart = 200L,
                pageEnd = 260L,
                blockIndex = fixture.blockIndex,
                revision = fixture.meta.contentRevision
            )
            val range = LocatorRange(
                start = fixture.locatorFor(205L),
                end = fixture.locatorFor(210L)
            )
            val local = mapping.charRangeFor(range)
            assertNotNull(local)
            assertEquals(5, local!!.first)
            assertEquals(9, local.last)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `charRangeFor returns null when range is outside page`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(1_024), "mapping-outside", Dispatchers.IO)
        try {
            val mapping = TxtTextMapping(
                pageStart = 20L,
                pageEnd = 40L,
                blockIndex = fixture.blockIndex,
                revision = fixture.meta.contentRevision
            )
            val range = LocatorRange(
                start = fixture.locatorFor(18L),
                end = fixture.locatorFor(25L)
            )
            assertNull(mapping.charRangeFor(range))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `locatorAt should clamp values after page end`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(1_024), "mapping-clamp", Dispatchers.IO)
        try {
            val mapping = TxtTextMapping(
                pageStart = 30L,
                pageEnd = 50L,
                blockIndex = fixture.blockIndex,
                revision = fixture.meta.contentRevision
            )
            val locator = mapping.locatorAt(999)
            assertEquals(50L, fixture.parseOffset(locator))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `rangeFor should normalize reversed range`() = runBlocking {
        val fixture = buildTxtRuntimeFixture("x".repeat(1_024), "mapping-range", Dispatchers.IO)
        try {
            val mapping = TxtTextMapping(
                pageStart = 500L,
                pageEnd = 560L,
                blockIndex = fixture.blockIndex,
                revision = fixture.meta.contentRevision
            )
            val range = mapping.rangeFor(startChar = 30, endChar = 12)
            assertEquals(512L, fixture.parseOffset(range.start))
            assertEquals(530L, fixture.parseOffset(range.end))
        } finally {
            fixture.close()
        }
    }
}
