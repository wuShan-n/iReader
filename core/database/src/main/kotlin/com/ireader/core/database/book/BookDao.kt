package com.ireader.core.database.book

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.ireader.core.database.collection.BookCollectionEntity
import com.ireader.core.database.progress.ProgressEntity
import com.ireader.reader.model.BookFormat
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

    @Query("SELECT * FROM books ORDER BY updatedAtEpochMs DESC")
    suspend fun listAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE documentId = :documentId LIMIT 1")
    suspend fun getByDocumentId(documentId: String): BookEntity?

    @Query("DELETE FROM books WHERE bookId = :bookId")
    suspend fun deleteById(bookId: Long)

    @Query("SELECT * FROM books WHERE bookId = :bookId LIMIT 1")
    fun observeById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE indexState = 'MISSING' ORDER BY updatedAtEpochMs DESC")
    fun observeMissing(): Flow<List<BookEntity>>

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

    @Query(
        "UPDATE books SET sourceUri = :sourceUri, canonicalPath = :canonicalPath, " +
            "lastModifiedEpochMs = :lastModifiedEpochMs, updatedAtEpochMs = :updatedAt WHERE bookId = :bookId"
    )
    suspend fun updateSource(
        bookId: Long,
        sourceUri: String?,
        canonicalPath: String,
        lastModifiedEpochMs: Long?,
        updatedAt: Long
    )

    @Query(
        "UPDATE books SET documentId = :documentId, format = :format, title = :title, author = :author, " +
            "language = :language, identifier = :identifier, series = :series, description = :description, " +
            "coverPath = :coverPath, capabilitiesJson = :capabilitiesJson, indexState = :indexState, " +
            "indexError = :indexError, updatedAtEpochMs = :updatedAt WHERE bookId = :bookId"
    )
    suspend fun updateMetadata(
        bookId: Long,
        documentId: String?,
        format: BookFormat,
        title: String?,
        author: String?,
        language: String?,
        identifier: String?,
        series: String?,
        description: String?,
        coverPath: String?,
        capabilitiesJson: String?,
        indexState: IndexState,
        indexError: String?,
        updatedAt: Long
    )
}
