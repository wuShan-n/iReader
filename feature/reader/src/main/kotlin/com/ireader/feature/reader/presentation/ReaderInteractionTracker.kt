package com.ireader.feature.reader.presentation

internal sealed interface ReaderInteractionEvent {
    data object TapPrev : ReaderInteractionEvent
    data object TapNext : ReaderInteractionEvent
    data object CenterTapToggleChrome : ReaderInteractionEvent
    data object DragPrev : ReaderInteractionEvent
    data object DragNext : ReaderInteractionEvent
    data object UndoPageTurn : ReaderInteractionEvent
    data object ClosePanelByTap : ReaderInteractionEvent
}

internal interface ReaderInteractionTracker {
    fun track(event: ReaderInteractionEvent)

    object None : ReaderInteractionTracker {
        override fun track(event: ReaderInteractionEvent) = Unit
    }
}
