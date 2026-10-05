package vn.lucbao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.lucbao.R

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

/** Fonts the user can pick in Settings (all bundled files are static and cover Vietnamese). */
val Roboto = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold),
)

val BeVietnamPro = FontFamily(
    Font(R.font.bevietnam_regular, FontWeight.Normal),
    Font(R.font.bevietnam_medium, FontWeight.Medium),
    Font(R.font.bevietnam_semibold, FontWeight.SemiBold),
    Font(R.font.bevietnam_bold, FontWeight.Bold),
)

val NotoSans = FontFamily(
    Font(R.font.notosans_regular, FontWeight.Normal),
    Font(R.font.notosans_medium, FontWeight.Medium),
    Font(R.font.notosans_semibold, FontWeight.SemiBold),
    Font(R.font.notosans_bold, FontWeight.Bold),
)

/** Index matches [vn.lucbao.data.Settings.font]. */
val FontChoices: List<Pair<String, FontFamily>> = listOf(
    "Roboto" to Roboto,
    "Phông của máy" to FontFamily.Default,
    "Be Vietnam Pro" to BeVietnamPro,
    "Noto Sans" to NotoSans,
)

fun fontFamilyFor(choice: Int): FontFamily = FontChoices.getOrNull(choice)?.second ?: Roboto

private val base = Typography()

/**
 * Keeps the font's top/bottom padding and does not trim line height: without this,
 * stacked Vietnamese accents (Ể, Ặ, Ỗ…) get cut off at the top of a line on some phones.
 */
@OptIn(ExperimentalTextApi::class)
@Suppress("DEPRECATION")
private fun TextStyle.styled(family: FontFamily, weight: FontWeight? = null, size: Float? = null) = copy(
    fontFamily = family,
    fontWeight = weight ?: fontWeight,
    fontSize = size?.sp ?: fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

fun lucTypography(family: FontFamily) = Typography(
    displayLarge = base.displayLarge.styled(family),
    displayMedium = base.displayMedium.styled(family),
    displaySmall = base.displaySmall.styled(family, FontWeight.Bold, 32f),
    headlineLarge = base.headlineLarge.styled(family),
    headlineMedium = base.headlineMedium.styled(family),
    headlineSmall = base.headlineSmall.styled(family, FontWeight.Bold, 22f),
    titleLarge = base.titleLarge.styled(family, FontWeight.Bold, 20f),
    titleMedium = base.titleMedium.styled(family, FontWeight.SemiBold, 17f),
    titleSmall = base.titleSmall.styled(family, FontWeight.SemiBold, 14f),
    bodyLarge = base.bodyLarge.styled(family, size = 15f),
    bodyMedium = base.bodyMedium.styled(family, size = 13f),
    bodySmall = base.bodySmall.styled(family, size = 11.5f),
    labelLarge = base.labelLarge.styled(family),
    labelMedium = base.labelMedium.styled(family),
    labelSmall = base.labelSmall.styled(family),
)

@Composable
fun LucTheme(dark: Boolean, font: Int = 0, content: @Composable () -> Unit) {
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
        val typography = remember(font) { lucTypography(fontFamilyFor(font)) }
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
