package com.sonora.music.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.Song
import com.sonora.music.ui.components.ConfirmDeleteDialog
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.EmptyStateView
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.components.SongItemRow
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    currentSongId: Long?,
    onBack: () -> Unit,
    onPlayAll: (List<Song>, Boolean) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRemoveSongFromPlaylist: (songId: Long) -> Unit,
    onRenamePlaylist: (newName: String) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlist == null) return
    val colors = LocalSonoraColors.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }

    if (showRenameDialog) {
        CreatePlaylistDialog(
            onDismiss = { showRenameDialog = false },
            onConfirm = { name, _ ->
                onRenamePlaylist(name)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete Playlist",
            message = "Are you sure you want to delete \"${playlist.name}\"? The audio files will remain safe on your device.",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDeletePlaylist()
                onBack()
            }
        )
    }

    songForInfo?.let { targetSong ->
        SongInfoDialog(song = targetSong, onDismiss = { songForInfo = null })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Back Header with Edit/Delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Playlist",
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row {
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = colors.secondaryText)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.accent)
                }
            }
        }

        if (songs.isEmpty()) {
            EmptyStateView(
                title = playlist.name,
                message = "This playlist is currently empty. Add songs from your library using the track menu.",
                icon = Icons.Default.QueueMusic,
                actionButtonText = "Back to Library",
                onActionClick = onBack
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(colors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = playlist.name,
                            color = colors.primaryText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (playlist.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playlist.description,
                                color = colors.secondaryText,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${songs.size} tracks",
                            color = colors.secondaryText,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { onPlayAll(songs, false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.primaryText
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            OutlinedButton(
                                onClick = { onPlayAll(songs, true) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                itemsIndexed(songs, key = { _, s -> s.id }) { _, song ->
                    SongItemRow(
                        song = song,
                        isPlayingThis = (song.id == currentSongId),
                        onClick = { onPlaySong(song, songs) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onRemoveSongFromPlaylist(song.id) },
                        onShowInfo = { songForInfo = song }
                    )
                }
            }
        }
    }
}
