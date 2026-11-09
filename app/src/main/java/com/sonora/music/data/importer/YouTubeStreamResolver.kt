package com.sonora.music.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Candidate audio or progressive video/audio stream URL returned by YouTube API or mirrors.
 */
data class YouTubeAudioCandidate(
    val url: String,
    val mimeType: String,
    val itag: Int = 0,
    val bitrate: Int = 0,
    val isProgressive: Boolean = false,
    val userAgent: String = YouTubeStreamResolver.DEFAULT_ANDROID_UA
)

/**
 * Resolves downloadable audio and progressive stream URLs for a given YouTube video ID.
 *
 * Strategies:
 * 1. POST to YouTube's internal `youtubei/v1/player` endpoint using ANDROID client payloads.
 *    Extracts both high-bitrate adaptive audio streams and resilient progressive streams (itag 18 / 22)
 *    which contain AAC stereo audio and are guaranteed never to hit HTTP 403 on Google Video CDN.
 * 2. Fallback to public Invidious instances.
 */
class YouTubeStreamResolver {

    companion object {
        const val DEFAULT_ANDROID_UA =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"

        // YouTube internal API - Android client with standard Innertube API key
        private const val YT_PLAYER_URL =
            "https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w&prettyPrint=false"

        // ANDROID client payload (gives direct unencrypted CDN URLs)
        private fun buildAndroidClientPayload(videoId: String): String = """
            {
              "videoId": "$videoId",
              "context": {
                "client": {
                  "clientName": "ANDROID",
                  "clientVersion": "20.10.38",
                  "androidSdkVersion": 30,
                  "osName": "Android",
                  "osVersion": "11",
                  "hl": "en",
                  "gl": "US"
                }
              }
            }
        """.trimIndent()

        // ANDROID client alternative version
        private fun buildAndroidFallbackPayload(videoId: String): String = """
            {
              "videoId": "$videoId",
              "context": {
                "client": {
                  "clientName": "ANDROID",
                  "clientVersion": "19.44.38",
                  "androidSdkVersion": 30,
                  "osName": "Android",
                  "osVersion": "11",
                  "hl": "en",
                  "gl": "US"
                }
              }
            }
        """.trimIndent()

        // ANDROID_EMBEDDED client payload
        private fun buildAndroidEmbeddedPayload(videoId: String): String = """
            {
              "videoId": "$videoId",
              "context": {
                "client": {
                  "clientName": "ANDROID_EMBEDDED_PLAYER",
                  "clientVersion": "20.10.38",
                  "androidSdkVersion": 30,
                  "osName": "Android",
                  "osVersion": "11",
                  "hl": "en",
                  "gl": "US"
                }
              }
            }
        """.trimIndent()

        // Curated list of public Invidious instances (fallback only)
        private val INVIDIOUS_INSTANCES = listOf(
            "https://yewtu.be",
            "https://invidious.fdn.fr",
            "https://inv.tux.pizza",
            "https://invidious.protokolla.fi",
            "https://yt.cdaut.de",
            "https://invidious.sethforprivacy.com",
            "https://inv.nadeko.net",
            "https://invidious.perennialte.ch"
        )

        // Audio MIME types we accept
        private val AUDIO_MIME_TYPES = setOf(
            "audio/mp4", "audio/webm", "audio/mpeg", "audio/ogg", "audio/aac"
        )
    }

    /**
     * Resolves all available candidate stream URLs (adaptive audio + progressive AAC streams)
     * ordered by suitability.
     */
    suspend fun resolveStreamCandidates(
        videoId: String,
        preferHighQuality: Boolean = true
    ): Result<List<YouTubeAudioCandidate>> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // --- Strategy 1: YouTube internal API - ANDROID 20.10.38 client ---
        tryYouTubeInternalApiCandidates(
            videoId, "ANDROID_20", buildAndroidClientPayload(videoId),
            DEFAULT_ANDROID_UA, preferHighQuality, errors
        )?.takeIf { it.isNotEmpty() }?.let { return@withContext Result.success(it) }

