package com.ireader.engines.epub.internal.parser.model

import com.ireader.reader.model.DocumentMetadata

internal data class EpubPackage(
    val metadata: DocumentMetadata,
    val manifest: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val opfPath: String,
    val opfDir: String,
    val navPath: String?,
    val ncxPath: String?,
    val mediaTypeByPath: Map<String, String>
)
