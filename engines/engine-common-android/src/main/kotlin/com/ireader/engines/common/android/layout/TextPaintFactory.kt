package com.ireader.engines.common.android.layout

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

object TextPaintFactory {

    fun create(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): TextPaint {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        val textSizePx = config.fontSizeSp * constraints.density * constraints.fontScale
        paint.textSize = textSizePx
        paint.color = Color.BLACK
        val family = config.fontFamilyName
        if (!family.isNullOrBlank()) {
            paint.typeface = Typeface.create(family, Typeface.NORMAL)
        }
        return paint
    }
}
