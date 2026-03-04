package com.ireader.feature.reader.presentation

import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.TapZonePreset
import com.ireader.reader.api.render.PageTurnMode
import kotlin.math.max

internal enum class ReaderTapAction {
    PREV,
    NEXT,
    CENTER,
    NONE
}

internal class ReaderGestureInterpreter {

    fun resolveTapAction(
        xPx: Float,
        viewportWidthPx: Int,
        prefs: ReaderDisplayPrefs
    ): ReaderTapAction {
        val width = viewportWidthPx.coerceAtLeast(1).toFloat()
        val x = xPx.coerceIn(0f, width)
        val edgeGuard = if (prefs.preventAccidentalTurn) width * 0.025f else 0f
        if (x <= edgeGuard || x >= width - edgeGuard) {
            return ReaderTapAction.NONE
        }

        val profile = tapZoneProfile(prefs.tapZonePreset)
        val leftEdge = width * profile.leftRatio
        val rightEdge = width * profile.rightRatio
        return when {
            x <= leftEdge -> profile.leftAction
            x >= rightEdge -> profile.rightAction
            else -> ReaderTapAction.CENTER
        }
    }

    fun resolveDragDirection(
        axis: GestureAxis,
        deltaPx: Float,
        viewportMainAxisPx: Int,
        pageTurnMode: PageTurnMode,
        prefs: ReaderDisplayPrefs
    ): PageTurnDirection? {
        if (!isMatchingAxis(axis = axis, pageTurnMode = pageTurnMode)) {
            return null
        }
        val threshold = dragThresholdPx(
            pageTurnMode = pageTurnMode,
            viewportMainAxisPx = viewportMainAxisPx,
            preventAccidentalTurn = prefs.preventAccidentalTurn
        )
        return when {
            deltaPx <= -threshold -> PageTurnDirection.NEXT
            deltaPx >= threshold -> PageTurnDirection.PREV
            else -> null
        }
    }

    private fun isMatchingAxis(axis: GestureAxis, pageTurnMode: PageTurnMode): Boolean {
        return when (pageTurnMode) {
            PageTurnMode.COVER_HORIZONTAL -> axis == GestureAxis.HORIZONTAL
        }
    }

    private fun dragThresholdPx(
        pageTurnMode: PageTurnMode,
        viewportMainAxisPx: Int,
        preventAccidentalTurn: Boolean
    ): Float {
        val viewport = viewportMainAxisPx.coerceAtLeast(1).toFloat()
        return when (pageTurnMode) {
            PageTurnMode.COVER_HORIZONTAL -> {
                val ratio = if (preventAccidentalTurn) 0.16f else 0.12f
                val minPx = if (preventAccidentalTurn) 68f else 52f
                max(viewport * ratio, minPx)
            }
        }
    }

    private fun tapZoneProfile(preset: TapZonePreset): TapZoneProfile {
        return when (preset) {
            TapZonePreset.CLASSIC_3_ZONE -> TapZoneProfile(
                leftRatio = 0.30f,
                rightRatio = 0.70f,
                leftAction = ReaderTapAction.PREV,
                rightAction = ReaderTapAction.NEXT
            )

            TapZonePreset.SAFE_CENTER -> TapZoneProfile(
                leftRatio = 0.22f,
                rightRatio = 0.78f,
                leftAction = ReaderTapAction.PREV,
                rightAction = ReaderTapAction.NEXT
            )

            TapZonePreset.LEFT_HAND -> TapZoneProfile(
                leftRatio = 0.28f,
                rightRatio = 0.58f,
                leftAction = ReaderTapAction.NEXT,
                rightAction = ReaderTapAction.PREV
            )

            TapZonePreset.RIGHT_HAND -> TapZoneProfile(
                leftRatio = 0.42f,
                rightRatio = 0.72f,
                leftAction = ReaderTapAction.PREV,
                rightAction = ReaderTapAction.NEXT
            )
        }
    }

    private data class TapZoneProfile(
        val leftRatio: Float,
        val rightRatio: Float,
        val leftAction: ReaderTapAction,
        val rightAction: ReaderTapAction
    )
}
