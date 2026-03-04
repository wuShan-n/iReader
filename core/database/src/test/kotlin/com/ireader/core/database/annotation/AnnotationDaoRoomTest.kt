package com.ireader.core.database.annotation

import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.testing.inMemoryReaderDatabase
import com.ireader.core.database.testing.sampleAnnotation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnnotationDaoRoomTest {

    private lateinit var database: ReaderDatabase
    private lateinit var dao: AnnotationDao

    @Before
    fun setUp() {
        database = inMemoryReaderDatabase()
        dao = database.annotationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `listByDocumentId should sort by updatedAt descending`() = runTest {
        val documentId = "doc-1"
        dao.upsert(sampleAnnotation(id = "a", documentId = documentId, updatedAtEpochMs = 100L))
        dao.upsert(sampleAnnotation(id = "b", documentId = documentId, updatedAtEpochMs = 300L))
        dao.upsert(sampleAnnotation(id = "c", documentId = documentId, updatedAtEpochMs = 200L))

        val list = dao.listByDocumentId(documentId)

        assertEquals(listOf("b", "c", "a"), list.map { it.id })
    }

    @Test
    fun `listByDocumentIdAndAnchorType should filter anchor type`() = runTest {
        val documentId = "doc-anchor"
        dao.upsert(
            sampleAnnotation(
                id = "fixed",
                documentId = documentId,
                anchorType = AnnotationAnchorType.FIXED_RECTS,
                updatedAtEpochMs = 100L
            )
        )
        dao.upsert(
            sampleAnnotation(
                id = "reflow",
                documentId = documentId,
                anchorType = AnnotationAnchorType.REFLOW_RANGE,
                updatedAtEpochMs = 200L
            )
        )

        val fixed = dao.listByDocumentIdAndAnchorType(documentId, AnnotationAnchorType.FIXED_RECTS)
        val reflow = dao.listByDocumentIdAndAnchorType(documentId, AnnotationAnchorType.REFLOW_RANGE)

        assertEquals(listOf("fixed"), fixed.map { it.id })
        assertEquals(listOf("reflow"), reflow.map { it.id })
    }

    @Test
    fun `observeByDocumentId should emit existing rows`() = runTest {
        val documentId = "doc-observe"
        dao.upsert(sampleAnnotation(id = "one", documentId = documentId, updatedAtEpochMs = 1L))
        dao.upsert(sampleAnnotation(id = "two", documentId = documentId, updatedAtEpochMs = 2L))

        val rows = dao.observeByDocumentId(documentId).first()

        assertEquals(2, rows.size)
        assertEquals(listOf("two", "one"), rows.map { it.id })
    }

    @Test
    fun `exists and deleteById should track row existence`() = runTest {
        val documentId = "doc-delete"
        val id = "ann-1"
        dao.upsert(sampleAnnotation(id = id, documentId = documentId))
        assertTrue(dao.exists(documentId, id))

        val deletedRows = dao.deleteById(documentId, id)

        assertEquals(1, deletedRows)
        assertFalse(dao.exists(documentId, id))
    }
}
