@file:Suppress("TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.store.Utf16TextStore
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
    private val store: Utf16TextStore,
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
        return TxtBlockLocatorCodec.parseOffset(locator, maxOffset = store.lengthChars)
    }

    private fun buildSelection(anchorOffset: Long, edgeOffset: Long): SelectionProvider.Selection {
        val startOffset = min(anchorOffset, edgeOffset).coerceIn(0L, store.lengthChars)
        val endOffset = max(anchorOffset, edgeOffset).coerceIn(0L, store.lengthChars)
        val startLocator = TxtBlockLocatorCodec.locatorForOffset(startOffset, store.lengthChars)
        val endLocator = TxtBlockLocatorCodec.locatorForOffset(endOffset, store.lengthChars)
        val textLength = (endOffset - startOffset).toInt().coerceAtLeast(0)
        val selectedText = if (textLength <= 0) {
            null
        } else {
            val cappedLength = textLength.coerceAtMost(maxSelectedChars)
            store.readString(startOffset, cappedLength).takeIf { it.isNotBlank() }
        }
        val progression = if (store.lengthChars == 0L) {
            0.0
        } else {
            startOffset.toDouble() / store.lengthChars.toDouble()
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
