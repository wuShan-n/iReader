package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator

sealed interface ReaderIntent {
    data class Start(val bookId: Long, val locatorArg: String?) : ReaderIntent
    data object RetryOpen : ReaderIntent
    data class SubmitPassword(val password: String) : ReaderIntent
    data object CancelPassword : ReaderIntent

    data class LayoutChanged(val constraints: LayoutConstraints) : ReaderIntent
    data object RefreshPage : ReaderIntent

    data object ToggleChrome : ReaderIntent
    data object OpenAnnotations : ReaderIntent
    data object OpenToc : ReaderIntent
    data object OpenSearch : ReaderIntent
    data object OpenSettings : ReaderIntent
    data object CloseSheet : ReaderIntent

    data object Next : ReaderIntent
    data object Prev : ReaderIntent
    data class GoTo(val locator: Locator) : ReaderIntent
    data class GoToProgress(val percent: Double) : ReaderIntent

    data class SearchQueryChanged(val query: String) : ReaderIntent
    data object ExecuteSearch : ReaderIntent

    data class UpdateConfig(val config: RenderConfig, val persist: Boolean) : ReaderIntent
}
