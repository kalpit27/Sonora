package com.sonora.music

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.sonora.music.audio.SonoraAudioController
import com.sonora.music.data.local.database.SonoraDatabase
import com.sonora.music.data.local.preferences.SonoraPreferences
import com.sonora.music.data.local.scanner.MediaScanner
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

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        database = SonoraDatabase.getInstance(this)
        preferences = SonoraPreferences(this)
        scanner = MediaScanner(this)
        repository = MusicRepository(database, scanner, preferences)

        audioController = SonoraAudioController(this) { songId ->
            applicationScope.launch {
                repository.recordSongPlayed(songId)
            }
        }
    }
}
