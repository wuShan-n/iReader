package com.ireader.reader.model

data class DocumentMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val identifier: String? = null, // ISBN / OPF id / 自定义
    val extra: Map<String, String> = emptyMap(),
)
