package com.ireader.core.data.book

import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.IndexState as DbIndexState
import com.ireader.core.database.book.ReadingStatus as DbReadingStatus
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.reader.model.BookFormat

enum class ReadingStatus {
    UNREAD,
    READING,
    FINISHED
}

enum class IndexState {
    PENDING,
    INDEXED,
    ERROR,
    MISSING
}

data class CollectionItem(
    val collectionId: Long,
    val name: String,
    val createdAtEpochMs: Long,
    val sortOrder: Int
)

data class BookRecord(
    val bookId: Long,
    val documentId: String?,
    val sourceUri: String?,
    val format: BookFormat,
    val fileName: String,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val lastModifiedEpochMs: Long?,
    val canonicalPath: String,
    val title: String?,
    val author: String?,
    val language: String?,
    val identifier: String?,
    val series: String?,
    val description: String?,
    val coverPath: String?,
    val favorite: Boolean,
    val readingStatus: ReadingStatus,
    val indexState: IndexState,
    val indexError: String?,
    val capabilitiesJson: String?,
    val addedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastOpenedAtEpochMs: Long?
)

internal fun DbReadingStatus.toData(): ReadingStatus =
    when (this) {
        DbReadingStatus.UNREAD -> ReadingStatus.UNREAD
        DbReadingStatus.READING -> ReadingStatus.READING
        DbReadingStatus.FINISHED -> ReadingStatus.FINISHED
    }

internal fun ReadingStatus.toDb(): DbReadingStatus =
    when (this) {
        ReadingStatus.UNREAD -> DbReadingStatus.UNREAD
        ReadingStatus.READING -> DbReadingStatus.READING
        ReadingStatus.FINISHED -> DbReadingStatus.FINISHED
    }

internal fun DbIndexState.toData(): IndexState =
    when (this) {
        DbIndexState.PENDING -> IndexState.PENDING
        DbIndexState.INDEXED -> IndexState.INDEXED
        DbIndexState.ERROR -> IndexState.ERROR
        DbIndexState.MISSING -> IndexState.MISSING
    }

internal fun IndexState.toDb(): DbIndexState =
    when (this) {
        IndexState.PENDING -> DbIndexState.PENDING
        IndexState.INDEXED -> DbIndexState.INDEXED
        IndexState.ERROR -> DbIndexState.ERROR
        IndexState.MISSING -> DbIndexState.MISSING
    }

internal fun CollectionEntity.toData(): CollectionItem =
    CollectionItem(
        collectionId = collectionId,
        name = name,
        createdAtEpochMs = createdAtEpochMs,
        sortOrder = sortOrder
    )

internal fun BookEntity.toRecord(): BookRecord =
    BookRecord(
        bookId = bookId,
        documentId = documentId,
        sourceUri = sourceUri,
        format = format,
        fileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        lastModifiedEpochMs = lastModifiedEpochMs,
        canonicalPath = canonicalPath,
        title = title,
        author = author,
        language = language,
        identifier = identifier,
        series = series,
        description = description,
        coverPath = coverPath,
        favorite = favorite,
        readingStatus = readingStatus.toData(),
        indexState = indexState.toData(),
        indexError = indexError,
        capabilitiesJson = capabilitiesJson,
        addedAtEpochMs = addedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        lastOpenedAtEpochMs = lastOpenedAtEpochMs
    )

