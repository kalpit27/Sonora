package com.sonora.music.data.repository

import com.sonora.music.data.local.database.SonoraDatabase
import com.sonora.music.data.local.database.entity.HistoryEntity
import com.sonora.music.data.local.database.entity.PlaylistEntity
import com.sonora.music.data.local.preferences.SonoraPreferences
import com.sonora.music.data.local.scanner.MediaScanner
import com.sonora.music.data.model.Album
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.MusicFolder
import com.sonora.music.data.model.Playlist
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import com.sonora.music.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val database: SonoraDatabase,
    private val scanner: MediaScanner,
    val preferences: SonoraPreferences
) {
    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()
    private val historyDao = database.historyDao()

    val allSongs: Flow<List<Song>> = songDao.getAllSongs().map { list ->
        list.map { it.toSong() }
    }

    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs().map { list ->
        list.map { it.toSong() }
    }

    val recentlyPlayedSongs: Flow<List<Song>> = songDao.getRecentlyPlayedSongs().map { list ->
        list.map { it.toSong() }
    }

    val recentlyAddedSongs: Flow<List<Song>> = songDao.getRecentlyAddedSongs().map { list ->
        list.map { it.toSong() }
    }

    val albums: Flow<List<Album>> = allSongs.map { songs ->
        songs.groupBy { it.albumId }
            .map { (albumId, songsInAlbum) ->
                val first = songsInAlbum.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = first.artist,
                    trackCount = songsInAlbum.size,
                    year = songsInAlbum.maxOfOrNull { it.year } ?: 0,
                    albumArtUriString = first.albumArtUriString
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    val artists: Flow<List<Artist>> = allSongs.map { songs ->
        songs.groupBy { it.artist.lowercase() }
            .toList()
            .mapIndexed { index, (_, songsForArtist) ->
                val artistName = songsForArtist.first().artist
                val uniqueAlbums = songsForArtist.map { it.albumId }.distinct().size
                Artist(
                    id = index.toLong(),
                    name = artistName,
                    trackCount = songsForArtist.size,
                    albumCount = uniqueAlbums
                )
            }
            .sortedBy { it.name.lowercase() }
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
            // Track count will be observed dynamically
            Playlist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                createdAt = entity.createdAt
            )
        }
    }

    fun getPlaylistWithSongCount(playlistId: Long): Flow<Int> =
        playlistDao.getSongCountForPlaylist(playlistId)

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.getSongsForPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
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

    suspend fun toggleFavorite(songId: Long, currentFavorite: Boolean) {
        songDao.setFavorite(songId, !currentFavorite)
    }

    suspend fun recordSongPlayed(songId: Long) {
        val isHistoryEnabled = preferences.isHistoryEnabledFlow.first()
        val now = System.currentTimeMillis()
        songDao.recordPlayback(songId, now)
        if (isHistoryEnabled) {
            historyDao.insertHistory(
                HistoryEntity(
                    songId = songId,
                    playedAt = now
                )
            )
        }
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
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
            songDao.searchSongs(cleanQuery).map { list ->
                list.map { it.toSong() }
            }
        }
    }

    // Sort songs helper
    fun sortSongs(songs: List<Song>, option: SortOption, order: SortOrder): List<Song> {
        val comparator = when (option) {
            SortOption.TITLE -> compareBy<Song> { it.title.lowercase() }
            SortOption.ARTIST -> compareBy<Song> { it.artist.lowercase() }
            SortOption.ALBUM -> compareBy<Song> { it.album.lowercase() }
            SortOption.DURATION -> compareBy<Song> { it.durationMs }
            SortOption.DATE_ADDED -> compareBy<Song> { it.dateAdded }
        }
        return if (order == SortOrder.ASCENDING) {
            songs.sortedWith(comparator)
        } else {
            songs.sortedWith(comparator.reversed())
        }
    }
}
