package app.fouru.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Misma paleta que el escritorio (desktop/src/styles.css).
object FourUColors {
    val Bg = Color(0xFF0B0B0E)
    val Surface = Color(0xFF141418)
    val Surface2 = Color(0xFF1C1C22)
    val Line = Color(0xFF26262E)
    val Text = Color(0xFFF2F2F5)
    val Muted = Color(0xFF8D8D98)
    val Accent = Color(0xFFE84A6F)
    val AccentSoft = Color(0x24E84A6F)
    val Danger = Color(0xFFFF6B6B)
}

private val scheme = darkColorScheme(
    primary = FourUColors.Accent,
    onPrimary = Color.White,
    primaryContainer = FourUColors.AccentSoft,
    onPrimaryContainer = FourUColors.Accent,
    secondaryContainer = FourUColors.AccentSoft,
    onSecondaryContainer = FourUColors.Accent,
    background = FourUColors.Bg,
    onBackground = FourUColors.Text,
    surface = FourUColors.Bg,
    onSurface = FourUColors.Text,
    surfaceVariant = FourUColors.Surface2,
    onSurfaceVariant = FourUColors.Muted,
    surfaceContainer = FourUColors.Surface,
    surfaceContainerHigh = FourUColors.Surface2,
    surfaceContainerHighest = FourUColors.Surface2,
    surfaceContainerLow = FourUColors.Surface,
    outline = FourUColors.Line,
    outlineVariant = FourUColors.Line,
    error = FourUColors.Danger,
)

private val base = Typography()
private val typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.8.sp),
)

@Composable
fun FourUTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** Título de sección en mayúsculas pequeñas, como en el escritorio. */
val SectionLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = FourUColors.Muted)
