package com.sonora.music.ui.navigation

sealed class SonoraDestination(val route: String) {
    object Main : SonoraDestination("main")
    object Search : SonoraDestination("search")
    object NowPlaying : SonoraDestination("now_playing")
    object Queue : SonoraDestination("queue")
    object Settings : SonoraDestination("settings")
    object Permissions : SonoraDestination("permissions")
    object ImportAudio : SonoraDestination("import_audio")
    object YouTubeImport : SonoraDestination("youtube_import")

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

    object SmartAlbumDetail : SonoraDestination("smart_album/{type}") {
        fun createRoute(type: com.sonora.music.data.model.SmartAlbumType) = "smart_album/${type.name}"
    }

    object ListeningDna : SonoraDestination("listening_dna")
    object MyAnalytics : SonoraDestination("my_analytics")
    object Duplicates : SonoraDestination("duplicates")
}

enum class MainTab(val title: String) {
    SONGS("Songs"),
    SMART_ALBUMS("Smart"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    FOLDERS("Folders")
}
