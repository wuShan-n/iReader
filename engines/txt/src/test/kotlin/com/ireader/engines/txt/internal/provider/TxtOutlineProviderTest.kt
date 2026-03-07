package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.LocatorExtraKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TxtOutlineProviderTest {

    @Test
    fun `getOutline should recover from corrupted cached json`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = buildString {
                append("第1章 起始\n")
                append("这里是正文。\n")
                append("第2章 发展\n")
                append("这里是后续正文。\n")
            },
            sampleHash = "outline-cache",
            ioDispatcher = Dispatchers.IO
        )
        val provider = TxtOutlineProvider(
            files = fixture.files,
            meta = fixture.meta,
            blockIndex = fixture.blockIndex,
            breakResolver = fixture.breakResolver,
            blockStore = fixture.blockStore,
            ioDispatcher = Dispatchers.IO,
            persistOutline = true
        )
        try {
            val first = provider.getOutline().requireOk()
            assertTrue(first.isNotEmpty())

            fixture.files.outlineIdx.writeText("{broken")

            val second = provider.getOutline().requireOk()
            assertTrue(second.isNotEmpty())
        } finally {
            provider.close()
            fixture.close()
        }
    }

    @Test
    fun `getOutline should detect chapter title across chunk boundary`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "A".repeat(63_997) + "\n第123章 跨块标题\n正文继续",
            sampleHash = "outline-boundary",
            ioDispatcher = Dispatchers.IO
        )
        val provider = TxtOutlineProvider(
            files = fixture.files,
            meta = fixture.meta,
            blockIndex = fixture.blockIndex,
            breakResolver = fixture.breakResolver,
            blockStore = fixture.blockStore,
            ioDispatcher = Dispatchers.IO,
            persistOutline = false
        )
        try {
            val outline = provider.getOutline().requireOk()
            assertTrue(outline.any { it.title.contains("第123章 跨块标题") })
        } finally {
            provider.close()
            fixture.close()
        }
    }

    @Test
    fun `getOutline should expose confidence in locator extras`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "第1章 起始\n这里是正文。\n",
            sampleHash = "outline-confidence",
            ioDispatcher = Dispatchers.IO
        )
        val provider = TxtOutlineProvider(
            files = fixture.files,
            meta = fixture.meta,
            blockIndex = fixture.blockIndex,
            breakResolver = fixture.breakResolver,
            blockStore = fixture.blockStore,
            ioDispatcher = Dispatchers.IO,
            persistOutline = false
        )
        try {
            val outline = provider.getOutline().requireOk()
            assertTrue(outline.isNotEmpty())
            assertTrue(outline.first().locator.extras[LocatorExtraKeys.OUTLINE_CONFIDENCE] != null)
        } finally {
            provider.close()
            fixture.close()
        }
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
