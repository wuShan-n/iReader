@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan

object SoftBreakProcessor {

    fun process(
        rawText: String,
        hardWrapLikely: Boolean,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ): CharSequence {
        val normalized = if (hardWrapLikely) {
            normalizeSoftBreaks(
                text = rawText,
                preservePunctuationBoundaries = false
            )
        } else {
            NormalizedText(rawText, collectHardBreakPositions(rawText))
        }
        if (paragraphSpacingPx <= 0 && paragraphIndentPx <= 0) {
            return normalized.text
        }
        val builder = SpannableStringBuilder(normalized.text)
        decorateParagraphs(
            builder = builder,
            hardBreakPositions = normalized.hardBreakPositions,
            paragraphSpacingPx = paragraphSpacingPx,
            paragraphIndentPx = paragraphIndentPx,
            startsAtParagraphBoundary = startsAtParagraphBoundary
        )
        return builder
    }

    fun decorateParagraphs(
        builder: SpannableStringBuilder,
        hardBreakPositions: IntArray,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ) {
        if (builder.isEmpty() || (paragraphSpacingPx <= 0 && paragraphIndentPx <= 0)) {
            return
        }

        var paragraphStart = if (startsAtParagraphBoundary) 0 else -1
        hardBreakPositions.forEach { index ->
            if (index !in 0 until builder.length) {
                return@forEach
            }
            if (paragraphSpacingPx > 0) {
                applyParagraphSpacing(builder, index, paragraphSpacingPx)
            }
            if (paragraphIndentPx > 0) {
                applyParagraphIndent(builder, paragraphStart, index + 1, paragraphIndentPx)
            }
            val nextStart = index + 1
            paragraphStart = if (nextStart < builder.length && builder[nextStart] != '\n') {
                nextStart
            } else {
                -1
            }
        }
        if (paragraphIndentPx > 0) {
            applyParagraphIndent(builder, paragraphStart, builder.length, paragraphIndentPx)
        }
    }

    private fun normalizeSoftBreaks(
        text: String,
        preservePunctuationBoundaries: Boolean
    ): NormalizedText {
        if (text.isEmpty()) {
            return NormalizedText("", intArrayOf())
        }
        val chars = text.toCharArray()
        val hardBreaks = ArrayList<Int>()
        var i = 0
        while (i < chars.size) {
            if (chars[i] == '\n') {
                if (
                    shouldTreatAsSoftBreak(
                        chars = chars,
                        index = i,
                        preservePunctuationBoundaries = preservePunctuationBoundaries
                    )
                ) {
                    chars[i] = ' '
                } else {
                    hardBreaks += i
                }
            }
            i++
        }
        return NormalizedText(String(chars), hardBreaks.toIntArray())
    }

    private fun startsWithListMarker(chars: CharArray, startIndex: Int): Boolean {
        var index = startIndex
        while (index < chars.size) {
            val ch = chars[index]
            if (ch != ' ' && ch != '\t' && ch != '\u3000') {
                break
            }
            index++
        }
        if (index !in chars.indices) {
            return false
        }
        val marker = chars[index]
        if (marker == '-' || marker == '*' || marker == '•' || marker == '·') {
            return true
        }
        if (!marker.isDigit()) {
            return false
        }
        val next = if (index + 1 < chars.size) chars[index + 1] else null
        return next == '.' || next == '、' || next == ')' || next == '）'
    }

    private fun lineStartsBoundaryMarker(chars: CharArray, startIndex: Int): Boolean {
        var index = startIndex
        while (index < chars.size) {
            val ch = chars[index]
            if (ch == '\n') {
                return false
            }
            if (ch != ' ' && ch != '\t' && ch != '\u3000') {
                break
            }
            index++
        }
        if (index >= chars.size) {
            return false
        }

        var endExclusive = index
        while (endExclusive < chars.size && chars[endExclusive] != '\n' && (endExclusive - index) < 80) {
            endExclusive++
        }
        if (endExclusive <= index) {
            return false
        }

        val line = String(chars, index, endExclusive - index).trim()
        if (line.isEmpty()) {
            return false
        }
        return CHINESE_CHAPTER_REGEX.matches(line) ||
            ENGLISH_CHAPTER_REGEX.containsMatchIn(line) ||
            DIRECTORY_TITLE_REGEX.matches(line)
    }

