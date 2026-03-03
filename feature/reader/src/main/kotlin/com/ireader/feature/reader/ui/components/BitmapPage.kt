package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.ireader.reader.api.render.RenderContent

@Composable
fun BitmapPage(content: RenderContent.BitmapPage, modifier: Modifier = Modifier) {
    Image(
        bitmap = content.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

