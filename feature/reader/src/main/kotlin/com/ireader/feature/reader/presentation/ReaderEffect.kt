package com.ireader.feature.reader.presentation

sealed interface ReaderEffect {
    data class Snackbar(val message: UiText) : ReaderEffect
    data object Back : ReaderEffect
    data class OpenAnnotations(val bookId: String) : ReaderEffect
}

