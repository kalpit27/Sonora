package com.sonora.music.data.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sonora.music.MainActivity
import com.sonora.music.R
import com.sonora.music.SonoraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SonoraDownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "sonora_background_downloads"
        const val CHANNEL_NAME = "Sonora Background Downloads"
        const val COMPLETE_CHANNEL_ID = "sonora_download_alerts"
        const val COMPLETE_CHANNEL_NAME = "Sonora Download Alerts"

        const val NOTIFICATION_ID = 4040
        const val COMPLETE_NOTIFICATION_ID = 4041

        const val ACTION_START_DOWNLOAD = "com.sonora.music.action.START_DOWNLOAD"
        const val ACTION_STOP_SERVICE = "com.sonora.music.action.STOP_SERVICE"
        const val ACTION_CANCEL_DOWNLOAD = "com.sonora.music.action.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, SonoraDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SonoraDownloadService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? SonoraApp
        val downloader = app?.youTubeAudioDownloader

        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            downloader?.cancelQueue()
            downloader?.cancelCurrentDownload()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground immediately
        val initialNotification = buildProgressNotification(
            title = "Downloading Audio...",
            subtitle = "Starting download queue in background...",
            progress = 0
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startObservingProgress(downloader)

        return START_STICKY
    }

    private fun startObservingProgress(downloader: YouTubeAudioDownloader?) {
        if (downloader == null) return
        observerJob?.cancel()

        observerJob = serviceScope.launch {
            var hasSeenActiveWork = false
            var wasQueueActive = false
            var lastPlaylistName: String? = null
            var lastCompletedCount = 0
            var lastFailedCount = 0

            // Safety watchdog: if after 6 seconds no work became active, shut down cleanly
            serviceScope.launch {
                kotlinx.coroutines.delay(6000L)
                if (!hasSeenActiveWork) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            combine(
                downloader.overallProgress,
                downloader.activeDownload,
                downloader.downloadQueue
            ) { queueProgress, singleActive, queueList ->
                Triple(queueProgress, singleActive, queueList)
            }.collect { (queueProgress, singleActive, queueList) ->
                val isQueueActive = queueProgress.isQueueActive
                val isSingleActive = singleActive.status == com.sonora.music.data.model.DownloadStatus.DOWNLOADING ||
                        singleActive.status == com.sonora.music.data.model.DownloadStatus.CONVERTING

                if (isQueueActive) {
                    hasSeenActiveWork = true
                    wasQueueActive = true
                    lastPlaylistName = queueProgress.targetPlaylistName
                    lastCompletedCount = queueProgress.completedTracks
                    lastFailedCount = queueProgress.failedTracks

                    val currentTitle = queueProgress.currentTrackTitle.ifBlank { "Processing..." }
                    val progressPct = queueProgress.overallPercentage.coerceIn(0, 100)
                    val playlistName = queueProgress.targetPlaylistName ?: "Playlist"
                    val countStr = "Track ${queueProgress.currentTrackIndex} of ${queueProgress.totalTracks}"

                    val notification = buildProgressNotification(
                        title = "Downloading $playlistName",
                        subtitle = "$countStr • $currentTitle",
                        progress = progressPct
                    )
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } else if (isSingleActive) {
                    hasSeenActiveWork = true
                    val title = singleActive.title.ifBlank { "Downloading Song" }
                    val statusText = if (singleActive.status == com.sonora.music.data.model.DownloadStatus.CONVERTING) "Converting Audio..." else "Downloading..."
                    val progressPct = singleActive.progress.coerceIn(0, 100)

                    val notification = buildProgressNotification(
                        title = title,
                        subtitle = "$statusText ($progressPct%)",
                        progress = progressPct
                    )
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } else if (hasSeenActiveWork) {
                    // Work was active and has now completed
                    if (wasQueueActive) {
                        showCompletionNotification(
                            completedCount = queueProgress.completedTracks.coerceAtLeast(lastCompletedCount),
                            failedCount = queueProgress.failedTracks.coerceAtLeast(lastFailedCount),
                            playlistName = queueProgress.targetPlaylistName ?: lastPlaylistName
                        )
                    } else if (singleActive.status == com.sonora.music.data.model.DownloadStatus.COMPLETED) {
                        showSingleCompletionNotification(singleActive.title)
                    }

                    // Release and stop
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun showSingleCompletionNotification(songTitle: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            3,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanTitle = songTitle.ifBlank { "Song" }
        val notification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sonora)
            .setContentTitle("Download Completed")
            .setContentText("\"$cleanTitle\" is ready in Sonora Offline Library.")
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(
        title: String,
        subtitle: String,
        progress: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, SonoraDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sonora)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpen)
            .setProgress(100, progress, progress <= 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletionNotification(
        completedCount: Int,
        failedCount: Int,
        playlistName: String?
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            2,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val name = playlistName?.ifBlank { "Playlist" } ?: "Playlist"
        val title = if (failedCount == 0) "$name Download Complete" else "$name Download Finished"
        val body = if (failedCount == 0) {
            "$completedCount songs downloaded and ready in Sonora Offline Library."
        } else {
            "$completedCount downloaded successfully, $failedCount failed."
        }

        val notification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sonora)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val downloadChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live background download and conversion progress"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                COMPLETE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when background downloads finish"
            }

            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Sonora:BackgroundDownloadWakeLock"
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours maximum safety timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observerJob?.cancel()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
