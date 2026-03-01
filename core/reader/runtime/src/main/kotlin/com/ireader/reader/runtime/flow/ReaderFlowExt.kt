package com.ireader.reader.runtime.flow

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.runtime.error.toReaderError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Flow<T>.asReaderResult(
    mapper: (Throwable) -> ReaderError = { it.toReaderError() }
): Flow<ReaderResult<T>> =
    this
        .map<T, ReaderResult<T>> { ReaderResult.Ok(it) }
        .catch { emit(ReaderResult.Err(mapper(it))) }

