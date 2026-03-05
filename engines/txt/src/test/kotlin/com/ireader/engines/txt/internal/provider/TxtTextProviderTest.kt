package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.testing.writeUtf16Text
import com.ireader.reader.api.error.ReaderResult
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTextProviderTest {

    @Test
    fun `getTextAround should cap max chars`() = runBlocking {
        val root = Files.createTempDirectory("txt_text_provider").toFile()
        val content = java.io.File(root, "content.u16")
        val text = "0123456789".repeat(40_000)
        writeUtf16Text(content, text)
        val store = Utf16TextStore(content)
        val provider = TxtTextProvider(store = store, ioDispatcher = Dispatchers.IO)
        try {
            val locator = TxtBlockLocatorCodec.locatorForOffset(
                offset = store.lengthChars / 2L,
                maxOffset = store.lengthChars
            )
            val around = provider.getTextAround(locator = locator, maxChars = 2_000_000).requireOk()
            assertTrue(around.length <= 200_000)
            assertTrue(around.isNotEmpty())
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
