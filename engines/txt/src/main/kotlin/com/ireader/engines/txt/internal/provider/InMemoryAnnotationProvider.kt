package com.ireader.engines.txt.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
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

internal class InMemoryAnnotationProvider : AnnotationProvider {

    private val state = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observeAll(): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        val all = state.value
        val range = query.range ?: return ReaderResult.Ok(all)

        val queryStart = parseOffset(range.start) ?: return ReaderResult.Ok(emptyList())
        val queryEnd = parseOffset(range.end) ?: return ReaderResult.Ok(emptyList())
        val from = minOf(queryStart, queryEnd)
        val to = maxOf(queryStart, queryEnd)

        val filtered = all.filter { annotation ->
            val anchor = annotation.anchor as? AnnotationAnchor.ReflowRange ?: return@filter false
            val anchorStart = parseOffset(anchor.range.start) ?: return@filter false
            val anchorEnd = parseOffset(anchor.range.end) ?: return@filter false
            val a = minOf(anchorStart, anchorEnd)
            val b = maxOf(anchorStart, anchorEnd)
            overlaps(from, to, a, b)
        }
        return ReaderResult.Ok(filtered)
    }

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val anchor = draft.anchor as? AnnotationAnchor.ReflowRange
            ?: return ReaderResult.Err(
                ReaderError.Internal("TXT only supports reflow range annotations")
            )
        if (!isTxtOffset(anchor.range.start) || !isTxtOffset(anchor.range.end)) {
            return ReaderResult.Err(ReaderError.Internal("Invalid txt.offset anchor"))
        }

        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId(UUID.randomUUID().toString()),
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
        val now = System.currentTimeMillis()
        state.value = state.value.map { current ->
            if (current.id == annotation.id) {
                annotation.copy(updatedAtEpochMs = now)
            } else {
                current
            }
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        state.value = state.value.filterNot { it.id == id }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val result = query(query)
        return when (result) {
            is ReaderResult.Err -> ReaderResult.Err(result.error)
            is ReaderResult.Ok -> {
                val decorations = result.value.mapNotNull { annotation ->
                    val anchor = annotation.anchor as? AnnotationAnchor.ReflowRange ?: return@mapNotNull null
                    Decoration.Reflow(
                        range = anchor.range,
                        style = annotation.style
                    )
                }
                ReaderResult.Ok(decorations)
            }
        }
    }

    private fun parseOffset(locator: Locator): Int? {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return null
        return locator.value.toIntOrNull()
    }

    private fun isTxtOffset(locator: Locator): Boolean = locator.scheme == LocatorSchemes.TXT_OFFSET

    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        return aStart < bEnd && bStart < aEnd
    }
}
