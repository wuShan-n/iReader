package com.ireader.engines.txt.internal.util

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal fun Throwable.toReaderError(): ReaderError {
    return when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(message = message, cause = this)
    }
}

