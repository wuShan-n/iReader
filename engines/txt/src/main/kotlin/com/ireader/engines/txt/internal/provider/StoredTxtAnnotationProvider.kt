package com.ireader.engines.txt.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import kotlinx.coroutines.flow.Flow

internal class StoredTxtAnnotationProvider(
    private val documentId: DocumentId,
    private val store: AnnotationStore
) : AnnotationProvider {

    override fun observeAll(): Flow<List<Annotation>> = store.observe(documentId)

    override suspend fun listAll(): ReaderResult<List<Annotation>> = store.list(documentId)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> =
        store.query(documentId, query)

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> =
        store.create(documentId, draft)

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> =
        store.update(documentId, annotation)

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> =
        store.delete(documentId, id)

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val annotations = when (val result = store.query(documentId, query)) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> return ReaderResult.Err(result.error)
        }
        return ReaderResult.Ok(
            annotations.map { annotation ->
                when (val anchor = annotation.anchor) {
                    is AnnotationAnchor.ReflowRange -> Decoration.Reflow(
                        range = anchor.range,
                        style = annotation.style
                    )

                    is AnnotationAnchor.FixedRects -> Decoration.Fixed(
                        page = anchor.page,
                        rects = anchor.rects,
                        style = annotation.style
                    )
                }
            }
        )
    }
}
