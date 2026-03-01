package com.ireader.core.files.importing

import com.ireader.core.database.importing.ImportStatus

data class ImportJobState(
    val jobId: String,
    val status: ImportStatus,
    val total: Int,
    val done: Int,
    val currentTitle: String?,
    val errorMessage: String?
)
