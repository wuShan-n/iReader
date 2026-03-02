package com.ireader.engines.txt.internal.open

import java.io.File

internal data class TxtBookFiles(
    val bookDir: File,
    val lockFile: File,
    val contentU16: File,
    val metaJson: File,
    val outlineJson: File,
    val paginationDir: File,
    val softBreakIdx: File,
    val softBreakLock: File,
    val bloomIdx: File,
    val bloomLock: File
)
