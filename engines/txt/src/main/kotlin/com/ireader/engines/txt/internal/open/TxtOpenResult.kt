package com.ireader.engines.txt.internal.open

import com.ireader.reader.model.DocumentId

internal data class TxtOpenResult(
    val documentId: DocumentId,
    val files: TxtBookFiles,
    val meta: TxtMeta
)

