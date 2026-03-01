package com.ireader.engines.epub.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class EpubAnnotationProvider : AnnotationProvider {

    private val state = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observeAll(): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        val items = state.value
        if (query.range == null && query.page == null) {
            return ReaderResult.Ok(items)
        }

        val filtered = items.filter { annotation ->
            when (val anchor = annotation.anchor) {
                is AnnotationAnchor.ReflowRange -> query.range != null
                is AnnotationAnchor.FixedRects -> query.page != null && query.page == anchor.page
            }
        }
        return ReaderResult.Ok(filtered)
    }

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val now = System.currentTimeMillis()
        val annotation = Annotation(
            id = AnnotationId(UUID.randomUUID().toString()),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        state.value = state.value + annotation
        return ReaderResult.Ok(annotation)
    }

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> {
        val current = state.value.toMutableList()
        val index = current.indexOfFirst { it.id == annotation.id }
        if (index < 0) {
            return ReaderResult.Err(ReaderError.NotFound("Annotation not found"))
        }
        current[index] = annotation.copy(updatedAtEpochMs = System.currentTimeMillis())
        state.value = current
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        state.value = state.value.filterNot { it.id == id }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val decorations = state.value.mapNotNull { annotation ->
            when (val anchor = annotation.anchor) {
                is AnnotationAnchor.ReflowRange -> Decoration.Reflow(
                    range = anchor.range,
                    style = annotation.style
                )

                is AnnotationAnchor.FixedRects -> null
            }
        }
        return ReaderResult.Ok(decorations)
    }
}
