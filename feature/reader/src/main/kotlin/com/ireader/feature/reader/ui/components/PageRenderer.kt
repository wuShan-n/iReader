package com.ireader.feature.reader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ireader.feature.reader.presentation.GestureAxis
import com.ireader.feature.reader.presentation.PageTurnAnimationKind
import com.ireader.feature.reader.presentation.PageTurnDirection
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.defaultPageTurnStyle
import com.ireader.feature.reader.presentation.pageTurnStyle
import com.ireader.feature.reader.presentation.resolvePageTurnAnimationKind
import com.ireader.feature.reader.ui.ReaderSurface
import com.ireader.reader.api.render.PAGE_PADDING_BOTTOM_DP_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_PADDING_TOP_DP_EXTRA_KEY
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_HORIZONTAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_HORIZONTAL_MIN_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MIN_DP
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

@Composable
fun PageRenderer(
    state: ReaderUiState,
    textColor: Color,
    backgroundColor: Color,
    onBackgroundTap: (Offset, IntSize, Boolean) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
    onSelectionStart: (Locator) -> Unit,
    onSelectionUpdate: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val page = state.page
    if (page == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No page")
        }
        return
    }

    DisposableEffect(page.id.value, page.content) {
        onDispose {
            val bitmap = (page.content as? RenderContent.BitmapPage)?.bitmap ?: return@onDispose
            if (!bitmap.isRecycled) {
                runCatching { bitmap.recycle() }
            }
        }
    }

    val content = page.content
    val containerModifier = if (content is RenderContent.Tiles || content is RenderContent.Text) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .pointerInput(page.id.value) {
                detectTapGestures(onTap = { tap ->
                    onSelectionClear()
                    onBackgroundTap(tap, size, true)
                })
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
                onLinkActivated = onLinkActivated,
                onSelectionStart = onSelectionStart,
                onSelectionUpdate = onSelectionUpdate,
                onSelectionFinish = onSelectionFinish,
                onSelectionClear = onSelectionClear,
                modifier = Modifier.fillMaxSize()
            )

            is RenderContent.BitmapPage -> BitmapPage(content = content, modifier = Modifier.fillMaxSize())

            is RenderContent.Tiles -> TilesPage(
                pageId = page.id.value,
                content = content,
                links = page.links,
                decorations = page.decorations,
                pageLocator = page.locator,
                onBackgroundTap = onBackgroundTap,
                onLinkActivated = onLinkActivated,
                onSelectionStart = onSelectionStart,
                onSelectionFinish = onSelectionFinish,
                onSelectionClear = onSelectionClear,
                modifier = Modifier.fillMaxSize()
            )

            RenderContent.Embedded -> {
                val handle = state.handle
                if (handle == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No reader handle bound")
                    }
                } else {
                    val reflowConfig = state.currentConfig as? RenderConfig.ReflowText
                    val horizontalPaddingDp = reflowConfig?.pagePaddingDp
                        ?.coerceIn(REFLOW_PAGE_PADDING_HORIZONTAL_MIN_DP, REFLOW_PAGE_PADDING_HORIZONTAL_MAX_DP)
                        ?: 0f
                    val topPaddingDp = reflowConfig?.resolveTopPaddingDp(horizontalPaddingDp) ?: horizontalPaddingDp
                    val bottomPaddingDp = reflowConfig?.resolveBottomPaddingDp(horizontalPaddingDp) ?: horizontalPaddingDp
                    val readiumBasePaddingDp = minOf(horizontalPaddingDp, topPaddingDp, bottomPaddingDp)
                    val horizontalExtraDp = (horizontalPaddingDp - readiumBasePaddingDp).coerceAtLeast(0f)
                    val topExtraDp = (topPaddingDp - readiumBasePaddingDp).coerceAtLeast(0f)
                    val bottomExtraDp = (bottomPaddingDp - readiumBasePaddingDp).coerceAtLeast(0f)
                    ReaderSurface(
                        handle = handle,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = horizontalExtraDp.dp,
                                end = horizontalExtraDp.dp,
                                top = topExtraDp.dp,
                                bottom = bottomExtraDp.dp
                            )
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
    onBackgroundTap: (Offset, IntSize, Boolean) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
    onSelectionStart: (Locator) -> Unit,
    onSelectionUpdate: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = state.pageTurnMode
    val reflowConfig = state.currentConfig as? RenderConfig.ReflowText
    val style = reflowConfig?.pageTurnStyle() ?: defaultPageTurnStyle(mode = mode)
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
                    style = style,
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

        var textLayoutState by remember(targetPage.id.value) { mutableStateOf<TextPageLayoutState?>(null) }
        val linkHits = remember(targetPage.id.value, targetPage.links, targetContent.mapping) {
            val mapping = targetContent.mapping
            targetPage.links.mapNotNull { link ->
                val range = link.range ?: return@mapNotNull null
                val charRange = mapping?.charRangeFor(range) ?: return@mapNotNull null
                if (charRange.isEmpty()) return@mapNotNull null
                TextLinkHit(link = link, range = charRange)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            TextPage(
                content = targetContent,
                links = targetPage.links,
                decorations = targetPage.decorations,
                reflowConfig = reflowConfig,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onLayoutStateChanged = { layoutState ->
                    textLayoutState = layoutState
                },
                modifier = Modifier.fillMaxSize()
            )
            TextGestureOverlay(
                pageId = targetPage.id.value,
                mode = mode,
                onTap = { tap, size ->
                    val link = hitTestTextLink(
                        tap = tap,
                        layoutState = textLayoutState,
                        links = linkHits
                    )
                    if (link != null) {
                        onLinkActivated(link)
                    } else {
                        onBackgroundTap(tap, size, true)
                    }
                },
                onDragEnd = onDragEnd,
                resolveLocatorAt = { tap ->
                    hitTestTextLocator(
                        tap = tap,
                        layoutState = textLayoutState,
                        mapping = targetContent.mapping
                    )
                },
                onSelectionStart = onSelectionStart,
                onSelectionUpdate = onSelectionUpdate,
                onSelectionFinish = onSelectionFinish,
                onSelectionClear = onSelectionClear,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private data class AnimatedTextTarget(
    val page: RenderPage,
    val transition: com.ireader.feature.reader.presentation.PageTurnTransition
)

private data class TextLinkHit(
    val link: DocumentLink,
    val range: IntRange
)

@Composable
private fun TextGestureOverlay(
    pageId: String,
    mode: PageTurnMode,
    onTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    resolveLocatorAt: (Offset) -> Locator?,
    onSelectionStart: (Locator) -> Unit,
    onSelectionUpdate: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
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
    }

    Box(
        modifier = modifier
            .pointerInput(pageId) {
                detectTapGestures(
                    onTap = { tap ->
                        onSelectionClear()
                        onTap(tap, size)
                    },
                    onLongPress = { tap ->
                        val locator = resolveLocatorAt(tap) ?: return@detectTapGestures
                        onSelectionStart(locator)
                        onSelectionFinish()
                    }
                )
            }
            .pointerInput(pageId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        val locator = resolveLocatorAt(position) ?: return@detectDragGesturesAfterLongPress
                        onSelectionStart(locator)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val locator = resolveLocatorAt(change.position)
                            ?: return@detectDragGesturesAfterLongPress
                        onSelectionUpdate(locator)
                    },
                    onDragEnd = onSelectionFinish,
                    onDragCancel = onSelectionFinish
                )
            }
            .then(dragModifier)
    )
}

private fun RenderConfig.ReflowText.resolveTopPaddingDp(defaultDp: Float): Float {
    return (extra[PAGE_PADDING_TOP_DP_EXTRA_KEY]?.toFloatOrNull() ?: defaultDp)
        .takeIf(Float::isFinite)
        ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
        ?: defaultDp.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
}

private fun RenderConfig.ReflowText.resolveBottomPaddingDp(defaultDp: Float): Float {
    return (extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY]?.toFloatOrNull() ?: defaultDp)
        .takeIf(Float::isFinite)
        ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
        ?: defaultDp.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
}

