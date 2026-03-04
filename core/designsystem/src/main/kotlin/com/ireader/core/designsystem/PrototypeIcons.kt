package com.ireader.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

object PrototypeIcons {
    @Composable
    fun MenuList(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawLine(
                color = tint,
                start = p(0.18f, 0.28f),
                end = p(0.82f, 0.28f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = p(0.18f, 0.5f),
                end = p(0.82f, 0.5f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = p(0.18f, 0.72f),
                end = p(0.82f, 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun LightbulbBolt(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.24f,
                center = p(0.5f, 0.42f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = tint,
                start = p(0.38f, 0.74f),
                end = p(0.62f, 0.74f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = p(0.42f, 0.84f),
                end = p(0.58f, 0.84f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val bolt = Path().apply {
                moveTo(size.width * 0.56f, size.height * 0.28f)
                lineTo(size.width * 0.45f, size.height * 0.47f)
                lineTo(size.width * 0.57f, size.height * 0.47f)
                lineTo(size.width * 0.47f, size.height * 0.63f)
            }
            drawPath(
                path = bolt,
                color = tint,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    @Composable
    fun Moon(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val outer = Size(size.width * 0.66f, size.height * 0.66f)
            drawArc(
                color = tint,
                startAngle = 45f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = p(0.17f, 0.17f),
                size = outer,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val inner = Size(size.width * 0.56f, size.height * 0.56f)
            drawArc(
                color = tint,
                startAngle = 210f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = p(0.3f, 0.13f),
                size = inner,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }

    @Composable
    fun SettingsHex(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val hex = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.12f)
                lineTo(size.width * 0.8f, size.height * 0.28f)
                lineTo(size.width * 0.8f, size.height * 0.72f)
                lineTo(size.width * 0.5f, size.height * 0.88f)
                lineTo(size.width * 0.2f, size.height * 0.72f)
                lineTo(size.width * 0.2f, size.height * 0.28f)
                close()
            }
            drawPath(path = hex, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.14f,
                center = p(0.5f, 0.5f),
                style = Stroke(width = stroke)
            )
        }
    }

    @Composable
    fun Target(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.36f,
                center = center,
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.17f,
                center = center,
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.06f,
                center = center
            )
        }
    }

    @Composable
    fun Cart(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val body = Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.24f)
                lineTo(size.width * 0.28f, size.height * 0.24f)
                lineTo(size.width * 0.34f, size.height * 0.62f)
                lineTo(size.width * 0.72f, size.height * 0.62f)
                lineTo(size.width * 0.78f, size.height * 0.36f)
                lineTo(size.width * 0.3f, size.height * 0.36f)
            }
            drawPath(
                path = body,
                color = tint,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(color = tint, radius = size.minDimension * 0.06f, center = p(0.42f, 0.78f))
            drawCircle(color = tint, radius = size.minDimension * 0.06f, center = p(0.68f, 0.78f))
        }
    }

    @Composable
    fun MoreVertical(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val r = size.minDimension * 0.06f
            drawCircle(color = tint, radius = r, center = p(0.5f, 0.28f))
            drawCircle(color = tint, radius = r, center = p(0.5f, 0.5f))
            drawCircle(color = tint, radius = r, center = p(0.5f, 0.72f))
        }
    }

    @Composable
    fun Book(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawRoundRect(
                color = tint,
                topLeft = p(0.2f, 0.16f),
                size = Size(size.width * 0.6f, size.height * 0.7f),
                cornerRadius = CornerRadius(size.minDimension * 0.06f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = tint,
                start = p(0.38f, 0.16f),
                end = p(0.38f, 0.86f),
                strokeWidth = stroke
            )
        }
    }

    @Composable
    fun Store(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawLine(
                color = tint,
                start = p(0.18f, 0.32f),
                end = p(0.82f, 0.32f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = tint,
                topLeft = p(0.22f, 0.38f),
                size = Size(size.width * 0.56f, size.height * 0.42f),
                cornerRadius = CornerRadius(size.minDimension * 0.04f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = tint,
                start = p(0.5f, 0.8f),
                end = p(0.5f, 0.38f),
                strokeWidth = stroke
            )
        }
    }

    @Composable
    fun Crown(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val path = Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.72f)
                lineTo(size.width * 0.24f, size.height * 0.32f)
                lineTo(size.width * 0.5f, size.height * 0.58f)
                lineTo(size.width * 0.76f, size.height * 0.32f)
                lineTo(size.width * 0.84f, size.height * 0.72f)
                close()
            }
            drawPath(path = path, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(
                color = tint,
                start = p(0.2f, 0.78f),
                end = p(0.8f, 0.78f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Smile(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.34f,
                center = center,
                style = Stroke(width = stroke)
            )
            drawCircle(color = tint, radius = size.minDimension * 0.04f, center = p(0.42f, 0.44f))
            drawCircle(color = tint, radius = size.minDimension * 0.04f, center = p(0.58f, 0.44f))
            drawArc(
                color = tint,
                startAngle = 18f,
                sweepAngle = 144f,
                useCenter = false,
                topLeft = p(0.33f, 0.44f),
                size = Size(size.width * 0.34f, size.height * 0.3f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }

    @Composable
    fun PinTop(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawLine(
                color = tint,
                start = p(0.2f, 0.24f),
                end = p(0.8f, 0.24f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = p(0.5f, 0.78f),
                end = p(0.5f, 0.36f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = p(0.38f, 0.48f),
                end = p(0.5f, 0.36f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = p(0.62f, 0.48f),
                end = p(0.5f, 0.36f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun FolderPlus(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val folder = Path().apply {
                moveTo(size.width * 0.14f, size.height * 0.72f)
                lineTo(size.width * 0.14f, size.height * 0.34f)
                lineTo(size.width * 0.36f, size.height * 0.34f)
                lineTo(size.width * 0.45f, size.height * 0.24f)
                lineTo(size.width * 0.86f, size.height * 0.24f)
                lineTo(size.width * 0.86f, size.height * 0.72f)
                close()
            }
            drawPath(path = folder, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color = tint, start = p(0.5f, 0.42f), end = p(0.5f, 0.62f), strokeWidth = stroke)
            drawLine(color = tint, start = p(0.4f, 0.52f), end = p(0.6f, 0.52f), strokeWidth = stroke)
        }
    }

    @Composable
    fun Folder(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val folder = Path().apply {
                moveTo(size.width * 0.14f, size.height * 0.72f)
                lineTo(size.width * 0.14f, size.height * 0.34f)
                lineTo(size.width * 0.36f, size.height * 0.34f)
                lineTo(size.width * 0.45f, size.height * 0.24f)
                lineTo(size.width * 0.86f, size.height * 0.24f)
                lineTo(size.width * 0.86f, size.height * 0.72f)
                close()
            }
            drawPath(path = folder, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Share(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            val a = p(0.3f, 0.5f)
            val b = p(0.68f, 0.32f)
            val c = p(0.68f, 0.68f)
            drawLine(color = tint, start = a, end = b, strokeWidth = stroke)
            drawLine(color = tint, start = a, end = c, strokeWidth = stroke)
            drawCircle(color = tint, radius = size.minDimension * 0.07f, center = a, style = Stroke(stroke))
            drawCircle(color = tint, radius = size.minDimension * 0.07f, center = b, style = Stroke(stroke))
            drawCircle(color = tint, radius = size.minDimension * 0.07f, center = c, style = Stroke(stroke))
        }
    }

    @Composable
    fun Trash(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawLine(
                color = tint,
                start = p(0.26f, 0.28f),
                end = p(0.74f, 0.28f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = tint,
                topLeft = p(0.3f, 0.32f),
                size = Size(size.width * 0.4f, size.height * 0.46f),
                cornerRadius = CornerRadius(size.minDimension * 0.04f),
                style = Stroke(width = stroke)
            )
            drawLine(color = tint, start = p(0.44f, 0.42f), end = p(0.44f, 0.7f), strokeWidth = stroke)
            drawLine(color = tint, start = p(0.56f, 0.42f), end = p(0.56f, 0.7f), strokeWidth = stroke)
        }
    }

    @Composable
    fun MoreHorizontal(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val r = size.minDimension * 0.06f
            drawCircle(color = tint, radius = r, center = p(0.3f, 0.5f))
            drawCircle(color = tint, radius = r, center = p(0.5f, 0.5f))
            drawCircle(color = tint, radius = r, center = p(0.7f, 0.5f))
        }
    }

    @Composable
    fun Search(
        modifier: Modifier = Modifier.size(22.dp),
        tint: Color = LocalContentColor.current
    ) {
        Canvas(modifier = modifier) {
            val stroke = iconStroke()
            drawCircle(
                color = tint,
                radius = size.minDimension * 0.24f,
                center = p(0.44f, 0.44f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = tint,
                start = p(0.58f, 0.58f),
                end = p(0.78f, 0.78f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.iconStroke(): Float = size.minDimension * 0.075f

private fun DrawScope.p(x: Float, y: Float): Offset = Offset(size.width * x, size.height * y)

