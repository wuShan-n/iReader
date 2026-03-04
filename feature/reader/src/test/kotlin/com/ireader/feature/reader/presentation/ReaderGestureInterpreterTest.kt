package com.ireader.feature.reader.presentation

import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.TapZonePreset
import com.ireader.reader.api.render.PageTurnMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderGestureInterpreterTest {

    private val interpreter = ReaderGestureInterpreter()

    @Test
    fun `classic tap zones should resolve prev center and next`() {
        val prefs = ReaderDisplayPrefs(
            tapZonePreset = TapZonePreset.CLASSIC_3_ZONE,
            preventAccidentalTurn = false
        )

        assertEquals(
            ReaderTapAction.PREV,
            interpreter.resolveTapAction(xPx = 100f, viewportWidthPx = 1080, prefs = prefs)
        )
        assertEquals(
            ReaderTapAction.CENTER,
            interpreter.resolveTapAction(xPx = 540f, viewportWidthPx = 1080, prefs = prefs)
        )
        assertEquals(
            ReaderTapAction.NEXT,
            interpreter.resolveTapAction(xPx = 980f, viewportWidthPx = 1080, prefs = prefs)
        )
    }

    @Test
    fun `left hand preset should mirror tap actions`() {
        val prefs = ReaderDisplayPrefs(
            tapZonePreset = TapZonePreset.LEFT_HAND,
            preventAccidentalTurn = false
        )

        assertEquals(
            ReaderTapAction.NEXT,
            interpreter.resolveTapAction(xPx = 100f, viewportWidthPx = 1080, prefs = prefs)
        )
        assertEquals(
            ReaderTapAction.PREV,
            interpreter.resolveTapAction(xPx = 920f, viewportWidthPx = 1080, prefs = prefs)
        )
    }

    @Test
    fun `edge guard should ignore near-edge taps when accidental prevention is enabled`() {
        val prefs = ReaderDisplayPrefs(preventAccidentalTurn = true)

        assertEquals(
            ReaderTapAction.NONE,
            interpreter.resolveTapAction(xPx = 1f, viewportWidthPx = 1080, prefs = prefs)
        )
    }

    @Test
    fun `drag should resolve direction based on mode and threshold`() {
        val prefs = ReaderDisplayPrefs(preventAccidentalTurn = true)

        assertEquals(
            PageTurnDirection.NEXT,
            interpreter.resolveDragDirection(
                axis = GestureAxis.HORIZONTAL,
                deltaPx = -220f,
                viewportMainAxisPx = 1080,
                pageTurnMode = PageTurnMode.COVER_HORIZONTAL,
                prefs = prefs
            )
        )
        assertNull(
            interpreter.resolveDragDirection(
                axis = GestureAxis.HORIZONTAL,
                deltaPx = -80f,
                viewportMainAxisPx = 1080,
                pageTurnMode = PageTurnMode.COVER_HORIZONTAL,
                prefs = prefs
            )
        )
        assertNull(
            interpreter.resolveDragDirection(
                axis = GestureAxis.VERTICAL,
                deltaPx = -260f,
                viewportMainAxisPx = 1080,
                pageTurnMode = PageTurnMode.COVER_HORIZONTAL,
                prefs = prefs
            )
        )
    }
}
