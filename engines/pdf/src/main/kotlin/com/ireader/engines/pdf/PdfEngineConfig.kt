package com.ireader.engines.pdf

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.math.max
import kotlin.math.min

enum class PdfBackendStrategy {
    AUTO,
    PDFIUM_ONLY,
    PLATFORM_ONLY
}

data class PdfEngineConfig(
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val tileBaseSizePx: Int = 512,
    val tileCacheMaxBytes: Int = defaultTileCacheBytes(),
    val backendStrategy: PdfBackendStrategy = PdfBackendStrategy.AUTO,
    val annotationProviderFactory: ((DocumentId) -> AnnotationProvider?)? = null
) {
    companion object {
        private fun defaultTileCacheBytes(): Int {
            val maxMem = Runtime.getRuntime().maxMemory()
            val target = maxMem / 12L
            return max(min(target, 192L * 1024 * 1024), 24L * 1024 * 1024).toInt()
        }
    }
}
