package com.ireader.core.files.importing

enum class ImportJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class ImportJobState(
    val jobId: String,
    val status: ImportJobStatus,
    val total: Int,
    val done: Int,
    val currentTitle: String?,
    val errorMessage: String?
)
