package com.sonora.music.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.sonora.music.SonoraApp
import com.sonora.music.data.importer.DriveScanSessionState
import com.sonora.music.data.importer.StagedDriveAudioItem
import java.util.concurrent.atomic.AtomicBoolean
import com.sonora.music.data.model.Album
import com.sonora.music.data.model.AnalyticsDashboardData
import com.sonora.music.data.model.AnalyticsRangeFilter
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.ArtistSortOption
import com.sonora.music.data.model.DuplicateGroup
import com.sonora.music.data.model.ListeningDnaStats
import com.sonora.music.data.model.MusicFolder
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.SmartAlbumSummary
import com.sonora.music.data.model.SmartAlbumType
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.ThemeMode
import com.sonora.music.data.model.TimeCapsuleGroup
import com.sonora.music.data.model.YearCollectionGroup
import com.sonora.music.data.repository.LibraryAnalyticsEngine
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

    val youTubeAudioDownloader = app.youTubeAudioDownloader
    val youTubeUrlParser = com.sonora.music.data.importer.YouTubeUrlParser()
    private val youTubeStreamResolver = com.sonora.music.data.importer.YouTubeStreamResolver()

    val googleDriveImporter = com.sonora.music.data.importer.GoogleDriveImporter(
        context = application,
        database = app.database,
        ffmpegConverter = youTubeAudioDownloader.ffmpegConverter
    )
    val driveImportState = googleDriveImporter.importState

    private val _driveScanState = MutableStateFlow(DriveScanSessionState())
    val driveScanState: StateFlow<DriveScanSessionState> = _driveScanState.asStateFlow()

    private val _drivePreviewUri = MutableStateFlow<String?>(null)
    val drivePreviewUri: StateFlow<String?> = _drivePreviewUri.asStateFlow()

    private val _isDrivePreviewPlaying = MutableStateFlow(false)
    val isDrivePreviewPlaying: StateFlow<Boolean> = _isDrivePreviewPlaying.asStateFlow()

    private val _drivePreviewError = MutableStateFlow<String?>(null)
    val drivePreviewError: StateFlow<String?> = _drivePreviewError.asStateFlow()

    private var drivePreviewPlayer: ExoPlayer? = null
    private var driveScanJob: kotlinx.coroutines.Job? = null
    private var driveImportCancellation = AtomicBoolean(false)

    val lyricsRepository = com.sonora.music.data.lyrics.LyricsRepository(application)
    val equalizerManager = com.sonora.music.audio.SonoraEqualizerManager.getInstance(application)

    private val _lyricsState = MutableStateFlow(com.sonora.music.data.lyrics.LyricsUiState())
    val lyricsState: StateFlow<com.sonora.music.data.lyrics.LyricsUiState> = _lyricsState.asStateFlow()

    private val _lyricSearchResults = MutableStateFlow<List<com.sonora.music.data.lyrics.LyricsSearchResultItem>>(emptyList())
    val lyricSearchResults: StateFlow<List<com.sonora.music.data.lyrics.LyricsSearchResultItem>> = _lyricSearchResults.asStateFlow()

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics: StateFlow<Boolean> = _isSearchingLyrics.asStateFlow()

    private var currentLyricsSongId: Long = -1L

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

    val defaultDownloadQuality: StateFlow<com.sonora.music.data.model.AudioQuality> = repository.preferences.downloadQualityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.sonora.music.data.model.AudioQuality.HIGH_320)

    val shortTrackMaxDurationMs: StateFlow<Long> = repository.preferences.shortTrackMaxDurationMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 120_000L)

    fun setDownloadQuality(quality: com.sonora.music.data.model.AudioQuality) {
        viewModelScope.launch {
            repository.preferences.setDownloadQuality(quality)
        }
    }

    fun setShortTrackMaxDurationMs(durationMs: Long) {
        viewModelScope.launch {
            repository.preferences.setShortTrackMaxDurationMs(durationMs)
        }
    }

    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combine live player state with reactive Room song updates so isFavorite, edits & play stats sync immediately everywhere
    val playbackState: StateFlow<PlaybackUiState> = combine(
        audioController.uiState,
        allSongs
    ) { state, latestSongs ->
        if (latestSongs.isEmpty()) {
            state
        } else {
            val byId = latestSongs.associateBy { it.id }
            state.copy(
                currentSong = state.currentSong?.let { cur -> byId[cur.id] ?: cur },
                queue = state.queue.map { q -> byId[q.id] ?: q }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackUiState())

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

    private val _artistSortOption = MutableStateFlow(ArtistSortOption.ALPHABETICAL)
    val artistSortOption: StateFlow<ArtistSortOption> = _artistSortOption.asStateFlow()

    val artists: StateFlow<List<Artist>> = combine(allSongs, _artistSortOption) { songs, artistSort ->
        LibraryAnalyticsEngine.buildArtists(songs, artistSort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setArtistSortOption(option: ArtistSortOption) {
        _artistSortOption.value = option
    }

    val folders: StateFlow<List<MusicFolder>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smartAlbumSummaries: StateFlow<List<SmartAlbumSummary>> = repository.smartAlbumSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeCapsuleGroups: StateFlow<List<TimeCapsuleGroup>> = repository.timeCapsuleGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val byYearGroups: StateFlow<List<YearCollectionGroup>> = repository.byYearGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val duplicateGroups: StateFlow<List<DuplicateGroup>> = repository.duplicateGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listeningDna: StateFlow<ListeningDnaStats> = repository.listeningDna
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningDnaStats())

    private val _analyticsFilter = MutableStateFlow(AnalyticsRangeFilter.DAYS_7)
    val analyticsFilter: StateFlow<AnalyticsRangeFilter> = _analyticsFilter.asStateFlow()

    private val _analyticsOffset = MutableStateFlow(0)
    val analyticsOffset: StateFlow<Int> = _analyticsOffset.asStateFlow()

    val analyticsDashboard: StateFlow<AnalyticsDashboardData> = repository
        .getAnalyticsDashboard(_analyticsFilter, _analyticsOffset)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsDashboardData())

    fun setAnalyticsFilter(filter: AnalyticsRangeFilter) {
        _analyticsFilter.value = filter
        _analyticsOffset.value = 0
    }

    fun stepAnalyticsRange(delta: Int) {
        _analyticsOffset.update { current -> (current + delta).coerceAtMost(0) }
    }

    fun getSmartAlbumSongs(type: SmartAlbumType) = repository.getSmartAlbumSongs(type)

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

        viewModelScope.launch {
            playbackState.collect { state ->
                val song = state.currentSong
                if (song != null && song.id != currentLyricsSongId) {
                    currentLyricsSongId = song.id
                    loadLyricsForSong(song)
                } else if (song == null) {
                    currentLyricsSongId = -1L
                    _lyricsState.value = com.sonora.music.data.lyrics.LyricsUiState()
                }
            }
        }
    }

    fun retryLoadLyrics() {
        val song = playbackState.value.currentSong ?: return
        loadLyricsForSong(song)
    }

    private fun loadLyricsForSong(song: Song) {
        viewModelScope.launch {
            _lyricsState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = lyricsRepository.getLyrics(song)
            if (result.isSuccess) {
                _lyricsState.update { it.copy(isLoading = false, lyrics = result.getOrNull()) }
            } else {
                _lyricsState.update { it.copy(isLoading = false, lyrics = null, errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun searchLyrics(query: String) {
        viewModelScope.launch {
            _isSearchingLyrics.value = true
            val results = lyricsRepository.searchLyricsList(query)
            _lyricSearchResults.value = results.getOrDefault(emptyList())
            _isSearchingLyrics.value = false
        }
    }

    fun applySelectedLyrics(item: com.sonora.music.data.lyrics.LyricsSearchResultItem) {
        val currentSong = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            _lyricsState.update { it.copy(isLoading = true) }
            val result = lyricsRepository.applyLyricsToSong(currentSong, item)
            if (result.isSuccess) {
                _lyricsState.value = com.sonora.music.data.lyrics.LyricsUiState(isLoading = false, lyrics = result.getOrNull())
            } else {
                _lyricsState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveCustomLyrics(rawText: String) {
        val currentSong = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            _lyricsState.update { it.copy(isLoading = true) }
            val result = lyricsRepository.saveCustomLyrics(currentSong, rawText)
            if (result.isSuccess) {
                _lyricsState.value = com.sonora.music.data.lyrics.LyricsUiState(isLoading = false, lyrics = result.getOrNull())
            } else {
                _lyricsState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearLyricSearchResults() {
        _lyricSearchResults.value = emptyList()
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
            repository.toggleFavorite(song.id)
        }
    }

    fun updateSongMetadata(
        songId: Long,
        title: String?,
        artist: String?,
        album: String?,
        year: Int?,
        artworkUriString: String?
    ) {
        viewModelScope.launch {
            repository.updateSongMetadata(songId, title, artist, album, year, artworkUriString)
        }
    }

    fun updateAlbumArtwork(songIds: List<Long>, artworkUriString: String?) {
        viewModelScope.launch {
            repository.updateAlbumArtwork(songIds, artworkUriString)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                app.contentResolver.delete(song.contentUri, null, null)
            } catch (_: Exception) {
                // Scoped storage or external file may require manual OS confirmation; still remove library entry
            }
            repository.deleteSongFromLibrary(song.id)
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

    fun importGoogleDriveUrl(url: String, destinationTreeUri: android.net.Uri? = null) {
        viewModelScope.launch {
            _isFetchingYouTubeInfo.value = false
            _youTubeUrlError.value = null
            val quality = defaultDownloadQuality.value
            val result = googleDriveImporter.importFromDriveUrl(
                url = url,
                quality = quality,
                destinationTreeUri = destinationTreeUri,
                existingSongs = allSongs.value
            )
            if (result.isSuccess) {
                repository.rescanLibrary()
            }
        }
    }

    fun inspectPickedDriveFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        stopDrivePreview()
        driveScanJob?.cancel()
        driveScanJob = viewModelScope.launch {
            _driveScanState.value = DriveScanSessionState(
                isScanning = true,
                scanProgressText = "Filtering selected files for supported audio..."
            )
            val result = googleDriveImporter.inspectPickedDocumentUris(
                uris = uris,
                existingSongs = allSongs.value
            )
            _driveScanState.value = result
        }
    }

    fun scanDriveFolderTree(treeUri: Uri) {
        stopDrivePreview()
        driveScanJob?.cancel()
        driveScanJob = viewModelScope.launch {
            _driveScanState.value = DriveScanSessionState(
                isScanning = true,
                scanProgressText = "Scanning folders for audio files..."
            )
            val result = googleDriveImporter.scanFolderTreeForAudio(
                treeUri = treeUri,
                existingSongs = allSongs.value,
                onScanProgress = { folders, found ->
                    _driveScanState.value = _driveScanState.value.copy(
                        scanProgressText = "Scanned $folders folder${if (folders == 1) "" else "s"} • $found audio track${if (found == 1) "" else "s"} found"
                    )
                }
            )
            _driveScanState.value = result
        }
    }

    fun cancelDriveScan() {
        driveScanJob?.cancel()
        driveScanJob = null
        _driveScanState.value = DriveScanSessionState()
    }

    fun clearDriveEmptyMessage() {
        _driveScanState.update { it.copy(emptyMessage = null) }
    }

    fun toggleDriveItemSelection(uriString: String) {
        _driveScanState.update { state ->
            val updated = state.items.map { item ->
                if (item.uriString == uriString && !item.isDuplicate) {
                    item.copy(isSelected = !item.isSelected)
                } else {
                    item
                }
            }
            state.copy(items = updated)
        }
    }

    fun toggleSelectAllDriveItems() {
        _driveScanState.update { state ->
            val selectable = state.items.filter { !it.isDuplicate }
            val allSelected = selectable.isNotEmpty() && selectable.all { it.isSelected }
            val nextTarget = !allSelected
            val updated = state.items.map { item ->
                if (item.isDuplicate) item else item.copy(isSelected = nextTarget)
            }
            state.copy(items = updated)
        }
    }

    fun toggleDrivePreview(item: StagedDriveAudioItem) {
        val currentlyPreviewingSame = _drivePreviewUri.value == item.uriString
        if (currentlyPreviewingSame && _isDrivePreviewPlaying.value) {
            stopDrivePreview()
            return
        }

        _drivePreviewError.value = null
        // Pause main music playback cleanly while previewing
        if (audioController.uiState.value.isPlaying) {
            audioController.togglePlayPause()
        }

        try {
            val player = drivePreviewPlayer ?: ExoPlayer.Builder(app)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    false
                )
                .build().also { created ->
                    created.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isDrivePreviewPlaying.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                _isDrivePreviewPlaying.value = false
                                if (playbackState == Player.STATE_ENDED) {
                                    _drivePreviewUri.value = null
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _isDrivePreviewPlaying.value = false
                            _drivePreviewUri.value = null
                            _drivePreviewError.value = "Unable to preview \"${item.title}\" — format or cloud access error."
                        }
                    })
                    drivePreviewPlayer = created
                }

            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(Uri.parse(item.uriString)))
            player.prepare()
            player.play()
            _drivePreviewUri.value = item.uriString
            _isDrivePreviewPlaying.value = true
        } catch (e: Exception) {
            _isDrivePreviewPlaying.value = false
            _drivePreviewUri.value = null
            _drivePreviewError.value = e.message ?: "Preview failed for ${item.originalFilename}"
        }
    }

    fun stopDrivePreview() {
        try {
            drivePreviewPlayer?.stop()
            drivePreviewPlayer?.clearMediaItems()
        } catch (_: Exception) {}
        _drivePreviewUri.value = null
        _isDrivePreviewPlaying.value = false
        _drivePreviewError.value = null
    }

    fun dismissDriveSelectionModal() {
        stopDrivePreview()
        _driveScanState.value = DriveScanSessionState()
    }

    fun importSelectedDriveItems() {
        val stagedItems = _driveScanState.value.items
        if (stagedItems.none { it.isSelected }) return
        stopDrivePreview()
        _driveScanState.value = DriveScanSessionState()
        driveImportCancellation.set(false)

        viewModelScope.launch {
            val result = googleDriveImporter.importStagedItems(
                items = stagedItems,
                existingSongs = allSongs.value,
                cancellationFlag = driveImportCancellation
            )
            if ((result.getOrNull() ?: 0) > 0) {
                repository.rescanLibrary()
                kotlinx.coroutines.delay(1200)
                repository.rescanLibrary()
            }
        }
    }

    fun cancelDriveImport() {
        driveImportCancellation.set(true)
    }

    fun resetDriveImportState() {
        googleDriveImporter.resetImportState()
    }

    fun fetchYouTubeUrl(urlOrId: String) {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) {
            _youTubeUrlError.value = "Please paste a YouTube or Google Drive URL"
            return
        }
        if (com.sonora.music.data.importer.GoogleDriveImporter.isGoogleDriveUrl(trimmed)) {
            importGoogleDriveUrl(trimmed)
            return
        }
        if (!youTubeUrlParser.isValidYouTubeUrl(trimmed)) {
            _youTubeUrlError.value = "Unsupported link format. Please provide a YouTube, YouTube Music, or Google Drive URL."
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
        quality: com.sonora.music.data.model.AudioQuality = defaultDownloadQuality.value,
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
        quality: com.sonora.music.data.model.AudioQuality = defaultDownloadQuality.value,
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
            val effectiveSong = if (song.albumArtUriString.isNullOrBlank() || song.albumArtUriString?.contains("albumart") == true) {
                val thumb = downloadHistory.value.find { 
                    it.title.equals(title.trim(), ignoreCase = true) ||
                    (uriString != null && it.mediaStoreUri == uriString)
                }?.thumbnailUrl
                if (!thumb.isNullOrBlank()) song.copy(albumArtUriString = thumb) else song
            } else {
                song
            }
            audioController.playSong(effectiveSong, listOf(effectiveSong))
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
                val effectiveSong = if (existing.albumArtUriString.isNullOrBlank() || existing.albumArtUriString?.contains("albumart") == true) {
                    val thumb = activeDownloadState.value.thumbnailUrl.takeIf { !it.isNullOrBlank() }
                        ?: downloadHistory.value.find { it.title.equals(title.trim(), ignoreCase = true) }?.thumbnailUrl
                    if (!thumb.isNullOrBlank()) existing.copy(albumArtUriString = thumb) else existing
                } else {
                    existing
                }
                audioController.playSong(effectiveSong, listOf(effectiveSong))
                return true
            }

            val driveMatch = driveImportState.value.takeIf { it.lastImportedUri == uriString }
            val mime = when {
                uriString.endsWith(".flac", ignoreCase = true) -> "audio/flac"
                uriString.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                uriString.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                uriString.endsWith(".aac", ignoreCase = true) -> "audio/aac"
                uriString.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
                uriString.endsWith(".opus", ignoreCase = true) -> "audio/opus"
                uriString.endsWith(".wma", ignoreCase = true) -> "audio/x-ms-wma"
                uriString.endsWith(".webm", ignoreCase = true) -> "audio/webm"
                else -> "audio/mpeg"
            }

            val fallbackDurationMs = driveMatch?.lastImportedDurationMs?.takeIf { it > 0L }
                ?: (activeDownloadState.value.durationSeconds * 1000L).takeIf { it > 0L }
                ?: 0L
            val thumbUrl = driveMatch?.lastImportedArtworkUri?.takeIf { !it.isNullOrBlank() }
                ?: activeDownloadState.value.thumbnailUrl.takeIf { !it.isNullOrBlank() }
                ?: downloadHistory.value.find { it.mediaStoreUri == uriString || it.title.equals(title.trim(), ignoreCase = true) }?.thumbnailUrl
            val resolvedArtist = driveMatch?.lastImportedArtist?.takeIf { it.isNotBlank() }
                ?: artist.ifBlank { "Unknown Artist" }

            val tempSong = Song(
                id = System.currentTimeMillis(),
                title = title,
                artist = resolvedArtist,
                album = "Sonora Imports",
                durationMs = fallbackDurationMs,
                contentUriString = uriString,
                albumArtUriString = thumbUrl,
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
        driveScanJob?.cancel()
        try {
            drivePreviewPlayer?.release()
        } catch (_: Exception) {}
        drivePreviewPlayer = null
        downloadManager.unregister()
        // Do not cancel background downloads on ViewModel destroy so downloads continue in background
    }
}
