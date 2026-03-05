package com.ireader.engines.common.android.id

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.id.DocumentIds
import com.ireader.reader.model.DocumentId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object SourceDocumentIds {

    fun fromSourceSha1(
        prefix: String,
        source: DocumentSource,
        extraParts: List<String> = emptyList(),
        includeDisplayName: Boolean = false
    ): DocumentId {
        val bytes = buildCanonicalBytes(
            source = source,
            extraParts = extraParts,
            includeDisplayName = includeDisplayName
        )
        return DocumentIds.fromSha1(prefix = prefix, rawBytes = bytes)
    }

    fun fromSourceSha256(
        source: DocumentSource,
        length: Int = 64,
        prefix: String? = null,
        extraParts: List<String> = emptyList(),
        includeDisplayName: Boolean = false
    ): DocumentId {
        val bytes = buildCanonicalBytes(
            source = source,
            extraParts = extraParts,
            includeDisplayName = includeDisplayName
        )
        return DocumentIds.fromSha256(rawBytes = bytes, length = length, prefix = prefix)
    }

    private fun buildCanonicalBytes(
        source: DocumentSource,
        extraParts: List<String>,
        includeDisplayName: Boolean
    ): ByteArray {
        val output = ByteArrayOutputStream(256)
        DataOutputStream(output).use { stream ->
            stream.writeInt(2) // schema version
            stream.writeBoolean(includeDisplayName)
            stream.writeStringPart(source.uri.toString())
            stream.writeStringPart((source.sizeBytes ?: -1L).toString())
            stream.writeStringPart(source.mimeType.orEmpty())
            if (includeDisplayName) {
                stream.writeStringPart(source.displayName.orEmpty())
            }
            stream.writeInt(extraParts.size)
            for (part in extraParts) {
                stream.writeStringPart(part)
            }
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeStringPart(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
