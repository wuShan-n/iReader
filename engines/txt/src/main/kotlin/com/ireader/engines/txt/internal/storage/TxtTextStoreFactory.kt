package com.ireader.engines.txt.internal.storage

import com.ireader.reader.source.DocumentSource
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineDispatcher

internal object TxtTextStoreFactory {

    private const val IN_MEMORY_THRESHOLD_BYTES: Long = 8L * 1024L * 1024L

    suspend fun create(
        source: DocumentSource,
        charset: Charset,
        ioDispatcher: CoroutineDispatcher
    ): TxtTextStore {
        val size = source.sizeBytes
        if (size != null && size <= IN_MEMORY_THRESHOLD_BYTES) {
            return InMemoryTxtTextStore(
                source = source,
                charset = charset,
                ioDispatcher = ioDispatcher,
                maxBytes = IN_MEMORY_THRESHOLD_BYTES
            )
        }

        val pfd = source.openFileDescriptor("r")
        if (pfd != null) {
            return IndexedTxtTextStore(
                source = source,
                pfd = pfd,
                charset = charset,
                ioDispatcher = ioDispatcher
            )
        }

        // 大文件且不可 seek 直接失败，避免高风险 OOM。
        if (size != null && size > IN_MEMORY_THRESHOLD_BYTES) {
            throw IllegalStateException(
                "TXT too large and source is not seekable: $size bytes"
            )
        }

        return InMemoryTxtTextStore(
            source = source,
            charset = charset,
            ioDispatcher = ioDispatcher,
            maxBytes = IN_MEMORY_THRESHOLD_BYTES
        )
    }
}
