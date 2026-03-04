package com.ireader.engines.common.android.reflow

import android.graphics.Paint
import android.text.style.LineHeightSpan

class ParagraphSpacingSpan(
    private val extraPx: Int
) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt
    ) {
        if (extraPx <= 0) {
            return
        }
        if (end > 0 && text[end - 1] == '\n') {
            fm.descent += extraPx
            fm.bottom += extraPx
        }
    }
}
