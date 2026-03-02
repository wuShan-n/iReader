package com.ireader.engines.common.android.error

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.zip.ZipException

fun Throwable.toReaderError(
    invalidPasswordKeywords: Set<String> = emptySet(),
    preserveInternalMessage: Boolean = true
): ReaderError {
    val lowerMessage = (message ?: "").lowercase(Locale.US)
    return when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is ZipException -> ReaderError.CorruptOrInvalid(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> {
            val invalidPassword = invalidPasswordKeywords.any { keyword ->
                keyword.isNotBlank() && lowerMessage.contains(keyword.lowercase(Locale.US))
            }
            if (invalidPassword) {
                ReaderError.InvalidPassword(cause = this)
            } else if (preserveInternalMessage) {
                ReaderError.Internal(message = message, cause = this)
            } else {
                ReaderError.Internal(cause = this)
            }
        }
    }
}
