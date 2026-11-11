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
    val ffmpegConverter: FFmpegAudioConverter = FFmpegAudioConverter(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadHistoryDao = database.downloadHistoryDao()
    private val downloadQueueDao = database.downloadQueueDao()
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

    init {
        // Restore existing queue from database upon app launch or process restart
        scope.launch {
            try {
                val savedQueue = downloadQueueDao.getQueueList()
                if (savedQueue.isNotEmpty()) {
                    val domainItems = savedQueue.map { it.toQueueDownloadItem() }
                    _downloadQueue.value = domainItems
                    val completed = domainItems.count { it.status == DownloadStatus.COMPLETED }
                    val failed = domainItems.count { it.status == DownloadStatus.FAILED }
                    val total = domainItems.size
                    val pendingIndex = domainItems.indexOfFirst {
                        it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING
                    }
                    val currentTitle = if (pendingIndex >= 0) domainItems[pendingIndex].title else ""
                    val pct = if (total > 0) ((completed.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100) else 100
                    val playlistName = savedQueue.firstOrNull()?.playlistTitle ?: "Imported Playlist"

                    _overallProgress.value = QueueOverallProgress(
                        totalTracks = total,
                        completedTracks = completed,
                        failedTracks = failed,
                        currentTrackIndex = if (pendingIndex >= 0) pendingIndex + 1 else total,
                        currentTrackTitle = currentTitle,
                        overallPercentage = pct,
                        isQueueActive = pendingIndex >= 0,
                        targetPlaylistName = playlistName
                    )

                    // Resume downloading in background if there were pending tracks left
                    if (pendingIndex >= 0) {
                        startBackgroundQueueProcessing(playlistName, allowDuplicates = false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
        // Cancel any active previous job directly without sending stop/cancel signals to service
        currentDownloadJob?.cancel()
        currentDownloadJob = null

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

            // Elevate to Foreground Service so download continues when minimized or closed
            SonoraDownloadService.start(context)

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
                stageDescription = "Downloading..."
            )

            try {
                // Network retry loop: retry up to 3 times on network interruptions
                var downloadedUri: Uri? = null
                var lastErr: Exception? = null
                val maxAttempts = 3

                for (attempt in 1..maxAttempts) {
                    ensureActive()
                    try {
                        downloadedUri = downloadAndConvertStream(
                            audioSourceUrl = audioSourceUrl,
                            videoId = videoInfo.videoId,
                            title = finalTitle,
                            artist = finalArtist,
                            quality = quality,
                            destinationTreeUri = destinationTreeUri,
                            onProgressUpdate = { progress, downloaded, total, stage ->
                                val currentStatus = if (progress >= 50 && progress < 95) DownloadStatus.CONVERTING else DownloadStatus.DOWNLOADING
                                val simpleStage = if (currentStatus == DownloadStatus.CONVERTING) "Converting Audio..." else "Downloading..."
                                _activeDownload.value = _activeDownload.value.copy(
                                    status = currentStatus,
                                    progress = progress,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    stageDescription = simpleStage
                                )
                            }
                        )
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastErr = e
                        if (attempt < maxAttempts) {
                            delay(attempt * 2000L)
                        }
                    }
                }

                if (downloadedUri == null) {
                    throw lastErr ?: Exception("Download failed after retries")
                }

                _activeDownload.value = _activeDownload.value.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    localUri = downloadedUri.toString(),
                    stageDescription = "Download Completed"
                )

                downloadHistoryDao.updateStatus(
                    id = historyId,
                    status = "COMPLETED",
                    localPath = downloadedUri.path,
                    mediaStoreUri = downloadedUri.toString(),
                    fileSize = _activeDownload.value.downloadedBytes,
                    errorMessage = null
                )

                val artFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "artwork/${videoInfo.videoId}.jpg")
                if (artFile.exists() && artFile.length() > 0) {
                    downloadHistoryDao.updateThumbnail(historyId, Uri.fromFile(artFile).toString())
                }

                withContext(Dispatchers.Main) {
                    onComplete(downloadedUri)
                }
            } catch (e: CancellationException) {
                cleanupPendingFiles()
                _activeDownload.value = _activeDownload.value.copy(
                    status = DownloadStatus.CANCELLED,
                    errorMessage = "Download was cancelled.",
                    stageDescription = "Download Cancelled"
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
                    stageDescription = "Download Failed"
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
     * Runs in the background via SonoraDownloadService even if Sonora is minimized or closed.
     * Persists queue items in Room database and retries on network interruptions.
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

        // Persist queue to Room DB so state restores when app is reopened
        scope.launch {
            val entities = queueItems.mapIndexed { idx, itm ->
                com.sonora.music.data.local.database.entity.DownloadQueueEntity(
                    id = itm.id,
                    videoId = itm.videoId,
                    title = itm.title,
                    artist = itm.artist,
                    thumbnailUrl = itm.thumbnailUrl,
                    downloadUrl = itm.downloadUrl,
                    quality = itm.quality.label,
                    status = itm.status.name,
                    progress = itm.progress,
                    downloadedBytes = itm.downloadedBytes,
                    totalBytes = itm.totalBytes,
                    errorMessage = itm.errorMessage,
                    localUri = itm.localUri,
                    isDuplicate = itm.isDuplicate,
                    playlistTitle = playlistInfo.title,
                    orderIndex = idx
                )
            }
            downloadQueueDao.clearQueue()
            downloadQueueDao.insertAll(entities)
        }

        startBackgroundQueueProcessing(playlistInfo.title, allowDuplicates, onPlaylistImported)
    }

    /**
     * Executes the playlist download queue sequentially in the background.
     * Handles network interruptions with exponential backoff retries.
     */
    private fun startBackgroundQueueProcessing(
        playlistTitle: String,
        allowDuplicates: Boolean = false,
        onPlaylistImported: (playlistId: Long) -> Unit = {}
    ) {
        queueJob?.cancel()
        SonoraDownloadService.start(context)

        queueJob = scope.launch {
            val downloadedTitles = mutableListOf<String>()

            // Pre-seed completed titles if resuming an interrupted session
            _downloadQueue.value.filter { it.status == DownloadStatus.COMPLETED }.forEach {
                downloadedTitles.add(it.title)
            }

            val totalCount = _downloadQueue.value.size

            for (index in _downloadQueue.value.indices) {
                ensureActive()

                val item = _downloadQueue.value.getOrNull(index) ?: continue
                if (item.status == DownloadStatus.COMPLETED || item.status == DownloadStatus.CANCELLED) {
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
                    downloadQueueDao.updateStatus(item.id, DownloadStatus.COMPLETED.name, 100, null, null)
                    downloadedTitles.add(item.title)
                    updateOverallProgress(index + 1, totalCount, playlistTitle)
                    continue
                }

                // Mark current track as downloading
                _downloadQueue.update { list ->
                    list.mapIndexed { idx, itm ->
                        if (idx == index) itm.copy(status = DownloadStatus.DOWNLOADING, progress = 0)
                        else itm
                    }
                }
                downloadQueueDao.updateProgress(item.id, DownloadStatus.DOWNLOADING.name, 0, 0, 0)

                _overallProgress.update {
                    it.copy(
                        currentTrackIndex = index + 1,
                        currentTrackTitle = item.title,
                        isQueueActive = true
                    )
                }

                val historyId = downloadHistoryDao.insertHistory(
                    DownloadHistoryEntity(
                        videoId = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        thumbnailUrl = item.thumbnailUrl,
                        quality = item.quality.label,
                        status = "DOWNLOADING",
                        downloadUrl = item.downloadUrl,
                        timestamp = System.currentTimeMillis()
                    )
                )

                var downloadedUri: Uri? = null
                var lastItemError: Exception? = null
                val maxAttempts = 3

                try {
                    for (attempt in 1..maxAttempts) {
                        ensureActive()
                        try {
                            val audioStreamUrl = if (!streamResolver.isDirectAudioUrl(item.downloadUrl)) {
                                val resolveResult = streamResolver.resolveAudioStreamUrl(item.videoId)
                                if (resolveResult.isFailure) {
                                    throw Exception("Stream resolve failed: ${resolveResult.exceptionOrNull()?.message}")
                                }
                                resolveResult.getOrThrow()
                            } else {
                                item.downloadUrl
                            }

                            var lastProgressDbWrite = 0L
                            downloadedUri = downloadAndConvertStream(
                                audioSourceUrl = audioStreamUrl,
                                videoId = item.videoId,
                                title = item.title,
                                artist = item.artist,
                                quality = item.quality,
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

                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressDbWrite > 500) {
                                        lastProgressDbWrite = now
                                        scope.launch {
                                            downloadQueueDao.updateProgress(item.id, DownloadStatus.DOWNLOADING.name, progress, downloaded, total)
                                        }
                                    }
                                }
                            )
                            lastItemError = null
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastItemError = e
                            e.printStackTrace()
                            if (attempt < maxAttempts) {
                                // Exponential backoff retry delay for network drops
                                delay(attempt * 2000L)
                            }
                        }
                    }

                    if (downloadedUri != null) {
                        _downloadQueue.update { list ->
                            list.mapIndexed { idx, itm ->
                                if (idx == index) itm.copy(
                                    status = DownloadStatus.COMPLETED,
                                    progress = 100,
                                    localUri = downloadedUri.toString()
                                ) else itm
                            }
                        }
                        downloadQueueDao.updateStatus(item.id, DownloadStatus.COMPLETED.name, 100, downloadedUri.toString(), null)

                        downloadHistoryDao.updateStatus(
                            id = historyId,
                            status = "COMPLETED",
                            localPath = downloadedUri.path,
                            mediaStoreUri = downloadedUri.toString(),
                            fileSize = _downloadQueue.value.getOrNull(index)?.downloadedBytes ?: 0L,
                            errorMessage = null
                        )

                        val playlistArtFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "artwork/${item.videoId}.jpg")
                        if (playlistArtFile.exists() && playlistArtFile.length() > 0) {
                            downloadHistoryDao.updateThumbnail(historyId, Uri.fromFile(playlistArtFile).toString())
                        }

                        downloadedTitles.add(item.title)
                    } else {
                        cleanupPendingFiles()
                        val errorMsg = lastItemError?.message ?: "Failed"
                        _downloadQueue.update { list ->
                            list.mapIndexed { idx, itm ->
                                if (idx == index) itm.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg)
                                else itm
                            }
                        }
                        downloadQueueDao.updateStatus(item.id, DownloadStatus.FAILED.name, 0, null, errorMsg)
                        downloadHistoryDao.updateStatus(historyId, "FAILED", null, null, 0L, errorMsg)
                    }
                } catch (e: CancellationException) {
                    cleanupPendingFiles()
                    _downloadQueue.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(status = DownloadStatus.CANCELLED, errorMessage = "Cancelled")
                            else itm
                        }
                    }
                    downloadQueueDao.updateStatus(item.id, DownloadStatus.CANCELLED.name, 0, null, "Cancelled by user")
                    downloadHistoryDao.updateStatus(historyId, "CANCELLED", null, null, 0L, "Cancelled by user")
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    cleanupPendingFiles()
                    val errorMsg = lastItemError?.message ?: e.message ?: "Failed"
                    _downloadQueue.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg)
                            else itm
                        }
                    }
                    downloadQueueDao.updateStatus(item.id, DownloadStatus.FAILED.name, 0, null, errorMsg)
                    downloadHistoryDao.updateStatus(historyId, "FAILED", null, null, 0L, errorMsg)
                }

                updateOverallProgress(index + 1, totalCount, playlistTitle)
            }

            // Sync with MediaStore & Room library
            delay(1200)
            val scannedSongs = mediaScanner.scanLocalAudio()
            database.songDao().syncMediaStoreSongs(scannedSongs)

            // Organize imported tracks under Sonora Playlist
            val playlistId = createSonoraPlaylistForImport(playlistTitle, downloadedTitles, scannedSongs)

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
        scope.launch {
            downloadQueueDao.clearQueue()
        }
        _downloadQueue.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PENDING) {
                    item.copy(status = DownloadStatus.CANCELLED, errorMessage = "Cancelled by user")
                } else item
            }
        }
        _overallProgress.update { it.copy(isQueueActive = false) }
        SonoraDownloadService.stop(context)
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
        scope.launch {
            downloadQueueDao.updateStatus(id, DownloadStatus.CANCELLED.name, 0, null, "Cancelled")
        }
    }

    /**
     * Retries an individual failed track in the queue.
     */
    fun retryQueueItem(id: String) {
        val item = _downloadQueue.value.find { it.id == id } ?: return
        _downloadQueue.update { list ->
            list.map {
                if (it.id == id) it.copy(status = DownloadStatus.PENDING, progress = 0, errorMessage = null)
                else it
            }
        }
        scope.launch {
            downloadQueueDao.updateStatus(id, DownloadStatus.PENDING.name, 0, null, null)
        }
        startBackgroundQueueProcessing(_overallProgress.value.targetPlaylistName ?: "Playlist", allowDuplicates = true)
    }

    /**
     * Retries all failed tracks in the queue.
     */
    fun retryFailedQueueItems() {
        val failedItems = _downloadQueue.value.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        if (failedItems.isEmpty()) return

        _downloadQueue.update { list ->
            list.map {
                if (it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED) {
                    it.copy(status = DownloadStatus.PENDING, progress = 0, errorMessage = null)
                } else it
            }
        }
        scope.launch {
            for (failed in failedItems) {
                downloadQueueDao.updateStatus(failed.id, DownloadStatus.PENDING.name, 0, null, null)
            }
        }
        startBackgroundQueueProcessing(_overallProgress.value.targetPlaylistName ?: "Playlist", allowDuplicates = true)
    }

    fun clearQueue() {
        cancelQueue()
        _downloadQueue.value = emptyList()
        _overallProgress.value = QueueOverallProgress()
        scope.launch {
            downloadQueueDao.clearQueue()
        }
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
        if (!_overallProgress.value.isQueueActive) {
            SonoraDownloadService.stop(context)
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

        onProgressUpdate(5, 0L, 0L, "Downloading...")

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
                val stageMsg = "Downloading..."
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

                            onProgressUpdate(downloadPct, totalDownloaded, totalBytes, "Downloading...")
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

        // Stage 2: Convert supported source audio to MP3 format (50% to 90% overall progress)
        onProgressUpdate(52, tempSourceFile.length(), tempSourceFile.length(), "Converting Audio...")

        // Download artwork locally so it is embedded into MP3 ID3 APIC tag and cached offline
        val localArtFile = if (!videoId.isNullOrBlank()) {
            val fallbackUrl = _activeDownload.value.thumbnailUrl?.takeIf { it.isNotBlank() } ?: ""
            downloadArtworkLocally(videoId, fallbackUrl)
        } else null
        val artworkBytes = localArtFile?.takeIf { it.exists() && it.length() > 0 }?.readBytes()

        val convertResult = ffmpegConverter.convertToMp3(
            sourceFile = tempSourceFile,
            targetMp3File = tempMp3File,
            quality = quality,
            title = title,
            artist = artist,
            artworkBytes = artworkBytes,
            onProgress = { convertPct, _ ->
                val overallPct = 50 + (convertPct * 0.4).toInt()
                onProgressUpdate(overallPct, tempSourceFile.length(), tempSourceFile.length(), "Converting Audio...")
            }
        )

        if (convertResult.isFailure) {
            tempSourceFile.delete()
            tempMp3File.delete()
            throw convertResult.exceptionOrNull() ?: Exception("Audio format conversion to MP3 failed")
        }

        // Stage 3: Save converted audio to destination folder with meaningful filename (90% to 100%)
        onProgressUpdate(92, tempMp3File.length(), tempMp3File.length(), "Saving Audio...")
        val isMp3 = ffmpegConverter.isMp3Stream(tempMp3File)
        val ext = if (isMp3) "mp3" else "m4a"
        val mimeType = if (isMp3) "audio/mpeg" else "audio/mp4"
        val meaningfulFilename = "${generateMeaningfulFilename(title, artist)}.$ext"
        val (outputStream, outputUri) = createAudioOutput(meaningfulFilename, title, artist, destinationTreeUri, mimeType)
        pendingMediaStoreUri = outputUri

        try {
            FileInputStream(tempMp3File).use { inStream ->
                inStream.copyTo(outputStream)
                outputStream.flush()
            }
            onProgressUpdate(100, tempMp3File.length(), tempMp3File.length(), "Download Completed")
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
                arrayOf(mimeType)
            ) { _, _ -> }
        }

        // Immediately index the downloaded song into Sonora Room database so it is instantly available and playable
        try {
            val mediaStoreId = try {
                android.content.ContentUris.parseId(outputUri)
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
            val artUri = localArtFile?.takeIf { it.exists() && it.length() > 0 }?.let { Uri.fromFile(it).toString() }
                ?: _activeDownload.value.thumbnailUrl

            val songEntity = com.sonora.music.data.local.database.entity.SongEntity(
                id = mediaStoreId,
                title = title,
                artist = artist,
                album = "Sonora Imports",
                durationMs = (_activeDownload.value.durationSeconds * 1000L).takeIf { it > 0 } ?: 0L,
                contentUriString = outputUri.toString(),
                albumId = 0L,
                albumArtUriString = artUri,
                trackNumber = 1,
                year = 2026,
                dateAdded = System.currentTimeMillis() / 1000,
                folderName = "Sonora",
                mimeType = mimeType,
                isFavorite = false,
                playCount = 0,
                lastPlayedTimestamp = 0L
            )
            database.songDao().insertSongs(listOf(songEntity))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        outputUri
    }

    /**
     * Creates an output stream and URI for saving the audio file,
     * supporting either user-selected SAF destination folder or default MediaStore Music/Sonora.
     */
    private fun createAudioOutput(
        fileName: String,
        title: String,
        artist: String,
        destinationTreeUri: Uri?,
        mimeType: String = "audio/mpeg"
    ): Pair<OutputStream, Uri> {
        if (destinationTreeUri != null) {
            val docId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(destinationTreeUri, docId)
            val createdUri = DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, fileName)
                ?: throw IllegalStateException("Could not create audio file in selected destination folder")
            val stream = context.contentResolver.openOutputStream(createdUri)
                ?: throw IllegalStateException("Could not open output stream for destination file")
            return Pair(stream, createdUri)
        } else {
            return createMediaStoreAudioOutput(fileName, title, artist, mimeType)
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

    /**
     * Downloads and caches artwork locally on the device so it is permanently available offline.
     */
    fun downloadArtworkLocally(videoId: String, thumbnailUrl: String): File? {
        return try {
            val artworkDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "artwork").apply { mkdirs() }
            val cleanId = videoId.ifBlank { System.currentTimeMillis().toString() }
            val targetFile = File(artworkDir, "$cleanId.jpg")
            if (targetFile.exists() && targetFile.length() > 1000) {
                return targetFile
            }

            val urlsToTry = mutableListOf<String>()
            if (thumbnailUrl.isNotBlank()) urlsToTry.add(thumbnailUrl)
            if (videoId.isNotBlank()) {
                urlsToTry.add("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                urlsToTry.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                urlsToTry.add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
            }

            for (u in urlsToTry.distinct()) {
                try {
                    val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3500
                        readTimeout = 3500
                    }
                    if (conn.responseCode in 200..299) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty() && !String(bytes.take(20).toByteArray()).contains("html", ignoreCase = true)) {
                            targetFile.writeBytes(bytes)
                            conn.disconnect()
                            return targetFile
                        }
                    }
                    conn.disconnect()
                } catch (ignored: Exception) {}
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
