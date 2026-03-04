package com.ireader.feature.annotations.presentation

import androidx.lifecycle.SavedStateHandle
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.LibraryBookRow
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import com.ireader.core.navigation.AppRoutes
import com.ireader.core.testing.MainDispatcherRule
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `create annotation should append item using progress locator fallback`() = runTest {
        val locatorCodec = FakeLocatorCodec()
        val store = FakeAnnotationStore()
        val vm = newViewModel(
            locatorCodec = locatorCodec,
            annotationStore = store,
            bookEntity = sampleBook(bookId = 7L, documentId = "doc-7"),
            progressEntity = ProgressEntity(
                bookId = 7L,
                locatorJson = locatorCodec.encode(Locator(LocatorSchemes.TXT_OFFSET, "128")),
                progression = 0.1,
                updatedAtEpochMs = 1L
            )
        )
        advanceUntilIdle()

        vm.onDraftContentChange("first note")
        vm.createAnnotation()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("first note", state.items.first().content)
    }

    @Test
    fun `save and delete should update list state`() = runTest {
        val locatorCodec = FakeLocatorCodec()
        val store = FakeAnnotationStore()
        val locator = Locator(LocatorSchemes.TXT_OFFSET, "16")
        store.seed(
            documentId = DocumentId("doc-edit"),
            annotations = listOf(
                Annotation(
                    id = AnnotationId("ann-1"),
                    type = AnnotationType.NOTE,
                    anchor = AnnotationAnchor.ReflowRange(LocatorRange(locator, locator)),
                    content = "before",
                    createdAtEpochMs = 10L
                )
            )
        )
        val vm = newViewModel(
            locatorCodec = locatorCodec,
            annotationStore = store,
            bookEntity = sampleBook(bookId = 9L, documentId = "doc-edit"),
            progressEntity = ProgressEntity(
                bookId = 9L,
                locatorJson = locatorCodec.encode(locator),
                progression = 0.5,
                updatedAtEpochMs = 20L
            )
        )
        advanceUntilIdle()

        vm.onStartEdit("ann-1")
        vm.onEditingContentChange("after")
        vm.saveEditing()
        advanceUntilIdle()
        assertEquals("after", vm.uiState.value.items.first().content)

        vm.deleteAnnotation("ann-1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun `missing document id should expose load error`() = runTest {
        val vm = newViewModel(
            locatorCodec = FakeLocatorCodec(),
            annotationStore = FakeAnnotationStore(),
            bookEntity = sampleBook(bookId = 11L, documentId = null),
            progressEntity = null
        )
        advanceUntilIdle()

        val error = vm.uiState.value.errorMessage.orEmpty()
        assertTrue(error.contains("缺少文档标识"))
    }

    private fun newViewModel(
        locatorCodec: LocatorCodec,
        annotationStore: FakeAnnotationStore,
        bookEntity: BookEntity,
        progressEntity: ProgressEntity?
    ): AnnotationsViewModel {
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(mapOf(bookEntity.bookId to bookEntity)),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val progressRepo = ProgressRepo(fakeProgressDao(mapOf(bookEntity.bookId to progressEntity)))
        return AnnotationsViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(AppRoutes.ARG_BOOK_ID to bookEntity.bookId)
            ),
            bookRepo = bookRepo,
            progressRepo = progressRepo,
            annotationStore = annotationStore,
            locatorCodec = locatorCodec
        )
    }

    private fun sampleBook(bookId: Long, documentId: String?): BookEntity {
        return BookEntity(
            bookId = bookId,
            documentId = documentId,
            sourceUri = "file:///tmp/book-$bookId.txt",
            sourceType = BookSourceType.FILE_PATH,
            format = BookFormat.TXT,
            fileName = "book-$bookId.txt",
            mimeType = "text/plain",
            fileSizeBytes = 100L,
            lastModifiedEpochMs = null,
            canonicalPath = "/tmp/book-$bookId.txt",
            fingerprintSha256 = "fp-$bookId",
            title = "Book $bookId",
            author = "Author",
            language = "zh",
            identifier = null,
            series = null,
            description = null,
            coverPath = null,
            favorite = false,
            readingStatus = ReadingStatus.UNREAD,
            indexState = IndexState.INDEXED,
            indexError = null,
            capabilitiesJson = null,
            addedAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            lastOpenedAtEpochMs = null
        )
    }

    private fun fakeBookDao(bookById: Map<Long, BookEntity>): BookDao {
        return proxyInterface(BookDao::class.java) { method, args ->
            when (method.name) {
                "getById" -> bookById[args?.get(0) as Long]
                "observeById" -> flowOf(bookById[args?.get(0) as Long])
                "observeMissing" -> flowOf(emptyList<BookEntity>())
                "observeLibrary" -> flowOf(emptyList<LibraryBookRow>())
                "listAll" -> bookById.values.toList()
                "findByFingerprint", "getByDocumentId" -> null
                "upsert" -> 1L
                "updateIndexState",
                "updateLastOpened",
                "updateFavorite",
                "updateReadingStatus",
                "updateSource",
                "updateMetadata",
                "deleteById" -> Unit
                else -> error("Unexpected BookDao call: ${method.name}")
            }
        }
    }

    private fun fakeCollectionDao(): CollectionDao {
        return proxyInterface(CollectionDao::class.java) { method, _ ->
            when (method.name) {
                "observeAll" -> flowOf(emptyList<CollectionEntity>())
                "upsert" -> 1L
                "getById", "getByName" -> null
                "deleteById" -> Unit
                else -> error("Unexpected CollectionDao call: ${method.name}")
            }
        }
    }

    private fun fakeBookCollectionDao(): BookCollectionDao {
        return proxyInterface(BookCollectionDao::class.java) { method, _ ->
            when (method.name) {
                "insert" -> 1L
                "delete", "deleteAllForBook" -> Unit
                "listCollectionIdsForBook" -> emptyList<Long>()
                else -> error("Unexpected BookCollectionDao call: ${method.name}")
            }
        }
    }

    private fun fakeProgressDao(progressByBookId: Map<Long, ProgressEntity?>): ProgressDao {
        return proxyInterface(ProgressDao::class.java) { method, args ->
            when (method.name) {
                "getByBookId" -> progressByBookId[args?.get(0) as Long]
                "observeByBookId" -> flowOf(progressByBookId[args?.get(0) as Long])
                "upsert", "deleteByBookId" -> Unit
                else -> error("Unexpected ProgressDao call: ${method.name}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxyInterface(
        clazz: Class<T>,
        handler: (method: java.lang.reflect.Method, args: Array<Any?>?) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz)
        ) { _, method, args ->
            handler(method, args)
        } as T
    }
}

