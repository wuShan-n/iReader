package com.ireader.core.files.importing

interface ImportWorkScheduler {
    fun enqueue(jobId: String)
    suspend fun cancel(jobId: String)
}
