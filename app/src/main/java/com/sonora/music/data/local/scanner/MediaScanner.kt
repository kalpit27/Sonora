package com.sonora.music.data.local.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sonora.music.data.local.database.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {

    suspend fun scanLocalAudio(): List<SongEntity> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<SongEntity>()

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )

        // Filter out ringtones/notifications, and sound files under 5 seconds
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            val cursor = context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeTypeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = it.getString(titleCol)?.trim()
                    val rawArtist = it.getString(artistCol)?.trim()
                    val rawAlbum = it.getString(albumCol)?.trim()
                    val albumId = it.getLong(albumIdCol)
                    val durationMs = it.getLong(durationCol)
                    val trackNumber = it.getInt(trackCol)
                    val year = it.getInt(yearCol)
                    val dateAdded = it.getLong(dateAddedCol)
                    val mimeType = it.getString(mimeTypeCol) ?: "audio/*"
                    val filePath = if (dataCol != -1) it.getString(dataCol) else null

                    val folderName = if (!filePath.isNullOrBlank()) {
                        try {
                            File(filePath).parentFile?.name ?: "Internal Storage"
                        } catch (e: Exception) {
                            "Music"
                        }
                    } else {
                        "Music"
                    }

                    val title = if (rawTitle.isNullOrBlank() || rawTitle == "<unknown>") {
                        filePath?.let { path -> File(path).nameWithoutExtension } ?: "Unknown Title"
                    } else {
                        rawTitle
                    }

                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }

                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                        "Unknown Album"
                    } else {
                        rawAlbum
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    songsList.add(
                        SongEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            contentUriString = contentUri,
                            albumId = albumId,
                            albumArtUriString = albumArtUri,
                            trackNumber = trackNumber,
                            year = year,
                            dateAdded = dateAdded,
                            folderName = folderName,
                            mimeType = mimeType,
                            isFavorite = false,
                            playCount = 0,
                            lastPlayedTimestamp = 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songsList
    }
}
