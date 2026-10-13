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
    val lastPlayedTimestamp: Long = 0
) {
    val contentUri: Uri
        get() = Uri.parse(contentUriString)

    val albumArtUri: Uri?
        get() = albumArtUriString?.let { Uri.parse(it) }

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
