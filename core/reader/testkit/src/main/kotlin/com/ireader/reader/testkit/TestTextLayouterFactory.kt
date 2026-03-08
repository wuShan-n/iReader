package com.ireader.reader.testkit

import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayoutMeasureResult
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory

class TestTextLayouterFactory(
    override val environmentKey: String = "reader-test"
) : TextLayouterFactory {
    override fun create(cacheSize: Int): TextLayouter {
        return object : TextLayouter {
            override fun measure(
                text: CharSequence,
                input: TextLayoutInput
            ): TextLayoutMeasureResult {
                if (text.isEmpty() || input.widthPx <= 0 || input.heightPx <= 0) {
                    return TextLayoutMeasureResult(
                        endChar = 0,
                        lineCount = 0,
                        lastVisibleLine = -1
                    )
                }

                val charsPerLine = (input.widthPx / 28).coerceAtLeast(8)
                val lineHeightPx = 54
                val maxLines = (input.heightPx / lineHeightPx).coerceAtLeast(1)

                var line = 0
                var column = 0
                var endChar = 0
                for (index in text.indices) {
                    if (line >= maxLines) {
                        break
                    }
                    val ch = text[index]
                    if (ch == '\n') {
                        endChar = index + 1
                        line++
                        column = 0
                        continue
                    }
                    if (column >= charsPerLine) {
                        line++
                        column = 0
                        if (line >= maxLines) {
                            break
                        }
                    }
                    column++
                    endChar = index + 1
                }

                val visibleLineCount = when {
                    endChar <= 0 -> 0
                    line >= maxLines -> maxLines
                    column > 0 -> line + 1
                    else -> line.coerceAtLeast(1)
                }
                return TextLayoutMeasureResult(
                    endChar = endChar,
                    lineCount = visibleLineCount,
                    lastVisibleLine = visibleLineCount - 1
                )
            }
        }
    }
}
