package app.recess.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.recess.android.R
import app.recess.core.Tone

/** The web app's palette (warm paper + sage), kept as named tokens. */
object Palette {
    val Bg = Color(0xFFF3EFE6)
    val BgWarm = Color(0xFFE8E0D2)
    val Surface = Color(0xFFFAF7F0)
    val Surface2 = Color(0xFFFFFDF8)
    val Ink = Color(0xFF1C1916)
    val InkSoft = Color(0xFF3F3A34)
    val Muted = Color(0xFF6F6A63)
    val Faint = Color(0xFF9A948B)
    val Line = Color(0xFFD9D1C3)
    val Accent = Color(0xFF3F5B57)
    val AccentFg = Color(0xFFF4F1EA)
    val AccentSoft = Color(0xFFE3EBE7)
    val Slate = Color(0xFF3D4A5C)
    val Good = Color(0xFF2F6B4F)
    val Warn = Color(0xFF8A5A28)
    val WarnSoft = Color(0xFFF3E6D6)
    val Danger = Color(0xFF8F3D32)

    fun tone(tone: Tone) = if (tone == Tone.Slate) Slate else Accent
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Display = FontFamily(variable(R.font.fraunces, 500), variable(R.font.fraunces, 600))
val Sans = FontFamily(variable(R.font.figtree, 400), variable(R.font.figtree, 500), variable(R.font.figtree, 600))

private val typography = Typography().let { t ->
    fun TextStyle.sans() = copy(fontFamily = Sans)
    fun TextStyle.display() = copy(fontFamily = Display, fontWeight = FontWeight.Medium)
    Typography(
        displaySmall = t.displaySmall.display().copy(fontSize = 34.sp, lineHeight = 38.sp),
        headlineLarge = t.headlineLarge.display().copy(fontSize = 30.sp, lineHeight = 36.sp),
        headlineMedium = t.headlineMedium.display().copy(fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = t.headlineSmall.display().copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = t.titleLarge.display().copy(fontSize = 18.sp),
        titleMedium = t.titleMedium.sans().copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        titleSmall = t.titleSmall.sans().copy(fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.sans(),
        bodyMedium = t.bodyMedium.sans().copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = t.bodySmall.sans().copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = t.labelLarge.sans().copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = t.labelMedium.sans().copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = t.labelSmall.sans().copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
    )
}

private val colors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.AccentFg,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Accent,
    secondary = Palette.Slate,
    onSecondary = Palette.AccentFg,
    secondaryContainer = Palette.AccentSoft,
    onSecondaryContainer = Palette.Accent,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.BgWarm,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Surface2,
    surfaceContainerLow = Palette.Surface2,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.BgWarm,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
)

@Composable
fun RecessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
