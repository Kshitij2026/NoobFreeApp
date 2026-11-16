package com.noobfree.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * NoobFree v1.2.0 Glassmorphism Color Palette.
 * Deep cosmic navy/indigo base with electric cyan (#00E5FF) and neon violet (#A855F7) accents.
 */
object GlassPalette {
    val DeepNavyTop = Color(0xFF060B19)
    val DeepIndigoMid = Color(0xFF0F172A)
    val CosmicPurpleBottom = Color(0xFF1A103C)
    val CyanGlow = Color(0xFF00E5FF)
    val VioletGlow = Color(0xFFA855F7)
    val EmeraldGlow = Color(0xFF10B981)
    val AmberGlow = Color(0xFFF59E0B)
    val RoseGlow = Color(0xFFF43F5E)

    val FrostedCardTop = Color(0x24FFFFFF)
    val FrostedCardBottom = Color(0x0DFFFFFF)
    val FrostedSurfaceBase = Color(0xB30F172A)
    val SpecularBorderTop = Color(0x4DFFFFFF)
    val SpecularBorderCyan = Color(0x4000E5FF)
    val SpecularBorderViolet = Color(0x33A855F7)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

private val GlassDarkColorScheme = darkColorScheme(
    primary = GlassPalette.CyanGlow,
    onPrimary = Color(0xFF002A32),
    primaryContainer = Color(0x3300E5FF),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = GlassPalette.VioletGlow,
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0x33A855F7),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = GlassPalette.EmeraldGlow,
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = Color(0x3310B981),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = Color.Transparent,
    surface = GlassPalette.FrostedSurfaceBase,
    surfaceVariant = Color(0x661E293B),
    onBackground = GlassPalette.TextPrimary,
    onSurface = GlassPalette.TextPrimary,
    onSurfaceVariant = GlassPalette.TextSecondary,
    outline = Color(0x4D94A3B8),
    error = GlassPalette.RoseGlow,
    errorContainer = Color(0x4DF43F5E),
    onErrorContainer = Color(0xFFFFE4E6)
)

private val GlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFF059669),
    background = Color.Transparent,
    surface = Color(0xD9FFFFFF),
    surfaceVariant = Color(0xB3E2E8F0),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    error = Color(0xFFE11D48),
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

val NoobFreeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = 0.6.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
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
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun NoobFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use our curated GlassColorScheme so dynamic Material You wallpapers never override the glassmorphism aesthetic
    val colorScheme = if (darkTheme) GlassDarkColorScheme else GlassLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NoobFreeTypography,
        content = content
    )
}
