package com.sonora.music.data.importer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.sonora.music.data.converter.FFmpegAudioConverter
import com.sonora.music.data.local.database.SonoraDatabase
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import com.sonora.music.data.local.database.entity.SongEntity
import com.sonora.music.data.model.AudioQuality
import com.sonora.music.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

data class StagedDriveAudioItem(
    val uriString: String,
    val originalFilename: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val formatLabel: String = "MP3",
    val mimeType: String = "audio/mpeg",
    val artworkUriString: String? = null,
    val isDuplicate: Boolean = false,
    val duplicateReason: String? = null,
    val isSelected: Boolean = !isDuplicate
)

data class DriveScanSessionState(
    val isScanning: Boolean = false,
    val scanProgressText: String = "",
    val items: List<StagedDriveAudioItem> = emptyList(),
    val skippedUnsupportedCount: Int = 0,
    val skippedNonAudioCount: Int = 0,
    val skippedReasons: List<String> = emptyList(),
    val emptyMessage: String? = null,
    val showSelectionModal: Boolean = false
)

data class GoogleDriveImportState(
    val isImporting: Boolean = false,
    val currentTitle: String = "",
    val currentFilename: String = "",
    val progress: Int = 0,
    val statusText: String = "",
    val completedCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0,
    val wasCancelled: Boolean = false,
    val lastImportedUri: String? = null,
    val lastImportedTitle: String? = null,
    val lastImportedArtist: String? = null,
    val lastImportedDurationMs: Long = 0L,
    val lastImportedArtworkUri: String? = null,
    val errorMessage: String? = null
)

