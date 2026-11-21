package uz.darkmora.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Global reactive theme & language state (persisted via UserSessionManager)
object AppThemeState {
    var isDark by mutableStateOf(true)
    var language by mutableStateOf("O'zbekcha")
}

fun tr(uz: String, en: String, ru: String): String {
    return when (AppThemeState.language) {
        "English" -> en
        "Русский" -> ru
        else -> uz
    }
}

data class DarkMoraColors(
    val isDark: Boolean,
    val bgPrimary: Color,
    val bgSurface: Color,
    val bgCard: Color,
    val cardBorder: Color,
    val dividerColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val iconTint: Color,
    val navBarBg: Color,
    val navOrbBg: Color,
    val navOrbIcon: Color,
    val navInactiveIcon: Color,
    val pillBg: Color,
    val pillActiveBg: Color,
    val pillActiveText: Color
)

val DarkPalette = DarkMoraColors(
    isDark = true,
    bgPrimary = Color(0xFF0A0B10),
    bgSurface = Color(0xFF12141E),
    bgCard = Color(0xFF171A26),
    cardBorder = Color(0x16FFFFFF),
    dividerColor = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    iconTint = Color(0xFFE2E8F0),
    navBarBg = Color(0xFF151824),
    navOrbBg = Color(0xFFFFFFFF),
    navOrbIcon = Color(0xFF0A0B10),
    navInactiveIcon = Color(0xFF737C94),
    pillBg = Color(0xFF171A26),
    pillActiveBg = Color(0xFFFFFFFF),
    pillActiveText = Color(0xFF0A0B10)
)

val LightPalette = DarkMoraColors(
    isDark = false,
    bgPrimary = Color(0xFFF6F7F9),
    bgSurface = Color(0xFFFFFFFF),
    bgCard = Color(0xFFFFFFFF),
    cardBorder = Color(0x14000000),
    dividerColor = Color(0x12000000),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    textMuted = Color(0xFF9CA3AF),
    iconTint = Color(0xFF1F2937),
    navBarBg = Color(0xFFFFFFFF),
    navOrbBg = Color(0xFF111827),
    navOrbIcon = Color(0xFFFFFFFF),
    navInactiveIcon = Color(0xFF9CA3AF),
    pillBg = Color(0xFFEEF0F4),
    pillActiveBg = Color(0xFF111827),
    pillActiveText = Color(0xFFFFFFFF)
)

val LocalAppColors = compositionLocalOf { DarkPalette }

// Dynamic getters so all existing screens automatically adapt to Light & Dark mode!
val BgDark: Color get() = if (AppThemeState.isDark) DarkPalette.bgPrimary else LightPalette.bgPrimary
val BgSurface: Color get() = if (AppThemeState.isDark) DarkPalette.bgSurface else LightPalette.bgSurface
val BgCard: Color get() = if (AppThemeState.isDark) DarkPalette.bgCard else LightPalette.bgCard
val CardBorder: Color get() = if (AppThemeState.isDark) DarkPalette.cardBorder else LightPalette.cardBorder
val DividerTone: Color get() = if (AppThemeState.isDark) DarkPalette.dividerColor else LightPalette.dividerColor

val TextPrimary: Color get() = if (AppThemeState.isDark) DarkPalette.textPrimary else LightPalette.textPrimary
val TextMuted: Color get() = if (AppThemeState.isDark) DarkPalette.textSecondary else LightPalette.textSecondary
val TextDim: Color get() = if (AppThemeState.isDark) DarkPalette.textMuted else LightPalette.textMuted

val NavSurface: Color get() = if (AppThemeState.isDark) DarkPalette.navBarBg else LightPalette.navBarBg
val NavActiveOrb: Color get() = if (AppThemeState.isDark) DarkPalette.navOrbBg else LightPalette.navOrbBg
val NavActiveIcon: Color get() = if (AppThemeState.isDark) DarkPalette.navOrbIcon else LightPalette.navOrbIcon
val NavInactiveIcon: Color get() = if (AppThemeState.isDark) DarkPalette.navInactiveIcon else LightPalette.navInactiveIcon

val PrimaryBlue = Color(0xFF3B82F6)
val PrimaryGlow = Color(0xFF60A5FA)
val AccentViolet = Color(0xFF8B5CF6)
val RatingGold = Color(0xFFFBBF24)
val GreenOnline = Color(0xFF10B981)
val RedBadge = Color(0xFFEF4444)
