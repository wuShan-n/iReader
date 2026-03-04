package com.ireader.feature.reader.presentation

import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.model.DocumentCapabilities

enum class ReaderSheet {
    None,
    Toc,
    Search,
    Brightness,
    Settings,
    SettingsFont,
    SettingsSpacing,
    SettingsPageTurn,
    SettingsMoreBackground,
    ReaderMore,
    FullSettings
}

enum class ReaderMenuTab {
    Toc,
    Notes,
    Bookmarks
}

enum class ReaderDockTab {
    Menu,
    Brightness,
    Settings
}

data class ReaderOverlayState(
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val showGestureHint: Boolean = true
)

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
    val bookId: Long = -1L,
    val title: String? = null,
    val isOpening: Boolean = false,
    val isRenderingFinal: Boolean = false,
    val chromeVisible: Boolean = true,
    val sheet: ReaderSheet = ReaderSheet.None,
    val activeDockTab: ReaderDockTab? = null,
    val activeMenuTab: ReaderMenuTab = ReaderMenuTab.Toc,
    val overlayState: ReaderOverlayState = ReaderOverlayState(),
    val capabilities: DocumentCapabilities? = null,
    val renderState: RenderState? = null,
    val page: RenderPage? = null,
    val controller: ReaderController? = null,
    val resources: ResourceProvider? = null,
    val toc: TocState = TocState(),
    val search: SearchState = SearchState(),
    val currentConfig: RenderConfig? = null,
    val pageTurnMode: PageTurnMode = PageTurnMode.COVER_HORIZONTAL,
    val pageTransition: PageTurnTransition = PageTurnTransition(),
    val displayPrefs: ReaderDisplayPrefs = ReaderDisplayPrefs(),
    val isNightMode: Boolean = false,
    val passwordPrompt: PasswordPrompt? = null,
    val error: ReaderUiError? = null
)
