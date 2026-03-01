package com.ireader.reader.runtime.error

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException

internal fun Throwable.toReaderError(): ReaderError =
    when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is ZipException -> ReaderError.CorruptOrInvalid(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(cause = this)
    }

