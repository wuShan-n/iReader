package com.ireader.feature.reader.ui.components

import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.core.common.android.typography.prefersInterCharacterJustify
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.toTypographySpec

@Composable
fun TextPage(
    content: RenderContent.Text,
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val config = reflowConfig ?: RenderConfig.ReflowText()
    val typography = config.toTypographySpec()
    val density = LocalDensity.current
    val pagePaddingPx = with(density) { typography.pagePaddingDp.dp.roundToPx() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFocusable = false
                isClickable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                gravity = Gravity.TOP or Gravity.START
            }
        },
        update = { textView ->
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, typography.fontSizeSp)
            textView.setLineSpacing(0f, typography.lineHeightMult)
            textView.includeFontPadding = typography.includeFontPadding
            textView.setPadding(pagePaddingPx, pagePaddingPx, pagePaddingPx, pagePaddingPx)
            textView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textView.gravity = Gravity.TOP or Gravity.START
            textView.setTextColor(textColor.toArgb())
            textView.setBackgroundColor(backgroundColor.toArgb())
            val preferInterCharacterJustify = content.text.prefersInterCharacterJustify()
            val familyName = typography.fontFamilyName
            textView.typeface = if (familyName.isNullOrBlank()) {
                Typeface.DEFAULT
            } else {
                Typeface.create(familyName, Typeface.NORMAL)
            }
            textView.text = content.text

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.breakStrategy = typography.breakStrategy.toAndroidBreakStrategy()
                textView.hyphenationFrequency = typography.hyphenationMode.toAndroidHyphenationFrequency()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                textView.justificationMode = typography.textAlign.toAndroidJustificationMode(
                    preferInterCharacter = preferInterCharacterJustify
                )
            }
            textView.requestLayout()
        }
    )
}
