package com.ireader.feature.reader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.feature.reader.domain.usecase.OpenBookResult
import com.ireader.feature.reader.domain.usecase.OpenBookUseCase
import com.ireader.feature.reader.web.ReaderWebViewLinkRouter
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.runtime.ReaderSessionHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val openBookUseCase: OpenBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: String? = null
    private var sessionHandle: ReaderSessionHandle? = null
    private var layoutConstraints: LayoutConstraints? = null
    private var loadJob: Job? = null

    fun loadBook(bookId: String) {
        if (bookId.isBlank()) {
            _uiState.value = ReaderUiState.Error("缺少书籍 ID")
            return
        }
        if (currentBookId == bookId && sessionHandle != null) {
            return
        }

        currentBookId = bookId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            closeSessionHandle()
            when (val result = openBookUseCase(bookId)) {
                is OpenBookResult.Failure -> {
                    _uiState.value = ReaderUiState.Error(result.message)
                }

                is OpenBookResult.Success -> {
                    sessionHandle = result.session
                    renderCurrentPage()
                }
            }
        }
    }

    fun onViewportChanged(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        fontScale: Float
    ) {
        if (widthPx <= 0 || heightPx <= 0) return

        val newConstraints = LayoutConstraints(
            viewportWidthPx = widthPx,
            viewportHeightPx = heightPx,
            density = density,
            fontScale = fontScale
        )
        if (newConstraints == layoutConstraints) {
            return
        }
        layoutConstraints = newConstraints

        viewModelScope.launch {
            renderCurrentPage()
        }
    }

    fun onWebSchemeUrl(url: String): Boolean {
        val handle = sessionHandle ?: return false
        val handled = ReaderWebViewLinkRouter.tryHandle(
            url = url,
            controller = handle.controller,
            scope = viewModelScope
        )
        if (handled) {
            viewModelScope.launch {
                delay(40L)
                renderCurrentPage()
            }
        }
        return handled
    }

    private suspend fun renderCurrentPage() {
        val handle = sessionHandle ?: return
        val constraints = layoutConstraints ?: return

        when (val layoutResult = handle.controller.setLayoutConstraints(constraints)) {
            is ReaderResult.Err -> {
                _uiState.value = ReaderUiState.Error(layoutResult.error.toUserMessage())
                return
            }

            is ReaderResult.Ok -> Unit
        }

        when (val renderResult = handle.controller.render()) {
            is ReaderResult.Err -> {
                _uiState.value = ReaderUiState.Error(renderResult.error.toUserMessage())
            }

            is ReaderResult.Ok -> {
                _uiState.value = pageToUiState(renderResult.value)
            }
        }
    }

    private fun pageToUiState(page: RenderPage): ReaderUiState {
        return when (val content = page.content) {
            is RenderContent.Html -> ReaderUiState.Html(
                pageId = page.id.value,
                inlineHtml = content.inlineHtml,
                contentUrl = content.contentUri?.toString(),
                baseUrl = content.baseUri?.toString()
            )

            is RenderContent.Text -> ReaderUiState.Text(
                text = content.text.toString()
            )

            is RenderContent.BitmapPage -> ReaderUiState.BitmapPage(
                bitmap = content.bitmap
            )

            is RenderContent.Tiles -> ReaderUiState.Unsupported(
                message = "当前版本暂不支持 PDF 页面渲染 UI"
            )
        }
    }

    override fun onCleared() {
        closeSessionHandle()
        super.onCleared()
    }

    private fun closeSessionHandle() {
        runCatching { sessionHandle?.close() }
        sessionHandle = null
    }
}

private fun ReaderError.toUserMessage(): String {
    return when (this) {
        is ReaderError.NotFound -> "文件不存在"
        is ReaderError.PermissionDenied -> "没有文件访问权限"
        is ReaderError.UnsupportedFormat -> "当前格式暂不支持打开"
        is ReaderError.InvalidPassword -> "文档密码错误"
        is ReaderError.CorruptOrInvalid -> "文档损坏或格式无效"
        is ReaderError.DrmRestricted -> "文档受 DRM 限制"
        is ReaderError.Io -> "读取文件失败"
        is ReaderError.Cancelled -> "打开已取消"
        is ReaderError.Internal -> message ?: "阅读器内部错误"
    }
}
