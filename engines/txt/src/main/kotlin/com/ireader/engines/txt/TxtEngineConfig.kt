package com.ireader.engines.txt

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.model.DocumentId
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class TxtEngineConfig(
    val cacheDir: File,
    val persistPagination: Boolean = true,
    val persistOutline: Boolean = false,
    val maxPageCache: Int = 7,
    val annotationProviderFactory: ((DocumentId) -> AnnotationProvider?)? = null,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val paginationDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
)
