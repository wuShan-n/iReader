package com.ireader.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ireader.core.designsystem.ReaderTokens.Shape.ActionRadius
import com.ireader.core.designsystem.ReaderTokens.Shape.BottomBarRadius
import com.ireader.core.designsystem.ReaderTokens.Shape.SurfaceRadius

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

private val IReaderSansFamily = FontFamily(
    Font(R.font.source_han_sans_sc_var, weight = FontWeight.Normal),
    Font(R.font.source_han_sans_sc_var, weight = FontWeight.Medium),
    Font(R.font.source_han_sans_sc_var, weight = FontWeight.SemiBold)
)

private val IReaderSerifFamily = FontFamily(
    Font(R.font.source_han_serif_sc_var, weight = FontWeight.Normal),
    Font(R.font.source_han_serif_sc_var, weight = FontWeight.Medium),
    Font(R.font.source_han_serif_sc_var, weight = FontWeight.SemiBold)
)

private val IReaderTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = IReaderSerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = IReaderSerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = IReaderSerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 29.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = IReaderSansFamily,
        fontWeight = FontWeight.Medium,
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
            small = RoundedCornerShape(ActionRadius),
            medium = RoundedCornerShape(SurfaceRadius),
            large = RoundedCornerShape(BottomBarRadius),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
