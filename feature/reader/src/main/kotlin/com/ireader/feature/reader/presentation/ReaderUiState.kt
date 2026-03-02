package com.ireader.feature.reader.presentation

import android.graphics.Bitmap

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Html(
        val pageId: String,
        val inlineHtml: String?,
        val contentUrl: String?,
        val baseUrl: String?
    ) : ReaderUiState

    data class Text(
        val text: String
    ) : ReaderUiState

    data class BitmapPage(
        val bitmap: Bitmap
    ) : ReaderUiState

    data class Unsupported(
        val message: String
    ) : ReaderUiState

    data class Error(
        val message: String
    ) : ReaderUiState
}
