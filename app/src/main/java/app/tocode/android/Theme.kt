package app.tocode.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF111827)
val InkSoft = Color(0xFF1F2937)
val Slate = Color(0xFF6B7280)
val SlateLight = Color(0xFF9CA3AF)
val Canvas = Color(0xFFF4F5F7)
val Paper = Color(0xFFFFFFFF)
val Hairline = Color(0xFFE6E8EC)
val Fog = Color(0xFFF0F2F5)
val Pine = Color(0xFF12805C)
val PineDeep = Color(0xFF0A4D38)
val PineSoft = Color(0xFFE2F3EB)
val Amber = Color(0xFFB4690E)
val AmberSoft = Color(0xFFFDF2E0)
val Coral = Color(0xFFD03F3F)
val CoralSoft = Color(0xFFFDECEC)
val Sky = Color(0xFF2F6BE0)
val SkySoft = Color(0xFFE7EFFD)
val Violet = Color(0xFF6A4BD6)
val VioletSoft = Color(0xFFEEEAFB)

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF15936A), PineDeep))
val NightBrush = Brush.linearGradient(listOf(InkSoft, Ink))

private val Colors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = PineSoft,
    onPrimaryContainer = PineDeep,
    secondary = Slate,
    secondaryContainer = PineSoft,
    onSecondaryContainer = PineDeep,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Fog,
    onSurfaceVariant = Slate,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Fog,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Coral,
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

@Composable
fun TocodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
