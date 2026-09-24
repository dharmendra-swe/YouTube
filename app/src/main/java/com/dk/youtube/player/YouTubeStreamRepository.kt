package com.dk.youtube.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Direct audio/video stream format metadata extracted from InnerTube.
 */
data class StreamFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val qualityLabel: String,
    val bitrate: Int,
    val isAudioOnly: Boolean,
    val isVideoOnly: Boolean
)

/**
 * Resolved stream bundle containing HLS manifest, progressive MP4s, and high-fidelity audio streams.
 */
data class VideoStreamBundle(
    val videoId: String,
    val title: String,
    val author: String,
    val durationSec: Long,
    val hlsManifestUrl: String?,
    val progressiveFormats: List<StreamFormat>,
    val audioOnlyFormats: List<StreamFormat>,
    val videoOnlyFormats: List<StreamFormat>
) {
    /**
     * Best stream for normal video playback (prefers HLS adaptive, fallback to highest progressive MP4).
     */
    val primaryVideoPlayableUrl: String?
        get() = hlsManifestUrl ?: progressiveFormats.firstOrNull()?.url

    /**
     * Best stream for Audio-Only mode (prefers highest bitrate standalone audio format).
     */
    val bestAudioUrl: String?
        get() = audioOnlyFormats.maxByOrNull { it.bitrate }?.url
            ?: progressiveFormats.firstOrNull()?.url
}

/**
 * High-performance, zero-quota InnerTube stream repository.
 * Requests unthrottled, direct media stream links from YouTube's player endpoints.
 */
object YouTubeStreamRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches raw video and audio stream links for the specified video ID.
     * Tries multiple client profiles (IOS and TV_EMBEDDED) for maximum reliability.
     */
    suspend fun fetchStreamBundle(videoId: String): VideoStreamBundle? = withContext(Dispatchers.IO) {
        // Attempt 1: IOS InnerTube Client (Returns direct HLS manifest & unthrottled formats)
        fetchFromIosClient(videoId)?.let { return@withContext it }

        // Attempt 2: TV HTML5 Embedded Client (Fallback)
        fetchFromTvClient(videoId)?.let { return@withContext it }

        // Attempt 3: Android Testsuite Client (Fallback)
        fetchFromAndroidTestSuiteClient(videoId)
    }

    private fun fetchFromIosClient(videoId: String): VideoStreamBundle? {
        return try {
            val bodyJson = JSONObject().apply {
                val context = JSONObject().apply {
                    val client = JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "19.29.1")
                        put("deviceModel", "iPhone16,2")
                        put("hl", "en")
                        put("gl", "IN")
                    }
                    put("client", client)
                }
                put("context", context)
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("User-Agent", "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
                .header("X-YouTube-Client-Name", "5")
                .header("X-YouTube-Client-Version", "19.29.1")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                parsePlayerResponse(videoId, JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromTvClient(videoId: String): VideoStreamBundle? {
        return try {
            val bodyJson = JSONObject().apply {
                val context = JSONObject().apply {
                    val client = JSONObject().apply {
                        put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                        put("clientVersion", "2.0")
                        put("hl", "en")
                        put("gl", "IN")
                    }
                    put("client", client)
                    val thirdParty = JSONObject().apply {
                        put("embedUrl", "https://www.youtube.com")
                    }
                    put("thirdParty", thirdParty)
                }
                put("context", context)
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("User-Agent", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                parsePlayerResponse(videoId, JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromAndroidTestSuiteClient(videoId: String): VideoStreamBundle? {
        return try {
            val bodyJson = JSONObject().apply {
                val context = JSONObject().apply {
                    val client = JSONObject().apply {
                        put("clientName", "ANDROID_TESTSUITE")
                        put("clientVersion", "1.9")
                        put("androidSdkVersion", 30)
                        put("hl", "en")
                        put("gl", "IN")
                    }
                    put("client", client)
                }
                put("context", context)
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("User-Agent", "com.google.android.youtube/1.9 (Linux; U; Android 11)")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                parsePlayerResponse(videoId, JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePlayerResponse(videoId: String, root: JSONObject): VideoStreamBundle? {
        val playability = root.optJSONObject("playabilityStatus")
        val status = playability?.optString("status")
        if (status != "OK") {
            return null
        }

        val videoDetails = root.optJSONObject("videoDetails")
        var title = videoDetails?.optString("title")?.takeIf { it.isNotBlank() }
        if (title == null) {
            title = root.optJSONObject("microformat")
                ?.optJSONObject("playerMicroformatRenderer")
                ?.optJSONObject("title")
                ?.optString("simpleText")
                ?.takeIf { it.isNotBlank() }
        }
        val finalTitle = title ?: ""
        val author = videoDetails?.optString("author")?.takeIf { it.isNotBlank() } ?: "YouTube Channel"
        val lengthSec = videoDetails?.optLong("lengthSeconds") ?: 0L

        val streamingData = root.optJSONObject("streamingData") ?: return null
        val hlsManifestUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }

        val progressiveList = mutableListOf<StreamFormat>()
        val audioOnlyList = mutableListOf<StreamFormat>()
        val videoOnlyList = mutableListOf<StreamFormat>()

        // Progressive formats (combined video + audio)
        val formatsArray = streamingData.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.optJSONObject(i) ?: continue
                val directUrl = extractFormatUrl(f) ?: continue
                val itag = f.optInt("itag", 0)
                val mime = f.optString("mimeType", "")
                val quality = f.optString("qualityLabel", "Standard")
                val bitrate = f.optInt("bitrate", 0)
                progressiveList.add(
                    StreamFormat(
                        itag = itag,
                        url = directUrl,
                        mimeType = mime,
                        qualityLabel = quality,
                        bitrate = bitrate,
                        isAudioOnly = false,
                        isVideoOnly = false
                    )
                )
            }
        }

        // Adaptive formats (separate video-only and audio-only)
        val adaptiveArray = streamingData.optJSONArray("adaptiveFormats")
        if (adaptiveArray != null) {
            for (i in 0 until adaptiveArray.length()) {
                val f = adaptiveArray.optJSONObject(i) ?: continue
                val directUrl = extractFormatUrl(f) ?: continue
                val itag = f.optInt("itag", 0)
                val mime = f.optString("mimeType", "")
                val quality = f.optString("qualityLabel", "")
                val bitrate = f.optInt("bitrate", 0)
                val isAudio = mime.startsWith("audio/")

                val format = StreamFormat(
                    itag = itag,
                    url = directUrl,
                    mimeType = mime,
                    qualityLabel = if (isAudio) "${bitrate / 1000} kbps" else quality,
                    bitrate = bitrate,
                    isAudioOnly = isAudio,
                    isVideoOnly = !isAudio
                )

                if (isAudio) {
                    audioOnlyList.add(format)
                } else {
                    videoOnlyList.add(format)
                }
            }
        }

        // Sort progressive by highest bitrate/quality descending
        progressiveList.sortByDescending { it.bitrate }
        audioOnlyList.sortByDescending { it.bitrate }

        if (hlsManifestUrl == null && progressiveList.isEmpty() && audioOnlyList.isEmpty()) {
            return null
        }

        return VideoStreamBundle(
            videoId = videoId,
            title = finalTitle,
            author = author,
            durationSec = lengthSec,
            hlsManifestUrl = hlsManifestUrl,
            progressiveFormats = progressiveList,
            audioOnlyFormats = audioOnlyList,
            videoOnlyFormats = videoOnlyList
        )
    }

    private fun extractFormatUrl(formatObj: JSONObject): String? {
        val rawUrl = formatObj.optString("url")
        if (rawUrl.isNotBlank()) return rawUrl

        // Check if embedded in signatureCipher or cipher
        val cipher = formatObj.optString("signatureCipher").ifBlank {
            formatObj.optString("cipher")
        }
        if (cipher.isNotBlank()) {
            try {
                val params = cipher.split("&")
                var urlParam: String? = null
                for (p in params) {
                    if (p.startsWith("url=")) {
                        urlParam = URLDecoder.decode(p.substring(4), "UTF-8")
                        break
                    }
                }
                if (!urlParam.isNullOrBlank()) return urlParam
            } catch (_: Exception) {}
        }
        return null
    }
}
