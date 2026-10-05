package vn.lucbao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class LucColors(
    val surface: Color,
    val surface2: Color,
    val card: Color,
    val line: Color,
    val primary: Color,
    val onPrimary: Color,
    val gold: Color,
    val text: Color,
    val muted: Color,
    val isDark: Boolean,
)

/** "Rừng đêm" */
val DarkLuc = LucColors(
    surface = Color(0xFF0C241B),
    surface2 = Color(0xFF123326),
    card = Color(0xFF173D2E),
    line = Color(0x1FA0E6BE),
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF04140D),
    gold = Color(0xFFE9C46A),
    text = Color(0xFFE8F5EE),
    muted = Color(0xFF93B5A4),
    isDark = true,
)

/** "Lá non" */
val LightLuc = LucColors(
    surface = Color(0xFFF5FAF5),
    surface2 = Color(0xFFE6F1E8),
    card = Color(0xFFFFFFFF),
    line = Color(0x1F145032),
    primary = Color(0xFF1F8A5B),
    onPrimary = Color(0xFFFFFFFF),
    gold = Color(0xFFB8862B),
    text = Color(0xFF10261C),
    muted = Color(0xFF5C7A6A),
    isDark = false,
)

val LocalLuc = staticCompositionLocalOf { DarkLuc }

object Luc {
    val colors: LucColors
        @Composable get() = LocalLuc.current
}

// The phone's own system font is used everywhere (custom fonts rendered badly on some
// phones). Only the "Lục Bảo" wordmark keeps its look, drawn as an image (Wordmark.kt).
private val base = Typography()

val LucTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 13.sp),
    bodySmall = base.bodySmall.copy(fontSize = 11.5.sp),
)

@Composable
fun LucTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkLuc else LightLuc
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            secondary = c.gold, onSecondary = c.onPrimary,
            background = c.surface, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            surfaceContainer = c.card, surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.card, surfaceContainerLow = c.surface2,
            outline = c.line, outlineVariant = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            secondary = c.gold, onSecondary = c.onPrimary,
            background = c.surface, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            surfaceContainer = c.card, surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.card, surfaceContainerLow = c.surface2,
            outline = c.line, outlineVariant = c.line,
        )
    }
    CompositionLocalProvider(LocalLuc provides c) {
        MaterialTheme(colorScheme = scheme, typography = LucTypography, content = content)
    }
}
