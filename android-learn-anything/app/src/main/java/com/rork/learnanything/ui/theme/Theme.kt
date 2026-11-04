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
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
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

// Cool, crisp, premium, and technology-forward.
private val Light = LAColors(
    canvas = Color(0xFFF3F5EA), surface = Color(0xFFFFFDF5), surfaceAlt = Color(0xFFE8EEDC), border = Color(0xFFD6DFC9),
    ink = Color(0xFF20352A), inkSoft = Color(0xFF657568), inkFaint = Color(0xFF9AA58F),
    hero = Color(0xFF20352A), onHero = Color(0xFFF8F6E9),
    coral = Color(0xFF4F8058), coralSoft = Color(0xFFE4EEDB),
    cobalt = Color(0xFF78965D), cobaltSoft = Color(0xFFE8EFDD),
    success = Color(0xFF477A4C), successSoft = Color(0xFFE2EEDC),
    amber = Color(0xFFB4874B), amberSoft = Color(0xFFF4EBD7),
    isDark = false,
)

private val Dark = LAColors(
    canvas = Color(0xFF101B16), surface = Color(0xFF1B2B21), surfaceAlt = Color(0xFF26392B), border = Color(0xFF3B4D3D),
    ink = Color(0xFFF3F5EA), inkSoft = Color(0xFFB7C5B1), inkFaint = Color(0xFF82917E),
    hero = Color(0xFF203529), onHero = Color(0xFFF3F5EA),
    coral = Color(0xFF9BBE83), coralSoft = Color(0xFF304832),
    cobalt = Color(0xFFB5CE8D), cobaltSoft = Color(0xFF344A35),
    success = Color(0xFF78B77A), successSoft = Color(0xFF253D2A),
    amber = Color(0xFFD7B46C), amberSoft = Color(0xFF453A25),
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
        "math" -> Triple("Math", if (dark) Color(0xFF9DA4FF) else Color(0xFF5963E8), Icons.Rounded.Functions)
        "programming" -> Triple("Programming", if (dark) Color(0xFF62DCD0) else Color(0xFF0B8F83), Icons.Rounded.Code)
        "mechanics" -> Triple("Mechanics", if (dark) Color(0xFFFF8C72) else Color(0xFFE45A3C), Icons.Rounded.Build)
        "science" -> Triple("Science", if (dark) Color(0xFF72D89D) else Color(0xFF238B60), Icons.Rounded.Science)
        "history" -> Triple("History", if (dark) Color(0xFFE6BE73) else Color(0xFF9B6B25), Icons.Rounded.HistoryEdu)
        "business" -> Triple("Business", if (dark) Color(0xFF82D1AA) else Color(0xFF257A58), Icons.AutoMirrored.Rounded.TrendingUp)
        "language" -> Triple("Languages", if (dark) Color(0xFFFF9DC0) else Color(0xFFC52A68), Icons.Rounded.Translate)
        "music" -> Triple("Music", if (dark) Color(0xFFC2A8F0) else Color(0xFF7048BD), Icons.Rounded.MusicNote)
        "sports" -> Triple("Sports", if (dark) Color(0xFF8EDBFF) else Color(0xFF087BAF), Icons.Rounded.SportsSoccer)
        "mind" -> Triple("Mind & Career", if (dark) Color(0xFFD8A0E0) else Color(0xFF9346A2), Icons.Rounded.Psychology)
        "geography" -> Triple("Geography", if (dark) Color(0xFF83D5CE) else Color(0xFF287F7C), Icons.Rounded.Public)
        else -> Triple("General", if (dark) Color(0xFF9DA4FF) else Color(0xFF5963E8), Icons.Rounded.AutoAwesome)
    }
    return WorldStyle(name, accent, soft(accent), icon)
}

val OverlineStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
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

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

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
        secondary = c.cobalt, onSecondary = Color.White, secondaryContainer = c.cobaltSoft, onSecondaryContainer = c.cobalt,
        tertiary = c.success, background = c.canvas, onBackground = c.ink, surface = c.canvas, onSurface = c.ink,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.inkSoft, outline = c.border, outlineVariant = c.border,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceAlt,
        surfaceContainerHighest = c.surfaceAlt, surfaceContainerLowest = c.canvas, error = Color(0xFFFF6B6B),
    ) else lightColorScheme(
        primary = c.coral, onPrimary = Color.White, primaryContainer = c.coralSoft, onPrimaryContainer = Color(0xFF31543A),
        secondary = c.cobalt, onSecondary = Color.White, secondaryContainer = c.cobaltSoft, onSecondaryContainer = Color(0xFF4C6540),
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
        MaterialTheme(colorScheme = scheme, typography = typography(), shapes = AppShapes, content = content)
    }
}