class GoogleDriveImporter(
    private val context: Context,
    private val database: SonoraDatabase,
    private val ffmpegConverter: FFmpegAudioConverter
) {

    private val _importState = MutableStateFlow(GoogleDriveImportState())
    val importState: StateFlow<GoogleDriveImportState> = _importState.asStateFlow()

    companion object {
        private val DRIVE_FILE_ID_PATTERN = Pattern.compile(
            """(?i)(?:https?://)?(?:drive|docs)\.google\.com/(?:file/d/|open\?id=|uc\?(?:.*&)?id=)([a-zA-Z0-9_-]+)"""
        )
        private val YEAR_EXTRACT_PATTERN = Regex("""\b(19\d{2}|20\d{2})\b""")

        fun isGoogleDriveUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.contains("drive.google.com") || trimmed.contains("docs.google.com")
        }

        fun extractFileId(url: String): String? {
            val matcher = DRIVE_FILE_ID_PATTERN.matcher(url.trim())
            return if (matcher.find()) matcher.group(1) else null
        }
    }

    /**
     * Filters and inspects document URIs returned from Android's audio-filtered file picker.
     * Extracts metadata (title, artist, album, duration, embedded art), removes any non-audio
     * or unsupported files, and flags duplicates already present in Sonora's library.
     */
    suspend fun inspectPickedDocumentUris(
        uris: List<Uri>,
        existingSongs: List<Song>,
        isCancelled: () -> Boolean = { false }
    ): DriveScanSessionState = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext DriveScanSessionState()
        }

        val wmaSupported = SmartDriveAudioHelper.isDeviceWmaDecoderAvailable()
        val stagedItems = mutableListOf<StagedDriveAudioItem>()
        val seenBatchKeys = mutableSetOf<String>()
        val skippedReasons = linkedSetOf<String>()
        var skippedUnsupported = 0
        var skippedNonAudio = 0

        for (uri in uris) {
            currentCoroutineContext().ensureActive()
            if (isCancelled()) {
                return@withContext DriveScanSessionState()
            }

            tryPersistReadGrant(uri)
            val (displayName, sizeBytes, providerMime) = queryDocumentDetails(uri)
            val decision = SmartDriveAudioHelper.classifyDocument(
                displayName = displayName,
                mimeType = providerMime,
                wmaDecoderSupported = wmaSupported
            )

            when (decision) {
                is AudioFilterDecision.NotAudioDocument -> {
                    skippedNonAudio++
                }
                is AudioFilterDecision.UnsupportedFormat -> {
                    skippedUnsupported++
                    skippedReasons.add(decision.reason)
                }
                is AudioFilterDecision.Supported -> {
                    val item = extractStagedItem(
                        uri = uri,
                        originalName = displayName,
                        sizeBytes = sizeBytes,
                        format = decision.format,
                        folderAlbumFallback = null,
                        existingSongs = existingSongs,
                        seenBatchKeys = seenBatchKeys
                    )
                    stagedItems.add(item)
                }
            }
        }

        buildScanSessionResult(
            items = stagedItems,
            skippedUnsupported = skippedUnsupported,
            skippedNonAudio = skippedNonAudio,
            skippedReasons = skippedReasons.toList()
        )
    }

    /**
     * Recursively scans a Google Drive or device folder tree via SAF, filtering out all non-audio
     * documents/images/PDFs so users can discover audio across nested folders in a single click.
     */
    suspend fun scanFolderTreeForAudio(
        treeUri: Uri,
        existingSongs: List<Song>,
        onScanProgress: (scannedFolders: Int, foundAudio: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): DriveScanSessionState = withContext(Dispatchers.IO) {
        tryPersistReadGrant(treeUri)

        val wmaSupported = SmartDriveAudioHelper.isDeviceWmaDecoderAvailable()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, String?>>() // docId to parent folder name
        val visitedFolders = hashSetOf<String>()
        queue.add(rootDocId to null)

        val stagedItems = mutableListOf<StagedDriveAudioItem>()
        val seenBatchKeys = mutableSetOf<String>()
        val skippedReasons = linkedSetOf<String>()
        var scannedFolders = 0
        var skippedUnsupported = 0
        var skippedNonAudio = 0

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (isCancelled()) {
                return@withContext DriveScanSessionState()
            }

            val (folderDocId, folderName) = queue.removeFirst()
            if (!visitedFolders.add(folderDocId)) continue
            scannedFolders++

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        if (isCancelled()) {
                            return@withContext DriveScanSessionState()
                        }

                        val childId = if (idCol != -1) cursor.getString(idCol) ?: continue else continue
                        val childName = if (nameCol != -1) cursor.getString(nameCol) ?: "Audio" else "Audio"
                        val childMime = if (mimeCol != -1) cursor.getString(mimeCol) else null
                        val childSize = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                        if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (visitedFolders.size < 250) {
                                queue.addLast(childId to childName)
                            }
                            continue
                        }

                        val decision = SmartDriveAudioHelper.classifyDocument(
                            displayName = childName,
                            mimeType = childMime,
                            wmaDecoderSupported = wmaSupported
                        )

                        when (decision) {
                            is AudioFilterDecision.NotAudioDocument -> skippedNonAudio++
                            is AudioFilterDecision.UnsupportedFormat -> {
                                skippedUnsupported++
                                skippedReasons.add(decision.reason)
                            }
                            is AudioFilterDecision.Supported -> {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                val staged = extractStagedItem(
                                    uri = docUri,
                                    originalName = childName,
                                    sizeBytes = childSize,
                                    format = decision.format,
                                    folderAlbumFallback = folderName,
                                    existingSongs = existingSongs,
                                    seenBatchKeys = seenBatchKeys
                                )
                                stagedItems.add(staged)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onScanProgress(scannedFolders, stagedItems.size)
        }

        buildScanSessionResult(
            items = stagedItems,
            skippedUnsupported = skippedUnsupported,
            skippedNonAudio = skippedNonAudio,
            skippedReasons = skippedReasons.toList()
        )
    }

    /**
     * Imports user-selected audio files from Drive/SAF into permanent offline storage (Music/Sonora),
     * preserving original filenames, native audio format/quality, metadata, album art, and library indexing.
     */
    suspend fun importStagedItems(
        items: List<StagedDriveAudioItem>,
        existingSongs: List<Song>,
        cancellationFlag: AtomicBoolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        val selected = items.filter { it.isSelected }
        if (selected.isEmpty()) {
            return@withContext Result.success(0)
        }

        val total = selected.size
        val historyDao = database.downloadHistoryDao()
        val songDao = database.songDao()
        val runtimeBatchKeys = mutableSetOf<String>()

        var importedCount = 0
        var skippedDuplicateCount = 0
        var failedCount = 0
        var lastUri: Uri? = null
        var lastTitle: String? = null
        var lastArtist: String? = null
        var lastDurationMs = 0L
        var lastArtUri: String? = null
        var cancelledMidBatch = false

        _importState.value = GoogleDriveImportState(
            isImporting = true,
            currentTitle = selected.first().title,
            currentFilename = selected.first().originalFilename,
            progress = 2,
            statusText = "Preparing import ($total track${if (total == 1) "" else "s"})...",
            totalCount = total
        )

        for ((index, item) in selected.withIndex()) {
            if (cancellationFlag.get()) {
                cancelledMidBatch = true
                break
            }

            val dupCheck = SmartDriveAudioHelper.checkDuplicateSong(
                candidateTitle = item.title,
                candidateArtist = item.artist,
                candidateOriginalFilename = item.originalFilename,
                candidateDurationMs = item.durationMs,
                existingSongs = existingSongs,
                seenBatchKeys = runtimeBatchKeys
            )
            if (item.isDuplicate || dupCheck.isDuplicate) {
                skippedDuplicateCount++
                val pct = (((index + 1).toFloat() / total) * 100).toInt().coerceIn(0, 100)
                _importState.value = _importState.value.copy(
                    skippedDuplicateCount = skippedDuplicateCount,
                    progress = pct,
                    statusText = "Skipped duplicate: ${item.title}"
                )
                continue
            }

            val format = SupportedDriveAudioFormat.entries.firstOrNull {
                it.displayLabel.equals(item.formatLabel, ignoreCase = true)
            } ?: SupportedDriveAudioFormat.MP3

            val preservedFilename = SmartDriveAudioHelper.sanitizePreservedFilename(
                originalName = item.originalFilename,
                format = format,
                fallbackTitle = item.title
            )

            val tempDir = File(context.cacheDir, "drive_saf_imports").apply { mkdirs() }
            val tempFile = File(tempDir, "saf_${System.currentTimeMillis()}_$index.${format.extension}")

            _importState.value = _importState.value.copy(
                currentTitle = item.title,
                currentFilename = preservedFilename,
                progress = ((index.toFloat() / total) * 100).toInt().coerceAtLeast(5),
                statusText = "Importing ${index + 1} of $total: $preservedFilename"
            )

            try {
                val sourceUri = Uri.parse(item.uriString)
                val baseProgress = (index.toFloat() / total) * 100f
                val stepSpan = 100f / total

                context.contentResolver.openInputStream(sourceUri)?.use { inStream ->
                    copyStreamWithCancellation(
                        input = inStream,
                        destination = tempFile,
                        expectedBytes = item.fileSizeBytes,
                        cancellationFlag = cancellationFlag
                    ) { filePct ->
                        val overallPct = (baseProgress + (filePct / 100f) * stepSpan * 0.85f).toInt().coerceIn(0, 99)
                        _importState.value = _importState.value.copy(
                            progress = overallPct
                        )
                    }
                } ?: throw IllegalStateException("Could not read ${item.originalFilename}")

                if (cancellationFlag.get()) {
                    throw CancellationException("Import cancelled by user")
                }

                // Persist embedded artwork to permanent offline storage
                val permanentArtUri = persistArtworkForImportedTrack(
                    previewArtUriString = item.artworkUriString,
                    audioFile = tempFile,
                    stableKey = "drive_${System.currentTimeMillis()}_$index"
                )

                val savedOutput = saveAudioToStorage(
                    sourceFile = tempFile,
                    filename = preservedFilename,
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    year = item.year,
                    trackNumber = item.trackNumber,
                    durationMs = item.durationMs,
                    mimeType = format.canonicalMimeType,
                    destinationTreeUri = null
                )

                val mediaStoreId = try {
                    ContentUris.parseId(savedOutput.uri)
                } catch (_: Exception) {
                    -1L
                }

                // Immediately register song in Room so library, artists, albums, Recently Added, and Offline update instantly
                if (mediaStoreId > 0L) {
                    val nowSec = System.currentTimeMillis() / 1000L
                    songDao.insertSongs(
                        listOf(
                            SongEntity(
                                id = mediaStoreId,
                                title = item.title,
                                artist = item.artist,
                                album = item.album,
                                durationMs = item.durationMs,
                                contentUriString = savedOutput.uri.toString(),
                                albumId = 0L,
                                albumArtUriString = permanentArtUri,
                                trackNumber = item.trackNumber,
                                year = item.year,
                                dateAdded = nowSec,
                                folderName = "Sonora",
                                mimeType = format.canonicalMimeType,
                                customTitle = item.title,
                                customArtist = item.artist,
                                customAlbum = item.album,
                                customYear = item.year.takeIf { it > 0 },
                                customArtworkUriString = permanentArtUri
                            )
                        )
                    )
                }

                historyDao.insertHistory(
                    DownloadHistoryEntity(
                        videoId = "saf_drive_${System.currentTimeMillis()}_$index",
                        title = item.title,
                        artist = item.artist,
                        thumbnailUrl = permanentArtUri,
                        quality = "${format.displayLabel} (Original)",
                        fileSizeBytes = tempFile.length(),
                        status = "COMPLETED",
                        localFilePath = savedOutput.filePath ?: savedOutput.uri.path,
                        mediaStoreUri = savedOutput.uri.toString(),
                        downloadUrl = "Google Drive / Files",
                        timestamp = System.currentTimeMillis()
                    )
                )

                importedCount++
                lastUri = savedOutput.uri
                lastTitle = item.title
                lastArtist = item.artist
                lastDurationMs = item.durationMs
                lastArtUri = permanentArtUri

                val completedPct = (((index + 1).toFloat() / total) * 100).toInt().coerceIn(0, 100)
                _importState.value = _importState.value.copy(
                    completedCount = importedCount,
                    progress = completedPct,
                    lastImportedUri = lastUri?.toString(),
                    lastImportedTitle = lastTitle,
                    lastImportedArtist = lastArtist,
                    lastImportedDurationMs = lastDurationMs,
                    lastImportedArtworkUri = lastArtUri
                )
            } catch (ce: CancellationException) {
                cancelledMidBatch = true
                tempFile.delete()
                break
            } catch (e: Exception) {
                e.printStackTrace()
                failedCount++
                historyDao.insertHistory(
                    DownloadHistoryEntity(
                        videoId = "saf_drive_fail_${System.currentTimeMillis()}_$index",
                        title = item.title,
                        artist = item.artist,
                        quality = item.formatLabel,
                        fileSizeBytes = item.fileSizeBytes,
                        status = "FAILED",
                        downloadUrl = "Google Drive / Files",
                        errorMessage = e.message ?: "Failed to import ${item.originalFilename}",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } finally {
                tempFile.delete()
            }
        }

        val summaryParts = mutableListOf<String>()
        if (importedCount > 0) summaryParts.add("$importedCount imported to library")
        if (skippedDuplicateCount > 0) summaryParts.add("$skippedDuplicateCount duplicate${if (skippedDuplicateCount == 1) "" else "s"} skipped")
        if (failedCount > 0) summaryParts.add("$failedCount failed")

        val finalStatus = when {
            cancelledMidBatch -> if (importedCount > 0) {
                "Import cancelled — $importedCount of $total saved offline"
            } else {
                "Import cancelled"
            }
            summaryParts.isNotEmpty() -> summaryParts.joinToString(" • ")
            else -> "No songs imported"
        }

        val errorMsg = when {
            !cancelledMidBatch && importedCount == 0 && failedCount > 0 ->
                "Failed to import selected audio file(s). Please check file permissions or format compatibility."
            else -> null
        }

        _importState.value = GoogleDriveImportState(
            isImporting = false,
            currentTitle = lastTitle ?: if (cancelledMidBatch) "Import Cancelled" else "Google Drive Import",
            currentFilename = "",
            progress = if (cancelledMidBatch) _importState.value.progress else 100,
            statusText = finalStatus,
            completedCount = importedCount,
            skippedDuplicateCount = skippedDuplicateCount,
            failedCount = failedCount,
            totalCount = total,
            wasCancelled = cancelledMidBatch,
            lastImportedUri = lastUri?.toString(),
            lastImportedTitle = lastTitle,
            lastImportedArtist = lastArtist,
            lastImportedDurationMs = lastDurationMs,
            lastImportedArtworkUri = lastArtUri,
            errorMessage = errorMsg
        )

        Result.success(importedCount)
    }

    /**
     * Imports an audio file directly from a public or shared Google Drive URL link
     * while preserving compatible audio formats and checking for existing duplicates.
     */
    suspend fun importFromDriveUrl(
        url: String,
        quality: AudioQuality = AudioQuality.HIGH_320,
        destinationTreeUri: Uri? = null,
        existingSongs: List<Song> = emptyList()
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val fileId = extractFileId(url)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid Google Drive URL. Please paste a valid drive.google.com/file/d/... link.")
            )

        _importState.value = GoogleDriveImportState(
            isImporting = true,
            currentTitle = "Resolving Google Drive File...",
            progress = 5,
            statusText = "Connecting to Google Drive..."
        )

        val historyDao = database.downloadHistoryDao()
        val tempDir = File(context.cacheDir, "drive_imports").apply { mkdirs() }
        val tempSource = File(tempDir, "drive_source_${fileId}_${System.currentTimeMillis()}")
        val tempMp3 = File(tempDir, "drive_converted_${fileId}_${System.currentTimeMillis()}.mp3")

        var historyId = 0L

        try {
            val (resolvedStreamUrl, resolvedFilename, fileSize) = resolveDriveDownloadInfo(fileId)
            val parsedNames = SmartDriveAudioHelper.parseTitleAndArtistFromFilename(resolvedFilename)
            val wmaSupported = SmartDriveAudioHelper.isDeviceWmaDecoderAvailable()
            val classification = SmartDriveAudioHelper.classifyDocument(resolvedFilename, null, wmaSupported)

            if (classification is AudioFilterDecision.UnsupportedFormat ||
                (classification is AudioFilterDecision.NotAudioDocument && resolvedFilename.contains('.'))
            ) {
                val msg = (classification as? AudioFilterDecision.UnsupportedFormat)?.reason
                    ?: "This Google Drive file ($resolvedFilename) is not a supported audio format."
                throw IllegalArgumentException(msg)
            }

            val parsedTitle = parsedNames.first
            val parsedArtist = parsedNames.second

            val urlDupCheck = SmartDriveAudioHelper.checkDuplicateSong(
                candidateTitle = parsedTitle,
                candidateArtist = parsedArtist,
                candidateOriginalFilename = resolvedFilename,
                candidateDurationMs = 0L,
                existingSongs = existingSongs,
                seenBatchKeys = mutableSetOf()
            )
            if (urlDupCheck.isDuplicate) {
                throw IllegalStateException("\"$parsedTitle\" is already in your Sonora library.")
            }

            _importState.value = _importState.value.copy(
                currentTitle = parsedTitle,
                currentFilename = resolvedFilename,
                progress = 10,
                statusText = "Downloading $resolvedFilename..."
            )

            historyId = historyDao.insertHistory(
                DownloadHistoryEntity(
                    videoId = "drive_$fileId",
                    title = parsedTitle,
                    artist = parsedArtist,
                    thumbnailUrl = null,
                    quality = quality.label,
                    fileSizeBytes = fileSize,
                    status = "DOWNLOADING",
                    downloadUrl = url,
                    timestamp = System.currentTimeMillis()
                )
            )

            downloadDriveStream(resolvedStreamUrl, tempSource, fileSize) { pct ->
                _importState.value = _importState.value.copy(
                    progress = (pct * 0.6f).toInt().coerceAtLeast(10),
                    statusText = "Downloading $resolvedFilename..."
                )
            }

            _importState.value = _importState.value.copy(
                progress = 72,
                statusText = "Reading metadata..."
            )

            var finalTitle = parsedTitle
            var finalArtist = parsedArtist
            var finalAlbum = "Unknown Album"
            var finalYear = 0
            var finalTrack = 0
            var finalDurationMs = 0L
            var artBytes: ByteArray? = null
            var localArtUri: String? = null

            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(tempSource.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotBlank() }?.let {
                    finalTitle = it
                }
                val tagArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                    ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
                if (!tagArtist.isNullOrBlank()) finalArtist = tagArtist
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotBlank() }?.let {
                    finalAlbum = it
                }
                finalDurationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val yearRaw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                if (!yearRaw.isNullOrBlank()) {
                    YEAR_EXTRACT_PATTERN.find(yearRaw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        finalYear = it
                    }
                }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let {
                    finalTrack = it.substringBefore('/').trim().toIntOrNull() ?: 0
                }
                artBytes = mmr.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    val artworkDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "artwork").apply { mkdirs() }
                    val artFile = File(artworkDir, "drive_${fileId}.jpg")
                    artFile.writeBytes(artBytes)
                    localArtUri = Uri.fromFile(artFile).toString()
                }
                mmr.release()
            } catch (_: Exception) {}

            val supportedFormat = (classification as? AudioFilterDecision.Supported)?.format
            val finalAudioFile: File
            val outputFilename: String
            val outputMime: String
            val savedQualityLabel: String

            if (supportedFormat != null) {
                finalAudioFile = tempSource
                outputFilename = SmartDriveAudioHelper.sanitizePreservedFilename(resolvedFilename, supportedFormat, finalTitle)
                outputMime = supportedFormat.canonicalMimeType
                savedQualityLabel = "${supportedFormat.displayLabel} (Original)"
            } else {
                val convertResult = ffmpegConverter.convertToMp3(
                    sourceFile = tempSource,
                    targetMp3File = tempMp3,
                    quality = quality,
                    title = finalTitle,
                    artist = finalArtist,
                    artworkBytes = artBytes,
                    onProgress = { convertPct, _ ->
                        _importState.value = _importState.value.copy(
                            progress = 72 + (convertPct * 0.22f).toInt(),
                            statusText = "Optimizing Audio..."
                        )
                    }
                )
                finalAudioFile = if (convertResult.isSuccess) tempMp3 else tempSource
                outputFilename = SmartDriveAudioHelper.sanitizePreservedFilename(
                    resolvedFilename,
                    SupportedDriveAudioFormat.MP3,
                    "$finalArtist - $finalTitle"
                )
                outputMime = "audio/mpeg"
                savedQualityLabel = quality.label
            }

            _importState.value = _importState.value.copy(
                progress = 95,
                statusText = "Saving to Sonora library..."
            )

            val savedOutput = saveAudioToStorage(
                sourceFile = finalAudioFile,
                filename = outputFilename,
                title = finalTitle,
                artist = finalArtist,
                album = finalAlbum,
                year = finalYear,
                trackNumber = finalTrack,
                durationMs = finalDurationMs,
                mimeType = outputMime,
                destinationTreeUri = destinationTreeUri
            )

            val mediaStoreId = try {
                ContentUris.parseId(savedOutput.uri)
            } catch (_: Exception) {
                -1L
            }
            if (mediaStoreId > 0L && destinationTreeUri == null) {
                database.songDao().insertSongs(
                    listOf(
                        SongEntity(
                            id = mediaStoreId,
                            title = finalTitle,
                            artist = finalArtist,
                            album = finalAlbum,
                            durationMs = finalDurationMs,
                            contentUriString = savedOutput.uri.toString(),
                            albumId = 0L,
                            albumArtUriString = localArtUri,
                            trackNumber = finalTrack,
                            year = finalYear,
                            dateAdded = System.currentTimeMillis() / 1000L,
                            folderName = "Sonora",
                            mimeType = outputMime,
                            customTitle = finalTitle,
                            customArtist = finalArtist,
                            customAlbum = finalAlbum,
                            customYear = finalYear.takeIf { it > 0 },
                            customArtworkUriString = localArtUri
                        )
                    )
                )
            }

            historyDao.updateStatus(
                id = historyId,
                status = "COMPLETED",
                localPath = savedOutput.filePath ?: savedOutput.uri.path,
                mediaStoreUri = savedOutput.uri.toString(),
                fileSize = finalAudioFile.length(),
                errorMessage = null
            )
            if (!localArtUri.isNullOrBlank()) {
                historyDao.updateThumbnail(historyId, localArtUri)
            }

            _importState.value = GoogleDriveImportState(
                isImporting = false,
                currentTitle = finalTitle,
                currentFilename = outputFilename,
                progress = 100,
                statusText = "$savedQualityLabel saved offline to Library",
                completedCount = 1,
                totalCount = 1,
                lastImportedUri = savedOutput.uri.toString(),
                lastImportedTitle = finalTitle,
                lastImportedArtist = finalArtist,
                lastImportedDurationMs = finalDurationMs,
                lastImportedArtworkUri = localArtUri
            )

            Result.success(savedOutput.uri)
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: "Failed to import audio from Google Drive."
            if (historyId > 0) {
                historyDao.updateStatus(
                    id = historyId,
                    status = "FAILED",
                    localPath = null,
                    mediaStoreUri = null,
                    fileSize = 0L,
                    errorMessage = errorMsg
                )
            }
            _importState.value = GoogleDriveImportState(
                isImporting = false,
                errorMessage = errorMsg,
                statusText = "Import Failed"
            )
            Result.failure(e)
        } finally {
            tempSource.delete()
            tempMp3.delete()
        }
    }

    fun resetImportState() {
        _importState.value = GoogleDriveImportState()
    }

    // --- Internal Extraction & Filtering Helpers ---

    private fun extractStagedItem(
        uri: Uri,
        originalName: String,
        sizeBytes: Long,
        format: SupportedDriveAudioFormat,
        folderAlbumFallback: String?,
        existingSongs: List<Song>,
        seenBatchKeys: MutableSet<String>
    ): StagedDriveAudioItem {
        val (parsedTitle, parsedArtist) = SmartDriveAudioHelper.parseTitleAndArtistFromFilename(originalName)
        var title = parsedTitle
        var artist = parsedArtist
        var album = folderAlbumFallback?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        var year = 0
        var trackNumber = 0
        var durationMs = 0L
        var previewArtUriString: String? = null

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                ?.let { title = it }

            val metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
            if (!metaArtist.isNullOrBlank() && !metaArtist.equals("<unknown>", ignoreCase = true)) {
                artist = metaArtist
            }

            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                ?.let { album = it }

            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

            val rawYear = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            if (!rawYear.isNullOrBlank()) {
                YEAR_EXTRACT_PATTERN.find(rawYear)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                    year = it
                }
            }

            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let {
                trackNumber = it.substringBefore('/').trim().toIntOrNull() ?: 0
            }

            val embeddedArt = mmr.embeddedPicture
            if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                val previewDir = File(context.cacheDir, "drive_preview_art").apply { mkdirs() }
                val artHash = uri.toString().hashCode().toUInt().toString(16)
                val artFile = File(previewDir, "art_${artHash}.jpg")
                artFile.writeBytes(embeddedArt)
                previewArtUriString = Uri.fromFile(artFile).toString()
            }
        } catch (_: Exception) {
            // Cloud/streaming SAF files may not always support metadata seek; fall back cleanly to filename metadata
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }

        val dupCheck = SmartDriveAudioHelper.checkDuplicateSong(
            candidateTitle = title,
            candidateArtist = artist,
            candidateOriginalFilename = originalName,
            candidateDurationMs = durationMs,
            existingSongs = existingSongs,
            seenBatchKeys = seenBatchKeys
        )

        return StagedDriveAudioItem(
            uriString = uri.toString(),
            originalFilename = originalName,
            displayName = originalName,
            title = title,
            artist = artist,
            album = album,
            year = year,
            trackNumber = trackNumber,
            durationMs = durationMs,
            fileSizeBytes = sizeBytes,
            formatLabel = format.displayLabel,
            mimeType = format.canonicalMimeType,
            artworkUriString = previewArtUriString,
            isDuplicate = dupCheck.isDuplicate,
            duplicateReason = dupCheck.reason,
            isSelected = !dupCheck.isDuplicate
        )
    }

    private fun buildScanSessionResult(
        items: List<StagedDriveAudioItem>,
        skippedUnsupported: Int,
        skippedNonAudio: Int,
        skippedReasons: List<String>
    ): DriveScanSessionState {
        if (items.isEmpty()) {
            val detail = when {
                skippedReasons.isNotEmpty() -> skippedReasons.first()
                skippedNonAudio > 0 ->
                    "Filtered out $skippedNonAudio non-audio file(s). No supported audio files (MP3, WAV, FLAC, M4A, AAC, OGG, OPUS) were found."
                else -> "No supported audio files found in your selection."
            }
            return DriveScanSessionState(
                isScanning = false,
                items = emptyList(),
                skippedUnsupportedCount = skippedUnsupported,
                skippedNonAudioCount = skippedNonAudio,
                skippedReasons = skippedReasons,
                emptyMessage = detail,
                showSelectionModal = false
            )
        }

        return DriveScanSessionState(
            isScanning = false,
            items = items,
            skippedUnsupportedCount = skippedUnsupported,
            skippedNonAudioCount = skippedNonAudio,
            skippedReasons = skippedReasons,
            emptyMessage = null,
            showSelectionModal = true
        )
    }

    private fun tryPersistReadGrant(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Normal for temporary picker grants
        }
    }

    private fun queryDocumentDetails(uri: Uri): Triple<String, Long, String?> {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Imported Audio"
        var size = 0L
        val mime = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                    }
                }
            }
        } catch (_: Exception) {}
        return Triple(name, size, mime)
    }

    private fun copyStreamWithCancellation(
        input: InputStream,
        destination: File,
        expectedBytes: Long,
        cancellationFlag: AtomicBoolean,
        onProgress: (Int) -> Unit
    ) {
        var totalRead = 0L
        val buffer = ByteArray(32 * 1024)
        FileOutputStream(destination).use { out ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                if (cancellationFlag.get()) {
                    throw CancellationException("Cancelled")
                }
                out.write(buffer, 0, read)
                totalRead += read
                if (expectedBytes > 0L) {
                    val pct = ((totalRead.toDouble() / expectedBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    onProgress(pct)
                }
            }
            out.flush()
        }
    }

    private fun persistArtworkForImportedTrack(
        previewArtUriString: String?,
        audioFile: File,
        stableKey: String
    ): String? {
        val artworkDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "artwork")
            .apply { mkdirs() }
        val targetArtFile = File(artworkDir, "$stableKey.jpg")

        if (!previewArtUriString.isNullOrBlank()) {
            try {
                val previewPath = Uri.parse(previewArtUriString).path
                if (!previewPath.isNullOrBlank()) {
                    val src = File(previewPath)
                    if (src.exists() && src.length() > 0L) {
                        src.copyTo(targetArtFile, overwrite = true)
                        return Uri.fromFile(targetArtFile).toString()
                    }
                }
            } catch (_: Exception) {}
        }

        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(audioFile.absolutePath)
            val bytes = mmr.embeddedPicture
            if (bytes != null && bytes.isNotEmpty()) {
                targetArtFile.writeBytes(bytes)
                Uri.fromFile(targetArtFile).toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }
    }

    private data class SavedAudioOutput(
        val uri: Uri,
        val filePath: String?
    )

    private fun saveAudioToStorage(
        sourceFile: File,
        filename: String,
        title: String,
        artist: String,
        album: String,
        year: Int,
        trackNumber: Int,
        durationMs: Long,
        mimeType: String,
        destinationTreeUri: Uri?
    ): SavedAudioOutput {
        if (destinationTreeUri != null) {
            val docId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(destinationTreeUri, docId)
            val createdUri = DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, filename)
                ?: throw IllegalStateException("Could not create output file in chosen destination folder")
            context.contentResolver.openOutputStream(createdUri)?.use { out ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(out)
                    out.flush()
                }
            }
            return SavedAudioOutput(createdUri, createdUri.path)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_RINGTONE, 0)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
                put(MediaStore.Audio.Media.IS_ALARM, 0)
                if (durationMs > 0L) put(MediaStore.Audio.Media.DURATION, durationMs)
                if (year > 0) put(MediaStore.Audio.Media.YEAR, year)
                if (trackNumber > 0) put(MediaStore.Audio.Media.TRACK, trackNumber)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Sonora")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create MediaStore entry for $filename")

            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(out)
                        out.flush()
                    }
                } ?: throw IllegalStateException("Failed to open output stream for $filename")

                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return SavedAudioOutput(uri, null)
            } catch (e: Exception) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = File(musicDir, "Sonora").apply { mkdirs() }
            val targetFile = createUniqueFile(targetDir, filename)
            FileInputStream(sourceFile).use { inStream ->
                FileOutputStream(targetFile).use { out ->
                    inStream.copyTo(out)
                    out.flush()
                }
            }

            val values = ContentValues().apply {
                @Suppress("DEPRECATION")
                put(MediaStore.Audio.Media.DATA, targetFile.absolutePath)
                put(MediaStore.Audio.Media.DISPLAY_NAME, targetFile.name)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_RINGTONE, 0)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
                put(MediaStore.Audio.Media.IS_ALARM, 0)
                if (durationMs > 0L) put(MediaStore.Audio.Media.DURATION, durationMs)
                if (year > 0) put(MediaStore.Audio.Media.YEAR, year)
                if (trackNumber > 0) put(MediaStore.Audio.Media.TRACK, trackNumber)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: Uri.fromFile(targetFile)
            return SavedAudioOutput(uri, targetFile.absolutePath)
        }
    }

    private fun createUniqueFile(dir: File, desiredName: String): File {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val stem = desiredName.substringBeforeLast('.')
        val ext = desiredName.substringAfterLast('.', "")
        var counter = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotBlank()) "$stem ($counter).$ext" else "$stem ($counter)"
            candidate = File(dir, nextName)
            counter++
        }
        return candidate
    }

    private fun resolveDriveDownloadInfo(fileId: String): Triple<String, String, Long> {
        val initialUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        var conn = (URL(initialUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        conn.connect()

        var resolvedUrl = conn.url.toString()
        var disposition = conn.getHeaderField("Content-Disposition")
        var contentLength = conn.contentLengthLong

        val contentType = conn.contentType ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            val html = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            conn.disconnect()

            val confirmPattern = Pattern.compile("""(?i)confirm=([0-9a-zA-Z_-]+)""")
            val confirmMatcher = confirmPattern.matcher(html)
            if (confirmMatcher.find()) {
                val confirmCode = confirmMatcher.group(1)
                val confirmedUrl = "https://drive.google.com/uc?export=download&confirm=$confirmCode&id=$fileId"
                conn = (URL(confirmedUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                conn.connect()
                resolvedUrl = conn.url.toString()
                disposition = conn.getHeaderField("Content-Disposition")
                contentLength = conn.contentLengthLong
            }
        }

        var filename = "Google_Drive_Audio.mp3"
        if (!disposition.isNullOrBlank()) {
            val filenamePattern = Pattern.compile("""(?i)filename\*?=(?:UTF-8'')?"?([^";]+)"?""")
            val fnMatcher = filenamePattern.matcher(disposition)
            if (fnMatcher.find()) {
                filename = URLDecoder.decode(fnMatcher.group(1).trim(), "UTF-8")
            }
        }

        conn.disconnect()
        return Triple(resolvedUrl, filename, contentLength.coerceAtLeast(0L))
    }

    private fun downloadDriveStream(
        streamUrl: String,
        destinationFile: File,
        expectedBytes: Long,
        onProgress: (Int) -> Unit
    ) {
        val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 25000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        conn.connect()

        val totalBytes = if (expectedBytes > 0) expectedBytes else conn.contentLengthLong
        var totalRead = 0L

        conn.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val pct = ((totalRead.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                        onProgress(pct)
                    }
                }
                output.flush()
            }
        }
        conn.disconnect()
    }
}
