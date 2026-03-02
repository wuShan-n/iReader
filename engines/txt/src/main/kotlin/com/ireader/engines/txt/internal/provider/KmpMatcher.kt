package com.ireader.engines.txt.internal.provider

internal class KmpMatcher(
    pattern: CharArray,
    caseSensitive: Boolean
) {
    private val normalizedPattern = if (caseSensitive) {
        pattern
    } else {
        CharArray(pattern.size) { index -> pattern[index].lowercaseChar() }
    }
    private val lps = buildLps(normalizedPattern)
    private val ignoreCase = !caseSensitive

    fun findAll(text: CharArray): List<Int> {
        if (normalizedPattern.isEmpty() || text.isEmpty()) {
            return emptyList()
        }
        val hits = ArrayList<Int>()
        var i = 0
        var j = 0
        while (i < text.size) {
            val textChar = normalize(text[i])
            val patternChar = normalizedPattern[j]
            if (textChar == patternChar) {
                i++
                j++
                if (j == normalizedPattern.size) {
                    hits.add(i - j)
                    j = lps[j - 1]
                }
            } else if (j != 0) {
                j = lps[j - 1]
            } else {
                i++
            }
        }
        return hits
    }

    private fun normalize(c: Char): Char {
        return if (ignoreCase) c.lowercaseChar() else c
    }

    private fun buildLps(pattern: CharArray): IntArray {
        val out = IntArray(pattern.size)
        var len = 0
        var i = 1
        while (i < pattern.size) {
            if (pattern[i] == pattern[len]) {
                len++
                out[i] = len
                i++
            } else if (len != 0) {
                len = out[len - 1]
            } else {
                out[i] = 0
                i++
            }
        }
        return out
    }
}

