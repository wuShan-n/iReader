package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.common.android.reflow.ReflowPaginator
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TxtPaginatorSoftBreakIndexTest {

    @Test
    fun `pageAt should apply soft break index when hardWrapLikely is false`() = runBlocking {
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
                ioDispatcher = Dispatchers.IO
            )
            val softBreakIndex = SoftBreakIndex.openIfValid(files.softBreakIdx, meta)
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
                    assertFalse(page.text.contains('\n'))
                }
            }
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
}
