package com.ireader.core.database.book

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ireader.reader.model.BookFormat

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["documentId"], unique = true),
        Index(value = ["fingerprintSha256"]),
        Index(value = ["addedAtEpochMs"]),
        Index(value = ["updatedAtEpochMs"]),
        Index(value = ["lastOpenedAtEpochMs"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val bookId: Long = 0,
    val documentId: String?,
    val sourceUri: String?,
    val sourceType: BookSourceType,
    val format: BookFormat,
    val fileName: String,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val lastModifiedEpochMs: Long?,
    val canonicalPath: String,
    val fingerprintSha256: String,
    val title: String?,
    val author: String?,
    val language: String?,
    val identifier: String?,
    val series: String?,
    val description: String?,
    val coverPath: String? = null,
    val favorite: Boolean = false,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val indexState: IndexState = IndexState.PENDING,
    val indexError: String? = null,
    val capabilitiesJson: String? = null,
    val addedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastOpenedAtEpochMs: Long? = null
)

enum class BookSourceType {
    IMPORTED_COPY,
    CONTENT_URI,
    FILE_PATH
}

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
