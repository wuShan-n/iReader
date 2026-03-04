@file:Suppress("TooGenericExceptionCaught")

package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.internal.util.pageLocator
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.NormalizedRect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PdfSelectionManager(
    private val pageCount: Int,
    private val textProvider: PdfTextProvider?
) : SelectionProvider, SelectionController {

    private val mutex = Mutex()
    private var current: SelectionProvider.Selection? = null

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return ReaderResult.Ok(mutex.withLock { current })
    }

    override suspend fun clearSelection(): ReaderResult<Unit> = clear()

    override suspend fun start(locator: com.ireader.reader.model.Locator): ReaderResult<Unit> {
        return update(locator)
    }

    override suspend fun update(locator: com.ireader.reader.model.Locator): ReaderResult<Unit> {
        val pageIndex = locator.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF locator: $locator"))
        return runCatching {
            val pageLocator = pageLocator(pageIndex = pageIndex, pageCount = pageCount)
            val selectedText = textProvider?.pageText(pageIndex)?.takeIf { it.isNotBlank() }
            val fullPageRect = NormalizedRect(0f, 0f, 1f, 1f)
            val next = SelectionProvider.Selection(
                locator = pageLocator,
                bounds = fullPageRect,
                selectedText = selectedText,
                rects = listOf(fullPageRect),
                extras = locator.extras
            )
            mutex.withLock {
                current = next
            }
            ReaderResult.Ok(Unit)
        }.getOrElse {
            ReaderResult.Err(it.toReaderError())
        }
    }

    override suspend fun finish(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clear(): ReaderResult<Unit> {
        return mutex.withLock {
            current = null
            ReaderResult.Ok(Unit)
        }
    }
}
