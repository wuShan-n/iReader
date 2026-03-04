package com.ireader.engines.txt.internal.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectorTest {

    private val detector = ChapterDetector()

    @Test
    fun `detects common chapter formats`() {
        assertTrue(detector.isChapterTitle("第12章 江湖再见"))
        assertTrue(detector.isChapterTitle("Chapter 7 - Lost City"))
        assertTrue(detector.isChapterTitle("PROLOGUE"))
    }

    @Test
    fun `ignores regular paragraph lines`() {
        assertFalse(detector.isChapterTitle("这是一个普通段落的第一行，长度会比较长并且不应该被识别为章节标题。"))
        assertFalse(detector.isChapterTitle("hello world"))
    }

    @Test
    fun `detects directory markers as chapter boundaries`() {
        assertTrue(detector.isChapterBoundaryTitle("目录"))
        assertTrue(detector.isChapterBoundaryTitle("目 录"))
        assertTrue(detector.isChapterBoundaryTitle("CONTENTS"))
        assertFalse(detector.isChapterBoundaryTitle("目光所及皆是故事"))
    }
}
