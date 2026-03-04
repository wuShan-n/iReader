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
        val ReaderPanelElevatedDay = Color(0xFFFFFCF8)
        val ReaderPanelElevatedNight = Color(0xFF242424)
        val ReaderDividerDay = Color(0x1A000000)
        val ReaderDividerNight = Color(0x29FFFFFF)

        val LibraryBackgroundTopDay = Color(0xFFFBF8F1)
        val LibraryBackgroundBottomDay = Color(0xFFF4EFE6)
        val LibraryBackgroundTopNight = Color(0xFF171715)
        val LibraryBackgroundBottomNight = Color(0xFF10100F)
        val LibraryPillDay = Color(0xFFE9E2D6)
        val LibraryPillNight = Color(0xFF32312F)
        val LibrarySearchDay = Color(0xFFFFFBF6)
        val LibrarySearchNight = Color(0xFF252421)
        val LibraryCardDay = Color(0xFFFFFCF8)
        val LibraryCardNight = Color(0xFF1F1F1F)
        val LibraryDividerDay = Color(0x12000000)
        val LibraryDividerNight = Color(0x29FFFFFF)

        val AccentBlue = Color(0xFF0F6EEC)
        val AccentBlueSoft = Color(0xFFDCE9FF)
        val AccentBlueNight = Color(0xFF9DC3FF)
        val AccentRedSoft = Color(0xFFFFE6E4)
        val AccentRed = Color(0xFFCF4A3A)
        val Success = Color(0xFF23855A)
        val Warning = Color(0xFFBE6A11)
        val SecondaryTextDay = Color(0xFF6F685E)
        val SecondaryTextNight = Color(0xFFA8A39B)
        val Scrim = Color(0x4D000000)

        val PrototypeBlue = Color(0xFF007AFF)
        val PrototypeBlueSoft = Color(0xFFEBF5FF)
        val PrototypeSurface = Color(0xFFF8F9FA)
        val PrototypeSurfaceMuted = Color(0xFFE9ECEF)
        val PrototypeBorder = Color(0xFFDEE2E6)
        val PrototypeTextPrimary = Color(0xFF1A1A1A)
        val PrototypeTextSecondary = Color(0xFF6C757D)
        val PrototypeTextTertiary = Color(0xFFADB5BD)
        val PrototypeDanger = Color(0xFFFF3B30)
    }

    object Border {
        val Hairline = 1.dp
        val Strong = 1.5.dp
    }

    object Shape {
        val BookCoverRadius = 10.dp
        val SurfaceRadius = 16.dp
        val SheetRadius = 24.dp
        val InputRadius = 18.dp
        val CapsuleRadius = 999.dp
        val TopBarRadius = 18.dp
        val BottomBarRadius = 20.dp
        val ActionRadius = 12.dp
        val CardRadius = 14.dp
        val PrototypeSheetTop = 20.dp
        val PrototypeCard = 8.dp
        val PrototypeActionTile = 16.dp
    }

    object Space {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 20.dp
        val Xxl = 24.dp
    }

    object Motion {
        const val Fast = 160
        const val Medium = 240
        const val Slow = 320
        const val SheetEnter = 280
        const val SheetExit = 180
        const val ChromeIn = 220
        const val ChromeOut = 170
        const val StaggerFast = 70
    }
}
