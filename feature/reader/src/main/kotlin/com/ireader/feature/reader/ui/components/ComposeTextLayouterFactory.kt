package com.ireader.feature.reader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayoutMeasureResult
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory

data class LayoutEnvSnapshot(
    val fontFamilyResolver: FontFamily.Resolver,
    val density: Density,
    val layoutDirection: LayoutDirection
)

class ComposeTextLayouterFactory(
    private val environment: LayoutEnvSnapshot
) : TextLayouterFactory {

    override val environmentKey: String = buildString {
        append(System.identityHashCode(environment.fontFamilyResolver))
        append(':')
        append(environment.density.density)
        append(':')
        append(environment.density.fontScale)
        append(':')
        append(environment.layoutDirection.name)
    }

    override fun create(cacheSize: Int): TextLayouter {
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = environment.fontFamilyResolver,
            defaultDensity = environment.density,
            defaultLayoutDirection = environment.layoutDirection,
            cacheSize = cacheSize
        )
        return ComposeTextLayouter(
            measurer = measurer
        )
    }
}

private class ComposeTextLayouter(
    private val measurer: TextMeasurer
) : TextLayouter {

    override fun measure(text: CharSequence, input: TextLayoutInput): TextLayoutMeasureResult {
        if (text.isEmpty() || input.widthPx <= 0 || input.heightPx <= 0) {
            return TextLayoutMeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }

        val layout = measurer.measure(
            text = text.toString(),
            style = buildComposeTextStyle(
                fontSizeSp = input.fontSizeSp,
                lineHeightMult = input.lineHeightMult,
                textAlign = input.textAlign,
                breakStrategy = input.breakStrategy,
                hyphenationMode = input.hyphenationMode,
                includeFontPadding = input.includeFontPadding,
                fontFamilyName = input.fontFamilyName
            ),
            constraints = Constraints(maxWidth = input.widthPx)
        )

        var lastVisibleLine = -1
        for (lineIndex in 0 until layout.lineCount) {
            val lineEnd = layout.getLineEnd(lineIndex).coerceAtMost(text.length)
            val lineBottom = layout.getLineBottom(lineIndex).toInt()
            val extraSpacing = if (
                input.paragraphSpacingPx > 0 &&
                lineEnd > 0 &&
                lineEnd <= text.length &&
                text[lineEnd - 1] == '\n' &&
                (lineEnd >= text.length || text[lineEnd] != '\n')
            ) {
                input.paragraphSpacingPx
            } else {
                0
            }
            if (lineBottom + extraSpacing <= input.heightPx) {
                lastVisibleLine = lineIndex
            } else {
                break
            }
        }

        if (lastVisibleLine < 0) {
            return TextLayoutMeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }

        return TextLayoutMeasureResult(
            endChar = layout.getLineEnd(lastVisibleLine).coerceAtMost(text.length),
            lineCount = lastVisibleLine + 1,
            lastVisibleLine = lastVisibleLine
        )
    }
}

internal fun buildComposeTextStyle(
    fontSizeSp: Float,
    lineHeightMult: Float,
    textAlign: TextAlignMode,
    breakStrategy: BreakStrategyMode,
    hyphenationMode: HyphenationMode,
    includeFontPadding: Boolean,
    fontFamilyName: String?,
    color: Color = Color.Unspecified
): TextStyle {
    return TextStyle(
        color = color,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMult).sp,
        textAlign = textAlign.toComposeTextAlign(),
        lineBreak = breakStrategy.toComposeLineBreak(),
        hyphens = hyphenationMode.toComposeHyphens(),
        platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
        fontFamily = fontFamilyName.toComposeFontFamilyOrNull()
    )
}

internal fun TextAlignMode.toComposeTextAlign(): TextAlign {
    return when (this) {
        TextAlignMode.START -> TextAlign.Start
        TextAlignMode.JUSTIFY -> TextAlign.Justify
    }
}

internal fun BreakStrategyMode.toComposeLineBreak(): LineBreak {
    return when (this) {
        BreakStrategyMode.SIMPLE -> LineBreak.Simple
        BreakStrategyMode.BALANCED -> LineBreak.Paragraph
        BreakStrategyMode.HIGH_QUALITY -> LineBreak.Paragraph
    }
}

internal fun HyphenationMode.toComposeHyphens(): Hyphens {
    return when (this) {
        HyphenationMode.NONE -> Hyphens.None
        HyphenationMode.NORMAL -> Hyphens.Auto
        HyphenationMode.FULL -> Hyphens.Auto
    }
}

internal fun String?.toComposeFontFamilyOrNull(): FontFamily? {
    return when (this?.trim()?.lowercase()) {
        null,
        "" -> null
        "sans-serif",
        "sans_serif",
        "default" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace",
        "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> null
    }
}
