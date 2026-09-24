package com.dk.youtube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun extractVideoId_standardWatchUrl_returnsId() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
    }

    @Test
    fun extractVideoId_shortUrl_returnsId() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
    }

    @Test
    fun extractVideoId_shortsUrl_returnsId() {
        val url = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
    }

    @Test
    fun extractVideoId_embedUrl_returnsId() {
        val url = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(url))
    }

    @Test
    fun extractVideoId_directId_returnsId() {
        val id = "dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(id))
    }

    @Test
    fun extractVideoId_nullOrBlank_returnsNull() {
        assertNull(YouTubeUrlParser.extractVideoId(null))
        assertNull(YouTubeUrlParser.extractVideoId(""))
        assertNull(YouTubeUrlParser.extractVideoId("   "))
    }

    @Test
    fun buildWatchUrl_constructsMobileUrl() {
        val videoId = "dQw4w9WgXcQ"
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeUrlParser.buildWatchUrl(videoId))
    }
}
