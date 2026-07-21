package com.weebflix.app.ui.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
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
import android.view.ViewGroup
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.VideoServer
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
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
            settings.blockNetworkImage = true
            settings.loadsImagesAutomatically = false

            addJavascriptInterface(WebViewBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (webViewResolving) {
                        Log.d(TAG, "WebView page loaded: $url, injecting server click...")
                        injectServerClick()
                    }
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "WebView error: $description at $failingUrl")
                    if (webViewResolving) {
                        webViewResolving = false
                        webViewResolveCallback?.invoke("")
                        webViewResolveCallback = null
                    }
                }
            }
            webChromeClient = WebChromeClient()

            visibility = View.INVISIBLE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        playerContainer.addView(webView)
    }

    private fun resolveWithWebView(server: VideoServer, callback: (String) -> Unit) {
        if (webViewResolving) {
            callback("")
            return
        }
        webViewResolving = true
        webViewResolveCallback = callback
        pendingResolveServer = server

        Log.d(TAG, "WebView resolving server: ${server.name}, loading episode page...")
        webView?.loadUrl(episodeUrl)
    }

    private var pendingResolveServer: VideoServer? = null

    private fun injectServerClick() {
        val server = pendingResolveServer ?: return
        val nume = server.dataNume

        val js = """
            (function() {
                var serverEl = document.querySelector('[data-nume="$nume"]');
                if (!serverEl) {
                    window.AndroidBridge.onUrlFound('');
                    return;
                }
                serverEl.click();
                var attempts = 0;
                var maxAttempts = 30;
                var checkInterval = setInterval(function() {
                    attempts++;
                    var embed = document.getElementById('player_embed');
                    if (!embed) {
                        if (attempts >= maxAttempts) {
                            clearInterval(checkInterval);
                            window.AndroidBridge.onUrlFound('');
                        }
                        return;
                    }
                    var html = embed.innerHTML.trim();
                    if (html.length < 20 && attempts < maxAttempts) return;
                    clearInterval(checkInterval);
                    var iframe = embed.querySelector('iframe');
                    if (iframe && iframe.src) {
                        window.AndroidBridge.onUrlFound(iframe.src);
                        return;
                    }
                    var video = embed.querySelector('video source') || embed.querySelector('video');
                    if (video) {
                        var src = video.src || video.getAttribute('src') || '';
                        if (src) {
                            window.AndroidBridge.onUrlFound(src);
                            return;
                        }
                    }
                    var links = embed.querySelectorAll('a[href*=".mp4"], a[href*=".m3u8"], a[href*=".mpd"]');
                    if (links.length > 0) {
                        window.AndroidBridge.onUrlFound(links[0].href);
                        return;
                    }
                    window.AndroidBridge.onUrlFound('');
                }, 500);
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    inner class WebViewBridge {
        @JavascriptInterface
        fun onUrlFound(url: String?) {
            val resolvedUrl = url ?: ""
            Log.d(TAG, "WebView resolved URL: $resolvedUrl")
            runOnUiThread {
                webViewResolving = false
                val callback = webViewResolveCallback
                webViewResolveCallback = null
                pendingResolveServer = null
                callback?.invoke(resolvedUrl)
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initExoPlayer(videoUrl: String) {
        exoPlayer?.release()

        val cacheDir = java.io.File(cacheDir, "exo_player_cache")
        val cache = androidx.media3.datasource.cache.SimpleCache(
            cacheDir,
            androidx.media3.datasource.cache.NoOpCacheEvictor(),
            androidx.media3.database.StandaloneDatabaseProvider(this)
        )

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .cache(okhttp3.Cache(java.io.File(cacheDir, "okhttp_cache"), 100L * 1024 * 1024))
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .addHeader("Referer", ProviderConfig.baseUrl)
                    .addHeader("Connection", "keep-alive")
                    .build())
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50_000,   // minBufferMs — YouTube-like aggressive pre-buffer
                120_000,  // maxBufferMs — keep 2 min in buffer
                2_500,    // bufferForPlaybackMs — start playing after 2.5s
                5_000     // bufferForPlaybackAfterRebufferMs — resume after 5s
            )
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
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
                            val nextIndex = currentServerIndex + 1
                            if (nextIndex < servers.size) {
                                tvError.visibility = View.VISIBLE
                                tvError.text = "Server ${servers[currentServerIndex].name} gagal.\nMencoba server berikutnya..."
                                tvError.postDelayed({
                                    if (!isFinishing) {
                                        currentServerIndex = nextIndex
                                        updateServerUI()
                                        loadServer(nextIndex)
                                    }
                                }, 1500)
                            } else {
                                tvError.visibility = View.VISIBLE
                                tvError.text = "Semua server gagal. Coba pilih server lain."
                            }
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
                hideBrightnessIndicator()
                hideVolumeIndicator()
                if (isSeekingGesture) {
                    isSeekingGesture = false
                    hideSeekIndicator()
                }
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

        val adjustedOutroStart = (totalSec - 130f).coerceAtLeast(skipOutroStart.toFloat())
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
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", nextEpisodeUrl)
                putExtra("title", nextEpisodeTitle)
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
        loadingPlayer.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        seekBar.progress = 0
        tvCurrentTime.text = "00:00"
        tvTotalTime.text = "00:00"

        val serverIndex = servers.indexOf(server)
        val cachedUrl = resolvedUrlCache[serverIndex]
        if (cachedUrl != null && cachedUrl.isNotEmpty()) {
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

        Log.d(TAG, "Resolving via WebView for server: ${server.name}")
        resolveWithWebView(server) { resolvedUrl ->
            runOnUiThread {
                if (!isFinishing) {
                    if (resolvedUrl.isNotEmpty()) {
                        Log.d(TAG, "WebView resolved: $resolvedUrl")
                        resolvedUrlCache[serverIndex] = resolvedUrl
                        loadingPlayer.visibility = View.GONE
                        tvError.visibility = View.GONE

                        if (resolvedUrl.contains(".mp4") || resolvedUrl.contains(".m3u8") || resolvedUrl.contains(".mpd")) {
                            initExoPlayer(resolvedUrl)
                        } else {
                            resolveEmbedUrl(resolvedUrl, server, serverIndex)
                        }
                    } else {
                        val nextIndex = currentServerIndex + 1
                        if (nextIndex < servers.size) {
                            tvError.visibility = View.VISIBLE
                            tvError.text = "Server ${server.name} gagal. Mencoba server berikutnya..."
                            tvError.postDelayed({
                                if (!isFinishing) {
                                    currentServerIndex = nextIndex
                                    updateServerUI()
                                    loadServer(nextIndex)
                                }
                            }, 1500)
                        } else {
                            showError("Tidak dapat memutar dari server ${server.name}.\nKlik untuk pilih server lain.")
                        }
                    }
                }
            }
        }
    }

    private fun resolveEmbedUrl(embedUrl: String, server: VideoServer, serverIndex: Int) {
        Log.d(TAG, "Fetching embed page via WebView: $embedUrl")
        webViewResolving = true

        webView?.evaluateJavascript("") {}

        webView?.loadUrl(embedUrl)

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!webViewResolving) return

                val extractJs = """
                    (function() {
                        var video = document.querySelector('video source') || document.querySelector('video');
                        if (video) {
                            var src = video.src || video.getAttribute('src') || '';
                            if (src) { window.AndroidBridge.onUrlFound(src); return; }
                        }
                        var iframes = document.querySelectorAll('iframe');
                        for (var i = 0; i < iframes.length; i++) {
                            if (iframes[i].src && iframes[i].src.indexOf('about:blank') === -1) {
                                window.AndroidBridge.onUrlFound(iframes[i].src);
                                return;
                            }
                        }
                        var all = document.body.innerHTML;
                        var m = all.match(/["'](?:file|source|src|video_url)["']\s*[:=]\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/);
                        if (m) { window.AndroidBridge.onUrlFound(m[1]); return; }
                        m = all.match(/(https?:\/\/[^\s"'<>]+\.(?:mp4|m3u8|mpd)(?:\?[^\s"'<>]*)?)/);
                        if (m) { window.AndroidBridge.onUrlFound(m[1]); return; }
                        window.AndroidBridge.onUrlFound('');
                    })();
                """.trimIndent()
                view?.evaluateJavascript(extractJs, null)
            }
        }

        webViewResolveCallback = { finalUrl ->
            runOnUiThread {
                webViewResolving = false
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (webViewResolving) {
                            injectServerClick()
                        }
                    }
                }

                if (!isFinishing && finalUrl.isNotEmpty()) {
                    resolvedUrlCache[serverIndex] = finalUrl
                    loadingPlayer.visibility = View.GONE
                    initExoPlayer(finalUrl)
                } else if (!isFinishing) {
                    val nextIndex = currentServerIndex + 1
                    if (nextIndex < servers.size) {
                        tvError.visibility = View.VISIBLE
                        tvError.text = "Server ${server.name} gagal. Mencoba server berikutnya..."
                        tvError.postDelayed({
                            if (!isFinishing) {
                                currentServerIndex = nextIndex
                                updateServerUI()
                                loadServer(nextIndex)
                            }
                        }, 1500)
                    } else {
                        showError("Semua server gagal.\nKlik untuk pilih server lain.")
                    }
                }
            }
        }
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

    @Suppress("DEPRECATION")
    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() { super.onBackPressed() }

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
