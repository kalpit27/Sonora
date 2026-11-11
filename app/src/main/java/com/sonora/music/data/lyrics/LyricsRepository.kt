package com.sonora.music.data.lyrics

import android.content.Context
import com.sonora.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsRepository(private val context: Context) {

    private val lyricsDir: File by lazy {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "lyrics").apply { mkdirs() }
    }

    /**
     * Retrieves synchronized or plain lyrics for a given song.
     * Checks local disk cache first, then queries the open LRCLIB database.
     */
    suspend fun getLyrics(song: Song): Result<LyricsResult> = withContext(Dispatchers.IO) {
        val cleanRawTitle = cleanTitle(song.title)
        val cleanRawArtist = cleanArtist(song.artist)

        val parsed = parseTrackAndArtist(cleanRawTitle, cleanRawArtist)
        val primaryTrack = parsed.track
        val primaryArtist = parsed.artist

        // 1. Check local cache (parsed track & artist, or raw title)
        val cacheFilePrimary = getCacheFile(primaryTrack, primaryArtist)
        val cacheFileRaw = getCacheFile(cleanRawTitle, cleanRawArtist)

        val cachedLrc = readFromCache(cacheFilePrimary) ?: readFromCache(cacheFileRaw)
        if (cachedLrc != null) {
            return@withContext Result.success(cachedLrc)
        }

        // 2. Fetch from LRCLIB open lyrics database
        try {
            val durationSec = (song.durationMs / 1000).toInt()
            val lrclibResult = fetchFromLrclib(primaryTrack, primaryArtist, cleanRawTitle, cleanRawArtist, durationSec)

            if (lrclibResult != null) {
                // Cache to disk for instant offline access
                try {
                    val contentToCache = if (lrclibResult.isSynced) {
                        lrclibResult.lines.joinToString("\n") { "[${formatMs(it.timeMs)}] ${it.text}" }
                    } else {
                        lrclibResult.plainLyrics ?: ""
                    }
                    if (contentToCache.isNotBlank()) {
                        cacheFilePrimary.writeText(contentToCache)
                    }
                } catch (ignored: Exception) {}

                return@withContext Result.success(lrclibResult)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Result.failure(NoSuchElementException("No lyrics found for \"$primaryTrack\""))
    }

    private fun readFromCache(cacheFile: File): LyricsResult? {
        if (!cacheFile.exists() || cacheFile.length() == 0L) return null
        return try {
            val cachedContent = cacheFile.readText()
            val parsedLines = LrcParser.parse(cachedContent)
            if (parsedLines.isNotEmpty()) {
                LyricsResult(
                    trackName = cacheFile.nameWithoutExtension,
                    artistName = "",
                    isSynced = true,
                    lines = parsedLines,
                    source = "Local Cache"
                )
            } else if (cachedContent.isNotBlank()) {
                LyricsResult(
                    trackName = cacheFile.nameWithoutExtension,
                    artistName = "",
                    isSynced = false,
                    plainLyrics = cachedContent,
                    source = "Local Cache"
                )
            } else null
        } catch (ignored: Exception) {
            null
        }
    }

    private fun fetchFromLrclib(
        primaryTrack: String,
        primaryArtist: String,
        rawTitle: String,
        rawArtist: String,
        durationSec: Int
    ): LyricsResult? {
        // Step 1: Direct GET with parsed track and artist
        fetchDirectLrclib(primaryTrack, primaryArtist, durationSec)?.let { return it }

        // Step 2: Direct GET with raw title and artist if different
        if (primaryTrack != rawTitle || primaryArtist != rawArtist) {
            fetchDirectLrclib(rawTitle, rawArtist, durationSec)?.let { return it }
        }

        // Step 3: Direct GET without duration (duration mismatch is a common cause of 404 on LRCLIB)
        if (durationSec > 0) {
            fetchDirectLrclib(primaryTrack, primaryArtist, 0)?.let { return it }
        }

        // Step 4: Search API queries ordered by relevance
        val queries = linkedSetOf<String>()
        if (primaryTrack.isNotBlank() && primaryArtist.isNotBlank() && !primaryArtist.equals("Unknown Artist", ignoreCase = true)) {
            queries.add("$primaryTrack $primaryArtist")
        }
        if (primaryTrack.isNotBlank()) {
            queries.add(primaryTrack)
        }
        if (rawTitle.isNotBlank() && rawArtist.isNotBlank() && !rawArtist.equals("Unknown Artist", ignoreCase = true)) {
            queries.add("$rawTitle $rawArtist")
        }
        if (rawTitle.isNotBlank() && rawTitle != primaryTrack) {
            queries.add(rawTitle)
        }

        for (query in queries) {
            val searchResult = fetchSearchLrclib(query, primaryTrack, primaryArtist)
            if (searchResult != null) return searchResult
        }

        return null
    }

    private fun fetchDirectLrclib(title: String, artist: String, durationSec: Int): LyricsResult? {
        if (title.isBlank()) return null
        val directUrl = buildString {
            append("https://lrclib.net/api/get?")
            append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            if (artist.isNotBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) {
                append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            }
            if (durationSec > 10) {
                append("&duration=").append(durationSec)
            }
        }
        val directJson = fetchJson(directUrl) ?: return null
        return parseLrclibJson(directJson, title, artist)
    }

    private fun fetchSearchLrclib(query: String, fallbackTitle: String, fallbackArtist: String): LyricsResult? {
        val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8")
        val searchJsonArray = fetchJsonArray(searchUrl) ?: return null
        if (searchJsonArray.length() == 0) return null

        var plainFallback: LyricsResult? = null

        for (i in 0 until searchJsonArray.length()) {
            val item = searchJsonArray.optJSONObject(i) ?: continue
            val res = parseLrclibJson(item, fallbackTitle, fallbackArtist) ?: continue
            // Prioritize synchronized lyrics immediately
            if (res.isSynced && res.lines.isNotEmpty()) {
                return res
            }
            if (plainFallback == null && !res.plainLyrics.isNullOrBlank()) {
                plainFallback = res
            }
        }

        return plainFallback
    }

    private fun parseTrackAndArtist(rawTitle: String, rawArtist: String): ParsedTrackInfo {
        var cleanTit = cleanTitle(rawTitle)
        var cleanArt = cleanArtist(rawArtist)

        // If title contains "Artist - Title" or "Artist : Title"
        val dashMatch = Regex("""^(.+?)\s*[-–—:]\s*(.+)$""").find(cleanTit)
        if (dashMatch != null) {
            val part1 = dashMatch.groupValues[1].trim()
            val part2 = dashMatch.groupValues[2].trim()

            // If raw artist is generic or channel-like (e.g. VEVO, Topic, Unknown Artist), part1 is the real artist
            if (cleanArt.isBlank() || cleanArt.equals("Unknown Artist", ignoreCase = true) ||
                cleanArt.contains("VEVO", ignoreCase = true) || cleanArt.contains("Topic", ignoreCase = true) ||
                cleanArt.replace(" ", "").equals(part1.replace(" ", ""), ignoreCase = true)) {
                cleanArt = part1
                cleanTit = part2
            } else {
                cleanTit = part2
                if (cleanArt.isBlank()) {
                    cleanArt = part1
                }
            }
        }

        return ParsedTrackInfo(track = cleanTit.trim(), artist = cleanArt.trim())
    }

    data class ParsedTrackInfo(
        val track: String,
        val artist: String
    )

    private fun parseLrclibJson(json: JSONObject, fallbackTitle: String, fallbackArtist: String): LyricsResult? {
        val syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val trackName = json.optString("trackName").ifBlank { fallbackTitle }
        val artistName = json.optString("artistName").ifBlank { fallbackArtist }

        if (syncedLyrics != null) {
            val lines = LrcParser.parse(syncedLyrics)
            if (lines.isNotEmpty()) {
                return LyricsResult(
                    trackName = trackName,
                    artistName = artistName,
                    isSynced = true,
                    lines = lines,
                    plainLyrics = plainLyrics,
                    source = "LRCLIB"
                )
            }
        }

        if (plainLyrics != null) {
            return LyricsResult(
                trackName = trackName,
                artistName = artistName,
                isSynced = false,
                plainLyrics = plainLyrics,
                source = "LRCLIB"
            )
        }

        return null
    }

    private fun fetchJson(urlString: String): JSONObject? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Sonora-Music-Player/1.0.0 (https://github.com/kalpit/sonora)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                JSONObject(text)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchJsonArray(urlString: String): JSONArray? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Sonora-Music-Player/1.0.0 (https://github.com/kalpit/sonora)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                JSONArray(text)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\(official\s*(music)?\s*(video|audio|lyric|visualizer)?.*?\)|\[official\s*(music)?\s*(video|audio|lyric|visualizer)?.*?]"""), "")
            .replace(Regex("""(?i)\(lyrics?\)|\[lyrics?]|\(audio\)|\(visualizer\)"""), "")
            .replace(Regex("""(?i)\(4k\s*(uhd|video)?\)|\(hd\s*video?\)"""), "")
            .replace(Regex("""(?i)\(feat\..*?\)|\(ft\..*?\)"""), "")
            .replace(Regex("""(?i)ft\..*|feat\..*"""), "")
            .replace(Regex("""(?i)\b(official\s*(music\s*)?video|official\s*audio|lyric\s*video|full\s*song|video\s*song)\b"""), "")
            .replace(Regex("""[|_~]"""), " ")
            .trim()
    }

    private fun cleanArtist(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)vevo$"""), "")
            .replace(Regex("""(?i)official$"""), "")
            .trim()
    }

    private fun getCacheFile(title: String, artist: String): File {
        val hash = (title.trim().lowercase() + "_" + artist.trim().lowercase()).hashCode()
        return File(lyricsDir, "lrc_$hash.lrc")
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        val hundredths = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(mins, secs, hundredths)
    }

    /**
     * Searches LRCLIB for lyrics matching a user query on the spot.
     */
    suspend fun searchLyricsList(query: String): Result<List<LyricsSearchResultItem>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        try {
            val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")
            val searchArray = fetchJsonArray(searchUrl) ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<LyricsSearchResultItem>()
            for (i in 0 until searchArray.length()) {
                val obj = searchArray.optJSONObject(i) ?: continue
                val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                val track = obj.optString("trackName").trim()
                val artist = obj.optString("artistName").trim()
                val album = obj.optString("albumName").takeIf { it.isNotBlank() && it != "null" }
                val dur = obj.optInt("duration", 0)
                val id = obj.optLong("id", 0L)

                if (synced != null || plain != null) {
                    list.add(
                        LyricsSearchResultItem(
                            id = id,
                            trackName = track.ifBlank { "Unknown Title" },
                            artistName = artist.ifBlank { "Unknown Artist" },
                            albumName = album,
                            durationSec = dur,
                            isSynced = !synced.isNullOrBlank(),
                            syncedLyrics = synced,
                            plainLyrics = plain
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies and caches a chosen search result to the current song for immediate synced playback.
     */
    suspend fun applyLyricsToSong(song: Song, item: LyricsSearchResultItem): Result<LyricsResult> = withContext(Dispatchers.IO) {
        try {
            val lines = if (!item.syncedLyrics.isNullOrBlank()) {
                LrcParser.parse(item.syncedLyrics)
            } else emptyList()

            val isSynced = lines.isNotEmpty()
            val result = LyricsResult(
                trackName = item.trackName,
                artistName = item.artistName,
                isSynced = isSynced,
                lines = lines,
                plainLyrics = item.plainLyrics,
                source = "LRCLIB Search"
            )

            val contentToCache = if (isSynced) {
                lines.joinToString("\n") { "[${formatMs(it.timeMs)}] ${it.text}" }
            } else {
                item.plainLyrics ?: ""
            }

            if (contentToCache.isNotBlank()) {
                val cleanTit = cleanTitle(song.title)
                val cleanArt = cleanArtist(song.artist)
                val parsed = parseTrackAndArtist(cleanTit, cleanArt)
                getCacheFile(parsed.track, parsed.artist).writeText(contentToCache)
                getCacheFile(cleanTit, cleanArt).writeText(contentToCache)
                getCacheFile(song.title, song.artist).writeText(contentToCache)
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves user-provided custom LRC or plain lyrics to the current song.
     */
    suspend fun saveCustomLyrics(song: Song, rawText: String): Result<LyricsResult> = withContext(Dispatchers.IO) {
        try {
            val parsedLines = LrcParser.parse(rawText)
            val isSynced = parsedLines.isNotEmpty()
            val result = LyricsResult(
                trackName = song.title,
                artistName = song.artist,
                isSynced = isSynced,
                lines = parsedLines,
                plainLyrics = if (!isSynced) rawText else null,
                source = "Custom"
            )

            val cleanTit = cleanTitle(song.title)
            val cleanArt = cleanArtist(song.artist)
            val parsed = parseTrackAndArtist(cleanTit, cleanArt)
            getCacheFile(parsed.track, parsed.artist).writeText(rawText)
            getCacheFile(cleanTit, cleanArt).writeText(rawText)
            getCacheFile(song.title, song.artist).writeText(rawText)

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

