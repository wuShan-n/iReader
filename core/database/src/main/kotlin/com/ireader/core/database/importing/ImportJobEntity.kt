package com.ireader.core.database.importing

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey val jobId: String,
    val status: ImportStatus,
    val total: Int,
    val done: Int,
    val currentTitle: String?,
    val errorMessage: String?,
    val sourceTreeUri: String?,
    val duplicateStrategy: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
