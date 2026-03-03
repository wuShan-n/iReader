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
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.model.DocumentLink

@Composable
fun PageRenderer(
    state: ReaderUiState,
    onToggleChrome: () -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
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

    val content = page.content
    val containerModifier = if (content is RenderContent.Tiles) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .pointerInput(page.id.value) {
                detectTapGestures(onTap = { onToggleChrome() })
            }
    }

    Box(
        modifier = containerModifier
    ) {
        when (content) {
            is RenderContent.Text -> TextPage(content = content, modifier = Modifier.fillMaxSize())
            is RenderContent.BitmapPage -> BitmapPage(content = content, modifier = Modifier.fillMaxSize())
            is RenderContent.Html -> HtmlPage(
                pageId = page.id.value,
                content = content,
                resourceProvider = state.resources,
                reflowConfig = state.currentConfig as? RenderConfig.ReflowText,
                onWebSchemeUrl = onWebSchemeUrl,
                modifier = Modifier.fillMaxSize()
            )

            is RenderContent.Tiles -> TilesPage(
                pageId = page.id.value,
                content = content,
                links = page.links,
                onToggleChrome = onToggleChrome,
                onLinkActivated = onLinkActivated,
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
