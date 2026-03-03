package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireader.reader.api.render.RenderContent

@Composable
fun TextPage(content: RenderContent.Text, modifier: Modifier = Modifier) {
    Text(
        text = content.text.toString(),
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    )
}

