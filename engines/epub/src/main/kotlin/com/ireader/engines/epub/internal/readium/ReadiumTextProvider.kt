package com.ireader.engines.epub.internal.readium

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlin.math.max
import kotlin.math.min
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.content

internal class ReadiumTextProvider(
    private val publication: Publication
) : TextProvider {

    @OptIn(ExperimentalReadiumApi::class)
    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        val startLocator = ReadiumLocatorMapper.toReadium(publication, range.start)
            ?: return ReaderResult.Err(ReaderError.CorruptOrInvalid("Invalid start locator"))

        val content = publication.content(startLocator)
            ?: return ReaderResult.Err(ReaderError.Internal("Content service is unavailable"))
        val text = content.text().orEmpty()

        val startOffset = range.start.extras["charStart"]?.toIntOrNull()
        val endOffset = range.end.extras["charEnd"]?.toIntOrNull()
        if (startOffset != null && endOffset != null) {
            val safeStart = max(0, min(startOffset, text.length))
            val safeEnd = max(safeStart, min(endOffset, text.length))
            return ReaderResult.Ok(text.substring(safeStart, safeEnd))
        }

        return ReaderResult.Ok(text)
    }

    @OptIn(ExperimentalReadiumApi::class)
    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        val startLocator = ReadiumLocatorMapper.toReadium(publication, locator)
            ?: return ReaderResult.Err(ReaderError.CorruptOrInvalid("Invalid locator"))
        val content = publication.content(startLocator)
            ?: return ReaderResult.Err(ReaderError.Internal("Content service is unavailable"))
        val text = content.text().orEmpty()
        val safeMax = maxChars.coerceIn(64, 100_000)
        return ReaderResult.Ok(text.take(safeMax))
    }
}
