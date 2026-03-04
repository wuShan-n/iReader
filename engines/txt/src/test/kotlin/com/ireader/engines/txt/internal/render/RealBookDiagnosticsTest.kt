package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.SoftBreakProcessor
import com.ireader.engines.txt.internal.provider.ChapterDetector
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RealBookDiagnosticsTest {

    @Test
    fun `real book sample should expose chapter rewind pressure and soft break merges`() {
        val file = File("docs/temp/来自末日(1-500章).txt")
        assumeTrue("dataset not found: ${file.path}", file.exists())

        val lines = file.readLines(Charsets.UTF_8).take(MAX_SAMPLE_LINES)
        val detector = ChapterDetector()
        var chapterBoundaryWithStrongPrelude = 0
        var chapterLines = 0

        for (index in lines.indices) {
            val line = lines[index]
            if (!detector.isChapterBoundaryTitle(line.trim())) {
                continue
            }
            chapterLines++
            val previous = lines.getOrNull(index - 1)?.trim().orEmpty()
            if (previous.isEmpty() || previous.lastOrNull() in STRONG_END_PUNCTUATION) {
                chapterBoundaryWithStrongPrelude++
            }
        }

        val raw = lines.joinToString(separator = "\n")
        val processed = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        ).toString()
        val softMerged = countSoftMergedNewlines(raw, processed)

        assertTrue("expected many chapter boundaries in sample", chapterLines >= 20)
        assertTrue("expected chapter boundaries with strong prelude", chapterBoundaryWithStrongPrelude >= 10)
        assertTrue("expected some soft-merged newlines in sample", softMerged > 0)
    }

    private fun countSoftMergedNewlines(raw: String, processed: String): Int {
        var merged = 0
        val limit = minOf(raw.length, processed.length)
        for (i in 0 until limit) {
            if (raw[i] == '\n' && processed[i] == ' ') {
                merged++
            }
        }
        return merged
    }

    private companion object {
        private const val MAX_SAMPLE_LINES = 3_000
        private val STRONG_END_PUNCTUATION = setOf('。', '！', '？', '.', '!', '?', '…', ';', '；', ':', '：')
    }
}
