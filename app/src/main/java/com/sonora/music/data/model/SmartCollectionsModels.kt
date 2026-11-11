package com.sonora.music.data.model

enum class SmartAlbumType(
    val title: String,
    val description: String
) {
    MOST_PLAYED("Most Played", "Ranked by total play count"),
    TIME_CAPSULE("Time Capsule", "Your listening history by month & year"),
    RECENTLY_PLAYED("Recently Played", "Latest tracks in chronological order"),
    RECENTLY_ADDED("Recently Added", "Newly downloaded and imported songs"),
    FAVORITES("Liked Songs", "All your favorited tracks offline"),
    MOST_SKIPPED("Most Skipped", "Tracks frequently skipped"),
    NEVER_PLAYED("Never Played", "Local tracks waiting for a first listen"),
    TOP_50("Top 50", "Your top 50 most-played songs"),
    RECENTLY_COMPLETED("Recently Completed", "Songs recently played to completion"),
    LONGEST_PLAYED("Longest Played", "Ranked by cumulative listening time"),
    SHORT_TRACKS("Short Tracks", "Tracks shorter than your threshold"),
    OFFLINE_COLLECTION("Offline Collection", "All locally stored audio tracks"),
    BY_YEAR("By Year", "Library organized by release year")
}

data class SmartAlbumSummary(
    val type: SmartAlbumType,
    val subtitle: String,
    val songCount: Int,
    val previewArtUriString: String? = null,
    val totalDurationMs: Long = 0L
)

data class TimeCapsuleGroup(
    val yearMonthKey: String,
    val label: String,
    val songs: List<Song>,
    val totalListeningMs: Long,
    val playCount: Int
)

data class YearCollectionGroup(
    val year: Int,
    val label: String,
    val songs: List<Song>
)

data class DuplicateGroup(
    val signature: String,
    val displayTitle: String,
    val songs: List<Song>
)

data class ArtistStat(
    val artistName: String,
    val totalPlayCount: Int,
    val totalListeningTimeMs: Long,
    val songCount: Int,
    val artworkUriString: String? = null
)

data class SongStat(
    val songId: Long,
    val title: String,
    val artist: String,
    val playCount: Int,
    val totalListeningTimeMs: Long,
    val artworkUriString: String? = null
)

data class AlbumStat(
    val albumTitle: String,
    val artist: String,
    val totalPlayCount: Int,
    val totalListeningTimeMs: Long,
    val trackCount: Int,
    val artworkUriString: String? = null
)

data class DayStat(
    val dateKey: String,
    val displayLabel: String,
    val totalListeningTimeMs: Long,
    val sessionCount: Int
)

data class TimePeriodPoint(
    val key: String,
    val label: String,
    val fullLabel: String,
    val durationMs: Long,
    val playCount: Int
) {
    val hours: Float
        get() = durationMs / 3_600_000f
}

data class HourlyActivityPoint(
    val hour: Int,
    val label: String,
    val durationMs: Long,
    val playCount: Int
)

enum class AnalyticsRangeFilter(val label: String) {
    DAYS_7("7 Days"),
    DAYS_30("30 Days"),
    MONTHS_12("12 Months")
}

data class ListeningDnaStats(
    val mostListenedArtist: ArtistStat? = null,
    val mostPlayedSong: SongStat? = null,
    val totalListeningTimeMs: Long = 0L,
    val top5Artists: List<ArtistStat> = emptyList(),
    val monthlyHistory: List<TimePeriodPoint> = emptyList(),
    val mostActiveDay: DayStat? = null,
    val totalSongsPlayed: Int = 0,
    val listeningTrends: List<TimePeriodPoint> = emptyList(),
    val favoriteAlbums: List<AlbumStat> = emptyList(),
    val artistListeningTimes: List<ArtistStat> = emptyList()
)

data class AnalyticsDashboardData(
    val filter: AnalyticsRangeFilter = AnalyticsRangeFilter.DAYS_7,
    val rangeOffset: Int = 0,
    val rangeLabel: String = "",
    val canGoNext: Boolean = false,
    // Overview cards
    val totalListeningMs: Long = 0L,
    val dailyAverageMs: Long = 0L,
    val songsPlayedCount: Int = 0,
    val mostActiveDay: DayStat? = null,
    // Section A: Primary interactive line graph (respects 7D/30D/12M & offset)
    val mainLinePoints: List<TimePeriodPoint> = emptyList(),
    // Section C: Additional analytics graphs
    val listeningHoursByDay: List<TimePeriodPoint> = emptyList(),
    val weeklyTrends: List<TimePeriodPoint> = emptyList(),
    val monthlyTrends: List<TimePeriodPoint> = emptyList(),
    val mostPlayedArtists: List<ArtistStat> = emptyList(),
    val top10Songs: List<SongStat> = emptyList(),
    val listeningTimeByArtist: List<ArtistStat> = emptyList(),
    val hourlyActivity: List<HourlyActivityPoint> = emptyList(),
    // Section D: Additional insights
    val mostListenedArtist: ArtistStat? = null,
    val mostPlayedSong: SongStat? = null,
    val longestListeningDay: DayStat? = null,
    val averageDailyListeningMs: Long = 0L,
    val highestListeningMonth: TimePeriodPoint? = null,
    val mostActiveHour: HourlyActivityPoint? = null,
    val currentWeekListeningMs: Long = 0L,
    val previousWeekListeningMs: Long = 0L,
    val weekComparisonDeltaPercent: Float? = null,
    val currentYearListeningMs: Long = 0L
)

