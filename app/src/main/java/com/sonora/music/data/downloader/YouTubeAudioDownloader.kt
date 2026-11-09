package com.sonora.music.data.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.sonora.music.data.converter.FFmpegAudioConverter
import com.sonora.music.data.importer.YouTubeStreamResolver
import com.sonora.music.data.local.database.SonoraDatabase
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import com.sonora.music.data.local.database.entity.PlaylistEntity
import com.sonora.music.data.local.scanner.MediaScanner
import com.sonora.music.data.model.ActiveDownloadState
import com.sonora.music.data.model.AudioQuality
import com.sonora.music.data.model.DownloadStatus
import com.sonora.music.data.model.QueueDownloadItem
import com.sonora.music.data.model.QueueOverallProgress
import com.sonora.music.data.model.YouTubePlaylistInfo
import com.sonora.music.data.model.YouTubePlaylistItem
import com.sonora.music.data.model.YouTubeVideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class YouTubeAudioDownloader(
    private val context: Context,
    private val database: SonoraDatabase,
    private val streamResolver: YouTubeStreamResolver = YouTubeStreamResolver(),
    private val ffmpegConverter: FFmpegAudioConverter = FFmpegAudioConverter(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadHistoryDao = database.downloadHistoryDao()
    private val mediaScanner = MediaScanner(context)

    // Single Download State
    private val _activeDownload = MutableStateFlow(ActiveDownloadState())
    val activeDownload: StateFlow<ActiveDownloadState> = _activeDownload.asStateFlow()

    // Download Queue States (for Playlist and batch downloads)
    private val _downloadQueue = MutableStateFlow<List<QueueDownloadItem>>(emptyList())
    val downloadQueue: StateFlow<List<QueueDownloadItem>> = _downloadQueue.asStateFlow()

    private val _overallProgress = MutableStateFlow(QueueOverallProgress())
    val overallProgress: StateFlow<QueueOverallProgress> = _overallProgress.asStateFlow()

    val downloadHistory: Flow<List<DownloadHistoryEntity>> = downloadHistoryDao.getAllHistory()

    private var currentDownloadJob: Job? = null
    private var queueJob: Job? = null
    private var pendingMediaStoreUri: Uri? = null
    private var pendingFile: File? = null

    /**
     * Checks if a file or track with the same title already exists locally in Sonora.
     */
    suspend fun isDuplicate(title: String): Boolean = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(title)
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Sonora")
        if (File(publicDir, "$safeTitle.mp3").exists() ||
            File(publicDir, "$safeTitle.m4a").exists() ||
            File(publicDir, "$safeTitle.webm").exists()) return@withContext true

        val historyMatch = downloadHistoryDao.getByTitle(title.trim())
        if (historyMatch != null && historyMatch.status == "COMPLETED") return@withContext true

        false
    }

    /**
     * Starts downloading and converting a single audio track to MP3.
     */
    fun startDownload(
        videoInfo: YouTubeVideoInfo,
        audioSourceUrl: String,
        quality: AudioQuality = AudioQuality.HIGH_320,
        customTitle: String? = null,
        customArtist: String? = null,
        allowDuplicate: Boolean = false,
        destinationTreeUri: Uri? = null,
        onComplete: (uri: Uri) -> Unit = {},
        onError: (message: String) -> Unit = {}
    ) {
        cancelCurrentDownload()

        val finalTitle = (customTitle?.trim()?.ifBlank { null } ?: videoInfo.title).trim()
        val finalArtist = (customArtist?.trim()?.ifBlank { null } ?: videoInfo.channelTitle).trim()

        currentDownloadJob = scope.launch {
            if (!allowDuplicate && isDuplicate(finalTitle)) {
                _activeDownload.value = ActiveDownloadState(
                    title = finalTitle,
                    artist = finalArtist,
                    thumbnailUrl = videoInfo.thumbnailUrl,
                    status = DownloadStatus.IDLE,
                    isDuplicate = true,
                    durationSeconds = videoInfo.durationSeconds
                )
                return@launch
            }

            val historyId = downloadHistoryDao.insertHistory(
                DownloadHistoryEntity(
                    videoId = videoInfo.videoId,
                    title = finalTitle,
                    artist = finalArtist,
                    thumbnailUrl = videoInfo.thumbnailUrl,
                    quality = quality.label,
                    fileSizeBytes = 0L,
                    status = "DOWNLOADING",
                    downloadUrl = audioSourceUrl,
                    timestamp = System.currentTimeMillis()
                )
            )

            _activeDownload.value = ActiveDownloadState(
                downloadId = historyId,
                videoId = videoInfo.videoId,
                title = finalTitle,
                artist = finalArtist,
                thumbnailUrl = videoInfo.thumbnailUrl,
                status = DownloadStatus.DOWNLOADING,
                progress = 0,
                quality = quality,
                isDuplicate = false,
                durationSeconds = videoInfo.durationSeconds,
                stageDescription = "Downloading audio stream..."
            )

            try {
                val downloadedUri = downloadAndConvertStream(
                    audioSourceUrl = audioSourceUrl,
                    videoId = videoInfo.videoId,
                    title = finalTitle,
                    artist = finalArtist,
                    quality = quality,
                    destinationTreeUri = destinationTreeUri,
                    onProgressUpdate = { progress, downloaded, total, stage ->
                        val currentStatus = if (progress >= 50 && progress < 95) DownloadStatus.CONVERTING else DownloadStatus.DOWNLOADING
                        _activeDownload.value = _activeDownload.value.copy(
                            status = currentStatus,
                            progress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            stageDescription = stage
                        )
                    }
                )

                _activeDownload.value = _activeDownload.value.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    localUri = downloadedUri.toString(),
                    stageDescription = "Saved locally as MP3"
                )

                downloadHistoryDao.updateStatus(
                    id = historyId,
                    status = "COMPLETED",
                    localPath = downloadedUri.path,
                    mediaStoreUri = downloadedUri.toString(),
                    fileSize = _activeDownload.value.downloadedBytes,
                    errorMessage = null
                )

                withContext(Dispatchers.Main) {
                    onComplete(downloadedUri)
                }
            } catch (e: CancellationException) {
                cleanupPendingFiles()
                _activeDownload.value = _activeDownload.value.copy(
                    status = DownloadStatus.CANCELLED,
                    errorMessage = "Download was cancelled.",
                    stageDescription = "Cancelled"
                )
                downloadHistoryDao.updateStatus(
                    id = historyId,
                    status = "CANCELLED",
                    localPath = null,
                    mediaStoreUri = null,
                    fileSize = 0L,
                    errorMessage = "Cancelled by user"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupPendingFiles()
                val errorMsg = e.message ?: "Download failed. Please check network connection."
                _activeDownload.value = _activeDownload.value.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = errorMsg,
                    stageDescription = "Failed"
                )
                downloadHistoryDao.updateStatus(
                    id = historyId,
                    status = "FAILED",
                    localPath = null,
                    mediaStoreUri = null,
                    fileSize = 0L,
                    errorMessage = errorMsg
                )
                withContext(Dispatchers.Main) {
                    onError(errorMsg)
                }
            }
        }
    }

    /**
     * Enqueues and downloads all selected tracks from a YouTube Playlist.
     * Converts every track to MP3, tracks overall progress, creates a dedicated
     * Sonora playlist in Room, and indexes all tracks into the local music library.
     */
    fun startPlaylistDownload(
        playlistInfo: YouTubePlaylistInfo,
        selectedTracks: List<YouTubePlaylistItem>,
        quality: AudioQuality = AudioQuality.HIGH_320,
        allowDuplicates: Boolean = false,
        onPlaylistImported: (playlistId: Long) -> Unit = {}
    ) {
        cancelQueue()
        if (selectedTracks.isEmpty()) return

        val queueItems = selectedTracks.map { track ->
            QueueDownloadItem(
                id = track.videoId,
                videoId = track.videoId,
                title = track.title,
                artist = track.channelTitle,
                thumbnailUrl = track.thumbnailUrl,
                downloadUrl = track.originalUrl,
                quality = quality,
                status = DownloadStatus.PENDING
            )
        }

        _downloadQueue.value = queueItems
        _overallProgress.value = QueueOverallProgress(
            totalTracks = queueItems.size,
            completedTracks = 0,
            failedTracks = 0,
            currentTrackIndex = 0,
            currentTrackTitle = queueItems.first().title,
            overallPercentage = 0,
            isQueueActive = true,
            targetPlaylistName = playlistInfo.title
        )

        queueJob = scope.launch {
            val downloadedTitles = mutableListOf<String>()

            for (index in queueItems.indices) {
                ensureActive()

                val item = _downloadQueue.value[index]
                if (item.status == DownloadStatus.CANCELLED) {
                    continue
                }

                // Check duplicate
                val isDupe = !allowDuplicates && isDuplicate(item.title)
                if (isDupe) {
                    _downloadQueue.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(status = DownloadStatus.COMPLETED, progress = 100, isDuplicate = true)
                            else itm
                        }
                    }
                    downloadedTitles.add(item.title)
                    updateOverallProgress(index + 1, queueItems.size, playlistInfo.title)
                    continue
                }

                // Set current item downloading
                _downloadQueue.update { list ->
                    list.mapIndexed { idx, itm ->
                        if (idx == index) itm.copy(status = DownloadStatus.DOWNLOADING, progress = 0)
                        else itm
                    }
                }

                _overallProgress.update {
                    it.copy(
                        currentTrackIndex = index + 1,
                        currentTrackTitle = item.title
                    )
                }

                val historyId = downloadHistoryDao.insertHistory(
                    DownloadHistoryEntity(
                        videoId = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        thumbnailUrl = item.thumbnailUrl,
                        quality = quality.label,
                        status = "DOWNLOADING",
                        downloadUrl = item.downloadUrl,
                        timestamp = System.currentTimeMillis()
                    )
                )

                try {
                    // Resolve real audio stream URL if item.downloadUrl is a YouTube watch page
                    val audioStreamUrl = if (!streamResolver.isDirectAudioUrl(item.downloadUrl)) {
                        val resolveResult = streamResolver.resolveAudioStreamUrl(item.videoId)
                        if (resolveResult.isFailure) {
                            throw Exception("Stream resolve failed: ${resolveResult.exceptionOrNull()?.message}")
                        }
                        resolveResult.getOrThrow()
                    } else {
                        item.downloadUrl
                    }

                    val downloadedUri = downloadAndConvertStream(
                        audioSourceUrl = audioStreamUrl,
                        videoId = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        quality = quality,
                        destinationTreeUri = null,
                        onProgressUpdate = { progress, downloaded, total, _ ->
                            _downloadQueue.update { list ->
                                list.mapIndexed { idx, itm ->
                                    if (idx == index) itm.copy(
                                        progress = progress,
                                        downloadedBytes = downloaded,
                                        totalBytes = total
                                    ) else itm
                                }
                            }
                        }
                    )

                    _downloadQueue.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 100,
                                localUri = downloadedUri.toString()
                            ) else itm
                        }
                    }

                    downloadHistoryDao.updateStatus(
                        id = historyId,
                        status = "COMPLETED",
                        localPath = downloadedUri.path,
                        mediaStoreUri = downloadedUri.toString(),
                        fileSize = _downloadQueue.value[index].downloadedBytes,
                        errorMessage = null
                    )

                    downloadedTitles.add(item.title)
                } catch (e: CancellationException) {
                    cleanupPendingFiles()
                    _downloadQueue.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(status = DownloadStatus.CANCELLED, errorMessage = "Cancelled")
                            else itm
                        }
                    }
                    downloadHistoryDao.updateStatus(historyId, "CANCELLED", null, null, 0L, "Cancelled by user")
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    cleanupPendingFiles()
                    val errorMsg = e.message ?: "Failed"
                    _downloadQueue.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg)
                            else itm
                        }
                    }
                    downloadHistoryDao.updateStatus(historyId, "FAILED", null, null, 0L, errorMsg)
                }

                updateOverallProgress(index + 1, queueItems.size, playlistInfo.title)
            }

            // Sync with MediaStore & Room library
            delay(1200)
            val scannedSongs = mediaScanner.scanLocalAudio()
            database.songDao().syncMediaStoreSongs(scannedSongs)

            // Organize imported tracks under Sonora Playlist
            val playlistId = createSonoraPlaylistForImport(playlistInfo.title, downloadedTitles, scannedSongs)

            _overallProgress.update {
                it.copy(
                    isQueueActive = false,
                    targetPlaylistId = playlistId
                )
            }

            withContext(Dispatchers.Main) {
                onPlaylistImported(playlistId)
            }
        }
    }

    private suspend fun createSonoraPlaylistForImport(
        playlistTitle: String,
        downloadedTitles: List<String>,
        scannedSongs: List<com.sonora.music.data.local.database.entity.SongEntity>
    ): Long = withContext(Dispatchers.IO) {
        val playlistId = database.playlistDao().insertPlaylist(
            PlaylistEntity(
                name = playlistTitle.ifBlank { "YouTube Import" },
                description = "Imported from YouTube Playlist • ${downloadedTitles.size} tracks",
                createdAt = System.currentTimeMillis()
            )
        )

        // Find matching song IDs in the library
        val matchingSongIds = mutableListOf<Long>()
        for (title in downloadedTitles) {
            val match = scannedSongs.find {
                it.title.equals(title, ignoreCase = true) || it.title.contains(title, ignoreCase = true)
            }
            if (match != null && !matchingSongIds.contains(match.id)) {
                matchingSongIds.add(match.id)
            }
        }

        if (matchingSongIds.isNotEmpty()) {
            database.playlistDao().addSongsToPlaylist(playlistId, matchingSongIds)
        }

        playlistId
    }

    private fun updateOverallProgress(processedCount: Int, total: Int, playlistName: String) {
        val completed = _downloadQueue.value.count { it.status == DownloadStatus.COMPLETED }
        val failed = _downloadQueue.value.count { it.status == DownloadStatus.FAILED }
        val percentage = if (total > 0) ((processedCount.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100) else 100

        _overallProgress.update {
            it.copy(
                completedTracks = completed,
                failedTracks = failed,
                overallPercentage = percentage,
                targetPlaylistName = playlistName
            )
        }
    }

    /**
     * Cancels the entire active queue and active download.
     */
    fun cancelQueue() {
        queueJob?.cancel()
        queueJob = null
        cancelCurrentDownload()
        _downloadQueue.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PENDING) {
                    item.copy(status = DownloadStatus.CANCELLED, errorMessage = "Cancelled by user")
                } else item
            }
        }
        _overallProgress.update { it.copy(isQueueActive = false) }
    }

    /**
     * Cancels an individual item in the queue.
     */
    fun cancelQueueItem(id: String) {
        val item = _downloadQueue.value.find { it.id == id }
        if (item?.status == DownloadStatus.DOWNLOADING) {
            currentDownloadJob?.cancel()
        }
        _downloadQueue.update { list ->
            list.map {
                if (it.id == id) it.copy(status = DownloadStatus.CANCELLED, errorMessage = "Cancelled")
                else it
            }
        }
    }

    /**
     * Retries an individual failed track in the queue.
     */
    fun retryQueueItem(id: String) {
        val item = _downloadQueue.value.find { it.id == id } ?: return
        scope.launch {
            _downloadQueue.update { list ->
                list.map {
                    if (it.id == id) it.copy(status = DownloadStatus.DOWNLOADING, progress = 0, errorMessage = null)
                    else it
                }
            }

            try {
                val downloadedUri = downloadAndConvertStream(
                    audioSourceUrl = item.downloadUrl,
                    videoId = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    quality = item.quality,
                    destinationTreeUri = null,
                    onProgressUpdate = { progress, downloaded, total, _ ->
                        _downloadQueue.update { list ->
                            list.map {
                                if (it.id == id) it.copy(progress = progress, downloadedBytes = downloaded, totalBytes = total)
                                else it
                            }
                        }
                    }
                )

                _downloadQueue.update { list ->
                    list.map {
                        if (it.id == id) it.copy(status = DownloadStatus.COMPLETED, progress = 100, localUri = downloadedUri.toString())
                        else it
                    }
                }
            } catch (e: Exception) {
                _downloadQueue.update { list ->
                    list.map {
                        if (it.id == id) it.copy(status = DownloadStatus.FAILED, errorMessage = e.message ?: "Failed")
                        else it
                    }
                }
            }
        }
    }

    /**
     * Retries all failed tracks in the queue.
     */
    fun retryFailedQueueItems() {
        val failedItems = _downloadQueue.value.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        if (failedItems.isEmpty()) return

        scope.launch {
            for (failed in failedItems) {
                retryQueueItem(failed.id)
            }
        }
    }

    fun clearQueue() {
        _downloadQueue.value = emptyList()
        _overallProgress.value = QueueOverallProgress()
    }

    fun cancelCurrentDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        cleanupPendingFiles()
        if (_activeDownload.value.status == DownloadStatus.DOWNLOADING || _activeDownload.value.status == DownloadStatus.CONVERTING) {
            _activeDownload.value = _activeDownload.value.copy(
                status = DownloadStatus.CANCELLED,
                errorMessage = "Download cancelled by user.",
                stageDescription = "Cancelled"
            )
        }
    }

    fun resetActiveDownload() {
        _activeDownload.value = ActiveDownloadState()
    }

    fun retryDownload(
        historyItem: DownloadHistoryEntity,
        destinationTreeUri: Uri? = null,
        onComplete: (uri: Uri) -> Unit = {},
        onError: (message: String) -> Unit = {}
    ) {
        val videoInfo = YouTubeVideoInfo(
            videoId = historyItem.videoId ?: "",
            originalUrl = "https://www.youtube.com/watch?v=${historyItem.videoId ?: ""}",
            title = historyItem.title,
            channelTitle = historyItem.artist,
            thumbnailUrl = historyItem.thumbnailUrl ?: ""
        )
        val url = historyItem.downloadUrl.orEmpty()
        startDownload(
            videoInfo = videoInfo,
            audioSourceUrl = url,
            allowDuplicate = true,
            destinationTreeUri = destinationTreeUri,
            onComplete = onComplete,
            onError = onError
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        downloadHistoryDao.clearAll()
    }

    suspend fun deleteHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        downloadHistoryDao.deleteById(id)
    }

    /**
     * Full Audio Processing Workflow:
     * 1. Download the supported source audio stream into a temporary working file.
     * 2. Use FFmpegAudioConverter to convert the audio into standard MP3 format.
     * 3. Write the resulting MP3 file to local storage (MediaStore or user-selected SAF folder).
     * 4. Invalidate and clean up temporary server/cache files.
     * 5. Trigger immediate Android MediaStore indexing.
     */
    private suspend fun downloadAndConvertStream(
        audioSourceUrl: String,
        videoId: String? = null,
        title: String,
        artist: String,
        quality: AudioQuality,
        destinationTreeUri: Uri? = null,
        onProgressUpdate: (progress: Int, downloaded: Long, total: Long, stage: String) -> Unit = { _, _, _, _ -> }
    ): Uri = withContext(Dispatchers.IO) {
        if (audioSourceUrl.contains("youtube.com/watch") || audioSourceUrl.contains("youtu.be/")) {
            throw IllegalStateException("Cannot download YouTube watch page directly as audio. Audio stream must be resolved first.")
        }

        onProgressUpdate(5, 0L, 0L, "Connecting to audio stream...")

        // Build candidate list of streams to try
        val candidatesToTry = mutableListOf<com.sonora.music.data.importer.YouTubeAudioCandidate>()

        if (!videoId.isNullOrBlank()) {
            val resolvedList = streamResolver.resolveStreamCandidates(videoId, preferHighQuality = true).getOrNull()
            if (!resolvedList.isNullOrEmpty()) {
                candidatesToTry.addAll(resolvedList)
            }
        }

        // Add the provided audioSourceUrl if direct and not already present
        if (audioSourceUrl.isNotBlank() && streamResolver.isDirectAudioUrl(audioSourceUrl) && candidatesToTry.none { it.url == audioSourceUrl }) {
            candidatesToTry.add(
                0,
                com.sonora.music.data.importer.YouTubeAudioCandidate(
                    url = audioSourceUrl,
                    mimeType = if (audioSourceUrl.contains("webm")) "audio/webm" else "audio/mp4",
                    userAgent = com.sonora.music.data.importer.YouTubeStreamResolver.DEFAULT_ANDROID_UA
                )
            )
        }

        if (candidatesToTry.isEmpty()) {
            throw IllegalStateException("No accessible audio stream could be resolved for this track.")
        }

        var tempSourceFile: File? = null
        val tempMp3File = File(context.cacheDir, "sonora_mp3_${System.currentTimeMillis()}.mp3")
        var chosenCandidate: com.sonora.music.data.importer.YouTubeAudioCandidate? = null
        var lastDownloadError: Exception? = null

        // Try downloading candidates in order:
        // High quality adaptive audio first; if 403 occurs on restricted videos,
        // seamlessly fall back to progressive AAC format (itag 18) which never 403s.
        for ((idx, candidate) in candidatesToTry.withIndex()) {
            try {
                val stageMsg = if (idx == 0) "Connecting to audio stream..." else "Retrying stream (source #${idx + 1})..."
                onProgressUpdate(5 + (idx * 2).coerceAtMost(10), 0L, 0L, stageMsg)

                val url = URL(candidate.url)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", candidate.userAgent)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Connection", "keep-alive")
                }
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    conn.disconnect()
                    lastDownloadError = IllegalStateException("Audio source returned HTTP $responseCode")
                    continue
                }

                val contentType = conn.contentType ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    conn.disconnect()
                    lastDownloadError = IllegalStateException("Server returned an HTML page instead of audio data.")
                    continue
                }

                val ext = when {
                    candidate.isProgressive -> "mp4"
                    candidate.mimeType.contains("webm", ignoreCase = true) || candidate.url.contains("audio/webm") || candidate.url.contains("audio%2Fwebm") -> "webm"
                    candidate.mimeType.contains("mp4", ignoreCase = true) || candidate.url.contains("audio/mp4") || candidate.url.contains("audio%2Fmp4") -> "m4a"
                    else -> "mp4"
                }

                val sourceFile = File(context.cacheDir, "sonora_source_${System.currentTimeMillis()}.$ext")
                tempSourceFile = sourceFile
                pendingFile = sourceFile

                val totalBytes = conn.contentLengthLong.let { if (it > 0) it else quality.estimateSizeBytes(210L) }
                val inStream = conn.inputStream
                val outStream = FileOutputStream(sourceFile)

                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalDownloaded = 0L
                var lastUpdateTimestamp = 0L

                try {
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        kotlinx.coroutines.currentCoroutineContext()[Job]?.ensureActive()

                        outStream.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTimestamp >= 100) {
                            lastUpdateTimestamp = now
                            val downloadPct = if (totalBytes > 0) {
                                ((totalDownloaded.toDouble() / totalBytes.toDouble()) * 50).toInt().coerceIn(0, 50)
                            } else 25

                            onProgressUpdate(downloadPct, totalDownloaded, totalBytes, "Downloading audio stream ($downloadPct%)...")
                        }
                    }
                    outStream.flush()
                } finally {
                    try { outStream.close() } catch (ignored: Exception) {}
                    try { inStream.close() } catch (ignored: Exception) {}
                    conn.disconnect()
                }

                if (sourceFile.exists() && sourceFile.length() > 0) {
                    chosenCandidate = candidate
                    break // Successfully downloaded!
                } else {
                    sourceFile.delete()
                    tempSourceFile = null
                    lastDownloadError = IllegalStateException("Downloaded audio stream was empty.")
                }
            } catch (e: CancellationException) {
                tempSourceFile?.delete()
                tempMp3File.delete()
                pendingFile = null
                throw e
            } catch (e: Exception) {
                tempSourceFile?.delete()
                tempSourceFile = null
                lastDownloadError = e
            }
        }

        if (chosenCandidate == null || tempSourceFile == null || !tempSourceFile.exists() || tempSourceFile.length() == 0L) {
            tempSourceFile?.delete()
            tempMp3File.delete()
            pendingFile = null
            throw lastDownloadError ?: IllegalStateException("Failed to download audio from all available stream sources.")
        }

        // Stage 2: Convert supported source audio to MP3 using FFmpeg (50% to 90% overall progress)
        onProgressUpdate(52, tempSourceFile.length(), tempSourceFile.length(), "Converting audio to MP3 format (FFmpeg)...")
        val convertResult = ffmpegConverter.convertToMp3(
            sourceFile = tempSourceFile,
            targetMp3File = tempMp3File,
            quality = quality,
            title = title,
            artist = artist,
            onProgress = { convertPct, stage ->
                val overallPct = 50 + (convertPct * 0.4).toInt()
                onProgressUpdate(overallPct, tempSourceFile.length(), tempSourceFile.length(), "Converting to MP3: $stage")
            }
        )

        if (convertResult.isFailure) {
            tempSourceFile.delete()
            tempMp3File.delete()
            throw convertResult.exceptionOrNull() ?: Exception("Audio format conversion to MP3 failed")
        }

        // Stage 3: Save converted MP3 to destination folder with meaningful filename (90% to 100%)
        onProgressUpdate(92, tempMp3File.length(), tempMp3File.length(), "Saving MP3 to local storage...")
        val meaningfulFilename = "${generateMeaningfulFilename(title, artist)}.mp3"
        val (outputStream, outputUri) = createAudioOutput(meaningfulFilename, title, artist, destinationTreeUri)
        pendingMediaStoreUri = outputUri

        try {
            FileInputStream(tempMp3File).use { inStream ->
                inStream.copyTo(outputStream)
                outputStream.flush()
            }
            onProgressUpdate(100, tempMp3File.length(), tempMp3File.length(), "Complete!")
        } finally {
            try { outputStream.close() } catch (ignored: Exception) {}
            // Clean up temporary server/cache files after processing
            tempSourceFile.delete()
            tempMp3File.delete()
            pendingFile = null
        }

        // Finalize MediaStore audio if using standard Android MediaStore
        if (destinationTreeUri == null) {
            finalizeMediaStoreAudio(outputUri)
        }
        pendingMediaStoreUri = null

        // Trigger Android MediaScanner to immediately index the file into library
        val physicalPath = getFilePathFromUri(outputUri)
        if (physicalPath != null) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(physicalPath),
                arrayOf("audio/mpeg")
            ) { _, _ -> }
        }

        outputUri
    }

    /**
     * Creates an output stream and URI for saving the MP3 file,
     * supporting either user-selected SAF destination folder or default MediaStore Music/Sonora.
     */
    private fun createAudioOutput(
        fileName: String,
        title: String,
        artist: String,
        destinationTreeUri: Uri?
    ): Pair<OutputStream, Uri> {
        if (destinationTreeUri != null) {
            val docId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(destinationTreeUri, docId)
            val createdUri = DocumentsContract.createDocument(context.contentResolver, parentUri, "audio/mpeg", fileName)
                ?: throw IllegalStateException("Could not create MP3 file in selected destination folder")
            val stream = context.contentResolver.openOutputStream(createdUri)
                ?: throw IllegalStateException("Could not open output stream for destination file")
            return Pair(stream, createdUri)
        } else {
            return createMediaStoreAudioOutput(fileName, title, artist, "audio/mpeg")
        }
    }

    private fun createMediaStoreAudioOutput(
        fileName: String,
        title: String,
        artist: String,
        mimeType: String = "audio/mpeg"
    ): Pair<OutputStream, Uri> {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, "Sonora Imports")
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Sonora")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry for audio file")

            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open output stream for MediaStore URI")

            return Pair(stream, uri)
        } else {
            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Sonora")
            if (!musicDir.exists()) {
                musicDir.mkdirs()
            }
            val targetFile = File(musicDir, fileName)
            pendingFile = targetFile
            val stream = FileOutputStream(targetFile)
            val uri = Uri.fromFile(targetFile)
            return Pair(stream, uri)
        }
    }

    private fun finalizeMediaStoreAudio(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
        }
    }

    private fun cleanupPendingFiles() {
        pendingMediaStoreUri?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (ignored: Exception) {}
            pendingMediaStoreUri = null
        }
        pendingFile?.let { file ->
            try {
                if (file.exists()) file.delete()
            } catch (ignored: Exception) {}
            pendingFile = null
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            if (uri.scheme == "file") return uri.path
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateMeaningfulFilename(title: String, artist: String? = null): String {
        val cleanTitle = sanitizeFilename(title)
        val cleanArtist = artist?.let { sanitizeFilename(it) }?.takeIf { it.isNotBlank() }
        return if (cleanArtist != null && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
            "$cleanArtist - $cleanTitle"
        } else {
            cleanTitle
        }.ifBlank { "Sonora_Track_${System.currentTimeMillis()}" }
    }

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }
}
