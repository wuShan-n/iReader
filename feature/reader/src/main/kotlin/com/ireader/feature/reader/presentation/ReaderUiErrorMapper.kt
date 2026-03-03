package com.ireader.feature.reader.presentation

import com.ireader.reader.api.error.ReaderError
import javax.inject.Inject

class ReaderUiErrorMapper @Inject constructor() {

    fun map(error: ReaderError): ReaderUiError {
        return when (error) {
            is ReaderError.UnsupportedFormat -> ReaderUiError(
                message = UiText.Dynamic("Unsupported format: ${error.detected ?: "unknown"}"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.NotFound -> ReaderUiError(
                message = UiText.Dynamic("The book file is missing"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.PermissionDenied -> ReaderUiError(
                message = UiText.Dynamic("File permission denied"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )

            is ReaderError.InvalidPassword -> ReaderUiError(
                message = UiText.Dynamic("A password is required"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )

            is ReaderError.CorruptOrInvalid -> ReaderUiError(
                message = UiText.Dynamic("The file is corrupt or invalid"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.DrmRestricted -> ReaderUiError(
                message = UiText.Dynamic("This file is DRM protected"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.Io -> ReaderUiError(
                message = UiText.Dynamic("I/O error while reading"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )

            is ReaderError.Cancelled -> ReaderUiError(
                message = UiText.Dynamic("Operation cancelled"),
                debugCode = error.code
            )

            is ReaderError.Internal -> ReaderUiError(
                message = UiText.Dynamic(error.message ?: "Internal reader error"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )
        }
    }
}

