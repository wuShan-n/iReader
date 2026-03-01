package com.ireader.engines.epub.internal.open

import com.ireader.reader.source.DocumentSource
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal object ZipExtract {
    private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024

    suspend fun extractTo(
        source: DocumentSource,
        outDir: File,
        ioDispatcher: CoroutineDispatcher
    ) = withContext(ioDispatcher) {
        var totalBytes = 0L

        source.openInputStream().use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val target = File(outDir, entry.name)

                    val outCanonical = outDir.canonicalFile
                    val targetCanonical = target.canonicalFile
                    if (!targetCanonical.path.startsWith(outCanonical.path)) {
                        zis.closeEntry()
                        continue
                    }

                    if (entry.isDirectory) {
                        targetCanonical.mkdirs()
                        zis.closeEntry()
                        continue
                    }

                    targetCanonical.parentFile?.mkdirs()
                    FileOutputStream(targetCanonical).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)

                            written += read
                            totalBytes += read

                            if (written > MAX_ENTRY_BYTES) {
                                error("EPUB entry too large: ${entry.name}")
                            }
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                error("EPUB archive too large")
                            }
                        }
                    }

                    zis.closeEntry()
                }
            }
        }
    }
}
