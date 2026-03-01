package com.ireader.reader.api.error

sealed interface ReaderResult<out T> {
    data class Ok<T>(val value: T) : ReaderResult<T>
    data class Err(val error: ReaderError) : ReaderResult<Nothing>
}

inline fun <T> ReaderResult<T>.getOrNull(): T? =
    when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> null
    }

inline fun <T> ReaderResult<T>.getOrThrow(): T =
    when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> throw error
    }

