package com.ireader.engines.epub.internal.open

import android.content.Context
import com.ireader.engines.epub.internal.parser.ContainerParser
import com.ireader.engines.epub.internal.parser.NavParser
import com.ireader.engines.epub.internal.parser.NcxParser
import com.ireader.engines.epub.internal.parser.OpfParser
import com.ireader.engines.epub.internal.content.EpubStore
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.DocumentId
import com.ireader.core.files.source.DocumentSource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class EpubOpener(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        try {
            val stableId = stableId(source)
            val docId = DocumentId("epub:$stableId")

            EpubLocks.withDocLock(docId.value) {
                val baseDir = File(context.cacheDir, "epub/$stableId")
                val extractedDir = File(baseDir, EXTRACTED_DIR)
                val markerFile = File(baseDir, COMPLETE_MARKER)

                if (!markerFile.exists()) {
                    if (extractedDir.exists()) {
                        extractedDir.deleteRecursively()
                    }
                    extractedDir.mkdirs()
                    ZipExtract.extractTo(source, extractedDir, ioDispatcher)
                    markerFile.parentFile?.mkdirs()
                    markerFile.writeText("ok")
                }

                val containerXml = File(extractedDir, CONTAINER_XML)
                if (!containerXml.exists()) {
                    return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Missing $CONTAINER_XML")
                    )
                }

                val opfPath = ContainerParser.parseOpfPath(containerXml)
                    ?: return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Invalid container.xml")
                    )

                val opfFile = File(extractedDir, opfPath)
                if (!opfFile.exists()) {
                    return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Missing OPF: $opfPath")
                    )
                }

                val pkg = OpfParser.parse(opfFile = opfFile, opfPath = opfPath)
                if (pkg.spine.isEmpty()) {
                    return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("OPF spine is empty")
                    )
                }

                val toc = pkg.navPath
                    ?.let { navRelPath ->
                        val navFile = File(extractedDir, navRelPath)
                        if (navFile.exists()) {
                            NavParser.parse(navFile = navFile, navRelPath = navRelPath)
                        } else {
                            emptyList()
                        }
                    }
                    .orEmpty()
                    .ifEmpty {
                        pkg.ncxPath
                            ?.let { ncxRelPath ->
                                val ncxFile = File(extractedDir, ncxRelPath)
                                if (ncxFile.exists()) {
                                    NcxParser.parse(ncxFile = ncxFile, ncxRelPath = ncxRelPath)
                                } else {
                                    emptyList()
                                }
                            }
                            .orEmpty()
                    }

                val authority = "${context.packageName}.epub"
                val container = EpubContainer(
                    id = docId,
                    rootDir = extractedDir,
                    baseDir = baseDir,
                    authority = authority,
                    opf = pkg,
                    outline = toc
                )

                EpubStore.register(docId.value, extractedDir)
                ReaderResult.Ok(
                    EpubDocument(
                        container = container,
                        openOptions = options,
                        ioDispatcher = ioDispatcher
                    )
                )
            }
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    private fun stableId(source: DocumentSource): String {
        val seed = buildString {
            append(source.uri)
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes ?: -1)
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val b = bytes[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }

    private fun Throwable.toReaderError(): ReaderError = when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is ZipException -> ReaderError.CorruptOrInvalid(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(message = message, cause = this)
    }

    private companion object {
        private const val EXTRACTED_DIR = "extracted"
        private const val COMPLETE_MARKER = ".complete"
        private const val CONTAINER_XML = "META-INF/container.xml"
    }
}
