package com.sonora.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Sonora Official Brand Palette
val ElectricOrange = Color(0xFFFF6500)
val DeepBlue = Color(0xFF1E3E62)
val MidnightNavy = Color(0xFF0B192C)
val PureBlack = Color(0xFF000000)

// Semantic Dark Palette
val DarkBackground = Color(0xFF0B192C)
val DarkElevatedBackground = Color(0xFF13243A)
val DarkPrimarySurface = Color(0xFF1E3E62)
val DarkSecondarySurface = Color(0xFF172B43)
val DarkPrimaryText = Color(0xFFF5F7FB)
val DarkSecondaryText = Color(0xFFB3C0D1)
val DarkDivider = Color(0xFF1F3854)
val DarkDisabled = Color(0xFF4A5F78)

// Semantic Light Palette
val LightBackground = Color(0xFFF4F6FA)
val LightElevatedBackground = Color(0xFFFFFFFF)
val LightSecondaryBackground = Color(0xFFE9EEF5)
val LightPrimarySurface = Color(0xFFFFFFFF)
val LightSecondarySurface = Color(0xFFDEE5EF)
val LightPrimaryText = Color(0xFF0B192C)
val LightSecondaryText = Color(0xFF526175)
val LightBorders = Color(0xFFDCE3EB)
val LightDivider = Color(0xFFDCE3EB)
val LightDisabled = Color(0xFFA5B2C2)

@Immutable
data class SonoraColorPalette(
    val background: Color,
    val elevatedBackground: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val secondaryAccent: Color,
    val border: Color,
    val divider: Color,
    val disabled: Color,
    val isDark: Boolean
)

val DarkSonoraPalette = SonoraColorPalette(
    background = DarkBackground,
    elevatedBackground = DarkElevatedBackground,
    surface = DarkPrimarySurface,
    surfaceVariant = DarkSecondarySurface,
    primaryText = DarkPrimaryText,
    secondaryText = DarkSecondaryText,
    accent = ElectricOrange,
    secondaryAccent = DeepBlue,
    border = DarkSecondarySurface,
    divider = DarkDivider,
    disabled = DarkDisabled,
    isDark = true
)

val LightSonoraPalette = SonoraColorPalette(
    background = LightBackground,
    elevatedBackground = LightElevatedBackground,
    surface = LightPrimarySurface,
    surfaceVariant = LightSecondarySurface,
    primaryText = LightPrimaryText,
    secondaryText = LightSecondaryText,
    accent = ElectricOrange,
    secondaryAccent = DeepBlue,
    border = LightBorders,
    divider = LightDivider,
    disabled = LightDisabled,
    isDark = false
)
