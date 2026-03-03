package com.ireader.feature.reader.domain

import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.model.BookFormat

data class ReaderBookInfo(
    val routeBookId: String,
    val bookId: Long,
    val title: String?,
    val format: BookFormat,
    val source: DocumentSource
)

interface ReaderBookRepository {
    suspend fun resolveBook(routeBookId: String): ReaderBookInfo?
    suspend fun markOpened(bookId: Long)
}

