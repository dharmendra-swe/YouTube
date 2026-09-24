package com.dk.youtube.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.youtube.player.VideoThumbnailImage
import com.dk.youtube.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotating Vinyl Disc & Audio-Only Visualizer for battery-saving playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    isLooping: Boolean,
    onToggleLoop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Int) -> Unit = {},
    onSwitchToVideo: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {}
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

    val volumePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VolumePulse"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.03f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingScale"
    )

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // Using a local state to immediately update UI on click, synced with parent's isLiked initially
    var localIsLiked by remember(isLiked) { mutableStateOf(isLiked) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Blurred Album Art Backdrop
        if (thumbnailUrl.isNotBlank()) {
            VideoThumbnailImage(
                url = thumbnailUrl,
                contentDescription = "Background",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .background(Color.Black.copy(alpha = 0.75f)), // 25% opacity effect via black overlay
                contentScale = ContentScale.Crop
            )
        }

        // Overlay Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(vertical = 32.dp)
        ) {
            // Frosted Glass Pill for "Switch to Video Mode"
            Surface(
                onClick = onSwitchToVideo,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
            }

            Spacer(Modifier.weight(1f))

            // Circular Visualizer & Vinyl Disc
            Box(contentAlignment = Alignment.Center) {
                // Canvas Waveform Rings
                Canvas(modifier = Modifier.size(300.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2 * 0.7f
                    val barCount = 45
                    for (i in 0 until barCount) {
                        val angle = (i * 360f / barCount).toDouble()
                        val angleRad = Math.toRadians(angle)
                        val baseLength = 8.dp.toPx()
                        // Dynamic amplitude based on fake beat and volumePulse
                        val amplitude = if (isPlaying) (sin(angleRad * 4 + Math.toRadians(rotation.toDouble())) * 20.dp.toPx() * volumePulse).toFloat() else 0f
                        val barLength = baseLength + abs(amplitude)

                        val startX = center.x + radius * cos(angleRad).toFloat()
                        val startY = center.y + radius * sin(angleRad).toFloat()

                        val endX = center.x + (radius + barLength) * cos(angleRad).toFloat()
                        val endY = center.y + (radius + barLength) * sin(angleRad).toFloat()

                        drawLine(
                            brush = Brush.radialGradient(
                                colors = listOf(YouTubeRed, Color(0xFFFF8800).copy(alpha = 0.6f), Color.Transparent),
                                center = center,
                                radius = size.width / 2
                            ),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Breathing Vinyl Disc
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(breathingScale)
                        .clip(CircleShape)
                        .background(Color(0xFF111111))
                        .border(2.dp, Color(0xFF222222), CircleShape),
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
                                    .clip(CircleShape),
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
                        
                        // Inner Vinyl Groove Realism
                        Box(modifier = Modifier.size(200.dp).border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape))
                        Box(modifier = Modifier.size(170.dp).border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape))
                        Box(modifier = Modifier.size(140.dp).border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape))

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0A0A0A))
                                .border(1.dp, Color(0xFF333333), CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Info Section (Left Aligned)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (title.isBlank() || title == "YouTube Ad-Free") "YouTube Music Mode" else title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (channel.isBlank() || channel == "YouTube Player") "YouTube Channel" else channel,
                        color = Color(0xFFB0B0B0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    // Refined Badge
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Audio • $audioQuality",
                            color = YouTubeRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        localIsLiked = !localIsLiked
                        Toast.makeText(context, if (localIsLiked) "Liked music" else "Removed from Liked music", Toast.LENGTH_SHORT).show()
                        onToggleLike()
                    }
                ) {
                    Icon(
                        imageVector = if (localIsLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (localIsLiked) YouTubeRed else Color.White
                    )
                }
            }

            // Scrubber
            if (durationSec > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = YouTubeRed,
                        activeTrackColor = YouTubeRed,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )

                    val interactionSource = remember { MutableInteractionSource() }
                    val isDragged by interactionSource.collectIsDraggedAsState()

                    Slider(
                        value = currentPositionSec.toFloat().coerceIn(0f, durationSec.toFloat()),
                        onValueChange = { onSeekTo(it.toInt()) },
                        valueRange = 0f..maxOf(durationSec.toFloat(), 1f),
                        colors = sliderColors,
                        interactionSource = interactionSource,
                        track = { sliderState ->
                            SliderDefaults.Track(
                                colors = sliderColors,
                                sliderState = sliderState,
                                modifier = Modifier.height(4.dp),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null
                            )
                        },
                        thumb = {
                            if (isDragged) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(color = YouTubeRed, shape = CircleShape)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format("%02d:%02d", currentPositionSec / 60, currentPositionSec % 60),
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format("%02d:%02d", durationSec / 60, durationSec % 60),
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Balanced 5-Button Transport Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* TODO Shuffle */ }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPrevious()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                val playPauseInteractionSource = remember { MutableInteractionSource() }
                val isPressed by playPauseInteractionSource.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "PlayScale"
                )

                LaunchedEffect(isPressed) {
                    if (isPressed) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(playScale)
                        .background(YouTubeRed, CircleShape)
                        // subtle red glow
                        .border(1.dp, Color(0x44FF0000), CircleShape)
                        .clickable(
                            interactionSource = playPauseInteractionSource,
                            indication = null,
                            onClick = onTogglePlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleLoop()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isLooping) YouTubeRed else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * Pitch black AMOLED battery saver overlay.
 * Ambient Mode / Always-On-Display (AOD) Layout
 */
@Composable
fun ScreenOffOverlay(
    title: String,
    channel: String,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = formatter.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Ambient Clock
            Text(
                text = currentTime,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Compact Song Title
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Text(
                text = channel,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp, bottom = 48.dp)
            )

            // Minimalist Wave Line
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val midY = canvasHeight / 2
                
                val path = Path()
                path.moveTo(0f, midY)
                
                val waveAmplitude = if (isPlaying) 15f else 2f
                val frequency = 0.02f
                
                for (x in 0..canvasWidth.toInt() step 5) {
                    val y = midY + sin(x * frequency + wavePhase) * waveAmplitude
                    path.lineTo(x.toFloat(), y)
                }
                
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        
        // Bottom instruction
        Text(
            text = "Tap anywhere to wake",
            color = Color(0xFF333333),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
