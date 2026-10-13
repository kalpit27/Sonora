package com.sonora.music.data.importer

import com.sonora.music.data.model.ImportedPlaylistResult
import com.sonora.music.data.model.ImportedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SmartPlaylistParser {

    suspend fun parse(input: String): Result<ImportedPlaylistResult> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Input cannot be empty"))
        }

        try {
            when {
                trimmed.contains("spotify.com") -> parseSpotify(trimmed)
                trimmed.contains("youtube.com") || trimmed.contains("youtu.be") -> parseYouTube(trimmed)
                else -> parsePlainText(trimmed)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to plain text parsing if network or link parsing failed
            Result.success(parsePlainTextInternal(trimmed))
        }
    }

    private fun parseSpotify(urlStr: String): Result<ImportedPlaylistResult> {
        val tracks = mutableListOf<ImportedTrack>()
        var playlistTitle = "Imported Spotify Playlist"

        // 1. Fetch Spotify oEmbed metadata
        val oEmbedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(urlStr, "UTF-8")
        val jsonStr = httpGet(oEmbedUrl)

        if (jsonStr != null) {
            val json = JSONObject(jsonStr)
            val title = json.optString("title", "").trim()
            if (title.isNotBlank()) {
                playlistTitle = title
            }
        }

        // 2. Fetch public HTML to parse embedded tracklist / meta tags
        val html = httpGet(urlStr)
        if (html != null) {
            // Check for OpenGraph or Twitter track meta tags
            val ogTitleRegex = Regex("""<meta property="og:title" content="([^"]+)">""")
            val ogTitleMatch = ogTitleRegex.find(html)
            if (ogTitleMatch != null && playlistTitle == "Imported Spotify Playlist") {
                playlistTitle = ogTitleMatch.groupValues[1].replace(" | Spotify", "").trim()
            }

            // Extract songs from track links inside playlist HTML: /track/ID -> song name in aria-label or inner text
            val trackRegex = Regex("""data-testid="tracklist-row"|href="/track/([a-zA-Z0-9]+)"[^>]*>([^<]+)<""")
            val matches = trackRegex.findAll(html)
            for (match in matches) {
                if (match.groupValues.size >= 3) {
                    val trackName = match.groupValues[2].trim()
                    if (trackName.isNotBlank() && trackName != "Spotify") {
                        tracks.add(ImportedTrack(title = trackName))
                    }
                }
            }

            // Also check for description meta tag which often lists tracks
            val descRegex = Regex("""<meta name="description" content="([^"]+)">""")
            val descMatch = descRegex.find(html)
            if (descMatch != null && tracks.isEmpty()) {
                val descContent = descMatch.groupValues[1]
                if (descContent.contains("·")) {
                    val parts = descContent.split("·")
                    for (part in parts) {
                        val clean = part.trim()
                        if (clean.isNotBlank() && clean.length > 2) {
                            tracks.add(cleanTrackLine(clean))
                        }
                    }
                }
            }
        }

        // If it was a single track URL
        if (tracks.isEmpty()) {
            tracks.add(cleanTrackLine(playlistTitle))
        }

        return Result.success(
            ImportedPlaylistResult(
                playlistName = playlistTitle,
                description = "Imported from Spotify ($urlStr)",
                source = "Spotify",
                tracks = tracks.distinctBy { it.title.lowercase() }
            )
        )
    }

    private fun parseYouTube(urlStr: String): Result<ImportedPlaylistResult> {
        val tracks = mutableListOf<ImportedTrack>()
        var playlistTitle = "Imported YouTube Music Playlist"

        // Check if playlist ID is present: list=PLAYLIST_ID
        val listIdRegex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
        val match = listIdRegex.find(urlStr)

        if (match != null) {
            val playlistId = match.groupValues[1]
            // Use public YouTube RSS feed for the playlist
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId"
            val rssXml = httpGet(rssUrl)

            if (rssXml != null) {
                // Extract playlist title
                val titleRegex = Regex("""<title>([^<]+)</title>""")
                val titleMatches = titleRegex.findAll(rssXml).toList()
                if (titleMatches.isNotEmpty()) {
                    playlistTitle = titleMatches.first().groupValues[1].trim()
                }

                // Extract all video titles in the playlist (skip the first one which is the playlist title)
                for (i in 1 until titleMatches.size) {
                    val rawTitle = titleMatches[i].groupValues[1].trim()
                    if (rawTitle.isNotBlank()) {
                        tracks.add(cleanTrackLine(rawTitle))
                    }
                }
            }
        }

        // If single video or RSS was empty, use YouTube oEmbed API
        if (tracks.isEmpty()) {
            val oEmbedUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(urlStr, "UTF-8") + "&format=json"
            val jsonStr = httpGet(oEmbedUrl)
            if (jsonStr != null) {
                val json = JSONObject(jsonStr)
                val title = json.optString("title", "").trim()
                val author = json.optString("author_name", "").trim()
                if (title.isNotBlank()) {
                    playlistTitle = title
                    tracks.add(ImportedTrack(title = cleanVideoTitle(title), artist = author))
                }
            }
        }

        if (tracks.isEmpty()) {
            tracks.add(cleanTrackLine(playlistTitle))
        }

        return Result.success(
            ImportedPlaylistResult(
                playlistName = playlistTitle,
                description = "Imported from YouTube Music",
                source = "YouTube Music",
                tracks = tracks.distinctBy { it.title.lowercase() }
            )
        )
    }

    private fun parsePlainText(text: String): Result<ImportedPlaylistResult> {
        return Result.success(parsePlainTextInternal(text))
    }

    private fun parsePlainTextInternal(text: String): ImportedPlaylistResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val tracks = lines.map { cleanTrackLine(it) }
        val defaultName = if (tracks.size == 1) tracks.first().title else "Imported Mix (${tracks.size} tracks)"

        return ImportedPlaylistResult(
            playlistName = defaultName,
            description = "Custom tracklist import",
            source = "Track List",
            tracks = tracks
        )
    }

    private fun cleanTrackLine(line: String): ImportedTrack {
        // Remove leading numbering like "1.", "01 -", "[1]", "(1)"
        val stripped = line.replace(Regex("""^[\d\s.\-_)\]]+"""), "").trim()

        // Check for separator like " - " or " by "
        return when {
            stripped.contains(" - ") -> {
                val parts = stripped.split(" - ", limit = 2)
                ImportedTrack(title = cleanVideoTitle(parts[0]), artist = parts.getOrElse(1) { "" }.trim())
            }
            stripped.contains(" by ", ignoreCase = true) -> {
                val parts = stripped.split(Regex("""\s+by\s+""", RegexOption.IGNORE_CASE), limit = 2)
                ImportedTrack(title = cleanVideoTitle(parts[0]), artist = parts.getOrElse(1) { "" }.trim())
            }
            else -> {
                ImportedTrack(title = cleanVideoTitle(stripped))
            }
        }
    }

    private fun cleanVideoTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\(official\s*(music)?\s*video\)"""), "")
            .replace(Regex("""(?i)\[official\s*(music)?\s*video\]"""), "")
            .replace(Regex("""(?i)\(official\s*audio\)"""), "")
            .replace(Regex("""(?i)\[official\s*audio\]"""), "")
            .replace(Regex("""(?i)\(lyric\s*video\)"""), "")
            .replace(Regex("""(?i)\[lyric\s*video\]"""), "")
            .replace(Regex("""(?i)\(from\s*"[^"]+"\)|\[from\s*"[^"]+"\]"""), "")
            .replace(Regex("""(?i)\(from\s+[^)]+\)"""), "")
            .replace(Regex("""(?i)ft\.?\s+.*|feat\.?\s+.*"""), "")
            .trim()
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            conn.connect()
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
