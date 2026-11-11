package com.sonora.music.data.lyrics

import java.util.regex.Pattern

object LrcParser {

    private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?]")

    /**
     * Parses an LRC formatted string into an ordered list of timestamped LyricLine objects.
     */
    fun parse(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        if (lrcContent.isBlank()) return lines

        val textLines = lrcContent.lines()

        for (rawLine in textLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Skip ID tags like [ar:...], [ti:...], [al:...], [by:...], [offset:...]
            if (line.matches(Regex("^\\[(ar|ti|al|by|offset|length|re|ve):.*]", RegexOption.IGNORE_CASE))) {
                continue
            }

            val matcher = TIMESTAMP_PATTERN.matcher(line)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val minStr = matcher.group(1) ?: "0"
                val secStr = matcher.group(2) ?: "0"
                val fracStr = matcher.group(3) ?: "0"

                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val millis = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val lyricText = if (lastMatchEnd < line.length) {
                    line.substring(lastMatchEnd).trim()
                } else {
                    ""
                }

                for (ts in timestamps) {
                    lines.add(LyricLine(timeMs = ts, text = lyricText))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }
}

