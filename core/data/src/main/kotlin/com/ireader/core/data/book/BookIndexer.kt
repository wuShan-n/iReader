package com.ireader.core.data.book

interface BookIndexer {
    suspend fun index(bookId: Long): Result<Unit>
    suspend fun reindex(bookId: Long): Result<Unit>
}
