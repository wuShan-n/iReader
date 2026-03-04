package com.ireader.engines.txt.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTxtAnnotationProviderTest {

    @Test
    fun `create and list should delegate to store`() = runBlocking {
        val store = FakeAnnotationStore()
        val provider = StoredTxtAnnotationProvider(
            documentId = DocumentId("doc-1"),
            store = store
        )
        val locator = Locator("txt.offset", "12")

        val created = provider.create(
            AnnotationDraft(
                type = AnnotationType.NOTE,
                anchor = AnnotationAnchor.ReflowRange(
                    LocatorRange(start = locator, end = locator)
                ),
                content = "hello"
            )
        )
        val listed = provider.listAll()

        assertTrue(created is ReaderResult.Ok)
        assertEquals(1, (listed as ReaderResult.Ok).value.size)
        assertEquals("doc-1", store.lastDocumentId)
    }

    @Test
    fun `decorations should map from annotation anchors`() = runBlocking {
        val store = FakeAnnotationStore()
        val provider = StoredTxtAnnotationProvider(DocumentId("doc-2"), store)
        val reflowLocator = Locator("txt.offset", "1")
        val pageLocator = Locator("pdf.page", "3")
        store.seed(
            Annotation(
                id = AnnotationId("a"),
                type = AnnotationType.HIGHLIGHT,
                anchor = AnnotationAnchor.ReflowRange(
                    LocatorRange(start = reflowLocator, end = reflowLocator)
                ),
                content = "x",
                createdAtEpochMs = 1L
            ),
            Annotation(
                id = AnnotationId("b"),
                type = AnnotationType.NOTE,
                anchor = AnnotationAnchor.FixedRects(page = pageLocator, rects = emptyList()),
                content = "y",
                createdAtEpochMs = 2L
            )
        )

        val decorations = provider.decorationsFor(AnnotationQuery())

        val mapped = (decorations as ReaderResult.Ok).value
        assertTrue(mapped.any { it is Decoration.Reflow })
        assertTrue(mapped.any { it is Decoration.Fixed })
    }
}

private class FakeAnnotationStore : AnnotationStore {
    private val state = MutableStateFlow<List<Annotation>>(emptyList())
    private var nextId = 1
    var lastDocumentId: String? = null
        private set

    override fun observe(documentId: DocumentId): Flow<List<Annotation>> {
        lastDocumentId = documentId.value
        return state
    }

    override suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>> {
        lastDocumentId = documentId.value
        return ReaderResult.Ok(state.value)
    }

    override suspend fun query(documentId: DocumentId, query: AnnotationQuery): ReaderResult<List<Annotation>> {
        lastDocumentId = documentId.value
        return ReaderResult.Ok(state.value)
    }

    override suspend fun create(documentId: DocumentId, draft: AnnotationDraft): ReaderResult<Annotation> {
        lastDocumentId = documentId.value
        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId("id-${nextId++}"),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        state.value = state.value + created
        return ReaderResult.Ok(created)
    }

    override suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit> {
        lastDocumentId = documentId.value
        state.value = state.value.map { current ->
            if (current.id == annotation.id) annotation else current
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit> {
        lastDocumentId = documentId.value
        state.value = state.value.filterNot { it.id == id }
        return ReaderResult.Ok(Unit)
    }

    fun seed(vararg annotations: Annotation) {
        state.value = annotations.toList()
    }
}
