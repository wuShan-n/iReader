package com.ireader.core.files.importing

import kotlinx.coroutines.flow.Flow

interface ImportManager {
    suspend fun enqueue(request: ImportRequest): String
    fun observe(jobId: String): Flow<ImportJobState>
    suspend fun cancel(jobId: String)
}
