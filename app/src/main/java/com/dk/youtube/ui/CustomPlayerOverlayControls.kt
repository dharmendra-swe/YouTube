package com.dk.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.youtube.ui.theme.YouTubeRed
import kotlinx.coroutines.delay

/**
 * Fullscreen Video Overlay Controls with Auto-Hide.
 */
@Composable
fun CustomPlayerOverlayControls(
    title: String,
    isPlaying: Boolean,
    currentPositionSec: Int,
    durationSec: Int,
    isFullscreen: Boolean,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeekTo: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(controlsVisible, isPlaying, isScrubbing) {
        if (controlsVisible && isPlaying && !isScrubbing) {
            delay(3500)
            controlsVisible = false
        }
    }

    val displayPos = if (isScrubbing) scrubPos.toInt() else currentPositionSec

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onRewind, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(56.dp)
                            .background(YouTubeRed, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(onClick = onForward, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Bottom Scrubber Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(displayPos), color = Color.White, fontSize = 12.sp)
                        Text(formatDuration(durationSec), color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    }
                    Slider(
                        value = displayPos.toFloat(),
                        onValueChange = {
                            isScrubbing = true
                            scrubPos = it
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            onSeekTo(scrubPos.toInt())
                        },
                        valueRange = 0f..maxOf(durationSec.toFloat(), 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = YouTubeRed,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color(0x66FFFFFF)
                        )
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
