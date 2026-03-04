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
    SCROLL_VERTICAL("scroll_vertical", PageTurnMode.SCROLL_VERTICAL),
    NO_ANIMATION("no_animation", PageTurnMode.COVER_HORIZONTAL)
}

enum class PageTurnAnimationKind {
    COVER_OVERLAY,
    SCROLL_VERTICAL,
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
    return when (mode) {
        PageTurnMode.COVER_HORIZONTAL -> PageTurnStyle.COVER_OVERLAY
        PageTurnMode.SCROLL_VERTICAL -> PageTurnStyle.SCROLL_VERTICAL
    }
}

fun parsePageTurnStyle(
    raw: String?,
    mode: PageTurnMode
): PageTurnStyle {
    val parsed = when (raw) {
        PageTurnStyle.SIMULATION.storageValue,
        "仿真翻页" -> PageTurnStyle.SIMULATION

        PageTurnStyle.COVER_OVERLAY.storageValue,
        "左右覆盖",
        PageTurnMode.COVER_HORIZONTAL.storageValue -> PageTurnStyle.COVER_OVERLAY

        PageTurnStyle.SCROLL_VERTICAL.storageValue,
        "上下滑动",
        "上下滚动",
        PageTurnMode.SCROLL_VERTICAL.storageValue -> PageTurnStyle.SCROLL_VERTICAL

        PageTurnStyle.NO_ANIMATION.storageValue,
        "无动效" -> PageTurnStyle.NO_ANIMATION

        null -> defaultPageTurnStyle(mode = mode)
        else -> defaultPageTurnStyle(mode = mode)
    }
    return if (parsed.mode == mode) parsed else defaultPageTurnStyle(mode = mode)
}

fun resolvePageTurnAnimationKind(
    mode: PageTurnMode,
    style: PageTurnStyle
): PageTurnAnimationKind {
    return when (mode) {
        PageTurnMode.SCROLL_VERTICAL -> PageTurnAnimationKind.SCROLL_VERTICAL
        PageTurnMode.COVER_HORIZONTAL -> {
            when (style) {
                PageTurnStyle.NO_ANIMATION -> PageTurnAnimationKind.NONE
                PageTurnStyle.SIMULATION -> PageTurnAnimationKind.SIMULATION
                PageTurnStyle.COVER_OVERLAY -> PageTurnAnimationKind.COVER_OVERLAY
                PageTurnStyle.SCROLL_VERTICAL -> PageTurnAnimationKind.COVER_OVERLAY
            }
        }
    }
}

fun parsePageTurnMode(raw: String?): PageTurnMode {
    return when (raw) {
        PageTurnMode.SCROLL_VERTICAL.storageValue,
        "上下滑动",
        "上下滚动" -> PageTurnMode.SCROLL_VERTICAL

        PageTurnMode.COVER_HORIZONTAL.storageValue,
        "左右覆盖",
        "仿真翻页",
        "无动效",
        null -> PageTurnMode.COVER_HORIZONTAL

        else -> PageTurnMode.fromStorageValue(raw)
    }
}

fun PageTurnMode.displayLabel(): String {
    return when (this) {
        PageTurnMode.COVER_HORIZONTAL -> "左右覆盖"
        PageTurnMode.SCROLL_VERTICAL -> "上下滚动"
    }
}
