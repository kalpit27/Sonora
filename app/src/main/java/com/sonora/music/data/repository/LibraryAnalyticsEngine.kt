package com.sonora.music.data.repository

import com.sonora.music.data.local.database.entity.ListeningSessionEntity
import com.sonora.music.data.model.Album
import com.sonora.music.data.model.AlbumStat
import com.sonora.music.data.model.AnalyticsDashboardData
import com.sonora.music.data.model.AnalyticsRangeFilter
import com.sonora.music.data.model.Artist
import com.sonora.music.data.model.ArtistSortOption
import com.sonora.music.data.model.ArtistStat
import com.sonora.music.data.model.DayStat
import com.sonora.music.data.model.DuplicateGroup
import com.sonora.music.data.model.HourlyActivityPoint
import com.sonora.music.data.model.ListeningDnaStats
import com.sonora.music.data.model.SmartAlbumSummary
import com.sonora.music.data.model.SmartAlbumType
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SongStat
import com.sonora.music.data.model.TimeCapsuleGroup
import com.sonora.music.data.model.TimePeriodPoint
import com.sonora.music.data.model.YearCollectionGroup
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object LibraryAnalyticsEngine {

    private val COLLAB_SPLIT_REGEX = Regex(
        """(?:\s*(?:;|&|,)\s*|\s+(?:feat\.?|ft\.?|featuring|with|vs\.?|/|x)\s+)""",
        RegexOption.IGNORE_CASE
    )
    private val DUPLICATE_SUFFIX_REGEX = Regex("""\s*\(\d+\)\s*$|\s*\[\d+]\s*$|\s*-\s*copy\s*$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun normalizeKey(value: String): String =
        value.trim().replace(WHITESPACE_REGEX, " ").lowercase(Locale.ROOT)

    fun parseArtists(rawArtist: String): List<String> {
        val cleaned = rawArtist.trim().replace(WHITESPACE_REGEX, " ")
        if (cleaned.isBlank() ||
            cleaned.equals("<unknown>", ignoreCase = true) ||
            cleaned.equals("unknown", ignoreCase = true) ||
            cleaned.equals("unknown artist", ignoreCase = true)
        ) {
            return listOf("Unknown Artist")
        }

        val parts = COLLAB_SPLIT_REGEX.split(cleaned)
            .map { it.trim().replace(WHITESPACE_REGEX, " ") }
            .filter {
                it.isNotBlank() &&
                    !it.equals("<unknown>", ignoreCase = true) &&
                    !it.equals("unknown", ignoreCase = true) &&
                    !it.equals("unknown artist", ignoreCase = true)
            }

        return if (parts.isEmpty()) listOf("Unknown Artist") else parts.distinctBy { normalizeKey(it) }
    }

    private fun chooseCanonicalName(variants: List<String>): String {
        val distinct = variants.groupingBy { it }.eachCount()
        return distinct.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { entry ->
                    // Prefer properly capitalized names over all-lowercase or all-uppercase
                    val s = entry.key
                    val hasUpper = s.any { it.isUpperCase() }
                    val hasLower = s.any { it.isLowerCase() }
                    when {
                        hasUpper && hasLower -> 2
                        hasUpper -> 1
                        else -> 0
                    }
                }.thenByDescending { it.value }
            )
            .firstOrNull()?.key ?: "Unknown Artist"
    }

    fun normalizeAlbumTitle(rawAlbum: String): String {
        val cleaned = rawAlbum.trim().replace(WHITESPACE_REGEX, " ")
        return if (cleaned.isBlank() ||
            cleaned.equals("<unknown>", ignoreCase = true) ||
            cleaned.equals("unknown", ignoreCase = true) ||
            cleaned.equals("unknown album", ignoreCase = true)
        ) {
            "Unknown Album"
        } else {
            cleaned
        }
    }

    private fun stableAlbumId(normalizedKey: String): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
        for (ch in normalizedKey) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        val positive = hash and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }

    fun buildArtists(
        songs: List<Song>,
        sortOption: ArtistSortOption = ArtistSortOption.ALPHABETICAL
    ): List<Artist> {
        val grouped = linkedMapOf<String, Pair<MutableList<String>, MutableList<Song>>>()

        songs.forEach { song ->
            parseArtists(song.artist).forEach { artistPart ->
                val key = normalizeKey(artistPart)
                val entry = grouped.getOrPut(key) { mutableListOf<String>() to mutableListOf() }
                entry.first.add(artistPart)
                entry.second.add(song)
            }
        }

        val artists = grouped.map { (key, pair) ->
            val (nameVariants, artistSongs) = pair
            val distinctSongs = artistSongs.distinctBy { it.id }
            val canonicalName = chooseCanonicalName(nameVariants)
            val artUri = distinctSongs
                .sortedByDescending { it.playCount }
                .firstNotNullOfOrNull { it.albumArtUriString?.takeIf { s -> s.isNotBlank() } }
            val albumCount = distinctSongs.map { normalizeKey(normalizeAlbumTitle(it.album)) }.distinct().size
            Artist(
                id = (key.hashCode().toLong() and 0x7FFFFFFFL) + 1L,
                name = canonicalName,
                trackCount = distinctSongs.size,
                albumCount = albumCount,
                artworkUriString = artUri,
                totalListeningTimeMs = distinctSongs.sumOf { it.totalListeningTimeMs },
                totalPlayCount = distinctSongs.sumOf { it.playCount },
                latestDateAdded = distinctSongs.maxOfOrNull { it.dateAdded } ?: 0L,
                songs = distinctSongs
            )
        }

        return when (sortOption) {
            ArtistSortOption.ALPHABETICAL -> artists.sortedBy { normalizeKey(it.name) }
            ArtistSortOption.RECENTLY_ADDED -> artists.sortedByDescending { it.latestDateAdded }
            ArtistSortOption.MOST_PLAYED -> artists.sortedWith(
                compareByDescending<Artist> { it.totalPlayCount }
                    .thenByDescending { it.totalListeningTimeMs }
                    .thenBy { normalizeKey(it.name) }
            )
        }
    }

    fun buildAlbums(songs: List<Song>): List<Album> {
        val usedIds = HashSet<Long>()
        val grouped = songs.groupBy { normalizeKey(normalizeAlbumTitle(it.album)) }
            .entries
            .sortedBy { it.key }

        return grouped.map { (key, songsInAlbum) ->
            val canonicalTitle = chooseCanonicalName(songsInAlbum.map { normalizeAlbumTitle(it.album) })

            val artCandidates = songsInAlbum.mapNotNull { it.albumArtUriString?.trim()?.takeIf { s -> s.isNotBlank() } }
            val artUri = artCandidates.firstOrNull { !it.contains("/audio/albumart") }
                ?: artCandidates.firstOrNull { uri ->
                    uri.contains("/audio/albumart/") && uri.substringAfterLast("/").toLongOrNull()?.let { it > 0L } == true
                }
                ?: artCandidates.firstOrNull()

            val fallbackAudioUri = songsInAlbum.firstOrNull { it.contentUriString.isNotBlank() }?.contentUriString

            val knownRawArtists = songsInAlbum
                .map { it.artist.trim().replace(WHITESPACE_REGEX, " ") }
                .filter {
                    it.isNotBlank() &&
                        !it.equals("<unknown>", ignoreCase = true) &&
                        !it.equals("unknown", ignoreCase = true) &&
                        !it.equals("unknown artist", ignoreCase = true)
                }
            val albumArtist = when {
                knownRawArtists.isEmpty() -> "Unknown Artist"
                else -> {
                    val distinctNormalized = knownRawArtists.distinctBy { normalizeKey(it) }
                    if (distinctNormalized.size == 1) chooseCanonicalName(knownRawArtists) else "Various Artists"
                }
            }

            var candidateId = stableAlbumId(key)
            while (!usedIds.add(candidateId)) {
                candidateId = if (candidateId == Long.MAX_VALUE) 1L else candidateId + 1L
            }

            Album(
                id = candidateId,
                title = canonicalTitle,
                artist = albumArtist,
                trackCount = songsInAlbum.size,
                year = songsInAlbum.maxOfOrNull { it.year } ?: 0,
                albumArtUriString = artUri,
                fallbackAudioUriString = fallbackAudioUri
            )
        }
    }

    fun buildTimeCapsuleGroups(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<TimeCapsuleGroup> {
        val songsById = allSongs.associateBy { it.id }
        val ymFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        data class MonthAccumulator(
            val yearMonth: YearMonth,
            val songsOrdered: LinkedHashMap<Long, Song> = linkedMapOf(),
            var listeningMs: Long = 0L,
            var playCount: Int = 0
        )

        val byMonth = linkedMapOf<YearMonth, MonthAccumulator>()

        sessions.sortedByDescending { it.timestampMs }.forEach { session ->
            val song = songsById[session.songId] ?: return@forEach
            val ym = YearMonth.from(Instant.ofEpochMilli(session.timestampMs).atZone(zoneId))
            val acc = byMonth.getOrPut(ym) { MonthAccumulator(ym) }
            acc.songsOrdered.putIfAbsent(song.id, song)
            acc.listeningMs += session.durationListenedMs
            acc.playCount += 1
        }

        // Include songs with legacy lastPlayedTimestamp that don't yet have sessions
        allSongs.filter { it.lastPlayedTimestamp > 0 }
            .sortedByDescending { it.lastPlayedTimestamp }
            .forEach { song ->
                val ym = YearMonth.from(Instant.ofEpochMilli(song.lastPlayedTimestamp).atZone(zoneId))
                val acc = byMonth.getOrPut(ym) { MonthAccumulator(ym) }
                if (!acc.songsOrdered.containsKey(song.id)) {
                    acc.songsOrdered[song.id] = song
                    if (acc.playCount == 0) {
                        acc.playCount += song.playCount.coerceAtLeast(1)
                    }
                    if (acc.listeningMs == 0L) {
                        acc.listeningMs += song.totalListeningTimeMs
                    }
                }
            }

        return byMonth.entries
            .sortedByDescending { it.key }
            .map { (ym, acc) ->
                TimeCapsuleGroup(
                    yearMonthKey = ym.toString(),
                    label = ym.format(ymFormatter),
                    songs = acc.songsOrdered.values.toList(),
                    totalListeningMs = acc.listeningMs,
                    playCount = acc.playCount
                )
            }
    }

    fun buildByYearGroups(allSongs: List<Song>): List<YearCollectionGroup> {
        val knownYears = allSongs.filter { it.year > 0 }
            .groupBy { it.year }
            .entries
            .sortedByDescending { it.key }
            .map { (year, list) ->
                YearCollectionGroup(year, year.toString(), list.sortedBy { normalizeKey(it.title) })
            }

        val unknown = allSongs.filter { it.year <= 0 }
        return if (unknown.isNotEmpty()) {
            knownYears + YearCollectionGroup(0, "Unknown Year", unknown.sortedBy { normalizeKey(it.title) })
        } else {
            knownYears
        }
    }

    fun getSmartAlbumSongs(
        type: SmartAlbumType,
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity> = emptyList(),
        shortTrackMaxMs: Long = 120_000L
    ): List<Song> {
        return when (type) {
            SmartAlbumType.MOST_PLAYED ->
                allSongs.filter { it.playCount > 0 }
                    .sortedWith(compareByDescending<Song> { it.playCount }.thenByDescending { it.totalListeningTimeMs })

            SmartAlbumType.TIME_CAPSULE ->
                buildTimeCapsuleGroups(allSongs, sessions)
                    .flatMap { it.songs }
                    .distinctBy { it.id }

            SmartAlbumType.RECENTLY_PLAYED ->
                allSongs.filter { it.lastPlayedTimestamp > 0 }
                    .sortedByDescending { it.lastPlayedTimestamp }

            SmartAlbumType.RECENTLY_ADDED ->
                allSongs.sortedByDescending { it.dateAdded }

            SmartAlbumType.FAVORITES ->
                allSongs.filter { it.isFavorite }
                    .sortedBy { normalizeKey(it.title) }

            SmartAlbumType.MOST_SKIPPED ->
                allSongs.filter { it.skipCount > 0 }
                    .sortedWith(compareByDescending<Song> { it.skipCount }.thenByDescending { it.lastPlayedTimestamp })

            SmartAlbumType.NEVER_PLAYED ->
                allSongs.filter { it.playCount == 0 }
                    .sortedByDescending { it.dateAdded }

            SmartAlbumType.TOP_50 ->
                allSongs.filter { it.playCount > 0 }
                    .sortedWith(compareByDescending<Song> { it.playCount }.thenByDescending { it.totalListeningTimeMs })
                    .take(50)

            SmartAlbumType.RECENTLY_COMPLETED ->
                allSongs.filter { it.lastCompletedTimestamp > 0 }
                    .sortedByDescending { it.lastCompletedTimestamp }

            SmartAlbumType.LONGEST_PLAYED ->
                allSongs.filter { it.totalListeningTimeMs > 0 }
                    .sortedByDescending { it.totalListeningTimeMs }

            SmartAlbumType.SHORT_TRACKS ->
                allSongs.filter { it.durationMs in 1..shortTrackMaxMs }
                    .sortedBy { it.durationMs }

            SmartAlbumType.OFFLINE_COLLECTION ->
                allSongs.sortedBy { normalizeKey(it.title) }

            SmartAlbumType.BY_YEAR ->
                buildByYearGroups(allSongs).flatMap { it.songs }
        }
    }

    fun getSmartAlbumSummaries(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity> = emptyList(),
        shortTrackMaxMs: Long = 120_000L
    ): List<SmartAlbumSummary> {
        return SmartAlbumType.entries.map { type ->
            val songs = getSmartAlbumSongs(type, allSongs, sessions, shortTrackMaxMs)
            SmartAlbumSummary(
                type = type,
                subtitle = when (type) {
                    SmartAlbumType.SHORT_TRACKS -> "Under ${shortTrackMaxMs / 1000}s"
                    SmartAlbumType.TIME_CAPSULE -> {
                        val months = buildTimeCapsuleGroups(allSongs, sessions).size
                        if (months > 0) "$months monthly capsules" else type.description
                    }
                    SmartAlbumType.BY_YEAR -> {
                        val years = buildByYearGroups(allSongs).size
                        if (years > 0) "$years release eras" else type.description
                    }
                    else -> type.description
                },
                songCount = songs.size,
                previewArtUriString = songs.firstNotNullOfOrNull { it.albumArtUriString },
                totalDurationMs = songs.sumOf { it.durationMs }
            )
        }
    }

    fun findDuplicateSongs(allSongs: List<Song>): List<DuplicateGroup> {
        fun cleanDuplicateTitle(title: String): String {
            return normalizeKey(DUPLICATE_SUFFIX_REGEX.replace(title, ""))
        }

        // Group by clean normalized title + 4-second duration bucket so re-encoded/copied files match reliably
        return allSongs.groupBy { song ->
            val cleanTitle = cleanDuplicateTitle(song.originalTitle)
            val durationBucket = (song.durationMs / 4000L)
            "$cleanTitle::$durationBucket"
        }.values
            .filter { it.size > 1 }
            .map { group ->
                DuplicateGroup(
                    signature = "${cleanDuplicateTitle(group.first().title)}_${group.first().durationMs / 4000L}",
                    displayTitle = group.first().title,
                    songs = group.sortedBy { it.dateAdded }
                )
            }
            .sortedBy { normalizeKey(it.displayTitle) }
    }

    fun computeListeningDna(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ListeningDnaStats {
        val songsById = allSongs.associateBy { it.id }

        val effectiveArtistStats = buildArtistStats(allSongs, sessions)
        val top5Artists = effectiveArtistStats.take(5)
        val mostListenedArtist = effectiveArtistStats.firstOrNull()

        val songStats = buildSongStats(allSongs, sessions)
        val mostPlayedSong = songStats.firstOrNull()

        val totalListeningMs = maxOf(
            sessions.sumOf { it.durationListenedMs },
            allSongs.sumOf { it.totalListeningTimeMs }
        )

        val totalSongsPlayed = maxOf(
            sessions.size,
            allSongs.sumOf { it.playCount }
        )

        val monthlyHistory = buildMonthlyPoints(sessions, zoneId)
        val weeklyTrends = buildWeeklyPoints(sessions, zoneId)
        val dailyStats = buildDayStatsMap(sessions, zoneId)
        val mostActiveDay = dailyStats.values.maxWithOrNull(
            compareBy<DayStat> { it.totalListeningTimeMs }.thenBy { it.sessionCount }
        )

        val albumStats = buildAlbumStats(allSongs, sessions)

        return ListeningDnaStats(
            mostListenedArtist = mostListenedArtist,
            mostPlayedSong = mostPlayedSong,
            totalListeningTimeMs = totalListeningMs,
            top5Artists = top5Artists,
            monthlyHistory = monthlyHistory,
            mostActiveDay = mostActiveDay,
            totalSongsPlayed = totalSongsPlayed,
            listeningTrends = weeklyTrends,
            favoriteAlbums = albumStats.take(10),
            artistListeningTimes = effectiveArtistStats
        )
    }

    fun computeAnalyticsDashboard(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity>,
        filter: AnalyticsRangeFilter,
        rangeOffset: Int,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AnalyticsDashboardData {
        val safeOffset = rangeOffset.coerceAtMost(0)
        val dayFmtShort = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
        val dayFmtWeek = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dayFmtFull = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
        val monthFmtShort = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
        val monthFmtFull = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        // Pre-aggregate sessions by LocalDate, YearMonth, and Hour
        data class Bucket(var ms: Long = 0L, var plays: Int = 0)
        val byDate = hashMapOf<LocalDate, Bucket>()
        val byMonth = hashMapOf<YearMonth, Bucket>()
        val byHour = Array(24) { Bucket() }

        sessions.forEach { session ->
            val zdt = Instant.ofEpochMilli(session.timestampMs).atZone(zoneId)
            val d = zdt.toLocalDate()
            val ym = YearMonth.from(d)
            val hr = zdt.hour

            val db = byDate.getOrPut(d) { Bucket() }
            db.ms += session.durationListenedMs
            db.plays += 1

            val mb = byMonth.getOrPut(ym) { Bucket() }
            mb.ms += session.durationListenedMs
            mb.plays += 1

            byHour[hr].ms += session.durationListenedMs
            byHour[hr].plays += 1
        }

        val mainLinePoints: List<TimePeriodPoint>
        val listeningHoursByDay: List<TimePeriodPoint>
        val rangeLabel: String

        when (filter) {
            AnalyticsRangeFilter.DAYS_7 -> {
                val end = today.plusDays((safeOffset * 7).toLong())
                val start = end.minusDays(6)
                rangeLabel = "${start.format(dayFmtShort)} – ${end.format(dayFmtShort)}"
                mainLinePoints = (0..6).map { idx ->
                    val d = start.plusDays(idx.toLong())
                    val b = byDate[d] ?: Bucket()
                    TimePeriodPoint(
                        key = d.toString(),
                        label = d.format(dayFmtWeek),
                        fullLabel = d.format(dayFmtFull),
                        durationMs = b.ms,
                        playCount = b.plays
                    )
                }
                listeningHoursByDay = mainLinePoints
            }

            AnalyticsRangeFilter.DAYS_30 -> {
                val end = today.plusDays((safeOffset * 30).toLong())
                val start = end.minusDays(29)
                rangeLabel = "${start.format(dayFmtShort)} – ${end.format(dayFmtShort)}"
                mainLinePoints = (0..29).map { idx ->
                    val d = start.plusDays(idx.toLong())
                    val b = byDate[d] ?: Bucket()
                    TimePeriodPoint(
                        key = d.toString(),
                        label = d.format(dayFmtShort),
                        fullLabel = d.format(dayFmtFull),
                        durationMs = b.ms,
                        playCount = b.plays
                    )
                }
                listeningHoursByDay = mainLinePoints
            }

            AnalyticsRangeFilter.MONTHS_12 -> {
                val endMonth = YearMonth.from(today).plusMonths((safeOffset * 12).toLong())
                val startMonth = endMonth.minusMonths(11)
                rangeLabel = "${startMonth.format(monthFmtShort)} – ${endMonth.format(monthFmtShort)}"
                mainLinePoints = (0..11).map { idx ->
                    val ym = startMonth.plusMonths(idx.toLong())
                    val b = byMonth[ym] ?: Bucket()
                    TimePeriodPoint(
                        key = ym.toString(),
                        label = ym.format(monthFmtShort),
                        fullLabel = ym.format(monthFmtFull),
                        durationMs = b.ms,
                        playCount = b.plays
                    )
                }
                // Daily breakdown for the most recent 14 days within/up to this window
                val endDay = if (safeOffset == 0) today else endMonth.atEndOfMonth()
                val startDay = endDay.minusDays(13)
                listeningHoursByDay = (0..13).map { idx ->
                    val d = startDay.plusDays(idx.toLong())
                    val b = byDate[d] ?: Bucket()
                    TimePeriodPoint(
                        key = d.toString(),
                        label = d.format(dayFmtShort),
                        fullLabel = d.format(dayFmtFull),
                        durationMs = b.ms,
                        playCount = b.plays
                    )
                }
            }
        }

        val totalListeningMs = maxOf(
            sessions.sumOf { it.durationListenedMs },
            allSongs.sumOf { it.totalListeningTimeMs }
        )
        val firstSessionDate = sessions.minOfOrNull {
            Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).toLocalDate()
        }
        val activeDaySpan = if (firstSessionDate != null) {
            (ChronoUnit.DAYS.between(firstSessionDate, today) + 1).coerceAtLeast(1L)
        } else {
            1L
        }
        val dailyAverageMs = totalListeningMs / activeDaySpan
        val songsPlayedCount = maxOf(sessions.size, allSongs.sumOf { it.playCount })

        val allDayStats = buildDayStatsMap(sessions, zoneId)
        val mostActiveDay = allDayStats.values.maxWithOrNull(
            compareBy<DayStat> { it.totalListeningTimeMs }.thenBy { it.sessionCount }
        )

        val artistStats = buildArtistStats(allSongs, sessions)
        val songStats = buildSongStats(allSongs, sessions)
        val monthlyTrends = buildMonthlyPoints(sessions, zoneId, today)
        val weeklyTrends = buildWeeklyPoints(sessions, zoneId, today)

        val hourlyActivity = (0..23).map { hr ->
            val label = when {
                hr == 0 -> "12a"
                hr < 12 -> "${hr}a"
                hr == 12 -> "12p"
                else -> "${hr - 12}p"
            }
            HourlyActivityPoint(
                hour = hr,
                label = label,
                durationMs = byHour[hr].ms,
                playCount = byHour[hr].plays
            )
        }

        val mostActiveHour = hourlyActivity
            .filter { it.durationMs > 0 || it.playCount > 0 }
            .maxWithOrNull(compareBy<HourlyActivityPoint> { it.durationMs }.thenBy { it.playCount })

        val highestListeningMonth = monthlyTrends
            .filter { it.durationMs > 0 || it.playCount > 0 }
            .maxByOrNull { it.durationMs }

        val currentWeekMs = (0..6).sumOf { offset ->
            byDate[today.minusDays(offset.toLong())]?.ms ?: 0L
        }
        val previousWeekMs = (7..13).sumOf { offset ->
            byDate[today.minusDays(offset.toLong())]?.ms ?: 0L
        }
        val weekDeltaPercent: Float? = when {
            previousWeekMs > 0L -> ((currentWeekMs - previousWeekMs).toFloat() / previousWeekMs.toFloat()) * 100f
            currentWeekMs > 0L -> 100f
            else -> null
        }

        val currentYearMs = sessions
            .filter { Instant.ofEpochMilli(it.timestampMs).atZone(zoneId).year == today.year }
            .sumOf { it.durationListenedMs }
            .let { sessionYearMs ->
                if (sessionYearMs == 0L && totalListeningMs > 0L) totalListeningMs else sessionYearMs
            }

        return AnalyticsDashboardData(
            filter = filter,
            rangeOffset = safeOffset,
            rangeLabel = rangeLabel,
            canGoNext = safeOffset < 0,
            totalListeningMs = totalListeningMs,
            dailyAverageMs = dailyAverageMs,
            songsPlayedCount = songsPlayedCount,
            mostActiveDay = mostActiveDay,
            mainLinePoints = mainLinePoints,
            listeningHoursByDay = listeningHoursByDay,
            weeklyTrends = weeklyTrends,
            monthlyTrends = monthlyTrends,
            mostPlayedArtists = artistStats.sortedWith(
                compareByDescending<ArtistStat> { it.totalPlayCount }.thenByDescending { it.totalListeningTimeMs }
            ).take(8),
            top10Songs = songStats.take(10),
            listeningTimeByArtist = artistStats.sortedByDescending { it.totalListeningTimeMs }.take(8),
            hourlyActivity = hourlyActivity,
            mostListenedArtist = artistStats.maxByOrNull { it.totalListeningTimeMs },
            mostPlayedSong = songStats.firstOrNull(),
            longestListeningDay = mostActiveDay,
            averageDailyListeningMs = dailyAverageMs,
            highestListeningMonth = highestListeningMonth,
            mostActiveHour = mostActiveHour,
            currentWeekListeningMs = currentWeekMs,
            previousWeekListeningMs = previousWeekMs,
            weekComparisonDeltaPercent = weekDeltaPercent,
            currentYearListeningMs = currentYearMs
        )
    }

    private fun buildArtistStats(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity>
    ): List<ArtistStat> {
        val songsById = allSongs.associateBy { it.id }
        val artistAgg = linkedMapOf<String, Triple<MutableList<String>, Pair<Long, Int>, MutableSet<Long>>>()
        // Triple(nameVariants, Pair(totalMs, totalPlays), songIds)

        // Start with Song-level totals so historical/song-level stats and artworks remain consistent
        allSongs.forEach { song ->
            parseArtists(song.artist).forEach { part ->
                val key = normalizeKey(part)
                val entry = artistAgg.getOrPut(key) {
                    Triple(mutableListOf(), (0L to 0), mutableSetOf())
                }
                entry.first.add(part)
                entry.third.add(song.id)
                artistAgg[key] = entry.copy(
                    second = (entry.second.first + song.totalListeningTimeMs) to (entry.second.second + song.playCount)
                )
            }
        }

        // If sessions exist and produce larger values for artists (e.g., if a song was later deleted or split), reconcile upward
        val sessionMsByArtist = hashMapOf<String, Long>()
        val sessionPlaysByArtist = hashMapOf<String, Int>()
        sessions.forEach { session ->
            val rawArtist = songsById[session.songId]?.artist ?: session.artistName
            parseArtists(rawArtist).forEach { part ->
                val key = normalizeKey(part)
                val entry = artistAgg.getOrPut(key) {
                    Triple(mutableListOf(part), (0L to 0), mutableSetOf())
                }
                entry.first.add(part)
                sessionMsByArtist[key] = (sessionMsByArtist[key] ?: 0L) + session.durationListenedMs
                sessionPlaysByArtist[key] = (sessionPlaysByArtist[key] ?: 0) + 1
            }
        }

        return artistAgg.map { (key, triple) ->
            val (variants, songSums, songIds) = triple
            val totalMs = maxOf(songSums.first, sessionMsByArtist[key] ?: 0L)
            val totalPlays = maxOf(songSums.second, sessionPlaysByArtist[key] ?: 0)
            val art = songIds.mapNotNull { songsById[it] }
                .sortedByDescending { it.playCount }
                .firstNotNullOfOrNull { it.albumArtUriString }
            ArtistStat(
                artistName = chooseCanonicalName(variants),
                totalPlayCount = totalPlays,
                totalListeningTimeMs = totalMs,
                songCount = songIds.size,
                artworkUriString = art
            )
        }.filter { it.totalPlayCount > 0 || it.totalListeningTimeMs > 0 }
            .sortedWith(compareByDescending<ArtistStat> { it.totalListeningTimeMs }.thenByDescending { it.totalPlayCount })
    }

    private fun buildSongStats(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity>
    ): List<SongStat> {
        val songsById = allSongs.associateBy { it.id }
        val sessionMs = hashMapOf<Long, Long>()
        val sessionPlays = hashMapOf<Long, Int>()

        sessions.forEach { session ->
            sessionMs[session.songId] = (sessionMs[session.songId] ?: 0L) + session.durationListenedMs
            sessionPlays[session.songId] = (sessionPlays[session.songId] ?: 0) + 1
        }

        val allIds = (songsById.keys + sessionPlays.keys).distinct()
        return allIds.mapNotNull { id ->
            val song = songsById[id]
            val matchingSession = if (song == null) sessions.firstOrNull { it.songId == id } else null
            val plays = maxOf(song?.playCount ?: 0, sessionPlays[id] ?: 0)
            val ms = maxOf(song?.totalListeningTimeMs ?: 0L, sessionMs[id] ?: 0L)
            if (plays == 0 && ms == 0L) return@mapNotNull null

            SongStat(
                songId = id,
                title = song?.title ?: matchingSession?.songTitle ?: "Unknown Track",
                artist = song?.artist ?: matchingSession?.artistName ?: "Unknown Artist",
                playCount = plays,
                totalListeningTimeMs = ms,
                artworkUriString = song?.albumArtUriString
            )
        }.sortedWith(compareByDescending<SongStat> { it.playCount }.thenByDescending { it.totalListeningTimeMs })
    }

    private fun buildAlbumStats(
        allSongs: List<Song>,
        sessions: List<ListeningSessionEntity>
    ): List<AlbumStat> {
        return allSongs.groupBy { normalizeKey(it.album.ifBlank { "Unknown Album" }) }
            .mapNotNull { (_, songsInAlbum) ->
                val plays = songsInAlbum.sumOf { it.playCount }
                val ms = songsInAlbum.sumOf { it.totalListeningTimeMs }
                if (plays == 0 && ms == 0L) return@mapNotNull null
                val first = songsInAlbum.first()
                AlbumStat(
                    albumTitle = chooseCanonicalName(songsInAlbum.map { it.album.ifBlank { "Unknown Album" } }),
                    artist = first.artist,
                    totalPlayCount = plays,
                    totalListeningTimeMs = ms,
                    trackCount = songsInAlbum.size,
                    artworkUriString = songsInAlbum.firstNotNullOfOrNull { it.albumArtUriString }
                )
            }
            .sortedWith(compareByDescending<AlbumStat> { it.totalListeningTimeMs }.thenByDescending { it.totalPlayCount })
    }

    private fun buildDayStatsMap(
        sessions: List<ListeningSessionEntity>,
        zoneId: ZoneId
    ): Map<LocalDate, DayStat> {
        val fmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
        val map = linkedMapOf<LocalDate, Pair<Long, Int>>()
        sessions.forEach { s ->
            val d = Instant.ofEpochMilli(s.timestampMs).atZone(zoneId).toLocalDate()
            val cur = map[d] ?: (0L to 0)
            map[d] = (cur.first + s.durationListenedMs) to (cur.second + 1)
        }
        return map.mapValues { (date, pair) ->
            DayStat(
                dateKey = date.toString(),
                displayLabel = date.format(fmt),
                totalListeningTimeMs = pair.first,
                sessionCount = pair.second
            )
        }
    }

    private fun buildWeeklyPoints(
        sessions: List<ListeningSessionEntity>,
        zoneId: ZoneId,
        today: LocalDate = LocalDate.now(),
        weekCount: Int = 8
    ): List<TimePeriodPoint> {
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val fmtShort = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
        val fmtFull = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

        val buckets = hashMapOf<LocalDate, Pair<Long, Int>>()
        sessions.forEach { s ->
            val d = Instant.ofEpochMilli(s.timestampMs).atZone(zoneId).toLocalDate()
            val monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val cur = buckets[monday] ?: (0L to 0)
            buckets[monday] = (cur.first + s.durationListenedMs) to (cur.second + 1)
        }

        return ((weekCount - 1) downTo 0).map { weeksAgo ->
            val monday = currentWeekStart.minusWeeks(weeksAgo.toLong())
            val sunday = monday.plusDays(6)
            val b = buckets[monday] ?: (0L to 0)
            TimePeriodPoint(
                key = monday.toString(),
                label = monday.format(fmtShort),
                fullLabel = "${monday.format(fmtFull)} – ${sunday.format(fmtFull)}",
                durationMs = b.first,
                playCount = b.second
            )
        }
    }

    private fun buildMonthlyPoints(
        sessions: List<ListeningSessionEntity>,
        zoneId: ZoneId,
        today: LocalDate = LocalDate.now(),
        monthCount: Int = 12
    ): List<TimePeriodPoint> {
        val currentYm = YearMonth.from(today)
        val fmtShort = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val fmtFull = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        val buckets = hashMapOf<YearMonth, Pair<Long, Int>>()
        sessions.forEach { s ->
            val ym = YearMonth.from(Instant.ofEpochMilli(s.timestampMs).atZone(zoneId))
            val cur = buckets[ym] ?: (0L to 0)
            buckets[ym] = (cur.first + s.durationListenedMs) to (cur.second + 1)
        }

        return ((monthCount - 1) downTo 0).map { monthsAgo ->
            val ym = currentYm.minusMonths(monthsAgo.toLong())
            val b = buckets[ym] ?: (0L to 0)
            TimePeriodPoint(
                key = ym.toString(),
                label = ym.format(fmtShort),
                fullLabel = ym.format(fmtFull),
                durationMs = b.first,
                playCount = b.second
            )
        }
    }
}

