@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.max
import kotlin.math.roundToInt

object SoftBreakProcessor {

    fun renderRawPreservingBreaks(
        rawText: String,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ): CharSequence {
        return normalizeLineEndings(rawText)
    }

    fun process(
        rawText: String,
        hardWrapLikely: Boolean,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean,
        ruleConfig: SoftBreakRuleConfig = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
    ): CharSequence {
        val normalizedInput = normalizeLineEndings(rawText)
        val normalized = normalizeSoftBreaks(
            text = normalizedInput,
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
        var leadingWhitespaceCount = 0
        var leadingFullWidthSpaces = 0
        while (index < paragraphEndExclusive) {
            val ch = text[index]
            if (ch == '\n') {
                return false
            }
            if (!ch.isWhitespace() && ch != '\u3000') {
                break
            }
            leadingWhitespaceCount++
            if (ch == '\u3000') {
                leadingFullWidthSpaces++
            }
            index++
        }
        if (index >= paragraphEndExclusive) {
            return false
        }
        if (leadingFullWidthSpaces > 0 || leadingWhitespaceCount >= MANUAL_INDENT_WHITESPACE_MIN) {
            return false
        }
        val visibleLength = paragraphEndExclusive - index
        if (visibleLength < MIN_INDENT_PARAGRAPH_CHARS) {
            return false
        }
        val snippet = text.subSequence(index, minOf(paragraphEndExclusive, index + 64)).toString().trim()
        if (isBoundaryTitleLine(snippet)) {
            return false
        }
        return true
    }

    private const val MIN_INDENT_PARAGRAPH_CHARS = 12
    private const val MANUAL_INDENT_WHITESPACE_MIN = 2
    private const val MAX_BOUNDARY_TITLE_CHARS = 48
    private val CHINESE_CHAPTER_REGEX = Regex("^第[零一二三四五六七八九十百千万0-9]{1,9}[章节回卷部篇].{0,30}$")
    private val ENGLISH_CHAPTER_REGEX = Regex("^(Chapter|CHAPTER)\\s+\\d+.*$")
    private val PROLOGUE_REGEX = Regex("^(Prologue|Epilogue|PROLOGUE|EPILOGUE)$")
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
        val boundary = isBoundaryTitleLine(lineText)
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

    private fun isBoundaryTitleLine(line: String): Boolean {
        if (line.isBlank()) {
            return false
        }
        if (DIRECTORY_TITLE_REGEX.matches(line)) {
            return true
        }
        if (line.length > MAX_BOUNDARY_TITLE_CHARS) {
            return false
        }
        return CHINESE_CHAPTER_REGEX.matches(line) ||
            ENGLISH_CHAPTER_REGEX.matches(line) ||
            PROLOGUE_REGEX.matches(line)
    }

    private fun estimateTypicalLineLength(
        lines: List<SoftBreakLineInfo>,
        hardWrapLikely: Boolean,
        ruleConfig: SoftBreakRuleConfig
    ): Int {
        val lengths = lines.asSequence()
            .map { it.length }
            .filter { it > 0 }
            .toList()
        if (lengths.isEmpty()) {
            return 72
        }
        val sorted = lengths.sorted()
        val trimCount = (sorted.size * 0.1f).roundToInt()
            .coerceAtMost((sorted.size - 1) / 2)
        val window = sorted.subList(trimCount, sorted.size - trimCount)
        val median = window[window.size / 2]
        val mean = window.average()
        val weightedTypical = ((median * 0.6) + (mean * 0.4)).roundToInt()

        val minTypical = if (hardWrapLikely) {
            ruleConfig.minTypicalHardWrap
        } else {
            ruleConfig.minTypicalNormal
        }
        return weightedTypical.coerceIn(minTypical, ruleConfig.maxTypical)
    }

    private fun normalizeLineEndings(text: String): String {
        if (text.indexOf('\r') < 0) {
            return text
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }
}
