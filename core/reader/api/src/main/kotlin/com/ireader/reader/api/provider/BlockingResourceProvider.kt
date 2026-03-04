package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import java.io.InputStream

/**
 * Optional fast-path for callers that must resolve resources synchronously,
 * e.g. WebView intercept callbacks.
 */
interface BlockingResourceProvider : ResourceProvider {
    fun openResourceBlocking(path: String): ReaderResult<InputStream>
    fun getMimeTypeBlocking(path: String): ReaderResult<String?>
}
