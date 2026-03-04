package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.common.android.reflow.ReflowPaginator
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.open.Utf16LeFileWriter
import com.ireader.engines.txt.internal.render.TxtTextSource
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TxtPaginatorSoftBreakIndexTest {

    @Test
    fun `pageAt should preserve line breaks with soft break index when hardWrapLikely is false`() = runBlocking {
        val dir = Files.createTempDirectory("txt_paginator_softbreak").toFile()
        val files = createBookFiles(dir)
        val text = buildSoftWrappedText()
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        val meta = TxtMeta(
            version = 2,
            sourceUri = "file:///books/soft-break.txt",
            displayName = "soft-break.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "sample",
            originalCharset = "UTF-8",
            lengthChars = text.length.toLong(),
            hardWrapLikely = false,
            createdAtEpochMs = 0L
        )

        try {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = Dispatchers.IO,
                profile = SOFT_BREAK_PROFILE
            )
            val softBreakIndex = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SOFT_BREAK_PROFILE,
                rulesVersion = softBreakRulesVersion()
            )
            assertNotNull(softBreakIndex)
            softBreakIndex!!.use { index ->
                Utf16TextStore(files.contentU16).use { store ->
                    val paginator = ReflowPaginator(
                        source = TxtTextSource(store),
                        hardWrapLikely = meta.hardWrapLikely,
                        softBreakIndex = index
                    )
                    val page = paginator.pageAt(
                        startOffset = 0L,
                        config = RenderConfig.ReflowText(),
                        constraints = defaultConstraints()
                    )
                    assertTrue(page.text.contains('\n'))
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pageAt should keep line breaks when hardWrapLikely is false and soft break index is missing`() = runBlocking {
        val dir = Files.createTempDirectory("txt_paginator_raw_breaks").toFile()
        val files = createBookFiles(dir)
        val text = buildSoftWrappedText()
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        try {
            Utf16TextStore(files.contentU16).use { store ->
                val paginator = ReflowPaginator(
                    source = TxtTextSource(store),
                    hardWrapLikely = false,
                    softBreakIndex = null
                )
                val page = paginator.pageAt(
                    startOffset = 0L,
                    config = RenderConfig.ReflowText(),
                    constraints = defaultConstraints()
                )
                assertTrue(page.text.contains('\n'))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `short lines ending with punctuation should stay hard when hardWrapLikely is false`() = runBlocking {
        val dir = Files.createTempDirectory("txt_paginator_short_lines").toFile()
        val files = createBookFiles(dir)
        val line1 = "这是一段稳定长度测试文本用于验证句号行也会继续合并。"
        val line2 = "这是一段稳定长度测试文本用于验证软换行识别继续推进"
        val line3 = "这是一段稳定长度测试文本用于验证分页效果保持一致"
        val text = "$line1\n$line2\n$line3"
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        val meta = TxtMeta(
            version = 2,
            sourceUri = "file:///books/short-lines.txt",
            displayName = "short-lines.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "sample-short-lines",
            originalCharset = "UTF-8",
            lengthChars = text.length.toLong(),
            hardWrapLikely = false,
            createdAtEpochMs = 0L
        )

        try {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = Dispatchers.IO,
                profile = SOFT_BREAK_PROFILE
            )
            val softBreakIndex = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SOFT_BREAK_PROFILE,
                rulesVersion = softBreakRulesVersion()
            )
            assertNotNull(softBreakIndex)
            softBreakIndex!!.use { index ->
                val flagsByOffset = LinkedHashMap<Long, Boolean>()
                index.forEachNewlineInRange(0L, text.length.toLong()) { offset, isSoft ->
                    flagsByOffset[offset] = isSoft
                }

                assertEquals(2, flagsByOffset.size)
                assertEquals(false, flagsByOffset[line1.length.toLong()])
                assertEquals(false, flagsByOffset[(line1.length + 1 + line2.length).toLong()])
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `hardWrapLikely true should merge short lines with same indentation`() = runBlocking {
        val dir = Files.createTempDirectory("txt_paginator_same_indent").toFile()
        val files = createBookFiles(dir)
        val line1 = "  这是一段带缩进的短行文本用于验证软换行策略继续推进"
        val line2 = "  这是一段带缩进的短行文本用于验证分页行为保持一致"
        val line3 = "  这是一段带缩进的短行文本用于验证右侧不再提前断行"
        val text = "$line1\n$line2\n$line3"
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        val meta = TxtMeta(
            version = 2,
            sourceUri = "file:///books/same-indent.txt",
            displayName = "same-indent.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "sample-same-indent",
            originalCharset = "UTF-8",
            lengthChars = text.length.toLong(),
            hardWrapLikely = true,
            createdAtEpochMs = 0L
        )

        try {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = Dispatchers.IO,
                profile = SOFT_BREAK_PROFILE
            )
            val softBreakIndex = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SOFT_BREAK_PROFILE,
                rulesVersion = softBreakRulesVersion()
            )
            assertNotNull(softBreakIndex)
            softBreakIndex!!.use { index ->
                val flagsByOffset = LinkedHashMap<Long, Boolean>()
                index.forEachNewlineInRange(0L, text.length.toLong()) { offset, isSoft ->
                    flagsByOffset[offset] = isSoft
                }

                assertEquals(2, flagsByOffset.size)
                assertEquals(true, flagsByOffset[line1.length.toLong()])
                assertEquals(true, flagsByOffset[(line1.length + 1 + line2.length).toLong()])
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `indent increase should keep boundary hard even when hardWrapLikely is true`() = runBlocking {
        val dir = Files.createTempDirectory("txt_paginator_indent_increase").toFile()
        val files = createBookFiles(dir)
        val line1 = "这是普通叙述文本用于验证缩进突增时应保留硬换行"
        val line2 = "    下一行缩进明显增加用于模拟新段落开始"
        val line3 = "    同级缩进继续叙述可按软换行合并"
        val text = "$line1\n$line2\n$line3"
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        val meta = TxtMeta(
            version = 2,
            sourceUri = "file:///books/indent-increase.txt",
            displayName = "indent-increase.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "sample-indent-increase",
            originalCharset = "UTF-8",
            lengthChars = text.length.toLong(),
            hardWrapLikely = true,
            createdAtEpochMs = 0L
        )

        try {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = Dispatchers.IO,
                profile = SOFT_BREAK_PROFILE
            )
            val softBreakIndex = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SOFT_BREAK_PROFILE,
                rulesVersion = softBreakRulesVersion()
            )
            assertNotNull(softBreakIndex)
            softBreakIndex!!.use { index ->
                val flagsByOffset = LinkedHashMap<Long, Boolean>()
                index.forEachNewlineInRange(0L, text.length.toLong()) { offset, isSoft ->
                    flagsByOffset[offset] = isSoft
                }

                assertEquals(2, flagsByOffset.size)
                assertEquals(false, flagsByOffset[line1.length.toLong()])
                assertEquals(true, flagsByOffset[(line1.length + 1 + line2.length).toLong()])
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `openIfValid should invalidate index when profile or rulesVersion mismatch`() = runBlocking {
        val dir = Files.createTempDirectory("txt_softbreak_profile_mismatch").toFile()
        val files = createBookFiles(dir)
        val text = buildSoftWrappedText()
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        val meta = TxtMeta(
            version = 2,
            sourceUri = "file:///books/profile-mismatch.txt",
            displayName = "profile-mismatch.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "sample-profile-mismatch",
            originalCharset = "UTF-8",
            lengthChars = text.length.toLong(),
            hardWrapLikely = true,
            createdAtEpochMs = 0L
        )

        try {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = Dispatchers.IO,
                profile = SOFT_BREAK_PROFILE
            )

            val valid = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SOFT_BREAK_PROFILE,
                rulesVersion = softBreakRulesVersion()
            )
            val wrongProfile = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SoftBreakTuningProfile.STRICT,
                rulesVersion = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.STRICT).rulesVersion
            )
            val wrongRulesVersion = SoftBreakIndex.openIfValid(
                file = files.softBreakIdx,
                meta = meta,
                profile = SOFT_BREAK_PROFILE,
                rulesVersion = softBreakRulesVersion() + 1
            )

            valid?.close()
            assertNotNull(valid)
            assertNull(wrongProfile)
            assertNull(wrongRulesVersion)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun defaultConstraints(): LayoutConstraints {
        return LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 2400,
            density = 3f,
            fontScale = 1f
        )
    }

    private fun buildSoftWrappedText(): String {
        return buildString {
            append("这是一段用于软换行索引测试的连续叙述文本保持稳定长度并继续展开内容甲")
            append('\n')
            append("这是一段用于软换行索引测试的连续叙述文本保持稳定长度并继续展开内容乙")
            append('\n')
            append("这是一段用于软换行索引测试的连续叙述文本保持稳定长度并继续展开内容丙")
        }
    }

    private fun createBookFiles(root: File): TxtBookFiles {
        val paginationDir = File(root, "pagination").apply { mkdirs() }
        return TxtBookFiles(
            bookDir = root,
            lockFile = File(root, "book.lock"),
            contentU16 = File(root, "content.u16"),
            metaJson = File(root, "meta.json"),
            outlineJson = File(root, "outline.json"),
            paginationDir = paginationDir,
            softBreakIdx = File(root, "softbreak.idx"),
            softBreakLock = File(root, "softbreak.lock"),
            bloomIdx = File(root, "bloom.idx"),
            bloomLock = File(root, "bloom.lock")
        )
    }
    private fun softBreakRulesVersion(): Int {
        return SoftBreakRuleConfig.forProfile(SOFT_BREAK_PROFILE).rulesVersion
    }

    private companion object {
        private val SOFT_BREAK_PROFILE = SoftBreakTuningProfile.BALANCED
    }
}
