package com.ireader.core.data.book

import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.BookCollectionEntity
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.reader.model.BookFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BookRepo @Inject constructor(
    private val bookDao: BookDao,
    private val collectionDao: CollectionDao,
    private val bookCollectionDao: BookCollectionDao
) {
    suspend fun upsert(entity: BookEntity): Long = bookDao.upsert(entity)

    suspend fun findByFingerprint(fingerprint: String): BookEntity? = bookDao.findByFingerprint(fingerprint)

    suspend fun getById(bookId: Long): BookEntity? = bookDao.getById(bookId)

    suspend fun getRecordById(bookId: Long): BookRecord? = bookDao.getById(bookId)?.toRecord()

    suspend fun listAll(): List<BookEntity> = bookDao.listAll()

    suspend fun getByDocumentId(documentId: String): BookEntity? = bookDao.getByDocumentId(documentId)

    suspend fun deleteById(bookId: Long) = bookDao.deleteById(bookId)

    fun observeById(bookId: Long): Flow<BookEntity?> = bookDao.observeById(bookId)

    fun observeMissingBooks(): Flow<List<BookEntity>> = bookDao.observeMissing()

    fun observeLibrary(query: LibraryQuery): Flow<List<LibraryBookItem>> {
        val sql = LibrarySqlBuilder.build(query)
        return bookDao.observeLibrary(sql).map { rows ->
            rows.map { row ->
                LibraryBookItem(
                    book = row.book.toRecord(),
                    progression = (row.progression ?: 0.0).coerceIn(0.0, 1.0),
                    progressUpdatedAtEpochMs = row.progressUpdatedAtEpochMs
                )
            }
        }
    }

    suspend fun setIndexState(bookId: Long, state: IndexState, error: String? = null) {
        bookDao.updateIndexState(
            bookId = bookId,
            state = state.toDb(),
            error = error,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun touchLastOpened(bookId: Long) {
        val now = System.currentTimeMillis()
        bookDao.updateLastOpened(
            bookId = bookId,
            lastOpenedAt = now,
            updatedAt = now
        )
    }

    suspend fun setFavorite(bookId: Long, favorite: Boolean) {
        bookDao.updateFavorite(
            bookId = bookId,
            favorite = favorite,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun setReadingStatus(bookId: Long, status: ReadingStatus) {
        bookDao.updateReadingStatus(
            bookId = bookId,
            status = status.toDb(),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateBookSource(
        bookId: Long,
        sourceUri: String?,
        canonicalPath: String,
        lastModifiedEpochMs: Long?
    ) {
        bookDao.updateSource(
            bookId = bookId,
            sourceUri = sourceUri,
            canonicalPath = canonicalPath,
            lastModifiedEpochMs = lastModifiedEpochMs,
            updatedAt = System.currentTimeMillis()
        )
    }

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
        indexError: String?
    ) {
        bookDao.updateMetadata(
            bookId = bookId,
            documentId = documentId,
            format = format,
            title = title,
            author = author,
            language = language,
            identifier = identifier,
            series = series,
            description = description,
            coverPath = coverPath,
            capabilitiesJson = capabilitiesJson,
            indexState = indexState.toDb(),
            indexError = indexError,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun observeCollections(): Flow<List<CollectionItem>> = collectionDao.observeAll().map { list ->
        list.map(CollectionEntity::toData)
    }

    suspend fun createCollection(name: String): Long {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Collection name cannot be blank" }

        val existing = collectionDao.getByName(normalized)
        if (existing != null) {
            return existing.collectionId
        }

        return collectionDao.upsert(
            CollectionEntity(
                name = normalized,
                createdAtEpochMs = System.currentTimeMillis(),
                sortOrder = 0
            )
        )
    }

    suspend fun addToCollection(bookId: Long, collectionId: Long) {
        bookCollectionDao.insert(BookCollectionEntity(bookId = bookId, collectionId = collectionId))
    }

    suspend fun removeFromCollection(bookId: Long, collectionId: Long) {
        bookCollectionDao.delete(bookId = bookId, collectionId = collectionId)
    }

    suspend fun listCollectionIdsForBook(bookId: Long): List<Long> {
        return bookCollectionDao.listCollectionIdsForBook(bookId)
    }
}
