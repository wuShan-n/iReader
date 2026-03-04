@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.max

object SoftBreakProcessor {

    fun process(
        rawText: String,
        hardWrapLikely: Boolean,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean,
        ruleConfig: SoftBreakRuleConfig = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
    ): CharSequence {
        val normalized = normalizeSoftBreaks(
            text = rawText,
            hardWrapLikely = hardWrapLikely,
            ruleConfig = ruleConfig
        )
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
        hardWrapLikely: Boolean,
        ruleConfig: SoftBreakRuleConfig
    ): NormalizedText {
        if (text.isEmpty()) {
            return NormalizedText("", intArrayOf())
        }
        val chars = text.toCharArray()
        val lines = ArrayList<SoftBreakLineInfo>(max(16, text.length / 48))
        val newlineOffsets = ArrayList<Int>(max(16, text.length / 80))

        var start = 0
        while (start <= chars.size) {
            val newline = text.indexOf('\n', start).let { idx ->
                if (idx >= 0) idx else chars.size
            }
            lines += parseLine(chars, start, newline)
            if (newline >= chars.size) {
                break
            }
            newlineOffsets += newline
            start = newline + 1
        }

        val typical = estimateTypicalLineLength(lines, hardWrapLikely, ruleConfig)
        val context = SoftBreakClassifierContext(
            typicalLineLength = typical,
            hardWrapLikely = hardWrapLikely,
            rules = ruleConfig
        )

        val hardBreaks = ArrayList<Int>()
        for (i in newlineOffsets.indices) {
            val offset = newlineOffsets[i]
            val nextLine = lines.getOrNull(i + 1) ?: EMPTY_LINE
            val decision = SoftBreakClassifier.classify(
                line0 = lines[i],
                line1 = nextLine,
                context = context
            )
            if (decision.isSoft) {
                chars[offset] = ' '
            } else {
                hardBreaks += offset
            }
        }
        return NormalizedText(String(chars), hardBreaks.toIntArray())
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
    private const val MIN_INDENT_PARAGRAPH_CHARS = 12
    private val CHINESE_CHAPTER_REGEX = Regex("^\\s*第[0-9一二三四五六七八九十百千零〇两\\d]+[章节卷回部篇集].*")
    private val ENGLISH_CHAPTER_REGEX = Regex("^\\s*(chapter|part|prologue|epilogue)\\b", RegexOption.IGNORE_CASE)
    private val DIRECTORY_TITLE_REGEX = Regex("^(目录|目\\s*录|contents)$", RegexOption.IGNORE_CASE)
    private val EMPTY_LINE = SoftBreakLineInfo(
        length = 0,
        leadingSpaces = 0,
        firstNonSpace = null,
        secondNonSpace = null,
        lastNonSpace = null,
        isBoundaryTitle = false,
        startsWithListMarker = false,
        startsWithDialogueMarker = false
    )

    private data class NormalizedText(
        val text: String,
        val hardBreakPositions: IntArray
    )

    private fun parseLine(chars: CharArray, start: Int, endExclusive: Int): SoftBreakLineInfo {
        var lineLength = 0
        var leadingSpaces = 0
        var firstNonSpace: Char? = null
        var secondNonSpace: Char? = null
        var lastNonSpace: Char? = null
        var seenNonSpace = false
        var i = start
        while (i < endExclusive) {
            val c = chars[i]
            lineLength++
            if (!seenNonSpace) {
                if (c == ' ' || c == '\t' || c == '\u3000') {
                    leadingSpaces++
                } else {
                    seenNonSpace = true
                    firstNonSpace = c
                }
            } else if (secondNonSpace == null && !c.isWhitespace()) {
                secondNonSpace = c
            }
            if (!c.isWhitespace()) {
                lastNonSpace = c
            }
            i++
        }

        val lineText = if (endExclusive > start) {
            String(chars, start, endExclusive - start).trim()
        } else {
            ""
        }
        val boundary = lineText.isNotEmpty() && (
            CHINESE_CHAPTER_REGEX.matches(lineText) ||
                ENGLISH_CHAPTER_REGEX.containsMatchIn(lineText) ||
                DIRECTORY_TITLE_REGEX.matches(lineText)
            )
        return SoftBreakLineInfo(
            length = lineLength,
            leadingSpaces = leadingSpaces,
            firstNonSpace = firstNonSpace,
            secondNonSpace = secondNonSpace,
            lastNonSpace = lastNonSpace,
            isBoundaryTitle = boundary,
            startsWithListMarker = SoftBreakClassifier.detectListMarker(firstNonSpace, secondNonSpace),
            startsWithDialogueMarker = SoftBreakClassifier.detectDialogueMarker(firstNonSpace)
        )
    }

    private fun estimateTypicalLineLength(
        lines: List<SoftBreakLineInfo>,
        hardWrapLikely: Boolean,
        ruleConfig: SoftBreakRuleConfig
    ): Int {
        var count = 0
        var sum = 0L
        lines.forEach { line ->
            if (line.length > 0) {
                count++
                sum += line.length.toLong()
            }
        }
        if (count == 0) {
            return 72
        }
        val minTypical = if (hardWrapLikely) {
            ruleConfig.minTypicalHardWrap
        } else {
            ruleConfig.minTypicalNormal
        }
        return (sum / count).toInt().coerceIn(minTypical, ruleConfig.maxTypical)
    }
}
