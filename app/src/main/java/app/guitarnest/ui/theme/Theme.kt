package app.guitarnest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitarnest.R

// Warm spruce and rosewood tones; green is kept for "in tune".
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8B4A1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC6),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF735846),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC6),
    onSecondaryContainer = Color(0xFF2A170A),
    tertiary = Color(0xFF2F6A4F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2F1CF),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED2),
    onSurfaceVariant = Color(0xFF52443B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFBEBE2),
    surfaceContainerHigh = Color(0xFFF5E5DC),
    surfaceContainerHighest = Color(0xFFEFDFD6),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD7C2B7),
    error = Color(0xFFBA1A1A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF532200),
    primaryContainer = Color(0xFF6F3406),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Color(0xFFE3BFA8),
    onSecondary = Color(0xFF412B1C),
    secondaryContainer = Color(0xFF5A4130),
    onSecondaryContainer = Color(0xFFFFDCC6),
    tertiary = Color(0xFF96D5B3),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF145138),
    onTertiaryContainer = Color(0xFFB2F1CF),
    background = Color(0xFF19120E),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF19120E),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD7C2B7),
    surfaceContainerLowest = Color(0xFF130D09),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF261E19),
    surfaceContainerHigh = Color(0xFF312823),
    surfaceContainerHighest = Color(0xFF3C332D),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF52443B),
)

/** Colours with a fixed meaning on the tuner. */
@Immutable
data class TunerPalette(val inTune: Color, val close: Color, val off: Color)

private val LightTuner = TunerPalette(inTune = Color(0xFF2E8B57), close = Color(0xFFC98A12), off = Color(0xFFC4452F))
private val DarkTuner = TunerPalette(inTune = Color(0xFF5BD08F), close = Color(0xFFF2B53B), off = Color(0xFFFF7A62))

val LocalTunerColors = staticCompositionLocalOf { LightTuner }

object TunerColors {
    val current: TunerPalette
        @Composable get() = LocalTunerColors.current
}

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800))

private val base = Typography()

private fun TextStyle.n(weight: FontWeight? = null) =
    copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)

private val AppTypography = Typography(
    displayLarge = base.displayLarge.n(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.n(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.n(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.n(FontWeight.Bold),
    headlineMedium = base.headlineMedium.n(FontWeight.Bold),
    headlineSmall = base.headlineSmall.n(FontWeight.Bold),
    titleLarge = base.titleLarge.n(FontWeight.Bold),
    titleMedium = base.titleMedium.n(FontWeight.Bold),
    titleSmall = base.titleSmall.n(FontWeight.Bold),
    bodyLarge = base.bodyLarge.n(),
    bodyMedium = base.bodyMedium.n(),
    bodySmall = base.bodySmall.n(),
    labelLarge = base.labelLarge.n(FontWeight.Bold),
    labelMedium = base.labelMedium.n(FontWeight.SemiBold),
    labelSmall = base.labelSmall.n(FontWeight.SemiBold).copy(fontSize = 11.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GuitarNestTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalTunerColors provides if (dark) DarkTuner else LightTuner) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
