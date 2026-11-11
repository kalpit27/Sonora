package com.sonora.music.audio

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sonora.music.data.model.PlaybackUiState
import com.sonora.music.data.model.Song
import com.sonora.music.data.model.SonoraRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface PlaybackTrackingCallbacks {
    fun onTrackStarted(song: Song)
    fun onListeningDelta(song: Song, deltaMs: Long, completed: Boolean, skipped: Boolean)
}

@UnstableApi
class SonoraAudioController(
    private val context: Context,
    private val trackingCallbacks: PlaybackTrackingCallbacks
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var originalQueue: List<Song> = emptyList()
    private var pendingPlayRequest: Pair<Song, List<Song>>? = null

    // Accurate session listening tracker
    private var activeTrackedSong: Song? = null
    private var lastStartedSongId: Long? = null
    private var unFlushedListeningMs: Long = 0L
    private var lastTickRealtimeMs: Long = 0L
    private var currentSessionCompleted: Boolean = false
    private var skipAlreadyRecordedForSongId: Long? = null

    init {
        initController()
    }

    private fun initController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, SonoraPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()?.apply {
                        addListener(playerListener)
                        syncPlayerState()
                    }
                    _uiState.update { it.copy(isConnected = true) }

                    // Execute any pending play request queued before connection completed
                    pendingPlayRequest?.let { (song, queue) ->
                        pendingPlayRequest = null
                        playSong(song, queue)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SonoraAudioController", "Failed to connect to MediaSessionService", e)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun captureElapsedTime() {
        val now = SystemClock.elapsedRealtime()
        if (lastTickRealtimeMs > 0L) {
            val delta = (now - lastTickRealtimeMs).coerceIn(0L, 1500L)
            unFlushedListeningMs += delta
        }
        lastTickRealtimeMs = if (mediaController?.isPlaying == true) now else 0L
    }

    private fun flushTrackingDelta(completed: Boolean = false, skipped: Boolean = false) {
        val song = activeTrackedSong ?: return
        val delta = unFlushedListeningMs
        unFlushedListeningMs = 0L
        if (delta > 0L || completed || skipped) {
            trackingCallbacks.onListeningDelta(
                song = song,
                deltaMs = delta,
                completed = completed,
                skipped = skipped
            )
        }
    }

    private fun beginTrackingSong(song: Song) {
        activeTrackedSong = song
        lastStartedSongId = song.id
        unFlushedListeningMs = 0L
        currentSessionCompleted = false
        skipAlreadyRecordedForSongId = null
        lastTickRealtimeMs = if (mediaController?.isPlaying == true) SystemClock.elapsedRealtime() else 0L
        trackingCallbacks.onTrackStarted(song)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                lastTickRealtimeMs = SystemClock.elapsedRealtime()
                startProgressTracker()
            } else {
                captureElapsedTime()
                lastTickRealtimeMs = 0L
                flushTrackingDelta()
                stopProgressTracker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            _uiState.update { it.copy(isBuffering = isBuffering) }

            if (playbackState == Player.STATE_ENDED) {
                captureElapsedTime()
                if (!currentSessionCompleted) {
                    currentSessionCompleted = true
                    flushTrackingDelta(completed = true)
                }
                handlePlaybackEnded()
            }
            syncPlayerState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val newIdx = controller.currentMediaItemIndex
            val newSong = _uiState.value.queue.getOrNull(newIdx)
            val prevSong = activeTrackedSong

            captureElapsedTime()
            if (prevSong != null && (prevSong.id != newSong?.id || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
                val autoCompleted = (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                    ) && !currentSessionCompleted
                val seekSkipped = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                    skipAlreadyRecordedForSongId != prevSong.id &&
                    !currentSessionCompleted &&
                    _uiState.value.durationMs > 0L &&
                    _uiState.value.positionMs < (_uiState.value.durationMs * 0.75).toLong()
                if (autoCompleted) {
                    currentSessionCompleted = true
                }
                flushTrackingDelta(completed = autoCompleted, skipped = seekSkipped)
            }

            syncPlayerState()

            if (newSong != null && (newSong.id != lastStartedSongId || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
                beginTrackingSong(newSong)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            syncPlayerState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e("SonoraAudioController", "ExoPlayer playback error: ${error.errorCodeName} - ${error.message}", error)
            captureElapsedTime()
            flushTrackingDelta()
            _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
        }
    }

    private fun syncPlayerState() {
        val controller = mediaController ?: return
        val currentIdx = controller.currentMediaItemIndex
        val isPlaying = controller.isPlaying
        val position = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration.coerceAtLeast(0L)

        val currentSong = _uiState.value.queue.getOrNull(currentIdx)

        _uiState.update { current ->
            current.copy(
                isPlaying = isPlaying,
                currentSong = currentSong,
                positionMs = position,
                durationMs = duration,
                currentQueueIndex = currentIdx
            )
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val now = SystemClock.elapsedRealtime()
                    if (lastTickRealtimeMs > 0L) {
                        val delta = (now - lastTickRealtimeMs).coerceIn(0L, 1500L)
                        unFlushedListeningMs += delta
                        if (unFlushedListeningMs >= 5_000L) {
                            flushTrackingDelta()
                        }
                    }
                    lastTickRealtimeMs = now
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playSong(song: Song, newQueue: List<Song> = listOf(song)) {
        val controller = mediaController
        if (controller == null) {
            pendingPlayRequest = Pair(song, newQueue)
            _uiState.update { it.copy(currentSong = song, isBuffering = true) }
            return
        }
        originalQueue = newQueue

        val playQueue = if (_uiState.value.isShuffle) {
            val rest = newQueue.filter { it.id != song.id }.shuffled()
            listOf(song) + rest
        } else {
            newQueue
        }

        val targetIndex = playQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        val mediaItems = playQueue.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(s.albumArtUri)
                        .build()
                )
                .build()
        }

        _uiState.update {
            it.copy(
                queue = playQueue,
                currentSong = song,
                currentQueueIndex = targetIndex,
                positionMs = 0L,
                durationMs = song.durationMs
            )
        }

        captureElapsedTime()
        flushTrackingDelta()
        beginTrackingSong(song)

        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun playQueueIndex(index: Int) {
        val controller = mediaController ?: return
        val queue = _uiState.value.queue
        if (index in queue.indices) {
            val song = queue[index]
            captureElapsedTime()
            flushTrackingDelta()
            _uiState.update {
                it.copy(
                    currentSong = song,
                    currentQueueIndex = index,
                    positionMs = 0L,
                    durationMs = song.durationMs
                )
            }
            beginTrackingSong(song)
            controller.seekTo(index, 0L)
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) {
                controller.seekTo(0, 0L)
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        val prev = activeTrackedSong
        val curPos = controller.currentPosition.coerceAtLeast(0L)
        val dur = controller.duration.coerceAtLeast(0L)
        val isEarlySkip = prev != null && !currentSessionCompleted &&
            (dur <= 0L || curPos < (dur * 0.75).toLong())
        if (isEarlySkip) {
            skipAlreadyRecordedForSongId = prev?.id
            captureElapsedTime()
            flushTrackingDelta(skipped = true)
        }
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        } else if (_uiState.value.repeatMode == SonoraRepeatMode.ALL && _uiState.value.queue.isNotEmpty()) {
            controller.seekTo(0, 0L)
            controller.play()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_uiState.value.isShuffle
        val currentSong = _uiState.value.currentSong

        val newQueue = if (newShuffle) {
            if (currentSong != null) {
                val rest = originalQueue.filter { it.id != currentSong.id }.shuffled()
                listOf(currentSong) + rest
            } else {
                originalQueue.shuffled()
            }
        } else {
            originalQueue
        }

        val newIndex = currentSong?.let { song ->
            newQueue.indexOfFirst { it.id == song.id }
        } ?: 0

        _uiState.update {
            it.copy(
                isShuffle = newShuffle,
                queue = newQueue,
                currentQueueIndex = newIndex
            )
        }

        val controller = mediaController ?: return
        val currentPos = controller.currentPosition
        val mediaItems = newQueue.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(s.albumArtUri)
                        .build()
                )
                .build()
        }
        controller.setMediaItems(mediaItems, newIndex.coerceAtLeast(0), currentPos)
    }

    fun cycleRepeatMode() {
        val nextMode = _uiState.value.repeatMode.next()
        _uiState.update { it.copy(repeatMode = nextMode) }
        val controller = mediaController ?: return
        when (nextMode) {
            SonoraRepeatMode.OFF -> controller.repeatMode = Player.REPEAT_MODE_OFF
            SonoraRepeatMode.ALL -> controller.repeatMode = Player.REPEAT_MODE_ALL
            SonoraRepeatMode.ONE -> controller.repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        val updatedQueue = _uiState.value.queue + song
        _uiState.update { it.copy(queue = updatedQueue) }
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()
        controller.addMediaItem(mediaItem)
    }

    fun playNext(song: Song) {
        val controller = mediaController ?: return
        val currentIdx = _uiState.value.currentQueueIndex
        val nextIdx = (currentIdx + 1).coerceAtMost(_uiState.value.queue.size)
        val updatedQueue = _uiState.value.queue.toMutableList().apply {
            add(nextIdx, song)
        }
        _uiState.update { it.copy(queue = updatedQueue) }
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()
        controller.addMediaItem(nextIdx, mediaItem)
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            controller.removeMediaItem(index)
            val newIdx = controller.currentMediaItemIndex
            val newSong = currentQueue.getOrNull(newIdx)
            _uiState.update {
                it.copy(
                    queue = currentQueue,
                    currentQueueIndex = newIdx,
                    currentSong = newSong
                )
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queue.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val item = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, item)
            controller.moveMediaItem(fromIndex, toIndex)
            val newIdx = controller.currentMediaItemIndex
            val newSong = currentQueue.getOrNull(newIdx)
            _uiState.update {
                it.copy(
                    queue = currentQueue,
                    currentQueueIndex = newIdx,
                    currentSong = newSong
                )
            }
        }
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        captureElapsedTime()
        flushTrackingDelta()
        activeTrackedSong = null
        lastStartedSongId = null
        controller.stop()
        controller.clearMediaItems()
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentSong = null,
                queue = emptyList(),
                currentQueueIndex = -1,
                positionMs = 0L,
                durationMs = 0L
            )
        }
    }

    private fun handlePlaybackEnded() {
        if (_uiState.value.repeatMode == SonoraRepeatMode.ALL) {
            val controller = mediaController ?: return
            lastStartedSongId = null
            controller.seekTo(0, 0L)
            controller.play()
        }
    }

    fun release() {
        captureElapsedTime()
        flushTrackingDelta()
        stopProgressTracker()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null
    }
}
