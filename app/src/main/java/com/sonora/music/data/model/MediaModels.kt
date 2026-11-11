package com.sonora.music.data.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val year: Int = 0,
    val albumArtUriString: String? = null,
    val fallbackAudioUriString: String? = null
) {
    val albumArtUri: Uri?
        get() = albumArtUriString?.let { Uri.parse(it) }
    val fallbackAudioUri: Uri?
        get() = fallbackAudioUriString?.let { Uri.parse(it) }
}

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int = 0,
    val artworkUriString: String? = null,
    val totalListeningTimeMs: Long = 0,
    val totalPlayCount: Int = 0,
    val latestDateAdded: Long = 0,
    val songs: List<Song> = emptyList()
) {
    val artworkUri: Uri?
        get() = artworkUriString?.trim()?.let { str ->
            if (str.startsWith("content://") || str.startsWith("file://") || str.startsWith("http://") || str.startsWith("https://")) {
                Uri.parse(str)
            } else {
                Uri.fromFile(java.io.File(str))
            }
        }
}

data class Playlist(
    val id: Long,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val trackCount: Int = 0,
    val albumArtUriString: String? = null
) {
    val albumArtUri: Uri?
        get() = albumArtUriString?.let { Uri.parse(it) }
}

data class Genre(
    val id: Long,
    val name: String,
    val trackCount: Int = 0
)

data class MusicFolder(
    val name: String,
    val path: String,
    val songCount: Int = 0
)
