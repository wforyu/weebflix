package com.weebflix.app.ui.player

import android.annotation.SuppressLint
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
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.VideoServer
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var playerContainer: FrameLayout
    private lateinit var gestureOverlay: FrameLayout
    private lateinit var loadingPlayer: ProgressBar
    private lateinit var tvAnimeTitle: TextView
    private lateinit var tvEpisodeTitle: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var centerControls: FrameLayout
    private lateinit var btnCenterPlayPause: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrevEp: ImageView
    private lateinit var btnNextEp: ImageView
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

    private var episodeUrl: String = ""
    private var episodeTitle: String = ""
    private var episodeNumber: String = ""
    private var animeTitle: String = ""
    private var servers: List<VideoServer> = emptyList()
    private var currentServerIndex: Int = 0
    private var isPlaying: Boolean = true

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val maxVolume by lazy { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    private var currentVolume: Int = 0
    private var currentBrightness: Float = 0.5f

    private var controlsVisible: Boolean = true
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideControls() }

    private var isSeekingGesture: Boolean = false
    private var seekDelta: Long = 0L

    // Skip opening timing (seconds)
    private var skipOpeningStart: Int = 90
    private var skipOpeningEnd: Int = 120

    // Skip outro timing (seconds) - outro typically starts ~30s before end
    private var skipOutroStart: Int = 1270
    private var skipOutroEnd: Int = 1400

    private var currentTimeSeconds: Float = 0f

    // WebView estimated playback time tracking
    private val timeUpdateHandler = Handler(Looper.getMainLooper())
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                currentTimeSeconds += 1f
                updateSeekBarFromTime()
                checkSkipButtonsVisibility()
                checkAutoPlay()
            }
            timeUpdateHandler.postDelayed(this, 1000)
        }
    }

    // Auto-play next episode
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

    @SuppressLint("SetJavaScriptEnabled")
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
        setupWebView()
        setupGestureDetector()
        setupControls()
        setupSeekBar()

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
        gestureOverlay = findViewById(R.id.gestureOverlay)
        loadingPlayer = findViewById(R.id.loadingPlayer)
        tvAnimeTitle = findViewById(R.id.tvAnimeTitle)
        tvEpisodeTitle = findViewById(R.id.tvEpisodeTitle)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        centerControls = findViewById(R.id.centerControls)
        btnCenterPlayPause = findViewById(R.id.btnCenterPlayPause)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevEp = findViewById(R.id.btnPrevEp)
        btnNextEp = findViewById(R.id.btnNextEp)
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
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.displayZoomControls = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loadingPlayer.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadingPlayer.visibility = View.GONE
                    injectVideoAutoplay()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        loadingPlayer.visibility = View.GONE
                    }
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                }
            }
        }
        playerContainer.addView(webView)
    }

    private fun injectVideoAutoplay() {
        val js = """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].play().catch(function(){});
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
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
                    val viewWidth = gestureOverlay.width
                    val startX = e1.x

                    if (startX < viewWidth / 2f) {
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

        btnPrevEp.setOnClickListener {
            if (currentServerIndex > 0) {
                currentServerIndex--
                loadServer(currentServerIndex)
                updateServerUI()
            }
        }

        btnNextEp.setOnClickListener {
            if (currentServerIndex < servers.size - 1) {
                currentServerIndex++
                loadServer(currentServerIndex)
                updateServerUI()
            }
        }

        btnPip.setOnClickListener { enterPipMode() }

        btnFullscreen.setOnClickListener { /* Already landscape fullscreen */ }

        tvServerName.setOnClickListener { showServerPickerDialog() }

        btnSkipOpening.setOnClickListener {
            val skipTo = skipOpeningEnd.toFloat()
            seekTo(skipTo)
            currentTimeSeconds = skipTo
            updateSeekBarFromTime()
            btnSkipOpening.visibility = View.GONE
        }

        btnSkipOutro.setOnClickListener {
            val skipTo = skipOutroEnd.toFloat()
            seekTo(skipTo)
            currentTimeSeconds = skipTo
            updateSeekBarFromTime()
            btnSkipOutro.visibility = View.GONE
        }

        btnCancelAutoPlay.setOnClickListener {
            cancelAutoPlay()
        }

        btnPlayNow.setOnClickListener {
            autoPlayHandler.removeCallbacks(autoPlayRunnable)
            navigateToNextEpisode()
        }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val totalTime = getTimeFromSeekBar()
                    tvCurrentTime.text = formatTime(progress.toFloat() / seekBar!!.max * totalTime)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                showControls()
                timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar == null) return
                val totalTime = getTimeFromSeekBar()
                val progress = seekBar.progress.toFloat() / seekBar.max
                val seekToTime = progress * totalTime
                currentTimeSeconds = seekToTime
                seekTo(seekToTime)
                scheduleAutoHide()
                if (isPlaying) {
                    timeUpdateHandler.postDelayed(timeUpdateRunnable, 1000)
                }
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
        if (seekDelta >= 0) {
            seekIcon.setImageResource(R.drawable.ic_player_skip_forward)
            seekText.text = "+${abs(seekDelta)}s"
        } else {
            seekIcon.setImageResource(R.drawable.ic_player_skip_backward)
            seekText.text = "-${abs(seekDelta)}s"
        }
        showSeekIndicator(seekDelta >= 0, seekText.text.toString())
    }

    private fun seekBy(seconds: Float) {
        val newTime = (currentTimeSeconds + seconds).coerceAtLeast(0f)
        currentTimeSeconds = newTime
        seekTo(newTime)
        updateSeekBarFromTime()
    }

    private fun seekTo(timeSeconds: Float) {
        val js = """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    try { videos[i].currentTime = $timeSeconds; } catch(e) {}
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying

        val js = """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    try {
                        if ($isPlaying) { videos[i].play(); } else { videos[i].pause(); }
                    } catch(e) {}
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)

        val icon = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
        btnPlayPause.setImageResource(icon)
        btnCenterPlayPause.setImageResource(icon)

        if (isPlaying) {
            timeUpdateHandler.postDelayed(timeUpdateRunnable, 1000)
        } else {
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        }

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

    private fun hideBrightnessIndicator() {
        brightnessIndicator.visibility = View.GONE
    }

    private fun showVolumeIndicator() {
        volumeIndicator.visibility = View.VISIBLE
        brightnessIndicator.visibility = View.GONE
        seekIndicator.visibility = View.GONE
    }

    private fun hideVolumeIndicator() {
        volumeIndicator.visibility = View.GONE
    }

    private fun showSeekIndicator(forward: Boolean, text: String) {
        seekIndicator.visibility = View.VISIBLE
        seekText.text = text
        if (forward) {
            seekIcon.setImageResource(R.drawable.ic_player_skip_forward)
        } else {
            seekIcon.setImageResource(R.drawable.ic_player_skip_backward)
        }
    }

    private fun hideSeekIndicator() {
        seekIndicator.visibility = View.GONE
    }

    // ===== Skip Opening / Outro =====

    private fun checkSkipButtonsVisibility() {
        val totalTime = getTimeFromSeekBar()

        // Skip Opening
        val inOpeningRange = currentTimeSeconds in skipOpeningStart.toFloat()..skipOpeningEnd.toFloat()
        if (inOpeningRange && controlsVisible) {
            btnSkipOpening.visibility = View.VISIBLE
        } else {
            btnSkipOpening.visibility = View.GONE
        }

        // Skip Outro
        val adjustedOutroStart = (totalTime - 130f).coerceAtLeast(skipOutroStart.toFloat())
        val inOutroRange = currentTimeSeconds >= adjustedOutroStart && currentTimeSeconds < totalTime
        if (inOutroRange && controlsVisible && nextEpisodeUrl.isNotEmpty()) {
            btnSkipOutro.visibility = View.VISIBLE
        } else {
            btnSkipOutro.visibility = View.GONE
        }
    }

    // ===== Auto-play Next Episode =====

    private fun checkAutoPlay() {
        val totalTime = getTimeFromSeekBar()
        val timeRemaining = totalTime - currentTimeSeconds

        // Show auto-play overlay in last 10 seconds
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
                putExtra("nextEpisodeUrl", "") // Will be fetched in new instance
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Episode selanjutnya tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== Episode Navigation (scrape from page) =====

    private fun fetchEpisodeNavigation() {
        if (nextEpisodeUrl.isNotEmpty()) return

        lifecycleScope.launch {
            try {
                val nav = WeebFlixApp.instance.scraper.getEpisodeNavigation(episodeUrl)
                if (!isFinishing) {
                    if (nav.nextEpisodeUrl.isNotEmpty()) {
                        nextEpisodeUrl = nav.nextEpisodeUrl
                        nextEpisodeTitle = nav.nextEpisodeTitle
                    }
                }
            } catch (_: Exception) {
                // Navigation not available, that's fine
            }
        }
    }

    // ===== Time Tracking =====

    private fun updateSeekBarFromTime() {
        val totalTime = getTimeFromSeekBar()
        if (totalTime > 0) {
            val progress = (currentTimeSeconds / totalTime * seekBar.max).toInt().coerceIn(0, seekBar.max)
            seekBar.progress = progress
            tvCurrentTime.text = formatTime(currentTimeSeconds)
            tvTotalTime.text = formatTime(totalTime)
        }
    }

    private fun getTimeFromSeekBar(): Float {
        return 1400f
    }

    private fun formatTime(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    // ===== Screen Brightness =====

    private fun getScreenBrightness(): Float {
        val lp = window.attributes
        if (lp.screenBrightness < 0) {
            return try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Settings.SettingNotFoundException) {
                0.5f
            }
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
                        loadUrl(episodeUrl)
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    Toast.makeText(this@PlayerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    loadUrl(episodeUrl)
                }
            }
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
        if (index in servers.indices) {
            val server = servers[index]
            val url = if (server.url.startsWith("http")) server.url
            else "${ProviderConfig.baseUrl}${server.url}"
            loadUrl(url)
        }
    }

    private fun loadUrl(url: String) {
        loadingPlayer.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    // ===== PiP =====

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            try {
                enterPictureInPictureMode(params)
                isPipMode = true
                hideControls()
            } catch (e: Exception) {
                Toast.makeText(this, "PiP not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        isPipMode = isInPipMode
        if (isInPipMode) {
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            centerControls.visibility = View.GONE
            btnSkipOpening.visibility = View.GONE
            btnSkipOutro.visibility = View.GONE
            autoPlayOverlay.visibility = View.GONE
            gestureOverlay.visibility = View.GONE
        } else {
            gestureOverlay.visibility = View.VISIBLE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    // ===== Back Press =====

    @Suppress("DEPRECATION")
    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ===== Lifecycle =====

    override fun onResume() {
        super.onResume()
        webView.onResume()
        if (isPlaying) {
            timeUpdateHandler.postDelayed(timeUpdateRunnable, 1000)
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
    }

    override fun onDestroy() {
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
