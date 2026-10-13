package com.sonora.music.data.importer

import com.sonora.music.data.model.ImportedPlaylistResult
import com.sonora.music.data.model.ImportedTrack
import com.sonora.music.data.model.MatchedPlaylistSummary
import com.sonora.music.data.model.Song

class LocalTrackMatcher {

    fun match(
        imported: ImportedPlaylistResult,
        localSongs: List<Song>
    ): MatchedPlaylistSummary {
        val matched = mutableListOf<Song>()
        val missing = mutableListOf<ImportedTrack>()
        val alreadyMatchedIds = mutableSetOf<Long>()

        for (track in imported.tracks) {
            val bestMatch = findBestMatch(track, localSongs, alreadyMatchedIds)
            if (bestMatch != null) {
                matched.add(bestMatch)
                alreadyMatchedIds.add(bestMatch.id)
            } else {
                missing.add(track)
            }
        }

        return MatchedPlaylistSummary(
            playlistName = imported.playlistName,
            description = imported.description,
            source = imported.source,
            matchedSongs = matched,
            missingTracks = missing
        )
    }

    private fun findBestMatch(
        track: ImportedTrack,
        localSongs: List<Song>,
        alreadyMatchedIds: Set<Long>
    ): Song? {
        val targetTitle = normalize(track.title)
        val targetArtist = normalize(track.artist)

        if (targetTitle.isBlank()) return null

        // 1. Direct normalized match
        val exact = localSongs.find { s ->
            !alreadyMatchedIds.contains(s.id) &&
                    normalize(s.title) == targetTitle &&
                    (targetArtist.isBlank() || normalize(s.artist).contains(targetArtist) || targetArtist.contains(normalize(s.artist)))
        }
        if (exact != null) return exact

        // 2. Contains match (e.g. "Starboy" in "Starboy (Feat. Daft Punk)")
        val containsMatch = localSongs.find { s ->
            if (alreadyMatchedIds.contains(s.id)) return@find false
            val localTitle = normalize(s.title)
            (localTitle.contains(targetTitle) || targetTitle.contains(localTitle)) &&
                    (targetArtist.isBlank() || normalize(s.artist).contains(targetArtist) || targetArtist.contains(normalize(s.artist)))
        }
        if (containsMatch != null) return containsMatch

        // 3. Token-based overlap
        val targetTokens = tokenize(track.title)
        return localSongs.find { s ->
            if (alreadyMatchedIds.contains(s.id)) return@find false
            val localTokens = tokenize(s.title)
            val common = targetTokens.intersect(localTokens)
            val overlap = common.size.toFloat() / targetTokens.size.coerceAtLeast(1).toFloat()
            overlap >= 0.7f
        }
    }

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace(Regex("""[^a-z0-9]"""), "")
            .trim()
    }

    private fun tokenize(str: String): Set<String> {
        return str.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length > 2 }
            .toSet()
    }
}
