package com.ireader.engines.common.android.id

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.id.DocumentIds
import com.ireader.reader.model.DocumentId

object SourceDocumentIds {

    fun fromSourceSha1(
        prefix: String,
        source: DocumentSource,
        extraParts: List<String> = emptyList()
    ): DocumentId {
        val raw = buildRaw(source, extraParts)
        return DocumentIds.fromSha1(prefix = prefix, raw = raw)
    }

    fun fromSourceSha256(
        source: DocumentSource,
        length: Int = 64,
        prefix: String? = null,
        extraParts: List<String> = emptyList()
    ): DocumentId {
        val raw = buildRaw(source, extraParts)
        return DocumentIds.fromSha256(raw = raw, length = length, prefix = prefix)
    }

    private fun buildRaw(source: DocumentSource, extraParts: List<String>): String {
        return buildString {
            append(source.uri.toString())
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes ?: -1L)
            append('|')
            append(source.mimeType.orEmpty())
            for (part in extraParts) {
                append('|')
                append(part)
            }
        }
    }
}
