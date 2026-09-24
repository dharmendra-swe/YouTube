package com.dk.youtube.player

import java.util.regex.Pattern

/**
 * Utility to extract YouTube Video IDs from diverse URL formats and shared text snippets.
 */
object YouTubeUrlParser {

    private val VIDEO_ID_PATTERNS = listOf(
        // youtu.be/VIDEO_ID
        Pattern.compile("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
        // youtube.com/watch?v=VIDEO_ID
        Pattern.compile("""[?&]v=([a-zA-Z0-9_-]{11})"""),
        // youtube.com/shorts/VIDEO_ID
        Pattern.compile("""youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
        // youtube.com/embed/VIDEO_ID or youtube-nocookie.com/embed/VIDEO_ID
        Pattern.compile("""(?:youtube\.com|youtube-nocookie\.com)/embed/([a-zA-Z0-9_-]{11})"""),
        // youtube.com/v/VIDEO_ID or youtube-nocookie.com/v/VIDEO_ID
        Pattern.compile("""(?:youtube\.com|youtube-nocookie\.com)/v/([a-zA-Z0-9_-]{11})""")
    )

    /**
     * Extracts an 11-character YouTube video ID from a shared string or URL.
     * Returns null if no valid ID could be identified.
     */
    fun extractVideoId(text: String?): String? {
        if (text.isNullOrBlank()) return null

        for (pattern in VIDEO_ID_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val id = matcher.group(1)
                if (id != null && id.length == 11) {
                    return id
                }
            }
        }

        // Direct 11-character video ID fallback
        val trimmed = text.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return trimmed
        }

        return null
    }

    /**
     * Builds standard mobile web watch URL.
     */
    fun buildWatchUrl(videoId: String): String {
        return "https://m.youtube.com/watch?v=$videoId"
    }
}
