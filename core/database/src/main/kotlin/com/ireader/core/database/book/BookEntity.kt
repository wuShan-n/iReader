package com.ireader.core.database.book

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ireader.reader.model.BookFormat

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val format: BookFormat,
    val title: String?,
    val author: String?,
    val language: String?,
    val identifier: String?,
    val canonicalPath: String,
    val originalUri: String?,
    val displayName: String?,
    val mimeType: String?,
    val fingerprintSha256: String,
    val sizeBytes: Long,
    val coverPath: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
