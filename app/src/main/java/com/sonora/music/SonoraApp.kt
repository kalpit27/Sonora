package com.sonora.music

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.sonora.music.audio.PlaybackTrackingCallbacks
import com.sonora.music.audio.SonoraAudioController
import com.sonora.music.data.local.database.SonoraDatabase
import com.sonora.music.data.local.preferences.SonoraPreferences
import com.sonora.music.data.local.scanner.MediaScanner
import com.sonora.music.data.model.Song
import com.sonora.music.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class SonoraApp : Application() {

    lateinit var database: SonoraDatabase
        private set

    lateinit var preferences: SonoraPreferences
        private set

    lateinit var scanner: MediaScanner
        private set

    lateinit var repository: MusicRepository
        private set

    lateinit var audioController: SonoraAudioController
        private set

    lateinit var youTubeAudioDownloader: com.sonora.music.data.downloader.YouTubeAudioDownloader
        private set

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        database = SonoraDatabase.getInstance(this)
        preferences = SonoraPreferences(this)
        scanner = MediaScanner(this)
        repository = MusicRepository(database, scanner, preferences)
        youTubeAudioDownloader = com.sonora.music.data.downloader.YouTubeAudioDownloader(this, database)

        audioController = SonoraAudioController(
            context = this,
            trackingCallbacks = object : PlaybackTrackingCallbacks {
                override fun onTrackStarted(song: Song) {
                    applicationScope.launch {
                        repository.onTrackStarted(song)
                    }
                }

                override fun onListeningDelta(
                    song: Song,
                    deltaMs: Long,
                    completed: Boolean,
                    skipped: Boolean
                ) {
                    applicationScope.launch {
                        repository.onListeningDelta(song, deltaMs, completed, skipped)
                    }
                }
            }
        )
    }
}