private class FakeAnnotationStore : AnnotationStore {
    private val states = mutableMapOf<String, MutableStateFlow<List<Annotation>>>()
    private var idCounter = 1

    override fun observe(documentId: DocumentId): Flow<List<Annotation>> = flow(documentId)

    override suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>> =
        ReaderResult.Ok(flow(documentId).value)

    override suspend fun query(documentId: DocumentId, query: AnnotationQuery): ReaderResult<List<Annotation>> =
        ReaderResult.Ok(flow(documentId).value)

    override suspend fun create(documentId: DocumentId, draft: AnnotationDraft): ReaderResult<Annotation> {
        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId("a-${idCounter++}"),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        flow(documentId).value = flow(documentId).value + created
        return ReaderResult.Ok(created)
    }

    override suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit> {
        val next = flow(documentId).value.map { current ->
            if (current.id == annotation.id) annotation.copy(updatedAtEpochMs = System.currentTimeMillis()) else current
        }
        flow(documentId).value = next
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit> {
        flow(documentId).value = flow(documentId).value.filterNot { it.id == id }
        return ReaderResult.Ok(Unit)
    }

    fun seed(documentId: DocumentId, annotations: List<Annotation>) {
        flow(documentId).value = annotations
    }

    private fun flow(documentId: DocumentId): MutableStateFlow<List<Annotation>> {
        return states.getOrPut(documentId.value) { MutableStateFlow(emptyList()) }
    }
}

private class FakeLocatorCodec : LocatorCodec {
    override fun encode(locator: Locator): String = "${locator.scheme}|${locator.value}"

    override fun decode(raw: String): Locator? {
        val separator = raw.indexOf('|')
        if (separator <= 0 || separator >= raw.lastIndex) return null
        return Locator(
            scheme = raw.substring(0, separator),
            value = raw.substring(separator + 1)
        )
    }
}
