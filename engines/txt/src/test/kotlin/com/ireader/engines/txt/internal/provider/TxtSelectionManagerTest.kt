package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.testing.writeUtf16Text
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.LocatorExtraKeys
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TxtSelectionManagerTest {

    @Test
    fun `buildSelection should include progression extras`() = runBlocking {
        val root = Files.createTempDirectory("txt_selection_manager").toFile()
        val content = java.io.File(root, "content.u16")
        writeUtf16Text(content, "第一段\n第二段\n第三段")
        val store = Utf16TextStore(content)
        val manager = TxtSelectionManager(store = store, ioDispatcher = Dispatchers.IO)
        try {
            val startOffset = 4L
            val locator = TxtBlockLocatorCodec.locatorForOffset(
                offset = startOffset,
                maxOffset = store.lengthChars
            )
            manager.start(locator).requireOk()

            val selection = manager.currentSelection().requireOk()
            assertNotNull(selection)
            val progression = selection!!.extras[LocatorExtraKeys.PROGRESSION]
            val expected = String.format(
                Locale.US,
                "%.6f",
                startOffset.toDouble() / store.lengthChars.toDouble()
            )
            assertEquals(expected, progression)
            assertEquals(startOffset.toString(), selection.extras["selectionStartOffset"])
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