    private fun collectHardBreakPositions(text: String): IntArray {
        if (text.isEmpty()) {
            return intArrayOf()
        }
        val positions = ArrayList<Int>()
        text.forEachIndexed { index, ch ->
            if (ch == '\n') {
                positions += index
            }
        }
        return positions.toIntArray()
    }

    private fun applyParagraphSpacing(
        builder: SpannableStringBuilder,
        lineBreakIndex: Int,
        paragraphSpacingPx: Int
    ) {
        val next = if (lineBreakIndex + 1 < builder.length) builder[lineBreakIndex + 1] else null
        if (next == '\n') {
            return
        }
        builder.setSpan(
            ParagraphSpacingSpan(paragraphSpacingPx),
            lineBreakIndex,
            lineBreakIndex + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun applyParagraphIndent(
        builder: SpannableStringBuilder,
        paragraphStart: Int,
        paragraphEndExclusive: Int,
        paragraphIndentPx: Int
    ) {
        if (paragraphStart < 0 || paragraphStart >= paragraphEndExclusive || paragraphIndentPx <= 0) {
            return
        }
        if (!shouldIndentParagraph(builder, paragraphStart, paragraphEndExclusive)) {
            return
        }
        builder.setSpan(
            LeadingMarginSpan.Standard(paragraphIndentPx, 0),
            paragraphStart,
            paragraphEndExclusive,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun shouldIndentParagraph(
        text: CharSequence,
        paragraphStart: Int,
        paragraphEndExclusive: Int
    ): Boolean {
        var index = paragraphStart
        while (index < paragraphEndExclusive) {
            val ch = text[index]
            if (ch == '\n') {
                return false
            }
            if (!ch.isWhitespace() && ch != '\u3000') {
                break
            }
            index++
        }
        if (index >= paragraphEndExclusive) {
            return false
        }
        val visibleLength = paragraphEndExclusive - index
        if (visibleLength < MIN_INDENT_PARAGRAPH_CHARS) {
            return false
        }
        val snippet = text.subSequence(index, minOf(paragraphEndExclusive, index + 28)).toString()
        if (CHINESE_CHAPTER_REGEX.matches(snippet)) {
            return false
        }
        if (ENGLISH_CHAPTER_REGEX.containsMatchIn(snippet)) {
            return false
        }
        return true
    }

    private fun shouldTreatAsSoftBreak(
        chars: CharArray,
        index: Int,
        preservePunctuationBoundaries: Boolean
    ): Boolean {
        val prev = if (index > 0) chars[index - 1] else null
        val next = if (index + 1 < chars.size) chars[index + 1] else null

        if (prev == null || next == null) {
            return false
        }
        if (prev == '\n' || next == '\n') {
            return false
        }
        if (preservePunctuationBoundaries && prev in STRONG_PARAGRAPH_PUNCTUATION) {
            return false
        }
        if (next == ' ' || next == '\t' || next == '\u3000') {
            return false
        }
        if (startsWithListMarker(chars, index + 1)) {
            return false
        }

        // Chapter and directory markers should remain as paragraph boundaries.
        if (lineStartsBoundaryMarker(chars, index + 1)) {
            return false
        }
        return true
    }

    private val STRONG_PARAGRAPH_PUNCTUATION = setOf('。', '！', '？', '.', '!', '?', ';', '；', ':', '：')
    private const val MIN_INDENT_PARAGRAPH_CHARS = 12
    private val CHINESE_CHAPTER_REGEX = Regex("^\\s*第[0-9一二三四五六七八九十百千零〇两\\d]+[章节卷回部篇集].*")
    private val ENGLISH_CHAPTER_REGEX = Regex("^\\s*(chapter|part|prologue|epilogue)\\b", RegexOption.IGNORE_CASE)
    private val DIRECTORY_TITLE_REGEX = Regex("^(目录|目\\s*录|contents)$", RegexOption.IGNORE_CASE)

    private data class NormalizedText(
        val text: String,
        val hardBreakPositions: IntArray
    )
}
