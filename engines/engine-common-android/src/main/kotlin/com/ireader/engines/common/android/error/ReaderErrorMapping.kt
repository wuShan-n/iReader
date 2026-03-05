package com.ireader.engines.common.android.error

import com.ireader.reader.api.error.ReaderError
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeoutException
import java.util.concurrent.CancellationException
import java.util.zip.ZipException

fun Throwable.toReaderError(
    invalidPasswordKeywords: Set<String> = emptySet(),
    preserveInternalMessage: Boolean = false
): ReaderError {
    if (this is ReaderError) {
        return this
    }
    val chain = buildList {
        var current: Throwable? = this@toReaderError
        while (current != null && size < 8) {
            add(current)
            current = current.cause
        }
    }
    val messages = chain
        .mapNotNull { it.message }
        .map { it.lowercase(Locale.US) }

    fun hasType(predicate: (Throwable) -> Boolean): Boolean {
        return chain.any(predicate)
    }

    val invalidPassword = invalidPasswordKeywords
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .any { keyword ->
            val normalized = keyword.lowercase(Locale.US)
            messages.any { message -> message.contains(normalized) }
        }

    return when {
        hasType { it is CancellationException } -> ReaderError.Cancelled(cause = this)
        hasType { it is FileNotFoundException } -> ReaderError.NotFound(cause = this)
        hasType { it is SecurityException } -> ReaderError.PermissionDenied(cause = this)
        invalidPassword -> ReaderError.InvalidPassword(cause = this)
        hasType { it is ZipException || it is EOFException || it is IllegalArgumentException } ->
            ReaderError.CorruptOrInvalid(cause = this)

        hasType { it is IOException || it is TimeoutException } -> ReaderError.Io(cause = this)
        preserveInternalMessage -> ReaderError.Internal(message = message, cause = this)
        else -> ReaderError.Internal(cause = this)
    }
}
