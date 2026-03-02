package com.ireader.engines.common.id

import com.ireader.engines.common.hash.Hashing
import com.ireader.reader.model.DocumentId

object DocumentIds {

    fun fromSha1(prefix: String, raw: String): DocumentId {
        return DocumentId("$prefix:${Hashing.sha1Hex(raw)}")
    }

    fun fromSha256(
        raw: String,
        length: Int = 64,
        prefix: String? = null
    ): DocumentId {
        val value = Hashing.sha256Hex(raw).take(length.coerceAtLeast(1))
        return if (prefix.isNullOrBlank()) {
            DocumentId(value)
        } else {
            DocumentId("$prefix:$value")
        }
    }
}
