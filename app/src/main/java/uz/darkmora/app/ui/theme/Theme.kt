package uz.darkmora.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentViolet,
    tertiary = PrimaryGlow,
    background = DarkPalette.bgPrimary,
    surface = DarkPalette.bgSurface,
    onPrimary = DarkPalette.textPrimary,
    onSecondary = DarkPalette.textPrimary,
    onBackground = DarkPalette.textPrimary,
    onSurface = DarkPalette.textPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentViolet,
    tertiary = PrimaryBlue,
    background = LightPalette.bgPrimary,
    surface = LightPalette.bgSurface,
    onPrimary = LightPalette.bgSurface,
    onSecondary = LightPalette.bgSurface,
    onBackground = LightPalette.textPrimary,
    onSurface = LightPalette.textPrimary
)

@Composable
fun DarkMoraTheme(
    isDarkTheme: Boolean = AppThemeState.isDark,
    content: @Composable () -> Unit
) {
    val palette = if (isDarkTheme) DarkPalette else LightPalette
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
