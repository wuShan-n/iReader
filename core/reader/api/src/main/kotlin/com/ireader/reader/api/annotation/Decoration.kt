package com.ireader.reader.api.annotation

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.AnnotationStyle

/**
 * Decoration 是“渲染叠加层”的通用表达。
 * - reflow：按 LocatorRange
 * - fixed：按 page + rects
 */
sealed interface Decoration {

    data class Reflow(
        val range: LocatorRange,
        val style: AnnotationStyle
    ) : Decoration

    data class Fixed(
        val page: Locator,
        val rects: List<NormalizedRect>,
        val style: AnnotationStyle
    ) : Decoration
}


