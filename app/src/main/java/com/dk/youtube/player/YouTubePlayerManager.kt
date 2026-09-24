package com.dk.youtube.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central Native Media & Playback Manager for Kavach YouTube.
 * Powered by Jetpack Media3 (ExoPlayer) and direct InnerTube stream pipelines.
 */
object YouTubePlayerManager {

    interface PlayerController {
        fun play()
        fun pause()
        fun seekBy(seconds: Int)
        fun seekTo(seconds: Int)
        fun loadVideo(id: String)
        fun stop()
    }

    private var activeController: PlayerController? = null
    var persistentWebView: android.webkit.WebView? = null
    var currentLoadedVideoId: String? = null
    var isPlayerInitialized: Boolean = false

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var currentStreamBundle: VideoStreamBundle? = null

    // Native Jetpack Media3 Player instance
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    private val _videoId = MutableStateFlow<String?>(null)
    val videoId: StateFlow<String?> = _videoId.asStateFlow()

    private val _videoTitle = MutableStateFlow("YouTube Ad-Free")
    val videoTitle: StateFlow<String> = _videoTitle.asStateFlow()

    private val _channelName = MutableStateFlow("YouTube Player")
    val channelName: StateFlow<String> = _channelName.asStateFlow()

    private val _audioQuality = MutableStateFlow("256 kbps")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoadingStream = MutableStateFlow(false)
    val isLoadingStream: StateFlow<Boolean> = _isLoadingStream.asStateFlow()

    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    private val _isBackgroundPlayEnabled = MutableStateFlow(true)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    private val _isAutoplayEnabled = MutableStateFlow(true)
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<YouTubeVideoItem>>(emptyList())
    val currentPlaylist: StateFlow<List<YouTubeVideoItem>> = _currentPlaylist.asStateFlow()

