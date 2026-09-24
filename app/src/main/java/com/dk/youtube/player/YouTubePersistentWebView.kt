package com.dk.youtube.player

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * Custom Persistent WebView that prevents Chromium media engine suspension when
 * the activity is backgrounded or device screen is locked.
 */
@SuppressLint("ViewConstructor")
class YouTubePersistentWebView(context: Context) : WebView(context) {

    override fun onPause() {
        // Crucial: DO NOT call super.onPause().
        // Calling super.onPause() commands Chromium to pause HTML5 audio/video playback.
        // Leaving this empty allows uninterrupted background playback.
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Force View.VISIBLE to prevent Chromium from pausing when screen is locked or in PiP
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(true)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }

    override fun onDetachedFromWindow() {
        // Don't call super — prevents Chromium from stopping media on Activity destroy/recreate
    }
}
