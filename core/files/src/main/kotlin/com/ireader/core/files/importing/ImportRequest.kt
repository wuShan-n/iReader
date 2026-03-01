package com.ireader.core.files.importing

import android.net.Uri

data class ImportRequest(
    val uris: List<Uri> = emptyList(),
    val treeUri: Uri? = null,
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP
)
