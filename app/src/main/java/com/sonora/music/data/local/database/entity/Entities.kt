package com.sonora.music.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sonora.music.data.model.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUriString: String,
    val albumId: Long,
    val albumArtUriString: String?,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
    val folderName: String,
    val mimeType: String,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUriString = contentUriString,
        albumId = albumId,
        albumArtUriString = albumArtUriString,
        trackNumber = trackNumber,
        year = year,
        dateAdded = dateAdded,
        folderName = folderName,
        mimeType = mimeType,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp
    )
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["songId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val orderIndex: Int
)

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["songId"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis()
)
