package com.gliffy.g2048.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Classic look-and-feel: warm cream page, sandy board, and the
 * standard 2048 tile palette. Dark mode: charcoal-blue variants.
 * All animation/easing specs live here so the board canvas can
 * reuse the same curves the headers/buttons use.
 */

object Palette {
    // light
    val pageLight = Color(0xFFFAF8EF)
    val boardLight = Color(0xFFBBADA0)
    val emptyLight = Color(0x55CDC1B4)
    val textLight = Color(0xFF776E65)
    val textSubLight = Color(0xFF8F8377)
    val scoreBoxLight = Color(0xFFBBADA0)
    val scoreTextLight = Color(0xFFFFFFFF)
    val scoreLblLight = Color(0xFFEEE4DA)
    val btnLight = Color(0xFF8F7A66)
    val btnTextLight = Color(0xFFF7F0E5)

    // dark
    val pageDark = Color(0xFF15151F)
    val boardDark = Color(0xFF232433)
    val emptyDark = Color(0x662D2E42)
    val textDark = Color(0xFFECE9F4)
    val textSubDark = Color(0xFF8D8AA4)
    val scoreBoxDark = Color(0xFF2E2F44)
    val scoreTextDark = Color(0xFFFFFFFF)
    val scoreLblDark = Color(0xFFB9B6CC)
    val btnDark = Color(0xFFEFE3C1)
    val btnTextDark = Color(0xFF2A2416)

    // tile background / label text (identical in both themes; classic palette)
    val tileStops: Map<Int, Pair<Color, Color>> = mapOf(
        2 to (Color(0xFFEEE4DA) to Color(0xFF776E65)),
        4 to (Color(0xFFEDE0C8) to Color(0xFF776E65)),
        8 to (Color(0xFFF2B179) to Color(0xFFFFFFFF)),
        16 to (Color(0xFFF59563) to Color(0xFFFFFFFF)),
        32 to (Color(0xFFF67C5F) to Color(0xFFFFFFFF)),
        64 to (Color(0xFFF65E3B) to Color(0xFFFFFFFF)),
        128 to (Color(0xFFEDCF72) to Color(0xFFFFFFFF)),
        256 to (Color(0xFFEDCC61) to Color(0xFFFFFFFF)),
        512 to (Color(0xFFEDC850) to Color(0xFFFFFFFF)),
        1024 to (Color(0xFFEDC53F) to Color(0xFFFFFFFF)),
        2048 to (Color(0xFFEDC22E) to Color(0xFFFFFFFF)),
    )
    val tileOther: Pair<Color, Color> = Color(0xFF3C3A32) to Color(0xFFFFFFFF)
    fun tileOf(v: Int): Pair<Color, Color> = tileStops[v] ?: tileOther
}

private fun lightScheme() = lightColorScheme(
    primary = Color(0xFF8F7A66),
    onPrimary = Color(0xFFF7F0E5),
    secondary = Color(0xFF776E65),
    onSecondary = Color(0xFFFFFFFF),
    background = Palette.pageLight,
    onBackground = Palette.textLight,
    surface = Palette.pageLight,
    onSurface = Palette.textLight,
    surfaceVariant = Color(0xFFCDC1B4),
    onSurfaceVariant = Palette.textLight,
    outline = Color(0xFF776E65).copy(alpha = 0.12f),
)

@Suppress("UnusedPrivateMember")
private fun darkScheme() = darkColorScheme(
    primary = Palette.btnDark,
    onPrimary = Palette.btnTextDark,
    secondary = Color(0xFFB9B6CC),
    onSecondary = Color(0xFF15151F),
    background = Palette.pageDark,
    onBackground = Palette.textDark,
    surface = Palette.pageDark,
    onSurface = Palette.textDark,
    surfaceVariant = Color(0xFF2E2F44),
    onSurfaceVariant = Palette.textDark,
    outline = Color.White.copy(alpha = 0.12f),
)

@Composable
fun GameTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = remember(dark) { if (dark) darkScheme() else lightScheme() }
    MaterialTheme(
        colorScheme = scheme,
        typography = gameTypography(),
        content = content,
    )
}

private fun gameTypography() = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.W600, fontSize = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.W600, fontSize = 13.sp),
)

object Easing {
    val slide = FastOutSlowInEasing
    val pop = FastOutSlowInEasing
    val overlay = LinearOutSlowInEasing
}
