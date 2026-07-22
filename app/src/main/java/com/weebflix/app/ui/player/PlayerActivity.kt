package com.weebflix.app.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.ParametersBuilder
import androidx.media3.ui.PlayerView
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlayerActivity"

        private var simpleCache: androidx.media3.datasource.cache.SimpleCache? = null
        private var sharedOkHttpClient: OkHttpClient? = null

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun getSimpleCache(context: Context): androidx.media3.datasource.cache.SimpleCache {
            return simpleCache ?: synchronized(this) {
                simpleCache ?:                     androidx.media3.datasource.cache.SimpleCache(
                    java.io.File(context.applicationContext.cacheDir, "exo_player_cache"),
                    androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(250L * 1024 * 1024),
                    androidx.media3.database.StandaloneDatabaseProvider(context.applicationContext)
                ).also { simpleCache = it }
            }
        }

        fun getOkHttpClient(cacheDir: java.io.File): OkHttpClient {
            return sharedOkHttpClient ?: synchronized(this) {
                sharedOkHttpClient ?: OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .cache(okhttp3.Cache(java.io.File(cacheDir, "okhttp_cache"), 100L * 1024 * 1024))
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .addHeader("Connection", "keep-alive")
                        if (chain.request().url.host.contains("googlevideo.com")) {
                            request.addHeader("Referer", "https://www.blogger.com/")
                                .addHeader("Origin", "https://www.blogger.com")
                            try {
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://www.blogger.com")
                                if (!cookies.isNullOrEmpty()) {
                                    request.addHeader("Cookie", cookies)
                                }
                            } catch (_: Exception) {}
                        }
                        chain.proceed(request.build())
                    }
                    .addInterceptor { chain ->
                        var response = chain.proceed(chain.request())
                        var retries = 0
                        while (!response.isSuccessful && retries < 2) {
                            retries++
                            response.close()
                            val backoff = retries * 1000L
                            java.lang.Thread.sleep(backoff)
                            response = chain.proceed(chain.request())
                        }
                        response
                    }
                    .build().also { sharedOkHttpClient = it }
            }
        }
    }

    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var gestureOverlay: FrameLayout
    private lateinit var loadingPlayer: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvAnimeTitle: TextView
    private lateinit var tvEpisodeTitle: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var centerControls: FrameLayout
    private lateinit var btnCenterPlayPause: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrevServer: ImageView
    private lateinit var btnNextServer: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var btnPip: ImageView
    private lateinit var btnFullscreen: ImageView
    private lateinit var tvServerName: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnSkipOpening: TextView
    private lateinit var btnSkipOutro: TextView
    private lateinit var autoPlayOverlay: LinearLayout
    private lateinit var tvAutoPlayTitle: TextView
    private lateinit var tvAutoPlayCountdown: TextView
    private lateinit var btnCancelAutoPlay: TextView
    private lateinit var btnPlayNow: TextView
    private lateinit var brightnessIndicator: LinearLayout
    private lateinit var brightnessProgress: ProgressBar
    private lateinit var brightnessText: TextView
    private lateinit var volumeIndicator: LinearLayout
    private lateinit var volumeProgress: ProgressBar
    private lateinit var volumeText: TextView
    private lateinit var seekIndicator: LinearLayout
    private lateinit var seekIcon: ImageView
    private lateinit var seekText: TextView

    private var exoPlayer: ExoPlayer? = null
    private var episodeUrl: String = ""
    private var episodeTitle: String = ""
    private var episodeNumber: String = ""
    private var animeTitle: String = ""
    private var servers: List<VideoServer> = emptyList()
    private var currentServerIndex: Int = 0
    private var isPlaying: Boolean = true
    private val resolvedUrlCache = mutableMapOf<Int, String>()

    private var webView: WebView? = null
    private var webViewResolving = false
    private var webViewResolveCallback: ((String) -> Unit)? = null
    private var webViewResolveMode = ResolveMode.NONE
    private var resolveGeneration: Long = 0

    private var pendingResolveServerIndex: Int = -1
    private var pendingAutoFailRunnable: Runnable? = null

    private enum class ResolveMode { NONE, SERVER_CLICK, EMBED_FETCH }

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val maxVolume by lazy { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    private var currentVolume: Int = 0
    private var currentBrightness: Float = 0.5f

    private var controlsVisible: Boolean = true
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideControls() }

    private var isSeekingGesture: Boolean = false
    private var seekDelta: Long = 0L

    private var skipOpeningStart: Int = 90
    private var skipOpeningEnd: Int = 120
    private var skipOutroStart: Int = 1270
    private var skipOutroEnd: Int = 1400

    private var nextEpisodeUrl: String = ""
    private var nextEpisodeTitle: String = ""
    private var autoPlayCountdown: Int = 0
    private var autoPlayActive: Boolean = false
    private val autoPlayHandler = Handler(Looper.getMainLooper())
    private val autoPlayRunnable = object : Runnable {
        override fun run() {
            if (autoPlayActive && autoPlayCountdown > 0) {
                autoPlayCountdown--
                tvAutoPlayCountdown.text = autoPlayCountdown.toString()
                if (autoPlayCountdown <= 0) {
                    navigateToNextEpisode()
                } else {
                    autoPlayHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    private lateinit var gestureDetector: GestureDetector
    private var isPipMode: Boolean = false

    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    checkSkipButtonsVisibility()
                    checkAutoPlay()
                    updateSeekBarFromPlayer()
                }
            }
            progressUpdateHandler.postDelayed(this, 500)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        episodeUrl = intent.getStringExtra("url") ?: ""
        episodeTitle = intent.getStringExtra("title") ?: ""
        episodeNumber = intent.getStringExtra("episodeNumber") ?: ""
        animeTitle = intent.getStringExtra("animeTitle") ?: ""
        skipOpeningStart = intent.getIntExtra("skipOpeningStart", 90)
        skipOpeningEnd = intent.getIntExtra("skipOpeningEnd", 120)
        nextEpisodeUrl = intent.getStringExtra("nextEpisodeUrl") ?: ""
        nextEpisodeTitle = intent.getStringExtra("nextEpisodeTitle") ?: ""

        initViews()
        setupGestureDetector()
        setupControls()
        setupSeekBar()
        initWebView()

        tvAnimeTitle.text = animeTitle
        tvEpisodeTitle.text = if (episodeTitle.isNotEmpty()) episodeTitle else "Episode $episodeNumber"

        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentBrightness = getScreenBrightness()

        showControls()
        scheduleAutoHide()

        if (episodeUrl.isNotEmpty()) {
            loadServers()
            fetchEpisodeNavigation()
        }
    }

    private fun initViews() {
        playerContainer = findViewById(R.id.playerContainer)
        playerView = findViewById(R.id.playerView)
        gestureOverlay = findViewById(R.id.gestureOverlay)
        loadingPlayer = findViewById(R.id.loadingPlayer)
        tvError = findViewById(R.id.tvError)
        tvAnimeTitle = findViewById(R.id.tvAnimeTitle)
        tvEpisodeTitle = findViewById(R.id.tvEpisodeTitle)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        centerControls = findViewById(R.id.centerControls)
        btnCenterPlayPause = findViewById(R.id.btnCenterPlayPause)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevServer = findViewById(R.id.btnPrevEp)
        btnNextServer = findViewById(R.id.btnNextEp)
        btnBack = findViewById(R.id.btnBack)
        btnPip = findViewById(R.id.btnPip)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        tvServerName = findViewById(R.id.tvServerName)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnSkipOpening = findViewById(R.id.btnSkipOpening)
        btnSkipOutro = findViewById(R.id.btnSkipOutro)
        autoPlayOverlay = findViewById(R.id.autoPlayOverlay)
        tvAutoPlayTitle = findViewById(R.id.tvAutoPlayTitle)
        tvAutoPlayCountdown = findViewById(R.id.tvAutoPlayCountdown)
        btnCancelAutoPlay = findViewById(R.id.btnCancelAutoPlay)
        btnPlayNow = findViewById(R.id.btnPlayNow)
        brightnessIndicator = findViewById(R.id.brightnessIndicator)
        brightnessProgress = findViewById(R.id.brightnessProgress)
        brightnessText = findViewById(R.id.brightnessText)
        volumeIndicator = findViewById(R.id.volumeIndicator)
        volumeProgress = findViewById(R.id.volumeProgress)
        volumeText = findViewById(R.id.volumeText)
        seekIndicator = findViewById(R.id.seekIndicator)
        seekIcon = findViewById(R.id.seekIcon)
        seekText = findViewById(R.id.seekText)

        playerView.useController = false
        playerView.keepScreenOn = true
    }

    // ===== Hidden WebView for resolving video URLs (bypasses Cloudflare) =====

    private fun initWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            settings.mediaPlaybackRequiresUserGesture = false
            settings.blockNetworkImage = false
            settings.loadsImagesAutomatically = false

            addJavascriptInterface(WebViewBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!webViewResolving) return

                    if (webViewResolveMode == ResolveMode.SERVER_CLICK) {
                        view?.evaluateJavascript(
                            """
                            (function() {
                                document.querySelectorAll('video, audio').forEach(function(el) {
                                    el.muted = true;
                                    el.pause();
                                });
                                var style = document.createElement('style');
                                style.textContent = 'video, audio { display:none !important; }';
                                document.head.appendChild(style);
                            })();
                            """.trimIndent(), null
                        )
                    }

                    when (webViewResolveMode) {
                        ResolveMode.SERVER_CLICK -> {
                            Log.d(TAG, "WebView page loaded: $url, injecting server click...")
                            injectServerClick()
                        }
                        ResolveMode.EMBED_FETCH -> {
                            Log.d(TAG, "Embed page loaded: $url, extracting video URL...")
                            val isBlogger = url?.contains("blogger.com") == true || url?.contains("bp.blogspot.com") == true
                            val isFiledon = url?.contains("filedon.co") == true
                            if (isBlogger) {
                                view?.evaluateJavascript(extractBloggerVideoJs(), null)
                            } else if (isFiledon) {
                                view?.evaluateJavascript(extractFiledonVideoJs(), null)
                            } else {
                                extractVideoFromEmbedPage(view)
                            }
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    document.querySelectorAll('video').forEach(function(v) {
                                        v.muted = true;
                                        v.play().catch(function(){});
                                    });
                                })();
                                """.trimIndent(), null
                            )
                        }
                        ResolveMode.NONE -> {}
                    }
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "WebView error: $description at $failingUrl")
                    if (webViewResolving) {
                        webViewResolving = false
                        webViewResolveMode = ResolveMode.NONE
                        webViewResolveCallback?.invoke("")
                        webViewResolveCallback = null
                    }
                }

                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null

                    val isBloggerVideoG = reqUrl.contains("blogger.com/video.g") || reqUrl.contains("video.g?token=")
                    val isVideoUrl = reqUrl.contains("googlevideo.com") ||
                        reqUrl.contains("videoplayback") ||
                        reqUrl.contains(".m3u8") ||
                        reqUrl.contains(".mp4") ||
                        reqUrl.contains(".mpd") ||
                        reqUrl.contains("blogspot.com/v/") ||
                        reqUrl.contains("bp.blogspot.com")

                    if (isBloggerVideoG) {
                        Log.d(TAG, ">>> Intercepting video.g HTML to inject XHR interception: $reqUrl")
                        return interceptBloggerHtml(reqUrl)
                    }

                    if (isVideoUrl && webViewResolving) {
                        Log.d(TAG, "Intercepted video URL: $reqUrl")
                        val gen = resolveGeneration
                        runOnUiThread {
                            if (gen == resolveGeneration) {
                                webViewResolving = false
                                webViewResolveMode = ResolveMode.NONE
                                val callback = webViewResolveCallback
                                webViewResolveCallback = null
                                pendingResolveServer = null
                                callback?.invoke(reqUrl)
                            }
                        }
                        return null
                    }

                    return null
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 60 && webViewResolving && webViewResolveMode == ResolveMode.EMBED_FETCH) {
                        val currentUrl = view?.url ?: ""
                        val isBlogger = currentUrl.contains("blogger.com") || currentUrl.contains("bp.blogspot.com")
                        val isFiledon = currentUrl.contains("filedon.co")

                        if (isBlogger) {
                            view?.evaluateJavascript(extractBloggerVideoJs(), null)
                        } else if (isFiledon) {
                            view?.evaluateJavascript(extractFiledonVideoJs(), null)
                        } else {
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    function checkVideo() {
                                        var vids = document.querySelectorAll('video, video source');
                                        for (var i = 0; i < vids.length; i++) {
                                            var s = vids[i].src || vids[i].getAttribute('src') || '';
                                            if (s && s.indexOf('about:blank') === -1 && s.indexOf('blob:') === -1 &&
                                                (s.indexOf('googlevideo.com') !== -1 ||
                                                 s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1 ||
                                                 s.indexOf('.mpd') !== -1 || s.indexOf('videoplayback') !== -1)) {
                                                window.AndroidBridge.onUrlFound(s);
                                                return true;
                                            }
                                        }
                                        var iframes = document.querySelectorAll('iframe');
                                        for (var j = 0; j < iframes.length; j++) {
                                            var isrc = iframes[j].src || iframes[j].getAttribute('src') || '';
                                            if (isrc && isrc.indexOf('about:blank') === -1 && isrc.indexOf('javascript:') === -1 &&
                                                isrc.indexOf('data:') === -1 &&
                                                (isrc.indexOf('.mp4') !== -1 || isrc.indexOf('.m3u8') !== -1 ||
                                                 isrc.indexOf('.mpd') !== -1 || isrc.indexOf('googlevideo.com') !== -1)) {
                                                window.AndroidBridge.onUrlFound(isrc);
                                                return true;
                                            }
                                        }
                                        return false;
                                    }
                                    if (!checkVideo()) {
                                        var obs = new MutationObserver(function(mutations) {
                                            if (checkVideo()) obs.disconnect();
                                        });
                                        obs.observe(document.body || document.documentElement, {childList: true, subtree: true, attributes: true});
                                        setTimeout(function() { obs.disconnect(); }, 8000);
                                    }
                                })();
                                """.trimIndent(), null
                            )
                        }
                    }
                }
            }

            visibility = View.INVISIBLE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        playerContainer.addView(webView)
    }

    private fun extractVideoFromEmbedPage(view: WebView?) {
        val extractJs = """
            (function() {
                var found = false;
                function notifyUrl(url) {
                    if (!found && url && url.indexOf('about:blank') === -1 && url.indexOf('javascript:') === -1) {
                        found = true; window.AndroidBridge.onUrlFound(url);
                    }
                }

                function isVideoLikeUrl(s) {
                    if (!s || s.indexOf('about:blank') !== -1 || s.indexOf('blob:') !== -1 || s.indexOf('data:') !== -1) return false;
                    return s.indexOf('googlevideo.com') !== -1 || s.indexOf('videoplayback') !== -1 ||
                        s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1 || s.indexOf('.mpd') !== -1 ||
                        s.indexOf('bp.blogspot.com') !== -1;
                }

                function scanDom() {
                    var vids = document.querySelectorAll('video, video source, source');
                    for (var i = 0; i < vids.length; i++) {
                        var s = vids[i].src || vids[i].getAttribute('src') || vids[i].currentSrc || '';
                        if (isVideoLikeUrl(s)) { notifyUrl(s); return true; }
                    }
                    var iframes = document.querySelectorAll('iframe');
                    for (var j = 0; j < iframes.length; j++) {
                        var isrc = iframes[j].src || iframes[j].getAttribute('src') || '';
                        if (isrc && isrc.indexOf('about:blank') === -1 && isrc.indexOf('javascript:') === -1 && isrc.indexOf('data:') === -1) {
                            if (isVideoLikeUrl(isrc)) { notifyUrl(isrc); return true; }
                        }
                    }
                    return false;
                }

                function scanBody() {
                    var all = document.body ? document.body.innerHTML : '';
                    var patterns = [
                        /["'](?:file|source|src|video_url|videoUrl|stream|url|video|videoSrc|playbackUrl|mediaUrl)["']\s*[:=]\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                        /(https?:\/\/[^\s"'<>]*googlevideo\.com[^\s"'<>]*)/i,
                        /(https?:\/\/[^\s"'<>]+\.googlevideo\.com[^\s"'<>]*)/i,
                        /(https?:\/\/[^\s"'<>]+\.(?:mp4|m3u8|mpd)(?:\?[^\s"'<>]*)?)/i
                    ];
                    for (var p = 0; p < patterns.length; p++) {
                        var m = all.match(patterns[p]);
                        if (m && m[1]) { notifyUrl(m[1]); return true; }
                    }
                    return false;
                }

                function scanScripts() {
                    var scripts = document.querySelectorAll('script');
                    for (var k = 0; k < scripts.length; k++) {
                        var txt = scripts[k].textContent || '';
                        var bp = txt.match(/["'](https?:\/\/[^\s"']*blogspot\.com[^\s"']*videoplayback[^\s"']*)/i);
                        if (bp && bp[1]) { notifyUrl(bp[1]); return true; }
                        var gv = txt.match(/["'](https?:\/\/[^\s"']*googlevideo\.com[^\s"']*)/i);
                        if (gv && gv[1]) { notifyUrl(gv[1]); return true; }
                        var mp4 = txt.match(/["'](https?:\/\/[^\s"']+\.(?:mp4|m3u8|mpd)(?:\?[^\s"']*)?)/i);
                        if (mp4 && mp4[1]) { notifyUrl(mp4[1]); return true; }
                        var fileMatch = txt.match(/(?:file|source|src|videoUrl|video_url|videoSrc|playbackUrl|mediaUrl)\s*[:=]\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i);
                        if (fileMatch && fileMatch[1]) { notifyUrl(fileMatch[1]); return true; }
                        var vidConfig = txt.match(/VIDEO_CONFIG\s*=\s*(\{[\s\S]*?\});/);
                        if (vidConfig && vidConfig[1]) {
                            var playUrl = vidConfig[1].match(/"play_url"\s*:\s*"([^"]+)"/);
                            if (playUrl && playUrl[1]) { notifyUrl(playUrl[1]); return true; }
                            var urlField = vidConfig[1].match(/"url"\s*:\s*"([^"]+)"/);
                            if (urlField && urlField[1] && urlField[1].indexOf('http') === 0) { notifyUrl(urlField[1]); return true; }
                        }
                    }
                    return false;
                }

                function scanGlobals() {
                    try {
                        var vc = window.VIDEO_CONFIG;
                        if (vc && vc.streams) {
                            for (var i = 0; i < vc.streams.length; i++) {
                                var s = vc.streams[i];
                                if (s && s.play_url) { notifyUrl(s.play_url); return true; }
                                if (s && s.url) { notifyUrl(s.url); return true; }
                            }
                        }
                    } catch(e) {}
                    try {
                        var yt = window.yt && window.yt.config_;
                        if (yt) {
                            var keys = Object.keys(yt);
                            for (var i = 0; i < keys.length; i++) {
                                var val = yt[keys[i]];
                                if (typeof val === 'string' && val.indexOf('http') === 0 && val.indexOf('googlevideo') !== -1) {
                                    notifyUrl(val); return true;
                                }
                            }
                        }
                    } catch(e) {}
                    var keys2 = ['videoUrl', 'video_url', 'streamUrl', 'stream_url', 'playerUrl', 'playbackUrl', 'mediaUrl', 'videoSrc'];
                    for (var g = 0; g < keys2.length; g++) {
                        try {
                            var val = window[keys2[g]];
                            if (val && typeof val === 'string' && val.indexOf('http') === 0) {
                                notifyUrl(val); return true;
                            }
                        } catch(e) {}
                    }
                    try {
                        if (window.__INITIAL_STATE__) {
                            var stateStr = JSON.stringify(window.__INITIAL_STATE__);
                            var stateUrl = stateStr.match(/https?:\/\/[^\s"']+\.(?:mp4|m3u8|mpd)[^\s"']*/i);
                            if (stateUrl) { notifyUrl(stateUrl[0]); return true; }
                        }
                    } catch(e) {}
                    try {
                        if (window.jwplayer) {
                            var jwState = window.jwplayer().getState ? window.jwplayer() : null;
                            if (jwState && jwState.getPlaylistItem) {
                                var item = jwState.getPlaylistItem();
                                if (item && item.file) { notifyUrl(item.file); return true; }
                            }
                        }
                    } catch(e) {}
                    return false;
                }

                function scanNetworkRequests() {
                    try {
                        var entries = performance.getEntriesByType('resource');
                        for (var i = 0; i < entries.length; i++) {
                            var name = entries[i].name || '';
                            if (isVideoLikeUrl(name)) { notifyUrl(name); return true; }
                        }
                    } catch(e) {}
                    return false;
                }

                var origXHR = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    if (typeof url === 'string' && isVideoLikeUrl(url)) {
                        notifyUrl(url);
                    }
                    return origXHR.apply(this, arguments);
                };
                var origFetch = window.fetch;
                window.fetch = function(url) {
                    if (typeof url === 'string' && isVideoLikeUrl(url)) {
                        notifyUrl(url);
                    }
                    return origFetch.apply(this, arguments);
                };

                function tryExtract(attempt) {
                    if (found) return;
                    scanDom(); if (found) return;
                    scanBody(); if (found) return;
                    scanScripts(); if (found) return;
                    scanGlobals(); if (found) return;
                    scanNetworkRequests(); if (found) return;
                    if (attempt < 8) {
                        setTimeout(function() { tryExtract(attempt + 1); }, 1200);
                    } else {
                        notifyUrl('');
                    }
                }
                tryExtract(0);
            })();
        """.trimIndent()
        view?.evaluateJavascript(extractJs, null)
    }

    private fun resolveWithWebView(server: VideoServer, callback: (String) -> Unit) {
        if (webViewResolving) {
            callback("")
            return
        }
        webViewResolving = true
        webViewResolveMode = ResolveMode.SERVER_CLICK
        webViewResolveCallback = callback
        pendingResolveServer = server
        resolveGeneration++
        val gen = resolveGeneration

        Log.d(TAG, "WebView resolving server: ${server.name}, loading episode page...")
        webView?.stopLoading()
        webView?.loadUrl(episodeUrl)

        webView?.postDelayed({
            if (webViewResolving && resolveGeneration == gen) {
                Log.w(TAG, "WebView safety timeout for server: ${server.name}")
                webViewResolving = false
                webViewResolveMode = ResolveMode.NONE
                webViewResolveCallback?.invoke("")
                webViewResolveCallback = null
                pendingResolveServer = null
            }
        }, 15000)
    }

    private var pendingResolveServer: VideoServer? = null

    private fun injectServerClick() {
        val server = pendingResolveServer ?: return
        val nume = server.dataNume.replace("\\", "\\\\").replace("\"", "\\\"")
        val name = server.name.replace("\\", "\\\\").replace("\"", "\\\"")

        val js = """
            (function() {
                function notify(url) { window.AndroidBridge.onUrlFound(url); }
                var notified = false;
                function safeNotify(url) {
                    if (notified) return;
                    if (url) { notified = true; notify(url); }
                }

                var serverEl = document.querySelector('[data-nume="$nume"]');
                if (!serverEl) {
                    var allOpts = document.querySelectorAll('.east_player_option, [data-nume]');
                    var found = false;
                    for (var i = 0; i < allOpts.length; i++) {
                        var txt = allOpts[i].textContent || allOpts[i].innerText || '';
                        if (txt.indexOf('$name') !== -1 || allOpts[i].getAttribute('data-nume') === '$nume') {
                            serverEl = allOpts[i]; found = true; break;
                        }
                    }
                    if (!found) { notify(''); return; }
                }

                var embed = document.getElementById('player_embed') || document.getElementById('embed') || document.querySelector('.player-embed, #player');
                if (embed) embed.innerHTML = '';
                serverEl.click();

                function scanForMedia() {
                    var badHosts = ['facebook.com', 'disqus.com', 'disquscdn.com', 'twitter.com', 'instagram.com', 'tiktok.com'];
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        var isrc = iframes[i].src || iframes[i].getAttribute('src') || '';
                        if (isrc && isrc.indexOf('about:blank') === -1 && isrc.indexOf('javascript:') === -1 && isrc.indexOf('data:') === -1) {
                            var isBad = false;
                            for (var h = 0; h < badHosts.length; h++) {
                                if (isrc.indexOf(badHosts[h]) !== -1) { isBad = true; break; }
                            }
                            if (!isBad && (isrc.indexOf('.mp4') !== -1 || isrc.indexOf('.m3u8') !== -1 || isrc.indexOf('.mpd') !== -1 || isrc.indexOf('googlevideo.com') !== -1 || isrc.indexOf('videoplayback') !== -1)) {
                                safeNotify(isrc); return true;
                            }
                        }
                    }
                    var vids = document.querySelectorAll('video, video source, source');
                    for (var j = 0; j < vids.length; j++) {
                        var vs = vids[j].src || vids[j].getAttribute('src') || vids[j].currentSrc || '';
                        if (vs && vs.indexOf('about:blank') === -1 && vs.indexOf('blob:') === -1) {
                            if (vs.indexOf('.mp4') !== -1 || vs.indexOf('.m3u8') !== -1 || vs.indexOf('.mpd') !== -1 ||
                                vs.indexOf('googlevideo.com') !== -1) {
                                safeNotify(vs); return true;
                            }
                        }
                    }
                    for (var i = 0; i < iframes.length; i++) {
                        var isrc2 = iframes[i].src || iframes[i].getAttribute('src') || '';
                        if (isrc2 && isrc2.indexOf('about:blank') === -1 && isrc2.indexOf('javascript:') === -1 && isrc2.indexOf('data:') === -1) {
                            var isBad2 = false;
                            for (var h2 = 0; h2 < badHosts.length; h2++) {
                                if (isrc2.indexOf(badHosts[h2]) !== -1) { isBad2 = true; break; }
                            }
                            if (!isBad2) { safeNotify(isrc2); return true; }
                        }
                    }
                    return false;
                }

                if (scanForMedia()) return;

                var obs = new MutationObserver(function() {
                    if (scanForMedia()) obs.disconnect();
                });
                obs.observe(document.body || document.documentElement, {childList: true, subtree: true, attributes: true});

                setTimeout(function() {
                    obs.disconnect();
                    if (!notified) {
                        var em2 = document.getElementById('player_embed') || document.getElementById('embed') || document.querySelector('.player-embed, #player');
                        if (em2) {
                            var all = em2.innerHTML || '';
                            var urlMatch = all.match(/["'](?:file|source|src|video_url|videoUrl)["']\s*[:=]\s*["'](https?:\/\/[^"']+)/i);
                            if (urlMatch && urlMatch[1]) { safeNotify(urlMatch[1]); return; }
                            urlMatch = all.match(/(https?:\/\/[^\s"'<>]+\.(?:mp4|m3u8|mpd)[^"'\s<>]*)/i);
                            if (urlMatch && urlMatch[1]) { safeNotify(urlMatch[1]); return; }
                        }
                        if (!notified) notify('');
                    }
                }, 10000);
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    inner class WebViewBridge {
        @JavascriptInterface
        fun onUrlFound(url: String?) {
            val resolvedUrl = url ?: ""
            if (resolvedUrl.isEmpty() || !resolvedUrl.startsWith("http")) {
                Log.d(TAG, "WebView resolved empty/non-http URL, ignoring")
                return
            }
            val gen = resolveGeneration
            Log.d(TAG, "WebView resolved URL (gen=$gen): $resolvedUrl")
            runOnUiThread {
                if (gen != resolveGeneration) return@runOnUiThread
                webViewResolving = false
                webViewResolveMode = ResolveMode.NONE
                val callback = webViewResolveCallback
                webViewResolveCallback = null
                pendingResolveServer = null
                callback?.invoke(resolvedUrl)
            }
        }

        @JavascriptInterface
        fun onIframeFound(iframeUrl: String?) {
            val url = iframeUrl ?: ""
            if (url.isEmpty()) return
            val gen = resolveGeneration
            Log.d(TAG, "WebView iframe found (gen=$gen): $url")
            lifecycleScope.launch {
                val videoUrl = try {
                    WeebFlixApp.instance.scraper.resolveBloggerVideoG(url)
                } catch (e: Exception) {
                    Log.e(TAG, "resolveBloggerVideoG from iframe error: ${e.message}")
                    ""
                }
                if (videoUrl.isNotEmpty() && gen == resolveGeneration) {
                    Log.d(TAG, "Blogger iframe resolved to video: $videoUrl")
                    withContext(Dispatchers.Main) {
                        if (gen == resolveGeneration) {
                            webViewResolving = false
                            webViewResolveMode = ResolveMode.NONE
                            val callback = webViewResolveCallback
                            webViewResolveCallback = null
                            pendingResolveServer = null
                            callback?.invoke(videoUrl)
                        }
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initExoPlayer(videoUrl: String) {
        exoPlayer?.release()

        val cache = getSimpleCache(this)
        val okHttpClient = getOkHttpClient(cacheDir)

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            if (videoUrl.contains("googlevideo.com")) {
                setDefaultRequestProperties(mapOf(
                    "Referer" to "https://www.blogger.com/",
                    "Origin" to "https://www.blogger.com"
                ))
            }
        }

        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,    // minBufferMs
                60_000,    // maxBufferMs
                2_500,     // bufferForPlaybackMs - ~2.5s before play
                1_500      // bufferForPlaybackAfterRebufferMs - ~1.5s after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                ParametersBuilder(this@PlayerActivity)
                    .setMaxVideoSize(1920, 1080)
                    .setPreferredAudioLanguage("id")
                    .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
            )
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { player ->
                playerView.player = player

                val mediaItem = MediaItem.fromUri(videoUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        runOnUiThread {
                            val icon = if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play
                            btnPlayPause.setImageResource(icon)
                            btnCenterPlayPause.setImageResource(icon)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        runOnUiThread {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    loadingPlayer.visibility = View.VISIBLE
                                    tvError.visibility = View.GONE
                                }
                                Player.STATE_READY -> {
                                    loadingPlayer.visibility = View.GONE
                                    tvError.visibility = View.GONE
                                }
                                Player.STATE_ENDED -> {
                                    isPlaying = false
                                    btnPlayPause.setImageResource(R.drawable.ic_player_play)
                                    btnCenterPlayPause.setImageResource(R.drawable.ic_player_play)
                                    showControls()
                                }
                                Player.STATE_IDLE -> {}
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        runOnUiThread {
                            loadingPlayer.visibility = View.GONE
                            Log.e(TAG, "Player error: ${error.message}", error)
                            val serverName = if (currentServerIndex in servers.indices) servers[currentServerIndex].name else "Unknown"
                            scheduleAutoFail(serverName)
                        }
                    }
                })

                progressUpdateHandler.postDelayed(progressUpdateRunnable, 500)
            }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = gestureOverlay.width
                val tapX = e.x
                val centerX = viewWidth / 2f

                if (tapX < centerX) {
                    seekBy(-10f)
                    showSeekIndicator(false, "-10s")
                } else {
                    seekBy(10f)
                    showSeekIndicator(true, "+10s")
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 30) {
                    handleSeekGesture(e1.x, e2.x)
                    return true
                } else if (abs(deltaY) > abs(deltaX) && abs(deltaY) > 20) {
                    val startX = e1.x
                    if (startX < gestureOverlay.width / 2f) {
                        handleBrightnessGesture(deltaY)
                    } else {
                        handleVolumeGesture(deltaY)
                    }
                    return true
                }
                return false
            }
        })

        gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (isSeekingGesture) {
                    isSeekingGesture = false
                    seekBy(seekDelta.toFloat())
                    hideSeekIndicator()
                }
                hideBrightnessIndicator()
                hideVolumeIndicator()
            }
            true
        }
    }

    private fun setupControls() {
        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnCenterPlayPause.setOnClickListener { togglePlayPause() }

        btnPrevServer.setOnClickListener {
            if (currentServerIndex > 0) {
                currentServerIndex--
                loadServer(currentServerIndex)
                updateServerUI()
            }
        }

        btnNextServer.setOnClickListener {
            if (currentServerIndex < servers.size - 1) {
                currentServerIndex++
                loadServer(currentServerIndex)
                updateServerUI()
            }
        }

        btnPip.setOnClickListener { enterPipMode() }
        btnFullscreen.setOnClickListener { }
        tvServerName.setOnClickListener { showServerPickerDialog() }
        tvError.setOnClickListener { if (servers.isNotEmpty()) showServerPickerDialog() }

        btnSkipOpening.setOnClickListener {
            exoPlayer?.seekTo(skipOpeningEnd * 1000L)
            btnSkipOpening.visibility = View.GONE
            scheduleAutoHide()
        }

        btnSkipOutro.setOnClickListener {
            exoPlayer?.seekTo(skipOutroEnd * 1000L)
            btnSkipOutro.visibility = View.GONE
            scheduleAutoHide()
        }

        btnCancelAutoPlay.setOnClickListener { cancelAutoPlay() }
        btnPlayNow.setOnClickListener {
            autoPlayHandler.removeCallbacks(autoPlayRunnable)
            navigateToNextEpisode()
        }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = exoPlayer?.duration?.takeIf { it > 0 } ?: return
                    val time = progress.toFloat() / (seekBar?.max ?: 1) * duration / 1000f
                    tvCurrentTime.text = formatTime(time)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                showControls()
                progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar == null) return
                val duration = exoPlayer?.duration?.takeIf { it > 0 } ?: return
                val progress = seekBar.progress.toFloat() / seekBar.max
                val seekToMs = (progress * duration).toLong()
                exoPlayer?.seekTo(seekToMs)
                scheduleAutoHide()
                progressUpdateHandler.postDelayed(progressUpdateRunnable, 500)
            }
        })
    }

    // ===== Gesture Handlers =====

    private fun handleBrightnessGesture(deltaY: Float) {
        val delta = (-deltaY / (gestureOverlay.height.toFloat() / 2f)).coerceIn(-0.05f, 0.05f)
        currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = currentBrightness
        window.attributes = layoutParams
        val percent = (currentBrightness * 100).toInt()
        brightnessProgress.progress = percent
        brightnessText.text = "$percent%"
        showBrightnessIndicator()
    }

    private fun handleVolumeGesture(deltaY: Float) {
        val delta = (-deltaY / (gestureOverlay.height.toFloat() / 2f) * maxVolume).coerceIn(-maxVolume.toFloat() / 50f, maxVolume.toFloat() / 50f)
        currentVolume = (currentVolume + delta.toInt()).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
        val percent = (currentVolume * 100f / maxVolume).toInt()
        volumeProgress.progress = percent
        volumeText.text = "$percent%"
        showVolumeIndicator()
    }

    private fun handleSeekGesture(startX: Float, currentX: Float) {
        val deltaX = currentX - startX
        seekDelta = (deltaX / gestureOverlay.width * 120f).toLong()
        isSeekingGesture = true
        seekText.text = if (seekDelta >= 0) "+${abs(seekDelta)}s" else "-${abs(seekDelta)}s"
        showSeekIndicator(seekDelta >= 0, seekText.text.toString())
    }

    private fun seekBy(seconds: Float) {
        val player = exoPlayer ?: return
        val newMs = (player.currentPosition + seconds * 1000).coerceAtLeast(0f).toLong()
        player.seekTo(newMs)
        scheduleAutoHide()
    }

    private fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
        showControls()
        scheduleAutoHide()
    }

    // ===== Controls Visibility =====

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        topBar.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        centerControls.visibility = View.VISIBLE
        checkSkipButtonsVisibility()
    }

    private fun hideControls() {
        controlsVisible = false
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        centerControls.visibility = View.GONE
        btnSkipOpening.visibility = View.GONE
        btnSkipOutro.visibility = View.GONE
        brightnessIndicator.visibility = View.GONE
        volumeIndicator.visibility = View.GONE
        seekIndicator.visibility = View.GONE
    }

    private fun scheduleAutoHide() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, 4000)
    }

    private fun showBrightnessIndicator() {
        brightnessIndicator.visibility = View.VISIBLE
        volumeIndicator.visibility = View.GONE
        seekIndicator.visibility = View.GONE
    }

    private fun hideBrightnessIndicator() { brightnessIndicator.visibility = View.GONE }

    private fun showVolumeIndicator() {
        volumeIndicator.visibility = View.VISIBLE
        brightnessIndicator.visibility = View.GONE
        seekIndicator.visibility = View.GONE
    }

    private fun hideVolumeIndicator() { volumeIndicator.visibility = View.GONE }

    private fun showSeekIndicator(forward: Boolean, text: String) {
        seekIndicator.visibility = View.VISIBLE
        seekText.text = text
        seekIcon.setImageResource(if (forward) R.drawable.ic_player_skip_forward else R.drawable.ic_player_skip_backward)
    }

    private fun hideSeekIndicator() { seekIndicator.visibility = View.GONE }

    // ===== Skip Opening / Outro =====

    private fun checkSkipButtonsVisibility() {
        val player = exoPlayer ?: return
        val duration = player.duration
        if (duration <= 0) return
        val currentSec = player.currentPosition / 1000f
        val totalSec = duration / 1000f

        btnSkipOpening.visibility = if (currentSec in skipOpeningStart.toFloat()..skipOpeningEnd.toFloat() && controlsVisible) View.VISIBLE else View.GONE

        val adjustedOutroStart = skipOutroStart.toFloat()
        btnSkipOutro.visibility = if (currentSec >= adjustedOutroStart && currentSec < totalSec && controlsVisible && nextEpisodeUrl.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // ===== Auto-play =====

    private fun checkAutoPlay() {
        val player = exoPlayer ?: return
        val duration = player.duration
        if (duration <= 0) return
        val timeRemaining = (duration - player.currentPosition) / 1000f
        if (timeRemaining in 0f..10f && nextEpisodeUrl.isNotEmpty() && !autoPlayActive) {
            startAutoPlayCountdown()
        }
    }

    private fun startAutoPlayCountdown() {
        autoPlayActive = true
        autoPlayCountdown = 10
        autoPlayOverlay.visibility = View.VISIBLE
        tvAutoPlayTitle.text = nextEpisodeTitle.ifEmpty { "Episode Selanjutnya" }
        tvAutoPlayCountdown.text = autoPlayCountdown.toString()
        autoPlayHandler.postDelayed(autoPlayRunnable, 1000)
    }

    private fun cancelAutoPlay() {
        autoPlayActive = false
        autoPlayCountdown = 0
        autoPlayOverlay.visibility = View.GONE
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
    }

    private fun navigateToNextEpisode() {
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
        if (nextEpisodeUrl.isNotEmpty()) {
            val nextEpNum = Regex("""(\d+)""").find(nextEpisodeTitle)?.groupValues?.getOrElse(1) { "" } ?: ""
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", nextEpisodeUrl)
                putExtra("title", nextEpisodeTitle)
                putExtra("episodeNumber", nextEpNum)
                putExtra("animeTitle", animeTitle)
                putExtra("nextEpisodeUrl", "")
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Episode selanjutnya tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== Navigation =====

    private fun fetchEpisodeNavigation() {
        if (nextEpisodeUrl.isNotEmpty()) return
        lifecycleScope.launch {
            try {
                val nav = WeebFlixApp.instance.scraper.getEpisodeNavigation(episodeUrl)
                if (!isFinishing && nav.nextEpisodeUrl.isNotEmpty()) {
                    nextEpisodeUrl = nav.nextEpisodeUrl
                    nextEpisodeTitle = nav.nextEpisodeTitle
                }
            } catch (_: Exception) { }
        }
    }

    // ===== Time Tracking =====

    private fun updateSeekBarFromPlayer() {
        val player = exoPlayer ?: return
        val duration = player.duration
        if (duration <= 0) return
        val currentMs = player.currentPosition
        seekBar.progress = (currentMs.toFloat() / duration * seekBar.max).toInt().coerceIn(0, seekBar.max)
        tvCurrentTime.text = formatTime(currentMs / 1000f)
        tvTotalTime.text = formatTime(duration / 1000f)
    }

    private fun formatTime(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hrs > 0) String.format("%d:%02d:%02d", hrs, mins, secs)
        else String.format("%02d:%02d", mins, secs)
    }

    private fun getScreenBrightness(): Float {
        val lp = window.attributes
        if (lp.screenBrightness < 0) {
            return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
            catch (_: Settings.SettingNotFoundException) { 0.5f }
        }
        return lp.screenBrightness
    }

    // ===== Server Selection =====

    private fun loadServers() {
        lifecycleScope.launch {
            try {
                servers = WeebFlixApp.instance.scraper.getEpisodeServers(episodeUrl)
                if (!isFinishing) {
                    if (servers.isNotEmpty()) {
                        updateServerUI()
                        loadServer(0)
                    } else {
                        showError("Tidak ada server yang tersedia")
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing) showError("Error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        loadingPlayer.visibility = View.GONE
        tvError.visibility = View.VISIBLE
        tvError.text = message
    }

    private fun scheduleAutoFail(serverName: String) {
        pendingAutoFailRunnable?.let { tvError.removeCallbacks(it) }
        val nextIndex = currentServerIndex + 1
        if (nextIndex < servers.size) {
            tvError.visibility = View.VISIBLE
            tvError.text = "Server $serverName gagal. Mencoba server berikutnya..."
            val r = Runnable {
                if (!isFinishing) {
                    currentServerIndex = nextIndex
                    updateServerUI()
                    loadServer(nextIndex)
                }
            }
            pendingAutoFailRunnable = r
            tvError.postDelayed(r, 3000)
        } else {
            showError("Semua server gagal. Coba pilih server lain.")
        }
    }

    private fun updateServerUI() {
        if (servers.isNotEmpty() && currentServerIndex in servers.indices) {
            tvServerName.text = servers[currentServerIndex].name
        }
    }

    private fun showServerPickerDialog() {
        val serverNames = servers.map { it.name }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_WeebFlix)
            .setTitle("Pilih Server")
            .setItems(serverNames) { _, which ->
                currentServerIndex = which
                loadServer(which)
                updateServerUI()
            }
            .show()
    }

    private fun loadServer(index: Int) {
        if (index in servers.indices) playServer(servers[index])
    }

    private fun playServer(server: VideoServer) {
        pendingAutoFailRunnable?.let { tvError.removeCallbacks(it) }
        pendingAutoFailRunnable = null

        loadingPlayer.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        seekBar.progress = 0
        tvCurrentTime.text = "00:00"
        tvTotalTime.text = "00:00"

        val serverIndex = servers.indexOf(server).takeIf { it >= 0 } ?: currentServerIndex
        val cachedUrl = resolvedUrlCache[serverIndex]
        if (cachedUrl != null && cachedUrl.isNotEmpty() && !cachedUrl.contains("\\u00") && !cachedUrl.contains("\\=") && !cachedUrl.contains("\\&")) {
            Log.d(TAG, "Playing cached URL: $cachedUrl")
            loadingPlayer.visibility = View.GONE
            initExoPlayer(cachedUrl)
            return
        }

        if (server.videoUrl.isNotEmpty()) {
            Log.d(TAG, "Playing resolved URL: ${server.videoUrl}")
            resolvedUrlCache[serverIndex] = server.videoUrl
            loadingPlayer.visibility = View.GONE
            initExoPlayer(server.videoUrl)
            return
        }

        val url = server.url
        if (url.isNotEmpty() && (url.contains(".mp4") || url.contains(".m3u8") || url.contains(".mpd") || url.contains("googlevideo.com"))) {
            Log.d(TAG, "Playing direct video URL: $url")
            resolvedUrlCache[serverIndex] = url
            loadingPlayer.visibility = View.GONE
            initExoPlayer(url)
            return
        }

        if (server.name.contains("Blogspot", ignoreCase = true) || server.url.contains("blogger.com") || server.url.contains("blogspot") || server.url.contains("bp.blogspot.com")) {
            Log.d(TAG, "Blogger server detected, using fast AJAX + XHR path...")
            lifecycleScope.launch {
                val ajaxUrl = WeebFlixApp.instance.scraper.resolveServerVideoUrl(server, episodeUrl)
                if (!isFinishing && ajaxUrl.isNotEmpty() && ajaxUrl.contains("blogger.com")) {
                    Log.d(TAG, "AJAX returned blogger embed URL: $ajaxUrl, loading in WebView with XHR interception")
                    runOnUiThread {
                        loadingPlayer.visibility = View.GONE
                        resolveEmbedUrlViaWebView(ajaxUrl, server, serverIndex)
                    }
                } else if (!isFinishing && ajaxUrl.isNotEmpty() && (ajaxUrl.contains("googlevideo.com") || ajaxUrl.contains(".mp4"))) {
                    Log.d(TAG, "AJAX returned direct video URL: $ajaxUrl")
                    runOnUiThread {
                        loadingPlayer.visibility = View.GONE
                        tvError.visibility = View.GONE
                        resolvedUrlCache[serverIndex] = ajaxUrl
                        initExoPlayer(ajaxUrl)
                    }
                } else {
                    Log.d(TAG, "AJAX failed, falling back to WebView page load...")
                    runOnUiThread {
                        resolveWithWebView(server) { resolvedUrl ->
                            runOnUiThread {
                                if (!isFinishing) {
                                    if (resolvedUrl.isNotEmpty()) {
                                        Log.d(TAG, "WebView resolved: $resolvedUrl")
                                        loadingPlayer.visibility = View.GONE
                                        tvError.visibility = View.GONE
                                        if (resolvedUrl.contains("googlevideo.com") || resolvedUrl.contains(".mp4") || resolvedUrl.contains(".m3u8") || resolvedUrl.contains(".mpd") || resolvedUrl.contains("wibufile.com")) {
                                            resolvedUrlCache[serverIndex] = resolvedUrl
                                            initExoPlayer(resolvedUrl)
                                        } else {
                                            resolveEmbedUrl(resolvedUrl, server, serverIndex)
                                        }
                                    } else {
                                        scheduleAutoFail(server.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        Log.d(TAG, "Trying scraper OkHttp resolution for: ${server.name}")
        lifecycleScope.launch {
            val scraperUrl = try {
                WeebFlixApp.instance.scraper.resolveServerVideoUrl(server, episodeUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Scraper resolution error: ${e.message}")
                ""
            }
            if (!isFinishing && scraperUrl.isNotEmpty()) {
                Log.d(TAG, "Scraper resolved: $scraperUrl")
                loadingPlayer.visibility = View.GONE
                tvError.visibility = View.GONE
                if (scraperUrl.contains(".mp4") || scraperUrl.contains(".m3u8") || scraperUrl.contains(".mpd") || scraperUrl.contains("googlevideo.com")) {
                    resolvedUrlCache[serverIndex] = scraperUrl
                    initExoPlayer(scraperUrl)
                } else {
                    resolveEmbedUrl(scraperUrl, server, serverIndex)
                }
            } else {
                Log.d(TAG, "Scraper failed for ${server.name}, trying WebView...")
                resolveWithWebView(server) { resolvedUrl ->
                    runOnUiThread {
                        if (!isFinishing) {
                            if (resolvedUrl.isNotEmpty()) {
                                Log.d(TAG, "WebView resolved: $resolvedUrl")
                                loadingPlayer.visibility = View.GONE
                                tvError.visibility = View.GONE
                                if (resolvedUrl.contains(".mp4") || resolvedUrl.contains(".m3u8") || resolvedUrl.contains(".mpd") || resolvedUrl.contains("googlevideo.com") || resolvedUrl.contains("wibufile.com")) {
                                    Log.d(TAG, "WebView resolved direct URL, playing: $resolvedUrl")
                                    resolvedUrlCache[serverIndex] = resolvedUrl
                                    initExoPlayer(resolvedUrl)
                                } else {
                                    resolveEmbedUrl(resolvedUrl, server, serverIndex)
                                }
                            } else {
                                scheduleAutoFail(server.name)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveEmbedUrl(embedUrl: String, server: VideoServer, serverIndex: Int) {
        Log.d(TAG, "Fetching embed page via WebView: $embedUrl")

        if (embedUrl.contains("blogger.com/video.g") || embedUrl.contains("blogger.com/video-embed")) {
            Log.d(TAG, "Blogger embed detected, going straight to WebView with XHR interception...")
            resolveEmbedUrlViaWebView(embedUrl, server, serverIndex)
            return
        }

        resolveEmbedUrlViaWebView(embedUrl, server, serverIndex)
    }

    @Suppress("DEPRECATION")
    private fun tryBloggerTokenDecode(bloggerUrl: String): String {
        try {
            val tokenParam = Regex("""token=([^&]+)""", RegexOption.IGNORE_CASE).find(bloggerUrl)
                ?.groupValues?.get(1) ?: return ""
            val decoded = android.util.Base64.decode(tokenParam, android.util.Base64.DEFAULT)
            val json = String(decoded, Charsets.UTF_8)
            Log.d(TAG, "Blogger token decoded: ${json.take(500)}")

            val patterns = listOf(
                Regex("""["']url["']\s*:\s*["'](https?://[^"']+)"""),
                Regex("""["']video_url["']\s*:\\s*["'](https?://[^"']+)"""),
                Regex("""["']source["']\s*:\s*["'](https?://[^"']+)"""),
                Regex("""https?://[^\s"']*googlevideo\.com[^\s"']*"""),
                Regex("""https?://[^\s"']*\.mp4[^\s"']*"""),
                Regex("""https?://[^\s"']*\.m3u8[^\s"']*""")
            )
            for (pattern in patterns) {
                val match = pattern.find(json)
                if (match != null) {
                    val url = match.groupValues.getOrElse(1) { match.value }
                    if (url.startsWith("http") && !url.contains("blogger.com")) {
                        return url
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Blogger token decode failed: ${e.message}")
        }
        return ""
    }

    private suspend fun tryBloggerOkHttpExtraction(bloggerUrl: String): String {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = getOkHttpClient(cacheDir)
                val request = okhttp3.Request.Builder()
                    .url(bloggerUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Referer", "https://blogger.com/")
                    .build()
                val response = client.newCall(request).execute()
                val html = response.use { it.body?.string() ?: "" }

                val patterns = listOf(
                    Regex("""["']https?://[^"']*googlevideo\.com/videoplayback[^"']*"""),
                    Regex("""["']https?://[^"']*\.googlevideo\.com[^"']*"""),
                    Regex("""["']https?://[^"']*blogspot\.com/v/[^"']*"""),
                    Regex("""["']https?://[^"']*\.mp4[^"']*"""),
                    Regex("""["']https?://[^"']*\.m3u8[^"']*"""),
                    Regex("""url["']?\s*[:=]\s*["'](https?://[^"']+)""", RegexOption.IGNORE_CASE),
                    Regex("""src["']?\s*[:=]\s*["'](https?://[^"']+)""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://[^"']*video[^"']*\.(?:mp4|m3u8)[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://[^"']+(?:videoplayback|videoplay)[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://r\d+---[a-z0-9-]+\.googlevideo\.com[^"']*)["']"""),
                    Regex("""["'](https?://[^"']*bp\.blogspot\.com[^"']*)"""),
                    Regex("""https?://[^\s"'<>]*googlevideo\.com[^\s"'<>]*"""),
                    Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*"""),
                    Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
                )

                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        var url = match.groupValues.getOrElse(1) { match.value }.trim()
                        url = url.removeSurrounding("\"").removeSurrounding("'")
                        if (url.startsWith("http") && !url.contains("blogger.com")) {
                            Log.d(TAG, "Blogger pattern match: $url")
                            return@withContext url
                        }
                    }
                }

                val scriptPatterns = listOf(
                    Regex("""(?:videoUrl|video_url|streamUrl|stream_url|file|source|src)\s*[=:]\s*["'](https?://[^"']+)""", RegexOption.IGNORE_CASE),
                    Regex(""""url"\s*:\s*"(https?://[^"]+)"""),
                    Regex("""'url'\s*:\s*'(https?://[^']+)'"""),
                    Regex("""https?://[^\s"']*googlevideo\.com[^\s"']*"""),
                    Regex("""https?://[^\s"']+\.mp4[^\s"']*"""),
                    Regex("""https?://[^\s"']+\.m3u8[^\s"']*""")
                )
                for (pattern in scriptPatterns) {
                    val matches = pattern.findAll(html)
                    for (match in matches) {
                        val url = match.groupValues.getOrElse(1) { match.value }.trim()
                        if (url.startsWith("http") && (url.contains("googlevideo") || url.contains(".mp4") || url.contains(".m3u8") || url.contains("blogspot") || url.contains("bp.blogspot"))) {
                            Log.d(TAG, "Blogger script pattern match: $url")
                            return@withContext url
                        }
                    }
                }

                Log.d(TAG, "No Blogger video URL found in HTML (${html.length} bytes)")
                ""
            } catch (e: Exception) {
                Log.e(TAG, "Blogger OkHttp extraction failed: ${e.message}")
                ""
            }
        }
    }

    private fun resolveEmbedUrlViaWebView(embedUrl: String, server: VideoServer, serverIndex: Int) {
        webViewResolving = true
        webViewResolveMode = ResolveMode.EMBED_FETCH
        pendingResolveServer = server
        pendingResolveServerIndex = serverIndex
        resolveGeneration++
        val gen = resolveGeneration

        webViewResolveCallback = { finalUrl ->
            runOnUiThread {
                if (gen != resolveGeneration) return@runOnUiThread
                webViewResolving = false
                webViewResolveMode = ResolveMode.NONE

                if (!isFinishing && finalUrl.isNotEmpty()) {
                    resolvedUrlCache[serverIndex] = finalUrl
                    loadingPlayer.visibility = View.GONE
                    initExoPlayer(finalUrl)
                } else if (!isFinishing) {
                    scheduleAutoFail(server.name)
                }
            }
        }

        Log.d(TAG, "Loading embed URL in WebView: $embedUrl")
        webView?.stopLoading()
        webView?.loadUrl(embedUrl)

        val isBlogger = embedUrl.contains("blogger.com") || embedUrl.contains("bp.blogspot.com")
        val isFiledon = embedUrl.contains("filedon.co")
        val timeoutMs = when {
            isBlogger -> 20000L
            isFiledon -> 15000L
            else -> 10000L
        }

        webView?.postDelayed({
            if (webViewResolving && resolveGeneration == gen) {
                Log.w(TAG, "Embed WebView safety timeout for: $embedUrl")
                if (isBlogger) {
                    Log.d(TAG, "Trying Blogger-specific JS extraction before timeout...")
                    webView?.evaluateJavascript(extractBloggerVideoJs(), null)
                    webView?.postDelayed({
                        if (webViewResolving && resolveGeneration == gen) {
                            webViewResolving = false
                            webViewResolveMode = ResolveMode.NONE
                            webViewResolveCallback?.invoke("")
                            webViewResolveCallback = null
                            pendingResolveServer = null
                        }
                    }, 5000)
                } else if (isFiledon) {
                    Log.d(TAG, "Trying filedon.co-specific JS extraction before timeout...")
                    webView?.evaluateJavascript(extractFiledonVideoJs(), null)
                    webView?.postDelayed({
                        if (webViewResolving && resolveGeneration == gen) {
                            webViewResolving = false
                            webViewResolveMode = ResolveMode.NONE
                            webViewResolveCallback?.invoke("")
                            webViewResolveCallback = null
                            pendingResolveServer = null
                        }
                    }, 5000)
                } else {
                    webViewResolving = false
                    webViewResolveMode = ResolveMode.NONE
                    webViewResolveCallback?.invoke("")
                    webViewResolveCallback = null
                    pendingResolveServer = null
                }
            }
        }, timeoutMs)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun interceptBloggerHtml(videoGUrl: String): android.webkit.WebResourceResponse? {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder().url(videoGUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val response = client.newCall(req).execute()
            val body = response.body?.string() ?: return null
            response.close()

            val xhrScript = """
(function() {
    window._xhrUrlFound = false;
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._url = url;
        return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
        var self = this;
        this.addEventListener('load', function() {
            try {
                if (window._xhrUrlFound) return;
                var text = self.responseText || '';
                if (text.indexOf('googlevideo.com') !== -1) {
                    var match = text.match(/https?:\/\/[^\s"']+googlevideo\.com[^\s"']*/);
                    if (match) {
                        var raw = match[0];
                        console.log('[XHRIntercept] Raw (first 300): ' + raw.substring(0, 300));
                        var videoUrl = raw.replace(/\\u003d/gi, '=').replace(/\\u0026/gi, '&').replace(/\\\//g, '/').replace(/\\\./g, '.').replace(/\\=/g, '=').replace(/\\&/g, '&').replace(/\\/g, '');
                        console.log('[XHRIntercept] Cleaned URL (len=' + videoUrl.length + '): ' + videoUrl.substring(0, 200));
                        window._xhrUrlFound = true;
                        window.AndroidBridge.onUrlFound(videoUrl);
                    }
                }
            } catch(e) { console.log('[XHRIntercept] Error: ' + e.message); }
        });
        return origSend.apply(this, arguments);
    };
    console.log('[XHRIntercept] Script installed');
})();
""".trimIndent()

            val modified = body.replaceFirst("<head>", "<head><script>$xhrScript</script>")
                .replaceFirst("<HEAD>", "<HEAD><script>$xhrScript</script>")

            Log.d(TAG, "Injected XHR interception into video.g HTML (${body.length} -> ${modified.length} bytes)")
            return android.webkit.WebResourceResponse(
                "text/html", "UTF-8",
                java.io.ByteArrayInputStream(modified.toByteArray(Charsets.UTF_8))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to intercept video.g HTML: ${e.message}")
            return null
        }
    }

    private fun extractBloggerVideoJs(): String {
        return """
            (function() {
                var notified = false;
                function notifyUrl(url) {
                    if (notified) return;
                    if (window._xhrUrlFound) { log('XHR already found URL, skipping'); return; }
                    notified = true;
                    console.log('[BloggerExtract] Notifying URL: ' + url);
                    window.AndroidBridge.onUrlFound(url);
                }
                function log(msg) { console.log('[BloggerExtract] ' + msg); }

                function tryExtract(attempt) {
                    if (notified || window._xhrUrlFound) return;

                    log('Attempt ' + attempt + ', checking VIDEO_CONFIG...');
                    try {
                        var vc = window.VIDEO_CONFIG;
                        if (vc && vc.streams) {
                            log('Found VIDEO_CONFIG.streams with ' + vc.streams.length + ' entries');
                            for (var i = 0; i < vc.streams.length; i++) {
                                var s = vc.streams[i];
                                log('Stream ' + i + ': play_url=' + (s.play_url || 'none') + ', url=' + (s.url || 'none'));
                                if (s && s.play_url) { notifyUrl(s.play_url); return; }
                                if (s && s.url) { notifyUrl(s.url); return; }
                            }
                        } else {
                            log('VIDEO_CONFIG not found or no streams');
                        }
                    } catch(e) { log('VIDEO_CONFIG error: ' + e.message); }

                    try {
                        var yt = window.yt && window.yt.config_;
                        if (yt) {
                            var keys = Object.keys(yt);
                            log('yt.config_ keys: ' + keys.join(', '));
                            for (var i = 0; i < keys.length; i++) {
                                var val = yt[keys[i]];
                                if (typeof val === 'string' && val.indexOf('http') === 0 && val.indexOf('googlevideo') !== -1) {
                                    log('Found googlevideo in yt.config_: ' + val.substring(0, 100));
                                    notifyUrl(val); return;
                                }
                            }
                        }
                    } catch(e) { log('yt.config_ error: ' + e.message); }

                    try {
                        var scripts = document.querySelectorAll('script');
                        log('Scanning ' + scripts.length + ' script tags...');
                        for (var i = 0; i < scripts.length; i++) {
                            var txt = scripts[i].textContent || '';
                            if (txt.length > 0 && attempt === 0) {
                                log('Script[' + i + '] length=' + txt.length + ', preview=' + txt.substring(0, 200));
                            }
                            var vidConfig = txt.match(/VIDEO_CONFIG\s*=\s*(\{[\s\S]*?\});/);
                            if (vidConfig && vidConfig[1]) {
                                log('Found VIDEO_CONFIG in script tag');
                                var streams = vidConfig[1].match(/"play_url"\s*:\s*"([^"]+)"/);
                                if (streams && streams[1]) { log('play_url: ' + streams[1]); notifyUrl(streams[1]); return; }
                                streams = vidConfig[1].match(/"url"\s*:\s*"([^"]+)"/);
                                if (streams && streams[1] && streams[1].indexOf('http') === 0) { log('url: ' + streams[1]); notifyUrl(streams[1]); return; }
                            }
                            var gvMatch = txt.match(/https?:\/\/[^\s"']+googlevideo\.com[^\s"']*/);
                            if (gvMatch && gvMatch[0]) { log('Found googlevideo URL in script: ' + gvMatch[0].substring(0, 100)); notifyUrl(gvMatch[0]); return; }
                            var mp4Match = txt.match(/https?:\/\/[^\s"']+\.mp4[^\s"']*/);
                            if (mp4Match && mp4Match[0]) { log('Found .mp4 URL in script: ' + mp4Match[0].substring(0, 100)); notifyUrl(mp4Match[0]); return; }
                        }
                    } catch(e) { log('Script scan error: ' + e.message); }

                    try {
                        var iframes = document.querySelectorAll('iframe');
                        log('Found ' + iframes.length + ' iframes');
                        for (var i = 0; i < iframes.length; i++) {
                            var src = iframes[i].src || iframes[i].getAttribute('src') || '';
                            log('iframe[' + i + '] src=' + src.substring(0, 200));
                            if (src.indexOf('.mp4') !== -1 || src.indexOf('googlevideo') !== -1 || src.indexOf('videoplayback') !== -1) {
                                notifyUrl(src); return;
                            }
                            if (src.indexOf('blogger.com/video.g') !== -1 || src.indexOf('blogger.com/video-embed') !== -1) {
                                log('Found blogger video iframe, sending to bridge: ' + src);
                                try { window.AndroidBridge.onIframeFound(src); } catch(e) {}
                            }
                        }
                    } catch(e) { log('iframe scan error: ' + e.message); }

                    try {
                        var videos = document.querySelectorAll('video, video source, source');
                        for (var i = 0; i < videos.length; i++) {
                            var s = videos[i].src || videos[i].getAttribute('src') || videos[i].currentSrc || '';
                            if (s && s.indexOf('http') === 0 && (s.indexOf('.mp4') !== -1 || s.indexOf('googlevideo') !== -1)) {
                                notifyUrl(s); return;
                            }
                        }
                    } catch(e) {}

                    try {
                        var entries = performance.getEntriesByType('resource');
                        if (attempt === 0) log('performance entries: ' + entries.length);
                        for (var i = 0; i < entries.length; i++) {
                            var name = entries[i].name || '';
                            if (name.indexOf('googlevideo') !== -1 || name.indexOf('.mp4') !== -1 || name.indexOf('videoplayback') !== -1) {
                                log('Found in performance: ' + name.substring(0, 100));
                                notifyUrl(name); return;
                            }
                        }
                    } catch(e) {}

                    if (attempt < 5) {
                        setTimeout(function() { tryExtract(attempt + 1); }, 1000);
                    } else {
                        log('No video URL found after ' + (attempt + 1) + ' attempts');
                        notifyUrl('');
                    }
                }
                tryExtract(0);
            })();
        """.trimIndent()
    }

    private fun extractFiledonVideoJs(): String {
        return """
            (function() {
                function notifyUrl(url) {
                    window.AndroidBridge.onUrlFound(url);
                }
                try {
                    var scripts = document.querySelectorAll('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var txt = scripts[i].textContent || '';
                        var patterns = [
                            /["']file["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)/i,
                            /["']source["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)/i,
                            /["']src["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)/i,
                            /["']url["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)/i,
                            /["']video_url["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)/i,
                            /https?:\/\/[^\s"']+\.mp4[^\s"']*/i,
                            /https?:\/\/[^\s"']+\.m3u8[^\s"']*/i
                        ];
                        for (var p = 0; p < patterns.length; p++) {
                            var match = txt.match(patterns[p]);
                            if (match && match[1]) { notifyUrl(match[1]); return; }
                            if (match && match[0] && match[0].indexOf('http') === 0) { notifyUrl(match[0]); return; }
                        }
                    }
                } catch(e) {}
                try {
                    var videos = document.querySelectorAll('video, video source, source');
                    for (var i = 0; i < videos.length; i++) {
                        var s = videos[i].src || videos[i].getAttribute('src') || videos[i].currentSrc || '';
                        if (s && s.indexOf('http') === 0 && (s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1)) {
                            notifyUrl(s); return;
                        }
                    }
                } catch(e) {}
                try {
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        var src = iframes[i].src || iframes[i].getAttribute('src') || '';
                        if (src && (src.indexOf('.mp4') !== -1 || src.indexOf('.m3u8') !== -1)) {
                            notifyUrl(src); return;
                        }
                    }
                } catch(e) {}
                try {
                    var entries = performance.getEntriesByType('resource');
                    for (var i = 0; i < entries.length; i++) {
                        var name = entries[i].name || '';
                        if (name.indexOf('.mp4') !== -1 || name.indexOf('.m3u8') !== -1) {
                            notifyUrl(name); return;
                        }
                    }
                } catch(e) {}
                try {
                    if (window.jwplayer) {
                        var item = window.jwplayer().getPlaylistItem ? window.jwplayer().getPlaylistItem() : null;
                        if (item && item.file) { notifyUrl(item.file); return; }
                    }
                } catch(e) {}
                notifyUrl('');
            })();
        """.trimIndent()
    }

    // ===== PiP =====

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                isPipMode = true
                hideControls()
            } catch (e: Exception) {
                Toast.makeText(this, "PiP not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        isPipMode = isInPipMode
        if (isInPipMode) {
            topBar.visibility = View.GONE; bottomBar.visibility = View.GONE; centerControls.visibility = View.GONE
            btnSkipOpening.visibility = View.GONE; btnSkipOutro.visibility = View.GONE; autoPlayOverlay.visibility = View.GONE
            gestureOverlay.visibility = View.GONE
        } else {
            gestureOverlay.visibility = View.VISIBLE
        }
    }

    // ===== Lifecycle =====

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 500)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
    }

    override fun onDestroy() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
        exoPlayer?.release()
        exoPlayer = null
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
