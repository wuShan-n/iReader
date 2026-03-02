package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.math.max
import kotlin.math.min

internal class TxtTextProvider(
    private val store: TxtTextStore,
    private val maxRangeChars: Int
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        val start = parseOffset(range.start)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid start locator"))
        val end = parseOffset(range.end)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid end locator"))

        val from = min(start, end).coerceAtLeast(0)
        val to = max(start, end).coerceAtLeast(from)
        val cappedEnd = min(to, from + maxRangeChars.coerceAtLeast(1))
        return ReaderResult.Ok(store.readRange(from, cappedEnd))
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        val offset = parseOffset(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid locator"))
        val safeMax = maxChars.coerceIn(32, 10_000)
        return ReaderResult.Ok(store.readAround(offset.coerceAtLeast(0), safeMax))
    }

    private fun parseOffset(locator: Locator): Int? {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return null
        return locator.value.toIntOrNull()
    }
}
