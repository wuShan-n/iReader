package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
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
        val iterator = publication.search(
            query = query,
            options = options.toReadiumSearchOptions()
        ) ?: return@flow

        var emitted = 0
        try {
            while (true) {
                val page = when (val result = iterator.next()) {
                    is Try.Success -> result.value
                    is Try.Failure -> return@flow
                } ?: break

                for (locator in page.locators) {
                    val appLocator = locator.toAppLocator()
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
