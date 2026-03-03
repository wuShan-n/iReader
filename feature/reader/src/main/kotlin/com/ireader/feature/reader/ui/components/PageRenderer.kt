package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.ui.ReaderSurface
import com.ireader.reader.api.render.RenderContent

@Composable
fun PageRenderer(
    state: ReaderUiState,
    onToggleChrome: () -> Unit,
    onWebSchemeUrl: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val page = state.page
    if (page == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No page")
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(page.id.value) {
                detectTapGestures(onTap = { onToggleChrome() })
            }
    ) {
        when (val content = page.content) {
            is RenderContent.Text -> TextPage(content = content, modifier = Modifier.fillMaxSize())
            is RenderContent.BitmapPage -> BitmapPage(content = content, modifier = Modifier.fillMaxSize())
            is RenderContent.Html -> HtmlPage(
                pageId = page.id.value,
                content = content,
                onWebSchemeUrl = onWebSchemeUrl,
                modifier = Modifier.fillMaxSize()
            )

            is RenderContent.Tiles -> TilesPage(
                pageId = page.id.value,
                content = content,
                modifier = Modifier.fillMaxSize()
            )

            RenderContent.Embedded -> {
                val controller = state.controller
                if (controller == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No controller bound")
                    }
                } else {
                    ReaderSurface(
                        controller = controller,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

