package com.ireader.core.files.importing

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.permission.UriPermissionGateway
import com.ireader.core.files.permission.UriPermissionResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.core.files.source.UriDocumentSourceFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultImportManagerTest {

    @Test
    fun `enqueue should reject empty request`() = runTest {
        val fixture = newFixture()

        val error = runCatching { fixture.manager.enqueue(ImportRequest()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `enqueue should fail when persistable read cannot be granted`() = runTest {
        val fixture = newFixture()
        val uri = Uri.parse("content://books/1")
        fixture.permissionStore.results[uri.toString()] = UriPermissionResult(
            granted = false,
            code = "PERMISSION_DENIED",
            message = "denied"
        )

        val error = runCatching {
            fixture.manager.enqueue(ImportRequest(uris = listOf(uri)))
        }.exceptionOrNull()

        assertTrue(error is SecurityException)
        assertTrue(error?.message?.contains("denied") == true)
    }

    @Test
    fun `enqueue should persist job and items then schedule work`() = runTest {
        val fixture = newFixture()
        val first = Uri.parse("content://books/1")
        val second = Uri.parse("content://books/2")
        fixture.sourceFactory.sources[first.toString()] = FakeDocumentSource(
            uri = first,
            displayName = "a.epub",
            mimeType = "application/epub+zip",
            sizeBytes = 11L
        )
        fixture.sourceFactory.sources[second.toString()] = FakeDocumentSource(
            uri = second,
            displayName = "b.pdf",
            mimeType = "application/pdf",
            sizeBytes = 22L
        )

        val jobId = fixture.manager.enqueue(ImportRequest(uris = listOf(first, second)))

        assertTrue(jobId.isNotBlank())
        assertEquals(listOf(jobId), fixture.scheduler.enqueuedIds)
        assertEquals(1, fixture.jobDao.upserts.size)
        assertEquals(jobId, fixture.jobDao.upserts.single().jobId)
        assertEquals(2, fixture.itemDao.upserted.flatten().size)
        assertEquals("a.epub", fixture.itemDao.upserted.flatten().first().displayName)
        assertEquals("application/pdf", fixture.itemDao.upserted.flatten().last().mimeType)
    }

    @Test
    fun `enqueue with tree uri only should create zero-total job without items`() = runTest {
        val fixture = newFixture()
        val treeUri = Uri.parse("content://tree/books")

        val jobId = fixture.manager.enqueue(
            ImportRequest(
                uris = emptyList(),
                treeUri = treeUri
            )
        )

        val job = fixture.jobDao.byId[jobId]
        assertNotNull(job)
        assertEquals(0, job?.total)
        assertEquals(treeUri.toString(), job?.sourceTreeUri)
        assertTrue(fixture.itemDao.upserted.isEmpty())
    }

    @Test
    fun `observe should map repo entity to import job state`() = runTest {
        val fixture = newFixture()
        val jobId = "job-observe"
        fixture.jobDao.emit(
            ImportJobEntity(
                jobId = jobId,
                status = ImportStatus.RUNNING,
                total = 10,
                done = 3,
                currentTitle = "chapter 1",
                errorMessage = null,
                sourceTreeUri = null,
                duplicateStrategy = DuplicateStrategy.SKIP.name,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )

        val state = fixture.manager.observe(jobId).first()

        assertEquals(jobId, state.jobId)
        assertEquals(ImportJobStatus.RUNNING, state.status)
        assertEquals(10, state.total)
        assertEquals(3, state.done)
        assertEquals("chapter 1", state.currentTitle)
    }

    @Test
    fun `cancel should request scheduler and mark existing job cancelled`() = runTest {
        val fixture = newFixture()
        val jobId = "job-cancel"
        fixture.jobDao.emit(
            ImportJobEntity(
                jobId = jobId,
                status = ImportStatus.RUNNING,
                total = 5,
                done = 2,
                currentTitle = null,
                errorMessage = null,
                sourceTreeUri = null,
                duplicateStrategy = DuplicateStrategy.SKIP.name,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )

        fixture.manager.cancel(jobId)

        assertEquals(listOf(jobId), fixture.scheduler.cancelledIds)
        assertEquals(ImportStatus.CANCELLED, fixture.jobDao.byId[jobId]?.status)
    }

    @Test
    fun `cancel should still ask scheduler when job does not exist`() = runTest {
        val fixture = newFixture()

        fixture.manager.cancel("missing")

        assertEquals(listOf("missing"), fixture.scheduler.cancelledIds)
        assertTrue(fixture.jobDao.byId.isEmpty())
    }

    private fun newFixture(): ImportManagerFixture {
        val jobDao = FakeImportJobDao()
        val itemDao = FakeImportItemDao()
        val permissionStore = FakePermissionStore()
        val sourceFactory = FakeSourceFactory()
        val scheduler = FakeScheduler()
        val manager = DefaultImportManager(
            jobRepo = ImportJobRepo(jobDao),
            itemRepo = ImportItemRepo(itemDao),
            permissionStore = permissionStore,
            sourceFactory = sourceFactory,
            workScheduler = scheduler
        )
        return ImportManagerFixture(
            manager = manager,
            jobDao = jobDao,
            itemDao = itemDao,
            permissionStore = permissionStore,
            sourceFactory = sourceFactory,
            scheduler = scheduler
        )
    }

    private data class ImportManagerFixture(
        val manager: DefaultImportManager,
        val jobDao: FakeImportJobDao,
        val itemDao: FakeImportItemDao,
        val permissionStore: FakePermissionStore,
        val sourceFactory: FakeSourceFactory,
        val scheduler: FakeScheduler
    )
}

private class FakePermissionStore : UriPermissionGateway {
    val results: MutableMap<String, UriPermissionResult> = mutableMapOf()

    override fun takePersistableRead(uri: Uri): UriPermissionResult {
        return results[uri.toString()] ?: UriPermissionResult(granted = true)
    }

    override fun hasPersistedRead(uri: Uri): Boolean {
        return false
    }
}

private class FakeSourceFactory : UriDocumentSourceFactory {
    val sources: MutableMap<String, DocumentSource> = mutableMapOf()

    override fun create(uri: Uri): DocumentSource {
        return sources[uri.toString()] ?: FakeDocumentSource(uri = uri)
    }
}

private class FakeDocumentSource(
    override val uri: Uri,
    override val displayName: String? = null,
    override val mimeType: String? = null,
    override val sizeBytes: Long? = null
) : DocumentSource {
    override suspend fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
}

private class FakeScheduler : ImportWorkScheduler {
    val enqueuedIds: MutableList<String> = mutableListOf()
    val cancelledIds: MutableList<String> = mutableListOf()

    override fun enqueue(jobId: String) {
        enqueuedIds += jobId
    }

    override suspend fun cancel(jobId: String) {
        cancelledIds += jobId
    }
}

private class FakeImportJobDao : ImportJobDao {
    val byId: MutableMap<String, ImportJobEntity> = mutableMapOf()
    val upserts: MutableList<ImportJobEntity> = mutableListOf()
    private val flows: MutableMap<String, MutableStateFlow<ImportJobEntity?>> = mutableMapOf()

    fun emit(entity: ImportJobEntity) {
        byId[entity.jobId] = entity
        flows.getOrPut(entity.jobId) { MutableStateFlow(null) }.value = entity
    }

    override suspend fun upsert(entity: ImportJobEntity) {
        upserts += entity
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
        val existing = byId[jobId] ?: return
        emit(
            existing.copy(
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
    val upserted: MutableList<List<ImportItemEntity>> = mutableListOf()

    override suspend fun upsertAll(items: List<ImportItemEntity>) {
        upserted += items
    }

    override suspend fun list(jobId: String): List<ImportItemEntity> = emptyList()

    override suspend fun listPendingOrFailed(jobId: String): List<ImportItemEntity> = emptyList()

    override suspend fun update(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: Long?,
        fingerprint: String?,
        errorCode: String?,
        errorMessage: String?,
        updatedAt: Long
    ) = Unit

    override suspend fun deleteByJob(jobId: String) = Unit

    override suspend fun listSucceededBookIds(jobId: String): List<Long> = emptyList()
}
