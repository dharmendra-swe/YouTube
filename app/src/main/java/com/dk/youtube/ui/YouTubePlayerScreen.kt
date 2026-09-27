package com.dk.youtube.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import com.dk.youtube.MainActivity
import com.dk.youtube.player.YouTubePlayerManager
import com.dk.youtube.player.YouTubeVideoItem
import com.dk.youtube.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fullscreen YouTube Screen with Edge-to-Edge display (No top header)
 * and a bottom-right floating circular controls button that fans out
 * 5 controls in a semicircular arc arrangement.
 */
@Composable
fun YouTubePlayerScreen(
    onEnterPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isPipMode: Boolean,
    isFullscreen: Boolean,
    onVideoSelect: (YouTubeVideoItem) -> Unit
) {
    val context = LocalContext.current

    val currentVideoId by YouTubePlayerManager.videoId.collectAsState()
    val currentTitle by YouTubePlayerManager.videoTitle.collectAsState()
    val currentChannel by YouTubePlayerManager.channelName.collectAsState()
    val isPlaying by YouTubePlayerManager.isPlaying.collectAsState()
    val isAudioOnly by YouTubePlayerManager.isAudioOnly.collectAsState()
    val currentAudioQuality by YouTubePlayerManager.audioQuality.collectAsState()
    val currentPositionSec by YouTubePlayerManager.currentPositionSec.collectAsState()
    val durationSec by YouTubePlayerManager.durationSec.collectAsState()
    val videoQuality by YouTubePlayerManager.videoQuality.collectAsState()
    val isLoadingStream by YouTubePlayerManager.isLoadingStream.collectAsState()
    val isLooping by YouTubePlayerManager.isLooping.collectAsState()
    val isLiked by YouTubePlayerManager.isLiked.collectAsState()
    val audioSessionId by YouTubePlayerManager.audioSessionId.collectAsState()
    val playbackSpeed by YouTubePlayerManager.playbackSpeed.collectAsState()
    val isSleepTimerActive by YouTubePlayerManager.isSleepTimerActive.collectAsState()
    val sleepTimerRemainingSec by YouTubePlayerManager.sleepTimerRemainingSec.collectAsState()
    val currentVideoUrl by YouTubePlayerManager.currentVideoUrl.collectAsState()
    val currentAudioUrl by YouTubePlayerManager.currentAudioUrl.collectAsState()

    var isMenuOpen by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isMenuOpen) {
            isMenuOpen = false
        } else if (isFullscreen) {
            onToggleFullscreen()
        } else {
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    // Start foreground service on first composition so background playback is available
    LaunchedEffect(Unit) {
        YouTubePlayerManager.startBackgroundService(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Primary YouTube WebView — loads m.youtube.com with full ad-blocking and background playback
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isFullscreen && !isPipMode) Modifier.statusBarsPadding() else Modifier)
                .then(if (isAudioOnly) Modifier.alpha(0f) else Modifier),
            factory = { ctx ->
                val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                
                val webView = (ctx as MainActivity).getOrCreateWebView(ctx)
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                
                swipeRefreshLayout.addView(webView)
                swipeRefreshLayout.setOnRefreshListener {
                    webView.reload()
                    swipeRefreshLayout.postDelayed({
                        swipeRefreshLayout.isRefreshing = false
                    }, 1500)
                }
                
                swipeRefreshLayout
            },
            update = { _ ->
                // WebView lifecycle managed by getOrCreateWebView + PersistentWebView
            }
        )

        // Native Fast Stream Loading Indicator
        if (isLoadingStream) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = YouTubeRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Non-PiP Overlays & Semicircular Floating Controls
        if (!isPipMode) {
            if (isAudioOnly) {
                AudioOnlyVisualizerCard(
                    title = currentTitle,
                    channel = currentChannel,
                    isPlaying = isPlaying,
                    thumbnailUrl = currentVideoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: "",
                    audioQuality = currentAudioQuality,
                    currentPositionSec = currentPositionSec,
                    durationSec = durationSec,
                    onTogglePlayPause = { YouTubePlayerManager.togglePlayPause(context) },
                    isLooping = isLooping,
                    onToggleLoop = { YouTubePlayerManager.toggleLoop() },
                    onPrevious = { YouTubePlayerManager.playPreviousVideo(context) },
                    onNext = { YouTubePlayerManager.playNextVideo(context) },
                    onSeekTo = { posSec -> YouTubePlayerManager.seekTo(posSec) },
                    onSwitchToVideo = {
                        YouTubePlayerManager.setAudioOnly(false, context)
                        Toast.makeText(context, "Switched to Video Mode", Toast.LENGTH_SHORT).show()
                    },
                    isLiked = isLiked,
                    onToggleLike = { YouTubePlayerManager.toggleLikeVideo() },
                    audioSessionId = audioSessionId,
                    playbackSpeed = playbackSpeed
                )
            } else if (isFullscreen) {
                CustomPlayerOverlayControls(
                    title = currentTitle,
                    isPlaying = isPlaying,
                    currentPositionSec = currentPositionSec,
                    durationSec = durationSec,
                    isFullscreen = true,
                    onTogglePlayPause = { YouTubePlayerManager.togglePlayPause(context) },
                    onRewind = { YouTubePlayerManager.seekBy(-10, context) },
                    onForward = { YouTubePlayerManager.seekBy(10, context) },
                    onPrevious = { YouTubePlayerManager.playPreviousVideo(context) },
                    onNext = { YouTubePlayerManager.playNextVideo(context) },
                    onSeekTo = { posSec -> YouTubePlayerManager.seekTo(posSec) },
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = { onToggleFullscreen() }
                )
            }

            // Dismiss overlay when tapping outside the open radial menu
            if (isMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isMenuOpen = false }
                )
            }

            val menuItems = listOf(
                ArcMenuItem(
                    id = "loop",
                    title = if (isLooping) "Loop: ON" else "Loop: OFF",
                    icon = Icons.Default.RepeatOne,
                    color = if (isLooping) YouTubeRed else Color(0xFF9E9E9E),
                    onClick = {
                        isMenuOpen = false
                        YouTubePlayerManager.toggleLoop()
                    }
                ),
                ArcMenuItem(
                    id = "video_quality",
                    title = "Video Quality: $videoQuality",
                    icon = Icons.Default.HighQuality,
                    color = Color(0xFFFF5722),
                    onClick = {
                        isMenuOpen = false
                        showVideoQualityDialog = true
                    }
                ),
                ArcMenuItem(
                    id = "audio_quality",
                    title = "Voice / Audio Quality: $currentAudioQuality",
                    icon = Icons.Default.GraphicEq,
                    color = Color(0xFFFFB300),
                    onClick = {
                        isMenuOpen = false
                        showAudioQualityDialog = true
                    }
                ),
                ArcMenuItem(
                    id = "mode_toggle",
                    title = if (isAudioOnly) "Switch to Video Mode" else "Switch to Music Mode",
                    icon = if (isAudioOnly) Icons.Default.MusicNote else Icons.Default.Videocam,
                    color = if (isAudioOnly) YouTubeRed else Color(0xFF3EA6FF),
                    onClick = {
                        val nextState = !isAudioOnly
                        YouTubePlayerManager.setAudioOnly(nextState, context)
                        Toast.makeText(
                            context,
                            if (nextState) "Switched to YouTube Music (Audio Only)" else "Switched to YouTube Video Mode",
                            Toast.LENGTH_SHORT
                        ).show()
                        isMenuOpen = false
                    }
                ),
                ArcMenuItem(
                    id = "sleep_timer",
                    title = if (isSleepTimerActive) "Sleep Timer: ${sleepTimerRemainingSec / 60}m remaining" else "Sleep Timer",
                    icon = Icons.Default.Bedtime,
                    color = if (isSleepTimerActive) YouTubeRed else Color(0xFF9C27B0),
                    onClick = {
                        isMenuOpen = false
                        showSleepTimerDialog = true
                    }
                ),
                ArcMenuItem(
                    id = "download",
                    title = "Download to Storage",
                    icon = Icons.Default.Download,
                    color = Color(0xFF4CAF50),
                    onClick = {
                        isMenuOpen = false
                        if (currentVideoUrl != null || currentAudioUrl != null) {
                            showDownloadDialog = true
                        } else {
                            Toast.makeText(context, "Media URL not ready yet", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            )

            // Bottom-Right Semicircular Controls Menu
            SemicircularControlsMenu(
                isMenuOpen = isMenuOpen,
                onToggleMenu = { isMenuOpen = !isMenuOpen },
                items = menuItems,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 10.dp, bottom = 60.dp)
            )
        }

        // Video Quality Selection Dialog
        if (showVideoQualityDialog) {
            QualitySelectionDialog(
                currentQuality = videoQuality,
                onSelectQuality = { q ->
                    YouTubePlayerManager.setVideoQuality(q)
                    showVideoQualityDialog = false
                    Toast.makeText(context, "Video Quality: $q", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showVideoQualityDialog = false }
            )
        }

        // Voice / Audio Quality Selection Dialog
        if (showAudioQualityDialog) {
            AudioQualitySelectionDialog(
                currentQuality = currentAudioQuality,
                onSelectQuality = { q ->
                    YouTubePlayerManager.setAudioQuality(q)
                    showAudioQualityDialog = false
                    Toast.makeText(context, "Audio Quality: $q", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showAudioQualityDialog = false }
            )
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                isActive = isSleepTimerActive,
                remainingSec = sleepTimerRemainingSec,
                onSetTimer = { mins ->
                    YouTubePlayerManager.startSleepTimer(mins)
                    showSleepTimerDialog = false
                    Toast.makeText(context, "Sleep Timer set for $mins minutes", Toast.LENGTH_SHORT).show()
                },
                onCancelTimer = {
                    YouTubePlayerManager.cancelSleepTimer()
                    showSleepTimerDialog = false
                    Toast.makeText(context, "Sleep Timer cancelled", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        // True Download Dialog
        if (showDownloadDialog) {
            DownloadDialog(
                hasVideoUrl = currentVideoUrl != null,
                hasAudioUrl = currentAudioUrl != null,
                onDownload = { isAudio ->
                    val url = if (isAudio) currentAudioUrl else currentVideoUrl
                    if (url != null) {
                        YouTubePlayerManager.downloadMedia(context, url, currentTitle, isAudio)
                    }
                    showDownloadDialog = false
                },
                onDismiss = { showDownloadDialog = false }
            )
        }
    }
}

private data class ArcMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

/**
 * Semicircular / Radial Arc Menu:
 * The bottom-right trigger button features a player controls icon.
 * When clicked, 4-5 buttons fan out in a circular/semicircular arc into the screen.
 */
@Composable
private fun SemicircularControlsMenu(
    isMenuOpen: Boolean,
    onToggleMenu: () -> Unit,
    items: List<ArcMenuItem>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val radiusDp = 125.dp
    val radiusPx = with(density) { radiusDp.toPx() }

    val transitionProgress by animateFloatAsState(
        targetValue = if (isMenuOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "arcMenuTransition"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // Semicircular Arc Action Buttons
        if (transitionProgress > 0.01f) {
            val stepAngle = if (items.size > 1) 90f / (items.size - 1) else 0f
            items.forEachIndexed { index, item ->
                // angleDeg = 0 deg (straight UP) to 90 deg (straight LEFT)
                val angleDeg = index * stepAngle
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val currentRadius = radiusPx * transitionProgress
                val xOffset = (-currentRadius * sin(angleRad)).toInt()
                val yOffset = (-currentRadius * cos(angleRad)).toInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOffset, yOffset) }
                        .alpha(transitionProgress)
                        .scale(0.35f + 0.65f * transitionProgress)
                        .size(46.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
                        .border(1.4.dp, item.color.copy(alpha = 0.9f), CircleShape)
                        .clickable(enabled = isMenuOpen, onClick = item.onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom-Right Trigger Button with Player Controls Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(elevation = 10.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.52f))
                .border(
                    width = 1.5.dp,
                    color = if (isMenuOpen) YouTubeRed else Color.White.copy(alpha = 0.45f),
                    shape = CircleShape
                )
                .clickable(onClick = onToggleMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMenuOpen) Icons.Default.Close else Icons.Default.PlayCircle,
                contentDescription = "Player Controls Menu",
                tint = if (isMenuOpen) YouTubeRed else Color.White.copy(alpha = 0.95f),
                modifier = Modifier
                    .size(35.dp)
                    .rotate(if (isMenuOpen) 90f * transitionProgress else 0f)
            )
        }
    }
}

/**
 * Voice / Audio Quality Selection Dialog.
 */
@Composable
fun AudioQualitySelectionDialog(
    currentQuality: String,
    onSelectQuality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val qualities = listOf(
        "320 kbps (Ultra High)",
        "256 kbps (High Quality)",
        "160 kbps (Standard)",
        "96 kbps (Data Saver)",
        "Auto"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YouTubeSurface,
        title = {
            Text("Select Voice / Audio Quality", color = YouTubeTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                qualities.forEach { q ->
                    val isSelected = currentQuality.contains(q.take(6)) || (q == "Auto" && currentQuality == "Auto") || (currentQuality in q)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectQuality(q) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = q,
                            color = if (isSelected) YouTubeRed else YouTubeTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = YouTubeRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = YouTubeTextSecondary)
            }
        }
    )
}

/**
 * Video Quality Selection Modal Dialog.
 */
@Composable
fun QualitySelectionDialog(
    currentQuality: String,
    onSelectQuality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val qualities = listOf("1080p", "720p", "480p", "360p", "240p", "Auto")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YouTubeSurface,
        title = {
            Text("Select Video Quality", color = YouTubeTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                qualities.forEach { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectQuality(q) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = q,
                            color = if (q.equals(currentQuality, ignoreCase = true)) YouTubeRed else YouTubeTextPrimary,
                            fontWeight = if (q.equals(currentQuality, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                        )
                        if (q.equals(currentQuality, ignoreCase = true)) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = YouTubeRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = YouTubeTextSecondary)
            }
        }
    )
}

/**
 * Sleep Timer Dialog.
 */
@Composable
fun SleepTimerDialog(
    isActive: Boolean,
    remainingSec: Int,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val times = listOf(15, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YouTubeSurface,
        title = {
            Text("Sleep Timer", color = YouTubeTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (isActive) {
                    Text(
                        text = "Time Remaining: ${remainingSec / 60}m ${remainingSec % 60}s",
                        color = YouTubeRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        text = "Stop audio automatically after:",
                        color = YouTubeTextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                times.forEach { mins ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetTimer(mins) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$mins Minutes",
                            color = YouTubeTextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isActive) {
                TextButton(onClick = onCancelTimer) {
                    Text("Turn Off Timer", color = YouTubeRed)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = YouTubeTextSecondary)
            }
        }
    )
}

/**
 * True Download Dialog.
 */
@Composable
fun DownloadDialog(
    hasVideoUrl: Boolean,
    hasAudioUrl: Boolean,
    onDownload: (Boolean) -> Unit, // true for Audio, false for Video
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YouTubeSurface,
        title = {
            Text("Download to Storage", color = YouTubeTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Save this media permanently to your device's Downloads folder.",
                    color = YouTubeTextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (hasVideoUrl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDownload(false) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF3EA6FF))
                        Spacer(Modifier.width(16.dp))
                        Text("Download Video (MP4)", color = YouTubeTextPrimary)
                    }
                }
                
                if (hasAudioUrl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDownload(true) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = YouTubeRed)
                        Spacer(Modifier.width(16.dp))
                        Text("Download Audio (M4A)", color = YouTubeTextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = YouTubeTextSecondary)
            }
        }
    )
}
