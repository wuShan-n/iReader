package com.ireader.feature.reader.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.NormalizedPoint
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
    links: List<DocumentLink>,
    onToggleChrome: () -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
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
        val drawScale = zoom
        val drawWidth = pageWidth * drawScale
        val drawHeight = pageHeight * drawScale
        val pageLeft = (viewportWidth - drawWidth) / 2f + offset.x
        val pageTop = (viewportHeight - drawHeight) / 2f + offset.y
        val normalizedScale = (drawScale * 100f).roundToInt() / 100f
        val quality = if (isGesturing) RenderPolicy.Quality.DRAFT else RenderPolicy.Quality.FINAL

        val tilePageSize = (512f / drawScale).coerceIn(96f, 2048f)
        val leftPage = ((0f - pageLeft) / drawScale).coerceIn(0f, pageWidth)
        val topPage = ((0f - pageTop) / drawScale).coerceIn(0f, pageHeight)
        val rightPage = ((viewportWidth - pageLeft) / drawScale).coerceIn(0f, pageWidth)
        val bottomPage = ((viewportHeight - pageTop) / drawScale).coerceIn(0f, pageHeight)

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
                .pointerInput(pageId, links, pageWidth, pageHeight, drawScale, pageLeft, pageTop) {
                    detectTapGestures { tap ->
                        val link = hitTestLink(
                            tap = tap,
                            links = links,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            drawScale = drawScale,
                            pageLeft = pageLeft,
                            pageTop = pageTop
                        )
                        if (link != null) {
                            onLinkActivated(link)
                        } else {
                            onToggleChrome()
                        }
                    }
                }
                .pointerInput(pageId) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val oldZoom = zoom
                        val nextZoom = (zoom * gestureZoom).coerceIn(0.7f, 6.0f)
                        val oldCenteredX = (viewportWidth - pageWidth * oldZoom) / 2f
                        val oldCenteredY = (viewportHeight - pageHeight * oldZoom) / 2f
                        val before = Offset(
                            x = (centroid.x - (oldCenteredX + offset.x)) / oldZoom,
                            y = (centroid.y - (oldCenteredY + offset.y)) / oldZoom
                        )
                        val nextCenteredX = (viewportWidth - pageWidth * nextZoom) / 2f
                        val nextCenteredY = (viewportHeight - pageHeight * nextZoom) / 2f
                        zoom = nextZoom
                        offset = Offset(
                            x = centroid.x - nextCenteredX - before.x * nextZoom + pan.x,
                            y = centroid.y - nextCenteredY - before.y * nextZoom + pan.y
                        )

                        isGesturing = true
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            delay(140L)
                            isGesturing = false
                        }
                    }
                }
        ) {
            needed.forEach { key ->
                val bitmap = bitmaps[key] ?: return@forEach
                if (bitmap.isRecycled) return@forEach

                val dstLeft = (pageLeft + key.leftPx * drawScale).roundToInt()
                val dstTop = (pageTop + key.topPx * drawScale).roundToInt()
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

private fun hitTestLink(
    tap: Offset,
    links: List<DocumentLink>,
    pageWidth: Float,
    pageHeight: Float,
    drawScale: Float,
    pageLeft: Float,
    pageTop: Float
): DocumentLink? {
    val pageX = (tap.x - pageLeft) / drawScale
    val pageY = (tap.y - pageTop) / drawScale
    if (pageX !in 0f..pageWidth || pageY !in 0f..pageHeight) return null

    val point = NormalizedPoint(
        x = (pageX / pageWidth).coerceIn(0f, 1f),
        y = (pageY / pageHeight).coerceIn(0f, 1f)
    )
    return links.firstOrNull { link ->
        link.bounds.orEmpty().any { rect -> rect.contains(point) }
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
