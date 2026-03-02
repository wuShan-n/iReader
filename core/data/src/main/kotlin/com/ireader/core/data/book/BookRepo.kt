package com.ireader.core.data.book

import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BookRepo @Inject constructor(
    private val dao: BookDao
) {
    suspend fun upsert(entity: BookEntity) = dao.upsert(entity)

    suspend fun findByFingerprint(fingerprint: String) = dao.findByFingerprint(fingerprint)

    suspend fun getById(bookId: String) = dao.getById(bookId)

    suspend fun deleteById(bookId: String) = dao.deleteById(bookId)

    fun observeById(bookId: String): Flow<BookEntity?> = dao.observeById(bookId)

    fun observeLibrary(sort: LibrarySort): Flow<List<BookEntity>> {
        return when (sort) {
            LibrarySort.RECENTLY_UPDATED -> dao.observeByUpdatedDesc()
            LibrarySort.RECENTLY_ADDED -> dao.observeByCreatedDesc()
            LibrarySort.TITLE_AZ -> dao.observeByTitleAsc()
            LibrarySort.AUTHOR_AZ -> dao.observeByAuthorAsc()
        }
    }
}
