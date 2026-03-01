package com.ireader.reader.api.open

import com.ireader.reader.model.BookFormat

data class OpenOptions(
    val hintFormat: BookFormat? = null,
    val password: String? = null,      // PDF
    val textEncoding: String? = null,  // TXT: "UTF-8"/"GBK"/...
    val extra: Map<String, String> = emptyMap()
)


