package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import kotlinx.coroutines.flow.Flow

/**
 * App-level annotation persistence abstraction.
 *
 * Implemented in data layer (Room/sync). Engines consume this contract.
 */
interface AnnotationStore {
    fun observe(documentId: DocumentId): Flow<List<Annotation>>

    suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>>

    suspend fun query(documentId: DocumentId, query: AnnotationQuery): ReaderResult<List<Annotation>>

    suspend fun create(documentId: DocumentId, draft: AnnotationDraft): ReaderResult<Annotation>

    suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit>

    suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit>
}
