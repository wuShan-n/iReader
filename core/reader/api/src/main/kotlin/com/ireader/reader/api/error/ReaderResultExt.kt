package com.ireader.reader.api.error

inline fun <T, R> ReaderResult<T>.map(transform: (T) -> R): ReaderResult<R> =
    when (this) {
        is ReaderResult.Ok -> ReaderResult.Ok(transform(value))
        is ReaderResult.Err -> this
    }

inline fun <T, R> ReaderResult<T>.flatMap(transform: (T) -> ReaderResult<R>): ReaderResult<R> =
    when (this) {
        is ReaderResult.Ok -> transform(value)
        is ReaderResult.Err -> this
    }

inline fun <T> ReaderResult<T>.mapError(transform: (ReaderError) -> ReaderError): ReaderResult<T> =
    when (this) {
        is ReaderResult.Ok -> this
        is ReaderResult.Err -> ReaderResult.Err(transform(error))
    }

inline fun <T, R> ReaderResult<T>.fold(
    onOk: (T) -> R,
    onErr: (ReaderError) -> R
): R =
    when (this) {
        is ReaderResult.Ok -> onOk(value)
        is ReaderResult.Err -> onErr(error)
    }

inline fun <T> ReaderResult<T>.onOk(block: (T) -> Unit): ReaderResult<T> =
    also { if (this is ReaderResult.Ok) block(value) }

inline fun <T> ReaderResult<T>.onErr(block: (ReaderError) -> Unit): ReaderResult<T> =
    also { if (this is ReaderResult.Err) block(error) }

