package com.sonora.music.data.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val year: Int = 0,
    val albumArtUriString: String? = null
) {
    val albumArtUri: Uri?
        get() = albumArtUriString?.let { Uri.parse(it) }
}

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int = 0
)

data class Playlist(
    val id: Long,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val trackCount: Int = 0
)

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
