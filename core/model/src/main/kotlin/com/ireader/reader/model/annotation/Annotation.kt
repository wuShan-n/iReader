package com.ireader.reader.model.annotation

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect

@JvmInline
value class AnnotationId(
    val value: String,
)

enum class AnnotationType { HIGHLIGHT, UNDERLINE, NOTE, BOOKMARK }

data class AnnotationStyle(
    val colorArgb: Int? = null,
    val opacity: Float? = null,
    val extra: Map<String, String> = emptyMap(),
)

/**
 * 锚点：reflow 用 LocatorRange；fixed（PDF）用 page + rects（归一化坐标）
 * 这样你可以：
 * - TXT/EPUB：高亮范围稳定
 * - PDF：高亮可以精确到矩形区域
 */
sealed interface AnnotationAnchor {
    data class ReflowRange(
        val range: LocatorRange,
    ) : AnnotationAnchor

    data class FixedRects(
        // 通常 scheme=pdf.page
        val page: Locator,
        // 归一化矩形列表
        val rects: List<NormalizedRect>,
    ) : AnnotationAnchor
}

data class Annotation(
    val id: AnnotationId,
    val type: AnnotationType,
    val anchor: AnnotationAnchor,
    // NOTE 文本等
    val content: String? = null,
    val style: AnnotationStyle = AnnotationStyle(),
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val extra: Map<String, String> = emptyMap(),
)

/**
 * 创建标注时通常不带 id/时间
 */
data class AnnotationDraft(
    val type: AnnotationType,
    val anchor: AnnotationAnchor,
    val content: String? = null,
    val style: AnnotationStyle = AnnotationStyle(),
    val extra: Map<String, String> = emptyMap(),
)
