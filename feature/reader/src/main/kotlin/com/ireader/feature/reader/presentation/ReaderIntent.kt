package com.ireader.feature.reader.presentation

import com.ireader.core.data.reader.ReaderBackgroundPreset
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

enum class GestureAxis {
    HORIZONTAL,
    VERTICAL
}

sealed interface ReaderIntent {
    data class Start(val bookId: Long, val locatorArg: String?) : ReaderIntent
    data object RetryOpen : ReaderIntent
    data class SubmitPassword(val password: String) : ReaderIntent
    data object CancelPassword : ReaderIntent

    data class LayoutChanged(val constraints: LayoutConstraints) : ReaderIntent
    data class TextLayouterFactoryChanged(val factory: TextLayouterFactory) : ReaderIntent
    data object RefreshPage : ReaderIntent

    data object ToggleChrome : ReaderIntent
    data object ToggleImmersiveChrome : ReaderIntent
    data object BackPressed : ReaderIntent
    data class HandleTap(
        val xPx: Float,
        val yPx: Float,
        val viewportWidthPx: Int,
        val viewportHeightPx: Int,
        val allowPageTurn: Boolean = true
    ) : ReaderIntent
    data class HandleDragEnd(
        val axis: GestureAxis,
        val deltaPx: Float,
        val viewportMainAxisPx: Int
    ) : ReaderIntent
    data object OpenAnnotations : ReaderIntent
    data object OpenToc : ReaderIntent
    data object OpenMenu : ReaderIntent
    data class ToggleDockTab(val tab: ReaderDockTab) : ReaderIntent
    data object CloseDockPanel : ReaderIntent
    data class SetMenuTab(val tab: ReaderMenuTab) : ReaderIntent
    data object OpenSearch : ReaderIntent
    data object OpenBrightness : ReaderIntent
    data object OpenSettings : ReaderIntent
    data class OpenSettingsSub(val sheet: ReaderSheet) : ReaderIntent
    data object OpenReaderMore : ReaderIntent
    data object OpenFullSettings : ReaderIntent
    data object ShareBook : ReaderIntent
    data object CreateAnnotation : ReaderIntent
    data object ToggleNightMode : ReaderIntent
    data class UpdateBrightness(val value: Float) : ReaderIntent
    data class SetUseSystemBrightness(val enabled: Boolean) : ReaderIntent
    data class SetEyeProtection(val enabled: Boolean) : ReaderIntent
    data class SelectBackground(val preset: ReaderBackgroundPreset) : ReaderIntent
    data class SetReadingProgressVisible(val visible: Boolean) : ReaderIntent
    data class SetFullScreenMode(val enabled: Boolean) : ReaderIntent
    data class SetVolumeKeyPaging(val enabled: Boolean) : ReaderIntent
    data object CloseSheet : ReaderIntent
    data object BackInSheetHierarchy : ReaderIntent

    data object Next : ReaderIntent
    data object Prev : ReaderIntent
    data class GoTo(val locator: Locator) : ReaderIntent
    data class GoToProgress(val percent: Double) : ReaderIntent
    data class ActivateLink(val link: DocumentLink) : ReaderIntent
    data class SelectionStart(val locator: Locator) : ReaderIntent
    data class SelectionUpdate(val locator: Locator) : ReaderIntent
    data object SelectionFinish : ReaderIntent
    data object ClearSelection : ReaderIntent

    data class SearchQueryChanged(val query: String) : ReaderIntent
    data object ExecuteSearch : ReaderIntent
    data class ApplyTextBreakPatch(
        val direction: TextBreakPatchDirection,
        val state: TextBreakPatchState
    ) : ReaderIntent
    data object ClearTextBreakPatches : ReaderIntent

    data class UpdateConfig(val config: RenderConfig, val persist: Boolean) : ReaderIntent
}
