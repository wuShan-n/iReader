package com.ireader.reader.api.provider

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow

data class SearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val maxHits: Int = 500,
    val startFrom: Locator? = null
)

data class SearchHit(
    val range: LocatorRange,
    val excerpt: String,
    val sectionTitle: String? = null
)

interface SearchProvider {
    fun search(query: String, options: SearchOptions = SearchOptions()): Flow<SearchHit>
}


