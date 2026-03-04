@file:Suppress("MagicNumber", "ReturnCount")

package com.ireader.engines.txt.internal.provider

internal class ChapterDetector {
    private val chinese = Regex("^第[零一二三四五六七八九十百千万0-9]{1,9}[章节回卷部篇].{0,30}$")
    private val english = Regex("^(Chapter|CHAPTER)\\s+\\d+.*$")
    private val prologue = Regex("^(Prologue|Epilogue|PROLOGUE|EPILOGUE)$")
    private val toc = Regex("^(目录|目\\s*录|contents)$", RegexOption.IGNORE_CASE)

    fun isChapterTitle(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) {
            return false
        }
        if (normalized.length > 48) {
            return false
        }
        return chinese.matches(normalized) || english.matches(normalized) || prologue.matches(normalized)
    }

    fun isChapterBoundaryTitle(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) {
            return false
        }
        return isChapterTitle(normalized) || toc.matches(normalized)
    }
}
