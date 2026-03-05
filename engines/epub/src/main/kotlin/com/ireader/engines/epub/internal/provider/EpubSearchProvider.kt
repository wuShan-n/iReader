package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.ReadiumLocatorExtras
import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

@OptIn(ExperimentalReadiumApi::class)
internal class EpubSearchProvider(
    private val publication: Publication,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty() || options.maxHits <= 0) return@flow

        val iterator = try {
            publication.search(
                query = normalizedQuery,
                options = options.toReadiumSearchOptions()
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        } ?: return@flow

        var emitted = 0
        var passedStart = options.startFrom == null

        try {
            while (emitted < options.maxHits) {
                val page = when (val result = iterator.next()) {
                    is Try.Success -> result.value
                    is Try.Failure -> return@flow // 失败直接结束，避免崩溃
                } ?: break

                for (locator in page.locators) {
                    if (emitted >= options.maxHits) return@flow

                    val appLocator = locator.toAppLocator()

                    if (!passedStart) {
                        val startFrom = options.startFrom
                        if (startFrom != null && !isAtOrAfter(appLocator, startFrom)) {
                            continue
                        }
                        passedStart = true
                    }

                    val excerpt = buildString {
                        append(locator.text.before.orEmpty())
                        append(locator.text.highlight.orEmpty())
                        append(locator.text.after.orEmpty())
                    }.ifBlank {
                        locator.title.orEmpty()
                    }.take(240)

                    emit(
                        SearchHit(
                            range = LocatorRange(start = appLocator, end = appLocator),
                            excerpt = excerpt,
                            sectionTitle = locator.title
                        )
                    )

                    emitted++
                }
            }
        } finally {
            runCatching { iterator.close() }
        }
    }.flowOn(ioDispatcher)

    private fun SearchOptions.toReadiumSearchOptions(): SearchService.Options =
        SearchService.Options(
            caseSensitive = caseSensitive,
            wholeWord = wholeWord
        )
}

internal fun isAtOrAfter(candidate: Locator, startFrom: Locator): Boolean {
    if (candidate.scheme != startFrom.scheme) return true
    if (candidate.value == startFrom.value) return true

    val candidatePosition = candidate.extras[ReadiumLocatorExtras.POSITION]?.toIntOrNull()
    val startPosition = startFrom.extras[ReadiumLocatorExtras.POSITION]?.toIntOrNull()
    if (candidatePosition != null && startPosition != null) {
        return candidatePosition >= startPosition
    }

    val candidateProgression = candidate.extras[ReadiumLocatorExtras.TOTAL_PROGRESSION]?.toDoubleOrNull()
        ?: candidate.extras[ReadiumLocatorExtras.PROGRESSION]?.toDoubleOrNull()
    val startProgression = startFrom.extras[ReadiumLocatorExtras.TOTAL_PROGRESSION]?.toDoubleOrNull()
        ?: startFrom.extras[ReadiumLocatorExtras.PROGRESSION]?.toDoubleOrNull()
    if (candidateProgression != null && startProgression != null) {
        return candidateProgression >= startProgression
    }

    return true
}
