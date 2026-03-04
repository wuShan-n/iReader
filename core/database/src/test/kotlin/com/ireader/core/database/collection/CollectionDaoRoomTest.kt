package com.ireader.core.database.collection

import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.testing.inMemoryReaderDatabase
import com.ireader.core.database.testing.sampleBook
import com.ireader.core.database.testing.sampleCollection
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
class CollectionDaoRoomTest {

    private lateinit var database: ReaderDatabase
    private lateinit var collectionDao: CollectionDao
    private lateinit var bookCollectionDao: BookCollectionDao

    @Before
    fun setUp() {
        database = inMemoryReaderDatabase()
        collectionDao = database.collectionDao()
        bookCollectionDao = database.bookCollectionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeAll should sort by sortOrder then case-insensitive name`() = runTest {
        collectionDao.upsert(sampleCollection(name = "zeta", sortOrder = 1))
        collectionDao.upsert(sampleCollection(name = "beta", sortOrder = 0))
        collectionDao.upsert(sampleCollection(name = "Alpha", sortOrder = 0))

        val names = collectionDao.observeAll().first().map { it.name }

        assertEquals(listOf("Alpha", "beta", "zeta"), names)
    }

    @Test
    fun `book collection insert should ignore duplicates and list ids`() = runTest {
        val bookId = database.bookDao().upsert(sampleBook(documentId = "doc-collection", canonicalPath = "/tmp/collection.epub"))
        val collectionId = collectionDao.upsert(sampleCollection(name = "Favorites", sortOrder = 0))

        val first = bookCollectionDao.insert(BookCollectionEntity(bookId = bookId, collectionId = collectionId))
        val second = bookCollectionDao.insert(BookCollectionEntity(bookId = bookId, collectionId = collectionId))
        val ids = bookCollectionDao.listCollectionIdsForBook(bookId)

        assertEquals(1L, first)
        assertEquals(-1L, second)
        assertEquals(listOf(collectionId), ids)
    }

    @Test
    fun `deleteAllForBook should clear relations for target book only`() = runTest {
        val firstBookId = database.bookDao().upsert(sampleBook(documentId = "doc-first", canonicalPath = "/tmp/first.epub"))
        val secondBookId = database.bookDao().upsert(sampleBook(documentId = "doc-second", canonicalPath = "/tmp/second.epub"))
        val collectionId = collectionDao.upsert(sampleCollection(name = "Tag", sortOrder = 0))
        bookCollectionDao.insert(BookCollectionEntity(bookId = firstBookId, collectionId = collectionId))
        bookCollectionDao.insert(BookCollectionEntity(bookId = secondBookId, collectionId = collectionId))

        bookCollectionDao.deleteAllForBook(firstBookId)

        assertTrue(bookCollectionDao.listCollectionIdsForBook(firstBookId).isEmpty())
        assertEquals(listOf(collectionId), bookCollectionDao.listCollectionIdsForBook(secondBookId))
    }

    @Test
    fun `deleteById should remove collection`() = runTest {
        val collectionId = collectionDao.upsert(sampleCollection(name = "Temp", sortOrder = 0))

        collectionDao.deleteById(collectionId)

        assertEquals(null, collectionDao.getById(collectionId))
        assertEquals(null, collectionDao.getByName("Temp"))
    }
}
