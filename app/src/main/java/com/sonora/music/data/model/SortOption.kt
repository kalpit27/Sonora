package com.sonora.music.data.model

enum class SortOption(val displayName: String) {
    TITLE("Song Name"),
    ARTIST("Artist Name"),
    ALBUM("Album Name"),
    DATE_ADDED("Date Added"),
    PLAY_COUNT("Play Count"),
    DURATION("Duration")
}

enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class ArtistSortOption(val displayName: String) {
    ALPHABETICAL("Alphabetical"),
    RECENTLY_ADDED("Recently Added"),
    MOST_PLAYED("Most Played")
}
