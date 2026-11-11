package com.sonora.music

import com.sonora.music.data.importer.AudioFilterDecision
import com.sonora.music.data.importer.SmartDriveAudioHelper
import com.sonora.music.data.importer.SupportedDriveAudioFormat
import com.sonora.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDriveAudioHelperTest {

    private fun fakeSong(
        id: Long,
        title: String,
        artist: String,
        durationMs: Long,
        originalTitle: String = title
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Test Album",
        durationMs = durationMs,
        contentUriString = "content://media/external/audio/media/$id",
        originalTitle = originalTitle
    )

    @Test
    fun classifyDocument_acceptsAllSupportedAudioFormats() {
        val cases = listOf(
            "song.mp3" to ("audio/mpeg" to SupportedDriveAudioFormat.MP3),
            "master.WAV" to ("audio/wav" to SupportedDriveAudioFormat.WAV),
            "lossless.flac" to ("application/x-flac" to SupportedDriveAudioFormat.FLAC),
            "track.m4a" to ("audio/mp4" to SupportedDriveAudioFormat.M4A),
            "voice.aac" to ("audio/aac" to SupportedDriveAudioFormat.AAC),
            "podcast.ogg" to ("application/ogg" to SupportedDriveAudioFormat.OGG),
            "live.opus" to ("audio/opus" to SupportedDriveAudioFormat.OPUS),
            "classic.wma" to ("audio/x-ms-wma" to SupportedDriveAudioFormat.WMA)
        )

        for ((filename, expected) in cases) {
            val (mime, expectedFmt) = expected
            val decision = SmartDriveAudioHelper.classifyDocument(filename, mime, wmaDecoderSupported = true)
            assertTrue("Expected $filename to be supported, got $decision", decision is AudioFilterDecision.Supported)
            assertEquals(expectedFmt, (decision as AudioFilterDecision.Supported).format)
        }
    }

    @Test
    fun classifyDocument_blocksDocumentsImagesVideosAndDirectories() {
        val blocked = listOf(
            "Statement.pdf" to "application/pdf",
            "AlbumCover.jpg" to "image/jpeg",
            "Artwork.png" to "image/png",
            "Notes.txt" to "text/plain",
            "Contract.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "Concert.mp4" to "video/mp4",
            "Archive.zip" to "application/zip",
            "Subfolder" to "vnd.android.document/directory",
            "Disguised.pdf" to "audio/mpeg"
        )

        for ((filename, mime) in blocked) {
            val decision = SmartDriveAudioHelper.classifyDocument(filename, mime, wmaDecoderSupported = true)
            assertTrue("Expected $filename ($mime) to be filtered out, got $decision", decision is AudioFilterDecision.NotAudioDocument)
        }
    }

    @Test
    fun classifyDocument_gatesWmaByDecoderAndRejectsUnsupportedAudio() {
        val wmaBlocked = SmartDriveAudioHelper.classifyDocument("legacy.wma", "audio/x-ms-wma", wmaDecoderSupported = false)
        assertTrue(wmaBlocked is AudioFilterDecision.UnsupportedFormat)

        val alac = SmartDriveAudioHelper.classifyDocument("studio.alac", "audio/alac", wmaDecoderSupported = true)
        assertTrue(alac is AudioFilterDecision.UnsupportedFormat)
    }

    @Test
    fun classifyDocument_fallsBackToAudioMimeWhenExtensionMissing() {
        val flac = SmartDriveAudioHelper.classifyDocument("Untitled Cloud File", "audio/flac", wmaDecoderSupported = false)
        assertTrue(flac is AudioFilterDecision.Supported)
        assertEquals(SupportedDriveAudioFormat.FLAC, (flac as AudioFilterDecision.Supported).format)

        val ogg = SmartDriveAudioHelper.classifyDocument("Stream Audio", "application/ogg", wmaDecoderSupported = false)
        assertTrue(ogg is AudioFilterDecision.Supported)
        assertEquals(SupportedDriveAudioFormat.OGG, (ogg as AudioFilterDecision.Supported).format)
    }

    @Test
    fun parseTitleAndArtist_handlesTrackNumbersAndMissingArtists() {
        val (title1, artist1) = SmartDriveAudioHelper.parseTitleAndArtistFromFilename("01 - Daft Punk - Instant Crush.flac")
        assertEquals("Instant Crush", title1)
        assertEquals("Daft Punk", artist1)

        val (title2, artist2) = SmartDriveAudioHelper.parseTitleAndArtistFromFilename("04. Ambient Sketch.wav")
        assertEquals("Ambient Sketch", title2)
        assertEquals("Unknown Artist", artist2)
    }

    @Test
    fun sanitizePreservedFilename_keepsOriginalNameAndFormatExtension() {
        val flac = SmartDriveAudioHelper.sanitizePreservedFilename(
            originalName = "Daft Punk: Live / 2007?.flac",
            format = SupportedDriveAudioFormat.FLAC,
            fallbackTitle = "Live 2007"
        )
        assertEquals("Daft Punk_ Live _ 2007_.flac", flac)
        assertTrue(flac.endsWith(".flac"))

        val extensionless = SmartDriveAudioHelper.sanitizePreservedFilename(
            originalName = "Cloud Studio Mix",
            format = SupportedDriveAudioFormat.OPUS,
            fallbackTitle = "Cloud Studio Mix"
        )
        assertEquals("Cloud Studio Mix.opus", extensionless)
    }

    @Test
    fun checkDuplicateSong_detectsLibraryAndBatchDuplicatesWithoutFalsePositives() {
        val library = listOf(
            fakeSong(1L, "Blinding Lights", "The Weeknd", 200_000L),
            fakeSong(2L, "Intro", "The xx", 127_000L)
        )
        val batchKeys = mutableSetOf<String>()

        // Exact library match (copied filename variant)
        val dupCopy = SmartDriveAudioHelper.checkDuplicateSong(
            candidateTitle = "Blinding Lights",
            candidateArtist = "the weeknd",
            candidateOriginalFilename = "Blinding Lights (1).flac",
            candidateDurationMs = 201_000L,
            existingSongs = library,
            seenBatchKeys = batchKeys
        )
        assertTrue(dupCopy.isDuplicate)
        assertNotNull(dupCopy.reason)

        // Totally different song named "Intro" by different artist and duration -> NOT duplicate
        val uniqueIntro = SmartDriveAudioHelper.checkDuplicateSong(
            candidateTitle = "Intro",
            candidateArtist = "M83",
            candidateOriginalFilename = "M83 - Intro.mp3",
            candidateDurationMs = 322_000L,
            existingSongs = library,
            seenBatchKeys = batchKeys
        )
        assertFalse(uniqueIntro.isDuplicate)

        // Second copy of M83 - Intro inside same multi-select batch -> IS duplicate
        val secondCopyInBatch = SmartDriveAudioHelper.checkDuplicateSong(
            candidateTitle = "Intro",
            candidateArtist = "M83",
            candidateOriginalFilename = "M83 - Intro (1).mp3",
            candidateDurationMs = 322_000L,
            existingSongs = library,
            seenBatchKeys = batchKeys
        )
        assertTrue(secondCopyInBatch.isDuplicate)
    }

    @Test
    fun formatHelpers_returnExpectedStrings() {
        assertEquals("3:35", SmartDriveAudioHelper.formatDuration(215_000L))
        assertEquals("--:--", SmartDriveAudioHelper.formatDuration(0L))
        assertEquals("4.5 MB", SmartDriveAudioHelper.formatFileSize(4_718_592L))
        assertEquals("512 KB", SmartDriveAudioHelper.formatFileSize(524_288L))
    }
}

