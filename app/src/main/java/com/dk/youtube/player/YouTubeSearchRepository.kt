package com.dk.youtube.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Model representing a YouTube video item for search and Up Next queues.
 */
data class YouTubeVideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val duration: String,
    val thumbnailUrl: String = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    val audioQuality: String = "256 kbps"
)

/**
 * Repository querying YouTube's official InnerTube client endpoints directly
 * without requiring any Google Cloud API keys, authorization tokens, or daily quota limits.
 */
object YouTubeSearchRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Intelligently flags high-fidelity audio capability.
     */
    fun resolveAudioQuality(title: String, channel: String): String {
        val lowerTitle = title.lowercase()
        val lowerChannel = channel.lowercase()
        val isMusic = lowerChannel.contains("topic") ||
                lowerChannel.contains("vevo") ||
                lowerChannel.contains("music") ||
                lowerChannel.contains("records") ||
                lowerChannel.contains("sound") ||
                lowerChannel.contains("series") ||
                lowerChannel.contains("audio") ||
                lowerTitle.contains("song") ||
                lowerTitle.contains("music") ||
                lowerTitle.contains("official video") ||
                lowerTitle.contains("official audio") ||
                lowerTitle.contains("lyric") ||
                lowerTitle.contains("remix") ||
                lowerTitle.contains("audio") ||
                lowerTitle.contains("album") ||
                lowerTitle.contains("feat.") ||
                lowerTitle.contains("ft.") ||
                lowerTitle.contains("acoustic") ||
                lowerTitle.contains("lofi") ||
                lowerTitle.contains("track")
        return if (isMusic) "256 kbps" else "160 kbps"
    }

    /**
     * Search YouTube videos for a given query or direct video link.
     */
    suspend fun searchVideos(query: String): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // Check if user directly pasted a video link or ID
        val directId = YouTubeUrlParser.extractVideoId(trimmed)
        if (directId != null && directId.length == 11) {
            return@withContext listOf(
                YouTubeVideoItem(
                    id = directId,
                    title = "Selected Video ($directId)",
                    channel = "Direct Link",
                    duration = "--:--",
                    audioQuality = "256 kbps"
                )
            )
        }

        val postJson = JSONObject().apply {
            val contextObj = JSONObject().apply {
                val clientObj = JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230515.00.00")
                    put("hl", "en")
                    put("gl", "IN")
                }
                put("client", clientObj)
            }
            put("context", contextObj)
            put("query", trimmed)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .post(postJson.toString().toRequestBody(jsonMediaType))
            .build()

        val results = mutableListOf<YouTubeVideoItem>()

        try {
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(bodyString)

            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i)
                val itemSection = section?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue

                for (j in 0 until itemSection.length()) {
                    val item = itemSection.optJSONObject(j) ?: continue
                    val vr = item.optJSONObject("videoRenderer") ?: continue

                    val videoId = vr.optString("videoId")
                    if (videoId.length != 11) continue

                    // Parse Title
                    val titleObj = vr.optJSONObject("title")
                    val titleRuns = titleObj?.optJSONArray("runs")
                    val title = if (titleRuns != null && titleRuns.length() > 0) {
                        titleRuns.optJSONObject(0)?.optString("text") ?: ""
                    } else {
                        titleObj?.optString("simpleText") ?: "YouTube Video"
                    }

                    // Parse Channel Name
                    val ownerObj = vr.optJSONObject("ownerText")
                    val ownerRuns = ownerObj?.optJSONArray("runs")
                    val channel = if (ownerRuns != null && ownerRuns.length() > 0) {
                        ownerRuns.optJSONObject(0)?.optString("text") ?: ""
                    } else {
                        "YouTube Channel"
                    }

                    // Parse Duration
                    val lengthObj = vr.optJSONObject("lengthText")
                    val duration = lengthObj?.optString("simpleText") ?: ""

                    results.add(
                        YouTubeVideoItem(
                            id = videoId,
                            title = title,
                            channel = channel,
                            duration = duration,
                            audioQuality = resolveAudioQuality(title, channel)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Network / parsing error fallback
        }

        return@withContext results
    }

    /**
     * Fetches algorithmically relevant related / Up Next videos for the specified video ID
     * directly from YouTube's recommendations algorithm (/youtubei/v1/next).
     */
    suspend fun getUpNextRelatedVideos(videoId: String, fallbackQuery: String? = null): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        if (videoId.length != 11) {
            return@withContext if (!fallbackQuery.isNullOrBlank()) searchVideos(fallbackQuery) else emptyList()
        }

        val postJson = JSONObject().apply {
            val contextObj = JSONObject().apply {
                val clientObj = JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230515.00.00")
                    put("hl", "en")
                    put("gl", "IN")
                }
                put("client", clientObj)
            }
            put("context", contextObj)
            put("videoId", videoId)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/next")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .post(postJson.toString().toRequestBody(jsonMediaType))
            .build()

        val results = mutableListOf<YouTubeVideoItem>()

        try {
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(bodyString)

            val secondaryResults = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONArray("results")

            if (secondaryResults != null) {
                for (i in 0 until secondaryResults.length()) {
                    val item = secondaryResults.optJSONObject(i) ?: continue

                    // 1. Check lockupViewModel (YouTube Modern Web format)
                    val lvm = item.optJSONObject("lockupViewModel")
                    if (lvm != null) {
                        var vId = lvm.optString("contentId")
                        if (vId.length != 11) {
                            val cmd = lvm.optJSONObject("rendererContext")
                                ?.optJSONObject("commandContext")
                                ?.optJSONObject("onTap")
                                ?.optJSONObject("innertubeCommand")
                            vId = cmd?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                        }

                        if (vId.length == 11 && vId != videoId) {
                            val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                            val title = meta?.optJSONObject("title")?.optString("content") ?: ""

                            val rows = meta?.optJSONObject("metadata")
                                ?.optJSONObject("contentMetadataViewModel")
                                ?.optJSONArray("metadataRows")
                            var channel = ""
                            if (rows != null && rows.length() > 0) {
                                val parts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                                if (parts != null && parts.length() > 0) {
                                    channel = parts.optJSONObject(0)?.optJSONObject("text")?.optString("content") ?: ""
                                }
                            }

                            if (title.isNotBlank()) {
                                val channelName = if (channel.isNotBlank()) channel else "YouTube"
                                results.add(
                                    YouTubeVideoItem(
                                        id = vId,
                                        title = title,
                                        channel = channelName,
                                        duration = "",
                                        audioQuality = resolveAudioQuality(title, channelName)
                                    )
                                )
                            }
                        }
                    }

                    // 2. Check compactVideoRenderer (Classic format)
                    val cvr = item.optJSONObject("compactVideoRenderer")
                    if (cvr != null) {
                        val vId = cvr.optString("videoId")
                        if (vId.length == 11 && vId != videoId) {
                            val titleObj = cvr.optJSONObject("title")
                            val titleRuns = titleObj?.optJSONArray("runs")
                            val title = if (titleRuns != null && titleRuns.length() > 0) {
                                titleRuns.optJSONObject(0)?.optString("text") ?: ""
                            } else {
                                titleObj?.optString("simpleText") ?: ""
                            }

                            val ownerObj = cvr.optJSONObject("shortBylineText")
                            val ownerRuns = ownerObj?.optJSONArray("runs")
                            val channel = if (ownerRuns != null && ownerRuns.length() > 0) {
                                ownerRuns.optJSONObject(0)?.optString("text") ?: ""
                            } else {
                                "YouTube"
                            }

                            val lengthObj = cvr.optJSONObject("lengthText")
                            val duration = lengthObj?.optString("simpleText") ?: ""

                            if (title.isNotBlank()) {
                                results.add(
                                    YouTubeVideoItem(
                                        id = vId,
                                        title = title,
                                        channel = channel,
                                        duration = duration,
                                        audioQuality = resolveAudioQuality(title, channel)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (results.isEmpty() && !fallbackQuery.isNullOrBlank()) {
            return@withContext searchVideos(fallbackQuery)
        }

        return@withContext results
    }
}
