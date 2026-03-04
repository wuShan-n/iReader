package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationProviderTest {

    @Test
    fun `in-memory provider page query returns matching annotations`() = runTest {
        val provider = InMemoryPdfAnnotationProvider(documentId = DocumentId("pdf:test"))
        val pageLocator = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "3")

        provider.create(
            AnnotationDraft(
                type = AnnotationType.HIGHLIGHT,
                anchor = AnnotationAnchor.FixedRects(
                    page = pageLocator,
                    rects = listOf(NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
                )
            )
        )
        provider.create(
            AnnotationDraft(
                type = AnnotationType.HIGHLIGHT,
                anchor = AnnotationAnchor.FixedRects(
                    page = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "4"),
                    rects = listOf(NormalizedRect(0.2f, 0.2f, 0.3f, 0.3f))
                )
            )
        )

        val result = provider.query(AnnotationQuery(page = pageLocator))
        val list = (result as ReaderResult.Ok).value

        assertEquals(1, list.size)
        val anchor = list.first().anchor as AnnotationAnchor.FixedRects
        assertEquals("3", anchor.page.value)
    }

    @Test
    fun `stored provider should delegate query to store`() = runTest {
        val store = FakeStore()
        val provider = StoredPdfAnnotationProvider(
            documentId = DocumentId("pdf:test"),
            store = store
        )

        val result = provider.query(
            AnnotationQuery(
                range = LocatorRange(
                    start = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "1"),
                    end = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "1")
                )
            )
        )

        assertTrue(result is ReaderResult.Ok)
        assertEquals(1, store.queryCalls)
    }

    private class FakeStore : AnnotationStore {
        var queryCalls: Int = 0

        override fun observe(documentId: DocumentId): Flow<List<Annotation>> = flowOf(emptyList())

        override suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>> =
            ReaderResult.Ok(emptyList())

        override suspend fun query(documentId: DocumentId, query: AnnotationQuery): ReaderResult<List<Annotation>> {
            queryCalls++
            return ReaderResult.Ok(emptyList())
        }

        override suspend fun create(documentId: DocumentId, draft: AnnotationDraft): ReaderResult<Annotation> {
            val now = System.currentTimeMillis()
            return ReaderResult.Ok(
                Annotation(
                    id = AnnotationId("a"),
                    type = draft.type,
                    anchor = draft.anchor,
                    content = draft.content,
                    style = draft.style,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )
        }

        override suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit> =
            ReaderResult.Ok(Unit)

        override suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit> =
            ReaderResult.Ok(Unit)
    }
}
