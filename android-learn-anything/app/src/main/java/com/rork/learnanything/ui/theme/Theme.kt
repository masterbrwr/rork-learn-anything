package com.rork.learnanything.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rork.learnanything.R

val Serif = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_bold, FontWeight.Bold),
)

@Immutable
data class LAColors(
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val hero: Color,
    val onHero: Color,
    val coral: Color,
    val coralSoft: Color,
    val cobalt: Color,
    val cobaltSoft: Color,
    val success: Color,
    val successSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val isDark: Boolean,
)

private val Light = LAColors(
    canvas = Color(0xFFF7F4EE), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF0ECE4), border = Color(0xFFE6E0D5),
    ink = Color(0xFF14171F), inkSoft = Color(0xFF5F6675), inkFaint = Color(0xFF9AA0AB),
    hero = Color(0xFF101828), onHero = Color(0xFFF7F4EE),
    coral = Color(0xFFF2553A), coralSoft = Color(0xFFFDE7E1),
    cobalt = Color(0xFF2F4BDB), cobaltSoft = Color(0xFFE6EBFC),
    success = Color(0xFF2E8B57), successSoft = Color(0xFFE2F2E8),
    amber = Color(0xFFD9901A), amberSoft = Color(0xFFFCF0D9),
    isDark = false,
)

private val Dark = LAColors(
    canvas = Color(0xFF0E1117), surface = Color(0xFF171B23), surfaceAlt = Color(0xFF1F2430), border = Color(0xFF2A303C),
    ink = Color(0xFFF2EFE8), inkSoft = Color(0xFFA3AAB8), inkFaint = Color(0xFF6B7280),
    hero = Color(0xFF1A2236), onHero = Color(0xFFF2EFE8),
    coral = Color(0xFFFF6A4D), coralSoft = Color(0xFF3A1F1A),
    cobalt = Color(0xFF8197FF), cobaltSoft = Color(0xFF1D2442),
    success = Color(0xFF4CB97A), successSoft = Color(0xFF16291F),
    amber = Color(0xFFF0B04A), amberSoft = Color(0xFF33281A),
    isDark = true,
)

val LocalLAColors = staticCompositionLocalOf { Light }

object LA {
    val colors: LAColors @Composable get() = LocalLAColors.current
}

@Immutable
data class WorldStyle(val name: String, val accent: Color, val soft: Color, val icon: ImageVector)

@Composable
fun worldStyle(world: String): WorldStyle {
    val dark = LA.colors.isDark
    fun soft(c: Color) = if (dark) c.copy(alpha = 0.18f) else c.copy(alpha = 0.12f)
    val (name, accent, icon) = when (world) {
        "math" -> Triple("Math", Color(0xFF3D5AFE).let { if (dark) Color(0xFF8C9EFF) else it }, Icons.Rounded.Functions)
        "programming" -> Triple("Programming", if (dark) Color(0xFF4DD0C4) else Color(0xFF0E8577), Icons.Rounded.Code)
        "mechanics" -> Triple("Mechanics", if (dark) Color(0xFFFF7A59) else Color(0xFFE0492C), Icons.Rounded.Build)
        "science" -> Triple("Science", if (dark) Color(0xFF6FCF97) else Color(0xFF23885A), Icons.Rounded.Science)
        "history" -> Triple("History", if (dark) Color(0xFFE0B26B) else Color(0xFF9A6A22), Icons.Rounded.HistoryEdu)
        "business" -> Triple("Business", if (dark) Color(0xFF7BC8A4) else Color(0xFF1F6E4E), Icons.AutoMirrored.Rounded.TrendingUp)
        "language" -> Triple("Languages", if (dark) Color(0xFFF48FB1) else Color(0xFFC2185B), Icons.Rounded.Translate)
        "music" -> Triple("Music", if (dark) Color(0xFFB39DDB) else Color(0xFF6A3FB5), Icons.Rounded.MusicNote)
        "sports" -> Triple("Sports", if (dark) Color(0xFF81D4FA) else Color(0xFF0277BD), Icons.Rounded.SportsSoccer)
        "mind" -> Triple("Mind & Career", if (dark) Color(0xFFCE93D8) else Color(0xFF8E3A9E), Icons.Rounded.Psychology)
        "geography" -> Triple("Geography", if (dark) Color(0xFF80CBC4) else Color(0xFF2B7A78), Icons.Rounded.Public)
        else -> Triple("General", if (dark) Color(0xFF8197FF) else Color(0xFF2F4BDB), Icons.Rounded.AutoAwesome)
    }
    return WorldStyle(name, accent, soft(accent), icon)
}

/** Small uppercase label used above sections and metrics. */
val OverlineStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)

/** Tabular figures so numbers don't jitter as they change. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
        headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
        headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    )
}

@Composable
fun AppTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = c.coral, onPrimary = Color.White, primaryContainer = c.coralSoft, onPrimaryContainer = c.coral,
        secondary = c.cobalt, onSecondary = Color.White, secondaryContainer = c.coralSoft, onSecondaryContainer = c.coral,
        tertiary = c.success, background = c.canvas, onBackground = c.ink, surface = c.canvas, onSurface = c.ink,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.inkSoft, outline = c.border, outlineVariant = c.border,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceAlt,
        surfaceContainerHighest = c.surfaceAlt, surfaceContainerLowest = c.canvas, error = Color(0xFFFF6B6B),
    ) else lightColorScheme(
        primary = c.coral, onPrimary = Color.White, primaryContainer = c.coralSoft, onPrimaryContainer = Color(0xFF8A2512),
        secondary = c.cobalt, onSecondary = Color.White, secondaryContainer = c.coralSoft, onSecondaryContainer = Color(0xFF8A2512),
        tertiary = c.success, background = c.canvas, onBackground = c.ink, surface = c.canvas, onSurface = c.ink,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.inkSoft, outline = c.border, outlineVariant = c.border,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceAlt,
        surfaceContainerHighest = c.surfaceAlt, surfaceContainerLowest = Color.White, error = Color(0xFFC62828),
    )
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalLAColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
