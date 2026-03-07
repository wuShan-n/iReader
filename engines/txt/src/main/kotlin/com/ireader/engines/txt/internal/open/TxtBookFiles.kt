package com.ireader.engines.txt.internal.open

import java.io.File

internal data class TxtBookFiles(
    val bookDir: File,
    val lockFile: File,
    val textStore: File,
    val metaJson: File,
    val manifestJson: File,
    val outlineIdx: File,
    val paginationDir: File,
    val breakMap: File,
    val blockLock: File,
    val breakLock: File,
    val searchIdx: File,
    val searchLock: File,
    val blockIdx: File = File(bookDir, "block.idx"),
    val breakPatch: File = File(bookDir, "break.patch")
)
