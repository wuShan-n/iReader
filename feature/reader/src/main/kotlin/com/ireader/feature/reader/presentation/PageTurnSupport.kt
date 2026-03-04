package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig

enum class PageTurnDirection {
    NEXT,
    PREV
}

data class PageTurnTransition(
    val sequence: Long = 0L,
    val direction: PageTurnDirection = PageTurnDirection.NEXT
) {
    fun next(direction: PageTurnDirection): PageTurnTransition {
        return copy(sequence = sequence + 1L, direction = direction)
    }
}

fun RenderConfig.ReflowText.pageTurnMode(): PageTurnMode {
    return parsePageTurnMode(extra[PAGE_TURN_EXTRA_KEY])
}

fun RenderConfig.ReflowText.withPageTurnMode(mode: PageTurnMode): RenderConfig.ReflowText {
    val style = defaultPageTurnStyle(mode = mode)
    return copy(
        extra = extra +
            (PAGE_TURN_EXTRA_KEY to mode.storageValue) +
            (PAGE_TURN_STYLE_EXTRA_KEY to style.storageValue)
    )
}

enum class PageTurnStyle(
    val storageValue: String,
    val mode: PageTurnMode
) {
    SIMULATION("simulation", PageTurnMode.COVER_HORIZONTAL),
    COVER_OVERLAY("cover_overlay", PageTurnMode.COVER_HORIZONTAL),
    NO_ANIMATION("no_animation", PageTurnMode.COVER_HORIZONTAL)
}

enum class PageTurnAnimationKind {
    COVER_OVERLAY,
    SIMULATION,
    NONE
}

fun RenderConfig.ReflowText.pageTurnStyle(): PageTurnStyle {
    val mode = pageTurnMode()
    return parsePageTurnStyle(
        raw = extra[PAGE_TURN_STYLE_EXTRA_KEY],
        mode = mode
    )
}

fun RenderConfig.ReflowText.withPageTurnStyle(style: PageTurnStyle): RenderConfig.ReflowText {
    return copy(
        extra = extra +
            (PAGE_TURN_EXTRA_KEY to style.mode.storageValue) +
            (PAGE_TURN_STYLE_EXTRA_KEY to style.storageValue)
    )
}

fun defaultPageTurnStyle(mode: PageTurnMode): PageTurnStyle {
    return PageTurnStyle.COVER_OVERLAY
}

fun parsePageTurnStyle(
    raw: String?,
    mode: PageTurnMode
): PageTurnStyle {
    val parsed = when (raw) {
        PageTurnStyle.SIMULATION.storageValue -> PageTurnStyle.SIMULATION
        PageTurnStyle.COVER_OVERLAY.storageValue -> PageTurnStyle.COVER_OVERLAY
        PageTurnStyle.NO_ANIMATION.storageValue -> PageTurnStyle.NO_ANIMATION
        else -> defaultPageTurnStyle(mode = mode)
    }
    return if (parsed.mode == mode) parsed else PageTurnStyle.COVER_OVERLAY
}

fun resolvePageTurnAnimationKind(
    mode: PageTurnMode,
    style: PageTurnStyle
): PageTurnAnimationKind {
    return when (mode) {
        PageTurnMode.COVER_HORIZONTAL -> {
            when (style) {
                PageTurnStyle.NO_ANIMATION -> PageTurnAnimationKind.NONE
                PageTurnStyle.SIMULATION -> PageTurnAnimationKind.SIMULATION
                PageTurnStyle.COVER_OVERLAY -> PageTurnAnimationKind.COVER_OVERLAY
            }
        }
    }
}

fun parsePageTurnMode(raw: String?): PageTurnMode {
    return PageTurnMode.fromStorageValue(raw)
}

fun PageTurnMode.displayLabel(): String {
    return when (this) {
        PageTurnMode.COVER_HORIZONTAL -> "左右覆盖"
    }
}
