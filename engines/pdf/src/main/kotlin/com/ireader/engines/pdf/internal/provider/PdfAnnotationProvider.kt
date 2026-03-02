package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InMemoryPdfAnnotationProvider(
    private val documentId: DocumentId
) : AnnotationProvider {

    private val state = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observeAll(): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        val all = state.value
        val pageQuery = query.page
        val rangeQuery = query.range
        val filtered = when {
            pageQuery != null -> all.filter { ann -> ann.matchesPage(pageQuery) }
            rangeQuery != null -> all.filter { ann -> ann.matchesRange(rangeQuery.start, rangeQuery.end) }
            else -> all
        }
        return ReaderResult.Ok(filtered)
    }

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId("${documentId.value}:${UUID.randomUUID()}"),
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

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> {
        state.value = state.value.map {
            if (it.id == annotation.id) annotation.copy(updatedAtEpochMs = System.currentTimeMillis()) else it
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        state.value = state.value.filterNot { it.id == id }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val items = when (val q = query(query)) {
            is ReaderResult.Ok -> q.value
            is ReaderResult.Err -> return ReaderResult.Err(q.error)
        }
        return ReaderResult.Ok(
            items.mapNotNull { ann ->
                when (val anchor = ann.anchor) {
                    is AnnotationAnchor.FixedRects -> Decoration.Fixed(
                        page = anchor.page,
                        rects = anchor.rects,
                        style = ann.style
                    )

                    is AnnotationAnchor.ReflowRange -> Decoration.Reflow(
                        range = anchor.range,
                        style = ann.style
                    )
                }
            }
        )
    }

    private fun Annotation.matchesPage(page: Locator): Boolean {
        return when (val anchor = anchor) {
            is AnnotationAnchor.FixedRects -> anchor.page.samePdfPage(page)
            is AnnotationAnchor.ReflowRange -> anchor.range.start.samePdfPage(page)
        }
    }

    private fun Annotation.matchesRange(start: Locator, end: Locator): Boolean {
        return when (val anchor = anchor) {
            is AnnotationAnchor.FixedRects -> anchor.page.samePdfPage(start)
            is AnnotationAnchor.ReflowRange -> {
                anchor.range.start.samePdfPage(start) && anchor.range.end.samePdfPage(end)
            }
        }
    }

    private fun Locator.samePdfPage(other: Locator): Boolean {
        if (scheme != LocatorSchemes.PDF_PAGE || other.scheme != LocatorSchemes.PDF_PAGE) return false
        return value == other.value
    }
}

internal class EmptyPdfSelectionProvider : SelectionProvider {
    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return ReaderResult.Ok(null)
    }

    override suspend fun clearSelection(): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }
}
