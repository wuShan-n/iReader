package com.ireader.engines.epub.internal.parser.model

internal data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String?,
    val properties: String?
)
