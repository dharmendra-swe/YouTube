package com.dk.youtube.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.dk.youtube.MainActivity
import com.dk.youtube.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Foreground Service that enables background & screen-off audio playback for YouTube Player.
 * Holds a PARTIAL_WAKE_LOCK to prevent CPU deep sleep while audio plays,
 * and renders rich lock-screen transport controls via MediaSessionCompat and MediaStyle.
 */
class YouTubePlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "youtube_player_audio_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.dk.youtube.player.ACTION_START"
        const val ACTION_UPDATE_META = "com.dk.youtube.player.ACTION_UPDATE_META"
        const val ACTION_TOGGLE = "com.dk.youtube.player.ACTION_TOGGLE"
        const val ACTION_PLAY = "com.dk.youtube.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dk.youtube.player.ACTION_PAUSE"
        const val ACTION_PREV = "com.dk.youtube.player.ACTION_PREV"
        const val ACTION_NEXT = "com.dk.youtube.player.ACTION_NEXT"
        const val ACTION_STOP = "com.dk.youtube.player.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_VIDEO_ID = "extra_video_id"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var stateJob: Job? = null
    private var thumbnailJob: Job? = null

    private var currentTitle: String = "YouTube Player"
    private var currentChannel: String = "YouTube Audio"
    private var isPlaying: Boolean = false
    private var currentVideoId: String? = null
    private var currentThumbnailBitmap: Bitmap? = null
    private var lastLoadedThumbnailVideoId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        acquireWakeLock()
        observePlayerState()
    }

    private fun initMediaSession() {
        val mediaButtonReceiver = ComponentName(this, androidx.media.session.MediaButtonReceiver::class.java)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = mediaButtonReceiver
        }
        val mediaButtonPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSessionCompat(this, "YouTubeMediaSession", mediaButtonReceiver, mediaButtonPendingIntent).apply {
            setMediaButtonReceiver(mediaButtonPendingIntent)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    YouTubePlayerManager.play(this@YouTubePlayerService)
                }

                override fun onPause() {
                    YouTubePlayerManager.pause(this@YouTubePlayerService)
                }

                override fun onSkipToNext() {
                    val nextPlayed = YouTubePlayerManager.playNextVideo(this@YouTubePlayerService)
                    if (!nextPlayed) {
                        YouTubePlayerManager.seekBy(10, this@YouTubePlayerService)
                    }
                }

                override fun onSkipToPrevious() {
                    YouTubePlayerManager.seekBy(-10, this@YouTubePlayerService)
                }

                override fun onFastForward() {
                    YouTubePlayerManager.seekBy(10, this@YouTubePlayerService)
                }

                override fun onRewind() {
                    YouTubePlayerManager.seekBy(-10, this@YouTubePlayerService)
                }

                override fun onStop() {
                    YouTubePlayerManager.stop(this@YouTubePlayerService)
                }

                override fun onSeekTo(pos: Long) {
                    YouTubePlayerManager.seekTo((pos / 1000).toInt())
                }
            })
            isActive = true
        }
    }

    private fun observePlayerState() {
        stateJob?.cancel()
        stateJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                YouTubePlayerManager.isPlaying,
                YouTubePlayerManager.videoTitle,
                YouTubePlayerManager.channelName,
                YouTubePlayerManager.durationSec,
                YouTubePlayerManager.currentPositionSec,
                YouTubePlayerManager.videoId
            ) { args: Array<Any?> ->
                val playing = args[0] as Boolean
                val title = args[1] as String
                val channel = args[2] as String
                val duration = args[3] as Int
                val pos = args[4] as Int
                val vId = args[5] as? String

                val metaChanged = (currentTitle != title || currentChannel != channel || isPlaying != playing || currentVideoId != vId)
                currentTitle = title
                currentChannel = channel
                isPlaying = playing
                currentVideoId = vId
                if (vId != null && vId != lastLoadedThumbnailVideoId) {
                    loadThumbnailAsync(vId)
                }
                if (metaChanged) {
                    updateMediaSessionMetadata(duration)
                    updateForegroundNotification()
                    if (isPlaying) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                }
                updatePlaybackState(pos)
            }.collect()
        }
    }

    private fun loadThumbnailAsync(vId: String) {
        lastLoadedThumbnailVideoId = vId
        thumbnailJob?.cancel()
        thumbnailJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://i.ytimg.com/vi/$vId/hqdefault.jpg")
                val conn = url.openConnection()
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = conn.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                if (bmp != null && currentVideoId == vId) {
                    currentThumbnailBitmap = bmp
                    withContext(Dispatchers.Main) {
                        updateMediaSessionMetadata()
                        updateForegroundNotification()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateMediaSessionMetadata(durationSec: Int? = null) {
        val dur = durationSec ?: YouTubePlayerManager.durationSec.value
        val durationMs = dur * 1000L

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentChannel)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "YouTube")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else -1L)

        currentThumbnailBitmap?.let { bmp ->
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState(posSec: Int? = null) {
        val pos = posSec ?: YouTubePlayerManager.currentPositionSec.value
        val posMs = pos * 1000L
        val speed = if (isPlaying) 1.0f else 0.0f

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, posMs, speed, SystemClock.elapsedRealtime())
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START, ACTION_UPDATE_META -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentChannel = intent.getStringExtra(EXTRA_CHANNEL) ?: currentChannel
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                val newVid = intent.getStringExtra(EXTRA_VIDEO_ID)
                if (newVid != null && newVid != currentVideoId) {
                    currentVideoId = newVid
                    loadThumbnailAsync(newVid)
                }

                updateMediaSessionMetadata()
                updatePlaybackState()
                updateForegroundNotification()
                if (isPlaying) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
            }

            ACTION_TOGGLE -> {
                YouTubePlayerManager.togglePlayPause(this)
            }

            ACTION_PLAY -> {
                YouTubePlayerManager.play(this)
            }

            ACTION_PAUSE -> {
                YouTubePlayerManager.pause(this)
            }

            ACTION_PREV -> {
                YouTubePlayerManager.seekBy(-10, this)
            }

            ACTION_NEXT -> {
                YouTubePlayerManager.playNextVideo(this)
            }

            ACTION_STOP -> {
                YouTubePlayerManager.stop(this)
                releaseWakeLock()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background and lock-screen audio playback controls for YouTube"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "YouTube:PlayerAudioWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(12 * 60 * 60 * 1000L) // Safe 12-hour timeout
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Intent.EXTRA_TEXT, currentVideoId?.let { "https://youtu.be/$it" })
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = Intent(this, YouTubePlayerService::class.java).apply { action = ACTION_PREV }
        val rewindPending = PendingIntent.getService(
            this, 1, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, YouTubePlayerService::class.java).apply { action = ACTION_TOGGLE }
        val togglePending = PendingIntent.getService(
            this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(this, YouTubePlayerService::class.java).apply { action = ACTION_NEXT }
        val forwardPending = PendingIntent.getService(
            this, 3, forwardIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, YouTubePlayerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle(currentTitle)
            .setContentText(currentChannel)
            .setSubText("YouTube")
            .setContentIntent(openPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        currentThumbnailBitmap?.let { bmp ->
            builder.setLargeIcon(bmp)
        }

        builder.setStyle(
            MediaNotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopPending)
        )
        .addAction(android.R.drawable.ic_media_rew, "Rewind 10s", rewindPending)
        .addAction(playPauseIcon, playPauseTitle, togglePending)
        .addAction(android.R.drawable.ic_media_ff, "Forward 10s", forwardPending)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPending)

        return builder.build()
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        thumbnailJob?.cancel()
        thumbnailJob = null
        currentThumbnailBitmap = null
        stateJob?.cancel()
        stateJob = null
        releaseWakeLock()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
