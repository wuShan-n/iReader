package com.ireader.engines.txt.internal.storage

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.TxtEngineConfig
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineDispatcher

internal object TxtTextStoreFactory {

    suspend fun create(
        source: DocumentSource,
        charset: Charset,
        ioDispatcher: CoroutineDispatcher,
        config: TxtEngineConfig
    ): TxtTextStore {
        val inMemoryThreshold = config.inMemoryThresholdBytes.coerceAtLeast(1L)
        val size = source.sizeBytes
        if (size != null && size <= inMemoryThreshold) {
            return InMemoryTxtTextStore(
                source = source,
                charset = charset,
                ioDispatcher = ioDispatcher,
                maxBytes = inMemoryThreshold
            )
        }

        val pfd = source.openFileDescriptor("r")
        if (pfd != null) {
            val anchorEveryBytes = when {
                size == null -> 1L * 1024L * 1024L
                size <= 32L * 1024L * 1024L -> 512L * 1024L
                size <= 256L * 1024L * 1024L -> 1L * 1024L * 1024L
                else -> 2L * 1024L * 1024L
            }
            return IndexedTxtTextStore(
                source = source,
                pfd = pfd,
                charset = charset,
                ioDispatcher = ioDispatcher,
                anchorEveryBytes = anchorEveryBytes,
                windowCacheChars = config.indexedWindowCacheChars
            )
        }

        // 大文件且不可 seek 直接失败，避免高风险 OOM。
        if (size != null && size > inMemoryThreshold) {
            throw IllegalStateException(
                "TXT too large and source is not seekable: $size bytes"
            )
        }

        return InMemoryTxtTextStore(
            source = source,
            charset = charset,
            ioDispatcher = ioDispatcher,
            maxBytes = inMemoryThreshold
        )
    }
}
