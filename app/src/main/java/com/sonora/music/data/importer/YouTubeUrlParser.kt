package com.sonora.music.data.importer

import com.sonora.music.data.model.YouTubePlaylistInfo
import com.sonora.music.data.model.YouTubePlaylistItem
import com.sonora.music.data.model.YouTubeVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class YouTubeUrlParser {

    companion object {
        // Regex patterns to identify YouTube video IDs (11 chars) across all known YouTube domains & schemes
        private val YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
            """(?i)(?:https?://)?(?:www\.|m\.|music\.)?(?:youtube\.com/(?:watch\?(?:.*&)?v=|embed/|v/|shorts/)|youtu\.be/)([a-zA-Z0-9_-]{11})"""
        )

        // Regex pattern to extract playlist ID from query parameter: ?list=... or &list=...
        private val YOUTUBE_PLAYLIST_ID_PATTERN = Pattern.compile(
            """(?i)[?&]list=([a-zA-Z0-9_-]+)"""
        )

        private val CLEAN_TITLE_REGEXES = listOf(
            Regex("""(?i)\(official\s*(music)?\s*video\)"""),
            Regex("""(?i)\[official\s*(music)?\s*video\]"""),
            Regex("""(?i)\(official\s*audio\)"""),
            Regex("""(?i)\[official\s*audio\]"""),
            Regex("""(?i)\(lyric\s*video\)"""),
            Regex("""(?i)\[lyric\s*video\]"""),
            Regex("""(?i)\(visualizer\)"""),
            Regex("""(?i)\[visualizer\]"""),
            Regex("""(?i)\(audio\)"""),
            Regex("""(?i)\[audio\]"""),
            Regex("""(?i)\[4k\]|\[hd\]|\(hd\)|\[hq\]|\(hq\)"""),
            Regex("""(?i)\(full\s*song\)"""),
            Regex("""(?i)\[full\s*song\]""")
        )
    }

    /**
     * Checks whether the given text contains a valid YouTube video URL, playlist URL, or ID.
     */
    fun isValidYouTubeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return false
        if (isPlaylistUrl(trimmed)) return true
        val matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) return true

        // Also accept raw 11-character video ID if alphanumeric + '_' + '-'
        return trimmed.length == 11 && trimmed.matches(Regex("""[a-zA-Z0-9_-]{11}"""))
    }

    /**
     * Checks whether the input string contains a YouTube playlist link or playlist ID.
     */
    fun isPlaylistUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return false
        val matcher = YOUTUBE_PLAYLIST_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val listId = matcher.group(1)
            // Valid playlist IDs are typically at least 12 characters or standard prefixes like PL, UU, LL, RD, OLAK
            return listId != null && listId.length >= 8
        }
        // Direct playlist ID check
        return (trimmed.startsWith("PL") || trimmed.startsWith("UU") || trimmed.startsWith("RD") || trimmed.startsWith("OLAK5uy_")) && trimmed.length >= 12
    }

    /**
     * Extracts the playlist ID from a YouTube link or raw ID string.
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val matcher = YOUTUBE_PLAYLIST_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        if ((trimmed.startsWith("PL") || trimmed.startsWith("UU") || trimmed.startsWith("RD") || trimmed.startsWith("OLAK5uy_")) && trimmed.length >= 12) {
            return trimmed
        }
        return null
    }

    /**
     * Extracts the 11-character YouTube video ID.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        if (trimmed.length == 11 && trimmed.matches(Regex("""[a-zA-Z0-9_-]{11}"""))) {
            return trimmed
        }
        return null
    }

    /**
     * Cleans up common video title artifacts (e.g. "[Official Video]") to produce
     * a clean, polished track title for the music library.
     */
    fun cleanTrackTitle(rawTitle: String): String {
        var cleaned = rawTitle.trim()
        for (pattern in CLEAN_TITLE_REGEXES) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim().replace(Regex("""\s+"""), " ")
    }

    /**
     * Extracts author and clean title if title is formatted as "Artist - Song".
     */
    fun parseArtistAndTitle(rawTitle: String, fallbackAuthor: String): Pair<String, String> {
        val cleaned = cleanTrackTitle(rawTitle)
        return when {
            cleaned.contains(" - ") -> {
                val parts = cleaned.split(" - ", limit = 2)
                val artist = parts[0].trim().ifBlank { fallbackAuthor }
                val title = parts[1].trim().ifBlank { cleaned }
                Pair(artist, title)
            }
            cleaned.contains(" – ") -> { // En-dash
                val parts = cleaned.split(" – ", limit = 2)
                val artist = parts[0].trim().ifBlank { fallbackAuthor }
                val title = parts[1].trim().ifBlank { cleaned }
                Pair(artist, title)
            }
            else -> {
                Pair(fallbackAuthor.ifBlank { "YouTube Creator" }, cleaned)
            }
        }
    }

    /**
     * Fetches video metadata via official YouTube oEmbed API.
     */
    suspend fun fetchVideoInfo(input: String): Result<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(input)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid YouTube URL. Please provide a standard youtube.com, youtu.be, or shorts link.")
            )

        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        val oEmbedEndpoint = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(canonicalUrl, "UTF-8") + "&format=json"

        try {
            val url = URL(oEmbedEndpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) SonoraMusic/1.0"
            )
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode == 404 || responseCode == 401 || responseCode == 403) {
                return@withContext Result.failure(
                    IllegalStateException("This YouTube video could not be found or has access restrictions (private or unavailable).")
                )
            }
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("YouTube API returned error (HTTP $responseCode). Please try again.")
                )
            }

            val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(jsonStr)

            val rawTitle = json.optString("title", "YouTube Audio").trim()
            val author = json.optString("author_name", "YouTube Creator").trim()
            val authorUrl = json.optString("author_url", "")
            val oEmbedThumbnail = json.optString("thumbnail_url", "")
            val fallbackThumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val chosenThumbnail = if (oEmbedThumbnail.isNotBlank()) oEmbedThumbnail else fallbackThumbnail

            val videoInfo = YouTubeVideoInfo(
                videoId = videoId,
                originalUrl = canonicalUrl,
                title = cleanTrackTitle(rawTitle),
                channelTitle = author,
                thumbnailUrl = chosenThumbnail,
                durationSeconds = 210L, // Default estimated 3.5m
                description = "Imported from YouTube: $author",
                authorUrl = authorUrl.ifBlank { null }
            )

            Result.success(videoInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(
                Exception("Network interruption: Unable to reach YouTube. Check your internet connection.", e)
            )
        }
    }

    /**
     * Fetches complete YouTube playlist metadata and all constituent tracks.
     * Uses YouTube's official Atom/RSS feed and fallback parsing.
     */
    suspend fun fetchPlaylistInfo(input: String): Result<YouTubePlaylistInfo> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(input)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid YouTube Playlist URL. Please provide a link containing '?list=PLAYLIST_ID'.")
            )

        val canonicalUrl = "https://www.youtube.com/playlist?list=$playlistId"
        val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId"

        try {
            val url = URL(rssUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) SonoraMusic/1.0"
            )
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val xml = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val parsed = parsePlaylistXml(playlistId, canonicalUrl, xml)
                if (parsed.tracks.isNotEmpty()) {
                    return@withContext Result.success(parsed)
                }
            }

            // Fallback: Webpage HTML scrape if RSS returned no tracks or was unavailable
            val htmlFallback = fetchPlaylistFromHtml(playlistId, canonicalUrl)
            if (htmlFallback.isSuccess && htmlFallback.getOrThrow().tracks.isNotEmpty()) {
                return@withContext htmlFallback
            }

            Result.failure(IllegalStateException("No tracks found in playlist or playlist is private."))
        } catch (e: Exception) {
            e.printStackTrace()
            // Try HTML fallback on network/RSS error
            val fallback = fetchPlaylistFromHtml(playlistId, canonicalUrl)
            if (fallback.isSuccess && fallback.getOrThrow().tracks.isNotEmpty()) {
                fallback
            } else {
                Result.failure(Exception("Unable to load YouTube playlist: ${e.message}", e))
            }
        }
    }

    private fun parsePlaylistXml(playlistId: String, originalUrl: String, xml: String): YouTubePlaylistInfo {
        // Extract Playlist Title
        val titleRegex = Regex("""<title>(.*?)</title>""")
        val titleMatches = titleRegex.findAll(xml).toList()
        val playlistTitle = if (titleMatches.isNotEmpty()) {
            cleanXmlText(titleMatches.first().groupValues[1])
        } else "YouTube Playlist"

        // Extract Author
        val authorRegex = Regex("""<author>\s*<name>(.*?)</name>""")
        val authorMatch = authorRegex.find(xml)
        val authorName = if (authorMatch != null) {
            cleanXmlText(authorMatch.groupValues[1])
        } else "YouTube"

        // Parse Entries
        val tracks = mutableListOf<YouTubePlaylistItem>()
        val entryRegex = Regex("""<entry>([\s\S]*?)</entry>""")
        val entries = entryRegex.findAll(xml)

        for (entry in entries) {
            val content = entry.groupValues[1]

            // Video ID
            val videoIdRegex = Regex("""<yt:videoId>(.*?)</yt:videoId>""")
            val videoIdMatch = videoIdRegex.find(content)
            val videoId = videoIdMatch?.groupValues?.get(1)?.trim() ?: continue

            // Title
            val trackTitleRegex = Regex("""<title>(.*?)</title>""")
            val trackTitleMatch = trackTitleRegex.find(content)
            val rawTitle = trackTitleMatch?.groupValues?.get(1)?.let { cleanXmlText(it) } ?: "Unknown Title"

            // Author/Channel
            val trackAuthorRegex = Regex("""<author>\s*<name>(.*?)</name>""")
            val trackAuthorMatch = trackAuthorRegex.find(content)
            val trackAuthor = trackAuthorMatch?.groupValues?.get(1)?.let { cleanXmlText(it) } ?: authorName

            // Thumbnail
            val thumbRegex = Regex("""<media:thumbnail\s+url="([^"]+)"""")
            val thumbMatch = thumbRegex.find(content)
            val thumbUrl = thumbMatch?.groupValues?.get(1) ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val (artist, cleanTitle) = parseArtistAndTitle(rawTitle, trackAuthor)

            tracks.add(
                YouTubePlaylistItem(
                    videoId = videoId,
                    title = cleanTitle,
                    channelTitle = artist,
                    thumbnailUrl = thumbUrl,
                    originalUrl = "https://www.youtube.com/watch?v=$videoId",
                    isSelected = true,
                    durationSeconds = 210L
                )
            )
        }

        return YouTubePlaylistInfo(
            playlistId = playlistId,
            title = playlistTitle,
            author = authorName,
            originalUrl = originalUrl,
            tracks = tracks
        )
    }

    private fun fetchPlaylistFromHtml(playlistId: String, originalUrl: String): Result<YouTubePlaylistInfo> {
        return try {
            val url = URL(originalUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            conn.connect()

            if (conn.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("Playlist page returned HTTP ${conn.responseCode}"))
            }

            val html = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }

            // Title
            val ogTitleRegex = Regex("""<meta property="og:title" content="([^"]+)">""")
            val titleMatch = ogTitleRegex.find(html)
            val playlistTitle = titleMatch?.groupValues?.get(1)?.replace(" - YouTube", "")?.trim() ?: "Imported Playlist"

            // Extract video IDs and titles from embedded JSON or links
            val tracks = mutableListOf<YouTubePlaylistItem>()
            val videoRegex = Regex(""""videoId":"([a-zA-Z0-9_-]{11})"[^}]*?"title":\{"runs":\[\{"text":"([^"]+)"""")
            val matches = videoRegex.findAll(html)

            val seenIds = mutableSetOf<String>()
            for (match in matches) {
                val vid = match.groupValues[1]
                val rawTitle = match.groupValues[2]
                if (seenIds.add(vid)) {
                    val (artist, cleanTitle) = parseArtistAndTitle(rawTitle, "YouTube")
                    tracks.add(
                        YouTubePlaylistItem(
                            videoId = vid,
                            title = cleanTitle,
                            channelTitle = artist,
                            thumbnailUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                            originalUrl = "https://www.youtube.com/watch?v=$vid",
                            isSelected = true,
                            durationSeconds = 210L
                        )
                    )
                }
            }

            Result.success(
                YouTubePlaylistInfo(
                    playlistId = playlistId,
                    title = playlistTitle,
                    author = "YouTube Playlist",
                    originalUrl = originalUrl,
                    tracks = tracks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanXmlText(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
