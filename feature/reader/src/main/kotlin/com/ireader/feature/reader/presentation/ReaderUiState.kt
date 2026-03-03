package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.model.DocumentCapabilities

enum class ReaderSheet {
    None,
    Toc,
    Search,
    Settings
}

data class PasswordPrompt(
    val reason: UiText = UiText.Dynamic("This file requires a password"),
    val lastTried: String? = null
)

data class TocItem(
    val title: String,
    val locatorEncoded: String,
    val depth: Int
)

data class TocState(
    val isLoading: Boolean = false,
    val items: List<TocItem> = emptyList(),
    val error: UiText? = null
)

data class SearchResultItem(
    val title: String?,
    val excerpt: String,
    val locatorEncoded: String
)

data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val error: UiText? = null
)

data class ReaderUiState(
    val bookId: String = "",
    val title: String? = null,
    val isOpening: Boolean = false,
    val isRenderingFinal: Boolean = false,
    val chromeVisible: Boolean = true,
    val sheet: ReaderSheet = ReaderSheet.None,
    val capabilities: DocumentCapabilities? = null,
    val renderState: RenderState? = null,
    val page: RenderPage? = null,
    val controller: ReaderController? = null,
    val toc: TocState = TocState(),
    val search: SearchState = SearchState(),
    val currentConfig: RenderConfig? = null,
    val passwordPrompt: PasswordPrompt? = null,
    val error: ReaderUiError? = null
)

