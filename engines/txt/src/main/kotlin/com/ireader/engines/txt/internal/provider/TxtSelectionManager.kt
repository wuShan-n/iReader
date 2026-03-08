@file:Suppress("TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TextAnchorAffinity
import com.ireader.engines.txt.internal.locator.TxtLocatorResolver
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TxtSelectionManager(
    private val blockIndex: TxtBlockIndex,
    private val contentFingerprint: String,
    private val projectionEngine: TextProjectionEngine,
    private val blockStore: BlockStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxSelectedChars: Int = 4_096
) : SelectionProvider, SelectionController {

    private val mutex = Mutex()
    private var activeAnchorOffset: Long? = null
    private var current: SelectionProvider.Selection? = null

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return ReaderResult.Ok(mutex.withLock { current })
    }

    override suspend fun clearSelection(): ReaderResult<Unit> = clear()

    override suspend fun start(locator: Locator): ReaderResult<Unit> {
        val offset = parseOffset(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid TXT locator: $locator"))
        return withContext(ioDispatcher) {
            runCatching {
                mutex.withLock {
                    activeAnchorOffset = offset
                    current = buildSelection(anchorOffset = offset, edgeOffset = offset)
                }
            }.fold(
                onSuccess = { ReaderResult.Ok(Unit) },
                onFailure = { ReaderResult.Err(it.toReaderError()) }
            )
        }
    }

    override suspend fun update(locator: Locator): ReaderResult<Unit> {
        val edgeOffset = parseOffset(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid TXT locator: $locator"))
        return withContext(ioDispatcher) {
            runCatching {
                mutex.withLock {
                    val anchor = activeAnchorOffset ?: edgeOffset.also { activeAnchorOffset = it }
                    current = buildSelection(anchorOffset = anchor, edgeOffset = edgeOffset)
                }
            }.fold(
                onSuccess = { ReaderResult.Ok(Unit) },
                onFailure = { ReaderResult.Err(it.toReaderError()) }
            )
        }
    }

    override suspend fun finish(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clear(): ReaderResult<Unit> {
        return mutex.withLock {
            activeAnchorOffset = null
            current = null
            ReaderResult.Ok(Unit)
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

    private fun buildSelection(anchorOffset: Long, edgeOffset: Long): SelectionProvider.Selection {
        val startOffset = min(anchorOffset, edgeOffset).coerceIn(0L, blockIndex.lengthCodeUnits)
        val endOffset = max(anchorOffset, edgeOffset).coerceIn(0L, blockIndex.lengthCodeUnits)
        val startLocator = TxtLocatorResolver.locatorForOffset(
            offset = startOffset,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = blockIndex.lengthCodeUnits,
            projectionEngine = projectionEngine
        )
        val endLocator = TxtLocatorResolver.locatorForOffset(
            offset = endOffset,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = blockIndex.lengthCodeUnits,
            projectionEngine = projectionEngine,
            extras = mapOf("affinity" to TextAnchorAffinity.BACKWARD.storageCode)
        )
        val selectedText = if (endOffset <= startOffset) {
            null
        } else {
            val cappedEnd = (startOffset + maxSelectedChars.toLong()).coerceAtMost(endOffset)
            projectionEngine.projectRange(
                startOffset = startOffset,
                endOffsetExclusive = cappedEnd
            ).displayText.takeIf { it.isNotBlank() }
        }
        val progression = if (blockIndex.lengthCodeUnits == 0L) {
            0.0
        } else {
            startOffset.toDouble() / blockIndex.lengthCodeUnits.toDouble()
        }.coerceIn(0.0, 1.0)
        return SelectionProvider.Selection(
            locator = startLocator,
            start = startLocator,
            end = endLocator,
            selectedText = selectedText,
            extras = mapOf(
                LocatorExtraKeys.PROGRESSION to String.format(Locale.US, "%.6f", progression),
                "selectionStartOffset" to startOffset.toString(),
                "selectionEndOffset" to endOffset.toString()
            ).filterValues { it.isNotBlank() }
        )
    }

    fun currentRangeOrNull(): LocatorRange? {
        val selection = current ?: return null
        val start = selection.start ?: return null
        val end = selection.end ?: return null
        return LocatorRange(start = start, end = end, extras = selection.extras)
    }
}
