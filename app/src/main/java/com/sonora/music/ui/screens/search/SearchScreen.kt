package com.sonora.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.Song
import com.sonora.music.ui.components.AddToPlaylistDialog
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.EmptyStateView
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.components.SongItemRow
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<Song>,
    currentSongId: Long?,
    playlists: List<Playlist>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddSongToPlaylist: (playlistId: Long, songId: Long) -> Unit,
    onCreatePlaylist: (name: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    songForPlaylist?.let { targetSong ->
        AddToPlaylistDialog(
            song = targetSong,
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onPlaylistSelected = { playlistId ->
                onAddSongToPlaylist(playlistId, targetSong.id)
                songForPlaylist = null
            },
            onCreateNewPlaylist = {
                showCreatePlaylistDialog = true
            }
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

    songForInfo?.let { targetSong ->
        SongInfoDialog(
            song = targetSong,
            onDismiss = { songForInfo = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Search Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.primaryText
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Search songs, artists, albums...", color = colors.secondaryText) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.accent
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = colors.secondaryText
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.elevatedBackground,
                    unfocusedContainerColor = colors.elevatedBackground,
                    focusedTextColor = colors.primaryText,
                    unfocusedTextColor = colors.primaryText
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
        }

        // Body Content
        if (searchQuery.isBlank()) {
            EmptyStateView(
                title = "Local Music Search",
                message = "Search across your device's audio files by title, artist, or album without internet.",
                icon = Icons.Default.Search
            )
        } else if (searchResults.isEmpty()) {
            EmptyStateView(
                title = "No Matches Found",
                message = "No songs matching \"$searchQuery\" were found in your local Sonora library.",
                icon = Icons.Default.SearchOff
            )
        } else {
            Text(
                text = "${searchResults.size} songs found",
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(searchResults, key = { it.id }) { song ->
                    SongItemRow(
                        song = song,
                        isPlayingThis = (song.id == currentSongId),
                        onClick = { onPlaySong(song, searchResults) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { songForPlaylist = song },
                        onShowInfo = { songForInfo = song }
                    )
                }
            }
        }
    }
}
