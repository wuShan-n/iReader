package com.ireader.engines.epub.internal.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class PathResolverTest {

    @Test
    fun `normalizePath handles dot segments`() {
        assertEquals("OPS/chapter1.xhtml", PathResolver.normalizePath("OPS/./Text/../chapter1.xhtml"))
    }

    @Test
    fun `resolveFrom uses file directory as base`() {
        val resolved = PathResolver.resolveFrom(
            baseFileRel = "OPS/nav/nav.xhtml",
            href = "../Text/ch01.xhtml#frag"
        )
        assertEquals("OPS/Text/ch01.xhtml", resolved)
    }

    @Test
    fun `fragmentOf returns empty when absent`() {
        assertEquals("", PathResolver.fragmentOf("OPS/ch01.xhtml"))
        assertEquals("p3", PathResolver.fragmentOf("OPS/ch01.xhtml#p3"))
    }
}
