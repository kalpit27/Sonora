package com.sonora.music.audio

import android.content.ComponentName
import android.content.Context
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

@UnstableApi
class SonoraAudioController(
    private val context: Context,
    private val onSongPlayed: (Long) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var originalQueue: List<Song> = emptyList()

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
                mediaController = controllerFuture?.get()?.apply {
                    addListener(playerListener)
                    syncPlayerState()
                }
                _uiState.update { it.copy(isConnected = true) }
            },
            MoreExecutors.directExecutor()
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            _uiState.update { it.copy(isBuffering = isBuffering) }

            if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
            }
            syncPlayerState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlayerState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            syncPlayerState()
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

        currentSong?.let { song ->
            onSongPlayed(song.id)
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
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
        val controller = mediaController ?: return
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

        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()

        onSongPlayed(song.id)
    }

    fun playQueueIndex(index: Int) {
        val controller = mediaController ?: return
        val queue = _uiState.value.queue
        if (index in queue.indices) {
            val song = queue[index]
            controller.seekTo(index, 0L)
            controller.play()
            _uiState.update {
                it.copy(
                    currentSong = song,
                    currentQueueIndex = index,
                    positionMs = 0L,
                    durationMs = song.durationMs
                )
            }
            onSongPlayed(song.id)
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
            controller.seekTo(0, 0L)
            controller.play()
        }
    }

    fun release() {
        stopProgressTracker()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null
    }
}
