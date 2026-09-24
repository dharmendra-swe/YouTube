package com.dk.youtube.player

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

/**
 * Multi-layer Ad Shielding & In-Stream Fast-Forward Engine:
 * Layer 1: Network interception of ad domains and tracking scripts.
 * Layer 2: 16x speed fast-forward skip of in-stream video ads.
 * Layer 3: Dynamic CSS cleanup of upsell banners and popups.
 */
object YouTubeAdBlocker {

    /**
     * Intercepts network calls made by WebView. Returns a 0-byte response for ad trackers.
     */
    fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? {
        val host = request?.url?.host?.lowercase() ?: return null

        // 1. Unconditionally allow essential media, static, and API streams
        if (host.endsWith("googlevideo.com") ||
            host.endsWith("youtube.com") ||
            host.endsWith("youtube-nocookie.com") ||
            host.endsWith("ytimg.com") ||
            host.endsWith("gstatic.com") ||
            host.endsWith("google.com") ||
            host.endsWith("googleapis.com") ||
            host.endsWith("ggpht.com") ||
            host.endsWith("gvt1.com") ||
            host.endsWith("android.com")
        ) {
            return null // Let default webview client handle it
        }

        // 2. Drop external ad and tracking networks with zero latency
        if (host.contains("doubleclick.net") ||
            host.contains("googleads") ||
            host.contains("adservice.google.com") ||
            host.contains("pagead2.googlesyndication.com")
        ) {
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }

        return null
    }

