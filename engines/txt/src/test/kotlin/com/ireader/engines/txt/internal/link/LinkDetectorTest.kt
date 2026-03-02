package com.ireader.engines.txt.internal.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {

    @Test
    fun detect_shouldFindUrlAndEmailAndNormalize() {
        val text = "Docs: www.example.com, API: https://a.b/c?q=1, mail: foo.bar@test.io."
        val links = LinkDetector.detect(text)

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
    }
}
