package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorJsonCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.IndexState
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import java.io.File
import javax.inject.Inject

sealed interface OpenBookResult {
    data class Success(
        val book: BookEntity,
        val session: ReaderSessionHandle
    ) : OpenBookResult

    data class Failure(
        val message: String
    ) : OpenBookResult
}

class OpenBookUseCase @Inject constructor(
    private val bookRepo: BookRepo,
    private val progressRepo: ProgressRepo,
    private val readerRuntime: ReaderRuntime
) {
    suspend operator fun invoke(bookId: Long): OpenBookResult {
        if (bookId <= 0L) {
            return OpenBookResult.Failure("缺少书籍 ID")
        }

        val book = bookRepo.getById(bookId)
            ?: return OpenBookResult.Failure("未找到书籍记录")

        val file = File(book.canonicalPath)
        if (!file.exists()) {
            bookRepo.setIndexState(bookId = bookId, state = IndexState.MISSING, error = "文件不存在")
            return OpenBookResult.Failure("书籍文件不存在")
        }

        val source = FileDocumentSource(
            file = file,
            displayName = book.fileName,
            mimeType = book.mimeType
        )

        val progress = progressRepo.getByBookId(bookId)
        val initialLocator = progress?.locatorJson?.let { LocatorJsonCodec.decode(it) }

        return when (
            val result = readerRuntime.openSession(
                source = source,
                options = OpenOptions(hintFormat = book.format),
                initialLocator = initialLocator
            )
        ) {
            is ReaderResult.Ok -> {
                bookRepo.touchLastOpened(bookId)
                OpenBookResult.Success(book = book, session = result.value)
            }

            is ReaderResult.Err -> OpenBookResult.Failure(result.error.toUserMessage())
        }
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
        is ReaderError.Internal -> message ?: "打开书籍失败"
    }
}
