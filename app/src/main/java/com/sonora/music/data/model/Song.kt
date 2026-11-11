package com.sonora.music.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUriString: String,
    val albumId: Long = 0,
    val albumArtUriString: String? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val folderName: String = "",
    val mimeType: String = "",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0,
    val skipCount: Int = 0,
    val totalListeningTimeMs: Long = 0,
    val lastCompletedTimestamp: Long = 0,
    val originalTitle: String = title,
    val originalArtist: String = artist,
    val originalAlbum: String = album
) {
    val contentUri: Uri
        get() {
            val str = contentUriString.trim()
            return if (str.startsWith("content://") || str.startsWith("file://") || str.startsWith("http://") || str.startsWith("https://")) {
                Uri.parse(str)
            } else {
                Uri.fromFile(java.io.File(str))
            }
        }

    val albumArtUri: Uri?
        get() = albumArtUriString?.trim()?.let { str ->
            if (str.startsWith("content://") || str.startsWith("file://") || str.startsWith("http://") || str.startsWith("https://")) {
                Uri.parse(str)
            } else {
                Uri.fromFile(java.io.File(str))
            }
        }

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
