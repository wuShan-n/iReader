package com.ireader.core.data.book

import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepo @Inject constructor(
    private val dao: BookDao
) {
    suspend fun upsert(entity: BookEntity) = dao.upsert(entity)

    suspend fun findByFingerprint(fingerprint: String) = dao.findByFingerprint(fingerprint)

    suspend fun getById(bookId: String) = dao.getById(bookId)

    suspend fun deleteById(bookId: String) = dao.deleteById(bookId)
}
