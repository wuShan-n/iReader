package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ireader.core.common.android.typography.resolvePagePaddingDp
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.toTypographySpec
import com.ireader.reader.model.DocumentLink
import kotlin.math.roundToInt

data class TextPageLayoutState(
    val layoutResult: TextLayoutResult,
    val contentOffset: Offset,
    val visibleTextLength: Int
)

@Composable
fun TextPage(
    content: RenderContent.Text,
    links: List<DocumentLink>,
    decorations: List<Decoration>,
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color,
    onLayoutStateChanged: (TextPageLayoutState?) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = reflowConfig ?: RenderConfig.ReflowText()
    val typography = config.toTypographySpec()
    val pagePadding = remember(config) { config.resolvePagePaddingDp() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val horizontalPaddingPx = with(density) { pagePadding.horizontal.dp.roundToPx() }
        val verticalTopPaddingPx = with(density) { pagePadding.top.dp.roundToPx() }
        val contentWidthPx = with(density) {
            (maxWidth.roundToPx() - horizontalPaddingPx * 2).coerceAtLeast(1)
        }
        val displayText = remember(
            content.text,
            content.mapping,
            content.justifyVisibleLastLine,
            links,
            decorations
        ) {
            buildComposeDisplayText(
                content = content,
                links = links,
                decorations = decorations
            )
        }
        val textStyle = remember(
            typography.fontSizeSp,
            typography.lineHeightMult,
            typography.breakStrategy,
            typography.hyphenationMode,
            typography.includeFontPadding,
            typography.fontFamilyName,
            textColor
        ) {
            buildComposeTextStyle(
                fontSizeSp = typography.fontSizeSp,
                lineHeightMult = typography.lineHeightMult,
                textAlign = TextAlignMode.JUSTIFY,
                breakStrategy = typography.breakStrategy,
                hyphenationMode = typography.hyphenationMode,
                includeFontPadding = typography.includeFontPadding,
                fontFamilyName = typography.fontFamilyName,
                color = textColor
            )
        }
        val layoutResult = remember(displayText, textStyle, contentWidthPx) {
            textMeasurer.measure(
                text = displayText,
                style = textStyle,
                constraints = Constraints(maxWidth = contentWidthPx)
            )
        }

        SideEffect {
            onLayoutStateChanged(
                TextPageLayoutState(
                    layoutResult = layoutResult,
                    contentOffset = Offset(
                        x = horizontalPaddingPx.toFloat(),
                        y = verticalTopPaddingPx.toFloat()
                    ),
                    visibleTextLength = content.text.length
                )
            )
        }

        DisposableEffect(onLayoutStateChanged) {
            onDispose {
                onLayoutStateChanged(null)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawText(
                textLayoutResult = layoutResult,
                topLeft = Offset(
                    x = horizontalPaddingPx.toFloat(),
                    y = verticalTopPaddingPx.toFloat()
                )
            )
        }
    }
}

private fun buildComposeDisplayText(
    content: RenderContent.Text,
    links: List<DocumentLink>,
    decorations: List<Decoration>
): AnnotatedString {
    val source = content.text.toString()
    val builder = AnnotatedString.Builder(source)
    val mapping = content.mapping

    if (mapping != null) {
        links.forEach { link ->
            val locatorRange = link.range ?: return@forEach
            val charRange = mapping.charRangeFor(locatorRange) ?: return@forEach
            applyComposeRange(builder, charRange) { start, end ->
                builder.addStyle(
                    SpanStyle(
                        color = Color(LINK_COLOR_ARGB),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    start,
                    end
                )
            }
        }

        decorations.filterIsInstance<Decoration.Reflow>().forEach { decoration ->
            val charRange = mapping.charRangeFor(decoration.range) ?: return@forEach
            val highlightColor = resolveHighlightColor(decoration)
            applyComposeRange(builder, charRange) { start, end ->
                builder.addStyle(
                    SpanStyle(background = Color(highlightColor)),
                    start,
                    end
                )
            }
        }
    }

    if (content.justifyVisibleLastLine) {
        val start = builder.length
        builder.append('\n')
        builder.append('\u2060')
        builder.addStyle(SpanStyle(color = Color.Transparent), start, builder.length)
    }

    return builder.toAnnotatedString()
}

private inline fun applyComposeRange(
    builder: AnnotatedString.Builder,
    range: IntRange,
    block: (start: Int, endExclusive: Int) -> Unit
) {
    if (range.isEmpty()) return
    val length = builder.length
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
