package com.ireader.engines.txt.internal.link

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.model.LocatorSchemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {

    @Test
    fun detect_shouldFindUrlAndEmailAndNormalize() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "x".repeat(2_000),
            sampleHash = "link-detector",
            ioDispatcher = Dispatchers.IO
        )
        try {
            val text = "Docs: www.example.com, API: https://a.b/c?q=1, mail: foo.bar@test.io."
            val links = LinkDetector.detect(
                text = text,
                pageStartOffset = 100L,
                blockIndex = fixture.blockIndex,
                contentFingerprint = fixture.meta.contentFingerprint,
                projectionEngine = fixture.projectionEngine
            )

            assertEquals(3, links.size)
            val urls = links.mapNotNull { link ->
                when (val target = link.target) {
                    is com.ireader.reader.model.LinkTarget.External -> target.url
                    else -> null
                }
            }
            assertTrue(urls.contains("http://www.example.com"))
            assertTrue(urls.contains("https://a.b/c?q=1"))
            assertTrue(urls.contains("mailto:foo.bar@test.io"))
            links.forEach { link ->
                val range = link.range
                assertNotNull(range)
                assertEquals(LocatorSchemes.TXT_STABLE_ANCHOR, range!!.start.scheme)
                assertEquals(LocatorSchemes.TXT_STABLE_ANCHOR, range.end.scheme)
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun detect_shouldReturnEmptyWhenTextHasNoUrlHints() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "x".repeat(1_024),
            sampleHash = "link-detector-empty",
            ioDispatcher = Dispatchers.IO
        )
        try {
            val links = LinkDetector.detect(
                text = "This is a plain paragraph without links or mail addresses.",
                pageStartOffset = 0L,
                blockIndex = fixture.blockIndex,
                contentFingerprint = fixture.meta.contentFingerprint,
                projectionEngine = fixture.projectionEngine
            )
            assertTrue(links.isEmpty())
        } finally {
            fixture.close()
        }
    }
}
