package com.ireader.feature.reader.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.TileRequest
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TilesPage(
    pageId: String,
    content: RenderContent.Tiles,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bitmaps = remember(pageId) { mutableStateMapOf<TileKey, Bitmap>() }
    val inflight = remember(pageId) { mutableStateMapOf<TileKey, Job>() }

    var zoom by remember(pageId) { mutableFloatStateOf(1f) }
    var offset by remember(pageId) { mutableStateOf(Offset.Zero) }
    var isGesturing by remember(pageId) { mutableStateOf(false) }
    var settleJob by remember(pageId) { mutableStateOf<Job?>(null) }

    DisposableEffect(pageId, content.tileProvider) {
        onDispose {
            settleJob?.cancel()
            inflight.values.forEach { it.cancel() }
            inflight.clear()
            bitmaps.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            bitmaps.clear()
            runCatching { content.tileProvider.close() }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidth = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeight = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val pageWidth = content.pageWidthPx.toFloat().coerceAtLeast(1f)
        val pageHeight = content.pageHeightPx.toFloat().coerceAtLeast(1f)
        val baseScale = min(viewportWidth / pageWidth, viewportHeight / pageHeight).coerceAtLeast(0.05f)
        val drawScale = baseScale * zoom
        val normalizedScale = (drawScale * 100f).roundToInt() / 100f
        val quality = if (isGesturing) RenderPolicy.Quality.DRAFT else RenderPolicy.Quality.FINAL

        val tilePageSize = (512f / drawScale).coerceIn(96f, 2048f)
        val leftPage = ((-offset.x) / drawScale).coerceIn(0f, pageWidth)
        val topPage = ((-offset.y) / drawScale).coerceIn(0f, pageHeight)
        val rightPage = ((viewportWidth - offset.x) / drawScale).coerceIn(0f, pageWidth)
        val bottomPage = ((viewportHeight - offset.y) / drawScale).coerceIn(0f, pageHeight)

        val startX = (leftPage / tilePageSize).toInt().coerceAtLeast(0)
        val endX = ceil(rightPage / tilePageSize).toInt().coerceAtLeast(startX)
        val startY = (topPage / tilePageSize).toInt().coerceAtLeast(0)
        val endY = ceil(bottomPage / tilePageSize).toInt().coerceAtLeast(startY)

        val needed = remember(
            pageId,
            startX,
            endX,
            startY,
            endY,
            tilePageSize,
            normalizedScale,
            quality
        ) {
            buildList {
                for (y in startY..endY) {
                    for (x in startX..endX) {
                        val left = (x * tilePageSize).toInt()
                        val top = (y * tilePageSize).toInt()
                        val width = min(tilePageSize.toInt(), content.pageWidthPx - left).coerceAtLeast(1)
                        val height = min(tilePageSize.toInt(), content.pageHeightPx - top).coerceAtLeast(1)
                        if (width <= 0 || height <= 0) continue
                        add(
                            TileKey(
                                leftPx = left,
                                topPx = top,
                                widthPx = width,
                                heightPx = height,
                                scale = normalizedScale,
                                quality = quality
                            )
                        )
                    }
                }
            }
        }

        LaunchedEffect(needed) {
            needed.forEach { key ->
                if (bitmaps.containsKey(key) || inflight.containsKey(key)) return@forEach
                val requestJob = scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        content.tileProvider.renderTile(
                            TileRequest(
                                leftPx = key.leftPx,
                                topPx = key.topPx,
                                widthPx = key.widthPx,
                                heightPx = key.heightPx,
                                scale = key.scale,
                                quality = key.quality
                            )
                        )
                    }

                    bitmaps.put(key, bitmap)?.let { previous ->
                        if (previous != bitmap && !previous.isRecycled) {
                            previous.recycle()
                        }
                    }

                    while (bitmaps.size > 120) {
                        val first = bitmaps.entries.firstOrNull() ?: break
                        bitmaps.remove(first.key)
                        if (!first.value.isRecycled) {
                            first.value.recycle()
                        }
                    }
                }
                inflight[key] = requestJob
                requestJob.invokeOnCompletion { inflight.remove(key) }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageId) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val oldZoom = zoom
                        val nextZoom = (zoom * gestureZoom).coerceIn(0.7f, 6.0f)

                        val oldScale = baseScale * oldZoom
                        val nextScale = baseScale * nextZoom
                        val before = (centroid - offset) / oldScale
                        zoom = nextZoom
                        offset = (centroid - before * nextScale) + pan

                        isGesturing = true
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            delay(140L)
                            isGesturing = false
                        }
                    }
                }
        ) {
            val drawWidth = pageWidth * drawScale
            val drawHeight = pageHeight * drawScale
            val centerOffsetX = (size.width - drawWidth) / 2f
            val centerOffsetY = (size.height - drawHeight) / 2f

            needed.forEach { key ->
                val bitmap = bitmaps[key] ?: return@forEach
                if (bitmap.isRecycled) return@forEach

                val dstLeft = (centerOffsetX + offset.x + key.leftPx * drawScale).roundToInt()
                val dstTop = (centerOffsetY + offset.y + key.topPx * drawScale).roundToInt()
                val dstWidth = (key.widthPx * drawScale).roundToInt().coerceAtLeast(1)
                val dstHeight = (key.heightPx * drawScale).roundToInt().coerceAtLeast(1)

                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(dstLeft, dstTop),
                    dstSize = IntSize(dstWidth, dstHeight)
                )
            }
        }
    }
}

private data class TileKey(
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float,
    val quality: RenderPolicy.Quality
)

