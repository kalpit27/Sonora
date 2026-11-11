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
    val lastPlayedTimestamp: Long = 0,
    val skipCount: Int = 0,
    val totalListeningTimeMs: Long = 0,
    val lastCompletedTimestamp: Long = 0,
    val customTitle: String? = null,
    val customArtist: String? = null,
    val customAlbum: String? = null,
    val customYear: Int? = null,
    val customArtworkUriString: String? = null
) {
    fun toSong(): Song = Song(
        id = id,
        title = customTitle?.takeIf { it.isNotBlank() } ?: title,
        artist = customArtist?.takeIf { it.isNotBlank() } ?: artist,
        album = customAlbum?.takeIf { it.isNotBlank() } ?: album,
        durationMs = durationMs,
        contentUriString = contentUriString,
        albumId = albumId,
        albumArtUriString = customArtworkUriString?.takeIf { it.isNotBlank() } ?: albumArtUriString,
        trackNumber = trackNumber,
        year = customYear ?: year,
        dateAdded = dateAdded,
        folderName = folderName,
        mimeType = mimeType,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp,
        skipCount = skipCount,
        totalListeningTimeMs = totalListeningTimeMs,
        lastCompletedTimestamp = lastCompletedTimestamp,
        originalTitle = title,
        originalArtist = artist,
        originalAlbum = album
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

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String? = null,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val quality: String = "320 kbps",
    val fileSizeBytes: Long = 0L,
    val status: String, // COMPLETED, FAILED, CANCELLED, DOWNLOADING
    val localFilePath: String? = null,
    val mediaStoreUri: String? = null,
    val downloadUrl: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val downloadUrl: String = "",
    val quality: String = "320 kbps",
    val status: String = "PENDING", // PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELLED
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val localUri: String? = null,
    val isDuplicate: Boolean = false,
    val playlistTitle: String? = null,
    val orderIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toQueueDownloadItem(): com.sonora.music.data.model.QueueDownloadItem {
        val q = com.sonora.music.data.model.AudioQuality.fromLabel(quality)
        val s = try {
            com.sonora.music.data.model.DownloadStatus.valueOf(status)
        } catch (e: Exception) {
            com.sonora.music.data.model.DownloadStatus.PENDING
        }
        return com.sonora.music.data.model.QueueDownloadItem(
            id = id,
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            downloadUrl = downloadUrl,
            quality = q,
            status = s,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            errorMessage = errorMessage,
            localUri = localUri,
            isDuplicate = isDuplicate
        )
    }
}

@Entity(
    tableName = "listening_sessions",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["timestampMs"])
    ]
)
data class ListeningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val songTitle: String,
    val artistName: String,
    val albumName: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationListenedMs: Long = 0,
    val wasCompleted: Boolean = false,
    val wasSkipped: Boolean = false
)