private fun hitTestTextLink(
    tap: Offset,
    layoutState: TextPageLayoutState?,
    links: List<TextLinkHit>
): DocumentLink? {
    val charOffset = hitTestTextCharOffset(
        tap = tap,
        layoutState = layoutState
    ) ?: return null
    if (links.isEmpty()) return null
    return links.firstOrNull { hit -> charOffset in hit.range }?.link
}

private fun hitTestTextLocator(
    tap: Offset,
    layoutState: TextPageLayoutState?,
    mapping: TextMapping?
): Locator? {
    val charOffset = hitTestTextCharOffset(
        tap = tap,
        layoutState = layoutState
    ) ?: return null
    return mapping?.locatorAt(charOffset)
}

internal fun coerceVisibleTextOffset(
    charOffset: Int,
    visibleTextLength: Int
): Int? {
    if (charOffset < 0 || visibleTextLength < 0) {
        return null
    }
    return charOffset.coerceAtMost(visibleTextLength)
}

private fun hitTestTextCharOffset(
    tap: Offset,
    layoutState: TextPageLayoutState?
): Int? {
    val state = layoutState ?: return null
    val local = tap - state.contentOffset
    val layout = state.layoutResult
    if (layout.lineCount <= 0) {
        return null
    }
    if (local.x < 0f || local.y < 0f) {
        return null
    }
    if (local.x > layout.size.width || local.y > layout.size.height) {
        return null
    }
    val line = layout.getLineForVerticalPosition(local.y)
    val lineLeft = layout.getLineLeft(line)
    val lineRight = layout.getLineRight(line)
    if (local.x < lineLeft || local.x > lineRight) {
        return null
    }
    val rawOffset = layout.getOffsetForPosition(local)
    return coerceVisibleTextOffset(rawOffset, state.visibleTextLength)
}

private fun buildPageTransform(
    mode: PageTurnMode,
    style: com.ireader.feature.reader.presentation.PageTurnStyle,
    direction: PageTurnDirection
): ContentTransform {
    val forward = direction == PageTurnDirection.NEXT
    val durationMs = 220
    return when (resolvePageTurnAnimationKind(mode = mode, style = style)) {
        PageTurnAnimationKind.COVER_OVERLAY -> {
            val enter = fadeIn(
                animationSpec = tween(durationMs)
            ) + scaleIn(
                initialScale = 0.99f,
                animationSpec = tween(durationMs)
            )
            (enter togetherWith ExitTransition.None).apply {
                targetContentZIndex = 1f
            }
        }

        PageTurnAnimationKind.SIMULATION -> {
            val enter = slideInHorizontally(
                animationSpec = tween(durationMs)
            ) { full -> if (forward) full else -full } +
                fadeIn(animationSpec = tween(durationMs / 2)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(durationMs)
                )
            (enter togetherWith ExitTransition.None).apply {
                targetContentZIndex = 1.1f
            }
        }

        PageTurnAnimationKind.NONE -> {
            EnterTransition.None togetherWith ExitTransition.None
        }
    }
}
