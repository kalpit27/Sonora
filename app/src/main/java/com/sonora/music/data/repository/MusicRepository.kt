package com.sonora.music.data.repository

import com.sonora.music.data.local.database.SonoraDatabase
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import com.sonora.music.data.local.database.entity.HistoryEntity
import com.sonora.music.data.local.database.entity.ListeningSessionEntity
import com.sonora.music.data.local.database.entity.PlaylistEntity
import com.sonora.music.data.local.database.entity.SongEntity
import com.sonora.music.data.local.preferences.SonoraPreferences
import com.sonora.music.data.local.scanner.MediaScanner
import com.sonora.music.data.model.Album
import com.sonora.music.data.model.AnalyticsDashboardData
import com.sonora.music.data.model.AnalyticsRangeFilter
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.ArtistSortOption
import com.sonora.music.data.model.DuplicateGroup
import com.sonora.music.data.model.ListeningDnaStats
import com.sonora.music.data.model.MusicFolder
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.SmartAlbumSummary
import com.sonora.music.data.model.SmartAlbumType
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.TimeCapsuleGroup
import com.sonora.music.data.model.YearCollectionGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MusicRepository(
    private val database: SonoraDatabase,
    private val scanner: MediaScanner,
    val preferences: SonoraPreferences
) {
    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()
    private val historyDao = database.historyDao()
    val downloadHistoryDao = database.downloadHistoryDao()

    private val trackingMutex = Mutex()
    private var activeSessionId: Long? = null
    private var activeSessionSongId: Long? = null
    private var activeSessionDurationMs: Long = 0L
    private var activeSessionCompleted: Boolean = false
    private var activeSessionSkipped: Boolean = false

    val downloadHistory: Flow<List<DownloadHistoryEntity>> =
        downloadHistoryDao.getAllHistory()

    val listeningSessions: Flow<List<ListeningSessionEntity>> =
        historyDao.getAllListeningSessionsFlow()

    private fun resolveSongWithArtwork(
        entity: SongEntity,
        historyList: List<DownloadHistoryEntity>
    ): Song {
        val song = entity.toSong()
        if (!entity.customArtworkUriString.isNullOrBlank()) {
            return song
        }
        val historyMatch = historyList.find { h ->
            !h.thumbnailUrl.isNullOrBlank() && (
                h.mediaStoreUri == entity.contentUriString ||
                    h.title.equals(entity.title, ignoreCase = true) ||
                    entity.title.contains(h.title, ignoreCase = true) ||
                    h.title.contains(entity.title, ignoreCase = true)
                )
        }
        return if (historyMatch != null && !historyMatch.thumbnailUrl.isNullOrBlank()) {
            song.copy(albumArtUriString = historyMatch.thumbnailUrl)
        } else {
            song
        }
    }

    val allSongs: Flow<List<Song>> = combine(
        songDao.getAllSongs(),
        downloadHistoryDao.getAllHistory()
    ) { songsList, historyList ->
        songsList.map { entity -> resolveSongWithArtwork(entity, historyList) }
    }

    val favoriteSongs: Flow<List<Song>> = allSongs.map { songs ->
        songs.filter { it.isFavorite }
    }

    val recentlyPlayedSongs: Flow<List<Song>> = allSongs.map { songs ->
        songs.filter { it.lastPlayedTimestamp > 0 }
            .sortedByDescending { it.lastPlayedTimestamp }
    }

    val recentlyAddedSongs: Flow<List<Song>> = allSongs.map { songs ->
        songs.sortedByDescending { it.dateAdded }
    }

    val albums: Flow<List<Album>> = allSongs.map { songs ->
        LibraryAnalyticsEngine.buildAlbums(songs)
    }

    val artists: Flow<List<Artist>> = allSongs.map { songs ->
        LibraryAnalyticsEngine.buildArtists(songs, ArtistSortOption.ALPHABETICAL)
    }

    val folders: Flow<List<MusicFolder>> = allSongs.map { songs ->
        songs.groupBy { it.folderName }
            .map { (folderName, songsInFolder) ->
                MusicFolder(
                    name = folderName,
                    path = folderName,
                    songCount = songsInFolder.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val playlists: Flow<List<Playlist>> = playlistDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                createdAt = entity.createdAt
            )
        }
    }

    val smartAlbumSummaries: Flow<List<SmartAlbumSummary>> = combine(
        allSongs,
        listeningSessions,
        preferences.shortTrackMaxDurationMsFlow
    ) { songs, sessions, shortMaxMs ->
        LibraryAnalyticsEngine.getSmartAlbumSummaries(songs, sessions, shortMaxMs)
    }

    val timeCapsuleGroups: Flow<List<TimeCapsuleGroup>> = combine(
        allSongs,
        listeningSessions
    ) { songs, sessions ->
        LibraryAnalyticsEngine.buildTimeCapsuleGroups(songs, sessions)
    }

    val byYearGroups: Flow<List<YearCollectionGroup>> = allSongs.map { songs ->
        LibraryAnalyticsEngine.buildByYearGroups(songs)
    }

    val duplicateGroups: Flow<List<DuplicateGroup>> = allSongs.map { songs ->
        LibraryAnalyticsEngine.findDuplicateSongs(songs)
    }

    val listeningDna: Flow<ListeningDnaStats> = combine(
        allSongs,
        listeningSessions
    ) { songs, sessions ->
        LibraryAnalyticsEngine.computeListeningDna(songs, sessions)
    }

    fun getAnalyticsDashboard(
        filterFlow: Flow<AnalyticsRangeFilter>,
        offsetFlow: Flow<Int>
    ): Flow<AnalyticsDashboardData> = combine(
        allSongs,
        listeningSessions,
        filterFlow,
        offsetFlow
    ) { songs, sessions, filter, offset ->
        LibraryAnalyticsEngine.computeAnalyticsDashboard(songs, sessions, filter, offset)
    }

    fun getSmartAlbumSongs(type: SmartAlbumType): Flow<List<Song>> = combine(
        allSongs,
        listeningSessions,
        preferences.shortTrackMaxDurationMsFlow
    ) { songs, sessions, shortMaxMs ->
        LibraryAnalyticsEngine.getSmartAlbumSongs(type, songs, sessions, shortMaxMs)
    }

    fun getPlaylistWithSongCount(playlistId: Long): Flow<Int> =
        playlistDao.getSongCountForPlaylist(playlistId)

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> =
        combine(
            playlistDao.getSongsForPlaylist(playlistId),
            downloadHistoryDao.getAllHistory()
        ) { entities, historyList ->
            entities.map { entity -> resolveSongWithArtwork(entity, historyList) }
        }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updatePlaylist(id: Long, name: String, description: String = "") {
        playlistDao.updatePlaylist(
            PlaylistEntity(
                id = id,
                name = name.trim(),
                description = description.trim()
            )
        )
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylist(id)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        playlistDao.addSongsToPlaylist(playlistId, songIds)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun reorderPlaylist(playlistId: Long, songIdsInOrder: List<Long>) {
        playlistDao.reorderPlaylistSongs(playlistId, songIdsInOrder)
    }

    suspend fun toggleFavorite(songId: Long): Boolean {
        return songDao.toggleFavoriteAtomic(songId)
    }

    suspend fun updateSongMetadata(
        songId: Long,
        title: String?,
        artist: String?,
        album: String?,
        year: Int?,
        artworkUriString: String?
    ) {
        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
        val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
        val cleanAlbum = album?.trim()?.takeIf { it.isNotBlank() }
        val cleanYear = year?.takeIf { it > 0 }
        val cleanArt = artworkUriString?.trim()?.takeIf { it.isNotBlank() }
        songDao.updateSongMetadata(
            id = songId,
            customTitle = cleanTitle,
            customArtist = cleanArtist,
            customAlbum = cleanAlbum,
            customYear = cleanYear,
            customArtworkUriString = cleanArt
        )
    }

    suspend fun updateAlbumArtwork(songIds: List<Long>, artworkUriString: String?) {
        if (songIds.isEmpty()) return
        val cleanArt = artworkUriString?.trim()?.takeIf { it.isNotBlank() }
        songDao.updateArtworkForSongs(songIds, cleanArt)
    }

    suspend fun deleteSongFromLibrary(songId: Long) {
        songDao.deleteSongById(songId)
        historyDao.deleteSessionsForSong(songId)
    }

    suspend fun onTrackStarted(song: Song) {
        trackingMutex.withLock {
            val isHistoryEnabled = preferences.isHistoryEnabledFlow.first()
            val now = System.currentTimeMillis()
            songDao.recordPlayback(song.id, now)
            if (isHistoryEnabled) {
                historyDao.insertHistory(
                    HistoryEntity(
                        songId = song.id,
                        playedAt = now
                    )
                )
                val sessionId = historyDao.insertListeningSession(
                    ListeningSessionEntity(
                        songId = song.id,
                        songTitle = song.title,
                        artistName = song.artist,
                        albumName = song.album,
                        timestampMs = now,
                        durationListenedMs = 0L,
                        wasCompleted = false,
                        wasSkipped = false
                    )
                )
                activeSessionId = sessionId
                activeSessionSongId = song.id
                activeSessionDurationMs = 0L
                activeSessionCompleted = false
                activeSessionSkipped = false
            } else {
                activeSessionId = null
                activeSessionSongId = null
            }
        }
    }

    suspend fun onListeningDelta(
        song: Song,
        deltaMs: Long,
        completed: Boolean,
        skipped: Boolean
    ) {
        trackingMutex.withLock {
            val now = System.currentTimeMillis()
            if (deltaMs > 0L) {
                songDao.addListeningTime(song.id, deltaMs)
            }
            if (completed) {
                songDao.recordSongCompleted(song.id, now)
            }
            if (skipped) {
                songDao.recordSongSkipped(song.id)
            }
            val sessionId = activeSessionId
            if (sessionId != null && activeSessionSongId == song.id) {
                activeSessionDurationMs += deltaMs.coerceAtLeast(0L)
                activeSessionCompleted = activeSessionCompleted || completed
                activeSessionSkipped = activeSessionSkipped || skipped
                historyDao.updateListeningSession(
                    sessionId = sessionId,
                    durationListenedMs = activeSessionDurationMs,
                    wasCompleted = activeSessionCompleted,
                    wasSkipped = activeSessionSkipped
                )
            }
        }
    }

    suspend fun recordSongPlayed(songId: Long) {
        val song = songDao.getSongById(songId)?.toSong() ?: return
        onTrackStarted(song)
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    suspend fun clearListeningAnalytics() {
        historyDao.clearListeningSessions()
    }

    suspend fun clearDownloadHistory() {
        downloadHistoryDao.clearAll()
    }

    suspend fun deleteDownloadHistoryItem(id: Long) {
        downloadHistoryDao.deleteById(id)
    }

    suspend fun rescanLibrary(): Int {
        val scanned = scanner.scanLocalAudio()
        songDao.syncMediaStoreSongs(scanned)
        return scanned.size
    }

    fun search(query: String): Flow<List<Song>> {
        val cleanQuery = query.trim()
        return if (cleanQuery.isBlank()) {
            flow { emit(emptyList()) }
        } else {
            combine(
                songDao.searchSongs(cleanQuery),
                downloadHistoryDao.getAllHistory()
            ) { list, historyList ->
                list.map { entity -> resolveSongWithArtwork(entity, historyList) }
            }
        }
    }

    fun sortSongs(songs: List<Song>, option: SortOption, order: SortOrder): List<Song> {
        val comparator = when (option) {
            SortOption.TITLE -> compareBy<Song> { it.title.lowercase() }
            SortOption.ARTIST -> compareBy<Song> { it.artist.lowercase() }
            SortOption.ALBUM -> compareBy<Song> { it.album.lowercase() }
            SortOption.DATE_ADDED -> compareBy<Song> { it.dateAdded }
            SortOption.PLAY_COUNT -> compareBy<Song> { it.playCount }.thenBy { it.totalListeningTimeMs }
            SortOption.DURATION -> compareBy<Song> { it.durationMs }
        }
        return if (order == SortOrder.ASCENDING) {
            songs.sortedWith(comparator)
        } else {
            songs.sortedWith(comparator.reversed())
        }
    }
}
