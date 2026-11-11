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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
import com.sonora.music.data.model.ArtistSortOption
import com.sonora.music.data.model.MusicFolder
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.SmartAlbumSummary
import com.sonora.music.data.model.SmartAlbumType
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.MatchedPlaylistSummary
import com.sonora.music.ui.components.AddToPlaylistDialog
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.CreatePlaylistDialog
import com.sonora.music.ui.components.EditSongMetadataDialog
import com.sonora.music.ui.components.EmptyStateView
import com.sonora.music.ui.components.ImportPlaylistDialog
import com.sonora.music.ui.components.MiniPlayer
import com.sonora.music.ui.components.SongInfoDialog
import com.sonora.music.ui.components.SongItemRow
import com.sonora.music.ui.components.SortDialog
import com.sonora.music.ui.components.formatListeningDuration
import com.sonora.music.ui.navigation.MainTab
import com.sonora.music.ui.theme.LocalSonoraColors
import com.sonora.music.ui.theme.SonoraGradients
import com.sonora.music.ui.theme.SpotifyBlack
import com.sonora.music.ui.theme.SpotifyCardElevated
import com.sonora.music.ui.theme.SpotifySurfaceHighlight
import java.util.Calendar

enum class SpotifyBottomNav {
    HOME,
    SEARCH,
    LIBRARY,
    IMPORT
}

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
    onNavigateYouTubeImport: () -> Unit = {},
    smartAlbumSummaries: List<SmartAlbumSummary> = emptyList(),
    artistSortOption: ArtistSortOption = ArtistSortOption.ALPHABETICAL,
    onArtistSortChange: (ArtistSortOption) -> Unit = {},
    onSelectSmartAlbum: (SmartAlbumType) -> Unit = {},
    onOpenListeningDna: () -> Unit = {},
    onOpenDuplicates: () -> Unit = {},
    onEditSongMetadata: (songId: Long, title: String?, artist: String?, album: String?, year: Int?, artworkUri: String?) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    var activeBottomNav by remember { mutableStateOf(SpotifyBottomNav.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(MainTab.SONGS) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songForEdit by remember { mutableStateOf<Song?>(null) }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

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

    songForEdit?.let { targetSong ->
        EditSongMetadataDialog(
            song = targetSong,
            onDismiss = { songForEdit = null },
            onSave = { title, artistStr, albumName, year, artworkUri ->
                onEditSongMetadata(targetSong.id, title, artistStr, albumName, year, artworkUri)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Main Content Area based on Spotify Navigation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeBottomNav) {
                    SpotifyBottomNav.HOME -> {
                        SpotifyHomeView(
                            greeting = greeting,
                            songs = songs,
                            albums = albums,
                            playlists = playlists,
                            favoriteSongs = favoriteSongs,
                            smartAlbumSummaries = smartAlbumSummaries,
                            playbackState = playbackState,
                            isScanning = isScanning,
                            onPlaySong = onPlaySong,
                            onPlaySongList = onPlaySong,
                            onSelectAlbum = onSelectAlbum,
                            onSelectPlaylist = onSelectPlaylist,
                            onSelectSmartAlbum = onSelectSmartAlbum,
                            onOpenListeningDna = onOpenListeningDna,
                            onToggleFavorite = onToggleFavorite,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onAddToPlaylist = { songForPlaylist = it },
                            onShowInfo = { songForInfo = it },
                            onEditMetadata = { songForEdit = it },
                            onRescanLibrary = onRescanLibrary,
                            onNavigateSettings = onNavigateSettings,
                            onNavigateYouTubeImport = onNavigateYouTubeImport,
                            onNavigateFavorites = {
                                onSelectSmartAlbum(SmartAlbumType.FAVORITES)
                            }
                        )
                    }

                    SpotifyBottomNav.LIBRARY -> {
                        SpotifyLibraryView(
                            selectedTab = selectedLibraryTab,
                            onTabSelected = { selectedLibraryTab = it },
                            songs = songs,
                            albums = albums,
                            artists = artists,
                            artistSortOption = artistSortOption,
                            onArtistSortChange = onArtistSortChange,
                            smartAlbumSummaries = smartAlbumSummaries,
                            onSelectSmartAlbum = onSelectSmartAlbum,
                            onOpenListeningDna = onOpenListeningDna,
                            onOpenDuplicates = onOpenDuplicates,
                            playlists = playlists,
                            folders = folders,
                            favoriteSongs = favoriteSongs,
                            playbackState = playbackState,
                            currentSortOption = currentSortOption,
                            onOpenSort = { showSortDialog = true },
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onPlaySong = onPlaySong,
                            onSelectAlbum = onSelectAlbum,
                            onSelectArtist = onSelectArtist,
                            onSelectPlaylist = onSelectPlaylist,
                            onSelectFolder = onSelectFolder,
                            onToggleFavorite = onToggleFavorite,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onAddToPlaylist = { songForPlaylist = it },
                            onShowInfo = { songForInfo = it },
                            onEditMetadata = { songForEdit = it }
                        )
                    }

                    SpotifyBottomNav.SEARCH -> {
                        onNavigateSearch()
                        activeBottomNav = SpotifyBottomNav.HOME
                    }

                    SpotifyBottomNav.IMPORT -> {
                        onNavigateYouTubeImport()
                        activeBottomNav = SpotifyBottomNav.HOME
                    }
                }
            }

            // Floating MiniPlayer docked right above bottom navigation bar
            AnimatedVisibility(
                visible = playbackState.currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                MiniPlayer(
                    playbackState = playbackState,
                    onExpandNowPlaying = onOpenNowPlaying,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onToggleFavorite = onToggleFavorite
                )
            }

            // Spotify-style Bottom Navigation Bar
            SpotifyBottomNavigationBar(
                currentTab = activeBottomNav,
                onTabSelected = { tab ->
                    if (tab == SpotifyBottomNav.SEARCH) {
                        onNavigateSearch()
                    } else if (tab == SpotifyBottomNav.IMPORT) {
                        onNavigateYouTubeImport()
                    } else {
                        activeBottomNav = tab
                    }
                }
            )
        }
    }
}