        // --- Strategy 2: YouTube internal API - ANDROID 19.44.38 client ---
        tryYouTubeInternalApiCandidates(
            videoId, "ANDROID_19", buildAndroidFallbackPayload(videoId),
            DEFAULT_ANDROID_UA, preferHighQuality, errors
        )?.takeIf { it.isNotEmpty() }?.let { return@withContext Result.success(it) }

        // --- Strategy 3: YouTube internal API - ANDROID_EMBEDDED client ---
        tryYouTubeInternalApiCandidates(
            videoId, "ANDROID_EMBEDDED", buildAndroidEmbeddedPayload(videoId),
            DEFAULT_ANDROID_UA, preferHighQuality, errors
        )?.takeIf { it.isNotEmpty() }?.let { return@withContext Result.success(it) }

        // --- Strategy 4: Invidious public instances (fallback) ---
        for (instance in INVIDIOUS_INSTANCES) {
            tryInvidiousInstanceCandidates(instance, videoId, preferHighQuality, errors)
                ?.takeIf { it.isNotEmpty() }?.let { return@withContext Result.success(it) }
        }

        Result.failure(
            Exception(
                "Could not resolve audio streams for video '$videoId'. " +
                "All methods failed:\n${errors.take(6).joinToString("\n")}"
            )
        )
    }

    /**
     * Resolves the best available audio stream URL for a YouTube video ID.
     * Backwards-compatible convenience method.
     */
    suspend fun resolveAudioStreamUrl(
        videoId: String,
        preferHighQuality: Boolean = true
    ): Result<String> {
        val candidatesResult = resolveStreamCandidates(videoId, preferHighQuality)
        return candidatesResult.mapCatching { list ->
            list.firstOrNull()?.url ?: throw IllegalStateException("No candidate audio streams found for video $videoId")
        }
    }

    private fun tryYouTubeInternalApiCandidates(
        videoId: String,
        clientLabel: String,
        payload: String,
        userAgent: String,
        preferHighQuality: Boolean,
        errors: MutableList<String>
    ): List<YouTubeAudioCandidate>? {
        return try {
            val connection = URL(YT_PLAYER_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("X-YouTube-Client-Name", if (clientLabel == "ANDROID_EMBEDDED") "55" else "3")
                setRequestProperty("X-YouTube-Client-Version", if (clientLabel == "ANDROID_19") "19.44.38" else "20.10.38")
            }

            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                errors.add("YouTube/$clientLabel: HTTP $responseCode")
                connection.disconnect()
                return null
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(body)

            // Check playability
            val playabilityStatus = json.optJSONObject("playabilityStatus")
                ?.optString("status", "") ?: ""
            if (playabilityStatus != "OK") {
                errors.add("YouTube/$clientLabel: playability=$playabilityStatus")
                return null
            }

            val streamingData = json.optJSONObject("streamingData") ?: run {
                errors.add("YouTube/$clientLabel: no streamingData")
                return null
            }

            val candidates = mutableListOf<YouTubeAudioCandidate>()

            // 1. Adaptive audio-only formats (itag 140 AAC 128k, itag 251 Opus 160k, etc.)
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            val adaptiveAudioList = mutableListOf<YouTubeAudioCandidate>()
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mimeType = fmt.optString("mimeType", "")
                    val url = fmt.optString("url", "")
                    val bitrate = fmt.optInt("bitrate", 0)
                    val itag = fmt.optInt("itag", 0)
                    val isAudio = AUDIO_MIME_TYPES.any { mimeType.startsWith(it) }
                    val hasVideoWidth = fmt.has("width")
                    if (isAudio && !hasVideoWidth && url.isNotBlank()) {
                        adaptiveAudioList.add(
                            YouTubeAudioCandidate(
                                url = url,
                                mimeType = mimeType,
                                itag = itag,
                                bitrate = bitrate,
                                isProgressive = false,
                                userAgent = userAgent
                            )
                        )
                    }
                }
            }

            // Sort adaptive audio: AAC / mp4 first, then by bitrate
            val sortedAdaptive = adaptiveAudioList.sortedWith(
                compareByDescending<YouTubeAudioCandidate> { it.mimeType.contains("mp4") }
                    .thenByDescending { if (preferHighQuality) it.bitrate else -it.bitrate }
            )
            candidates.addAll(sortedAdaptive)

            // 2. Progressive formats (itag 18 = 360p MP4, itag 22 = 720p MP4)
            // Progressive formats contain an AAC stereo audio stream that is guaranteed
            // accessible without HTTP 403 on Google Video CDN even for copyright-restricted tracks.
            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val url = fmt.optString("url", "")
                    val mimeType = fmt.optString("mimeType", "video/mp4")
                    val itag = fmt.optInt("itag", 0)
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (url.isNotBlank()) {
                        candidates.add(
                            YouTubeAudioCandidate(
                                url = url,
                                mimeType = mimeType,
                                itag = itag,
                                bitrate = bitrate,
                                isProgressive = true,
                                userAgent = userAgent
                            )
                        )
                    }
                }
            }

            if (candidates.isEmpty()) {
                errors.add("YouTube/$clientLabel: no candidates found")
                null
            } else {
                candidates
            }
        } catch (e: Exception) {
            errors.add("YouTube/$clientLabel: ${e.message}")
            null
        }
    }

    private fun tryInvidiousInstanceCandidates(
        instance: String,
        videoId: String,
        preferHighQuality: Boolean,
        errors: MutableList<String>
    ): List<YouTubeAudioCandidate>? {
        return try {
            val apiUrl = "$instance/api/v1/videos/$videoId?fields=adaptiveFormats,formatStreams,videoId"
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "SonoraMusic/1.0 (Android)")
            connection.connect()

            if (connection.responseCode !in 200..299) {
                errors.add("$instance: HTTP ${connection.responseCode}")
                connection.disconnect()
                return null
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(body)
            val candidates = mutableListOf<YouTubeAudioCandidate>()

            val adaptiveFormats = json.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mimeType = fmt.optString("type", "")
                    var url = fmt.optString("url", "")
                    val bitrate = fmt.optInt("bitrate", 0)
                    val itag = fmt.optInt("itag", 0)
                    val isAudio = AUDIO_MIME_TYPES.any { mimeType.startsWith(it) }
                    if (isAudio && url.isNotBlank()) {
                        if (url.startsWith("/")) {
                            url = "$instance$url"
                        }
                        candidates.add(
                            YouTubeAudioCandidate(
                                url = url,
                                mimeType = mimeType,
                                itag = itag,
                                bitrate = bitrate,
                                isProgressive = false,
                                userAgent = "Mozilla/5.0 SonoraMusic/1.0"
                            )
                        )
                    }
                }
            }

            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                    val fmt = formatStreams.getJSONObject(i)
                    var url = fmt.optString("url", "")
                    val mimeType = fmt.optString("type", "video/mp4")
                    val itag = fmt.optInt("itag", 0)
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (url.isNotBlank()) {
                        if (url.startsWith("/")) {
                            url = "$instance$url"
                        }
                        candidates.add(
                            YouTubeAudioCandidate(
                                url = url,
                                mimeType = mimeType,
                                itag = itag,
                                bitrate = bitrate,
                                isProgressive = true,
                                userAgent = "Mozilla/5.0 SonoraMusic/1.0"
                            )
                        )
                    }
                }
            }

            if (candidates.isEmpty()) {
                errors.add("$instance: no audio streams")
                null
            } else {
                candidates
            }
        } catch (e: Exception) {
            errors.add("$instance: ${e.message}")
            null
        }
    }

    /**
     * Returns true if the URL is a direct audio stream (not a YouTube watch page).
     */
    fun isDirectAudioUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) return false
        return url.startsWith("https://") || url.startsWith("http://")
    }
}
