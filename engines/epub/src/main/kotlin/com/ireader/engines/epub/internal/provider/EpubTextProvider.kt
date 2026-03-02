package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toReadiumLocatorOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

@OptIn(ExperimentalReadiumApi::class)
internal class EpubTextProvider(
    private val publication: Publication,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val start = range.start.toReadiumLocatorOrNull()
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Unsupported locator scheme: ${range.start.scheme}")
                    )
                val end = range.end.toReadiumLocatorOrNull()

                start.text.highlight
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return@withContext ReaderResult.Ok(it) }

                val content = publication.content(start)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Content service unavailable")
                    )

                val iterator = content.iterator()
                val endPosition = end?.locations?.position
                val maxLength = 8_192
                val builder = StringBuilder()

                while (builder.length < maxLength) {
                    val element = iterator.nextOrNull() ?: break
                    val position = element.locator.locations.position
                    if (endPosition != null && position != null && position > endPosition) {
                        break
                    }

                    val text = (element as? Content.TextualElement)?.text
                        ?.trim()
                        .orEmpty()
                    if (text.isEmpty()) continue

                    if (builder.isNotEmpty()) {
                        builder.append('\n')
                    }
                    builder.append(text)
                }

                ReaderResult.Ok(builder.toString())
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Io(cause = t))
            }
        }
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val readium = locator.toReadiumLocatorOrNull()
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Unsupported locator scheme: ${locator.scheme}")
                    )

                val rich = buildString {
                    append(readium.text.before.orEmpty())
                    append(readium.text.highlight.orEmpty())
                    append(readium.text.after.orEmpty())
                }.trim()

                val max = maxChars.coerceAtLeast(1)
                if (rich.isNotEmpty()) {
                    return@withContext ReaderResult.Ok(rich.take(max))
                }

                val content = publication.content(readium)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Content service unavailable")
                    )

                val iterator = content.iterator()
                val builder = StringBuilder()
                while (builder.length < max) {
                    val element = iterator.nextOrNull() ?: break
                    val text = (element as? Content.TextualElement)?.text
                        ?.trim()
                        .orEmpty()
                    if (text.isEmpty()) continue
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(text)
                }

                ReaderResult.Ok(builder.toString().take(max))
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Io(cause = t))
            }
        }
    }
}
