package com.sonora.music.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.lyrics.LyricsResult
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun SyncedLyricsView(
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = colors.accent,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Searching synced lyrics...",
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    if (lyricsResult == null || (lyricsResult.lines.isEmpty() && lyricsResult.plainLyrics.isNullOrBlank())) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = colors.secondaryText.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No lyrics found for this song",
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Lyrics could not be matched automatically. Search LRCLIB directly to find lyrics.",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Lyrics", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", color = colors.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Synced Karaoke-Style Lyrics
        if (lyricsResult.isSynced && lyricsResult.lines.isNotEmpty()) {
            val listState = rememberLazyListState()

            val activeIndex by remember(currentPositionMs, lyricsResult.lines) {
                derivedStateOf {
                    val lines = lyricsResult.lines
                    var idx = -1
                    for (i in lines.indices) {
                        if (lines[i].timeMs <= currentPositionMs) {
                            idx = i
                        } else {
                            break
                        }
                    }
                    idx.coerceIn(-1, lines.size - 1)
                }
            }

            // Smoothly auto-scroll so active line stays comfortably in the upper-middle of screen
            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0 && activeIndex < lyricsResult.lines.size) {
                    val scrollTarget = (activeIndex - 1).coerceAtLeast(0)
                    try {
                        listState.animateScrollToItem(scrollTarget, scrollOffset = 0)
                    } catch (ignored: Exception) {}
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 60.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(lyricsResult.lines) { index, line ->
                    val isActive = index == activeIndex

                    val textColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        label = "lyricColor"
                    )

                    val scale by animateFloatAsState(
                        targetValue = if (isActive) 1.05f else 1.0f,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        label = "lyricScale"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSeekTo(line.timeMs)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = line.text.ifBlank { "♪" },
                            color = textColor,
                            fontSize = if (isActive) 23.sp else 18.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            lineHeight = if (isActive) 32.sp else 26.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale)
                        )
                    }
                }
            }
        } else {
            // 2. Unsynced Plain Lyrics View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = lyricsResult.plainLyrics ?: "",
                    color = colors.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.sp
                )
            }
        }

        // Search pill button in top right for quick searching another version
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface.copy(alpha = 0.85f))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Lyrics",
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Search",
                    color = colors.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