    /**
     * Injects CSS to hide "Open App" / "Open in App" header buttons, popups, and promotional banners.
     */
    fun injectPlayerCss(webView: WebView?) {
        val css = """

            .mobile-topbar-header-sign-in-button,
            .mobile-topbar-header-sign-in-button *,
            .mobile-topbar-header-content .mobile-topbar-header-sign-in-button,
            ytm-mobile-topbar-renderer .mobile-topbar-header-sign-in-button,
            #header-bar .mobile-topbar-header-sign-in-button,
            #header-bar .mobile-topbar-header-content .mobile-topbar-header-sign-in-button,
            header.mobile-topbar-header .mobile-topbar-header-sign-in-button,
            .icon-avatar_logged_out,
            ytm-button-renderer.icon-avatar_logged_out,
            ytm-button-renderer.icon-avatar_logged_out *,
            [aria-label="Open App"],
            [aria-label="Open in App"],
            [aria-label*="Open App" i],
            [aria-label*="Open in App" i],
            a[href*="vnd.youtube"],
            a[href*="vnd.youtube"] *,
            a[href*="mweb_c3_open_app"],
            a[href*="mweb_c3_open_app"] *,
            a[href*="redirect_app_store"],
            a[href*="redirect_app_store"] *,
            a.ytSpecButtonShapeNextHost[href*="vnd.youtube"],
            a.ytSpecButtonShapeNextHost[aria-label="Open App"],
            .yt-spec-banner-promo,
            .eom-snack-bar,
            ytm-upsell-dialog-renderer,
            ytm-mealbar-promo-renderer,
            ytm-consent-bump-v2-renderer,
            ytm-pivot-bar-renderer [aria-label*="Open App" i],
            ytm-pivot-bar-renderer [aria-label*="Open in App" i],
            .mealbar-promo-renderer,
            .banner-promo-renderer {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
                width: 0 !important;
                height: 0 !important;
                max-width: 0 !important;
                max-height: 0 !important;
                min-width: 0 !important;
                min-height: 0 !important;
                overflow: hidden !important;
                position: absolute !important;
                clip: rect(0, 0, 0, 0) !important;
                margin: 0 !important;
                padding: 0 !important;
                border: 0 !important;
                font-size: 0 !important;
                line-height: 0 !important;
            }
        """.trimIndent().replace("\n", " ").replace("\"", "\\\"")

        val js = """
            (function() {
                var style = document.getElementById('youtube-clean-css');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'youtube-clean-css';
                    (document.head || document.documentElement).appendChild(style);
                }
                style.textContent = "$css";
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    /**
     * Injects the two-way JavaScript bridge and continuous 500ms in-stream ad fast-forwarder.
     */
    fun injectPlayerScript(webView: WebView?) {
        val js = """
            (function() {
                // Prevent YouTube from auto-pausing on window blur, visibilitychange, or PiP mode
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                } catch(e) {}

                window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
                window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);

                // Continuously eradicate any Open App buttons and promotional banners from DOM
                function purgeOpenAppElements() {
                    var selectors = [
                        '.mobile-topbar-header-sign-in-button',
                        'ytm-button-renderer.icon-avatar_logged_out',
                        '[aria-label="Open App"]',
                        '[aria-label="Open in App"]',
                        '[aria-label*="Open App" i]',
                        '[aria-label*="Open in App" i]',
                        'a[href*="vnd.youtube"]',
                        'a[href*="mweb_c3_open_app"]',
                        'a[href*="redirect_app_store"]',
                        'a.ytSpecButtonShapeNextHost[href*="vnd.youtube"]',
                        'ytm-mealbar-promo-renderer',
                        'ytm-upsell-dialog-renderer',
                        'ytm-consent-bump-v2-renderer'
                    ];
                    selectors.forEach(function(sel) {
                        try {
                            var nodes = document.querySelectorAll(sel);
                            for (var i = 0; i < nodes.length; i++) {
                                try { nodes[i].remove(); } catch(e) {}
                            }
                        } catch(e) {}
                    });
                    // Also scan for any span with text "Open App" and destroy its ancestor button/link
                    try {
                        var spans = document.querySelectorAll('span[role="text"]');
                        for (var j = 0; j < spans.length; j++) {
                            var txt = (spans[j].textContent || '').trim();
                            if (txt === 'Open App' || txt === 'Open in App') {
                                var ancestor = spans[j].closest('a, button, ytm-button-renderer, .mobile-topbar-header-sign-in-button');
                                if (ancestor) { try { ancestor.remove(); } catch(e) {} }
                            }
                        }
                    } catch(e) {}
                }
                purgeOpenAppElements();
                setInterval(purgeOpenAppElements, 400);
                if (window.MutationObserver) {
                    try {
                        var obs = new MutationObserver(purgeOpenAppElements);
                        obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
                    } catch(e) {}
                }

                function getCleanTitle() {
                    var el = document.querySelector('h2.slim-video-metadata-title, h1.title, .slim-video-information-title, ytm-slim-video-metadata-section-renderer .title');
                    if (el && el.textContent && el.textContent.trim()) {
                        return el.textContent.trim();
                    }
                    var t = document.title ? document.title.replace(/^\(\d+\)\s*/, '').replace(/ - YouTube$/, '').trim() : '';
                    return (t && t !== 'YouTube') ? t : '';
                }

                function getCleanAuthor() {
                    var el = document.querySelector('.slim-owner-channel-name, ytm-slim-owner-renderer .channel-name, .ytm-channel-name, a[href*="/@"]');
                    if (el && el.textContent && el.textContent.trim()) {
                        return el.textContent.trim();
                    }
                    return 'YouTube';
                }

                function bindVideo(v) {
                    if (v.__ytHooked) return;
                    v.__ytHooked = true;

                    function reportMetadata() {
                        var dur = Math.floor(v.duration || 0);
                        var title = getCleanTitle();
                        var author = getCleanAuthor();
                        if (window.AndroidBridge && title) {
                            window.AndroidBridge.onVideoReady(title, author, dur);
                        }
                    }

                    v.addEventListener('play', function() {
                        reportMetadata();
                        if (window.AndroidBridge) window.AndroidBridge.onStateChange(1);
                    });
                    v.addEventListener('playing', function() {
                        reportMetadata();
                        if (window.AndroidBridge) window.AndroidBridge.onStateChange(1);
                    });
                    v.addEventListener('pause', function() {
                        if (window.AndroidBridge && !window.__inPipMode) {
                            window.AndroidBridge.onStateChange(2);
                        }
                    });
                    v.addEventListener('ended', function() {
                        if (window.AndroidBridge) window.AndroidBridge.onVideoEnded();
                    });
                    v.addEventListener('timeupdate', function() {
                        if (window.AndroidBridge) {
                            var cur = Math.floor(v.currentTime || 0);
                            var dur = Math.floor(v.duration || 0);
                            window.AndroidBridge.onTimeUpdate(cur, dur);
                        }
                    });
                    v.addEventListener('loadedmetadata', reportMetadata);

                    // Ensure autoplay starts without user interaction block
                    if (v.paused) {
                        v.muted = false;
                        var p = v.play();
                        if (p !== undefined) {
                            p.catch(function() {
                                v.muted = true;
                                v.play().then(function() {
                                    setTimeout(function() { v.muted = false; }, 400);
                                }).catch(function() {
                                    var btn = document.querySelector('.ytp-large-play-button, .player-control-play-pause-icon');
                                    if (btn) btn.click();
                                });
                            });
                        }
                    }

                    if (window.AndroidBridge) {
                        window.AndroidBridge.onApiReady();
                    }
                }

                window.playVideo = function() {
                    var v = document.querySelector('video');
                    if (v) {
                        v.muted = false;
                        v.play();
                    } else {
                        var btn = document.querySelector('.ytp-large-play-button, .player-control-play-pause-icon');
                        if (btn) btn.click();
                    }
                };

                window.pauseVideo = function() {
                    var v = document.querySelector('video');
                    if (v) v.pause();
                };

                window.seekBy = function(sec) {
                    var v = document.querySelector('video');
                    if (v) v.currentTime = Math.max(0, (v.currentTime || 0) + sec);
                };

                window.seekTo = function(sec) {
                    var v = document.querySelector('video');
                    if (v) v.currentTime = sec;
                };

                window.playNextVideo = function() {
                    var nextBtn = document.querySelector('.ytp-next-button, .ytm-autonav-endscreen-button-icon, .player-control-next, [aria-label="Next video"], [aria-label="Next"]');
                    if (nextBtn) {
                        nextBtn.click();
                        return true;
                    }
                    var rec = document.querySelector('ytm-video-with-context-renderer a, ytm-compact-video-renderer a, ytm-item-section-renderer ytm-video-with-context-renderer a');
                    if (rec && rec.href) {
                        location.href = rec.href;
                        return true;
                    }
                    var v = document.querySelector('video');
                    if (v && isFinite(v.duration) && v.duration > 0) {
                        v.currentTime = v.duration - 0.5;
                        v.play();
                        return true;
                    }
                    return false;
                };

                window.playPreviousVideo = function() {
                    if (window.history && window.history.length > 1) {
                        window.history.back();
                        return true;
                    }
                    var v = document.querySelector('video');
                    if (v) {
                        v.currentTime = 0;
                        return true;
                    }
                    return false;
                };

                window.setVolume = function(vol) {
                    var v = document.querySelector('video');
                    if (v) {
                        v.volume = vol / 100.0;
                        v.muted = (vol === 0);
                    }
                };

                window.toggleMute = function() {
                    var v = document.querySelector('video');
                    if (v) v.muted = !v.muted;
                };

                window.setPlaybackQuality = function(quality) {
                    var map = { '1080p': 'hd1080', '720p': 'hd720', '480p': 'large', '360p': 'medium', '240p': 'small', 'Auto': 'auto' };
                    var ytQuality = map[quality] || 'auto';
                    var player = document.getElementById('movie_player');
                    if (player && typeof player.setPlaybackQualityRange === 'function') {
                        player.setPlaybackQualityRange(ytQuality, ytQuality);
                    }
                };

                // Continuous watcher loop (runs every 500ms)
                if (!window.__ytWatcherRunning) {
                    window.__ytWatcherRunning = true;
                    setInterval(function() {
                        var v = document.querySelector('video');
                        if (v) {
                            bindVideo(v);

                            // Detect internal navigation in YouTube web UI
                            var curUrl = location.href;
                            if (curUrl !== window.__lastYtUrl) {
                                window.__lastYtUrl = curUrl;
                                var vMatch = curUrl.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                                if (vMatch && vMatch[1] && window.AndroidBridge && typeof window.AndroidBridge.onVideoNavigated === 'function') {
                                    window.AndroidBridge.onVideoNavigated(vMatch[1], getCleanTitle());
                                }
                            }

                            // Periodically ensure title & author are populated
                            var curTitle = getCleanTitle();
                            var curAuthor = getCleanAuthor();
                            if (curTitle && curTitle !== window.__lastReportedTitle) {
                                window.__lastReportedTitle = curTitle;
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onVideoReady(curTitle, curAuthor, Math.floor(v.duration || 0));
                                }
                            }

                            // In-Stream Ad Fast-Forward Killer
                            var ad = document.querySelector('.ad-showing, .ad-interrupting');
                            if (ad) {
                                v.muted = true;
                                v.playbackRate = 16.0;
                                if (isFinite(v.duration) && v.duration > 0) {
                                    v.currentTime = v.duration;
                                }
                            }
                        } else {
                            var bigPlay = document.querySelector('.ytp-large-play-button');
                            if (bigPlay) bigPlay.click();
                        }

                        // Auto-click skip button
                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                        if (skipBtn) skipBtn.click();

                        // Auto-unmute
                        var unmuteBtn = document.querySelector('.ytp-unmute');
                        if (unmuteBtn) unmuteBtn.click();

                        // Dismiss promotional prompts
                        var dismiss = document.querySelector('button[aria-label="No thanks"], button[aria-label="Dismiss"], [aria-label="Accept all"], [aria-label="I agree"]');
                        if (dismiss) dismiss.click();
                    }, 500);
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }
}
