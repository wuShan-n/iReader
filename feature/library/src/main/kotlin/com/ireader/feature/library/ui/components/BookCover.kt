package com.ireader.feature.library.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BookCover(
    coverPath: String?,
    titleFallback: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    text = titleFallback.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.headlineMedium
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
