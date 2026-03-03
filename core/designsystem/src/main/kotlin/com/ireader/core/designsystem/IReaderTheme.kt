package com.ireader.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val IReaderLightColorScheme = lightColorScheme(
    primary = Color(0xFF0F6EEC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF001D4F),
    secondary = Color(0xFF44536B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF1E1E1B),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF22211F),
    surfaceVariant = Color(0xFFE7E2D8),
    onSurfaceVariant = Color(0xFF4C473F),
    outline = Color(0xFF9A9387)
)

private val IReaderDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DC3FF),
    onPrimary = Color(0xFF00377F),
    primaryContainer = Color(0xFF004AAB),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFFB8C7E3),
    onSecondary = Color(0xFF1D3149),
    background = Color(0xFF161715),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF20211E),
    onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF3A3A37),
    onSurfaceVariant = Color(0xFFC9C3B9),
    outline = Color(0xFF928B80)
)

private val IReaderTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
@Suppress("FunctionNaming")
fun IReaderTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        IReaderDarkColorScheme
    } else {
        IReaderLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = IReaderTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
