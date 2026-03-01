package com.ireader.engines.txt

import java.io.File

data class TxtEngineConfig(
    val cacheDir: File? = null,
    val persistPagination: Boolean = true,
    val persistOutline: Boolean = false,
    val paginationWriteEveryNewStarts: Int = 12,
    val persistLastPosition: Boolean = true,
    val lastPositionMinIntervalMs: Long = 1500L,
    val outlineAsTree: Boolean = true
)
