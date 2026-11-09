package com.sonora.music.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SonoraRepeatMode
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.theme.LocalSonoraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    val song = playbackState.currentSong
    if (song == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Track Playing",
                    color = colors.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select any audio track to start playback",
                    color = colors.secondaryText,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
        return
    }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        SongInfoDialog(
            song = song,
            onDismiss = { showInfoDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.primaryText
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "THE LISTENING ROOM",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Sonora Offline Archive",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onOpenQueue) {
                BadgedBox(
                    badge = {
                        if (playbackState.queue.isNotEmpty()) {
                            Badge(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ) {
                                Text(playbackState.queue.size.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = colors.primaryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Album Artwork with elevation
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = colors.accent.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                artworkUri = song.albumArtUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track Information & Favorite Heart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = colors.primaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { onToggleFavorite(song) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) colors.accent else colors.secondaryText,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Seeking Scrubber with Elapsed / Remaining Time
        Column(modifier = Modifier.fillMaxWidth()) {
            val totalDuration = playbackState.durationMs.coerceAtLeast(1L)
            val currentPos = if (isUserScrubbing) scrubPositionMs.toLong() else playbackState.positionMs

            Slider(
                value = (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    isUserScrubbing = true
                    scrubPositionMs = frac * totalDuration
                },
                onValueChangeFinished = {
                    isUserScrubbing = false
                    onSeekTo(scrubPositionMs.toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentPos),
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
                Text(
                    text = formatMs(totalDuration),
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        // Playback Controls Row: Shuffle, Previous, Play/Pause, Next, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playbackState.isShuffle) colors.accent else colors.secondaryText,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Previous Track
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = colors.primaryText,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play / Pause Major Action Button (Electric Orange)
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .shadow(elevation = 8.dp, shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Next Track
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = colors.primaryText,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat Button
            IconButton(
                onClick = onCycleRepeatMode,
                modifier = Modifier.size(44.dp)
            ) {
                val repeatIcon = when (playbackState.repeatMode) {
                    SonoraRepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (playbackState.repeatMode != SonoraRepeatMode.OFF) colors.accent else colors.secondaryText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom secondary utilities (Track info)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Track Info",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}
