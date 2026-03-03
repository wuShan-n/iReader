package com.ireader.feature.reader.domain.impl

import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.IndexState
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.feature.reader.domain.ReaderBookInfo
import com.ireader.feature.reader.domain.ReaderBookRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataReaderBookRepository @Inject constructor(
    private val bookRepo: BookRepo
) : ReaderBookRepository {

    override suspend fun resolveBook(routeBookId: String): ReaderBookInfo? {
        val bookId = routeBookId.toLongOrNull() ?: return null
        val book = bookRepo.getById(bookId) ?: return null

        val file = File(book.canonicalPath)
        if (!file.exists()) {
            bookRepo.setIndexState(bookId = bookId, state = IndexState.MISSING, error = "File not found")
            return null
        }

        return ReaderBookInfo(
            routeBookId = routeBookId,
            bookId = bookId,
            title = book.title?.takeIf { it.isNotBlank() } ?: book.fileName,
            format = book.format,
            source = FileDocumentSource(
                file = file,
                displayName = book.fileName,
                mimeType = book.mimeType
            )
        )
    }

    override suspend fun markOpened(bookId: Long) {
        bookRepo.touchLastOpened(bookId)
    }
}

