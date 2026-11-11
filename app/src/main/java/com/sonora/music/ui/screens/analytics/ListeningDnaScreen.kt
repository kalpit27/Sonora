package com.sonora.music.ui.screens.analytics

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.ListeningDnaStats
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.formatListeningDuration
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun ListeningDnaScreen(
    dna: ListeningDnaStats,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primaryText)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Your Listening DNA",
                    color = colors.primaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Calculated locally from your offline playback history",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary highlight metrics (Total Listening Time, Total Songs Played, Most Active Day)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DnaMetricCard(
                        title = "Total Listening Time",
                        value = formatListeningDuration(dna.totalListeningTimeMs),
                        subtitle = "${dna.totalSongsPlayed} total plays",
                        modifier = Modifier.weight(1f)
                    )
                    DnaMetricCard(
                        title = "Most Active Day",
                        value = dna.mostActiveDay?.let { formatListeningDuration(it.totalListeningTimeMs) } ?: "0m",
                        subtitle = dna.mostActiveDay?.displayLabel ?: "No sessions yet",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Most-Listened Artist & Most-Played Song
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    dna.mostListenedArtist?.let { topArtist ->
                        DnaSpotlightCard(
                            badge = "MOST-LISTENED ARTIST",
                            title = topArtist.artistName,
                            detail = "${formatListeningDuration(topArtist.totalListeningTimeMs)} • ${topArtist.totalPlayCount} plays • ${topArtist.songCount} tracks",
                            artUriString = topArtist.artworkUriString
                        )
                    }
                    dna.mostPlayedSong?.let { topSong ->
                        DnaSpotlightCard(
                            badge = "MOST-PLAYED SONG",
                            title = topSong.title,
                            detail = "${topSong.artist} • ${topSong.playCount} plays • ${formatListeningDuration(topSong.totalListeningTimeMs)}",
                            artUriString = topSong.artworkUriString
                        )
                    }
                }
            }

            // Top 5 Artists
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.elevatedBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Top 5 Artists",
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (dna.top5Artists.isEmpty()) {
                            Text("Play songs to reveal your top 5 artists.", color = colors.secondaryText, fontSize = 13.sp)
                        } else {
                            val maxMs = dna.top5Artists.maxOfOrNull { it.totalListeningTimeMs }?.coerceAtLeast(1L) ?: 1L
                            dna.top5Artists.forEachIndexed { idx, artist ->
                                HorizontalBarRow(
                                    rank = idx + 1,
                                    title = artist.artistName,
                                    subtitle = "${artist.songCount} tracks in library",
                                    valueLabel = "${formatListeningDuration(artist.totalListeningTimeMs)} (${artist.totalPlayCount}x)",
                                    progress = artist.totalListeningTimeMs.toFloat() / maxMs.toFloat(),
                                    accentColor = colors.accent
                                )
                            }
                        }
                    }
                }
            }

            // Listening Trends
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.elevatedBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Listening Trends (Weekly)",
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SmoothInteractiveLineChart(
                            points = dna.listeningTrends,
                            accentColor = colors.accent
                        )
                    }
                }
            }

            // Monthly Listening History
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.elevatedBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Monthly Listening History",
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        VerticalInteractiveBarChart(
                            entries = dna.monthlyHistory.map {
                                BarChartEntry(
                                    shortLabel = it.label,
                                    fullLabel = it.fullLabel,
                                    value = it.hours,
                                    formattedValue = "${formatListeningDuration(it.durationMs)} (${it.playCount} plays)"
                                )
                            },
                            accentColor = colors.accent,
                            maxVisibleLabels = 6
                        )
                    }
                }
            }

            // Favorite Albums
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.elevatedBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Favorite Albums",
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (dna.favoriteAlbums.isEmpty()) {
                            Text("No album listening activity recorded yet.", color = colors.secondaryText, fontSize = 13.sp)
                        } else {
                            val maxAlb = dna.favoriteAlbums.maxOfOrNull { it.totalListeningTimeMs }?.coerceAtLeast(1L) ?: 1L
                            dna.favoriteAlbums.take(6).forEachIndexed { idx, album ->
                                HorizontalBarRow(
                                    rank = idx + 1,
                                    title = album.albumTitle,
                                    subtitle = "${album.artist} • ${album.trackCount} tracks",
                                    valueLabel = "${formatListeningDuration(album.totalListeningTimeMs)} (${album.totalPlayCount}x)",
                                    progress = album.totalListeningTimeMs.toFloat() / maxAlb.toFloat(),
                                    accentColor = colors.accent
                                )
                            }
                        }
                    }
                }
            }

            // Artist Listening Time Breakdown
            if (dna.artistListeningTimes.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.elevatedBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Artist Listening Time",
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val maxArtist = dna.artistListeningTimes.maxOf { it.totalListeningTimeMs }.coerceAtLeast(1L)
                            dna.artistListeningTimes.take(15).forEachIndexed { idx, item ->
                                HorizontalBarRow(
                                    rank = idx + 1,
                                    title = item.artistName,
                                    subtitle = "${item.totalPlayCount} plays",
                                    valueLabel = formatListeningDuration(item.totalListeningTimeMs),
                                    progress = item.totalListeningTimeMs.toFloat() / maxArtist.toFloat(),
                                    accentColor = colors.accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnaMetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.elevatedBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, color = colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = colors.accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DnaSpotlightCard(
    badge: String,
    title: String,
    detail: String,
    artUriString: String?
) {
    val colors = LocalSonoraColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.elevatedBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtImage(
                artworkUri = artUriString?.let { Uri.parse(it) },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badge,
                    color = colors.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = title,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

