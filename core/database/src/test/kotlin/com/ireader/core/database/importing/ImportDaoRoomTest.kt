package com.ireader.core.database.importing

import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.testing.inMemoryReaderDatabase
import com.ireader.core.database.testing.sampleImportItem
import com.ireader.core.database.testing.sampleImportJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportDaoRoomTest {

    private lateinit var database: ReaderDatabase
    private lateinit var jobDao: ImportJobDao
    private lateinit var itemDao: ImportItemDao

    @Before
    fun setUp() {
        database = inMemoryReaderDatabase()
        jobDao = database.importJobDao()
        itemDao = database.importItemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `listPendingOrFailed should filter and sort ascending by updatedAt`() = runTest {
        val jobId = "job-42"
        itemDao.upsertAll(
            listOf(
                sampleImportItem(jobId = jobId, uri = "content://books/1", status = ImportItemStatus.RUNNING, updatedAtEpochMs = 200L),
                sampleImportItem(jobId = jobId, uri = "content://books/2", status = ImportItemStatus.FAILED, updatedAtEpochMs = 150L),
                sampleImportItem(jobId = jobId, uri = "content://books/3", status = ImportItemStatus.PENDING, updatedAtEpochMs = 100L),
                sampleImportItem(jobId = jobId, uri = "content://books/4", status = ImportItemStatus.SUCCEEDED, updatedAtEpochMs = 50L)
            )
        )

        val rows = itemDao.listPendingOrFailed(jobId)

        assertEquals(listOf("content://books/3", "content://books/2"), rows.map { it.uri })
    }

    @Test
    fun `update should persist all mutable fields`() = runTest {
        val jobId = "job-update"
        val uri = "content://book/1"
        itemDao.upsertAll(
            listOf(
                sampleImportItem(
                    jobId = jobId,
                    uri = uri,
                    status = ImportItemStatus.PENDING,
                    updatedAtEpochMs = 1L
                )
            )
        )

        itemDao.update(
            jobId = jobId,
            uri = uri,
            status = ImportItemStatus.FAILED,
            bookId = 9L,
            fingerprint = "fp-updated",
            errorCode = "IO",
            errorMessage = "copy failed",
            updatedAt = 500L
        )

        val updated = itemDao.list(jobId).single()
        assertEquals(ImportItemStatus.FAILED, updated.status)
        assertEquals(9L, updated.bookId)
        assertEquals("fp-updated", updated.fingerprintSha256)
        assertEquals("IO", updated.errorCode)
        assertEquals("copy failed", updated.errorMessage)
        assertEquals(500L, updated.updatedAtEpochMs)
    }

    @Test
    fun `listSucceededBookIds should return distinct non-null ids only`() = runTest {
        val jobId = "job-succeeded"
        itemDao.upsertAll(
            listOf(
                sampleImportItem(jobId = jobId, uri = "a", status = ImportItemStatus.SUCCEEDED, bookId = 1L, updatedAtEpochMs = 1L),
                sampleImportItem(jobId = jobId, uri = "b", status = ImportItemStatus.SUCCEEDED, bookId = 1L, updatedAtEpochMs = 2L),
                sampleImportItem(jobId = jobId, uri = "c", status = ImportItemStatus.SUCCEEDED, bookId = null, updatedAtEpochMs = 3L),
                sampleImportItem(jobId = jobId, uri = "d", status = ImportItemStatus.FAILED, bookId = 2L, updatedAtEpochMs = 4L)
            )
        )

        val ids = itemDao.listSucceededBookIds(jobId)

        assertEquals(listOf(1L), ids)
    }

    @Test
    fun `updateProgress should update job and observe should emit latest value`() = runTest {
        val jobId = "job-progress"
        jobDao.upsert(sampleImportJob(jobId = jobId, status = ImportStatus.QUEUED, total = 5, done = 0))

        jobDao.updateProgress(
            jobId = jobId,
            status = ImportStatus.RUNNING,
            total = 5,
            done = 3,
            currentTitle = "chapter 3",
            errorMessage = null,
            updatedAt = 300L
        )

        val current = jobDao.get(jobId)
        val observed = jobDao.observe(jobId).first()
        assertEquals(ImportStatus.RUNNING, current?.status)
        assertEquals(3, current?.done)
        assertEquals("chapter 3", current?.currentTitle)
        assertEquals(300L, current?.updatedAtEpochMs)
        assertEquals(current, observed)
    }

    @Test
    fun `deleteByJob should remove all job items`() = runTest {
        val jobId = "job-clean"
        itemDao.upsertAll(
            listOf(
                sampleImportItem(jobId = jobId, uri = "x", status = ImportItemStatus.PENDING, updatedAtEpochMs = 1L),
                sampleImportItem(jobId = jobId, uri = "y", status = ImportItemStatus.FAILED, updatedAtEpochMs = 2L)
            )
        )

        itemDao.deleteByJob(jobId)

        assertTrue(itemDao.list(jobId).isEmpty())
    }
}
