package com.ireader.reader.api.engine

data class DocumentCapabilities(
    val reflowable: Boolean,
    val fixedLayout: Boolean,
    val outline: Boolean,
    val search: Boolean,
    val textExtraction: Boolean,
    val annotations: Boolean,
    val selection: Boolean,
    val links: Boolean,
)
