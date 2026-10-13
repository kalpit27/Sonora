package com.sonora.music

import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SonoraRepeatMode
import com.sonora.music.data.model.SortOption
import com.sonora.music.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonoraLogicTest {

    private fun createSampleSong(
        id: Long,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        dateAdded: Long = 1000L
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            contentUriString = "content://media/external/audio/media/$id",
            albumId = 1L,
            albumArtUriString = null,
            trackNumber = 1,
            year = 2024,
            dateAdded = dateAdded,
            folderName = "Music",
            mimeType = "audio/mpeg",
            isFavorite = false,
            playCount = 0,
            lastPlayedTimestamp = 0
        )
    }

    @Test
    fun formattedDuration_formatsCorrectly() {
        val song1 = createSampleSong(1, "Track 1", "Artist 1", "Album 1", 65000L) // 1:05
        assertEquals("1:05", song1.formattedDuration)

        val song2 = createSampleSong(2, "Track 2", "Artist 2", "Album 2", 243000L) // 4:03
        assertEquals("4:03", song2.formattedDuration)

        val song3 = createSampleSong(3, "Track 3", "Artist 3", "Album 3", 0L) // 0:00
        assertEquals("0:00", song3.formattedDuration)
    }

    @Test
    fun repeatMode_cyclesCorrectly() {
        var mode = SonoraRepeatMode.OFF
        mode = mode.next()
        assertEquals(SonoraRepeatMode.ALL, mode)

        mode = mode.next()
        assertEquals(SonoraRepeatMode.ONE, mode)

        mode = mode.next()
        assertEquals(SonoraRepeatMode.OFF, mode)
    }

    @Test
    fun playbackProgress_calculatesAccurately() {
        val stateZero = PlaybackUiState(positionMs = 0L, durationMs = 100000L)
        assertEquals(0f, stateZero.progress, 0.001f)

        val stateHalf = PlaybackUiState(positionMs = 50000L, durationMs = 100000L)
        assertEquals(0.5f, stateHalf.progress, 0.001f)

        val stateFull = PlaybackUiState(positionMs = 100000L, durationMs = 100000L)
        assertEquals(1.0f, stateFull.progress, 0.001f)

        val stateOverflow = PlaybackUiState(positionMs = 150000L, durationMs = 100000L)
        assertEquals(1.0f, stateOverflow.progress, 0.001f)
    }

    @Test
    fun sortSongs_byTitle_ascendingAndDescending() {
        val s1 = createSampleSong(1, "Bravo", "Artist", "Album", 1000L)
        val s2 = createSampleSong(2, "Alpha", "Artist", "Album", 2000L)
        val s3 = createSampleSong(3, "Charlie", "Artist", "Album", 3000L)
        val songs = listOf(s1, s2, s3)

        // Title Ascending
        val sortedAsc = songs.sortedWith(compareBy { it.title.lowercase() })
        assertEquals("Alpha", sortedAsc[0].title)
        assertEquals("Bravo", sortedAsc[1].title)
        assertEquals("Charlie", sortedAsc[2].title)

        // Title Descending
        val sortedDesc = songs.sortedWith(compareBy<Song> { it.title.lowercase() }.reversed())
        assertEquals("Charlie", sortedDesc[0].title)
        assertEquals("Bravo", sortedDesc[1].title)
        assertEquals("Alpha", sortedDesc[2].title)
    }

    @Test
    fun sortSongs_byDuration_ascendingAndDescending() {
        val s1 = createSampleSong(1, "Song 1", "Artist", "Album", 300000L)
        val s2 = createSampleSong(2, "Song 2", "Artist", "Album", 120000L)
        val s3 = createSampleSong(3, "Song 3", "Artist", "Album", 240000L)
        val songs = listOf(s1, s2, s3)

        val sortedAsc = songs.sortedWith(compareBy { it.durationMs })
        assertEquals(120000L, sortedAsc[0].durationMs)
        assertEquals(240000L, sortedAsc[1].durationMs)
        assertEquals(300000L, sortedAsc[2].durationMs)

        val sortedDesc = songs.sortedWith(compareBy<Song> { it.durationMs }.reversed())
        assertEquals(300000L, sortedDesc[0].durationMs)
        assertEquals(240000L, sortedDesc[1].durationMs)
        assertEquals(120000L, sortedDesc[2].durationMs)
    }

    @Test
    fun queueReordering_preservesIntegrity() {
        val s1 = createSampleSong(1, "S1", "A1", "Alb", 1000L)
        val s2 = createSampleSong(2, "S2", "A2", "Alb", 2000L)
        val s3 = createSampleSong(3, "S3", "A3", "Alb", 3000L)
        val queue = mutableListOf(s1, s2, s3)

        // Move index 2 ("S3") to index 0
        val moved = queue.removeAt(2)
        queue.add(0, moved)

        assertEquals("S3", queue[0].title)
        assertEquals("S1", queue[1].title)
        assertEquals("S2", queue[2].title)
        assertEquals(3, queue.size)
    }

    @Test
    fun favoriteToggle_updatesCorrectly() {
        val s1 = createSampleSong(1, "Track", "Artist", "Album", 1000L)
        assertFalse(s1.isFavorite)

        val s1Favorite = s1.copy(isFavorite = !s1.isFavorite)
        assertTrue(s1Favorite.isFavorite)

        val s1Unfavorite = s1Favorite.copy(isFavorite = !s1Favorite.isFavorite)
        assertFalse(s1Unfavorite.isFavorite)
    }

    @Test
    fun localTrackMatcher_matchesExactAndFuzzyTracks() {
        val s1 = createSampleSong(10, "Starboy", "The Weeknd", "Starboy", 230000L)
        val s2 = createSampleSong(20, "Kesariya (From Brahmastra)", "Arijit Singh", "Brahmastra", 268000L)
        val s3 = createSampleSong(30, "Shape of You", "Ed Sheeran", "Divide", 233000L)
        val localLibrary = listOf(s1, s2, s3)

        val importedResult = com.sonora.music.data.model.ImportedPlaylistResult(
            playlistName = "Top Hits",
            description = "Test import",
            source = "Spotify",
            tracks = listOf(
                com.sonora.music.data.model.ImportedTrack(title = "Starboy", artist = "The Weeknd"),
                com.sonora.music.data.model.ImportedTrack(title = "Kesariya", artist = "Arijit Singh"),
                com.sonora.music.data.model.ImportedTrack(title = "Believer", artist = "Imagine Dragons") // Not in local library
            )
        )

        val matcher = com.sonora.music.data.importer.LocalTrackMatcher()
        val summary = matcher.match(importedResult, localLibrary)

        assertEquals(2, summary.matchedSongs.size)
        assertEquals(1, summary.missingTracks.size)
        assertEquals("Believer", summary.missingTracks[0].title)
        assertEquals("Starboy", summary.matchedSongs[0].title)
        assertEquals("Kesariya (From Brahmastra)", summary.matchedSongs[1].title)
        assertEquals(3, summary.totalTracks)
        assertEquals(0.666f, summary.matchPercentage, 0.01f)
    }

    @Test
    fun downloadTask_stateTransitions_behaveAccurately() {
        val initial = com.sonora.music.data.model.DownloadTask(
            id = 101L,
            title = "Starboy",
            artist = "The Weeknd",
            status = com.sonora.music.data.model.DownloadStatus.DOWNLOADING,
            progress = 0
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.DOWNLOADING, initial.status)
        assertEquals(0, initial.progress)

        // Progress update
        val inProgress = initial.copy(progress = 55, downloadedBytes = 5500L, totalBytes = 10000L)
        assertEquals(55, inProgress.progress)
        assertEquals(5500L, inProgress.downloadedBytes)

        // Completion
        val completed = inProgress.copy(
            status = com.sonora.music.data.model.DownloadStatus.COMPLETED,
            progress = 100,
            localUri = "content://media/external/audio/media/101"
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.COMPLETED, completed.status)
        assertEquals(100, completed.progress)
        assertEquals("content://media/external/audio/media/101", completed.localUri)
    }

    @Test
    fun importedTrack_withDownloadStatus_preservesIntegrity() {
        val track = com.sonora.music.data.model.ImportedTrack(
            title = "Kesariya",
            artist = "Arijit Singh",
            directUrl = "https://example.com/audio/kesariya.mp3",
            downloadStatus = com.sonora.music.data.model.DownloadStatus.DOWNLOADING,
            downloadProgress = 42
        )
        assertEquals("Kesariya", track.title)
        assertEquals("https://example.com/audio/kesariya.mp3", track.directUrl)
        assertEquals(com.sonora.music.data.model.DownloadStatus.DOWNLOADING, track.downloadStatus)
        assertEquals(42, track.downloadProgress)
    }
}
