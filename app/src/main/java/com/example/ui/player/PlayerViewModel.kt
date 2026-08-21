package com.example.ui.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.local.AppDatabase
import com.example.data.model.StreamFormat
import com.example.data.model.VideoItem
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoResizeMode(val displayName: String, val exoMode: Int) {
    FIT("Fit Screen", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill / Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Crop to Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

data class AudioTrackInfo(
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = VideoRepository(database.videoDao(), application)

    private var exoPlayer: ExoPlayer? = null

    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(true)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _resizeMode = MutableStateFlow(VideoResizeMode.FIT)
    val resizeMode: StateFlow<VideoResizeMode> = _resizeMode.asStateFlow()

    private val _isScreenLocked = MutableStateFlow(false)
    val isScreenLocked: StateFlow<Boolean> = _isScreenLocked.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _resumedPositionNotice = MutableStateFlow<Long?>(null)
    val resumedPositionNotice: StateFlow<Long?> = _resumedPositionNotice.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<AudioTrackInfo>> = _availableAudioTracks.asStateFlow()

    private var progressTickerJob: Job? = null
    private var lastSavedPositionMs = 0L

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val context = getApplication<Application>()
            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context))
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build()

            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressTicker()
                    } else {
                        saveCurrentProgress()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _isBuffering.value = true
                        }
                        Player.STATE_READY -> {
                            _isBuffering.value = false
                            _durationMs.value = player.duration.coerceAtLeast(0L)
                        }
                        Player.STATE_ENDED -> {
                            _isBuffering.value = false
                            _isPlaying.value = false
                            saveCurrentProgress()
                        }
                        Player.STATE_IDLE -> {
                            _isBuffering.value = false
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _isBuffering.value = false
                    _errorMessage.value = "Playback error: ${error.localizedMessage ?: "Unknown error"}"
                }

                override fun onTracksChanged(tracks: Tracks) {
                    updateAvailableTracks(tracks)
                }
            })

            exoPlayer = player
        }
        return exoPlayer!!
    }

    fun loadVideo(videoId: Long) {
        viewModelScope.launch {
            val video = repository.getVideoById(videoId)
            if (video == null) {
                _errorMessage.value = "Video not found"
                return@launch
            }

            _currentVideo.value = video
            val player = getPlayer()
            player.stop()
            player.clearMediaItems()

            val uri = Uri.parse(video.videoUrl)
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setDescription(video.description)
                        .build()
                )

            // Explicit MIME type if HLS or DASH
            when (video.streamFormat) {
                StreamFormat.HLS.name -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                StreamFormat.DASH.name -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                else -> {
                    if (video.videoUrl.contains(".m3u8", ignoreCase = true)) {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (video.videoUrl.contains(".mpd", ignoreCase = true)) {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                    }
                }
            }

            val mediaItem = mediaItemBuilder.build()
            player.setMediaItem(mediaItem)
            player.prepare()

            // Resume from last watch position if available
            val resumePos = video.watchPositionMs
            if (resumePos > 3000L && (video.durationMs == 0L || resumePos < (video.durationMs * 0.95f))) {
                player.seekTo(resumePos)
                _resumedPositionNotice.value = resumePos
            } else {
                _resumedPositionNotice.value = null
            }

            player.playWhenReady = true
            _errorMessage.value = null
            startProgressTicker()
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    val buf = player.bufferedPosition.coerceAtLeast(0L)

                    _currentPositionMs.value = pos
                    _durationMs.value = dur
                    _bufferedPositionMs.value = buf

                    // Periodic save every 5 seconds or significant jump
                    if (kotlin.math.abs(pos - lastSavedPositionMs) > 5000L) {
                        saveCurrentProgress()
                    }
                }
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        saveCurrentProgress()
    }

    fun seekRelative(offsetMs: Long) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
            _currentPositionMs.value = newPos
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    fun toggleResizeMode() {
        val nextMode = when (_resizeMode.value) {
            VideoResizeMode.FIT -> VideoResizeMode.FILL
            VideoResizeMode.FILL -> VideoResizeMode.ZOOM
            VideoResizeMode.ZOOM -> VideoResizeMode.FIT
        }
        _resizeMode.value = nextMode
    }

    fun toggleScreenLock() {
        _isScreenLocked.value = !_isScreenLocked.value
    }

    fun clearResumeNotice() {
        _resumedPositionNotice.value = null
    }

    private fun updateAvailableTracks(tracks: Tracks) {
        val audioList = mutableListOf<AudioTrackInfo>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val label = format.label ?: format.language ?: "Audio Track ${audioList.size + 1}"
                    val lang = format.language ?: "und"
                    audioList.add(
                        AudioTrackInfo(
                            id = "${group.hashCode()}_$i",
                            label = "$label (${format.sampleMimeType ?: ""})",
                            language = lang,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }
        _availableAudioTracks.value = audioList
    }

    fun selectAudioTrack(language: String) {
        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setPreferredAudioLanguage(language)
            ?.build() ?: TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    }

    fun saveCurrentProgress() {
        val video = _currentVideo.value ?: return
        val player = exoPlayer ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        if (dur > 0L || pos > 0L) {
            lastSavedPositionMs = pos
            viewModelScope.launch {
                repository.updateWatchProgress(video.id, pos, dur)
            }
        }
    }

    fun resetAndPlayFromBeginning() {
        exoPlayer?.seekTo(0L)
        _currentPositionMs.value = 0L
        _resumedPositionNotice.value = null
        saveCurrentProgress()
    }

    override fun onCleared() {
        super.onCleared()
        progressTickerJob?.cancel()
        saveCurrentProgress()
        exoPlayer?.release()
        exoPlayer = null
    }
}
