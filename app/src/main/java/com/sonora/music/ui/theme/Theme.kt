package com.sonora.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sonora.music.data.model.ThemeMode

val LocalSonoraColors = staticCompositionLocalOf { DarkSonoraPalette }

private val DarkMaterialColorScheme = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = PureBlack,
    primaryContainer = DeepBlue,
    onPrimaryContainer = DarkPrimaryText,
    secondary = DeepBlue,
    onSecondary = DarkPrimaryText,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkElevatedBackground,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSecondarySurface,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkDivider
)

private val LightMaterialColorScheme = lightColorScheme(
    primary = ElectricOrange,
    onPrimary = LightElevatedBackground,
    primaryContainer = LightSecondaryBackground,
    onPrimaryContainer = LightPrimaryText,
    secondary = DeepBlue,
    onSecondary = LightElevatedBackground,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightElevatedBackground,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSecondarySurface,
    onSurfaceVariant = LightSecondaryText,
    outline = LightBorders
)

object SonoraThemeTokens {
    val colors: SonoraColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalSonoraColors.current
}

@Composable
fun SonoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val sonoraColors = if (isDark) DarkSonoraPalette else LightSonoraPalette
    val materialColorScheme = if (isDark) DarkMaterialColorScheme else LightMaterialColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = sonoraColors.background.toArgb()
                window.navigationBarColor = sonoraColors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalSonoraColors provides sonoraColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = SonoraTypography,
            shapes = SonoraShapes,
            content = content
        )
    }
}
