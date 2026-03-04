package com.ireader.core.files.importing

import android.net.Uri
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.permission.UriPermissionGateway
import com.ireader.core.files.source.UriDocumentSourceFactory
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultImportManager @Inject constructor(
    private val jobRepo: ImportJobRepo,
    private val itemRepo: ImportItemRepo,
    private val permissionStore: UriPermissionGateway,
    private val sourceFactory: UriDocumentSourceFactory,
    private val workScheduler: ImportWorkScheduler
) : ImportManager {

    override suspend fun enqueue(request: ImportRequest): String = withContext(Dispatchers.IO) {
        require(request.uris.isNotEmpty() || request.treeUri != null) {
            "ImportRequest must contain uris or treeUri"
        }

        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()

        request.treeUri?.let(::requirePersistedRead)
        request.uris.forEach(::requirePersistedRead)

        val items = request.uris.map { uri ->
            val source = sourceFactory.create(uri)
            ImportItemEntity(
                jobId = jobId,
                uri = uri.toString(),
                displayName = source.displayName,
                mimeType = source.mimeType,
                sizeBytes = source.sizeBytes,
                status = ImportItemStatus.PENDING,
                bookId = null,
                fingerprintSha256 = null,
                errorCode = null,
                errorMessage = null,
                updatedAtEpochMs = now
            )
        }

        val job = ImportJobEntity(
            jobId = jobId,
            status = ImportStatus.QUEUED,
            total = items.size,
            done = 0,
            currentTitle = null,
            errorMessage = null,
            sourceTreeUri = request.treeUri?.toString(),
            duplicateStrategy = request.duplicateStrategy.name,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        jobRepo.upsert(job)
        if (items.isNotEmpty()) {
            itemRepo.upsertAll(items)
        }
        workScheduler.enqueue(jobId)

        return@withContext jobId
    }

    override fun observe(jobId: String): Flow<ImportJobState> {
        return jobRepo.observe(jobId)
            .filterNotNull()
            .map { entity ->
                ImportJobState(
                    jobId = entity.jobId,
                    status = entity.status.toImportJobStatus(),
                    total = entity.total,
                    done = entity.done,
                    currentTitle = entity.currentTitle,
                    errorMessage = entity.errorMessage
                )
            }
    }

    override suspend fun cancel(jobId: String) {
        workScheduler.cancel(jobId)
        val now = System.currentTimeMillis()
        val current = jobRepo.get(jobId) ?: return
        jobRepo.upsert(
            current.copy(
                status = ImportStatus.CANCELLED,
                updatedAtEpochMs = now
            )
        )
    }

    private fun requirePersistedRead(uri: Uri) {
        val result = permissionStore.takePersistableRead(uri)
        if (!result.granted) {
            val message = "Cannot persist read permission for $uri: ${result.message ?: result.code.orEmpty()}"
            throw SecurityException(message)
        }
    }

    private fun ImportStatus.toImportJobStatus(): ImportJobStatus =
        when (this) {
            ImportStatus.QUEUED -> ImportJobStatus.QUEUED
            ImportStatus.RUNNING -> ImportJobStatus.RUNNING
            ImportStatus.SUCCEEDED -> ImportJobStatus.SUCCEEDED
            ImportStatus.FAILED -> ImportJobStatus.FAILED
            ImportStatus.CANCELLED -> ImportJobStatus.CANCELLED
        }
}
