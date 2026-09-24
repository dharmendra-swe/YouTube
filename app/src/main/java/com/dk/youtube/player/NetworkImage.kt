package com.dk.youtube.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val imageHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private val memoryCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount / 1024
    }
}

/**
 * Lightweight Compose Network Image component tailored for YouTube video thumbnails.
 */
@Composable
fun VideoThumbnailImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf(memoryCache.get(url)) }
    var isLoading by remember(url) { mutableStateOf(bitmap == null) }

    LaunchedEffect(url) {
        if (bitmap != null) {
            isLoading = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = imageHttpClient.newCall(request).execute()
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 2 // Downsample by 2x for memory efficiency
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bmp != null) {
                        memoryCache.put(url, bmp)
                        withContext(Dispatchers.Main) {
                            bitmap = bmp
                            isLoading = false
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF282828), Color(0xFF181818))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFF0033),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "▶", color = Color(0xFFAAAAAA), fontSize = 18.sp)
                }
            }
        }
    }
}
