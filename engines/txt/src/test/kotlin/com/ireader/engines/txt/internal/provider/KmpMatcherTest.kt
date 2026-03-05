package com.ireader.engines.txt.internal.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class KmpMatcherTest {

    @Test
    fun `findAll returns all match positions`() {
        val matcher = KmpMatcher("aba".toCharArray(), caseSensitive = true)
        val hits = matcher.findAll("ababa".toCharArray())
        assertEquals(listOf(0, 2), hits)
    }

    @Test
    fun `findAll supports case insensitive matching`() {
        val matcher = KmpMatcher("World".toCharArray(), caseSensitive = false)
        val hits = matcher.findAll("hello world WORLD".toCharArray())
        assertEquals(listOf(6, 12), hits)
    }

    @Test
    fun `forEachMatch supports early stop`() {
        val matcher = KmpMatcher("aba".toCharArray(), caseSensitive = true)
        val hits = mutableListOf<Int>()

        matcher.forEachMatch("ababa".toCharArray()) { index ->
            hits += index
            false
        }

        assertEquals(listOf(0), hits)
    }
}
