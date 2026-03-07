package com.ireader.core.common.android.typography

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan

private const val OBJECT_REPLACEMENT_CHAR: Char = '\uFFFC'
private const val WORD_JOINER_CHAR: Char = '\u2060'

fun appendHiddenTrailingLine(
    text: CharSequence,
    lineWidthPx: Int
): CharSequence {
    if (text.isEmpty() || lineWidthPx <= 0) {
        return text
    }
    val builder = SpannableStringBuilder(text)
    val start = builder.length
    builder.append(OBJECT_REPLACEMENT_CHAR)
    builder.append(WORD_JOINER_CHAR)
    builder.setSpan(
        HiddenTrailingLineSpan(lineWidthPx + 1),
        start,
        start + 1,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return builder
}

private class HiddenTrailingLineSpan(
    private val widthPx: Int
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return widthPx
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) = Unit
}
