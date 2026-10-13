package com.sonora.music.data.model

enum class DownloadStatus {
    IDLE,
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadTask(
    val id: Long,
    val title: String,
    val artist: String = "",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val localUri: String? = null,
    val errorMessage: String? = null
)

data class ImportedTrack(
    val title: String,
    val artist: String = "",
    val directUrl: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    val downloadProgress: Int = 0
)

data class ImportedPlaylistResult(
    val playlistName: String,
    val description: String = "",
    val source: String, // e.g. "Spotify", "YouTube Music", "Track List"
    val tracks: List<ImportedTrack>
)

data class MatchedPlaylistSummary(
    val playlistName: String,
    val description: String = "",
    val source: String,
    val matchedSongs: List<Song>,
    val missingTracks: List<ImportedTrack>
) {
    val totalTracks: Int
        get() = matchedSongs.size + missingTracks.size

    val matchPercentage: Float
        get() = if (totalTracks > 0) (matchedSongs.size.toFloat() / totalTracks.toFloat()) else 0f
}

