package com.sonora.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.sonora.music.ui.theme.DeepBlue
import com.sonora.music.ui.theme.ElectricOrange
import com.sonora.music.ui.theme.MidnightNavy

@Composable
fun AlbumArtImage(
    artworkUri: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentDescription: String? = "Album Artwork"
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MidnightNavy),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    SonoraArtworkPlaceholder()
                },
                error = {
                    SonoraArtworkPlaceholder()
                }
            )
        } else {
            SonoraArtworkPlaceholder()
        }
    }
}

@Composable
fun SonoraArtworkPlaceholder(
    modifier: Modifier = Modifier
) {
    // Premium Sonora Vinyl / Acoustic placeholder
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DeepBlue,
                        MidnightNavy
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner acoustic hub ring
        Box(
            modifier = Modifier
                .fillMaxSize(0.45f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricOrange,
                            Color(0xFFB34200)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Center spindle
            Box(
                modifier = Modifier
                    .fillMaxSize(0.35f)
                    .clip(CircleShape)
                    .background(MidnightNavy),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = ElectricOrange,
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }
    }
}
