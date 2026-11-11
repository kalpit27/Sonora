package com.sonora.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonora.music.ui.theme.LocalSonoraColors
import kotlin.math.sin

/**
 * Full-width rhythmic audio waveform visualizer for NowPlayingScreen.
 * Animates reactive multi-frequency audio bars when music is playing.
 */
@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    barColor: Color = LocalSonoraColors.current.accent,
    maxHeight: Dp = 32.dp
) {
    val transition = rememberInfiniteTransition(label = "WaveformAnim")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val bounce by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            // Harmonic wave formula combining primary wave + fast secondary harmonics
            val normalizedIdx = i.toFloat() / barCount.toFloat()
            val wave1 = sin(phase + normalizedIdx * 4.5f)
            val wave2 = sin((phase * 1.8f) - normalizedIdx * 7f)
            val rawHeightFraction = if (isPlaying) {
                ((wave1 * 0.45f + wave2 * 0.35f + 0.8f) / 1.6f) * bounce
            } else {
                0.12f // Calm resting baseline when paused
            }

            val clampedFraction = rawHeightFraction.coerceIn(0.12f, 1f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.5.dp)
                    .height(maxHeight * clampedFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        barColor.copy(
                            alpha = if (isPlaying) (0.6f + clampedFraction * 0.4f).coerceIn(0.6f, 1f) else 0.35f
                        )
                    )
            )
        }
    }
}

/**
 * Mini 3-bar / 4-bar equalizer dancing animation for MiniPlayer and song lists.
 */
@Composable
fun MiniEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalSonoraColors.current.accent
) {
    val transition = rememberInfiniteTransition(label = "MiniEqAnim")

    val anim1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val anim2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val anim3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(310, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val anim4 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        modifier = modifier.size(width = 16.dp, height = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) 14.dp * anim1 else 4.dp
        val h2 = if (isPlaying) 14.dp * anim2 else 7.dp
        val h3 = if (isPlaying) 14.dp * anim3 else 3.dp
        val h4 = if (isPlaying) 14.dp * anim4 else 5.dp

        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(h1)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(h2)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(h3)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(h4)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

