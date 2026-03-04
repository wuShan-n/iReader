package com.ireader.core.data.importing

import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportReposTest {

    @Test
    fun `ImportJobRepo updateProgress should delegate with exact parameters`() = runTest {
        val dao = FakeImportJobDao().apply {
            byId["job-1"] = sampleJob("job-1")
        }
        val repo = ImportJobRepo(dao)

        repo.updateProgress(
            jobId = "job-1",
            status = ImportStatus.RUNNING,
            total = 9,
            done = 3,
            currentTitle = "chapter",
            errorMessage = null,
            now = 600L
        )

        assertEquals(ImportStatus.RUNNING, dao.byId["job-1"]?.status)
        assertEquals(9, dao.byId["job-1"]?.total)
        assertEquals(3, dao.byId["job-1"]?.done)
        assertEquals("chapter", dao.byId["job-1"]?.currentTitle)
        assertEquals(600L, dao.byId["job-1"]?.updatedAtEpochMs)
    }

    @Test
    fun `ImportJobRepo observe should expose dao flow`() = runTest {
        val dao = FakeImportJobDao()
        val repo = ImportJobRepo(dao)
        dao.emit(sampleJob("job-observe", status = ImportStatus.SUCCEEDED))

        val entity = repo.observe("job-observe").first()

        assertEquals("job-observe", entity?.jobId)
        assertEquals(ImportStatus.SUCCEEDED, entity?.status)
    }

    @Test
    fun `ImportItemRepo update should delegate all mutable fields`() = runTest {
        val dao = FakeImportItemDao().apply {
            upsertAll(
                listOf(
                    sampleItem(
                        jobId = "job-2",
                        uri = "content://book/2",
                        status = ImportItemStatus.PENDING
                    )
                )
            )
        }
        val repo = ImportItemRepo(dao)

        repo.update(
            jobId = "job-2",
            uri = "content://book/2",
            status = ImportItemStatus.FAILED,
            bookId = 100L,
            fingerprint = "fp",
            errorCode = "IO",
            errorMessage = "failed",
            now = 900L
        )

        val item = dao.list("job-2").single()
        assertEquals(ImportItemStatus.FAILED, item.status)
        assertEquals(100L, item.bookId)
        assertEquals("fp", item.fingerprintSha256)
        assertEquals("IO", item.errorCode)
        assertEquals("failed", item.errorMessage)
        assertEquals(900L, item.updatedAtEpochMs)
    }

    @Test
    fun `ImportItemRepo listSucceededBookIds should return dao values`() = runTest {
        val dao = FakeImportItemDao().apply {
            upsertAll(
                listOf(
                    sampleItem("job-3", "a", ImportItemStatus.SUCCEEDED, bookId = 1L),
                    sampleItem("job-3", "b", ImportItemStatus.SUCCEEDED, bookId = 2L),
                    sampleItem("job-3", "c", ImportItemStatus.FAILED, bookId = 9L)
                )
            )
        }
        val repo = ImportItemRepo(dao)

        val ids = repo.listSucceededBookIds("job-3")

        assertEquals(listOf(1L, 2L), ids)
    }
}

private class FakeImportJobDao : ImportJobDao {
    val byId: MutableMap<String, ImportJobEntity> = mutableMapOf()
    private val flows: MutableMap<String, MutableStateFlow<ImportJobEntity?>> = mutableMapOf()

    fun emit(entity: ImportJobEntity) {
        byId[entity.jobId] = entity
        flows.getOrPut(entity.jobId) { MutableStateFlow(null) }.value = entity
    }

    override suspend fun upsert(entity: ImportJobEntity) {
        emit(entity)
    }

    override suspend fun get(jobId: String): ImportJobEntity? = byId[jobId]

    override fun observe(jobId: String): Flow<ImportJobEntity?> {
        return flows.getOrPut(jobId) { MutableStateFlow(byId[jobId]) }
    }

    override suspend fun updateProgress(
        jobId: String,
        status: ImportStatus,
        total: Int,
        done: Int,
        currentTitle: String?,
        errorMessage: String?,
        updatedAt: Long
    ) {
        val current = byId[jobId] ?: return
        emit(
            current.copy(
                status = status,
                total = total,
                done = done,
                currentTitle = currentTitle,
                errorMessage = errorMessage,
                updatedAtEpochMs = updatedAt
            )
        )
    }
}

private class FakeImportItemDao : ImportItemDao {
    private val rows: MutableMap<String, MutableMap<String, ImportItemEntity>> = mutableMapOf()

    override suspend fun upsertAll(items: List<ImportItemEntity>) {
        items.forEach { item ->
            rows.getOrPut(item.jobId) { mutableMapOf() }[item.uri] = item
        }
    }

    override suspend fun list(jobId: String): List<ImportItemEntity> {
        return rows[jobId]?.values?.sortedBy { it.uri } ?: emptyList()
    }

    override suspend fun listPendingOrFailed(jobId: String): List<ImportItemEntity> {
        return rows[jobId]
            ?.values
            ?.filter { it.status == ImportItemStatus.PENDING || it.status == ImportItemStatus.FAILED }
            ?.sortedBy { it.updatedAtEpochMs }
            ?: emptyList()
    }

    override suspend fun update(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: Long?,
        fingerprint: String?,
        errorCode: String?,
        errorMessage: String?,
        updatedAt: Long
    ) {
        val current = rows[jobId]?.get(uri) ?: return
        rows[jobId]?.set(
            uri,
            current.copy(
                status = status,
                bookId = bookId,
                fingerprintSha256 = fingerprint,
                errorCode = errorCode,
                errorMessage = errorMessage,
                updatedAtEpochMs = updatedAt
            )
        )
    }

    override suspend fun deleteByJob(jobId: String) {
        rows.remove(jobId)
    }

    override suspend fun listSucceededBookIds(jobId: String): List<Long> {
        return rows[jobId]
            ?.values
            ?.filter { it.status == ImportItemStatus.SUCCEEDED }
            ?.mapNotNull { it.bookId }
            ?: emptyList()
    }
}

private fun sampleJob(jobId: String, status: ImportStatus = ImportStatus.QUEUED): ImportJobEntity {
    return ImportJobEntity(
        jobId = jobId,
        status = status,
        total = 0,
        done = 0,
        currentTitle = null,
        errorMessage = null,
        sourceTreeUri = null,
        duplicateStrategy = "SKIP",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L
    )
}

private fun sampleItem(
    jobId: String,
    uri: String,
    status: ImportItemStatus,
    bookId: Long? = null
): ImportItemEntity {
    return ImportItemEntity(
        jobId = jobId,
        uri = uri,
        displayName = null,
        mimeType = null,
        sizeBytes = null,
        status = status,
        bookId = bookId,
        fingerprintSha256 = null,
        errorCode = null,
        errorMessage = null,
        updatedAtEpochMs = 1L
    )
}
