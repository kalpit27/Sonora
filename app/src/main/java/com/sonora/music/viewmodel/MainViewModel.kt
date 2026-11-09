package com.sonora.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.sonora.music.SonoraApp
import com.sonora.music.data.model.Album
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.MusicFolder
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@UnstableApi
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SonoraApp
    private val repository = app.repository
    private val audioController = app.audioController
    val downloadManager = com.sonora.music.data.downloader.SonoraDownloadManager(application)
    val downloadTasks = downloadManager.downloadTasks

    val youTubeAudioDownloader = com.sonora.music.data.downloader.YouTubeAudioDownloader(application, app.database)
    val youTubeUrlParser = com.sonora.music.data.importer.YouTubeUrlParser()
    private val youTubeStreamResolver = com.sonora.music.data.importer.YouTubeStreamResolver()

    val activeDownloadState = youTubeAudioDownloader.activeDownload
    val downloadQueue = youTubeAudioDownloader.downloadQueue
    val queueOverallProgress = youTubeAudioDownloader.overallProgress

    val downloadHistory: StateFlow<List<com.sonora.music.data.local.database.entity.DownloadHistoryEntity>> =
        repository.downloadHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isFetchingYouTubeInfo = MutableStateFlow(false)
    val isFetchingYouTubeInfo: StateFlow<Boolean> = _isFetchingYouTubeInfo.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _youTubeVideoInfo = MutableStateFlow<com.sonora.music.data.model.YouTubeVideoInfo?>(null)
    val youTubeVideoInfo: StateFlow<com.sonora.music.data.model.YouTubeVideoInfo?> = _youTubeVideoInfo.asStateFlow()

    private val _youTubePlaylistInfo = MutableStateFlow<com.sonora.music.data.model.YouTubePlaylistInfo?>(null)
    val youTubePlaylistInfo: StateFlow<com.sonora.music.data.model.YouTubePlaylistInfo?> = _youTubePlaylistInfo.asStateFlow()

    private val _youTubeUrlError = MutableStateFlow<String?>(null)
    val youTubeUrlError: StateFlow<String?> = _youTubeUrlError.asStateFlow()


    val themeMode: StateFlow<ThemeMode> = repository.preferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val sortOption: StateFlow<SortOption> = repository.preferences.sortOptionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.TITLE)

    val sortOrder: StateFlow<SortOrder> = repository.preferences.sortOrderFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.ASCENDING)

    val isHistoryEnabled: StateFlow<Boolean> = repository.preferences.isHistoryEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val playbackState: StateFlow<PlaybackUiState> = audioController.uiState

    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedSongs: StateFlow<List<Song>> = combine(allSongs, sortOption, sortOrder) { songs, option, order ->
        repository.sortSongs(songs, option, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedSongs: StateFlow<List<Song>> = repository.recentlyAddedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = repository.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<MusicFolder>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Song>> = _searchQuery
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        downloadManager.onDownloadFinishedListener = { task, uri ->
            viewModelScope.launch {
                kotlinx.coroutines.delay(1200)
                repository.rescanLibrary()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.rescanLibrary()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        audioController.playSong(song, queue)
    }

    fun playAll(songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val queue = if (shuffle) songs.shuffled() else songs
        audioController.playSong(queue.first(), queue)
    }

    fun playQueueIndex(index: Int) {
        audioController.playQueueIndex(index)
    }

    fun togglePlayPause() {
        audioController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        audioController.seekTo(positionMs)
    }

    fun skipNext() {
        audioController.skipNext()
    }

    fun skipPrevious() {
        audioController.skipPrevious()
    }

    fun toggleShuffle() {
        audioController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        audioController.cycleRepeatMode()
    }

    fun playNext(song: Song) {
        audioController.playNext(song)
    }

    fun addToQueue(song: Song) {
        audioController.addToQueue(song)
    }

    fun removeFromQueue(index: Int) {
        audioController.removeFromQueue(index)
    }

    fun reorderQueue(from: Int, to: Int) {
        audioController.reorderQueue(from, to)
    }

    fun clearQueue() {
        audioController.clearQueue()
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id, song.isFavorite)
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            repository.createPlaylist(name, description)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch {
            repository.updatePlaylist(id, name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlistId, listOf(songId))
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, songIds)
        }
    }

    fun getPlaylistSongs(playlistId: Long) = repository.getPlaylistSongs(playlistId)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.preferences.setThemeMode(mode)
        }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch {
            repository.preferences.setSortOption(option)
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            repository.preferences.setSortOrder(order)
        }
    }

    fun setSorting(option: SortOption, order: SortOrder) {
        viewModelScope.launch {
            repository.preferences.setSortOption(option)
            repository.preferences.setSortOrder(order)
        }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setHistoryEnabled(enabled)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private val smartPlaylistParser = com.sonora.music.data.importer.SmartPlaylistParser()
    private val localTrackMatcher = com.sonora.music.data.importer.LocalTrackMatcher()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun importPlaylist(
        urlOrText: String,
        onSuccess: (com.sonora.music.data.model.MatchedPlaylistSummary) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val parseResult = smartPlaylistParser.parse(urlOrText)
                if (parseResult.isSuccess) {
                    val imported = parseResult.getOrThrow()
                    val currentSongs = allSongs.value
                    val matchedSummary = localTrackMatcher.match(imported, currentSongs)
                    onSuccess(matchedSummary)
                } else {
                    onError(parseResult.exceptionOrNull()?.message ?: "Failed to parse playlist")
                }
            } catch (e: Exception) {
                onError(e.message ?: "An unexpected error occurred during import")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun saveImportedPlaylist(
        summary: com.sonora.music.data.model.MatchedPlaylistSummary,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(
                name = summary.playlistName,
                description = summary.description
            )
            if (summary.matchedSongs.isNotEmpty()) {
                val songIds = summary.matchedSongs.map { it.id }
                repository.addSongsToPlaylist(playlistId, songIds)
            }
            onCreated(playlistId)
        }
    }

    val isExternalDownloaderAvailable: Boolean
        get() = downloadManager.isExternalDownloaderAvailable()

    fun startDirectDownload(title: String, artist: String, url: String) {
        downloadManager.enqueueDownload(title, artist, url)
    }

    fun openWebMusicSearch(title: String, artist: String) {
        downloadManager.openWebMusicSearch(title, artist)
    }

    fun openYouTubeMusicSearch(title: String, artist: String) {
        downloadManager.openYouTubeMusicSearch(title, artist)
    }

    fun openExternalDownloader(title: String, artist: String) {
        downloadManager.openExternalDownloader(title, artist)
    }

    fun fetchYouTubeUrl(urlOrId: String) {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) {
            _youTubeUrlError.value = "Please paste a YouTube URL"
            return
        }
        if (!youTubeUrlParser.isValidYouTubeUrl(trimmed)) {
            _youTubeUrlError.value = "Unsupported link format. Please provide a standard youtube.com, youtu.be, or playlist URL."
            return
        }
        viewModelScope.launch {
            _isFetchingYouTubeInfo.value = true
            _youTubeUrlError.value = null
            _youTubeVideoInfo.value = null
            _youTubePlaylistInfo.value = null

            try {
                if (youTubeUrlParser.isPlaylistUrl(trimmed)) {
                    val result = youTubeUrlParser.fetchPlaylistInfo(trimmed)
                    if (result.isSuccess) {
                        _youTubePlaylistInfo.value = result.getOrThrow()
                        _youTubeUrlError.value = null
                    } else {
                        // If playlist fetch failed, attempt fallback to single video if video ID exists
                        val videoId = youTubeUrlParser.extractVideoId(trimmed)
                        if (videoId != null) {
                            val videoResult = youTubeUrlParser.fetchVideoInfo(trimmed)
                            if (videoResult.isSuccess) {
                                _youTubeVideoInfo.value = videoResult.getOrThrow()
                                _youTubeUrlError.value = null
                            } else {
                                _youTubeUrlError.value = result.exceptionOrNull()?.message ?: "Unable to load playlist or video"
                            }
                        } else {
                            _youTubeUrlError.value = result.exceptionOrNull()?.message ?: "Unable to load playlist"
                        }
                    }
                } else {
                    val result = youTubeUrlParser.fetchVideoInfo(trimmed)
                    if (result.isSuccess) {
                        _youTubeVideoInfo.value = result.getOrThrow()
                        _youTubeUrlError.value = null
                    } else {
                        _youTubeUrlError.value = result.exceptionOrNull()?.message ?: "Unable to fetch video details"
                    }
                }
            } catch (e: Exception) {
                _youTubeUrlError.value = e.message ?: "Failed to inspect YouTube URL"
            } finally {
                _isFetchingYouTubeInfo.value = false
            }
        }
    }

    fun fetchYouTubeVideo(urlOrId: String) {
        fetchYouTubeUrl(urlOrId)
    }

    fun togglePlaylistItemSelection(videoId: String, isSelected: Boolean) {
        val current = _youTubePlaylistInfo.value ?: return
        _youTubePlaylistInfo.value = current.copy(
            tracks = current.tracks.map { track ->
                if (track.videoId == videoId) track.copy(isSelected = isSelected) else track
            }
        )
    }

    fun selectAllPlaylistItems(selectAll: Boolean) {
        val current = _youTubePlaylistInfo.value ?: return
        _youTubePlaylistInfo.value = current.copy(
            tracks = current.tracks.map { it.copy(isSelected = selectAll) }
        )
    }

    fun startPlaylistImport(
        playlistInfo: com.sonora.music.data.model.YouTubePlaylistInfo,
        selectedTracks: List<com.sonora.music.data.model.YouTubePlaylistItem>,
        quality: com.sonora.music.data.model.AudioQuality = com.sonora.music.data.model.AudioQuality.HIGH_320,
        allowDuplicates: Boolean = false,
        onImportComplete: (Long) -> Unit = {}
    ) {
        youTubeAudioDownloader.startPlaylistDownload(
            playlistInfo = playlistInfo,
            selectedTracks = selectedTracks,
            quality = quality,
            allowDuplicates = allowDuplicates,
            onPlaylistImported = { playlistId ->
                viewModelScope.launch {
                    repository.rescanLibrary()
                    onImportComplete(playlistId)
                }
            }
        )
    }

    fun cancelQueue() {
        youTubeAudioDownloader.cancelQueue()
    }

    fun cancelQueueItem(id: String) {
        youTubeAudioDownloader.cancelQueueItem(id)
    }

    fun retryQueueItem(id: String) {
        youTubeAudioDownloader.retryQueueItem(id)
    }

    fun retryFailedQueueItems() {
        youTubeAudioDownloader.retryFailedQueueItems()
    }

    fun clearDownloadQueue() {
        youTubeAudioDownloader.clearQueue()
    }

    fun clearPlaylistPreview() {
        _youTubePlaylistInfo.value = null
        _youTubeUrlError.value = null
    }

    fun clearYouTubePreview() {
        _youTubeVideoInfo.value = null
        _youTubePlaylistInfo.value = null
        _youTubeUrlError.value = null
    }

    fun startYouTubeAudioDownload(
        videoInfo: com.sonora.music.data.model.YouTubeVideoInfo,
        sourceUrl: String,
        quality: com.sonora.music.data.model.AudioQuality = com.sonora.music.data.model.AudioQuality.HIGH_320,
        customTitle: String? = null,
        customArtist: String? = null,
        allowDuplicate: Boolean = false,
        destinationTreeUri: android.net.Uri? = null
    ) {
        viewModelScope.launch {
            val resolvedUrl: String = if (!youTubeStreamResolver.isDirectAudioUrl(sourceUrl)) {
                _youTubeUrlError.value = null
                _isResolvingStream.value = true
                val videoId = videoInfo.videoId.ifBlank {
                    youTubeUrlParser.extractVideoId(sourceUrl) ?: ""
                }
                if (videoId.isBlank()) {
                    _isResolvingStream.value = false
                    _youTubeUrlError.value = "Cannot resolve audio: missing video ID."
                    return@launch
                }
                val result = youTubeStreamResolver.resolveAudioStreamUrl(videoId)
                _isResolvingStream.value = false
                if (result.isFailure) {
                    _youTubeUrlError.value = "Stream resolution failed: ${result.exceptionOrNull()?.message}"
                    return@launch
                }
                result.getOrThrow()
            } else {
                sourceUrl
            }

            youTubeAudioDownloader.startDownload(
                videoInfo = videoInfo,
                audioSourceUrl = resolvedUrl,
                quality = quality,
                customTitle = customTitle,
                customArtist = customArtist,
                allowDuplicate = allowDuplicate,
                destinationTreeUri = destinationTreeUri,
                onComplete = { _ ->
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(1500)
                        repository.rescanLibrary()
                        kotlinx.coroutines.delay(3000)
                        repository.rescanLibrary()
                    }
                }
            )
        }
    }



    fun cancelYouTubeAudioDownload() {
        youTubeAudioDownloader.cancelCurrentDownload()
    }

    fun resetActiveDownload() {
        youTubeAudioDownloader.resetActiveDownload()
    }

    fun retryYouTubeDownload(historyItem: com.sonora.music.data.local.database.entity.DownloadHistoryEntity) {
        youTubeAudioDownloader.retryDownload(
            historyItem = historyItem,
            onComplete = { _ ->
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1500)
                    repository.rescanLibrary()
                    kotlinx.coroutines.delay(3000)
                    repository.rescanLibrary()
                }
            }
        )
    }

    fun clearDownloadHistory() {
        viewModelScope.launch {
            repository.clearDownloadHistory()
        }
    }

    fun deleteDownloadHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteDownloadHistoryItem(id)
        }
    }

    /**
     * Attempts to find the song in the current library and play it.
     * Returns true if the song was found and playback started, false otherwise.
     */
    fun playSongByTitleOrUri(title: String, uriString: String? = null): Boolean {
        val currentSongs = allSongs.value
        val song = currentSongs.find { 
            (uriString != null && it.contentUriString == uriString) ||
            it.title.equals(title, ignoreCase = true) ||
            it.title.contains(title, ignoreCase = true)
        }
        if (song != null) {
            audioController.playSong(song, listOf(song))
            return true
        }
        return false
    }

    /**
     * Plays a song directly from a known MediaStore URI and metadata.
     * This bypasses the library scan — use immediately after a download completes.
     * Returns true if playback started successfully.
     */
    fun playDownloadedSongByUri(
        title: String,
        artist: String,
        uriString: String
    ): Boolean {
        return try {
            val existing = allSongs.value.find { 
                it.contentUriString == uriString ||
                it.title.equals(title.trim(), ignoreCase = true)
            }
            if (existing != null) {
                audioController.playSong(existing, listOf(existing))
                return true
            }

            val mime = when {
                uriString.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                uriString.endsWith(".webm", ignoreCase = true) -> "audio/webm"
                else -> "audio/mpeg"
            }

            val fallbackDurationMs = (activeDownloadState.value.durationSeconds * 1000L).takeIf { it > 0L } ?: 0L

            val tempSong = Song(
                id = System.currentTimeMillis(),
                title = title,
                artist = artist,
                album = "Sonora Imports",
                durationMs = fallbackDurationMs,
                contentUriString = uriString,
                mimeType = mime
            )
            audioController.playSong(tempSong, listOf(tempSong))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            playSongByTitleOrUri(title, uriString)
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.unregister()
        youTubeAudioDownloader.cancelCurrentDownload()
    }
}
