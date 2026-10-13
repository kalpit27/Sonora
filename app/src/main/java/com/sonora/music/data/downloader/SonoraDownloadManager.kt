package com.sonora.music.data.downloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.sonora.music.data.model.DownloadStatus
import com.sonora.music.data.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

class SonoraDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadTasks = MutableStateFlow<Map<Long, DownloadTask>>(emptyMap())
    val downloadTasks: StateFlow<Map<Long, DownloadTask>> = _downloadTasks.asStateFlow()

    var onDownloadFinishedListener: ((DownloadTask, Uri?) -> Unit)? = null

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(recvContext: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != -1L) {
                    checkDownloadStatus(downloadId)
                }
            }
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // System broadcast ACTION_DOWNLOAD_COMPLETE requires RECEIVER_EXPORTED on API 33+
                context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(downloadCompleteReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
    }

    /**
     * Enqueue a direct audio URL download via Android DownloadManager.
     * Saves directly into public /sdcard/Music/Sonora/ directory.
     */
    fun enqueueDownload(
        title: String,
        artist: String = "",
        downloadUrl: String,
        extension: String = "mp3"
    ): Result<Long> {
        val cleanUrl = downloadUrl.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            return Result.failure(IllegalArgumentException("Please enter a valid HTTP or HTTPS audio URL"))
        }

        return try {
            val safeTitle = title.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
            val safeArtist = if (artist.isNotBlank()) " - " + artist.trim().replace(Regex("""[\\/:*?"<>|]"""), "_") else ""
            val fileName = "$safeTitle$safeArtist.$extension"

            val uri = Uri.parse(cleanUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle(title.ifBlank { fileName })
                setDescription(if (artist.isNotBlank()) "Sonora Music • $artist" else "Downloading to Sonora Library")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)

                // Save directly to the public Music directory under Sonora folder
                try {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Sonora/$fileName")
                } catch (e: Exception) {
                    // Fallback to app external files dir if public external fails
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
                    dir.mkdirs()
                    setDestinationUri(Uri.fromFile(File(dir, fileName)))
                }
            }

            val downloadId = downloadManager.enqueue(request)
            val task = DownloadTask(
                id = downloadId,
                title = title,
                artist = artist,
                status = DownloadStatus.DOWNLOADING,
                progress = 0
            )

            _downloadTasks.update { it + (downloadId to task) }
            Result.success(downloadId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun cancelDownload(downloadId: Long) {
        try {
            downloadManager.remove(downloadId)
            _downloadTasks.update { it - downloadId }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkDownloadStatus(downloadId: Long) {
        scope.launch {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var cursor: Cursor? = null
            try {
                cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                    val localUriStr = if (uriIndex >= 0) cursor.getString(uriIndex) else null

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val fileUri = localUriStr?.let { Uri.parse(it) }
                            val filePath = fileUri?.path

                            // Trigger Android MediaScanner to immediately index the file into MediaStore
                            if (filePath != null) {
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(filePath),
                                    arrayOf("audio/*")
                                ) { scannedPath, scannedUri ->
                                    // MediaScan complete
                                }
                            }

                            _downloadTasks.update { map ->
                                val existing = map[downloadId]
                                if (existing != null) {
                                    val updated = existing.copy(
                                        status = DownloadStatus.COMPLETED,
                                        progress = 100,
                                        localUri = localUriStr
                                    )
                                    map + (downloadId to updated)
                                } else map
                            }

                            val completedTask = _downloadTasks.value[downloadId]
                            if (completedTask != null) {
                                onDownloadFinishedListener?.invoke(completedTask, fileUri)
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                            _downloadTasks.update { map ->
                                val existing = map[downloadId]
                                if (existing != null) {
                                    val updated = existing.copy(
                                        status = DownloadStatus.FAILED,
                                        errorMessage = "Download failed (code: $reason)"
                                    )
                                    map + (downloadId to updated)
                                } else map
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
    }

    /**
     * Check if a dedicated downloader app (like Seal or NewPipe) is installed.
     */
    fun isExternalDownloaderAvailable(): Boolean {
        val knownPackages = listOf("com.junkfood.seal", "org.schabi.newpipe")
        val pm = context.packageManager
        for (pkg in knownPackages) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return true
            } catch (e: Exception) {
                // Not installed
            }
        }
        return false
    }

    /**
     * Deep-links the track or URL to an installed external downloader app (Seal / NewPipe).
     */
    fun openExternalDownloader(trackTitle: String, artist: String, optionalUrl: String? = null): Boolean {
        val query = if (optionalUrl.isNullOrBlank()) "$trackTitle $artist" else optionalUrl
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * One-tap search for the song on web/browser (e.g. Google / Pagalworld / MP3 download).
     * Files saved to device's Music folder will be auto-indexed by Sonora.
     */
    fun openWebMusicSearch(trackTitle: String, artist: String) {
        try {
            val query = "$trackTitle $artist mp3 download"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val webUrl = "https://www.google.com/search?q=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * One-tap search on YouTube Music or YouTube.
     */
    fun openYouTubeMusicSearch(trackTitle: String, artist: String) {
        try {
            val query = "$trackTitle $artist"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val ytUrl = "https://music.youtube.com/search?q=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
