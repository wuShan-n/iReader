@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtTextProvider(
    private val store: Utf16TextStore,
    private val ioDispatcher: CoroutineDispatcher
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val start = parseOffset(range.start)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Invalid TXT range start: ${range.start}")
                    )
                val end = parseOffset(range.end)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Invalid TXT range end: ${range.end}")
                    )
                val minOffset = min(start, end).coerceIn(0L, store.lengthChars)
                val maxOffset = maxOf(start, end).coerceIn(0L, store.lengthChars)
                val length = (maxOffset - minOffset).toInt().coerceAtLeast(0)
                val capped = length.coerceAtMost(MAX_EXTRACT_CHARS)
                ReaderResult.Ok(store.readString(minOffset, capped))
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError())
            }
        }
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val offset = parseOffset(locator)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Invalid TXT locator: $locator")
                    )
                val half = (maxChars / 2).coerceAtLeast(16)
                ReaderResult.Ok(store.readAround(offset, before = half, after = half))
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError())
            }
        }
    }

    private fun parseOffset(locator: Locator): Long? {
        return TxtBlockLocatorCodec.parseOffset(locator, store.lengthChars)
    }

    private companion object {
        private const val MAX_EXTRACT_CHARS = 200_000
    }
}
