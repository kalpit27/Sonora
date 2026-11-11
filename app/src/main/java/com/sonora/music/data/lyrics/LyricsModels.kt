package com.sonora.music.data.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsResult(
    val trackName: String,
    val artistName: String,
    val isSynced: Boolean,
    val lines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val source: String = "LRCLIB"
)

data class LyricsUiState(
    val isLoading: Boolean = false,
    val lyrics: LyricsResult? = null,
    val activeLineIndex: Int = -1,
    val errorMessage: String? = null
)

data class LyricsSearchResultItem(
    val id: Long = 0L,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val durationSec: Int = 0,
    val isSynced: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)

