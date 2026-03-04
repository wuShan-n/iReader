package com.ireader.feature.reader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.ireader.feature.reader.presentation.GestureAxis
import com.ireader.feature.reader.presentation.PageTurnDirection
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.ui.ReaderSurface
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.model.DocumentLink

@Composable
fun PageRenderer(
    state: ReaderUiState,
    textColor: Color,
    backgroundColor: Color,
    onBackgroundTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
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
    val containerModifier = if (content is RenderContent.Tiles || content is RenderContent.Text) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .pointerInput(page.id.value) {
                detectTapGestures(onTap = { tap -> onBackgroundTap(tap, size) })
            }
    }

    Box(modifier = containerModifier) {
        when (content) {
            is RenderContent.Text -> AnimatedTextPage(
                page = page,
                state = state,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onBackgroundTap = onBackgroundTap,
                onDragEnd = onDragEnd,
                modifier = Modifier.fillMaxSize()
            )

            is RenderContent.BitmapPage -> BitmapPage(content = content, modifier = Modifier.fillMaxSize())

            is RenderContent.Html -> HtmlPage(
                pageId = page.id.value,
                content = content,
                resourceProvider = state.resources,
                reflowConfig = state.currentConfig as? RenderConfig.ReflowText,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onWebSchemeUrl = onWebSchemeUrl,
                modifier = Modifier.fillMaxSize()
            )

            is RenderContent.Tiles -> TilesPage(
                pageId = page.id.value,
                content = content,
                links = page.links,
                onBackgroundTap = onBackgroundTap,
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

@Composable
private fun AnimatedTextPage(
    page: RenderPage,
    state: ReaderUiState,
    textColor: Color,
    backgroundColor: Color,
    onBackgroundTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = state.pageTurnMode
    val target = AnimatedTextTarget(
        page = page,
        transition = state.pageTransition
    )
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (initialState.transition.sequence == targetState.transition.sequence) {
                fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
            } else {
                buildPageTransform(
                    mode = mode,
                    direction = targetState.transition.direction
                )
            }
        },
        label = "txt-page-turn",
        modifier = modifier
    ) { animatedTarget ->
        val targetPage = animatedTarget.page
        val targetContent = targetPage.content as? RenderContent.Text
        if (targetContent == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unsupported page content")
            }
            return@AnimatedContent
        }

        Box(modifier = Modifier.fillMaxSize()) {
            TextPage(
                content = targetContent,
                reflowConfig = state.currentConfig as? RenderConfig.ReflowText,
                textColor = textColor,
                backgroundColor = backgroundColor,
                modifier = Modifier.fillMaxSize()
            )
            TextGestureOverlay(
                pageId = targetPage.id.value,
                mode = mode,
                onBackgroundTap = onBackgroundTap,
                onDragEnd = onDragEnd,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private data class AnimatedTextTarget(
    val page: RenderPage,
    val transition: com.ireader.feature.reader.presentation.PageTurnTransition
)

@Composable
private fun TextGestureOverlay(
    pageId: String,
    mode: PageTurnMode,
    onBackgroundTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragDeltaPx by remember(pageId, mode) { mutableFloatStateOf(0f) }

    val dragModifier = when (mode) {
        PageTurnMode.COVER_HORIZONTAL -> Modifier.pointerInput(pageId, mode) {
            detectHorizontalDragGestures(
                onDragStart = { dragDeltaPx = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragDeltaPx += amount
                },
                onDragCancel = { dragDeltaPx = 0f },
                onDragEnd = {
                    onDragEnd(
                        GestureAxis.HORIZONTAL,
                        dragDeltaPx,
                        size.width
                    )
                    dragDeltaPx = 0f
                }
            )
        }

        PageTurnMode.SCROLL_VERTICAL -> Modifier.pointerInput(pageId, mode) {
            detectVerticalDragGestures(
                onDragStart = { dragDeltaPx = 0f },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    dragDeltaPx += amount
                },
                onDragCancel = { dragDeltaPx = 0f },
                onDragEnd = {
                    onDragEnd(
                        GestureAxis.VERTICAL,
                        dragDeltaPx,
                        size.height
                    )
                    dragDeltaPx = 0f
                }
            )
        }
    }

    Box(
        modifier = modifier
            .pointerInput(pageId) {
                detectTapGestures(
                    onTap = { tap ->
                        onBackgroundTap(tap, size)
                    }
                )
            }
            .then(dragModifier)
    )
}

private fun buildPageTransform(
    mode: PageTurnMode,
    direction: PageTurnDirection
): ContentTransform {
    val forward = direction == PageTurnDirection.NEXT
    val durationMs = 220
    return when (mode) {
        PageTurnMode.COVER_HORIZONTAL -> {
            val enter = slideInHorizontally(
                animationSpec = tween(durationMs)
            ) { full -> if (forward) full else -full } + fadeIn(animationSpec = tween(durationMs / 2))
            val exit = fadeOut(animationSpec = tween(durationMs / 2))
            enter togetherWith exit
        }

        PageTurnMode.SCROLL_VERTICAL -> {
            val enter = slideInVertically(
                animationSpec = tween(durationMs)
            ) { full -> if (forward) full else -full } + fadeIn(animationSpec = tween(durationMs / 2))
            val exit = slideOutVertically(
                animationSpec = tween(durationMs)
            ) { full -> if (forward) -full else full } + fadeOut(animationSpec = tween(durationMs))
            enter togetherWith exit
        }
    }
}
