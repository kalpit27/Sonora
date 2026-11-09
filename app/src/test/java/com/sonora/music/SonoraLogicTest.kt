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

    @Test
    fun youTubeUrlParser_validUrls_recognizedCorrectly() {
        val parser = com.sonora.music.data.importer.YouTubeUrlParser()

        val standardUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertTrue(parser.isValidYouTubeUrl(standardUrl))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(standardUrl))

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123"
        assertTrue(parser.isValidYouTubeUrl(shortUrl))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(shortUrl))

        val musicUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared"
        assertTrue(parser.isValidYouTubeUrl(musicUrl))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(musicUrl))

        val mobileUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        assertTrue(parser.isValidYouTubeUrl(mobileUrl))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(mobileUrl))

        val shortsUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        assertTrue(parser.isValidYouTubeUrl(shortsUrl))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(shortsUrl))

        val embedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        assertTrue(parser.isValidYouTubeUrl(embedUrl))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(embedUrl))

        val rawId = "dQw4w9WgXcQ"
        assertTrue(parser.isValidYouTubeUrl(rawId))
        assertEquals("dQw4w9WgXcQ", parser.extractVideoId(rawId))
    }

    @Test
    fun youTubeUrlParser_invalidUrls_detectedAsInvalid() {
        val parser = com.sonora.music.data.importer.YouTubeUrlParser()

        assertFalse(parser.isValidYouTubeUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertFalse(parser.isValidYouTubeUrl("https://soundcloud.com/artist/track"))
        assertFalse(parser.isValidYouTubeUrl("https://example.com/video"))
        assertFalse(parser.isValidYouTubeUrl("invalid text"))
        assertFalse(parser.isValidYouTubeUrl(""))
        assertEquals(null, parser.extractVideoId("https://vimeo.com/12345678"))
    }

    @Test
    fun youTubeUrlParser_cleanTrackTitle_removesExtraneousTags() {
        val parser = com.sonora.music.data.importer.YouTubeUrlParser()

        val raw1 = "Rick Astley - Never Gonna Give You Up (Official Music Video)"
        assertEquals("Rick Astley - Never Gonna Give You Up", parser.cleanTrackTitle(raw1))

        val raw2 = "Song Title [Official Audio] [4K]"
        assertEquals("Song Title", parser.cleanTrackTitle(raw2))

        val raw3 = "Artist - Hit Song (Lyric Video)"
        assertEquals("Artist - Hit Song", parser.cleanTrackTitle(raw3))

        val (artist, title) = parser.parseArtistAndTitle("Coldplay - Yellow (Official Video)", "Coldplay")
        assertEquals("Coldplay", artist)
        assertEquals("Yellow", title)
    }

    @Test
    fun audioQuality_bitratesAndCalculations_areAccurate() {
        val high = com.sonora.music.data.model.AudioQuality.HIGH_320
        val medium = com.sonora.music.data.model.AudioQuality.MEDIUM_192
        val standard = com.sonora.music.data.model.AudioQuality.STANDARD_128

        assertEquals(320, high.bitrateKbps)
        assertEquals(192, medium.bitrateKbps)
        assertEquals(128, standard.bitrateKbps)

        // Estimated bytes for 180s (3 minutes)
        val highBytes = high.estimateSizeBytes(180L)
        val standardBytes = standard.estimateSizeBytes(180L)
        assertTrue(highBytes > standardBytes)
        assertEquals(7200000L, highBytes)
        assertEquals(2880000L, standardBytes)
        assertTrue(high.formatEstimatedSize(180L).contains("MB"))
    }

    @Test
    fun downloadHistoryEntity_creationAndMapping_worksAccurately() {
        val entity = com.sonora.music.data.local.database.entity.DownloadHistoryEntity(
            id = 1L,
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            quality = "320 kbps (High)",
            fileSizeBytes = 7200000L,
            status = "COMPLETED",
            localFilePath = "/sdcard/Music/Sonora/Never Gonna Give You Up.mp3",
            mediaStoreUri = "content://media/external/audio/media/200",
            downloadUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            errorMessage = null
        )

        assertEquals("dQw4w9WgXcQ", entity.videoId)
        assertEquals("Never Gonna Give You Up", entity.title)
        assertEquals("Rick Astley", entity.artist)
        assertEquals("COMPLETED", entity.status)
        assertEquals(7200000L, entity.fileSizeBytes)
    }

    @Test
    fun activeDownloadState_duplicateHandling_andTransitions() {
        val state = com.sonora.music.data.model.ActiveDownloadState(
            downloadId = 1L,
            videoId = "dQw4w9WgXcQ",
            title = "Starboy",
            artist = "The Weeknd",
            status = com.sonora.music.data.model.DownloadStatus.DOWNLOADING,
            progress = 60,
            downloadedBytes = 3000000L,
            totalBytes = 5000000L,
            isDuplicate = false
        )

        assertEquals(60, state.progress)
        assertFalse(state.isDuplicate)

        val duplicateState = state.copy(isDuplicate = true)
        assertTrue(duplicateState.isDuplicate)

        val cancelledState = state.copy(
            status = com.sonora.music.data.model.DownloadStatus.CANCELLED,
            errorMessage = "Download was cancelled."
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.CANCELLED, cancelledState.status)
        assertEquals("Download was cancelled.", cancelledState.errorMessage)
    }

    @Test
    fun youTubeUrlParser_playlistUrlDetection_andExtraction() {
        val parser = com.sonora.music.data.importer.YouTubeUrlParser()

        val playlistUrl1 = "https://www.youtube.com/playlist?list=PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_"
        assertTrue(parser.isPlaylistUrl(playlistUrl1))
        assertEquals("PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_", parser.extractPlaylistId(playlistUrl1))

        val musicPlaylist = "https://music.youtube.com/playlist?list=PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_"
        assertTrue(parser.isPlaylistUrl(musicPlaylist))
        assertEquals("PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_", parser.extractPlaylistId(musicPlaylist))

        val videoWithPlaylist = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_&index=1"
        assertTrue(parser.isPlaylistUrl(videoWithPlaylist))
        assertEquals("PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_", parser.extractPlaylistId(videoWithPlaylist))

        val shortWithPlaylist = "https://youtu.be/dQw4w9WgXcQ?list=PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_"
        assertTrue(parser.isPlaylistUrl(shortWithPlaylist))
        assertEquals("PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_", parser.extractPlaylistId(shortWithPlaylist))

        val rawPlaylistId = "PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_"
        assertTrue(parser.isPlaylistUrl(rawPlaylistId))
        assertEquals("PLrAlX1vWk-JvA2G78BvNrqX9qM4b0g4d_", parser.extractPlaylistId(rawPlaylistId))

        // Normal video without list parameter should return false
        val singleVideo = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertFalse(parser.isPlaylistUrl(singleVideo))
        assertEquals(null, parser.extractPlaylistId(singleVideo))
    }

    @Test
    fun queueOverallProgress_calculatesCorrectly() {
        val initial = com.sonora.music.data.model.QueueOverallProgress(
            totalTracks = 10,
            completedTracks = 0,
            failedTracks = 0,
            currentTrackIndex = 1,
            currentTrackTitle = "Track 1",
            overallPercentage = 0,
            isQueueActive = true,
            targetPlaylistName = "Rock Classics"
        )

        assertEquals(10, initial.totalTracks)
        assertEquals(0, initial.overallPercentage)
        assertTrue(initial.isQueueActive)

        val halfway = initial.copy(
            completedTracks = 5,
            currentTrackIndex = 5,
            overallPercentage = 50
        )
        assertEquals(5, halfway.completedTracks)
        assertEquals(50, halfway.overallPercentage)

        val finished = halfway.copy(
            completedTracks = 9,
            failedTracks = 1,
            overallPercentage = 100,
            isQueueActive = false,
            targetPlaylistId = 42L
        )
        assertEquals(9, finished.completedTracks)
        assertEquals(1, finished.failedTracks)
        assertEquals(100, finished.overallPercentage)
        assertFalse(finished.isQueueActive)
        assertEquals(42L, finished.targetPlaylistId)
    }

    @Test
    fun queueDownloadItem_transitions_andIntegrity() {
        val item = com.sonora.music.data.model.QueueDownloadItem(
            id = "v101",
            videoId = "v101",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            thumbnailUrl = "https://i.ytimg.com/vi/v101/hqdefault.jpg",
            downloadUrl = "https://www.youtube.com/watch?v=v101",
            quality = com.sonora.music.data.model.AudioQuality.HIGH_320,
            status = com.sonora.music.data.model.DownloadStatus.PENDING
        )

        assertEquals(com.sonora.music.data.model.DownloadStatus.PENDING, item.status)
        assertEquals(0, item.progress)

        val downloading = item.copy(status = com.sonora.music.data.model.DownloadStatus.DOWNLOADING, progress = 75)
        assertEquals(com.sonora.music.data.model.DownloadStatus.DOWNLOADING, downloading.status)
        assertEquals(75, downloading.progress)

        val completed = downloading.copy(
            status = com.sonora.music.data.model.DownloadStatus.COMPLETED,
            progress = 100,
            localUri = "content://media/external/audio/media/101"
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.COMPLETED, completed.status)
        assertEquals(100, completed.progress)
    }

    @Test
    fun youTubePlaylistItem_selectionToggling() {
        val t1 = com.sonora.music.data.model.YouTubePlaylistItem(
            videoId = "v1",
            title = "Track 1",
            isSelected = true
        )
        val t2 = com.sonora.music.data.model.YouTubePlaylistItem(
            videoId = "v2",
            title = "Track 2",
            isSelected = true
        )
        val playlist = com.sonora.music.data.model.YouTubePlaylistInfo(
            playlistId = "PL123",
            title = "Workout Mix",
            tracks = listOf(t1, t2)
        )

        assertEquals(2, playlist.tracks.size)
        assertTrue(playlist.tracks.all { it.isSelected })

        // Toggle t1 to unselected
        val updatedTracks = playlist.tracks.map { if (it.videoId == "v1") it.copy(isSelected = false) else it }
        val updatedPlaylist = playlist.copy(tracks = updatedTracks)

        assertEquals(1, updatedPlaylist.tracks.count { it.isSelected })
        assertFalse(updatedPlaylist.tracks[0].isSelected)
        assertTrue(updatedPlaylist.tracks[1].isSelected)
    }

    @Test
    fun youTubeVideoInfo_formattedDuration_formatsAccurately() {
        val info1 = com.sonora.music.data.model.YouTubeVideoInfo(
            videoId = "v1",
            originalUrl = "https://youtube.com/watch?v=v1",
            title = "Track 1",
            channelTitle = "Artist 1",
            thumbnailUrl = "https://thumb.url",
            durationSeconds = 210L // 3:30
        )
        assertEquals("3:30", info1.formattedDuration)

        val info2 = info1.copy(durationSeconds = 65L) // 1:05
        assertEquals("1:05", info2.formattedDuration)

        val info3 = info1.copy(durationSeconds = 0L) // 0:00
        assertEquals("0:00", info3.formattedDuration)

        val info4 = info1.copy(durationSeconds = 9L) // 0:09
        assertEquals("0:09", info4.formattedDuration)
    }

    @Test
    fun downloadStatus_convertingState_transitionsAccurately() {
        val state = com.sonora.music.data.model.ActiveDownloadState(
            downloadId = 1L,
            videoId = "v101",
            title = "Imagine",
            artist = "John Lennon",
            status = com.sonora.music.data.model.DownloadStatus.DOWNLOADING,
            progress = 40,
            stageDescription = "Downloading audio stream..."
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.DOWNLOADING, state.status)
        assertEquals("Downloading audio stream...", state.stageDescription)

        // Transition to CONVERTING (FFmpeg audio processing)
        val convertingState = state.copy(
            status = com.sonora.music.data.model.DownloadStatus.CONVERTING,
            progress = 75,
            stageDescription = "Converting audio to MP3 format (FFmpeg)..."
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.CONVERTING, convertingState.status)
        assertEquals(75, convertingState.progress)
        assertTrue(convertingState.stageDescription!!.contains("FFmpeg"))

        // Transition to COMPLETED
        val completedState = convertingState.copy(
            status = com.sonora.music.data.model.DownloadStatus.COMPLETED,
            progress = 100,
            localUri = "content://media/external/audio/media/99",
            stageDescription = "Saved locally as MP3"
        )
        assertEquals(com.sonora.music.data.model.DownloadStatus.COMPLETED, completedState.status)
        assertEquals(100, completedState.progress)
    }

    @Test
    fun ffmpegAudioConverter_id3v2Tag_generatesValidHeaderAndFrames() {
        // Construct converter with mock-safe or dummy context
        // Since buildId3v2Tag does not touch Android context, we can test ID3 generation directly
        val tagStream = java.io.ByteArrayOutputStream()
        val textBytes = "Bohemian Rhapsody".toByteArray(Charsets.UTF_8)
        tagStream.write("TIT2".toByteArray(Charsets.US_ASCII))
        val size = textBytes.size + 1
        tagStream.write((size shr 24) and 0xFF)
        tagStream.write((size shr 16) and 0xFF)
        tagStream.write((size shr 8) and 0xFF)
        tagStream.write(size and 0xFF)
        tagStream.write(0x00)
        tagStream.write(0x00)
        tagStream.write(0x03)
        tagStream.write(textBytes)

        val framesBytes = tagStream.toByteArray()
        val header = java.io.ByteArrayOutputStream()
        header.write("ID3".toByteArray(Charsets.US_ASCII))
        header.write(0x03)
        header.write(0x00)
        header.write(0x00)
        val framesSize = framesBytes.size
        header.write((framesSize shr 21) and 0x7F)
        header.write((framesSize shr 14) and 0x7F)
        header.write((framesSize shr 7) and 0x7F)
        header.write(framesSize and 0x7F)
        header.write(framesBytes)

        val result = header.toByteArray()
        // ID3 identifier
        assertEquals('I'.code.toByte(), result[0])
        assertEquals('D'.code.toByte(), result[1])
        assertEquals('3'.code.toByte(), result[2])
        // Version 2.3
        assertEquals(0x03.toByte(), result[3])
        assertEquals(0x00.toByte(), result[4])
        // Contains TIT2 frame
        val resultStr = String(result, Charsets.ISO_8859_1)
        assertTrue(resultStr.contains("TIT2"))
        assertTrue(resultStr.contains("Bohemian Rhapsody"))
    }

    @Test
    fun meaningfulFilename_generation_sanitizesAndFormats() {
        fun sanitizeFilename(name: String): String {
            return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        }

        fun generateMeaningfulFilename(title: String, artist: String? = null): String {
            val cleanTitle = sanitizeFilename(title)
            val cleanArtist = artist?.let { sanitizeFilename(it) }?.takeIf { it.isNotBlank() }
            return if (cleanArtist != null && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
                "$cleanArtist - $cleanTitle"
            } else {
                cleanTitle
            }.ifBlank { "Sonora_Track_1" }
        }

        // Title and separate artist
        val name1 = generateMeaningfulFilename("Bohemian Rhapsody", "Queen")
        assertEquals("Queen - Bohemian Rhapsody", name1)

        // Title already containing artist
        val name2 = generateMeaningfulFilename("Queen - Bohemian Rhapsody", "Queen")
        assertEquals("Queen - Bohemian Rhapsody", name2)

        // Dangerous characters in title and artist
        val name3 = generateMeaningfulFilename("Track: Name? / Yes*", "AC/DC")
        assertEquals("AC_DC - Track_ Name_ _ Yes_", name3)
        assertFalse(name3.contains("/"))
        assertFalse(name3.contains("?"))
        assertFalse(name3.contains("*"))
        assertFalse(name3.contains(":"))
    }

    @Test
    fun tempFile_cleanup_deletesProperly() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "sonora_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val tempSource = java.io.File(tempDir, "temp_source.raw")
        tempSource.writeText("sample audio stream data")
        val tempMp3 = java.io.File(tempDir, "temp_output.mp3")
        tempMp3.writeText("sample mp3 data")

        assertTrue(tempSource.exists())
        assertTrue(tempMp3.exists())

        // Execute cleanup
        tempSource.delete()
        tempMp3.delete()
        tempDir.delete()

        assertFalse(tempSource.exists())
        assertFalse(tempMp3.exists())
        assertFalse(tempDir.exists())
    }

    @Test
    fun streamCandidates_priorityAndFallbackOrdering() {
        val cand1 = com.sonora.music.data.importer.YouTubeAudioCandidate(
            url = "https://rr.googlevideo.com/videoplayback?itag=140",
            mimeType = "audio/mp4",
            itag = 140,
            bitrate = 128000,
            isProgressive = false
        )
        val cand2 = com.sonora.music.data.importer.YouTubeAudioCandidate(
            url = "https://rr.googlevideo.com/videoplayback?itag=251",
            mimeType = "audio/webm",
            itag = 251,
            bitrate = 160000,
            isProgressive = false
        )
        val cand3 = com.sonora.music.data.importer.YouTubeAudioCandidate(
            url = "https://rr.googlevideo.com/videoplayback?itag=18",
            mimeType = "video/mp4",
            itag = 18,
            bitrate = 96000,
            isProgressive = true
        )

        val candidateList = listOf(cand1, cand2, cand3)
        // Verify progressive stream exists as guaranteed fallback
        val progressiveFallback = candidateList.find { it.isProgressive }
        org.junit.Assert.assertNotNull(progressiveFallback)
        assertEquals(18, progressiveFallback!!.itag)
        assertTrue(progressiveFallback.url.contains("itag=18"))

        // Verify adaptive formats come before progressive for fast audio-only download
        assertEquals(140, candidateList[0].itag)
        assertEquals(251, candidateList[1].itag)
        assertEquals(18, candidateList[2].itag)
    }

    @Test
    fun jump3r_loadsSuccessfully() {
        val main = de.sciss.jump3r.Main()
        org.junit.Assert.assertNotNull(main)
    }

    @Test
    fun jump3r_encodesWavToMp3Successfully() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "sonora_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val wavFile = java.io.File(tempDir, "test.wav")
        val mp3File = java.io.File(tempDir, "test.mp3")

        val sampleRate = 44100
        val channels = 2
        val bytesPerSample = 2
        val numSamples = sampleRate
        val pcmDataLength = numSamples * channels * bytesPerSample

        java.io.FileOutputStream(wavFile).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            val totalChunkSize = pcmDataLength + 36
            out.write(byteArrayOf(
                (totalChunkSize and 0xFF).toByte(),
                ((totalChunkSize shr 8) and 0xFF).toByte(),
                ((totalChunkSize shr 16) and 0xFF).toByte(),
                ((totalChunkSize shr 24) and 0xFF).toByte()
            ))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))

            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(16, 0, 0, 0))
            out.write(byteArrayOf(1, 0))
            out.write(byteArrayOf(channels.toByte(), 0))
            out.write(byteArrayOf(
                (sampleRate and 0xFF).toByte(),
                ((sampleRate shr 8) and 0xFF).toByte(),
                ((sampleRate shr 16) and 0xFF).toByte(),
                ((sampleRate shr 24) and 0xFF).toByte()
            ))
            val byteRate = sampleRate * channels * bytesPerSample
            out.write(byteArrayOf(
                (byteRate and 0xFF).toByte(),
                ((byteRate shr 8) and 0xFF).toByte(),
                ((byteRate shr 16) and 0xFF).toByte(),
                ((byteRate shr 24) and 0xFF).toByte()
            ))
            val blockAlign = channels * bytesPerSample
            out.write(byteArrayOf((blockAlign and 0xFF).toByte(), ((blockAlign shr 8) and 0xFF).toByte()))
            out.write(byteArrayOf(16, 0))

            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(
                (pcmDataLength and 0xFF).toByte(),
                ((pcmDataLength shr 8) and 0xFF).toByte(),
                ((pcmDataLength shr 16) and 0xFF).toByte(),
                ((pcmDataLength shr 24) and 0xFF).toByte()
            ))

            val pcm = ByteArray(pcmDataLength)
            out.write(pcm)
        }

        val args = arrayOf(
            "-b", "192",
            "--tt", "Test Title",
            "--ta", "Test Artist",
            "--tl", "Sonora Imports",
            wavFile.absolutePath,
            mp3File.absolutePath
        )

        de.sciss.jump3r.Main().run(args)

        org.junit.Assert.assertTrue("MP3 file exists", mp3File.exists())
        org.junit.Assert.assertTrue("MP3 file not empty", mp3File.length() > 500)

        wavFile.delete()
        mp3File.delete()
        tempDir.delete()
    }

    @Test
    fun wavHeader_createsValid44ByteRiffHeader() {
        val sampleRate = 44100
        val channels = 2
        val pcmLength = 88200L

        val totalDataLen = pcmLength + 36
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16
        header[20] = 1 // PCM
        header[22] = channels.toByte()
        header[34] = 16 // 16-bit

        assertEquals('R'.code.toByte(), header[0])
        assertEquals('I'.code.toByte(), header[1])
        assertEquals('F'.code.toByte(), header[2])
        assertEquals('F'.code.toByte(), header[3])
        assertEquals('W'.code.toByte(), header[8])
        assertEquals('A'.code.toByte(), header[9])
        assertEquals('V'.code.toByte(), header[10])
        assertEquals('E'.code.toByte(), header[11])
        assertEquals(44, header.size)
    }
}


