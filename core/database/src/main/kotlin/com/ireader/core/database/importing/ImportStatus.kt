package com.ireader.core.database.importing

enum class ImportStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
