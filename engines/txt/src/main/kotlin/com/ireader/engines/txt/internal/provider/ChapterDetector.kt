@file:Suppress("MagicNumber", "ReturnCount")

package com.ireader.engines.txt.internal.provider

internal class ChapterDetector {
    private val chinese = Regex("^第[零一二三四五六七八九十百千万0-9]{1,9}[章节回卷部篇].{0,30}$")
    private val english = Regex("^(Chapter|CHAPTER)\\s+\\d+.*$")
    private val prologue = Regex("^(Prologue|Epilogue|PROLOGUE|EPILOGUE)$")

    fun isChapterTitle(line: String): Boolean {
        if (line.isBlank()) {
            return false
        }
        if (line.length > 48) {
            return false
        }
        return chinese.matches(line) || english.matches(line) || prologue.matches(line)
    }
}
