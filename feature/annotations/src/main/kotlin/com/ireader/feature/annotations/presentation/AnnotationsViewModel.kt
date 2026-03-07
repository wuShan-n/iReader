package com.ireader.feature.annotations.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.annotation.AnnotationDocumentLookup
import com.ireader.core.data.annotation.AnnotationListItem
import com.ireader.core.data.annotation.AnnotationMutationFailure
import com.ireader.core.data.annotation.AnnotationMutationResult
import com.ireader.core.data.annotation.AnnotationRepository
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.navigation.AppRoutes
import com.ireader.reader.api.provider.AnnotationStore
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
    private val annotationRepository: AnnotationRepository
) : ViewModel() {
    constructor(
        savedStateHandle: SavedStateHandle,
        bookRepo: BookRepo,
        progressRepo: ProgressRepo,
        annotationStore: AnnotationStore,
        locatorCodec: LocatorCodec
    ) : this(
        savedStateHandle = savedStateHandle,
        annotationRepository = AnnotationRepository(
            bookRepo = bookRepo,
            progressRepo = progressRepo,
            annotationStore = annotationStore,
            locatorCodec = locatorCodec
        )
    )

    private val bookId: Long = savedStateHandle.get<Long>(AppRoutes.ARG_BOOK_ID) ?: -1L
    private val stateStore = MutableStateFlow(AnnotationsUiState())
    val uiState: StateFlow<AnnotationsUiState> = stateStore.asStateFlow()

    private var resolvedDocumentId: String? = null
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            loadDocument()
        }
    }

    fun onDraftContentChange(value: String) {
        stateStore.update { it.copy(draftContent = value) }
    }

    fun onStartEdit(id: String) {
        val annotation = uiState.value.items.firstOrNull { it.id == id } ?: return
        stateStore.update {
            it.copy(
                editingId = id,
                editingContent = annotation.content
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
            val documentId = resolvedDocumentId ?: return@launch
            val content = uiState.value.draftContent.trim()
            if (content.isBlank()) {
                stateStore.update { it.copy(errorMessage = "请输入笔记内容") }
                return@launch
            }

            when (val result = annotationRepository.createNote(documentId, bookId, content)) {
                AnnotationMutationResult.Success -> {
                    stateStore.update { it.copy(draftContent = "", errorMessage = null) }
                }

                is AnnotationMutationResult.Failure -> {
                    stateStore.update { it.copy(errorMessage = createErrorMessage(result.reason)) }
                }
            }
        }
    }

    fun saveEditing() {
        viewModelScope.launch {
            val documentId = resolvedDocumentId ?: return@launch
            val editingId = uiState.value.editingId ?: return@launch
            val nextContent = uiState.value.editingContent.trim()
            if (nextContent.isBlank()) {
                stateStore.update { it.copy(errorMessage = "笔记内容不能为空") }
                return@launch
            }

            when (val result = annotationRepository.updateContent(documentId, editingId, nextContent)) {
                AnnotationMutationResult.Success -> {
                    stateStore.update {
                        it.copy(
                            editingId = null,
                            editingContent = "",
                            errorMessage = null
                        )
                    }
                }

                is AnnotationMutationResult.Failure -> {
                    stateStore.update { it.copy(errorMessage = updateErrorMessage(result.reason)) }
                }
            }
        }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            val documentId = resolvedDocumentId ?: return@launch
            when (val result = annotationRepository.delete(documentId, id)) {
                AnnotationMutationResult.Success -> {
                    stateStore.update {
                        if (it.editingId == id) {
                            it.copy(editingId = null, editingContent = "", errorMessage = null)
                        } else {
                            it.copy(errorMessage = null)
                        }
                    }
                }

                is AnnotationMutationResult.Failure -> {
                    stateStore.update { it.copy(errorMessage = deleteErrorMessage(result.reason)) }
                }
            }
        }
    }

    private suspend fun loadDocument() {
        when (val result = annotationRepository.resolveDocument(bookId)) {
            AnnotationDocumentLookup.InvalidBookId -> {
                stateStore.update {
                    it.copy(isLoading = false, errorMessage = "无效书籍参数")
                }
            }

            AnnotationDocumentLookup.BookNotFound -> {
                stateStore.update {
                    it.copy(isLoading = false, errorMessage = "书籍不存在，无法加载笔记")
                }
            }

            AnnotationDocumentLookup.MissingDocumentId -> {
                stateStore.update {
                    it.copy(isLoading = false, errorMessage = "该书籍缺少文档标识，无法加载笔记")
                }
            }

            is AnnotationDocumentLookup.Success -> {
                resolvedDocumentId = result.documentId
                stateStore.update {
                    it.copy(isLoading = false, documentId = result.documentId, errorMessage = null)
                }
                observeJob?.cancel()
                observeJob = viewModelScope.launch {
                    annotationRepository.observe(result.documentId).collect { items ->
                        stateStore.update {
                            it.copy(items = items.map { item -> item.toUiItem() }, errorMessage = null)
                        }
                    }
                }
            }
        }
    }

    private fun createErrorMessage(reason: AnnotationMutationFailure): String {
        return when (reason) {
            AnnotationMutationFailure.MISSING_PROGRESS -> "缺少阅读定位，无法创建笔记"
            AnnotationMutationFailure.LEGACY_TXT_LOCATOR -> "旧版 TXT 定位已失效，请先重新打开书籍后再创建笔记"
            AnnotationMutationFailure.MISSING_ANNOTATION,
            AnnotationMutationFailure.UPDATE_FAILED,
            AnnotationMutationFailure.DELETE_FAILED,
            AnnotationMutationFailure.CREATE_FAILED -> "创建笔记失败"
        }
    }

    private fun updateErrorMessage(reason: AnnotationMutationFailure): String {
        return when (reason) {
            AnnotationMutationFailure.MISSING_ANNOTATION -> "笔记不存在或已删除"
            else -> "更新笔记失败"
        }
    }

    private fun deleteErrorMessage(reason: AnnotationMutationFailure): String {
        return when (reason) {
            AnnotationMutationFailure.MISSING_ANNOTATION -> "笔记不存在或已删除"
            else -> "删除笔记失败"
        }
    }

    private fun AnnotationListItem.toUiItem(): AnnotationItemUi {
        return AnnotationItemUi(
            id = id,
            content = content,
            typeLabel = typeLabel,
            locatorEncoded = locatorEncoded,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }
}
