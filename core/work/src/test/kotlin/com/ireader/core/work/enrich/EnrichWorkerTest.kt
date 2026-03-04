package com.ireader.core.work.enrich

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ireader.core.data.book.BookIndexer
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.reader.model.BookFormat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EnrichWorkerTest {

    private lateinit var context: Context
    private lateinit var database: ReaderDatabase
    private lateinit var bookRepo: BookRepo
    private lateinit var importItemRepo: ImportItemRepo
    private lateinit var indexer: RecordingBookIndexer

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookRepo = BookRepo(
            bookDao = database.bookDao(),
            collectionDao = database.collectionDao(),
            bookCollectionDao = database.bookCollectionDao()
        )
        importItemRepo = ImportItemRepo(database.importItemDao())
        indexer = RecordingBookIndexer()
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
    }

    @Test
    fun `doWork should return success for job without succeeded books`() = runTest {
        val worker = buildWorker(jobId = "job-empty")

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(indexer.calls.isEmpty())
    }

    @Test
    fun `doWork should invoke index for each succeeded book`() = runTest {
        val jobId = "job-success"
        val firstBookId = insertBook("first.epub", BookFormat.EPUB)
        val secondBookId = insertBook("second.pdf", BookFormat.PDF)
        database.importItemDao().upsertAll(
            listOf(
                importItem(jobId = jobId, uri = "content://first", status = ImportItemStatus.SUCCEEDED, bookId = firstBookId),
                importItem(jobId = jobId, uri = "content://second", status = ImportItemStatus.SUCCEEDED, bookId = secondBookId),
                importItem(jobId = jobId, uri = "content://failed", status = ImportItemStatus.FAILED, bookId = null)
            )
        )
        val worker = buildWorker(jobId = jobId)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(setOf(firstBookId, secondBookId), indexer.calls.toSet())
    }

    @Test
    fun `doWork should continue when one index operation fails`() = runTest {
        val jobId = "job-partial"
        val firstBookId = insertBook("first.pdf", BookFormat.PDF)
        val secondBookId = insertBook("second.txt", BookFormat.TXT)
        indexer.failIds += firstBookId
        database.importItemDao().upsertAll(
            listOf(
                importItem(jobId = jobId, uri = "content://a", status = ImportItemStatus.SUCCEEDED, bookId = firstBookId),
                importItem(jobId = jobId, uri = "content://b", status = ImportItemStatus.SUCCEEDED, bookId = secondBookId)
            )
        )
        val worker = buildWorker(jobId = jobId)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(setOf(firstBookId, secondBookId), indexer.calls.toSet())
    }

    private fun buildWorker(jobId: String): EnrichWorker {
        return TestListenableWorkerBuilder<EnrichWorker>(context)
            .setInputData(EnrichWorkerInput.data(jobId))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker? {
                        if (workerClassName != EnrichWorker::class.java.name) {
                            return null
                        }
                        return EnrichWorker(
                            appContext = appContext,
                            params = workerParameters,
                            importItemRepo = importItemRepo,
                            bookRepo = bookRepo,
                            bookIndexer = indexer
                        )
                    }
                }
            )
            .build()
    }

    private suspend fun insertBook(fileName: String, format: BookFormat): Long {
        val now = System.currentTimeMillis()
        return bookRepo.upsert(
            BookEntity(
                documentId = null,
                sourceUri = "content://book/${UUID.randomUUID()}",
                sourceType = BookSourceType.IMPORTED_COPY,
                format = format,
                fileName = fileName,
                mimeType = when (format) {
                    BookFormat.EPUB -> "application/epub+zip"
                    BookFormat.PDF -> "application/pdf"
                    BookFormat.TXT -> "text/plain"
                },
                fileSizeBytes = 1024L,
                lastModifiedEpochMs = null,
                canonicalPath = "/tmp/$fileName",
                fingerprintSha256 = UUID.randomUUID().toString(),
                title = fileName.substringBeforeLast('.'),
                author = null,
                language = null,
                identifier = null,
                series = null,
                description = null,
                coverPath = null,
                favorite = false,
                readingStatus = ReadingStatus.UNREAD,
                indexState = IndexState.PENDING,
                indexError = null,
                capabilitiesJson = null,
                addedAtEpochMs = now,
                updatedAtEpochMs = now,
                lastOpenedAtEpochMs = null
            )
        )
    }

    private fun importItem(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: Long?
    ): ImportItemEntity {
        return ImportItemEntity(
            jobId = jobId,
            uri = uri,
            displayName = "book",
            mimeType = null,
            sizeBytes = null,
            status = status,
            bookId = bookId,
            fingerprintSha256 = null,
            errorCode = null,
            errorMessage = null,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}

private class RecordingBookIndexer : BookIndexer {
    val calls: MutableList<Long> = mutableListOf()
    val failIds: MutableSet<Long> = mutableSetOf()

    override suspend fun index(bookId: Long): Result<Unit> {
        calls += bookId
        return if (bookId in failIds) {
            Result.failure(IllegalStateException("forced failure"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun reindex(bookId: Long): Result<Unit> = index(bookId)
}
