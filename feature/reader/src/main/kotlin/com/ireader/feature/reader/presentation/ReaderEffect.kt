package com.ireader.feature.reader.presentation

sealed interface ReaderEffect {
    data class Snackbar(val message: UiText) : ReaderEffect
    data object Back : ReaderEffect
    data class OpenAnnotations(val bookId: Long) : ReaderEffect
    data class OpenExternalUrl(val url: String) : ReaderEffect
    data class ShareText(val text: String) : ReaderEffect
}
