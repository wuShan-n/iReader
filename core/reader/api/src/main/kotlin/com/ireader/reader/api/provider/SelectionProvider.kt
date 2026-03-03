package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.NormalizedRect

/**
 * Unified current-selection API for reflow/fixed engines.
 */
interface SelectionProvider {

    data class Selection(
        val locator: Locator,
        val bounds: NormalizedRect? = null,
        val start: Locator? = null,
        val end: Locator? = null,
        val selectedText: String? = null,
        val rects: List<NormalizedRect> = emptyList(),
        val extras: Map<String, String> = emptyMap()
    )

    suspend fun currentSelection(): ReaderResult<Selection?>

    suspend fun clearSelection(): ReaderResult<Unit>
}
