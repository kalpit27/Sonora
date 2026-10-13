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
import kotlinx.coroutines.launch

@UnstableApi
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SonoraApp
    private val repository = app.repository
    private val audioController = app.audioController
    val downloadManager = com.sonora.music.data.downloader.SonoraDownloadManager(application)
    val downloadTasks = downloadManager.downloadTasks

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

    override fun onCleared() {
        super.onCleared()
        downloadManager.unregister()
    }
}
