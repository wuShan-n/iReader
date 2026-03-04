package com.ireader.feature.library.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BookCover(
    coverPath: String?,
    titleFallback: String,
    shape: Shape = RoundedCornerShape(9.dp),
    modifier: Modifier = Modifier
) {
    val placeholder = coverGradient(titleFallback)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(shape)
            .background(placeholder)
    ) {
        val density = LocalDensity.current
        val reqWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val reqHeight = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val bitmap = rememberCoverBitmap(path = coverPath, reqWidth = reqWidth, reqHeight = reqHeight)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = titleFallback.ifBlank { "未命名" }.take(12),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun rememberCoverBitmap(path: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
    val key = "${path.orEmpty()}@$reqWidth@$reqHeight"
    val state = produceState<Bitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.IO) {
            val trimmedPath = path?.trim().orEmpty()
            if (trimmedPath.isBlank()) return@withContext null

            CoverBitmapCache.get(key)?.let { return@withContext it }

            val file = File(trimmedPath)
            val bitmap = runCatching {
                BitmapDecodeExt.decodeSampled(file = file, reqWidth = reqWidth, reqHeight = reqHeight)
            }.getOrNull()
            if (bitmap != null) {
                CoverBitmapCache.put(key, bitmap)
            }
            bitmap
        }
    }
    return state.value
}

private fun coverGradient(seed: String): Brush {
    val normalized = seed.trim().ifBlank { "?" }
    val hash = normalized.hashCode().toUInt().toLong()
    val hueA = (hash % 360L).toFloat()
    val hueB = ((hash / 7L) % 360L).toFloat()
    val colorA = Color.hsv(hue = hueA, saturation = 0.42f, value = 0.85f)
    val colorB = Color.hsv(hue = hueB, saturation = 0.62f, value = 0.58f)
    return Brush.linearGradient(listOf(colorA, colorB))
}
