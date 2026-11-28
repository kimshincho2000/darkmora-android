package uz.darkmora.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF05070D)
val BgCard = Color(0x99101420)
val BgCardElevated = Color(0xB3171C2B)
val BorderSubtle = Color(0x2EFFFFFF)
val BorderStrong = Color(0x52FFFFFF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB4B9C9)
val TextMuted = Color(0xFF757C91)
val AccentWhite = Color(0xFFFFFFFF)
val AccentEmerald = Color(0xFF22C55E)
val AccentCyan = Color(0xFF38BDF8)
val AccentBlue = Color(0xFF3B82F6)
val AccentViolet = Color(0xFF8B5CF6)
val AccentAmber = Color(0xFFF59E0B)
val AccentRose = Color(0xFFF43F5E)

private val AdminDarkScheme = darkColorScheme(
    primary = AccentWhite,
    secondary = AccentViolet,
    tertiary = AccentEmerald,
    background = BgBlack,
    surface = BgCard,
    onPrimary = BgBlack,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun DarkMoraAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdminDarkScheme,
        content = content
    )
}
