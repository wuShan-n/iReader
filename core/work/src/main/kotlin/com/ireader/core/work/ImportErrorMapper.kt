package com.ireader.core.work

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException

internal fun Throwable.toImportError(): Pair<String, String> {
    return when (this) {
        is ReaderError -> code to (message ?: code)
        is CancellationException -> "CANCELLED" to (message ?: "Cancelled")
        is FileNotFoundException -> "NOT_FOUND" to (message ?: "Not found")
        is SecurityException -> "PERMISSION_DENIED" to (message ?: "Permission denied")
        is ZipException -> "CORRUPT_OR_INVALID" to (message ?: "Corrupt or invalid document")
        is IOException -> "IO" to (message ?: "I/O error")
        else -> "INTERNAL" to (message ?: this::class.java.simpleName)
    }
}
