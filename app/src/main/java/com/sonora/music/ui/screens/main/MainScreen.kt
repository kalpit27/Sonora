package com.sonora.music.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.Album
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.MusicFolder
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.MatchedPlaylistSummary
import com.sonora.music.ui.components.AddToPlaylistDialog
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.EmptyStateView
import com.sonora.music.ui.components.ImportPlaylistDialog
import com.sonora.music.ui.components.MiniPlayer
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.components.SongItemRow
import com.sonora.music.ui.components.SortDialog
import com.sonora.music.ui.navigation.MainTab
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun MainScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    folders: List<MusicFolder>,
    favoriteSongs: List<Song>,
    playbackState: PlaybackUiState,
    isScanning: Boolean,
    currentSortOption: SortOption,
    currentSortOrder: SortOrder,
    onSortChange: (SortOption, SortOrder) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSelectAlbum: (Long) -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onSelectFolder: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRescanLibrary: () -> Unit,
    onCreatePlaylist: (name: String, description: String) -> Unit,
    onAddSongToPlaylist: (playlistId: Long, songId: Long) -> Unit,
    isImporting: Boolean = false,
    onImportPlaylist: (urlOrText: String, onMatched: (MatchedPlaylistSummary) -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
    onSaveImportedPlaylist: (MatchedPlaylistSummary) -> Unit = {},
    isExternalDownloaderAvailable: Boolean = false,
    onStartDirectDownload: (title: String, artist: String, url: String) -> Unit = { _, _, _ -> },
    onOpenWebSearch: (title: String, artist: String) -> Unit = { _, _ -> },
    onOpenYouTubeMusic: (title: String, artist: String) -> Unit = { _, _ -> },
    onOpenExternalDownloader: (title: String, artist: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    var selectedTab by remember { mutableStateOf(MainTab.SONGS) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }

    if (showSortDialog) {
        SortDialog(
            currentOption = currentSortOption,
            currentOrder = currentSortOrder,
            onDismiss = { showSortDialog = false },
            onApply = onSortChange
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

    if (showImportDialog) {
        ImportPlaylistDialog(
            isImporting = isImporting,
            isExternalDownloaderAvailable = isExternalDownloaderAvailable,
            onDismiss = { showImportDialog = false },
            onImport = onImportPlaylist,
            onSavePlaylist = onSaveImportedPlaylist,
            onStartDirectDownload = onStartDirectDownload,
            onOpenWebSearch = onOpenWebSearch,
            onOpenYouTubeMusic = onOpenYouTubeMusic,
            onOpenExternalDownloader = onOpenExternalDownloader
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
            onCreateNewPlaylist = {
                showCreatePlaylistDialog = true
            }
        )
    }

    songForInfo?.let { targetSong ->
        SongInfoDialog(
            song = targetSong,
            onDismiss = { songForInfo = null }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Editorial Sonora Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Sonora",
                            color = colors.primaryText,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
                    Text(
                        text = "Your Music. Your Space.",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.primaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onRescanLibrary,
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                tint = colors.primaryText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.primaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Real Indexed Stats Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} tracks • ${albums.size} albums • ${playlists.size} playlists",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (selectedTab == MainTab.SONGS && songs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSortDialog = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentSortOption.displayName,
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = Color.White,
                            containerColor = colors.elevatedBackground,
                            labelColor = colors.secondaryText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tab Body Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    MainTab.SONGS -> {
                        if (songs.isEmpty()) {
                            EmptyStateView(
                                title = "No Music Indexed",
                                message = "No local audio files were found. Place songs on your device and tap Rescan.",
                                icon = Icons.Default.MusicNote,
                                actionButtonText = "Rescan Device Music",
                                onActionClick = onRescanLibrary
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(songs, key = { it.id }) { song ->
                                    SongItemRow(
                                        song = song,
                                        isPlayingThis = (song.id == playbackState.currentSong?.id),
                                        onClick = { onPlaySong(song, songs) },
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

                    MainTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            EmptyStateView(
                                title = "No Albums Found",
                                message = "Albums with metadata will appear here after scanning device storage.",
                                icon = Icons.Default.Album
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albums, key = { it.id }) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = { onSelectAlbum(album.id) }
                                    )
                                }
                            }
                        }
                    }

                    MainTab.ARTISTS -> {
                        if (artists.isEmpty()) {
                            EmptyStateView(
                                title = "No Artists Found",
                                message = "Artists will appear here once audio files are indexed.",
                                icon = Icons.Default.Person
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
                            ) {
                                items(artists, key = { it.id }) { artist ->
                                    ArtistRow(
                                        artist = artist,
                                        onClick = { onSelectArtist(artist.name) }
                                    )
                                }
                            }
                        }
                    }

                    MainTab.PLAYLISTS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Create Playlist Action Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.elevatedBackground)
                                    .clickable { showCreatePlaylistDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Create New Playlist",
                                    color = colors.primaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            // Import Playlist Action Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.elevatedBackground)
                                    .clickable { showImportDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Import Spotify / YT Music",
                                        color = colors.primaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Match links with tracks on your device",
                                        color = colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (playlists.isEmpty()) {
                                EmptyStateView(
                                    title = "No Playlists Yet",
                                    message = "Create local playlists to organize your favorite tracks. Playlists are preserved permanently across rescans.",
                                    icon = Icons.Default.QueueMusic
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentPadding = PaddingValues(bottom = 90.dp)
                                ) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        PlaylistRow(
                                            playlist = playlist,
                                            onClick = { onSelectPlaylist(playlist.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MainTab.FAVORITES -> {
                        if (favoriteSongs.isEmpty()) {
                            EmptyStateView(
                                title = "No Favorites Yet",
                                message = "Tap the heart icon on any song to add it to your Sonora Favorites collection.",
                                icon = Icons.Default.Favorite
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(favoriteSongs, key = { it.id }) { song ->
                                    SongItemRow(
                                        song = song,
                                        isPlayingThis = (song.id == playbackState.currentSong?.id),
                                        onClick = { onPlaySong(song, favoriteSongs) },
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

                    MainTab.FOLDERS -> {
                        if (folders.isEmpty()) {
                            EmptyStateView(
                                title = "No Folders Detected",
                                message = "Audio folders will be listed here after indexing device storage.",
                                icon = Icons.Default.Folder
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
                            ) {
                                items(folders, key = { it.name }) { folder ->
                                    FolderRow(
                                        folder = folder,
                                        onClick = { onSelectFolder(folder.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Mini Player anchored above bottom insets
        AnimatedVisibility(
            visible = playbackState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                playbackState = playbackState,
                onExpandNowPlaying = onOpenNowPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = colors.elevatedBackground,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    artworkUri = album.albumArtUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = album.title,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.artist} • ${album.trackCount} tracks",
                color = colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevatedBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.trackCount} tracks • ${artist.albumCount} albums",
                color = colors.secondaryText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevatedBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (playlist.description.isNotBlank()) {
                Text(
                    text = playlist.description,
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: MusicFolder,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevatedBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.songCount} audio tracks",
                color = colors.secondaryText,
                fontSize = 12.sp
            )
        }
    }
}
