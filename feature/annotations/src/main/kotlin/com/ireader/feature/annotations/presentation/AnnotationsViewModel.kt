package com.ireader.feature.annotations.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.navigation.AppRoutes
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnnotationItemUi(
    val id: String,
    val content: String,
    val typeLabel: String,
    val locatorEncoded: String,
    val updatedAtEpochMs: Long
)

data class AnnotationsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val documentId: String? = null,
    val draftContent: String = "",
    val editingId: String? = null,
    val editingContent: String = "",
    val items: List<AnnotationItemUi> = emptyList()
)

@HiltViewModel
class AnnotationsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepo: BookRepo,
    private val progressRepo: ProgressRepo,
    private val annotationStore: AnnotationStore,
    private val locatorCodec: LocatorCodec
) : ViewModel() {

    private val bookId: Long = savedStateHandle.get<Long>(AppRoutes.ARG_BOOK_ID) ?: -1L
    private val stateStore = MutableStateFlow(AnnotationsUiState())
    val uiState: StateFlow<AnnotationsUiState> = stateStore.asStateFlow()

    private var resolvedDocumentId: DocumentId? = null
    private var observeJob: Job? = null
    private var annotationsById: Map<String, Annotation> = emptyMap()

    init {
        viewModelScope.launch {
            loadDocument()
        }
    }

    fun onDraftContentChange(value: String) {
        stateStore.update { it.copy(draftContent = value) }
    }

    fun onStartEdit(id: String) {
        val annotation = annotationsById[id] ?: return
        stateStore.update {
            it.copy(
                editingId = id,
                editingContent = annotation.content.orEmpty()
            )
        }
    }

    fun onEditingContentChange(value: String) {
        stateStore.update { it.copy(editingContent = value) }
    }

    fun onCancelEdit() {
        stateStore.update { it.copy(editingId = null, editingContent = "") }
    }

    fun onDismissError() {
        stateStore.update { it.copy(errorMessage = null) }
    }

    fun createAnnotation() {
        viewModelScope.launch {
            val docId = resolvedDocumentId ?: return@launch
            val content = uiState.value.draftContent.trim()
            if (content.isBlank()) {
                stateStore.update { it.copy(errorMessage = "请输入笔记内容") }
                return@launch
            }

            val fallbackLocator = resolveFallbackLocator()
            if (fallbackLocator == null) {
                stateStore.update { it.copy(errorMessage = "缺少阅读定位，无法创建笔记") }
                return@launch
            }

            val draft = AnnotationDraft(
                type = AnnotationType.NOTE,
                anchor = fallbackLocator.toAnchor(),
                content = content
            )
            when (annotationStore.create(docId, draft)) {
                is ReaderResult.Ok -> {
                    stateStore.update { it.copy(draftContent = "", errorMessage = null) }
                }

                is ReaderResult.Err -> {
                    stateStore.update { it.copy(errorMessage = "创建笔记失败") }
                }
            }
        }
    }

    fun saveEditing() {
        viewModelScope.launch {
            val docId = resolvedDocumentId ?: return@launch
            val editingId = uiState.value.editingId ?: return@launch
            val original = annotationsById[editingId] ?: return@launch
            val nextContent = uiState.value.editingContent.trim()
            if (nextContent.isBlank()) {
                stateStore.update { it.copy(errorMessage = "笔记内容不能为空") }
                return@launch
            }

            when (annotationStore.update(docId, original.copy(content = nextContent))) {
                is ReaderResult.Ok -> {
                    stateStore.update {
                        it.copy(
                            editingId = null,
                            editingContent = "",
                            errorMessage = null
                        )
                    }
                }

                is ReaderResult.Err -> {
                    stateStore.update { it.copy(errorMessage = "更新笔记失败") }
                }
            }
        }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            val docId = resolvedDocumentId ?: return@launch
            when (annotationStore.delete(docId, AnnotationId(id))) {
                is ReaderResult.Ok -> {
                    stateStore.update {
                        if (it.editingId == id) {
                            it.copy(editingId = null, editingContent = "", errorMessage = null)
                        } else {
                            it.copy(errorMessage = null)
                        }
                    }
                }

                is ReaderResult.Err -> {
                    stateStore.update { it.copy(errorMessage = "删除笔记失败") }
                }
            }
        }
    }

    private suspend fun loadDocument() {
        if (bookId <= 0L) {
            stateStore.update { it.copy(isLoading = false, errorMessage = "无效书籍参数") }
            return
        }

        val record = bookRepo.getRecordById(bookId)
        val documentIdValue = record?.documentId?.takeIf { it.isNotBlank() }
        if (documentIdValue == null) {
            stateStore.update { it.copy(isLoading = false, errorMessage = "该书籍缺少文档标识，无法加载笔记") }
            return
        }

        val documentId = DocumentId(documentIdValue)
        resolvedDocumentId = documentId
        stateStore.update { it.copy(isLoading = false, documentId = documentIdValue, errorMessage = null) }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            annotationStore.observe(documentId).collect { annotations ->
                annotationsById = annotations.associateBy { it.id.value }
                stateStore.update {
                    it.copy(
                        items = annotations.map { annotation -> annotation.toUiItem() },
                        errorMessage = null
                    )
                }
            }
        }
    }

    private suspend fun resolveFallbackLocator(): Locator? {
        val progress = progressRepo.getByBookId(bookId) ?: return null
        return locatorCodec.decode(progress.locatorJson)
    }

    private fun Locator.toAnchor(): AnnotationAnchor {
        return if (scheme == LocatorSchemes.PDF_PAGE) {
            AnnotationAnchor.FixedRects(page = this, rects = emptyList())
        } else {
            AnnotationAnchor.ReflowRange(LocatorRange(start = this, end = this))
        }
    }

    private fun Annotation.toUiItem(): AnnotationItemUi {
        val locator = when (val anchor = anchor) {
            is AnnotationAnchor.ReflowRange -> anchor.range.start
            is AnnotationAnchor.FixedRects -> anchor.page
        }
        return AnnotationItemUi(
            id = id.value,
            content = content.orEmpty().ifBlank { "(无文本内容)" },
            typeLabel = when (type) {
                AnnotationType.HIGHLIGHT -> "高亮"
                AnnotationType.UNDERLINE -> "下划线"
                AnnotationType.NOTE -> "笔记"
                AnnotationType.BOOKMARK -> "书签"
            },
            locatorEncoded = locatorCodec.encode(locator),
            updatedAtEpochMs = updatedAtEpochMs
        )
    }
}
