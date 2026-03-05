package com.ireader.engines.txt.internal.link

import com.ireader.reader.model.LocatorSchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {

    @Test
    fun detect_shouldFindUrlAndEmailAndNormalize() {
        val text = "Docs: www.example.com, API: https://a.b/c?q=1, mail: foo.bar@test.io."
        val links = LinkDetector.detect(
            text = text,
            pageStartOffset = 100L,
            maxOffset = 1000L
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
            assertEquals(LocatorSchemes.TXT_OFFSET, range!!.start.scheme)
            assertEquals(LocatorSchemes.TXT_OFFSET, range.end.scheme)
        }
    }

    @Test
    fun detect_shouldReturnEmptyWhenTextHasNoUrlHints() {
        val links = LinkDetector.detect(
            text = "This is a plain paragraph without links or mail addresses.",
            pageStartOffset = 0L,
            maxOffset = 500L
        )
        assertTrue(links.isEmpty())
    }
}
