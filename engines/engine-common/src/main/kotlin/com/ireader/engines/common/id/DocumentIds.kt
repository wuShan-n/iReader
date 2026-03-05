package com.ireader.engines.common.id

import com.ireader.engines.common.hash.Hashing
import com.ireader.reader.model.DocumentId

object DocumentIds {

    fun fromSha1(prefix: String, raw: String): DocumentId {
        return fromSha1(prefix = prefix, rawBytes = raw.toByteArray(Charsets.UTF_8))
    }

    fun fromSha1(prefix: String, rawBytes: ByteArray): DocumentId {
        return DocumentId("$prefix:${Hashing.sha1Hex(rawBytes)}")
    }

    fun fromSha256(
        raw: String,
        length: Int = 64,
        prefix: String? = null
    ): DocumentId {
        return fromSha256(rawBytes = raw.toByteArray(Charsets.UTF_8), length = length, prefix = prefix)
    }

    fun fromSha256(
        rawBytes: ByteArray,
        length: Int = 64,
        prefix: String? = null
    ): DocumentId {
        val value = Hashing.sha256Hex(rawBytes).take(length.coerceAtLeast(1))
        return if (prefix.isNullOrBlank()) {
            DocumentId(value)
        } else {
            DocumentId("$prefix:$value")
        }
    }
}