/**
 * Spotify-style Home Screen with Greeting, Quick-Access 6-Grid, and Horizontal Carousels.
 */
@Composable
private fun SpotifyHomeView(
    greeting: String,
    songs: List<Song>,
    albums: List<Album>,
    playlists: List<Playlist>,
    favoriteSongs: List<Song>,
    smartAlbumSummaries: List<SmartAlbumSummary>,
    playbackState: PlaybackUiState,
    isScanning: Boolean,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlaySongList: (Song, List<Song>) -> Unit,
    onSelectAlbum: (Long) -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onSelectSmartAlbum: (SmartAlbumType) -> Unit,
    onOpenListeningDna: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShowInfo: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onRescanLibrary: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateYouTubeImport: () -> Unit,
    onNavigateFavorites: () -> Unit
) {
    val colors = LocalSonoraColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 90.dp)
    ) {
        // Spotify Ambient Header with Greeting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SonoraGradients.ambientHeader)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    color = colors.primaryText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onRescanLibrary,
                        enabled = !isScanning,
                        modifier = Modifier.size(36.dp)
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
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.primaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spotify Iconic 6-Card Quick Access Grid (2 Columns x 3 Rows)
        // Filled with real songs and their album artwork
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Liked Songs & Sonora Imports
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Liked Songs
                SpotifyQuickCard(
                    title = "Liked Songs",
                    gradient = SonoraGradients.likedSongsCard,
                    icon = Icons.Default.Favorite,
                    subtitle = "${favoriteSongs.size} tracks",
                    onClick = onNavigateFavorites,
                    modifier = Modifier.weight(1f)
                )

                // Card 2: Sonora Imports
                SpotifyQuickCard(
                    title = "Sonora Imports",
                    gradient = SonoraGradients.importArchiveCard,
                    icon = Icons.Default.Download,
                    subtitle = "Offline Audio",
                    onClick = onNavigateYouTubeImport,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Top Songs 1 & 2 (with real song cover artwork!)
            if (songs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val s1 = songs.getOrNull(0)
                    val s2 = songs.getOrNull(1)

                    if (s1 != null) {
                        SpotifyQuickSongCard(
                            song = s1,
                            onClick = { onPlaySong(s1, songs) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (s2 != null) {
                        SpotifyQuickSongCard(
                            song = s2,
                            onClick = { onPlaySong(s2, songs) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Row 3: Top Songs 3 & 4 (with real song cover artwork!)
            if (songs.size > 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val s3 = songs.getOrNull(2)
                    val s4 = songs.getOrNull(3)

                    if (s3 != null) {
                        SpotifyQuickSongCard(
                            song = s3,
                            onClick = { onPlaySong(s3, songs) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (s4 != null) {
                        SpotifyQuickSongCard(
                            song = s4,
                            onClick = { onPlaySong(s4, songs) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Horizontal Carousel 1: Jump Back In (Songs with Large Square Artwork!)
        if (songs.isNotEmpty()) {
            SpotifySectionHeader(title = "Jump Back In", onSeeAll = null)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SpotifySongCard(
                        song = song,
                        isPlaying = song.id == playbackState.currentSong?.id,
                        onClick = { onPlaySong(song, songs) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Horizontal Carousel 2: Downloaded Offline
        val offlineSongs = songs.filter { it.folderName.contains("Sonora", ignoreCase = true) || it.album.contains("Sonora", ignoreCase = true) }
        if (offlineSongs.isNotEmpty()) {
            SpotifySectionHeader(title = "Downloaded Offline", onSeeAll = null)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(offlineSongs, key = { it.id }) { song ->
                    SpotifySongCard(
                        song = song,
                        isPlaying = song.id == playbackState.currentSong?.id,
                        onClick = { onPlaySong(song, offlineSongs) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Horizontal Carousel 2.5: Automatic Smart Albums & Listening DNA
        if (smartAlbumSummaries.isNotEmpty()) {
            SpotifySectionHeader(title = "Smart Albums", onSeeAll = null)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "home_listening_dna") {
                    Surface(
                        modifier = Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenListeningDna),
                        color = colors.elevatedBackground,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                imageVector = Icons.Default.AutoGraph,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Listening DNA",
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Your stats & habits",
                                color = colors.secondaryText,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                items(smartAlbumSummaries, key = { it.type.name }) { summary ->
                    Surface(
                        modifier = Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSmartAlbum(summary.type) },
                        color = colors.elevatedBackground,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = summary.type.title,
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${summary.songCount} tracks",
                                color = colors.secondaryText,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Horizontal Carousel 3: Top Albums
        if (albums.isNotEmpty()) {
            SpotifySectionHeader(title = "Top Albums", onSeeAll = null)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    SpotifyAlbumCard(
                        album = album,
                        onClick = { onSelectAlbum(album.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Horizontal Carousel 4: Your Playlists
        if (playlists.isNotEmpty()) {
            SpotifySectionHeader(title = "Your Playlists", onSeeAll = null)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    // Find first matching song art or fallback
                    val playlistSongArt = songs.firstOrNull()?.albumArtUri
                    SpotifyPlaylistCard(
                        playlist = playlist,
                        artworkUri = playlist.albumArtUri ?: playlistSongArt,
                        onClick = { onSelectPlaylist(playlist.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Vertical Section: Quick Picks
        if (songs.isNotEmpty()) {
            SpotifySectionHeader(title = "Quick Picks", onSeeAll = null)
            Column(modifier = Modifier.fillMaxWidth()) {
                songs.take(15).forEach { song ->
                    SongItemRow(
                        song = song,
                        isPlayingThis = (song.id == playbackState.currentSong?.id),
                        onClick = { onPlaySong(song, songs) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onShowInfo = { onShowInfo(song) },
                        onEditMetadata = { onEditMetadata(song) }
                    )
                }
            }
        }
    }
}

/**
 * Spotify-style Library View with Category Filter Chips and Content Grids/Lists.
 */
@Composable
private fun SpotifyLibraryView(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    artistSortOption: ArtistSortOption,
    onArtistSortChange: (ArtistSortOption) -> Unit,
    smartAlbumSummaries: List<SmartAlbumSummary>,
    onSelectSmartAlbum: (SmartAlbumType) -> Unit,
    onOpenListeningDna: () -> Unit,
    onOpenDuplicates: () -> Unit,
    playlists: List<Playlist>,
    folders: List<MusicFolder>,
    favoriteSongs: List<Song>,
    playbackState: PlaybackUiState,
    currentSortOption: SortOption,
    onOpenSort: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onSelectAlbum: (Long) -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onSelectFolder: (String) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShowInfo: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit
) {
    val colors = LocalSonoraColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Library Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                color = colors.primaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCreatePlaylist) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = colors.primaryText,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onOpenSort) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = colors.primaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Spotify Filter Pills Row
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
                    onClick = { onTabSelected(tab) },
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
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Library Tab Content
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
                            message = "No local audio files found on device.",
                            icon = Icons.Default.MusicNote
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
                                    onAddToPlaylist = { onAddToPlaylist(song) },
                                    onShowInfo = { onShowInfo(song) },
                                    onEditMetadata = { onEditMetadata(song) }
                                )
                            }
                        }
                    }
                }

                MainTab.SMART_ALBUMS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "smart_dna_card") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenListeningDna),
                                color = colors.accent.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoGraph, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Your Listening DNA", color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("Top artists, monthly history & listening habits", color = colors.secondaryText, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        item(key = "smart_duplicates_card") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenDuplicates),
                                color = colors.elevatedBackground,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Duplicate Audio Files", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("Scan and manage duplicate local audio files", color = colors.secondaryText, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        items(smartAlbumSummaries, key = { it.type.name }) { summary ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectSmartAlbum(summary.type) },
                                color = colors.elevatedBackground,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (summary.type == SmartAlbumType.FAVORITES) Icons.Default.Favorite else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = summary.type.title,
                                            color = colors.primaryText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = summary.subtitle,
                                            color = colors.secondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = "${summary.songCount}",
                                        color = colors.accent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                MainTab.ALBUMS -> {
                    if (albums.isEmpty()) {
                        EmptyStateView(
                            title = "No Albums Found",
                            message = "Albums with metadata will appear here.",
                            icon = Icons.Default.Album
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(albums, key = { it.id }) { album ->
                                SpotifyAlbumCard(
                                    album = album,
                                    onClick = { onSelectAlbum(album.id) }
                                )
                            }
                        }
                    }
                }

                MainTab.PLAYLISTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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

                MainTab.FAVORITES -> {
                    if (favoriteSongs.isEmpty()) {
                        EmptyStateView(
                            title = "No Liked Songs",
                            message = "Tap the heart icon on any song to add it here.",
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
                                    onAddToPlaylist = { onAddToPlaylist(song) },
                                    onShowInfo = { onShowInfo(song) },
                                    onEditMetadata = { onEditMetadata(song) }
                                )
                            }
                        }
                    }
                }

                MainTab.ARTISTS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArtistSortOption.entries.forEach { option ->
                                val selected = artistSortOption == option
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onArtistSortChange(option) },
                                    color = if (selected) colors.accent else colors.elevatedBackground,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = option.displayName,
                                        color = if (selected) Color.White else colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp)
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

                MainTab.FOLDERS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp)
                    ) {
                        items(folders, key = { it.path }) { folder ->
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

/**
 * Spotify Bottom Navigation Bar with 4 Icons.
 */
@Composable
private fun SpotifyBottomNavigationBar(
    currentTab: SpotifyBottomNav,
    onTabSelected: (SpotifyBottomNav) -> Unit
) {
    val colors = LocalSonoraColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = SpotifyBlack.copy(alpha = 0.98f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpotifyNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == SpotifyBottomNav.HOME,
                onClick = { onTabSelected(SpotifyBottomNav.HOME) }
            )

            SpotifyNavItem(
                icon = Icons.Default.Search,
                label = "Search",
                isSelected = currentTab == SpotifyBottomNav.SEARCH,
                onClick = { onTabSelected(SpotifyBottomNav.SEARCH) }
            )

            SpotifyNavItem(
                icon = Icons.Default.QueueMusic,
                label = "Your Library",
                isSelected = currentTab == SpotifyBottomNav.LIBRARY,
                onClick = { onTabSelected(SpotifyBottomNav.LIBRARY) }
            )

            SpotifyNavItem(
                icon = Icons.Default.Download,
                label = "Import Audio",
                isSelected = currentTab == SpotifyBottomNav.IMPORT,
                onClick = { onTabSelected(SpotifyBottomNav.IMPORT) }
            )
        }
    }
}

@Composable
private fun SpotifyNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    val tint = if (isSelected) colors.accent else colors.secondaryText

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SpotifySectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null
) {
    val colors = LocalSonoraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
        if (onSeeAll != null) {
            Text(
                text = "Show all",
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Spotify Quick 6-Grid Card with Gradient Artwork Tile.
 */
@Composable
private fun SpotifyQuickCard(
    title: String,
    gradient: androidx.compose.ui.graphics.Brush,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        color = SpotifyCardElevated,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
}

/**
 * Spotify Quick 6-Grid Card for Songs with real Cover Artwork!
 */
@Composable
private fun SpotifyQuickSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        color = SpotifyCardElevated,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    artworkUri = song.albumArtUri,
                    audioUri = song.contentUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = song.title,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Spotify Large Square Card for Songs with real Cover Artwork & Floating Play Button!
 */
@Composable
private fun SpotifySongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                artworkUri = song.albumArtUri,
                audioUri = song.contentUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            )

            // Floating Play/Pause Button in bottom-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .shadow(elevation = 6.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            color = if (isPlaying) colors.accent else colors.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${song.artist} • ${song.formattedDuration}",
            color = colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Spotify Carousel Card for Playlists.
 */
@Composable
private fun SpotifyPlaylistCard(
    playlist: Playlist,
    artworkUri: Any? = null,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AlbumArtImage(
                    artworkUri = artworkUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SonoraGradients.ambientNowPlaying),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            color = colors.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist • Sonora",
            color = colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Spotify Carousel Card for Albums.
 */
@Composable
private fun SpotifyAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    val colors = LocalSonoraColors.current
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(8.dp))
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                artworkUri = album.albumArtUri,
                audioUri = album.fallbackAudioUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            color = colors.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${album.artist} • ${album.trackCount} ${if (album.trackCount == 1) "track" else "tracks"}",
            color = colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(SpotifyCardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (artist.artworkUri != null) {
                AlbumArtImage(
                    artworkUri = artist.artworkUri,
                    audioUri = artist.songs.firstOrNull()?.contentUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
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
                text = "${artist.trackCount} tracks • ${formatListeningDuration(artist.totalListeningTimeMs)} listened",
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyCardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.albumArtUri != null) {
                AlbumArtImage(
                    artworkUri = playlist.albumArtUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(6.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
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
            Text(
                text = if (playlist.description.isNotBlank()) playlist.description else "Playlist • Sonora",
                color = colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyCardElevated),
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
                text = "Folder • ${folder.songCount} tracks",
                color = colors.secondaryText,
                fontSize = 12.sp
            )
        }
    }
}
