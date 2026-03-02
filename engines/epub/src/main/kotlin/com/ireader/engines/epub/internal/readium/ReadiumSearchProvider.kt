package com.ireader.engines.epub.internal.readium

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
import org.readium.r2.shared.util.use

internal class ReadiumSearchProvider(
    private val publication: Publication
) : SearchProvider {

    @OptIn(ExperimentalReadiumApi::class)
    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow

        val iterator = publication.search(
            query = query,
            options = SearchService.Options(
                caseSensitive = options.caseSensitive,
                wholeWord = options.wholeWord
            )
        ) ?: return@flow

        iterator.use {
            var emitted = 0
            while (emitted < options.maxHits) {
                when (val next = iterator.next()) {
                    is Try.Failure -> break
                    is Try.Success -> {
                        val collection = next.value ?: break
                        for (item in collection.locators) {
                            if (emitted >= options.maxHits) break
                            val mapped = ReadiumLocatorMapper.toModel(item)
                            emit(
                                SearchHit(
                                    range = LocatorRange(start = mapped, end = mapped),
                                    excerpt = buildExcerpt(item),
                                    sectionTitle = item.title
                                )
                            )
                            emitted += 1
                        }
                    }
                }
            }
        }
    }

    private fun buildExcerpt(locator: org.readium.r2.shared.publication.Locator): String {
        val text = locator.text
        val before = text.before.orEmpty().takeLast(40)
        val highlight = text.highlight.orEmpty()
        val after = text.after.orEmpty().take(60)
        val content = (before + highlight + after).trim()
        return if (content.isBlank()) {
            locator.href.toString()
        } else {
            content
        }
    }
}
