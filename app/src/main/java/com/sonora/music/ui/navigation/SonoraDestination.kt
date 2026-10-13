package com.sonora.music.ui.navigation

sealed class SonoraDestination(val route: String) {
    object Main : SonoraDestination("main")
    object Search : SonoraDestination("search")
    object NowPlaying : SonoraDestination("now_playing")
    object Queue : SonoraDestination("queue")
    object Settings : SonoraDestination("settings")
    object Permissions : SonoraDestination("permissions")

    // Detail screens
    object AlbumDetail : SonoraDestination("album_detail/{albumId}") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }

    object ArtistDetail : SonoraDestination("artist_detail/{artistName}") {
        fun createRoute(artistName: String) = "artist_detail/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }

    object PlaylistDetail : SonoraDestination("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist_detail/$playlistId"
    }

    object FolderDetail : SonoraDestination("folder_detail/{folderName}") {
        fun createRoute(folderName: String) = "folder_detail/${java.net.URLEncoder.encode(folderName, "UTF-8")}"
    }
}

enum class MainTab(val title: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    FOLDERS("Folders")
}
