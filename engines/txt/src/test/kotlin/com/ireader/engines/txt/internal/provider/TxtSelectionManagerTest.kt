package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.LocatorExtraKeys
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TxtSelectionManagerTest {

    @Test
    fun `buildSelection should include progression extras`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "第一段\n第二段\n第三段",
            sampleHash = "txt-selection-manager",
            ioDispatcher = Dispatchers.IO
        )
        val manager = TxtSelectionManager(
            blockIndex = fixture.blockIndex,
            revision = fixture.meta.contentRevision,
            breakResolver = fixture.breakResolver,
            blockStore = fixture.blockStore,
            ioDispatcher = Dispatchers.IO
        )
        try {
            val startOffset = 4L
            val locator = fixture.locatorFor(startOffset)
            manager.start(locator).requireOk()

            val selection = manager.currentSelection().requireOk()
            assertNotNull(selection)
            val progression = selection!!.extras[LocatorExtraKeys.PROGRESSION]
            val expected = String.format(
                Locale.US,
                "%.6f",
                startOffset.toDouble() / fixture.store.lengthChars.toDouble()
            )
            assertEquals(expected, progression)
            assertEquals(startOffset.toString(), selection.extras["selectionStartOffset"])
        } finally {
            fixture.close()
        }
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
