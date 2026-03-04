package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
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
    return copy(extra = extra + (PAGE_TURN_EXTRA_KEY to mode.storageValue))
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
