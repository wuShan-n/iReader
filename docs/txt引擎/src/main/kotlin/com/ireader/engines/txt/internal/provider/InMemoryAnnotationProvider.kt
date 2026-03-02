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

    private data class IndexedAnnotation(
        val start: Int,
        val end: Int,
        val annotation: Annotation
    )

    private val lock = Any()
    private val state = MutableStateFlow<List<Annotation>>(emptyList())
    @Volatile
    private var index: List<IndexedAnnotation> = emptyList()

    override fun observeAll(): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun listAll(): ReaderResult<List<Annotation>> = synchronized(lock) {
        ReaderResult.Ok(state.value)
    }

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        val all = synchronized(lock) { state.value }
        val range = query.range ?: return ReaderResult.Ok(all)

        val queryStart = parseOffset(range.start) ?: return ReaderResult.Ok(emptyList())
        val queryEnd = parseOffset(range.end) ?: return ReaderResult.Ok(emptyList())
        val from = minOf(queryStart, queryEnd)
        val to = maxOf(queryStart, queryEnd)
        if (from >= to) return ReaderResult.Ok(emptyList())

        val indexed = index
        if (indexed.isEmpty()) return ReaderResult.Ok(emptyList())

        var cursor = lowerBoundStart(indexed, from)
        while (cursor > 0 && indexed[cursor - 1].end > from) {
            cursor -= 1
        }

        val result = ArrayList<Annotation>()
        while (cursor < indexed.size) {
            val candidate = indexed[cursor]
            if (candidate.start >= to) break
            if (overlaps(from, to, candidate.start, candidate.end)) {
                result.add(candidate.annotation)
            }
            cursor += 1
        }
        return ReaderResult.Ok(result)
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
        synchronized(lock) {
            val updated = state.value + created
            state.value = updated
            rebuildIndexLocked(updated)
        }
        return ReaderResult.Ok(created)
    }

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val updated = state.value.map { current ->
                if (current.id == annotation.id) {
                    annotation.copy(updatedAtEpochMs = now)
                } else {
                    current
                }
            }
            state.value = updated
            rebuildIndexLocked(updated)
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        synchronized(lock) {
            val updated = state.value.filterNot { it.id == id }
            state.value = updated
            rebuildIndexLocked(updated)
        }
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

    private fun lowerBoundStart(values: List<IndexedAnnotation>, target: Int): Int {
        var lo = 0
        var hi = values.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (values[mid].start < target) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return lo
    }

    private fun rebuildIndexLocked(annotations: List<Annotation>) {
        index = annotations.mapNotNull { annotation ->
            val anchor = annotation.anchor as? AnnotationAnchor.ReflowRange ?: return@mapNotNull null
            val start = parseOffset(anchor.range.start) ?: return@mapNotNull null
            val end = parseOffset(anchor.range.end) ?: return@mapNotNull null
            val from = minOf(start, end)
            val to = maxOf(start, end)
            if (from >= to) return@mapNotNull null
            IndexedAnnotation(start = from, end = to, annotation = annotation)
        }.sortedBy { it.start }
    }
}
