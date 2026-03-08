package com.ireader.reader.testkit

import com.ireader.reader.api.error.ReaderResult

fun <T> ReaderResult<T>.requireOk(): T {
    return when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> error("Expected ReaderResult.Ok but was $error")
    }
}
