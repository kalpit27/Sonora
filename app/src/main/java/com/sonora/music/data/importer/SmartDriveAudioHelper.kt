package com.sonora.music.data.importer

import android.media.MediaCodecList
import com.sonora.music.data.model.Song
import com.sonora.music.data.repository.LibraryAnalyticsEngine
import java.util.Locale
import kotlin.math.abs

enum class SupportedDriveAudioFormat(
    val displayLabel: String,
    val extension: String,
    val canonicalMimeType: String,
    val aliases: Set<String>
) {
    MP3("MP3", "mp3", "audio/mpeg", setOf("mp3", "mpga")),
    WAV("WAV", "wav", "audio/wav", setOf("wav", "wave")),
    FLAC("FLAC", "flac", "audio/flac", setOf("flac")),
    M4A("M4A", "m4a", "audio/mp4", setOf("m4a", "mp4a")),
    AAC("AAC", "aac", "audio/aac", setOf("aac", "adts")),
    OGG("OGG", "ogg", "audio/ogg", setOf("ogg", "oga")),
    OPUS("OPUS", "opus", "audio/opus", setOf("opus")),
    WMA("WMA", "wma", "audio/x-ms-wma", setOf("wma"))
}

sealed class AudioFilterDecision {
    data class Supported(val format: SupportedDriveAudioFormat) : AudioFilterDecision()
    data class UnsupportedFormat(val reason: String, val extension: String?) : AudioFilterDecision()
    data class NotAudioDocument(val extension: String?) : AudioFilterDecision()
}

data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val reason: String? = null
)

object SmartDriveAudioHelper {

    /**
     * MIME filters passed directly to Android's Storage Access Framework document picker
     * so Google Drive and local storage show only audio files and hide PDFs/images/docs.
     */
    val PICKER_AUDIO_MIME_TYPES: Array<String> = arrayOf(
        "audio/*",
        "application/ogg",
        "application/x-ogg",
        "application/flac",
        "application/x-flac"
    )

