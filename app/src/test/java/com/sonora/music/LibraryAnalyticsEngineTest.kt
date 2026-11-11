package com.sonora.music

import com.sonora.music.data.local.database.entity.ListeningSessionEntity
import com.sonora.music.data.local.database.entity.SongEntity
import com.sonora.music.data.model.AnalyticsRangeFilter
import com.sonora.music.data.model.ArtistSortOption
import com.sonora.music.data.model.SmartAlbumType
import com.sonora.music.data.model.Song
import com.sonora.music.data.repository.LibraryAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LibraryAnalyticsEngineTest {

    private val utc = ZoneOffset.UTC

    private fun song(
        id: Long,
        title: String = "Track $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        durationMs: Long = 180_000L,
        year: Int = 2024,
        dateAdded: Long = 1000L + id,
        isFavorite: Boolean = false,
        playCount: Int = 0,
        lastPlayedTimestamp: Long = 0L,
        skipCount: Int = 0,
        totalListeningTimeMs: Long = 0L,
        lastCompletedTimestamp: Long = 0L,
        artUri: String? = null
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUriString = "content://media/$id",
        albumId = id * 10L,
        albumArtUriString = artUri,
        year = year,
        dateAdded = dateAdded,
        folderName = "Sonora",
        mimeType = "audio/mpeg",
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp,
        skipCount = skipCount,
        totalListeningTimeMs = totalListeningTimeMs,
        lastCompletedTimestamp = lastCompletedTimestamp
    )

    @Test
    fun parseArtists_handlesCollaborationsMissingAndDeduplicatesCase() {
        assertEquals(listOf("Unknown Artist"), LibraryAnalyticsEngine.parseArtists(""))
        assertEquals(listOf("Unknown Artist"), LibraryAnalyticsEngine.parseArtists(" <unknown> "))

        val collab = LibraryAnalyticsEngine.parseArtists("Drake feat. Future & Metro Boomin, drake")
        assertEquals(listOf("Drake", "Future", "Metro Boomin"), collab)
    }

    @Test
    fun buildArtists_preventsDuplicatesAcrossCapitalizationAndComputesTotals() {
        val songs = listOf(
            song(1, artist = "drake", playCount = 5, totalListeningTimeMs = 300_000L, dateAdded = 100L),
            song(2, artist = "Drake  feat.  Future", playCount = 3, totalListeningTimeMs = 180_000L, dateAdded = 200L, artUri = "file:///art2.jpg"),
            song(3, artist = "", playCount = 1, totalListeningTimeMs = 60_000L, dateAdded = 300L)
        )

        val alpha = LibraryAnalyticsEngine.buildArtists(songs, ArtistSortOption.ALPHABETICAL)
        assertEquals(3, alpha.size) // Drake, Future, Unknown Artist
        val drake = alpha.first { it.name.equals("Drake", ignoreCase = true) }
        assertEquals("Drake", drake.name)
        assertEquals(2, drake.trackCount)
        assertEquals(480_000L, drake.totalListeningTimeMs)
        assertEquals("file:///art2.jpg", drake.artworkUriString)

        val mostPlayed = LibraryAnalyticsEngine.buildArtists(songs, ArtistSortOption.MOST_PLAYED)
        assertEquals("Drake", mostPlayed.first().name)
    }

    @Test
    fun smartAlbums_filterAndRankAllCollectionsAccurately() {
        val now = LocalDate.of(2026, 11, 10).atStartOfDay().toInstant(utc).toEpochMilli()
        val songs = listOf(
            song(1, title = "Fav Hit", playCount = 12, isFavorite = true, totalListeningTimeMs = 1_500_000L, durationMs = 200_000L, lastCompletedTimestamp = now - 5000L, lastPlayedTimestamp = now - 1000L, year = 2023),
            song(2, title = "Short Intro", playCount = 0, durationMs = 45_000L, skipCount = 0, dateAdded = 9000L, year = 2025),
            song(3, title = "Skipped Track", playCount = 4, skipCount = 7, lastPlayedTimestamp = now - 8000L, year = 2023),
            song(4, title = "Long Track", playCount = 2, durationMs = 300_000L, totalListeningTimeMs = 2_000_000L, lastPlayedTimestamp = now - 2000L, year = 0)
        )

        assertEquals(listOf(1L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.FAVORITES, songs).map { it.id })
        assertEquals(listOf(2L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.NEVER_PLAYED, songs).map { it.id })
        assertEquals(listOf(3L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.MOST_SKIPPED, songs).map { it.id })
        assertEquals(listOf(1L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.RECENTLY_COMPLETED, songs).map { it.id })
        assertEquals(listOf(4L, 1L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.LONGEST_PLAYED, songs).map { it.id })
        assertEquals(listOf(2L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.SHORT_TRACKS, songs, shortTrackMaxMs = 60_000L).map { it.id })
        assertEquals(listOf(1L, 3L, 4L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.MOST_PLAYED, songs).map { it.id })
        assertEquals(listOf(1L, 4L, 3L), LibraryAnalyticsEngine.getSmartAlbumSongs(SmartAlbumType.RECENTLY_PLAYED, songs).map { it.id })

        val byYear = LibraryAnalyticsEngine.buildByYearGroups(songs)
        assertEquals(listOf("2025", "2023", "Unknown Year"), byYear.map { it.label })
    }

    @Test
    fun findDuplicateSongs_detectsCopySuffixAndDurationBucket() {
        val songs = listOf(
            song(1, title = "Neon Sky", durationMs = 211_000L),
            song(2, title = "Neon Sky (1)", durationMs = 211_500L),
            song(3, title = "Different Song", durationMs = 211_000L)
        )

        val groups = LibraryAnalyticsEngine.findDuplicateSongs(songs)
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.first().songs.map { it.id })
    }

    @Test
    fun songEntityCustomOverrides_preserveOriginalsAndExposeCustomMetadata() {
        val entity = SongEntity(
            id = 42L,
            title = "track_01_raw",
            artist = "<unknown>",
            album = "Downloads",
            durationMs = 190_000L,
            contentUriString = "content://media/42",
            albumId = 9L,
            albumArtUriString = null,
            trackNumber = 1,
            year = 2020,
            dateAdded = 5000L,
            folderName = "Sonora",
            mimeType = "audio/mpeg",
            isFavorite = true,
            playCount = 3,
            customTitle = "Midnight Drive",
            customArtist = "The Midnight",
            customAlbum = "Endless Summer",
            customYear = 2024,
            customArtworkUriString = "file:///custom_art/song_42.jpg"
        )
        val domain = entity.toSong()
        assertEquals("Midnight Drive", domain.title)
        assertEquals("The Midnight", domain.artist)
        assertEquals("Endless Summer", domain.album)
        assertEquals(2024, domain.year)
        assertEquals("file:///custom_art/song_42.jpg", domain.albumArtUriString)
        assertEquals("track_01_raw", domain.originalTitle)
        assertTrue(domain.isFavorite)
    }

    @Test
    fun analyticsDashboard_zeroFillsCalendarDaysSupportsRangesAndWeekComparison() {
        val today = LocalDate.of(2026, 11, 10)
        fun epochAt(dayOffset: Long, hour: Int): Long =
            today.plusDays(dayOffset).atTime(hour, 15).toInstant(utc).toEpochMilli()

        val songs = listOf(
            song(1, title = "Song A", artist = "Artist One", album = "Alb 1", playCount = 5, totalListeningTimeMs = 7_200_000L),
            song(2, title = "Song B", artist = "Artist Two", album = "Alb 2", playCount = 2, totalListeningTimeMs = 1_800_000L)
        )
        val sessions = listOf(
            // Current week (today = offset 0, -2)
            ListeningSessionEntity(songId = 1, songTitle = "Song A", artistName = "Artist One", albumName = "Alb 1", timestampMs = epochAt(0, 20), durationListenedMs = 3_600_000L, wasCompleted = true),
            ListeningSessionEntity(songId = 1, songTitle = "Song A", artistName = "Artist One", albumName = "Alb 1", timestampMs = epochAt(-2, 20), durationListenedMs = 3_600_000L, wasCompleted = true),
            // Previous week (-8)
            ListeningSessionEntity(songId = 2, songTitle = "Song B", artistName = "Artist Two", albumName = "Alb 2", timestampMs = epochAt(-8, 14), durationListenedMs = 1_800_000L, wasCompleted = true)
        )

        // 7-day range zero-filling every day
        val d7 = LibraryAnalyticsEngine.computeAnalyticsDashboard(
            allSongs = songs,
            sessions = sessions,
            filter = AnalyticsRangeFilter.DAYS_7,
            rangeOffset = 0,
            today = today,
            zoneId = utc
        )
        assertEquals(7, d7.mainLinePoints.size)
        assertEquals(0.0f, d7.mainLinePoints.first().hours, 0.001f)
        assertEquals(1.0f, d7.mainLinePoints.last().hours, 0.001f)
        assertFalse(d7.canGoNext)
        assertEquals(7_200_000L, d7.currentWeekListeningMs)
        assertEquals(1_800_000L, d7.previousWeekListeningMs)
        assertEquals(300f, d7.weekComparisonDeltaPercent ?: 0f, 0.1f)
        assertNotNull(d7.mostActiveHour)
        assertEquals(20, d7.mostActiveHour?.hour)

        // Previous 7-day range navigation includes day -8
        val prevD7 = LibraryAnalyticsEngine.computeAnalyticsDashboard(
            allSongs = songs,
            sessions = sessions,
            filter = AnalyticsRangeFilter.DAYS_7,
            rangeOffset = -1,
            today = today,
            zoneId = utc
        )
        assertTrue(prevD7.canGoNext)
        assertEquals(1_800_000L, prevD7.mainLinePoints.sumOf { it.durationMs })

        // 30-day & 12-month point counts
        val d30 = LibraryAnalyticsEngine.computeAnalyticsDashboard(songs, sessions, AnalyticsRangeFilter.DAYS_30, 0, today, utc)
        assertEquals(30, d30.mainLinePoints.size)
        val m12 = LibraryAnalyticsEngine.computeAnalyticsDashboard(songs, sessions, AnalyticsRangeFilter.MONTHS_12, 0, today, utc)
        assertEquals(12, m12.mainLinePoints.size)

        // Listening DNA
        val dna = LibraryAnalyticsEngine.computeListeningDna(songs, sessions, utc)
        assertEquals("Artist One", dna.mostListenedArtist?.artistName)
        assertEquals("Song A", dna.mostPlayedSong?.title)
        assertEquals(9_000_000L, dna.totalListeningTimeMs)
    }

    @Test
    fun analyticsDashboard_emptyLibrary_returnsSafeDefaults() {
        val emptyDash = LibraryAnalyticsEngine.computeAnalyticsDashboard(
            allSongs = emptyList(),
            sessions = emptyList(),
            filter = AnalyticsRangeFilter.DAYS_7,
            rangeOffset = 0,
            today = LocalDate.of(2026, 11, 10),
            zoneId = utc
        )
        assertEquals(7, emptyDash.mainLinePoints.size)
        assertEquals(0L, emptyDash.totalListeningMs)
        assertEquals(24, emptyDash.hourlyActivity.size)
    }

    @Test
    fun buildAlbums_generatesUniqueIdsNormalizesUnknownAndPrioritizesArtwork() {
        val base1 = song(1, artist = "Arijit Singh", album = "Roadtrip", year = 2022, artUri = "content://media/external/audio/albumart/777")
            .copy(albumId = 777L)
        val base2 = song(2, artist = "Shreya Ghoshal", album = "roadtrip", year = 2024, artUri = "file:///custom/roadtrip.jpg")
            .copy(albumId = 777L)
        val base3 = song(3, artist = "Drake", album = "Late Night", year = 2025, artUri = null)
            .copy(albumId = 777L)
        val unk1 = song(4, artist = "<unknown>", album = "<unknown>", year = 0, artUri = "content://media/external/audio/albumart/0")
        val unk2 = song(5, artist = "   ", album = "unknown", year = 2021)

        val built = LibraryAnalyticsEngine.buildAlbums(listOf(base1, base2, base3, unk1, unk2))

        // Distinct albums: Late Night, Roadtrip, Unknown Album
        assertEquals(3, built.size)
        // Strictly unique Album IDs even when underlying MediaStore albumId collides (777L)
        assertEquals(built.size, built.map { it.id }.distinct().size)

        val roadtrip = built.first { it.title.equals("Roadtrip", ignoreCase = true) }
        assertEquals("Roadtrip", roadtrip.title)
        assertEquals("Various Artists", roadtrip.artist)
        assertEquals(2, roadtrip.trackCount)
        assertEquals(2024, roadtrip.year)
        assertEquals("file:///custom/roadtrip.jpg", roadtrip.albumArtUriString)
        assertEquals("content://media/1", roadtrip.fallbackAudioUriString)

        val lateNight = built.first { it.title == "Late Night" }
        assertEquals("Drake", lateNight.artist)
        assertEquals(1, lateNight.trackCount)

        val unknown = built.first { it.title == "Unknown Album" }
        assertEquals("Unknown Artist", unknown.artist)
        assertEquals(2, unknown.trackCount)
        assertEquals(2021, unknown.year)
    }
}

