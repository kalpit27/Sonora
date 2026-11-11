package com.sonora.music.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.ArtistSortOption
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.Song
import com.sonora.music.ui.components.AddToPlaylistDialog
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.EditSongMetadataDialog
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.components.SongItemRow
import com.sonora.music.ui.components.formatListeningDuration
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun ArtistDetailScreen(
    artistName: String,
    artist: Artist? = null,
    songs: List<Song>,
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
    onEditSongMetadata: (songId: Long, title: String?, artist: String?, album: String?, year: Int?, artworkUri: String?) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songForEdit by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackSort by remember { mutableStateOf(ArtistSortOption.ALPHABETICAL) }

    val sortedArtistSongs = remember(songs, trackSort) {
        when (trackSort) {
            ArtistSortOption.ALPHABETICAL -> songs.sortedBy { it.title.lowercase() }
            ArtistSortOption.RECENTLY_ADDED -> songs.sortedByDescending { it.dateAdded }
            ArtistSortOption.MOST_PLAYED -> songs.sortedWith(
                compareByDescending<Song> { it.playCount }.thenByDescending { it.totalListeningTimeMs }
            )
        }
    }

    songForEdit?.let { targetSong ->
        EditSongMetadataDialog(
            song = targetSong,
            onDismiss = { songForEdit = null },
            onSave = { title, artistStr, albumName, year, artworkUri ->
                onEditSongMetadata(targetSong.id, title, artistStr, albumName, year, artworkUri)
            }
        )
    }

    songForPlaylist?.let { targetSong ->
        AddToPlaylistDialog(
            song = targetSong,
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onPlaylistSelected = { playlistId ->
                onAddSongToPlaylist(playlistId, targetSong.id)
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

    songForInfo?.let { targetSong ->
        SongInfoDialog(song = targetSong, onDismiss = { songForInfo = null })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Back Header
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Artist",
                color = colors.secondaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        AlbumArtImage(
                            artworkUri = artist?.artworkUri ?: songs.firstNotNullOfOrNull { it.albumArtUri },
                            audioUri = songs.firstOrNull()?.contentUri,
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = artistName,
                        color = colors.primaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val totalListenMs = artist?.totalListeningTimeMs ?: songs.sumOf { it.totalListeningTimeMs }
                    val albumCount = artist?.albumCount ?: songs.map { it.album.lowercase() }.distinct().size
                    Text(
                        text = "${songs.size} tracks • $albumCount albums • ${formatListeningDuration(totalListenMs)} listened",
                        color = colors.secondaryText,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { onPlayAll(sortedArtistSongs, false) },
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
                            onClick = { onPlayAll(sortedArtistSongs, true) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Artist Track Sorting Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ArtistSortOption.entries.forEach { option ->
                            val selected = trackSort == option
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { trackSort = option },
                                color = if (selected) colors.accent else colors.surface,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = option.displayName,
                                    color = if (selected) Color.White else colors.primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(sortedArtistSongs, key = { it.id }) { song ->
                SongItemRow(
                    song = song,
                    isPlayingThis = (song.id == currentSongId),
                    onClick = { onPlaySong(song, sortedArtistSongs) },
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
