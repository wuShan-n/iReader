package com.ireader.reader.api.render

/**
 * 渲染策略：用于优化体验
 * - PDF：先出 DRAFT（低清）再补 FINAL（高清）
 * - TXT/EPUB：通常忽略 quality，但可用于“快速分页->精分页”
 */
data class RenderPolicy(
    val quality: Quality = Quality.FINAL,
    val allowCache: Boolean = true,
    val prefetchNeighbors: Int = 1 // 渲染后预取前后页
) {
    enum class Quality { DRAFT, FINAL }

    companion object {
        val Default = RenderPolicy()
    }
}

