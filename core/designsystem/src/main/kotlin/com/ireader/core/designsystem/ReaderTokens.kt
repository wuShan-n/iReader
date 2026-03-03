package com.ireader.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ReaderTokens {
    object Palette {
        val ReaderBackgroundDay = Color(0xFFFDF9F3)
        val ReaderBackgroundNight = Color(0xFF131313)
        val ReaderTextDay = Color(0xFF2D2A26)
        val ReaderTextNight = Color(0xFFBEB9B0)
        val ReaderPanelDay = Color(0xFFF6F2EA)
        val ReaderPanelNight = Color(0xFF1F1F1F)
        val AccentBlue = Color(0xFF0F6EEC)
        val AccentBlueSoft = Color(0xFFDCE9FF)
        val Success = Color(0xFF23855A)
        val Warning = Color(0xFFBE6A11)
    }

    object Shape {
        val BookCoverRadius = 10.dp
        val SurfaceRadius = 16.dp
        val CapsuleRadius = 999.dp
    }

    object Motion {
        const val Fast = 140
        const val Medium = 220
        const val Slow = 320
    }
}
