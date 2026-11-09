package com.sonora.music.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sonora.music.ui.screens.details.AlbumDetailScreen
import com.sonora.music.ui.screens.details.ArtistDetailScreen
import com.sonora.music.ui.screens.details.FolderDetailScreen
import com.sonora.music.ui.screens.details.PlaylistDetailScreen
import com.sonora.music.ui.screens.main.MainScreen
import com.sonora.music.ui.screens.permissions.PermissionScreen
import com.sonora.music.ui.screens.player.NowPlayingScreen
import com.sonora.music.ui.screens.queue.QueueScreen
import com.sonora.music.ui.screens.search.SearchScreen
import com.sonora.music.ui.screens.settings.SettingsScreen
import com.sonora.music.viewmodel.MainViewModel

@UnstableApi
@Composable
fun SonoraNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.sortedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isHistoryEnabled by viewModel.isHistoryEnabled.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val youTubeVideoInfo by viewModel.youTubeVideoInfo.collectAsState()
    val youTubePlaylistInfo by viewModel.youTubePlaylistInfo.collectAsState()
    val isFetchingYouTubeInfo by viewModel.isFetchingYouTubeInfo.collectAsState()
    val youTubeUrlError by viewModel.youTubeUrlError.collectAsState()
    val activeDownloadState by viewModel.activeDownloadState.collectAsState()
    val downloadQueue by viewModel.downloadQueue.collectAsState()
    val queueOverallProgress by viewModel.queueOverallProgress.collectAsState()
    val downloadHistory by viewModel.downloadHistory.collectAsState()
    val isResolvingStream by viewModel.isResolvingStream.collectAsState()

    val startDestination = if (hasStoragePermission) {
        SonoraDestination.Main.route
    } else {
        SonoraDestination.Permissions.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(SonoraDestination.Permissions.route) {
            PermissionScreen(
                hasDeniedOnce = false,
                onRequestPermission = onRequestPermission,
                onSkipToLibrary = {
                    navController.navigate(SonoraDestination.Main.route) {
                        popUpTo(SonoraDestination.Permissions.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = SonoraDestination.Main.route,
            enterTransition = { fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            MainScreen(
                songs = songs,
                albums = albums,
                artists = artists,
                playlists = playlists,
                folders = folders,
                favoriteSongs = favoriteSongs,
                playbackState = playbackState,
                isScanning = isScanning,
                currentSortOption = sortOption,
                currentSortOrder = sortOrder,
                onSortChange = viewModel::setSorting,
                onNavigateSearch = { navController.navigate(SonoraDestination.Search.route) },
                onNavigateSettings = { navController.navigate(SonoraDestination.Settings.route) },
                onOpenNowPlaying = { navController.navigate(SonoraDestination.NowPlaying.route) },
                onSelectAlbum = { albumId -> navController.navigate(SonoraDestination.AlbumDetail.createRoute(albumId)) },
                onSelectArtist = { artistName -> navController.navigate(SonoraDestination.ArtistDetail.createRoute(artistName)) },
                onSelectPlaylist = { playlistId -> navController.navigate(SonoraDestination.PlaylistDetail.createRoute(playlistId)) },
                onSelectFolder = { folderName -> navController.navigate(SonoraDestination.FolderDetail.createRoute(folderName)) },
                onPlaySong = viewModel::playSong,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onToggleFavorite = viewModel::toggleFavorite,
                onRescanLibrary = viewModel::rescanLibrary,
                onCreatePlaylist = viewModel::createPlaylist,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                isImporting = isImporting,
                onImportPlaylist = viewModel::importPlaylist,
                onSaveImportedPlaylist = { summary ->
                    viewModel.saveImportedPlaylist(summary) { newPlaylistId ->
                        navController.navigate(SonoraDestination.PlaylistDetail.createRoute(newPlaylistId))
                    }
                },
                isExternalDownloaderAvailable = viewModel.isExternalDownloaderAvailable,
                onStartDirectDownload = viewModel::startDirectDownload,
                onOpenWebSearch = viewModel::openWebMusicSearch,
                onOpenYouTubeMusic = viewModel::openYouTubeMusicSearch,
                onOpenExternalDownloader = viewModel::openExternalDownloader,
                onNavigateYouTubeImport = { navController.navigate(SonoraDestination.YouTubeImport.route) }
            )
        }

        composable(
            route = SonoraDestination.Search.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) }
        ) {
            SearchScreen(
                searchQuery = searchQuery,
                searchResults = searchResults,
                currentSongId = playbackState.currentSong?.id,
                playlists = playlists,
                onQueryChange = viewModel::updateSearchQuery,
                onBack = { navController.popBackStack() },
                onPlaySong = viewModel::playSong,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                onCreatePlaylist = viewModel::createPlaylist
            )
        }

        composable(
            route = SonoraDestination.NowPlaying.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(350)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300)) }
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                onBack = { navController.popBackStack() },
                onOpenQueue = { navController.navigate(SonoraDestination.Queue.route) },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeatMode = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite
            )
        }

        composable(
            route = SonoraDestination.Queue.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) }
        ) {
            QueueScreen(
                playbackState = playbackState,
                onBack = { navController.popBackStack() },
                onPlayIndex = viewModel::playQueueIndex,
                onRemoveIndex = viewModel::removeFromQueue,
                onMoveIndex = viewModel::reorderQueue,
                onClearQueue = viewModel::clearQueue
            )
        }

        composable(
            route = SonoraDestination.Settings.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) }
        ) {
            SettingsScreen(
                currentThemeMode = themeMode,
                currentSortOption = sortOption,
                currentSortOrder = sortOrder,
                isHistoryEnabled = isHistoryEnabled,
                isScanning = isScanning,
                onBack = { navController.popBackStack() },
                onSetThemeMode = viewModel::setThemeMode,
                onSetSorting = { opt, ord ->
                    viewModel.setSortOption(opt)
                    viewModel.setSortOrder(ord)
                },
                onSetHistoryEnabled = viewModel::setHistoryEnabled,
                onClearHistory = viewModel::clearHistory,
                onRescanLibrary = viewModel::rescanLibrary
            )
        }

        composable(
            route = SonoraDestination.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            val album = albums.find { it.id == albumId }
            val albumSongs = songs.filter { it.albumId == albumId }

            AlbumDetailScreen(
                album = album,
                songs = albumSongs,
                currentSongId = playbackState.currentSong?.id,
                playlists = playlists,
                onBack = { navController.popBackStack() },
                onPlayAll = viewModel::playAll,
                onPlaySong = viewModel::playSong,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                onCreatePlaylist = viewModel::createPlaylist
            )
        }

        composable(
            route = SonoraDestination.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedArtist = backStackEntry.arguments?.getString("artistName") ?: return@composable
            val artistName = java.net.URLDecoder.decode(encodedArtist, "UTF-8")
            val artistSongs = songs.filter { it.artist.equals(artistName, ignoreCase = true) }

            ArtistDetailScreen(
                artistName = artistName,
                songs = artistSongs,
                currentSongId = playbackState.currentSong?.id,
                playlists = playlists,
                onBack = { navController.popBackStack() },
                onPlayAll = viewModel::playAll,
                onPlaySong = viewModel::playSong,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                onCreatePlaylist = viewModel::createPlaylist
            )
        }

        composable(
            route = SonoraDestination.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            val playlist = playlists.find { it.id == playlistId }
            val playlistSongs by viewModel.getPlaylistSongs(playlistId).collectAsState(initial = emptyList())

            PlaylistDetailScreen(
                playlist = playlist,
                songs = playlistSongs,
                currentSongId = playbackState.currentSong?.id,
                onBack = { navController.popBackStack() },
                onPlayAll = viewModel::playAll,
                onPlaySong = viewModel::playSong,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                onRemoveSongFromPlaylist = { songId -> viewModel.removeSongFromPlaylist(playlistId, songId) },
                onRenamePlaylist = { newName -> viewModel.renamePlaylist(playlistId, newName) },
                onDeletePlaylist = { viewModel.deletePlaylist(playlistId) }
            )
        }

        composable(
            route = SonoraDestination.FolderDetail.route,
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedFolder = backStackEntry.arguments?.getString("folderName") ?: return@composable
            val folderName = java.net.URLDecoder.decode(encodedFolder, "UTF-8")
            val folderSongs = songs.filter { it.folderName.equals(folderName, ignoreCase = true) }

            FolderDetailScreen(
                folderName = folderName,
                songs = folderSongs,
                currentSongId = playbackState.currentSong?.id,
                playlists = playlists,
                onBack = { navController.popBackStack() },
                onPlayAll = viewModel::playAll,
                onPlaySong = viewModel::playSong,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                onCreatePlaylist = viewModel::createPlaylist
            )
        }

        val renderImportAudio: @Composable () -> Unit = {
            com.sonora.music.ui.screens.youtube.YouTubeImportScreen(
                videoInfo = youTubeVideoInfo,
                playlistInfo = youTubePlaylistInfo,
                isFetchingInfo = isFetchingYouTubeInfo,
                urlError = youTubeUrlError,
                activeDownload = activeDownloadState,
                downloadQueue = downloadQueue,
                queueOverallProgress = queueOverallProgress,
                downloadHistory = downloadHistory,
                isResolvingStream = isResolvingStream,
                isExternalDownloaderAvailable = viewModel.isExternalDownloaderAvailable,
                onBack = { navController.popBackStack() },
                onFetchYouTubeUrl = viewModel::fetchYouTubeUrl,
                onClearPreview = viewModel::clearYouTubePreview,
                onTogglePlaylistItemSelection = viewModel::togglePlaylistItemSelection,
                onSelectAllPlaylistItems = viewModel::selectAllPlaylistItems,
                onStartDownload = viewModel::startYouTubeAudioDownload,
                onStartPlaylistImport = { playlist, selectedTracks, quality, allowDupes ->
                    viewModel.startPlaylistImport(playlist, selectedTracks, quality, allowDupes)
                },
                onCancelDownload = viewModel::cancelYouTubeAudioDownload,
                onCancelQueue = viewModel::cancelQueue,
                onCancelQueueItem = viewModel::cancelQueueItem,
                onRetryQueueItem = viewModel::retryQueueItem,
                onRetryFailedQueueItems = viewModel::retryFailedQueueItems,
                onClearQueue = viewModel::clearDownloadQueue,
                onResetActiveDownload = viewModel::resetActiveDownload,
                onRetryDownload = viewModel::retryYouTubeDownload,
                onDeleteHistoryItem = viewModel::deleteDownloadHistoryItem,
                onClearHistory = viewModel::clearDownloadHistory,
                onPlaySong = { title, uriStr ->
                    // If we have a local URI (freshly downloaded), play directly without
                    // needing the track to be in the library index yet.
                    val artist = viewModel.activeDownloadState.value.artist
                    val started = if (!uriStr.isNullOrBlank()) {
                        viewModel.playDownloadedSongByUri(title, artist, uriStr)
                    } else {
                        viewModel.playSongByTitleOrUri(title, uriStr)
                    }
                    // Only navigate to NowPlaying if playback actually started
                    if (started) {
                        navController.navigate(SonoraDestination.NowPlaying.route)
                    }
                },
                onOpenPlaylistDetail = { playlistId ->
                    navController.navigate(SonoraDestination.PlaylistDetail.createRoute(playlistId))
                },
                onOpenExternalDownloader = { title, artist, _ ->
                    viewModel.openExternalDownloader(title, artist)
                },
                onOpenWebSearch = viewModel::openWebMusicSearch
            )
        }

        composable(
            route = SonoraDestination.YouTubeImport.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) }
        ) {
            renderImportAudio()
        }

        composable(
            route = SonoraDestination.ImportAudio.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) }
        ) {
            renderImportAudio()
        }
    }
}
