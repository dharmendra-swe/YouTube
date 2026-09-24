package com.dk.youtube.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.youtube.player.VideoThumbnailImage
import com.dk.youtube.ui.theme.*

/**
 * Rotating Vinyl Disc & Audio-Only Visualizer for battery-saving playback.
 */
@Composable
fun AudioOnlyVisualizerCard(
    title: String,
    channel: String,
    isPlaying: Boolean,
    thumbnailUrl: String,
    audioQuality: String,
    currentPositionSec: Int = 0,
    durationSec: Int = 0,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Int) -> Unit = {},
    onSwitchToVideo: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(YouTubeBlack, YouTubeOledBlack)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            // Button to return to Video Mode easily
            Button(
                onClick = onSwitchToVideo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Switch to Video Mode",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Rotating Vinyl Disc
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF151515))
                    .border(2.dp, Color(0xFF333333), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1F1F))
                        .rotate(if (isPlaying) rotation else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailUrl.isNotBlank()) {
                        VideoThumbnailImage(
                            url = thumbnailUrl,
                            contentDescription = "Cover Art",
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .border(2.dp, YouTubeRed, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = YouTubeRed,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(YouTubeBlack)
                            .border(1.5.dp, Color(0xFF555555), CircleShape)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = if (title.isBlank() || title == "YouTube Ad-Free") "YouTube Music Mode" else title,
                    color = YouTubeTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (channel.isBlank() || channel == "YouTube Player") "YouTube Channel" else channel,
                    color = YouTubeTextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = "High Fidelity Audio • $audioQuality",
                    color = YouTubeRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Audio Duration Scrubber
            if (durationSec > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Slider(
                        value = currentPositionSec.toFloat().coerceIn(0f, durationSec.toFloat()),
                        onValueChange = { onSeekTo(it.toInt()) },
                        valueRange = 0f..maxOf(durationSec.toFloat(), 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = YouTubeRed,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format("%02d:%02d", currentPositionSec / 60, currentPositionSec % 60),
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                        Text(
                            text = String.format("%02d:%02d", durationSec / 60, durationSec % 60),
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Transport Controls (Previous, Rewind, Play/Pause, Forward, Next)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(46.dp)
                        .background(YouTubeSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = YouTubeTextPrimary
                    )
                }

                IconButton(
                    onClick = onRewind,
                    modifier = Modifier
                        .size(46.dp)
                        .background(YouTubeSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = YouTubeTextPrimary
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(YouTubeRed, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onForward,
                    modifier = Modifier
                        .size(46.dp)
                        .background(YouTubeSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = YouTubeTextPrimary
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(46.dp)
                        .background(YouTubeSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Song",
                        tint = YouTubeTextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Pitch black AMOLED battery saver overlay.
 */
@Composable
fun ScreenOffOverlay(
    title: String,
    channel: String,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AMOLED Pure Black Saver",
                color = Color(0xFF444444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = title,
                color = Color(0xFF666666),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Text(
                text = "Tap anywhere to wake screen",
                color = Color(0xFF333333),
                fontSize = 11.sp
            )
        }
    }
}