    private val _durationSec = MutableStateFlow(0)
    val durationSec: StateFlow<Int> = _durationSec.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0)
    val currentPositionSec: StateFlow<Int> = _currentPositionSec.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _videoQuality = MutableStateFlow("1080p")
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    /**
     * Initializes or returns the singleton ExoPlayer configured with OkHttp and AudioAttributes.
     */
    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        _exoPlayer?.let { return it }

        val appContext = context.applicationContext

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        val dataSourceFactory = DefaultDataSource.Factory(appContext, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startProgressTicker()
                        } else {
                            progressJob?.cancel()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _isLoadingStream.value = false
                                val dur = duration
                                if (dur > 0) {
                                    _durationSec.value = (dur / 1000).toInt()
                                }
                            }
                            Player.STATE_BUFFERING -> {
                                // Keep buffering active
                            }
                            Player.STATE_ENDED -> {
                                _isPlaying.value = false
                                progressJob?.cancel()
                                if (_isAutoplayEnabled.value) {
                                    playNextVideo(appContext)
                                }
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _isLoadingStream.value = false
                        _isPlaying.value = false
                    }
                })
            }

        _exoPlayer = player
        isPlayerInitialized = true
        return player
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (isActive) {
                val player = _exoPlayer
                if (player != null && player.isPlaying) {
                    val pos = (player.currentPosition / 1000).toInt()
                    val dur = if (player.duration > 0) (player.duration / 1000).toInt() else _durationSec.value
                    _currentPositionSec.value = pos
                    if (dur > 0) _durationSec.value = dur
                }
                delay(400)
            }
        }
    }

    fun registerController(controller: PlayerController) {
        activeController = controller
    }

    fun unregisterController(controller: PlayerController) {
        if (activeController === controller) {
            activeController = null
        }
    }

    fun updatePlaybackState(
        playing: Boolean,
        vId: String? = null,
        title: String? = null,
        channel: String? = null,
        duration: Int? = null,
        currentPos: Int? = null,
        audioQuality: String? = null
    ) {
        _isPlaying.value = playing
        vId?.let { if (it.isNotBlank()) _videoId.value = it }
        title?.let { if (it.isNotBlank()) _videoTitle.value = it }
        channel?.let { if (it.isNotBlank()) _channelName.value = it }
        duration?.let { if (it > 0) _durationSec.value = it }
        currentPos?.let { if (it >= 0) _currentPositionSec.value = it }
        audioQuality?.let { if (it.isNotBlank()) _audioQuality.value = it }
    }

    fun setAudioOnly(enabled: Boolean, context: Context? = null) {
        _isAudioOnly.value = enabled
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
                .build()
        }

        if (enabled && context != null) {
            startBackgroundService(context)
        }
    }

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _isBackgroundPlayEnabled.value = enabled
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
    }

    fun setPlaylist(list: List<YouTubeVideoItem>) {
        _currentPlaylist.value = list
    }

    fun playNextVideo(context: Context? = null): Boolean {
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.playNextVideo === 'function') window.playNextVideo();", null)
        }

        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return true

        val curId = _videoId.value
        val currentIndex = playlist.indexOfFirst { it.id == curId }
        val nextVideo = if (currentIndex != -1 && currentIndex + 1 < playlist.size) {
            playlist[currentIndex + 1]
        } else {
            playlist.firstOrNull { it.id != curId } ?: playlist.first()
        }

        loadVideo(
            id = nextVideo.id,
            title = nextVideo.title,
            channel = nextVideo.channel,
            audioQuality = nextVideo.audioQuality,
            context = context
        )
        return true
    }

    fun playPreviousVideo(context: Context? = null): Boolean {
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.playPreviousVideo === 'function') window.playPreviousVideo();", null)
        }

        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return true

        val curId = _videoId.value
        val currentIndex = playlist.indexOfFirst { it.id == curId }
        val prevVideo = if (currentIndex > 0) {
            playlist[currentIndex - 1]
        } else {
            playlist.lastOrNull() ?: playlist.first()
        }

        loadVideo(
            id = prevVideo.id,
            title = prevVideo.title,
            channel = prevVideo.channel,
            audioQuality = prevVideo.audioQuality,
            context = context
        )
        return true
    }

    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 100)
        _volume.value = clamped
        _isMuted.value = (clamped == 0)
        _exoPlayer?.volume = clamped / 100f
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.setVolume === 'function') window.setVolume($clamped);", null)
        }
    }

    fun toggleMute() {
        val nowMuted = !_isMuted.value
        _isMuted.value = nowMuted
        _exoPlayer?.volume = if (nowMuted) 0f else (_volume.value / 100f)
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.toggleMute === 'function') window.toggleMute();", null)
        }
    }

    fun setVideoQuality(quality: String) {
        _videoQuality.value = quality
        val maxHeight = when (quality.lowercase()) {
            "1080p", "1080" -> 1080
            "720p", "720" -> 720
            "480p", "480" -> 480
            "360p", "360" -> 360
            "240p", "240" -> 240
            else -> Int.MAX_VALUE
        }
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, maxHeight)
                .build()
        }
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.setPlaybackQuality === 'function') window.setPlaybackQuality('$quality');", null)
        }
    }

    fun play(context: Context? = null) {
        _isPlaying.value = true
        _exoPlayer?.play()
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.playVideo === 'function') window.playVideo();", null)
        }
        context?.let { startBackgroundService(it) }
    }

    fun pause(context: Context? = null) {
        _isPlaying.value = false
        _exoPlayer?.pause()
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.pauseVideo === 'function') window.pauseVideo();", null)
        }
    }

    fun togglePlayPause(context: Context? = null) {
        if (_isPlaying.value) {
            pause(context)
        } else {
            play(context)
        }
    }

    fun toggleLoop() {
        val nextLoop = !_isLooping.value
        _isLooping.value = nextLoop
        _exoPlayer?.repeatMode = if (nextLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("let v = document.querySelector('video'); if (v) v.loop = $nextLoop;", null)
        }
    }

    fun seekBy(seconds: Int, context: Context? = null) {
        _exoPlayer?.let { player ->
            val target = (player.currentPosition + (seconds * 1000L)).coerceAtLeast(0L)
            player.seekTo(target)
            _currentPositionSec.value = (target / 1000).toInt()
        }
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.seekBy === 'function') window.seekBy($seconds);", null)
        }
    }

    fun seekTo(seconds: Int) {
        _currentPositionSec.value = seconds
        _exoPlayer?.seekTo(seconds * 1000L)
        persistentWebView?.post {
            persistentWebView?.evaluateJavascript("if (typeof window.seekTo === 'function') window.seekTo($seconds);", null)
        }
    }

    fun loadVideo(
        id: String,
        title: String? = null,
        channel: String? = null,
        audioQuality: String? = null,
        context: Context? = null
    ) {
        _videoId.value = id
        _videoTitle.value = title ?: "Loading Video..."
        _channelName.value = channel ?: "YouTube"
        _audioQuality.value = audioQuality ?: "256 kbps"
        _isPlaying.value = true
        _isLoadingStream.value = true
        _currentPositionSec.value = 0
        _durationSec.value = 0
        currentLoadedVideoId = id
        isPlayerInitialized = true

        // Primary: Navigate WebView to YouTube mobile watch page
        // The WebView + injected JS bridge handles all playback, ad-blocking, and state sync
        val watchUrl = "https://m.youtube.com/watch?v=$id"
        persistentWebView?.post {
            persistentWebView?.loadUrl(watchUrl)
        }

        // Start background service immediately for lock-screen controls
        context?.applicationContext?.let { appContext ->
            startBackgroundService(appContext)
        }

        // Also attempt InnerTube stream extraction for ExoPlayer fallback (optional, may fail)
        loadJob?.cancel()
        loadJob = playerScope.launch {
            val bundle = YouTubeStreamRepository.fetchStreamBundle(id)
            currentStreamBundle = bundle

            withContext(Dispatchers.Main) {
                if (bundle != null) {
                    if (bundle.title.isNotBlank() && bundle.title != "YouTube Video") {
                        val current = _videoTitle.value
                        if (current.isBlank() || current == "Loading Video..." || current == "YouTube Ad-Free") {
                            _videoTitle.value = bundle.title
                        }
                    }
                    if (bundle.author.isNotBlank() && bundle.author != "YouTube Channel") {
                        val current = _channelName.value
                        if (current.isBlank() || current == "YouTube" || current == "YouTube Player") {
                            _channelName.value = bundle.author
                        }
                    }
                    if (bundle.durationSec > 0) {
                        _durationSec.value = bundle.durationSec.toInt()
                    }
                }
                _isLoadingStream.value = false

                // Update service with resolved metadata
                context?.applicationContext?.let { startBackgroundService(it) }
            }
        }
    }

    fun stop(context: Context? = null) {
        _isPlaying.value = false
        progressJob?.cancel()
        _exoPlayer?.stop()
        context?.let { stopBackgroundService(it) }
    }

    fun releasePlayer() {
        progressJob?.cancel()
        loadJob?.cancel()
        _exoPlayer?.release()
        _exoPlayer = null
        isPlayerInitialized = false
    }

    fun startBackgroundService(context: Context) {
        if (!_isBackgroundPlayEnabled.value) return
        val intent = Intent(context, YouTubePlayerService::class.java).apply {
            action = YouTubePlayerService.ACTION_START
            putExtra(YouTubePlayerService.EXTRA_TITLE, _videoTitle.value)
            putExtra(YouTubePlayerService.EXTRA_CHANNEL, _channelName.value)
            putExtra(YouTubePlayerService.EXTRA_IS_PLAYING, _isPlaying.value)
            putExtra(YouTubePlayerService.EXTRA_VIDEO_ID, _videoId.value)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    fun stopBackgroundService(context: Context) {
        val intent = Intent(context, YouTubePlayerService::class.java).apply {
            action = YouTubePlayerService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}
    }
}
