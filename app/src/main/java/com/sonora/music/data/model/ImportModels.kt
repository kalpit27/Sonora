package com.sonora.music.data.model

enum class DownloadStatus {
    IDLE,
    PENDING,
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AudioQuality(val label: String, val bitrateKbps: Int) {
    HIGH_320("320 kbps (High Quality)", 320),
    VERY_HIGH_256("256 kbps (Very High)", 256),
    MEDIUM_192("192 kbps (Balanced)", 192),
    STANDARD_128("128 kbps (Compact)", 128);

    fun estimateSizeBytes(durationSeconds: Long): Long {
        val safeDuration = if (durationSeconds > 0) durationSeconds else 210L // ~3.5 mins default
        return (safeDuration * (bitrateKbps * 1000L / 8L))
    }

    fun formatEstimatedSize(durationSeconds: Long): String {
        val bytes = estimateSizeBytes(durationSeconds)
        val mb = bytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }

    companion object {
        fun fromLabel(label: String): AudioQuality {
            return entries.find { it.label == label || it.label.contains(label) || it.name.equals(label, ignoreCase = true) }
                ?: HIGH_320
        }
    }
}

data class YouTubeVideoInfo(
    val videoId: String,
    val originalUrl: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 210L,
    val description: String = "",
    val authorUrl: String? = null
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class ActiveDownloadState(
    val downloadId: Long = 0L,
    val videoId: String? = null,
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val quality: AudioQuality = AudioQuality.HIGH_320,
    val errorMessage: String? = null,
    val localUri: String? = null,
    val isDuplicate: Boolean = false,
    val stageDescription: String? = null,
    val destinationPath: String? = null,
    val durationSeconds: Long = 210L
)

data class YouTubePlaylistItem(
    val videoId: String,
    val title: String,
    val channelTitle: String = "",
    val thumbnailUrl: String = "",
    val originalUrl: String = "",
    val isSelected: Boolean = true,
    val durationSeconds: Long = 210L
)

data class YouTubePlaylistInfo(
    val playlistId: String,
    val title: String,
    val author: String = "",
    val originalUrl: String = "",
    val tracks: List<YouTubePlaylistItem> = emptyList()
)

data class QueueOverallProgress(
    val totalTracks: Int = 0,
    val completedTracks: Int = 0,
    val failedTracks: Int = 0,
    val currentTrackIndex: Int = 0,
    val currentTrackTitle: String = "",
    val overallPercentage: Int = 0,
    val isQueueActive: Boolean = false,
    val targetPlaylistName: String? = null,
    val targetPlaylistId: Long? = null
)

data class QueueDownloadItem(
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val downloadUrl: String = "",
    val quality: AudioQuality = AudioQuality.HIGH_320,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val localUri: String? = null,
    val isDuplicate: Boolean = false
)

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

