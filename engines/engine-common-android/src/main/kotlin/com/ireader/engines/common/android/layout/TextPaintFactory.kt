package com.ireader.engines.common.android.layout

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.RenderConfig

object TextPaintFactory {

    private val typefaceCache = LinkedHashMap<String, Typeface>(16, 0.75f, true)
    private val typefaceLock = Any()

    fun create(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): TextPaint {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        val textSizePx = config.fontSizeSp * constraints.density * constraints.fontScale
        paint.textSize = textSizePx
        paint.color = resolveTextColor(config)
        val family = config.fontFamilyName
        if (!family.isNullOrBlank()) {
            paint.typeface = resolveTypeface(family)
        }
        return paint
    }

    private fun resolveTextColor(config: RenderConfig.ReflowText): Int {
        val raw = config.extra[READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY] ?: return Color.BLACK
        return raw.toLongOrNull()?.toInt() ?: Color.BLACK
    }

    private fun resolveTypeface(family: String): Typeface {
        synchronized(typefaceLock) {
            val cached = typefaceCache[family]
            if (cached != null) {
                return cached
            }
            return Typeface.create(family, Typeface.NORMAL).also { created ->
                typefaceCache[family] = created
                if (typefaceCache.size > 32) {
                    val eldestKey = typefaceCache.entries.firstOrNull()?.key
                    if (eldestKey != null) {
                        typefaceCache.remove(eldestKey)
                    }
                }
            }
        }
    }
}
