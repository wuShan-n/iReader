package com.ireader.core.data.importing

import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportItemRepo @Inject constructor(
    private val dao: ImportItemDao
) {
    suspend fun upsertAll(items: List<ImportItemEntity>) = dao.upsertAll(items)

    suspend fun list(jobId: String) = dao.list(jobId)

    suspend fun listPendingOrFailed(jobId: String) = dao.listPendingOrFailed(jobId)

    suspend fun update(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: String?,
        fingerprint: String?,
        errorCode: String?,
        errorMessage: String?,
        now: Long
    ) = dao.update(
        jobId = jobId,
        uri = uri,
        status = status,
        bookId = bookId,
        fingerprint = fingerprint,
        errorCode = errorCode,
        errorMessage = errorMessage,
        updatedAt = now
    )
}
