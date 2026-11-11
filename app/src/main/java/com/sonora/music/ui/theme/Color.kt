package com.sonora.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Sonora x Spotify Brand Palette
val ElectricOrange = Color(0xFFFF6500)
val ElectricOrangeDark = Color(0xFFCC5200)
val ElectricOrangeLight = Color(0xFFFF8533)
val SpotifyGreen = Color(0xFF1ED760)

// Legacy aliases for backwards compatibility
val DeepBlue = Color(0xFF1E3E62)
val MidnightNavy = Color(0xFF121212)

// Spotify Pitch Dark & Charcoal Tones
val SpotifyBlack = Color(0xFF121212)
val SpotifyDarkCharcoal = Color(0xFF181818)
val SpotifyCardElevated = Color(0xFF242424)
val SpotifySurface = Color(0xFF282828)
val SpotifySurfaceHighlight = Color(0xFF333333)
val SpotifyTextPrimary = Color(0xFFFFFFFF)
val SpotifyTextSecondary = Color(0xFFB3B3B3)
val SpotifyTextMuted = Color(0xFF727272)
val SpotifyDivider = Color(0xFF2A2A2A)
val SpotifyBorder = Color(0xFF333333)
val PureBlack = Color(0xFF000000)

// Semantic Dark Palette (Spotify Dark + Sonora Electric Orange)
val DarkBackground = SpotifyBlack
val DarkElevatedBackground = SpotifyCardElevated
val DarkPrimarySurface = SpotifySurface
val DarkSecondarySurface = SpotifySurfaceHighlight
val DarkPrimaryText = SpotifyTextPrimary
val DarkSecondaryText = SpotifyTextSecondary
val DarkDivider = SpotifyDivider
val DarkDisabled = SpotifyTextMuted

// Semantic Light Palette
val LightBackground = Color(0xFFF7F8FA)
val LightElevatedBackground = Color(0xFFFFFFFF)
val LightSecondaryBackground = Color(0xFFEEF1F6)
val LightPrimarySurface = Color(0xFFFFFFFF)
val LightSecondarySurface = Color(0xFFE4E8F0)
val LightPrimaryText = Color(0xFF121212)
val LightSecondaryText = Color(0xFF5A6372)
val LightBorders = Color(0xFFDFE4ED)
val LightDivider = Color(0xFFDFE4ED)
val LightDisabled = Color(0xFFA1AAB7)

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
    secondaryAccent = ElectricOrangeDark,
    border = SpotifyBorder,
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
    secondaryAccent = ElectricOrangeDark,
    border = LightBorders,
    divider = LightDivider,
    disabled = LightDisabled,
    isDark = false
)

// Ambient Gradients for Spotify Immersion
object SonoraGradients {
    val ambientNowPlaying = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF3D1E08),
            Color(0xFF221105),
            SpotifyBlack,
            SpotifyBlack
        )
    )

    val ambientHeader = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2C1605),
            SpotifyBlack
        )
    )

    val likedSongsCard = Brush.linearGradient(
        colors = listOf(
            Color(0xFF450AF5),
            Color(0xFF8E8EE5),
            Color(0xFFFF6500)
        )
    )

    val importArchiveCard = Brush.linearGradient(
        colors = listOf(
            Color(0xFFB31217),
            Color(0xFFFF6500)
        )
    )
}

