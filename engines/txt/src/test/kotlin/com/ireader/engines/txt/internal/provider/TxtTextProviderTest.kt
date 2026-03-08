package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.api.error.ReaderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTextProviderTest {

    @Test
    fun `getTextAround should cap max chars`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "0123456789".repeat(40_000),
            sampleHash = "txt-text-provider",
            ioDispatcher = Dispatchers.IO
        )
        val provider = TxtTextProvider(
            blockIndex = fixture.blockIndex,
            contentFingerprint = fixture.meta.contentFingerprint,
            blockStore = fixture.blockStore,
            projectionEngine = fixture.projectionEngine,
            ioDispatcher = Dispatchers.IO
        )
        try {
            val locator = fixture.locatorFor(fixture.store.lengthChars / 2L)
            val around = provider.getTextAround(locator = locator, maxChars = 2_000_000).requireOk()
            assertTrue(around.length <= 200_000)
            assertTrue(around.isNotEmpty())
        } finally {
            fixture.close()
        }
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
