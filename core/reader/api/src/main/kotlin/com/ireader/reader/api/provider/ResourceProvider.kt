package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import java.io.InputStream

/**
 * 资源访问：当 EPUB 不直接暴露 file:// 路径时，
 * 你可以用 WebViewAssetLoader / 自建 ContentProvider 走这层读取。
 */
interface ResourceProvider {
    suspend fun openResource(path: String): ReaderResult<InputStream>
    suspend fun getMimeType(path: String): ReaderResult<String?>
}

