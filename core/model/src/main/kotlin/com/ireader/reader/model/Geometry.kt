package com.ireader.reader.model

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/**
 * 归一化矩形：0..1 相对当前页内容坐标，UI 自行映射到像素。
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(p: NormalizedPoint): Boolean = p.x in left..right && p.y in top..bottom
}
