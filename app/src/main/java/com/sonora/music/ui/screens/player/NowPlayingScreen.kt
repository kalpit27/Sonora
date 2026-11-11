package com.sonora.music.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.audio.SonoraEqualizerManager
import com.sonora.music.data.lyrics.LyricsUiState
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SonoraRepeatMode
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.AudioWaveformVisualizer
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.theme.LocalSonoraColors
import com.sonora.music.ui.theme.SpotifySurfaceHighlight
import com.sonora.music.ui.theme.rememberDynamicPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    equalizerManager: SonoraEqualizerManager,
    lyricsState: LyricsUiState = LyricsUiState(),
    searchResults: List<com.sonora.music.data.lyrics.LyricsSearchResultItem> = emptyList(),
    isSearchingLyrics: Boolean = false,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRetryLyrics: () -> Unit = {},
    onSearchLyrics: (String) -> Unit = {},
    onSelectLyricResult: (com.sonora.music.data.lyrics.LyricsSearchResultItem) -> Unit = {},
    onSaveCustomLyrics: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    val song = playbackState.currentSong
    if (song == null) {
        Box(
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
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSearchLyricsDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Dynamic Palette Engine: extracts vibrant and dominant tones from album artwork
    val paletteState = rememberDynamicPalette(
        song = song,
        defaultAccent = colors.accent,
        defaultBackground = colors.background
    )
    val palette = paletteState.value

    val animatedDominant by animateColorAsState(
        targetValue = palette.dominant,
        animationSpec = tween(durationMillis = 650),
        label = "dominantColor"
    )
    val animatedMuted by animateColorAsState(
        targetValue = palette.darkMuted,
        animationSpec = tween(durationMillis = 650),
        label = "mutedColor"
    )

    val dynamicBackground = Brush.verticalGradient(
        colors = listOf(
            animatedDominant.copy(alpha = 0.55f),
            animatedMuted.copy(alpha = 0.85f),
            colors.background,
            colors.background
        )
    )

    if (showInfoDialog) {
        SongInfoDialog(
            song = song,
            onDismiss = { showInfoDialog = false }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            equalizerManager = equalizerManager,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showSearchLyricsDialog) {
        SearchLyricsDialog(
            song = song,
            searchResults = searchResults,
            isSearching = isSearchingLyrics,
            onSearch = onSearchLyrics,
            onSelectResult = onSelectLyricResult,
            onSaveCustomLyrics = onSaveCustomLyrics,
            onDismiss = { showSearchLyricsDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(dynamicBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Collapse, Mode Switch (Cover | Lyrics), Equalizer & Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = colors.primaryText,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Spotify-style Cover / Lyrics Mode Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface.copy(alpha = 0.7f))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!showLyrics) colors.accent else Color.Transparent)
                            .clickable { showLyrics = false }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Cover",
                            color = if (!showLyrics) Color.White else colors.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (showLyrics) colors.accent else Color.Transparent)
                            .clickable { showLyrics = true }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Lyrics",
                            color = if (showLyrics) Color.White else colors.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Audio Equalizer & FX Button
                    IconButton(onClick = { showEqualizerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Audio Equalizer",
                            tint = colors.primaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = colors.primaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Center Content: Animated Switch between Album Artwork (+ Waveform) vs Karaoke Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                    },
                    label = "CoverLyricsContent"
                ) { lyricsMode ->
                    if (lyricsMode) {
                        SyncedLyricsView(
                            lyricsResult = lyricsState.lyrics,
                            isLoading = lyricsState.isLoading,
                            currentPositionMs = playbackState.positionMs,
                            onSeekTo = onSeekTo,
                            onRetry = onRetryLyrics,
                            onOpenSearch = { showSearchLyricsDialog = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f)
                                    .shadow(
                                        elevation = 28.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        spotColor = animatedDominant.copy(alpha = 0.65f)
                                    )
                                    .clip(RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AlbumArtImage(
                                    artworkUri = song.albumArtUriString ?: song.albumArtUri,
                                    audioUri = song.contentUriString,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Real-time audio waveform visualizer pulsing rhythmically
                            AudioWaveformVisualizer(
                                isPlaying = playbackState.isPlaying,
                                barColor = palette.vibrant,
                                barCount = 28,
                                maxHeight = 28.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Track Title, Artist & Like Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = colors.primaryText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = song.artist,
                        color = colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(song) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (song.isFavorite) "Liked" else "Like",
                        tint = if (song.isFavorite) colors.accent else colors.secondaryText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 4. Spotify-style Seeking Scrubber
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
                        thumbColor = colors.primaryText,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = SpotifySurfaceHighlight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(currentPos),
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatMs(totalDuration),
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 5. Main Playback Controls: Shuffle, Prev, Hero Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button with active indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.isShuffle) colors.accent else colors.secondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (playbackState.isShuffle) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
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
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Hero Play/Pause Button (Iconic 68dp Electric Orange Circle)
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .shadow(elevation = 12.dp, shape = CircleShape, spotColor = colors.accent)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
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
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Repeat Button with active indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.size(44.dp)
                ) {
                    val repeatIcon = when (playbackState.repeatMode) {
                        SonoraRepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val isRepeatActive = playbackState.repeatMode != SonoraRepeatMode.OFF
                    IconButton(
                        onClick = onCycleRepeatMode,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Repeat",
                            tint = if (isRepeatActive) colors.accent else colors.secondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (isRepeatActive) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
                }
            }

            // 6. Bottom Source & Queue Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Source",
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sonora Offline Audio",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Lyrics Toggle Icon
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) colors.accent else colors.secondaryText,
                            modifier = Modifier.size(22.dp)
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
                                tint = colors.primaryText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
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
