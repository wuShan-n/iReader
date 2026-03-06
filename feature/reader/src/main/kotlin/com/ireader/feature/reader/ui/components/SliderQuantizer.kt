package com.ireader.feature.reader.ui.components

import kotlin.math.roundToInt

internal fun sliderDiscreteSteps(
    range: ClosedFloatingPointRange<Float>,
    step: Float
): Int {
    if (step <= 0f) return 0
    val count = ((range.endInclusive - range.start) / step).roundToInt()
    return (count - 1).coerceAtLeast(0)
}

internal fun snapSliderValue(
    raw: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float
): Float {
    val clamped = raw.coerceIn(range.start, range.endInclusive)
    if (step <= 0f) return clamped
    val index = ((clamped - range.start) / step).roundToInt()
    return (range.start + index * step).coerceIn(range.start, range.endInclusive)
}
