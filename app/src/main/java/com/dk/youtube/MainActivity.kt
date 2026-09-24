package com.dk.youtube

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dk.youtube.player.*
import com.dk.youtube.ui.YouTubePlayerScreen
import com.dk.youtube.ui.theme.YouTubeTheme

class MainActivity : ComponentActivity(), YouTubePlayerManager.PlayerController {

    private var isPipActive = mutableStateOf(false)
    private var isFullscreenMode = mutableStateOf(false)

    // JavaScript to Android bridge for synchronized playback states & seekbar
    inner class AndroidPlayerBridge {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun onStateChange(state: Int) {
            mainHandler.post {
                val isPlaying = (state == 1)
                YouTubePlayerManager.updatePlaybackState(
                    playing = isPlaying,
                    vId = YouTubePlayerManager.videoId.value
                )
            }
        }

        @JavascriptInterface
        fun onVideoReady(title: String, author: String, durationSec: Float) {
            mainHandler.post {
                YouTubePlayerManager.updatePlaybackState(
                    playing = true,
                    title = if (title.isNotBlank()) title else null,
                    channel = if (author.isNotBlank()) author else null,
                    duration = durationSec.toInt()
                )
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentSec: Int, durationSec: Int) {
            mainHandler.post {
                YouTubePlayerManager.updatePlaybackState(
                    playing = YouTubePlayerManager.isPlaying.value,
                    duration = if (durationSec > 0) durationSec else null,
                    currentPos = currentSec
                )
            }
        }

        @JavascriptInterface
        fun onVideoEnded() {
            mainHandler.post {
                if (YouTubePlayerManager.isAutoplayEnabled.value) {
                    val played = YouTubePlayerManager.playNextVideo(applicationContext)
                    if (played) {
                        Toast.makeText(applicationContext, "Autoplaying next video...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onApiReady() {
            mainHandler.post {
                YouTubePlayerManager.isPlayerInitialized = true
            }
        }

        @JavascriptInterface
        fun onVideoNavigated(vId: String, title: String) {
            mainHandler.post {
                if (vId.isNotBlank() && vId != YouTubePlayerManager.videoId.value) {
                    YouTubePlayerManager.currentLoadedVideoId = vId
                    YouTubePlayerManager.updatePlaybackState(
                        playing = true,
                        vId = vId,
                        title = if (title.isNotBlank()) title else null
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Android 12+ smooth auto-PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .build()
                setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }

        YouTubePlayerManager.registerController(this)

        val incomingId = parseVideoIdFromIntent(intent)
        if (incomingId != null) {
            YouTubePlayerManager.loadVideo(
                id = incomingId,
                title = "Loading Video...",
                channel = "YouTube",
                context = this
            )
        }

        setContent {
            YouTubeTheme {
                val context = LocalContext.current
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotificationPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasNotificationPerm) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                YouTubePlayerScreen(
                    onEnterPip = { enterPipMode() },
                    onToggleFullscreen = { toggleFullscreen() },
                    isPipMode = isPipActive.value,
                    isFullscreen = isFullscreenMode.value,
                    onVideoSelect = { item ->
                        YouTubePlayerManager.loadVideo(
                            id = item.id,
                            title = item.title,
                            channel = item.channel,
                            audioQuality = item.audioQuality,
                            context = this
                        )
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newVideoId = parseVideoIdFromIntent(intent)
        if (newVideoId != null) {
            YouTubePlayerManager.loadVideo(newVideoId, context = this)
        }
    }

    private fun parseVideoIdFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val fromText = YouTubeUrlParser.extractVideoId(sharedText)
        if (fromText != null) return fromText

        val dataUri = intent.data?.toString()
        val fromData = YouTubeUrlParser.extractVideoId(dataUri)
        if (fromData != null) return fromData

        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(context: Context): WebView {
        YouTubePlayerManager.persistentWebView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            val currentId = YouTubePlayerManager.videoId.value
            if (currentId != null && currentId != YouTubePlayerManager.currentLoadedVideoId) {
                loadVideo(currentId)
            }
            return existing
        }

        val wv = YouTubePersistentWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Clean mobile Chrome UA without '; wv' for native HTML5 video performance
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }

            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(AndroidPlayerBridge(), "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                    return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("intent://") ||
                        url.startsWith("vnd.youtube") ||
                        url.startsWith("market://") ||
                        url.contains("mweb_c3_open_app") ||
                        url.contains("redirect_app_store")
                    ) {
                        return true
                    }
                    if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        val newVid = YouTubeUrlParser.extractVideoId(url)
                        if (newVid != null && newVid != YouTubePlayerManager.videoId.value) {
                            YouTubePlayerManager.currentLoadedVideoId = newVid
                            YouTubePlayerManager.updatePlaybackState(
                                playing = true,
                                vId = newVid,
                                title = "Loading Video...",
                                channel = "YouTube"
                            )
                        }
                        return false
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    YouTubeAdBlocker.injectPlayerCss(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    YouTubeAdBlocker.injectPlayerCss(view)
                    YouTubeAdBlocker.injectPlayerScript(view)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val adBlocked = YouTubeAdBlocker.shouldInterceptRequest(request)
                    if (adBlocked != null) return adBlocked
                    return super.shouldInterceptRequest(view, request)
                }
            }
        }
        YouTubePlayerManager.persistentWebView = wv

        val currentId = YouTubePlayerManager.videoId.value
        if (currentId != null && currentId != YouTubePlayerManager.currentLoadedVideoId) {
            loadVideo(currentId)
        } else if (currentId == null && YouTubePlayerManager.currentLoadedVideoId == null) {
            wv.loadUrl("https://m.youtube.com/")
        }
        return wv
    }

    override fun play() {
        YouTubePlayerManager.play(this)
    }

    override fun pause() {
        YouTubePlayerManager.pause(this)
    }

    override fun seekBy(seconds: Int) {
        YouTubePlayerManager.seekBy(seconds, this)
    }

    override fun seekTo(seconds: Int) {
        YouTubePlayerManager.seekTo(seconds)
    }

    override fun loadVideo(id: String) {
        YouTubePlayerManager.loadVideo(id = id, context = this)
    }

    override fun stop() {
        YouTubePlayerManager.stop(this)
    }

    private fun applyPipMode(inPip: Boolean) {
        // Native Media3 PlayerView automatically scales and fits in PiP window without DOM scripts
    }

    private fun enterPipMode() {
        if (YouTubePlayerManager.videoId.value == null && !YouTubePlayerManager.isPlaying.value) {
            Toast.makeText(this, "Please play a video to use Picture-in-Picture", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                Toast.makeText(this, "Picture-in-Picture not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFullscreen() {
        isFullscreenMode.value = !isFullscreenMode.value
        requestedOrientation = if (isFullscreenMode.value) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Start foreground service to keep audio playing when app goes to background
        YouTubePlayerManager.startBackgroundService(this)
        if (!YouTubePlayerManager.isAudioOnly.value && YouTubePlayerManager.isPlaying.value) {
            enterPipMode()
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure foreground service is running when Activity goes to background/screen off
        // This keeps PARTIAL_WAKE_LOCK alive and notification visible for audio playback
        if (YouTubePlayerManager.isPlaying.value || YouTubePlayerManager.isAudioOnly.value) {
            YouTubePlayerManager.startBackgroundService(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // Do not pause the WebView manually here, let YouTubePersistentWebView handle it
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipActive.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        YouTubePlayerManager.unregisterController(this)
        if (isFinishing && !YouTubePlayerManager.isBackgroundPlayEnabled.value) {
            YouTubePlayerManager.releasePlayer()
        }
        super.onDestroy()
    }
}
