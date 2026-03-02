package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal object TxtCharsetDetector {

    suspend fun detect(
        source: DocumentSource,
        overrideName: String?,
        ioDispatcher: CoroutineDispatcher
    ): Charset = withContext(ioDispatcher) {
        if (!overrideName.isNullOrBlank()) {
            return@withContext Charset.forName(overrideName)
        }

        val header = source.openInputStream().use { input ->
            val buf = ByteArray(4)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }

        // UTF-8 BOM: EF BB BF
        if (
            header.size >= 3 &&
            header[0] == 0xEF.toByte() &&
            header[1] == 0xBB.toByte() &&
            header[2] == 0xBF.toByte()
        ) {
            return@withContext Charsets.UTF_8
        }

        // UTF-16 LE BOM: FF FE
        if (
            header.size >= 2 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xFE.toByte()
        ) {
            return@withContext Charsets.UTF_16LE
        }

        // UTF-16 BE BOM: FE FF
        if (
            header.size >= 2 &&
            header[0] == 0xFE.toByte() &&
            header[1] == 0xFF.toByte()
        ) {
            return@withContext Charsets.UTF_16BE
        }

        Charsets.UTF_8
    }
}
