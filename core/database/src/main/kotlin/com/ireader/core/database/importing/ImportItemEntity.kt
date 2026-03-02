package com.ireader.core.database.importing

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "import_items",
    primaryKeys = ["jobId", "uri"],
    indices = [Index("jobId")]
)
data class ImportItemEntity(
    val jobId: String,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val status: ImportItemStatus,
    val bookId: Long?,
    val fingerprintSha256: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val updatedAtEpochMs: Long
)
