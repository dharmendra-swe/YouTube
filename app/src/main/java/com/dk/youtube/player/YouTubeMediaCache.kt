package com.dk.youtube.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object YouTubeMediaCache {
    private var cache: Cache? = null
    
    // 500 MB max cache size for looping/offline media
    private const val MAX_CACHE_SIZE_BYTES: Long = 500 * 1024 * 1024 

    fun getInstance(context: Context): Cache {
        if (cache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "youtube_media_cache")
            val eviction = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            cache = SimpleCache(cacheDir, eviction, databaseProvider)
        }
        return cache!!
    }

    fun release() {
        cache?.release()
        cache = null
    }
}
