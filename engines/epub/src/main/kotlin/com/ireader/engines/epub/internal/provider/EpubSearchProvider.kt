package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

@OptIn(ExperimentalReadiumApi::class)
internal class EpubSearchProvider(
    private val publication: Publication
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank() || options.maxHits <= 0) {
            return@flow
        }

        val iterator = publication.search(
            query = query,
            options = options.toReadiumSearchOptions()
        ) ?: throw IllegalStateException("EPUB search service is unavailable")

        var emitted = 0
        var passedStart = options.startFrom == null
        try {
            while (true) {
                val page = when (val result = iterator.next()) {
                    is Try.Success -> result.value
                    is Try.Failure -> throw IllegalStateException("EPUB search failed: $result")
                } ?: break

                for (locator in page.locators) {
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
                        locator.title ?: ""
                    }.take(240)

                    emit(
                        SearchHit(
                            range = LocatorRange(start = appLocator, end = appLocator),
                            excerpt = excerpt,
                            sectionTitle = locator.title
                        )
                    )

                    emitted++
                    if (emitted >= options.maxHits) {
                        return@flow
                    }
                }
            }
        } finally {
            runCatching { iterator.close() }
        }
    }

    private fun SearchOptions.toReadiumSearchOptions(): SearchService.Options =
        SearchService.Options(
            caseSensitive = caseSensitive,
            wholeWord = wholeWord
        )
}

internal fun isAtOrAfter(candidate: Locator, startFrom: Locator): Boolean {
    if (candidate.scheme != startFrom.scheme) {
        return true
    }
    if (candidate.value == startFrom.value) {
        return true
    }

    val candidatePosition = candidate.extras["position"]?.toIntOrNull()
    val startPosition = startFrom.extras["position"]?.toIntOrNull()
    if (candidatePosition != null && startPosition != null) {
        return candidatePosition >= startPosition
    }

    val candidateProgression = candidate.extras["totalProgression"]?.toDoubleOrNull()
        ?: candidate.extras["progression"]?.toDoubleOrNull()
    val startProgression = startFrom.extras["totalProgression"]?.toDoubleOrNull()
        ?: startFrom.extras["progression"]?.toDoubleOrNull()
    if (candidateProgression != null && startProgression != null) {
        return candidateProgression >= startProgression
    }

    return true
}
