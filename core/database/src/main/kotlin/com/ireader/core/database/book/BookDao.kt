package com.ireader.core.database.book

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.ireader.core.database.collection.BookCollectionEntity
import com.ireader.core.database.progress.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookEntity): Long

    @Query(
        "SELECT * FROM books WHERE fingerprintSha256 = :fingerprint " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findByFingerprint(fingerprint: String): BookEntity?

    @Query("SELECT * FROM books WHERE bookId = :bookId LIMIT 1")
    suspend fun getById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE documentId = :documentId LIMIT 1")
    suspend fun getByDocumentId(documentId: String): BookEntity?

    @Query("DELETE FROM books WHERE bookId = :bookId")
    suspend fun deleteById(bookId: Long)

    @Query("SELECT * FROM books WHERE bookId = :bookId LIMIT 1")
    fun observeById(bookId: Long): Flow<BookEntity?>

    @RawQuery(observedEntities = [BookEntity::class, ProgressEntity::class, BookCollectionEntity::class])
    fun observeLibrary(query: SupportSQLiteQuery): Flow<List<LibraryBookRow>>

    @Query(
        "UPDATE books SET indexState = :state, indexError = :error, updatedAtEpochMs = :updatedAt WHERE bookId = :bookId"
    )
    suspend fun updateIndexState(bookId: Long, state: IndexState, error: String?, updatedAt: Long)

    @Query(
        "UPDATE books SET lastOpenedAtEpochMs = :lastOpenedAt, updatedAtEpochMs = :updatedAt WHERE bookId = :bookId"
    )
    suspend fun updateLastOpened(bookId: Long, lastOpenedAt: Long, updatedAt: Long)

    @Query("UPDATE books SET favorite = :favorite, updatedAtEpochMs = :updatedAt WHERE bookId = :bookId")
    suspend fun updateFavorite(bookId: Long, favorite: Boolean, updatedAt: Long)

    @Query(
        "UPDATE books SET readingStatus = :status, updatedAtEpochMs = :updatedAt WHERE bookId = :bookId"
    )
    suspend fun updateReadingStatus(bookId: Long, status: ReadingStatus, updatedAt: Long)
}
