package com.ireader.core.data.importing

import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ImportJobRepo @Inject constructor(
    private val dao: ImportJobDao
) {
    suspend fun upsert(entity: ImportJobEntity) = dao.upsert(entity)

    suspend fun get(jobId: String) = dao.get(jobId)

    fun observe(jobId: String): Flow<ImportJobEntity?> = dao.observe(jobId)

    suspend fun updateProgress(
        jobId: String,
        status: ImportStatus,
        total: Int,
        done: Int,
        currentTitle: String?,
        errorMessage: String?,
        now: Long
    ) = dao.updateProgress(
        jobId = jobId,
        status = status,
        total = total,
        done = done,
        currentTitle = currentTitle,
        errorMessage = errorMessage,
        updatedAt = now
    )
}
