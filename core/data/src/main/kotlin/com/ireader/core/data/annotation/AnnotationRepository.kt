package com.ireader.core.data.annotation

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AnnotationListItem(
    val id: String,
    val content: String,
    val typeLabel: String,
    val locatorEncoded: String,
    val updatedAtEpochMs: Long
)

sealed interface AnnotationDocumentLookup {
    data object InvalidBookId : AnnotationDocumentLookup
    data object BookNotFound : AnnotationDocumentLookup
    data object MissingDocumentId : AnnotationDocumentLookup
    data class Success(val documentId: String) : AnnotationDocumentLookup
}

enum class AnnotationMutationFailure {
    MISSING_PROGRESS,
    UNSUPPORTED_PROGRESS_LOCATOR,
    MISSING_ANNOTATION,
    CREATE_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED
}

sealed interface AnnotationMutationResult {
    data object Success : AnnotationMutationResult
    data class Failure(val reason: AnnotationMutationFailure) : AnnotationMutationResult
}

@Singleton
class AnnotationRepository @Inject constructor(
    private val bookRepo: BookRepo,
    private val progressRepo: ProgressRepo,
    private val annotationStore: AnnotationStore,
    private val locatorCodec: LocatorCodec
) {
    suspend fun resolveDocument(bookId: Long): AnnotationDocumentLookup {
        if (bookId <= 0L) {
            return AnnotationDocumentLookup.InvalidBookId
        }
        val record = bookRepo.getRecordById(bookId) ?: return AnnotationDocumentLookup.BookNotFound
        val documentId = record.documentId?.takeIf { it.isNotBlank() }
            ?: return AnnotationDocumentLookup.MissingDocumentId
        return AnnotationDocumentLookup.Success(documentId)
    }

    fun observe(documentId: String): Flow<List<AnnotationListItem>> {
        return annotationStore.observe(DocumentId(documentId))
            .map { annotations -> annotations.map(::toListItem) }
    }

    suspend fun createNote(documentId: String, bookId: Long, content: String): AnnotationMutationResult {
        val fallbackLocator = resolveFallbackLocator(bookId)
            ?: return AnnotationMutationResult.Failure(AnnotationMutationFailure.MISSING_PROGRESS)
        val anchor = fallbackLocator.toAnchorOrNull()
            ?: return AnnotationMutationResult.Failure(AnnotationMutationFailure.UNSUPPORTED_PROGRESS_LOCATOR)
        return when (
            annotationStore.create(
                documentId = DocumentId(documentId),
                draft = AnnotationDraft(
                    type = AnnotationType.NOTE,
                    anchor = anchor,
                    content = content
                )
            )
        ) {
            is ReaderResult.Ok -> AnnotationMutationResult.Success
            is ReaderResult.Err -> AnnotationMutationResult.Failure(AnnotationMutationFailure.CREATE_FAILED)
        }
    }

    suspend fun updateContent(
        documentId: String,
        annotationId: String,
        content: String
    ): AnnotationMutationResult {
        val target = when (val result = annotationStore.list(DocumentId(documentId))) {
            is ReaderResult.Ok -> {
                result.value.firstOrNull { annotation -> annotation.id.value == annotationId }
                    ?: return AnnotationMutationResult.Failure(AnnotationMutationFailure.MISSING_ANNOTATION)
            }

            is ReaderResult.Err -> {
                return AnnotationMutationResult.Failure(AnnotationMutationFailure.UPDATE_FAILED)
            }
        }
        return when (
            annotationStore.update(
                documentId = DocumentId(documentId),
                annotation = target.copy(content = content)
            )
        ) {
            is ReaderResult.Ok -> AnnotationMutationResult.Success
            is ReaderResult.Err -> AnnotationMutationResult.Failure(AnnotationMutationFailure.UPDATE_FAILED)
        }
    }

    suspend fun delete(documentId: String, annotationId: String): AnnotationMutationResult {
        return when (
            annotationStore.delete(
                documentId = DocumentId(documentId),
                id = AnnotationId(annotationId)
            )
        ) {
            is ReaderResult.Ok -> AnnotationMutationResult.Success
            is ReaderResult.Err -> AnnotationMutationResult.Failure(AnnotationMutationFailure.DELETE_FAILED)
        }
    }

    private suspend fun resolveFallbackLocator(bookId: Long): Locator? {
        val progress = progressRepo.getByBookId(bookId) ?: return null
        return locatorCodec.decode(progress.locatorJson)
    }

    private fun toListItem(annotation: Annotation): AnnotationListItem {
        val locator = when (val anchor = annotation.anchor) {
            is AnnotationAnchor.ReflowRange -> anchor.range.start
            is AnnotationAnchor.FixedRects -> anchor.page
        }
        return AnnotationListItem(
            id = annotation.id.value,
            content = annotation.content.orEmpty().ifBlank { "(无文本内容)" },
            typeLabel = when (annotation.type) {
                AnnotationType.HIGHLIGHT -> "高亮"
                AnnotationType.UNDERLINE -> "下划线"
                AnnotationType.NOTE -> "笔记"
                AnnotationType.BOOKMARK -> "书签"
            },
            locatorEncoded = locatorCodec.encode(locator),
            updatedAtEpochMs = annotation.updatedAtEpochMs
        )
    }

    private fun Locator.toAnchorOrNull(): AnnotationAnchor? {
        return if (scheme == LocatorSchemes.PDF_PAGE) {
            AnnotationAnchor.FixedRects(page = this, rects = emptyList())
        } else if (scheme == LocatorSchemes.EPUB_CFI ||
            scheme == LocatorSchemes.REFLOW_PAGE ||
            scheme == LocatorSchemes.TXT_STABLE_ANCHOR
        ) {
            AnnotationAnchor.ReflowRange(LocatorRange(start = this, end = this))
        } else {
            null
        }
    }
}
