package com.ireader.engines.txt.internal.projection

import com.ireader.engines.common.io.readStringUtf8
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TextProjectionEngineTest {

    @Test
    fun `patch should persist and affect projected text`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "甲\n乙",
            sampleHash = "break-patch-persist",
            ioDispatcher = Dispatchers.IO
        )
        try {
            fixture.projectionEngine.patch(offset = 1L, state = BreakMapState.SOFT_SPACE)

            val projected = fixture.projectionEngine.projectRange(0L, fixture.sourceText.length.toLong())
            assertEquals("甲 乙", projected.displayText)

            val reopened = TextProjectionEngine(
                store = fixture.store,
                files = fixture.files,
                meta = fixture.meta,
                breakIndex = fixture.breakIndex
            )
            assertEquals(BreakMapState.SOFT_SPACE, reopened.stateAt(1L))
            assertEquals("甲 乙", reopened.projectRange(0L, fixture.sourceText.length.toLong()).displayText)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `projectRange should ignore malformed break map blocks instead of crashing`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = buildString {
                repeat(6000) { index ->
                    append("第")
                    append(index)
                    append("行内容")
                    append('\n')
                }
            },
            sampleHash = "break-map-corruption-guard",
            ioDispatcher = Dispatchers.IO
        )
        try {
            corruptFirstBreakMapBlockCount(fixture.files.breakMap)

            val projected = fixture.projectionEngine.projectRange(
                startOffset = 0L,
                endOffsetExclusive = fixture.sourceText.length.toLong()
            )

            assertEquals(fixture.sourceText, projected.displayText)
        } finally {
            fixture.close()
        }
    }

    private fun corruptFirstBreakMapBlockCount(file: java.io.File) {
        RandomAccessFile(file, "rw").use { raf ->
            val magic = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
            check(magic == "BRK1") { "Unexpected break.map magic: $magic" }
            raf.readInt() // version
            raf.readInt() // block newlines
            raf.readLong() // length chars
            raf.readLong() // newline count
            raf.readStringUtf8() // sample hash
            raf.readStringUtf8() // profile
            raf.readInt() // rules version
            val indexOffset = raf.readLong()

            raf.seek(indexOffset)
            val blockCount = raf.readInt()
            check(blockCount > 0) { "Expected at least one block in break.map" }
            val firstBlockFilePos = raf.readLong()

            raf.seek(firstBlockFilePos)
            raf.writeInt(Int.MAX_VALUE / 8)
        }
    }
}
