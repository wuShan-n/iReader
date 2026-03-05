package com.ireader.feature.reader.ui.components

import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.core.common.android.typography.prefersInterCharacterJustify
import com.ireader.core.common.android.typography.effectiveForInterCharacterScript
import com.ireader.core.common.android.typography.resolveAndroidLineBreakConfig
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.toTypographySpec
import com.ireader.reader.model.DocumentLink
import kotlin.math.roundToInt

@Composable
fun TextPage(
    content: RenderContent.Text,
    links: List<DocumentLink>,
    decorations: List<Decoration>,
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color,
    onTextViewBound: (TextView) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = reflowConfig ?: RenderConfig.ReflowText()
    val typography = config.toTypographySpec()
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { typography.pagePaddingDp.dp.roundToPx() }
    val verticalPaddingPx = horizontalPaddingPx
    val displayText = remember(content.text, content.mapping, links, decorations) {
        buildDisplayText(
            content = content,
            links = links,
            decorations = decorations
        )
    }
    val preferInterCharacterJustify = remember(content.text) {
        content.text.prefersInterCharacterJustify()
    }
    val effectiveBreakStrategy = remember(typography.breakStrategy, preferInterCharacterJustify) {
        typography.breakStrategy.effectiveForInterCharacterScript(preferInterCharacterJustify)
    }
    val lineBreakConfig = remember(preferInterCharacterJustify) {
        resolveAndroidLineBreakConfig(preferInterCharacter = preferInterCharacterJustify)
    }
    val typeface = remember(typography.fontFamilyName) {
        val familyName = typography.fontFamilyName
        if (familyName.isNullOrBlank()) {
            Typeface.DEFAULT
        } else {
            Typeface.create(familyName, Typeface.NORMAL)
        }
    }
    val textColorArgb = remember(textColor) { textColor.toArgb() }
    val backgroundColorArgb = remember(backgroundColor) { backgroundColor.toArgb() }

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
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                onTextViewBound(this)
            }
        },
        update = { textView ->
            val expectedTextSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                typography.fontSizeSp,
                textView.resources.displayMetrics
            )
            if (kotlin.math.abs(textView.textSize - expectedTextSizePx) > 0.5f) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, typography.fontSizeSp)
            }
            if (textView.lineSpacingExtra != 0f || textView.lineSpacingMultiplier != typography.lineHeightMult) {
                textView.setLineSpacing(0f, typography.lineHeightMult)
            }
            if (textView.includeFontPadding != typography.includeFontPadding) {
                textView.includeFontPadding = typography.includeFontPadding
            }
            runCatching {
                if (!textView.isFallbackLineSpacing) {
                    textView.setFallbackLineSpacing(true)
                }
            }
            if (
                textView.paddingLeft != horizontalPaddingPx ||
                textView.paddingTop != verticalPaddingPx ||
                textView.paddingRight != horizontalPaddingPx ||
                textView.paddingBottom != verticalPaddingPx
            ) {
                textView.setPadding(
                    horizontalPaddingPx,
                    verticalPaddingPx,
                    horizontalPaddingPx,
                    verticalPaddingPx
                )
            }
            if (textView.currentTextColor != textColorArgb) {
                textView.setTextColor(textColorArgb)
            }
            if ((textView.background as? android.graphics.drawable.ColorDrawable)?.color != backgroundColorArgb) {
                textView.setBackgroundColor(backgroundColorArgb)
            }
            if (textView.typeface !== typeface) {
                textView.typeface = typeface
            }
            if (textView.text !== displayText) {
                textView.text = displayText
            }
            val breakStrategy = effectiveBreakStrategy.toAndroidBreakStrategy()
            if (textView.breakStrategy != breakStrategy) {
                textView.breakStrategy = breakStrategy
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lineBreakConfig?.let { config ->
                    if (textView.lineBreakStyle != config.lineBreakStyle) {
                        textView.lineBreakStyle = config.lineBreakStyle
                    }
                    if (textView.lineBreakWordStyle != config.lineBreakWordStyle) {
                        textView.lineBreakWordStyle = config.lineBreakWordStyle
                    }
                }
            }
            val hyphenation = typography.hyphenationMode.toAndroidHyphenationFrequency()
            if (textView.hyphenationFrequency != hyphenation) {
                textView.hyphenationFrequency = hyphenation
            }
            runCatching {
                val expectedJustification = typography.textAlign.toAndroidJustificationMode(
                    preferInterCharacter = preferInterCharacterJustify
                )
                if (textView.justificationMode != expectedJustification) {
                    textView.justificationMode = expectedJustification
                }
            }
        }
    )
}

private fun buildDisplayText(
    content: RenderContent.Text,
    links: List<DocumentLink>,
    decorations: List<Decoration>
): CharSequence {
    val source = content.text
    val mapping = content.mapping ?: return source
    val spannable = SpannableString(source)

    links.forEach { link ->
        val locatorRange = link.range ?: return@forEach
        val charRange = mapping.charRangeFor(locatorRange) ?: return@forEach
        applyRange(spannable, charRange) { start, end ->
            spannable.setSpan(
                ForegroundColorSpan(LINK_COLOR_ARGB),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                UnderlineSpan(),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    decorations.filterIsInstance<Decoration.Reflow>().forEach { decoration ->
        val charRange = mapping.charRangeFor(decoration.range) ?: return@forEach
        val highlightColor = resolveHighlightColor(decoration)
        applyRange(spannable, charRange) { start, end ->
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    return spannable
}

private inline fun applyRange(
    spannable: SpannableString,
    range: IntRange,
    block: (start: Int, endExclusive: Int) -> Unit
) {
    if (range.isEmpty()) return
    val length = spannable.length
    if (length <= 0) return
    val start = range.first.coerceIn(0, length - 1)
    val endExclusive = (range.last + 1).coerceIn(0, length)
    if (start >= endExclusive) return
    block(start, endExclusive)
}

private fun resolveHighlightColor(decoration: Decoration.Reflow): Int {
    val base = decoration.style.colorArgb ?: DEFAULT_HIGHLIGHT_COLOR_ARGB
    val baseAlpha = (base ushr 24) and 0xFF
    val opacity = decoration.style.opacity
    val alpha = when {
        opacity != null -> {
            (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        }

        decoration.style.colorArgb != null -> baseAlpha
        else -> (DEFAULT_HIGHLIGHT_OPACITY * 255f).roundToInt()
    }.coerceIn(0, 255)
    return (base and 0x00FF_FFFF) or (alpha shl 24)
}

private const val LINK_COLOR_ARGB: Int = 0xFF2D6CDF.toInt()
private const val DEFAULT_HIGHLIGHT_COLOR_ARGB: Int = 0xFFFFD54F.toInt()
private const val DEFAULT_HIGHLIGHT_OPACITY: Float = 0.35f