    private val NON_AUDIO_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "txt", "rtf", "odt", "csv", "xls", "xlsx", "ppt", "pptx",
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "svg",
        "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
        "zip", "rar", "7z", "tar", "gz", "apk", "aab", "exe", "dmg",
        "html", "htm", "json", "xml", "yaml", "yml", "md", "lrc", "srt", "vtt", "db"
    )

    private val UNSUPPORTED_AUDIO_EXTENSIONS = setOf(
        "aif", "aiff", "alac", "ape", "dsf", "dff", "mid", "midi", "ra", "rm", "amr", "ac3", "dts"
    )

    private val DUPLICATE_SUFFIX_REGEX = Regex("""\s*\(\d+\)\s*$|\s*\[\d+]\s*$|\s*[-_]\s*copy\s*$""", RegexOption.IGNORE_CASE)
    private val TRACK_PREFIX_REGEX = Regex("""^\d{1,3}\s*[.\-_)]\s+""")
    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|]""")

    fun classifyDocument(
        displayName: String,
        mimeType: String?,
        wmaDecoderSupported: Boolean
    ): AudioFilterDecision {
        val cleanName = displayName.trim()
        val ext = if (cleanName.contains('.')) {
            cleanName.substringAfterLast('.', "").trim().lowercase(Locale.ROOT)
        } else {
            ""
        }
        val cleanMime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)

        if (cleanMime == "vnd.android.document/directory") {
            return AudioFilterDecision.NotAudioDocument(null)
        }

        // Block known non-audio document/image/video extensions regardless of cloud MIME quirks
        if (ext in NON_AUDIO_EXTENSIONS) {
            return AudioFilterDecision.NotAudioDocument(ext)
        }

        // Block known non-audio MIME types unless file extension explicitly identifies supported audio
        val formatByExt = findFormatByExtension(ext)
        if (formatByExt == null && cleanMime != null && isExplicitNonAudioMime(cleanMime)) {
            return AudioFilterDecision.NotAudioDocument(ext.ifBlank { null })
        }

        val resolvedFormat = formatByExt ?: findFormatByMime(cleanMime)
        if (resolvedFormat != null) {
            if (resolvedFormat == SupportedDriveAudioFormat.WMA && !wmaDecoderSupported) {
                return AudioFilterDecision.UnsupportedFormat(
                    reason = "Skipped $cleanName (.wma decoder unavailable on this device)",
                    extension = "wma"
                )
            }
            return AudioFilterDecision.Supported(resolvedFormat)
        }

        if (ext in UNSUPPORTED_AUDIO_EXTENSIONS || cleanMime?.startsWith("audio/") == true) {
            val label = ext.uppercase(Locale.ROOT).ifBlank { cleanMime ?: "audio" }
            return AudioFilterDecision.UnsupportedFormat(
                reason = "Unsupported audio format: $label",
                extension = ext.ifBlank { null }
            )
        }

        return AudioFilterDecision.NotAudioDocument(ext.ifBlank { null })
    }

    fun isDeviceWmaDecoderAvailable(): Boolean {
        return try {
            val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            codecs.any { info ->
                !info.isEncoder && info.supportedTypes.any { type ->
                    type.equals("audio/x-ms-wma", ignoreCase = true) ||
                        type.equals("audio/wma", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parses fallback (title, artist) from an audio filename without assigning fake
     * artist strings like "Google Drive" when artist tags are missing.
     */
    fun parseTitleAndArtistFromFilename(filename: String): Pair<String, String> {
        val stem = filename.substringBeforeLast('.').trim()
            .replace(TRACK_PREFIX_REGEX, "")
            .trim()
        if (stem.isBlank()) {
            return Pair("Imported Track", "Unknown Artist")
        }
        return when {
            stem.contains(" - ") -> {
                val parts = stem.split(" - ", limit = 2)
                val artistPart = parts[0].trim().ifBlank { "Unknown Artist" }
                val titlePart = parts[1].trim().ifBlank { stem }
                Pair(titlePart, artistPart)
            }
            stem.contains(" – ") -> {
                val parts = stem.split(" – ", limit = 2)
                val artistPart = parts[0].trim().ifBlank { "Unknown Artist" }
                val titlePart = parts[1].trim().ifBlank { stem }
                Pair(titlePart, artistPart)
            }
            else -> Pair(stem, "Unknown Artist")
        }
    }

    /**
     * Preserves the original filename and format extension while sanitizing OS-illegal characters.
     */
    fun sanitizePreservedFilename(
        originalName: String,
        format: SupportedDriveAudioFormat,
        fallbackTitle: String
    ): String {
        val raw = originalName.trim().ifBlank { "$fallbackTitle.${format.extension}" }
        val sanitized = raw.replace(ILLEGAL_FILENAME_CHARS, "_").trim()
        val ext = sanitized.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val hasMatchingExt = ext in format.aliases || ext == format.extension
        val result = if (hasMatchingExt) sanitized else "${sanitized.substringBeforeLast('.')}.${format.extension}"
        return result.ifBlank { "Sonora_Track.${format.extension}" }
    }

    fun buildDuplicateBatchKey(
        title: String,
        artist: String,
        originalFilename: String,
        durationMs: Long
    ): String {
        val cleanTitle = cleanNormalizedTitle(title)
        val cleanArtist = LibraryAnalyticsEngine.normalizeKey(artist)
        val cleanStem = cleanNormalizedTitle(originalFilename.substringBeforeLast('.'))
        val durationBucket = if (durationMs > 0L) (durationMs / 4500L).toString() else "u"
        return "$cleanTitle|$cleanArtist|$cleanStem|$durationBucket"
    }

    fun checkDuplicateSong(
        candidateTitle: String,
        candidateArtist: String,
        candidateOriginalFilename: String,
        candidateDurationMs: Long,
        existingSongs: List<Song>,
        seenBatchKeys: MutableSet<String>
    ): DuplicateCheckResult {
        val candCleanTitle = cleanNormalizedTitle(candidateTitle)
        val candCleanArtist = LibraryAnalyticsEngine.normalizeKey(candidateArtist)
        val candStem = cleanNormalizedTitle(candidateOriginalFilename.substringBeforeLast('.'))
        val artistKnown = candCleanArtist.isNotBlank() && candCleanArtist != "unknown artist" && candCleanArtist != "<unknown>"

        val batchKey = buildDuplicateBatchKey(candidateTitle, candidateArtist, candidateOriginalFilename, candidateDurationMs)
        if (!seenBatchKeys.add(batchKey)) {
            return DuplicateCheckResult(true, "Duplicate in selection")
        }

        // Check batch key overlaps with exact title+duration match inside same batch
        if (candCleanTitle.isNotBlank() && candidateDurationMs > 0L) {
            val titleDurationKey = "td::$candCleanTitle::${candidateDurationMs / 4500L}"
            if (!seenBatchKeys.add(titleDurationKey)) {
                return DuplicateCheckResult(true, "Duplicate in selection")
            }
        }

        for (existing in existingSongs) {
            val exTitle = cleanNormalizedTitle(existing.title)
            val exOrigTitle = cleanNormalizedTitle(existing.originalTitle)
            val exArtist = LibraryAnalyticsEngine.normalizeKey(existing.artist)
            val durationCompatible = candidateDurationMs <= 0L || existing.durationMs <= 0L ||
                abs(candidateDurationMs - existing.durationMs) <= 4500L

            if (candCleanTitle.isNotBlank() && (candCleanTitle == exTitle || candCleanTitle == exOrigTitle)) {
                if (artistKnown && exArtist == candCleanArtist && durationCompatible) {
                    return DuplicateCheckResult(true, "Already in Library")
                }
                if (candidateDurationMs > 0L && existing.durationMs > 0L && abs(candidateDurationMs - existing.durationMs) <= 4500L) {
                    return DuplicateCheckResult(true, "Already in Library")
                }
            }

            if (candStem.isNotBlank() && (candStem == exTitle || candStem == exOrigTitle) && durationCompatible) {
                if (!artistKnown || exArtist == candCleanArtist || exArtist == "unknown artist") {
                    return DuplicateCheckResult(true, "Same filename already imported")
                }
            }
        }

        return DuplicateCheckResult(false, null)
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSec = durationMs / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    private fun cleanNormalizedTitle(raw: String): String {
        val withoutExtSuffix = DUPLICATE_SUFFIX_REGEX.replace(raw.trim(), "")
        return LibraryAnalyticsEngine.normalizeKey(withoutExtSuffix)
    }

    private fun findFormatByExtension(ext: String): SupportedDriveAudioFormat? {
        if (ext.isBlank()) return null
        return SupportedDriveAudioFormat.entries.firstOrNull { ext in it.aliases }
    }

    private fun findFormatByMime(mime: String?): SupportedDriveAudioFormat? {
        if (mime.isNullOrBlank()) return null
        return when (mime) {
            "audio/mpeg", "audio/mp3", "audio/x-mpeg" -> SupportedDriveAudioFormat.MP3
            "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> SupportedDriveAudioFormat.WAV
            "audio/flac", "audio/x-flac", "application/flac", "application/x-flac" -> SupportedDriveAudioFormat.FLAC
            "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/mp4a-latm" -> SupportedDriveAudioFormat.M4A
            "audio/aac", "audio/aacp", "audio/x-aac", "audio/adts" -> SupportedDriveAudioFormat.AAC
            "audio/ogg", "application/ogg", "application/x-ogg", "audio/vorbis" -> SupportedDriveAudioFormat.OGG
            "audio/opus", "audio/x-opus" -> SupportedDriveAudioFormat.OPUS
            "audio/x-ms-wma", "audio/wma" -> SupportedDriveAudioFormat.WMA
            else -> null
        }
    }

    private fun isExplicitNonAudioMime(mime: String): Boolean {
        if (mime in setOf("application/ogg", "application/x-ogg", "application/flac", "application/x-flac")) {
            return false
        }
        return mime.startsWith("image/") ||
            mime.startsWith("video/") ||
            mime.startsWith("text/") ||
            mime == "application/pdf" ||
            mime == "application/msword" ||
            mime == "application/zip" ||
            mime == "application/x-zip-compressed" ||
            mime == "application/json" ||
            mime.startsWith("application/vnd.")
    }
}

