package com.ireader.feature.reader.presentation

enum class ReaderErrorAction {
    Retry,
    Back
}

data class ReaderUiError(
    val message: UiText,
    val actionLabel: UiText? = null,
    val action: ReaderErrorAction? = null,
    val debugCode: String? = null
)

