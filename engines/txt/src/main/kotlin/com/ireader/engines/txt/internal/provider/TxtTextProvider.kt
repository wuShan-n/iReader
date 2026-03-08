@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtLocatorResolver
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtTextProvider(
    private val blockIndex: TxtBlockIndex,
    private val contentFingerprint: String,
    private val blockStore: BlockStore,
    private val projectionEngine: TextProjectionEngine,
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
                val minOffset = min(start, end).coerceIn(0L, blockIndex.lengthCodeUnits)
                val maxOffset = maxOf(start, end).coerceIn(0L, blockIndex.lengthCodeUnits)
                val cappedEnd = (minOffset + MAX_EXTRACT_CODE_UNITS)
                    .coerceAtMost(maxOffset)
                ReaderResult.Ok(
                    projectionEngine.projectRange(
                        startOffset = minOffset,
                        endOffsetExclusive = cappedEnd
                    ).displayText
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
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
                val capped = maxChars.coerceIn(32, MAX_AROUND_CODE_UNITS)
                val half = (capped / 2).coerceAtLeast(16)
                val start = (offset - half.toLong()).coerceAtLeast(0L)
                val end = (offset + half.toLong()).coerceAtMost(blockIndex.lengthCodeUnits)
                ReaderResult.Ok(
                    projectionEngine.projectRange(
                        startOffset = start,
                        endOffsetExclusive = end
                    ).displayText
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    private fun parseOffset(locator: Locator): Long? {
        return TxtLocatorResolver.parsePublicOffset(
            locator = locator,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = blockIndex.lengthCodeUnits,
            projectionEngine = projectionEngine
        )
    }

    private companion object {
        private const val MAX_EXTRACT_CODE_UNITS = 200_000L
        private const val MAX_AROUND_CODE_UNITS = 200_000
    }
}
