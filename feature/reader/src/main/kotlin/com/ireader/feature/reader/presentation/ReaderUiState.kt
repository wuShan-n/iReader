package com.ireader.feature.reader.presentation

import android.graphics.Bitmap
import com.ireader.reader.api.render.ReaderNavigatorAdapter

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Navigator(
        val sessionId: String,
        val adapter: ReaderNavigatorAdapter
    ) : ReaderUiState

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
