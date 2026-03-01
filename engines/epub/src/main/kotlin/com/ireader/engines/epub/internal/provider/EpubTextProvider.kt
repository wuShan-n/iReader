package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.cache.SimpleLruCache
import com.ireader.engines.epub.internal.locator.EpubLocator
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.text.XhtmlTextExtractor
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class EpubTextProvider(
    private val container: EpubContainer,
    private val ioDispatcher: CoroutineDispatcher
) : TextProvider {

    private val cache = SimpleLruCache<Int, String>(maxSize = 24)

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        val startIndex = resolveSpineIndex(range.start)
            ?: return ReaderResult.Err(ReaderError.CorruptOrInvalid("Invalid start locator"))
        val endIndex = resolveSpineIndex(range.end)
            ?: return ReaderResult.Err(ReaderError.CorruptOrInvalid("Invalid end locator"))

        val from = min(startIndex, endIndex)
        val to = max(startIndex, endIndex)

        val chunks = mutableListOf<String>()
        for (index in from..to) {
            chunks += chapterText(index)
        }

        val merged = chunks.joinToString("\n")
        val startOffset = range.start.extras["charStart"]?.toIntOrNull()
        val endOffset = range.end.extras["charEnd"]?.toIntOrNull()
        return if (from == to && startOffset != null && endOffset != null) {
            val safeStart = startOffset.coerceIn(0, merged.length)
            val safeEnd = endOffset.coerceIn(safeStart, merged.length)
            ReaderResult.Ok(merged.substring(safeStart, safeEnd))
        } else {
            ReaderResult.Ok(merged)
        }
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        val index = resolveSpineIndex(locator)
            ?: return ReaderResult.Err(ReaderError.CorruptOrInvalid("Invalid locator"))
        val safeMax = maxChars.coerceIn(64, 100_000)
        return ReaderResult.Ok(chapterText(index).take(safeMax))
    }

    suspend fun chapterText(spineIndex: Int): String = withContext(ioDispatcher) {
        cache.getOrPut(spineIndex) {
            val file = File(container.rootDir, container.spinePath(spineIndex))
            runCatching { XhtmlTextExtractor.extract(file) }
                .getOrElse {
                    val raw = runCatching { file.readText() }.getOrDefault("")
                    stripHtml(raw)
                }
        }
    }

    private fun resolveSpineIndex(locator: Locator): Int? {
        return when (locator.scheme) {
            LocatorSchemes.EPUB_CFI -> EpubLocator.spineIndexFromEpubValue(container, locator.value)
            LocatorSchemes.REFLOW_PAGE -> EpubLocator.parseReflowPage(locator.value)?.first
            else -> null
        }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
