package com.sonora.music.ui.screens.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.SmartAlbumType
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.TimeCapsuleGroup
import com.sonora.music.data.model.YearCollectionGroup
import com.sonora.music.ui.components.AddToPlaylistDialog
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.EditSongMetadataDialog
import com.sonora.music.ui.components.EmptyStateView
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.components.SongItemRow
import com.sonora.music.ui.components.formatListeningDuration
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun SmartAlbumDetailScreen(
    type: SmartAlbumType,
    songs: List<Song>,
    timeCapsuleGroups: List<TimeCapsuleGroup>,
    byYearGroups: List<YearCollectionGroup>,
    shortTrackMaxDurationMs: Long,
    currentSongId: Long?,
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onPlayAll: (List<Song>, Boolean) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddSongToPlaylist: (playlistId: Long, songId: Long) -> Unit,
    onCreatePlaylist: (name: String, description: String) -> Unit,
    onSetShortTrackMaxMs: (Long) -> Unit,
    onEditSongMetadata: (songId: Long, title: String?, artist: String?, album: String?, year: Int?, artworkUri: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songForEdit by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    songForEdit?.let { target ->
        EditSongMetadataDialog(
            song = target,
            onDismiss = { songForEdit = null },
            onSave = { title, artist, album, year, artUri ->
                onEditSongMetadata(target.id, title, artist, album, year, artUri)
            }
        )
    }

    songForPlaylist?.let { target ->
        AddToPlaylistDialog(
            song = target,
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onPlaylistSelected = { pid ->
                onAddSongToPlaylist(pid, target.id)
                songForPlaylist = null
            },
            onCreateNewPlaylist = { showCreatePlaylistDialog = true }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreatePlaylistDialog = false
            }
        )
    }

    songForInfo?.let { target ->
        SongInfoDialog(song = target, onDismiss = { songForInfo = null })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Header bar
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.title,
                    color = colors.primaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${songs.size} tracks • ${type.description}",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        // Configurable duration selector for Short Tracks
        if (type == SmartAlbumType.SHORT_TRACKS) {
            val presets = listOf(
                30_000L to "Under 30s",
                60_000L to "Under 1m",
                90_000L to "Under 1.5m",
                120_000L to "Under 2m",
                180_000L to "Under 3m"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (ms, label) ->
                    val selected = shortTrackMaxDurationMs == ms
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSetShortTrackMaxMs(ms) },
                        color = if (selected) colors.accent else colors.elevatedBackground,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onPlayAll(songs, false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { onPlayAll(songs, true) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when {
            type == SmartAlbumType.TIME_CAPSULE && timeCapsuleGroups.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    timeCapsuleGroups.forEach { group ->
                        item(key = "tc_header_${group.yearMonthKey}") {
                            SmartGroupHeader(
                                title = group.label,
                                subtitle = "${group.songs.size} songs • ${group.playCount} plays • ${formatListeningDuration(group.totalListeningMs)}",
                                onPlayGroup = { onPlayAll(group.songs, false) }
                            )
                        }
                        items(group.songs, key = { "tc_${group.yearMonthKey}_${it.id}" }) { song ->
                            SongItemRow(
                                song = song,
                                isPlayingThis = song.id == currentSongId,
                                onClick = { onPlaySong(song, group.songs) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onAddToPlaylist = { songForPlaylist = song },
                                onShowInfo = { songForInfo = song },
                                onEditMetadata = { songForEdit = song }
                            )
                        }
                    }
                }
            }

            type == SmartAlbumType.BY_YEAR && byYearGroups.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    byYearGroups.forEach { group ->
                        item(key = "yr_header_${group.year}") {
                            SmartGroupHeader(
                                title = group.label,
                                subtitle = "${group.songs.size} tracks",
                                onPlayGroup = { onPlayAll(group.songs, false) }
                            )
                        }
                        items(group.songs, key = { "yr_${group.year}_${it.id}" }) { song ->
                            SongItemRow(
                                song = song,
                                isPlayingThis = song.id == currentSongId,
                                onClick = { onPlaySong(song, group.songs) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onAddToPlaylist = { songForPlaylist = song },
                                onShowInfo = { songForInfo = song },
                                onEditMetadata = { songForEdit = song }
                            )
                        }
                    }
                }
            }

            songs.isEmpty() -> {
                val emptyIcon = when (type) {
                    SmartAlbumType.FAVORITES -> Icons.Default.Favorite
                    SmartAlbumType.TIME_CAPSULE -> Icons.Default.CalendarMonth
                    SmartAlbumType.LONGEST_PLAYED -> Icons.Default.HourglassBottom
                    SmartAlbumType.RECENTLY_PLAYED -> Icons.Default.History
                    else -> Icons.Default.AutoAwesome
                }
                EmptyStateView(
                    title = "No Tracks in ${type.title}",
                    message = "Start playing or liking music in your library and this smart collection will populate automatically.",
                    icon = emptyIcon
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(songs, key = { "${type.name}_${it.id}" }) { song ->
                        Column {
                            SongItemRow(
                                song = song,
                                isPlayingThis = song.id == currentSongId,
                                onClick = { onPlaySong(song, songs) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onAddToPlaylist = { songForPlaylist = song },
                                onShowInfo = { songForInfo = song },
                                onEditMetadata = { songForEdit = song }
                            )
                            val statBadge = when (type) {
                                SmartAlbumType.MOST_PLAYED, SmartAlbumType.TOP_50 ->
                                    "${song.playCount} plays • ${formatListeningDuration(song.totalListeningTimeMs)}"
                                SmartAlbumType.LONGEST_PLAYED ->
                                    "${formatListeningDuration(song.totalListeningTimeMs)} listened • ${song.playCount} plays"
                                SmartAlbumType.MOST_SKIPPED ->
                                    "Skipped ${song.skipCount}x"
                                else -> null
                            }
                            if (statBadge != null) {
                                Text(
                                    text = statBadge,
                                    color = colors.accent.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 76.dp, bottom = 6.dp)
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
private fun SmartGroupHeader(
    title: String,
    subtitle: String,
    onPlayGroup: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.elevatedBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = colors.secondaryText,
                fontSize = 12.sp
            )
        }
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onPlayGroup),
            color = colors.accent.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

