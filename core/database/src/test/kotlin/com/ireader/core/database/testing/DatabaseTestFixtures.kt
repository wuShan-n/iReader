package com.ireader.core.database.testing

import androidx.room.Room
import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.annotation.AnnotationAnchorType
import com.ireader.core.database.annotation.AnnotationEntity
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.database.progress.ProgressEntity
import com.ireader.reader.model.BookFormat
import org.robolectric.RuntimeEnvironment

fun inMemoryReaderDatabase(): ReaderDatabase {
    return Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        ReaderDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
}

fun sampleBook(
    documentId: String? = "doc-1",
    fingerprint: String = "fingerprint-1",
    title: String? = "Sample Title",
    updatedAtEpochMs: Long = 100L,
    addedAtEpochMs: Long = 100L,
    sourceUri: String? = "content://book/1",
    canonicalPath: String = "/tmp/book-1.epub",
    indexState: IndexState = IndexState.PENDING,
    readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    format: BookFormat = BookFormat.EPUB
): BookEntity {
    return BookEntity(
        documentId = documentId,
        sourceUri = sourceUri,
        sourceType = BookSourceType.IMPORTED_COPY,
        format = format,
        fileName = "book.epub",
        mimeType = "application/epub+zip",
        fileSizeBytes = 1024L,
        lastModifiedEpochMs = 99L,
        canonicalPath = canonicalPath,
        fingerprintSha256 = fingerprint,
        title = title,
        author = "Author",
        language = "en",
        identifier = "id-1",
        series = null,
        description = "desc",
        coverPath = null,
        favorite = false,
        readingStatus = readingStatus,
        indexState = indexState,
        indexError = null,
        capabilitiesJson = null,
        addedAtEpochMs = addedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        lastOpenedAtEpochMs = null
    )
}

fun sampleProgress(
    bookId: Long,
    progression: Double = 0.35,
    updatedAtEpochMs: Long = 200L
): ProgressEntity {
    return ProgressEntity(
        bookId = bookId,
        locatorJson = """{"scheme":"txt.stable.anchor","value":"2049:0"}""",
        progression = progression,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun sampleCollection(
    name: String,
    sortOrder: Int,
    createdAtEpochMs: Long = 10L
): CollectionEntity {
    return CollectionEntity(
        name = name,
        createdAtEpochMs = createdAtEpochMs,
        sortOrder = sortOrder
    )
}

fun sampleImportJob(
    jobId: String = "job-1",
    status: ImportStatus = ImportStatus.QUEUED,
    total: Int = 2,
    done: Int = 0,
    currentTitle: String? = null,
    errorMessage: String? = null,
    updatedAtEpochMs: Long = 100L
): ImportJobEntity {
    return ImportJobEntity(
        jobId = jobId,
        status = status,
        total = total,
        done = done,
        currentTitle = currentTitle,
        errorMessage = errorMessage,
        sourceTreeUri = null,
        duplicateStrategy = "SKIP",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun sampleImportItem(
    jobId: String,
    uri: String,
    status: ImportItemStatus,
    bookId: Long? = null,
    updatedAtEpochMs: Long
): ImportItemEntity {
    return ImportItemEntity(
        jobId = jobId,
        uri = uri,
        displayName = "book",
        mimeType = "application/epub+zip",
        sizeBytes = 100L,
        status = status,
        bookId = bookId,
        fingerprintSha256 = null,
        errorCode = null,
        errorMessage = null,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun sampleAnnotation(
    id: String,
    documentId: String,
    anchorType: String = AnnotationAnchorType.FIXED_RECTS,
    updatedAtEpochMs: Long = 100L
): AnnotationEntity {
    return AnnotationEntity(
        id = id,
        documentId = documentId,
        type = "HIGHLIGHT",
        anchorType = anchorType,
        rangeStartLocatorJson = if (anchorType == AnnotationAnchorType.REFLOW_RANGE) """{"scheme":"epub.cfi","value":"a"}""" else null,
        rangeEndLocatorJson = if (anchorType == AnnotationAnchorType.REFLOW_RANGE) """{"scheme":"epub.cfi","value":"b"}""" else null,
        pageLocatorJson = if (anchorType == AnnotationAnchorType.FIXED_RECTS) """{"scheme":"pdf.page","value":"1"}""" else null,
        rectsJson = if (anchorType == AnnotationAnchorType.FIXED_RECTS) """[{"left":0.1,"top":0.1,"right":0.2,"bottom":0.2}]""" else null,
        content = "note",
        styleJson = """{"colorArgb":123}""",
        extraJson = """{"k":"v"}""",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = updatedAtEpochMs
    )
}
