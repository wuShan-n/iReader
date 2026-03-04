package com.ireader.core.database.book

import androidx.sqlite.db.SimpleSQLiteQuery
import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.testing.inMemoryReaderDatabase
import com.ireader.core.database.testing.sampleBook
import com.ireader.core.database.testing.sampleCollection
import com.ireader.core.database.testing.sampleProgress
import com.ireader.reader.model.BookFormat
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
class BookDaoRoomTest {

    private lateinit var database: ReaderDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun setUp() {
        database = inMemoryReaderDatabase()
        bookDao = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `findByFingerprint should return most recently updated row`() = runTest {
        val fingerprint = "same-fingerprint"
        val olderId = bookDao.upsert(
            sampleBook(
                documentId = "doc-old",
                fingerprint = fingerprint,
                updatedAtEpochMs = 100L,
                canonicalPath = "/tmp/old.epub"
            )
        )
        val newerId = bookDao.upsert(
            sampleBook(
                documentId = "doc-new",
                fingerprint = fingerprint,
                updatedAtEpochMs = 200L,
                canonicalPath = "/tmp/new.epub"
            )
        )

        val found = bookDao.findByFingerprint(fingerprint)

        assertEquals(newerId, found?.bookId)
        assertTrue(found?.bookId != olderId)
    }

    @Test
    fun `observeMissing should filter missing books and sort by updated desc`() = runTest {
        val olderMissing = bookDao.upsert(
            sampleBook(
                documentId = "missing-1",
                indexState = IndexState.MISSING,
                updatedAtEpochMs = 100L,
                canonicalPath = "/tmp/missing-1.epub"
            )
        )
        val newerMissing = bookDao.upsert(
            sampleBook(
                documentId = "missing-2",
                indexState = IndexState.MISSING,
                updatedAtEpochMs = 300L,
                canonicalPath = "/tmp/missing-2.epub"
            )
        )
        bookDao.upsert(
            sampleBook(
                documentId = "indexed",
                indexState = IndexState.INDEXED,
                updatedAtEpochMs = 400L,
                canonicalPath = "/tmp/indexed.epub"
            )
        )

        val rows = bookDao.observeMissing().first()

        assertEquals(listOf(newerMissing, olderMissing), rows.map { it.bookId })
    }

    @Test
    fun `update operations should persist all target fields`() = runTest {
        val bookId = bookDao.upsert(
            sampleBook(
                documentId = "doc-update",
                canonicalPath = "/tmp/original.epub"
            )
        )

        bookDao.updateFavorite(bookId, favorite = true, updatedAt = 500L)
        bookDao.updateReadingStatus(bookId, status = ReadingStatus.FINISHED, updatedAt = 501L)
        bookDao.updateIndexState(bookId, state = IndexState.ERROR, error = "index failed", updatedAt = 502L)
        bookDao.updateLastOpened(bookId, lastOpenedAt = 700L, updatedAt = 503L)
        bookDao.updateSource(
            bookId = bookId,
            sourceUri = "content://book/new",
            canonicalPath = "/tmp/new-source.epub",
            lastModifiedEpochMs = 777L,
            updatedAt = 504L
        )
        bookDao.updateMetadata(
            bookId = bookId,
            documentId = "doc-new",
            format = BookFormat.PDF,
            title = "New title",
            author = "New author",
            language = "zh",
            identifier = "new-id",
            series = "series-1",
            description = "new description",
            coverPath = "/tmp/cover.png",
            capabilitiesJson = """{"search":true}""",
            indexState = IndexState.INDEXED,
            indexError = null,
            updatedAt = 505L
        )

        val updated = bookDao.getById(bookId)

        assertEquals(true, updated?.favorite)
        assertEquals(ReadingStatus.FINISHED, updated?.readingStatus)
        assertEquals(IndexState.INDEXED, updated?.indexState)
        assertEquals(null, updated?.indexError)
        assertEquals(700L, updated?.lastOpenedAtEpochMs)
        assertEquals("content://book/new", updated?.sourceUri)
        assertEquals("/tmp/new-source.epub", updated?.canonicalPath)
        assertEquals(777L, updated?.lastModifiedEpochMs)
        assertEquals("doc-new", updated?.documentId)
        assertEquals(BookFormat.PDF, updated?.format)
        assertEquals("New title", updated?.title)
        assertEquals("New author", updated?.author)
        assertEquals("zh", updated?.language)
        assertEquals("new-id", updated?.identifier)
        assertEquals("series-1", updated?.series)
        assertEquals("new description", updated?.description)
        assertEquals("/tmp/cover.png", updated?.coverPath)
        assertEquals("""{"search":true}""", updated?.capabilitiesJson)
        assertEquals(505L, updated?.updatedAtEpochMs)
    }

    @Test
    fun `deleteById should cascade to progress and book collection`() = runTest {
        val bookId = bookDao.upsert(
            sampleBook(
                documentId = "doc-delete",
                canonicalPath = "/tmp/delete.epub"
            )
        )
        val collectionId = database.collectionDao().upsert(sampleCollection(name = "Reading", sortOrder = 0))
        database.progressDao().upsert(sampleProgress(bookId = bookId))
        database.bookCollectionDao().insert(
            com.ireader.core.database.collection.BookCollectionEntity(
                bookId = bookId,
                collectionId = collectionId
            )
        )

        bookDao.deleteById(bookId)

        assertEquals(null, bookDao.getById(bookId))
        assertEquals(null, database.progressDao().getByBookId(bookId))
        assertTrue(database.bookCollectionDao().listCollectionIdsForBook(bookId).isEmpty())
    }

    @Test
    fun `observeLibrary should join progress values`() = runTest {
        val bookId = bookDao.upsert(
            sampleBook(
                documentId = "doc-library",
                canonicalPath = "/tmp/library.epub"
            )
        )
        database.progressDao().upsert(sampleProgress(bookId = bookId, progression = 0.67))

        val rows = bookDao.observeLibrary(
            SimpleSQLiteQuery(
                """
                SELECT books.*, progress.progression AS progression, progress.updatedAtEpochMs AS progressUpdatedAtEpochMs
                FROM books
                LEFT JOIN progress ON progress.bookId = books.bookId
                WHERE books.bookId = ?
                """.trimIndent(),
                arrayOf(bookId)
            )
        ).first()

        assertEquals(1, rows.size)
        assertEquals(bookId, rows.first().book.bookId)
        assertEquals(0.67, rows.first().progression ?: 0.0, 0.0001)
    }
}
