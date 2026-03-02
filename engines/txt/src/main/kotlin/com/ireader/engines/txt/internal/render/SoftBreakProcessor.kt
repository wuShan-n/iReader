@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")

package com.ireader.engines.txt.internal.render

import android.text.SpannableStringBuilder
import android.text.Spanned

internal object SoftBreakProcessor {

    fun process(
        rawText: String,
        hardWrapLikely: Boolean,
        paragraphSpacingPx: Int
    ): CharSequence {
        val normalized = if (hardWrapLikely) {
            replaceSoftBreaks(rawText)
        } else {
            rawText
        }
        if (paragraphSpacingPx <= 0) {
            return normalized
        }
        val builder = SpannableStringBuilder(normalized)
        var index = builder.indexOf('\n')
        while (index >= 0) {
            builder.setSpan(
                ParagraphSpacingSpan(paragraphSpacingPx),
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = builder.indexOf('\n', index + 1)
        }
        return builder
    }

    private fun replaceSoftBreaks(text: String): String {
        if (text.isEmpty()) {
            return text
        }
        val chars = text.toCharArray()
        var i = 0
        while (i < chars.size) {
            if (chars[i] == '\n' && shouldTreatAsSoftBreak(chars, i)) {
                chars[i] = ' '
            }
            i++
        }
        return String(chars)
    }

    private fun shouldTreatAsSoftBreak(chars: CharArray, index: Int): Boolean {
        val prev = if (index > 0) chars[index - 1] else null
        val next = if (index + 1 < chars.size) chars[index + 1] else null

        if (prev == null || next == null) {
            return false
        }
        if (prev == '\n' || next == '\n') {
            return false
        }
        if (prev in STRONG_PARAGRAPH_PUNCTUATION) {
            return false
        }
        if (next == ' ' || next == '\t' || next == '\u3000') {
            return false
        }

        // Chapter markers usually should remain as paragraph boundaries.
        if (next == '第' || next == '章' || next == '回' || next == '卷') {
            return false
        }
        if (next == 'C' || next == 'c' || next == 'P' || next == 'p') {
            return false
        }
        return true
    }

    private fun SpannableStringBuilder.indexOf(char: Char, start: Int = 0): Int {
        var index = start
        while (index < length) {
            if (this[index] == char) {
                return index
            }
            index++
        }
        return -1
    }

    private val STRONG_PARAGRAPH_PUNCTUATION = setOf('。', '！', '？', '.', '!', '?', ';', '；', ':', '：')
}
