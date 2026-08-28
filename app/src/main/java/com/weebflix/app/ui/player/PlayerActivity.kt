package com.weebflix.app.ui.player

import android.app.PendingIntent
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
import android.os.SystemClock
import android.text.TextWatcher
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.ParametersBuilder
import androidx.media3.ui.PlayerView
import androidx.media3.session.MediaSession
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.VideoServer
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.scraper.YouTubeScraper
import com.weebflix.app.data.scraper.YouTubeVideo
import com.weebflix.app.ui.youtube.YouTubeChannelActivity
import com.weebflix.app.ui.youtube.adapter.YouTubeFeedAdapter
import com.weebflix.app.ui.youtube.adapter.YouTubeFormat
import com.weebflix.app.ui.util.TvUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        // Hold-seek arming slop (~25px, squared) — any real finger drift beyond this cancels the
        // pending hold and the drag is classified normally (volume/brightness/seek). Deliberately
        // LARGER than GestureDetector's internal touch-slop so our 320ms hold timer keeps claim
        // priority over the scroll classifier instead of the other way round.
        private const val HOLD_SLOP_UNSIGNED_SQUARED = 625f

        private val drakorP2pHosts: MutableSet<String> = java.util.Collections.synchronizedSet(java.util.LinkedHashSet<String>())

        private val BLOCKED_DOMAINS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagmanager.com", "google-analytics.com", "googletagservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "yandex", "mc.yandex", "yastatic.net",
            "propellerads.com", "popads.net", "popunder",
            "exoclick.com", "adsterra.com", "adserver",
            "trafficjunky.com", "clicksure.com",
            "ad.fox", "adpartner", "adsrv",
            "mvp789", "zafn9604", "install.js",
            "cpm", "revive", "mgid", "taboola",
            "outbrain", "infolinks", "facebook.com/tr",
            "shopee.co.id", "shopee.", "lazada.", "tokopedia.",
            "analytics.", "fractionfridgejudiciary", "shroudedspoutunleveled",
            "egret.com", "egret",
            "adtrue", "adnow", "adreactor", "exosrv.com",
            "pixel.quantserve",
            "juicyads", "adbucks", "clicksor", "bidvertiser",
            "popcash", "adtiger", "adfox", "adgain",
            "onclickads", "adf.ly", "ouo.io", "sh.st",
            "adfoc.us", "linkbucks", "admyad",
            "static.fbadd", "adpushup", "adthrive",
            "media.net", "adversal", "adblade",
            "adbutler", "adventory", "adglare",
            "adk2", "adk3", "adk4", "adk5",
            "snapads", "tremorhub", "video-ad",
            "prebid", "openx.net", "rubicon",
            "criteo", "pubmatic", "appnexus",
            "casalemedia", "sharethrough", "indexww",
            "sovrn", "amazon-adsystem", "adnxs",
            "adsafeprotected", "moatads", "teads",
            "adroll", "adsymptotic", "bluekai",
            "exelator", "lotame", "krxd",
            "demdex", "rlcdn", "crwdcntrl",
            "addthis", "disqus", "scorecardresearch",
            "quantserve", "comscore", "parsely",
            "admantx", "nuffnang", "clickky",
            "ad6media", "adkreator", "adnium",
            "nativeads", "adspirit", "adrecord",
            "adtng", "advangelists", "advariant",
            "bidfluence", "adventurefeeder", "pixel.quantserve",
            "2mdn.net", "adobedc.net", "demand.supply",
            "adacado", "adform", "adition",
            "adloox", "adnami", "adplus",
            "adstir", "adtelligent", "adzerk",
            "bidswitch", "conversantmedia", "districtm",
            "improvedigital", "indexexchange", "loopme",
            "mediafuse", "onetag", "optimizely",
            "pexo.co", "plugrush", "pornhub",
            "revcontent", "sekindo", "skimresources",
            "smartyads", "sortable", "spotxchange",
            "stickyadstv", "streamrail", "triplelift",
            "undertone", "vidazoo", "videology",
            "visx.net", "adtheorent", "adyoulike",
            "emxdgt.com", "lngtd.com", "adhigh",
            "adtima", "admicro", "dable",
            "popin.cc", "popin", "revenuehits",
            "adkengage", "adk2x", "g.doubleclick"
        )

        private var simpleCache: androidx.media3.datasource.cache.SimpleCache? = null
        private var sharedOkHttpClient: OkHttpClient? = null

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun getSimpleCache(context: Context): androidx.media3.datasource.cache.SimpleCache {
            return simpleCache ?: synchronized(this) {
                simpleCache ?:                     androidx.media3.datasource.cache.SimpleCache(
                    java.io.File(context.applicationContext.cacheDir, "exo_player_cache"),
                    androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024),
                    androidx.media3.database.StandaloneDatabaseProvider(context.applicationContext)
                ).also { simpleCache = it }
            }
        }

        fun getOkHttpClient(cacheDir: java.io.File): OkHttpClient {
            return sharedOkHttpClient ?: synchronized(this) {
                sharedOkHttpClient ?: run {
                    val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    })
                    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                        init(null, trustAllCerts, java.security.SecureRandom())
                    }
                    OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                        .hostnameVerifier { _, _ -> true }
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
                            } else if (chain.request().url.host.contains("abysscdn.com") || chain.request().url.host.contains("hydrax") || chain.request().url.host.contains("drakor.bid")) {
                                request.addHeader("Referer", "https://drakor.kita.mobi/")
                                    .addHeader("Origin", "https://drakor.kita.mobi")
                            } else if (chain.request().url.host.contains("turboviplay.com")) {
                                request.addHeader("Referer", "https://turbovidhls.com/")
                                    .addHeader("Origin", "https://turbovidhls.com")
                                request.addHeader("Cache-Control", "no-cache")
                            } else if (chain.request().url.host.contains("cloudflarestorage.com")) {
                                request.addHeader("Referer", "https://filedon.co/")
                                    .addHeader("Origin", "https://filedon.co")
                            } else if (chain.request().url.host.contains("anichin.stream") || chain.request().url.host.contains("1a-1791.com")) {
                                request.addHeader("Referer", "https://anichin.stream/")
                                    .addHeader("Origin", "https://anichin.stream")
                            } else if (chain.request().url.host.contains("drakorkita.stream") || chain.request().url.host in drakorP2pHosts) {
                                request.addHeader("Referer", "https://drakorkita.stream/")
                                    .addHeader("Origin", "https://drakorkita.stream")
                            } else if (chain.request().url.host.contains("surrit.com")) {
                                request.addHeader("Referer", "https://missav.ws/")
                                    .addHeader("Origin", "https://missav.ws")
                            }
                            chain.proceed(request.build())
                        }
                        .addInterceptor { chain ->
                            val reqUrl = chain.request().url.toString()
                            val isTurboSegment = reqUrl.contains("turboviplay.com") && (reqUrl.contains(".ts") || reqUrl.contains("data3/") || reqUrl.contains(".m3u8"))
                            val isGoogleCdn = reqUrl.contains("googleusercontent.com") || reqUrl.contains("googlevideo.com")
                            if (isTurboSegment || isGoogleCdn) {
                                java.lang.Thread.sleep(120L)
                            }
                            var response = chain.proceed(chain.request())
                            var retries = 0
                            val maxRetries = 3
                            while (!response.isSuccessful && retries < maxRetries) {
                                retries++
                                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                                val code = response.code
                                response.close()
                                val backoff = if (retryAfter != null) {
                                    (retryAfter * 1000L).coerceAtMost(10000L)
                                } else if (code == 429) {
                                    retries * 5000L
                                } else {
                                    retries * 2000L
                                }
                                Log.w(TAG, "HTTP $code on $reqUrl, retry $retries/$maxRetries in ${backoff}ms")
                                java.lang.Thread.sleep(backoff)
                                response = chain.proceed(chain.request())
                            }
                            if (isTurboSegment && response.isSuccessful) {
                                val ct = response.header("Content-Type") ?: ""
                                if (ct.contains("text/html") || ct.contains("application/json")) {
                                    Log.w(TAG, "turboviplay got non-video Content-Type ($ct), retrying...")
                                    response.close()
                                    java.lang.Thread.sleep(3000L)
                                    response = chain.proceed(chain.request())
                                    retries++
                                    while (!response.isSuccessful && retries < maxRetries) {
                                        retries++
                                        response.close()
                                        java.lang.Thread.sleep(retries * 3000L)
                                        response = chain.proceed(chain.request())
                                    }
                                }
                            }
                            response
                        }
                        .build().also { sharedOkHttpClient = it }
                }
            }
        }
    }

    private val isTvMode by lazy { TvUtils.isTv(this) }

    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var gestureOverlay: FrameLayout
    private lateinit var loadingPlayer: ProgressBar
    private lateinit var tvLoadingProgress: TextView
    private lateinit var tvLoadingHint: TextView
    private lateinit var tvError: TextView
    private lateinit var tvAnimeTitle: TextView
    private lateinit var tvEpisodeTitle: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var centerControls: FrameLayout
    private lateinit var btnCenterPlayPause: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnRewind: ImageView
    private lateinit var btnForward: ImageView
    private lateinit var btnPrevEpisodeNav: TextView
    private lateinit var btnNextEpisodeNav: TextView
    private lateinit var btnYtPrev: ImageView
    private lateinit var btnYtNext: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var btnPip: ImageView
    private lateinit var btnFullscreen: ImageView
    private lateinit var btnQuality: ImageView
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
    private lateinit var webViewPlayerControls: FrameLayout
    private lateinit var btnWebViewBack: ImageView
    private lateinit var wvTopBar: LinearLayout
    private lateinit var tvWvAnimeTitle: TextView
    private lateinit var tvWvEpisodeTitle: TextView
    private lateinit var tvWvServerBadge: TextView
    private lateinit var wvCenterControls: FrameLayout
    private lateinit var btnWvCenterPlayPause: ImageView
    private lateinit var wvBottomBar: LinearLayout
    private lateinit var tvWvCurrentTime: TextView
    private lateinit var tvWvTotalTime: TextView
    private lateinit var wvSeekBar: SeekBar
    private lateinit var btnWvPlayPause: ImageView
    private lateinit var btnWvRewind: ImageView
    private lateinit var btnWvForward: ImageView
    private lateinit var btnWvFullscreen: ImageView
    private lateinit var btnWvPip: ImageView
    private lateinit var wvLoadingSpinner: ProgressBar
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
    private lateinit var seekTimeText: TextView
    private lateinit var zoomIndicator: LinearLayout
    private lateinit var zoomText: TextView
    private var pinchOverlayRef: View? = null
    private val zoomHideHandler = Handler(Looper.getMainLooper())
    private val zoomHideRunnable = Runnable { if (::zoomIndicator.isInitialized) zoomIndicator.visibility = View.GONE }

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var pipActionReceiver: android.content.BroadcastReceiver? = null
    private var episodeUrl: String = ""
    private var episodeTitle: String = ""
    private var episodeNumber: String = ""
    private var animeTitle: String = ""
    private var imageUrl: String = ""
    private var animeUrl: String = ""
    private var activeProviderId: String = ""
    private var servers: List<VideoServer> = emptyList()
    private var currentServerIndex: Int = 0
    private var isPlaying: Boolean = true
    private val resolvedUrlCache = mutableMapOf<Int, String>()

    private var ytTrackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null
    private var ytResolutionOptions: List<Int> = emptyList()
    private var ytCurrentResolution: Int = 0

    private lateinit var playerArea: FrameLayout
    private lateinit var ytBelowArea: View
    private lateinit var ytFeedScroll: NestedScrollView
    private lateinit var ytDetailTitle: TextView
    private lateinit var ytDetailMeta: TextView
    private lateinit var ytRelatedList: RecyclerView
    private lateinit var ytRelatedAdapter: YouTubeFeedAdapter
    private lateinit var ytFullscreenPanel: View
    private lateinit var ytFullscreenList: RecyclerView
    private lateinit var ytFullscreenAdapter: YouTubeFeedAdapter
    private lateinit var btnYtFsClose: ImageView
    private lateinit var ytFsFeedHeader: TextView
    // The related list below the player and the GoTube-style fullscreen queue share one backing
    // list; appending to one refreshes the other via notifyDataSetChanged() (syncYtFullscreenFeed).
    private val ytRelatedItems = mutableListOf<YouTubeVideo>()
    private lateinit var ytActionRow: View
    private lateinit var ytLikeCount: TextView
    private lateinit var btnYtLike: android.widget.ImageButton
    private lateinit var btnYtDislike: android.widget.ImageButton
    private lateinit var btnYtSubscribe: TextView
    private lateinit var ytCommentHeader: LinearLayout
    private lateinit var btnYtCommentToggle: android.widget.ImageButton
    private lateinit var ytCommentList: RecyclerView
    private lateinit var ytCommentAdapter: com.weebflix.app.ui.youtube.adapter.YouTubeCommentAdapter

    private lateinit var ytDetailPanel: View
    private lateinit var ytHomeSwipe: SwipeRefreshLayout
    private lateinit var ytHomeList: RecyclerView
    private lateinit var ytHomeAdapter: YouTubeFeedAdapter
    private lateinit var ytHomeSearchInput: EditText
    private lateinit var btnYtHomeSearchClear: ImageView
    private lateinit var ytMiniPlayer: View
    private lateinit var btnMiniClose: android.widget.ImageView
    private lateinit var miniPlayerView: androidx.media3.ui.PlayerView
    private lateinit var miniTitle: TextView
    private var ytMiniCollapsed = false
    private var ytHomeLoading = false
    private var ytHomeEnded = false
    private var ytHomeJob: kotlinx.coroutines.Job? = null
    private var ytHomeSearching = false
    private var ytHomeSearchQuery = ""
    private var ytHomeSearchJob: kotlinx.coroutines.Job? = null

    private val ytScraper by lazy {
        com.weebflix.app.data.provider.ProviderFactory.getProvider(com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) as YouTubeScraper
    }
    private var currentYtVideoId: String = ""
    private var ytLoadingRelated = false
    private var ytRelatedEnded = false
    private var ytRelatedContinuation: String = ""
    private var ytUpNext: YouTubeVideo? = null
    private val ytPlayHistory = ArrayDeque<YouTubeVideo>()
    private var ytCommentContinuation: String = ""
    private var ytLoadingComments = false
    private var ytCommentsEnded = false
    private var ytFirstBundleLoaded = false
    private var ytCommentsExpanded = false
    private var currentChannelId: String = ""
    private var currentChannelName: String = ""
    private var isYtLiked = false
    private var isYtDisliked = false
    private var isYtSubscribed = false
    private var startPositionMs: Long = 0L
    private var pendingYtSeekMs: Long = 0L
    private var ytFullscreen = false

    private var webView: WebView? = null
    private var webViewResolving = false
    private var webViewResolveCallback: ((String) -> Unit)? = null
    private var webViewResolveMode = ResolveMode.NONE
    private var resolveGeneration: Long = 0
    private var isWebViewPlayback = false
    private var webViewPlaybackUrl = ""
    private var webViewFullscreenView: View? = null
    private var webViewFullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    private var pendingResolveServerIndex: Int = -1
    private var pendingAutoFailRunnable: Runnable? = null
    private var syncByteRetryCount = 0
    private val maxSyncByteRetries = 2

    private enum class ResolveMode { NONE, SERVER_CLICK, EMBED_FETCH, DRAKOR_KITA }

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val maxVolume by lazy { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    private var currentVolume: Int = 0
    private var volumeFloat = 0f
    private var currentBrightness: Float = 0.5f

    private var controlsVisible: Boolean = true
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideControls() }

    private var wvControlsVisible: Boolean = true
    private val wvAutoHideHandler = Handler(Looper.getMainLooper())
    private val wvAutoHideRunnable = Runnable { wvHideControls() }

    private var isSeekingGesture: Boolean = false
    private var seekDelta: Long = 0L
    private var suppressSingleAfterPinch = false

    private var skipOpeningStart: Int = 90
    private var skipOpeningEnd: Int = 120
    private var skipOutroStart: Int = 1270
    private var skipOutroEnd: Int = 1400
    private var activeSkipOpeningEndMs: Long = 0L

    private var nextEpisodeUrl: String = ""
    private var nextEpisodeTitle: String = ""
    private var chainedNextEpisodeUrl: String = ""
    private var chainedNextEpisodeTitle: String = ""
    private var autoPlayCountdown: Int = 0
    private var autoPlayActive: Boolean = false
    private var turboRetryCount: Int = 0
    private var drakorDlFallbackTried: Boolean = false
    private var dlTrackingActive: Boolean = false
    private var dlProgressTotal: Long = 0L
    private var dlProgressLoaded: Long = 0L
    private var lastShownPct: Int = -1
    private var dlTotalFetched: Boolean = false
    private var lastProgressUiTime: Long = 0L
    private val progressTransferListener = object : androidx.media3.datasource.TransferListener {
        override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
            if (isNetwork && dlTrackingActive && !dlTotalFetched) {
                fetchDlTotalAsync()
            }
        }
        override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork && dlTrackingActive) {
                dlProgressLoaded += bytesTransferred
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgressUiTime >= 250L) {
                    lastProgressUiTime = now
                    runOnUiThread { updateLoadingProgress() }
                }
            }
        }
        override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
            if (isNetwork && dlTrackingActive) {
                runOnUiThread { updateLoadingProgress() }
            }
        }
    }
    private var wvUserSeeking: Boolean = false
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
    private var lastHistorySaveMs = 0L
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    checkSkipButtonsVisibility()
                    checkAutoPlay()
                    updateSeekBarFromPlayer()
                    // Throttled periodic save so watch history survives app kills and
                    // long mini-player sessions (only saved on destroy otherwise).
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastHistorySaveMs > 5000) {
                        lastHistorySaveMs = now
                        saveWatchHistory()
                    }
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
                if (isWebViewPlayback) {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        exitWebViewPlayback()
                    }
                } else {
                    finish()
                }
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
        imageUrl = intent.getStringExtra("imageUrl") ?: ""
        animeUrl = intent.getStringExtra("animeUrl") ?: ""
        activeProviderId = intent.getStringExtra("providerId") ?: com.weebflix.app.data.provider.ProviderFactory.getActiveProvider().id
        if (isTvMode) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        skipOpeningStart = intent.getIntExtra("skipOpeningStart", 90)
        skipOpeningEnd = intent.getIntExtra("skipOpeningEnd", 120)
        nextEpisodeUrl = intent.getStringExtra("nextEpisodeUrl") ?: ""
        nextEpisodeTitle = intent.getStringExtra("nextEpisodeTitle") ?: ""
        startPositionMs = intent.getLongExtra("startPositionMs", 0L)

        initViews()
        setupYtRelatedList()
        setupYtFullscreenFeed()
        setupYtComments()
        setupYtFeedScroll()
        setupYtHomeList()
        applyYtArea()
        setupGestureDetector()
        setupControls()
        setupSeekBar()
        registerPipActionReceiver()
        // WebView is initialized lazily on first use

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBar.coerceAtLeast(8), v.paddingRight, v.paddingBottom)
            val navBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            // The bottom bar overlays the video. In YouTube portrait it sits inside the small
            // 16:9 strip (not at the screen bottom), so adding nav-bar padding there would push
            // it up into the center play button — skip it. Fullscreen players keep the padding.
            if (activeProviderId != com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID || ytFullscreen) {
                bottomBar.setPadding(bottomBar.paddingLeft, bottomBar.paddingTop, bottomBar.paddingRight, navBarHeight.coerceAtLeast(12))
            }
            wvTopBar.setPadding(wvTopBar.paddingLeft, statusBar.coerceAtLeast(8), wvTopBar.paddingRight, wvTopBar.paddingBottom)
            wvBottomBar.setPadding(wvBottomBar.paddingLeft, wvBottomBar.paddingTop, wvBottomBar.paddingRight, navBarHeight.coerceAtLeast(12))
            insets
        }

        tvAnimeTitle.text = animeTitle
        tvEpisodeTitle.text = if (episodeTitle.isNotEmpty()) episodeTitle else "Episode $episodeNumber"

        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeFloat = currentVolume.toFloat()
        currentBrightness = getScreenBrightness()
        brightnessProgress.progress = (currentBrightness * 100).toInt()
        brightnessText.text = "${(currentBrightness * 100).toInt()}%"
        val volPercent = (currentVolume * 100f / maxVolume).toInt()
        volumeProgress.progress = volPercent
        volumeText.text = "$volPercent%"

        showControls()
        scheduleAutoHide()

        if (episodeUrl.isNotEmpty()) {
            loadServers()
            fetchEpisodeNavigation()
        }
    }

    /** TV D-pad / media-key handling. Only active in TV mode (phones use the gesture overlay). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isTvMode && event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_SPACE -> {
                    togglePlayPause()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    seekBy(10f)
                    showSeekIndicator(true, "+10s")
                    scheduleAutoHide()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    seekBy(-10f)
                    showSeekIndicator(false, "-10s")
                    scheduleAutoHide()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun initViews() {
        playerContainer = findViewById(R.id.playerContainer)
        playerView = findViewById(R.id.playerView)
        gestureOverlay = findViewById(R.id.gestureOverlay)
        loadingPlayer = findViewById(R.id.loadingPlayer)
        tvLoadingProgress = findViewById(R.id.tvLoadingProgress)
        tvLoadingHint = findViewById(R.id.tvLoadingHint)
        tvError = findViewById(R.id.tvError)
        tvAnimeTitle = findViewById(R.id.tvAnimeTitle)
        tvEpisodeTitle = findViewById(R.id.tvEpisodeTitle)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        centerControls = findViewById(R.id.centerControls)
        btnCenterPlayPause = findViewById(R.id.btnCenterPlayPause)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRewind = findViewById(R.id.btnPrevEp)
        btnForward = findViewById(R.id.btnNextEp)
        btnPrevEpisodeNav = findViewById(R.id.btnPrevEpisode)
        btnNextEpisodeNav = findViewById(R.id.btnNextEpisode)
        btnYtPrev = findViewById(R.id.btnYtPrev)
        btnYtNext = findViewById(R.id.btnYtNext)
        btnBack = findViewById(R.id.btnBack)
        btnPip = findViewById(R.id.btnPip)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        btnQuality = findViewById(R.id.btnQuality)
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
        webViewPlayerControls = findViewById(R.id.webViewPlayerControls)
        btnWebViewBack = findViewById(R.id.btnWebViewBack)
        wvTopBar = findViewById(R.id.wvTopBar)
        tvWvAnimeTitle = findViewById(R.id.tvWvAnimeTitle)
        tvWvEpisodeTitle = findViewById(R.id.tvWvEpisodeTitle)
        tvWvServerBadge = findViewById(R.id.tvWvServerBadge)
        wvCenterControls = findViewById(R.id.wvCenterControls)
        btnWvCenterPlayPause = findViewById(R.id.btnWvCenterPlayPause)
        wvBottomBar = findViewById(R.id.wvBottomBar)
        tvWvCurrentTime = findViewById(R.id.tvWvCurrentTime)
        tvWvTotalTime = findViewById(R.id.tvWvTotalTime)
        wvSeekBar = findViewById(R.id.wvSeekBar)
        btnWvPlayPause = findViewById(R.id.btnWvPlayPause)
        btnWvRewind = findViewById(R.id.btnWvRewind)
        btnWvForward = findViewById(R.id.btnWvForward)
        btnWvFullscreen = findViewById(R.id.btnWvFullscreen)
        btnWvPip = findViewById(R.id.btnWvPip)
        wvLoadingSpinner = findViewById(R.id.wvLoadingSpinner)
        brightnessIndicator = findViewById(R.id.brightnessIndicator)
        brightnessProgress = findViewById(R.id.brightnessProgress)
        brightnessText = findViewById(R.id.brightnessText)
        volumeIndicator = findViewById(R.id.volumeIndicator)
        volumeProgress = findViewById(R.id.volumeProgress)
        volumeText = findViewById(R.id.volumeText)
        seekIndicator = findViewById(R.id.seekIndicator)
        seekIcon = findViewById(R.id.seekIcon)
        seekText = findViewById(R.id.seekText)
        seekTimeText = findViewById(R.id.seekTimeText)
        zoomIndicator = findViewById(R.id.zoomIndicator)
        zoomText = findViewById(R.id.zoomText)

        playerArea = findViewById(R.id.playerArea)
        ytBelowArea = findViewById(R.id.ytBelowArea)
        ytFeedScroll = findViewById(R.id.ytFeedScroll)
        ytDetailTitle = findViewById(R.id.ytDetailTitle)
        ytDetailMeta = findViewById(R.id.ytDetailMeta)
        ytRelatedList = findViewById(R.id.ytRelatedList)
        ytFullscreenPanel = findViewById(R.id.ytFullscreenPanel)
        ytFullscreenList = findViewById(R.id.ytFullscreenList)
        btnYtFsClose = findViewById(R.id.btnYtFsClose)
        ytFsFeedHeader = findViewById(R.id.ytFsFeedHeader)
        ytActionRow = findViewById(R.id.ytActionRow)
        ytLikeCount = findViewById(R.id.ytLikeCount)
        btnYtLike = findViewById(R.id.btnYtLike)
        btnYtDislike = findViewById(R.id.btnYtDislike)
        btnYtSubscribe = findViewById(R.id.btnYtSubscribe)
        ytCommentHeader = findViewById(R.id.ytCommentHeader)
        btnYtCommentToggle = findViewById(R.id.btnYtCommentToggle)
        ytCommentList = findViewById(R.id.ytCommentList)
        ytDetailPanel = findViewById(R.id.ytDetailPanel)
        ytHomeSwipe = findViewById(R.id.ytHomeSwipe)
        ytHomeList = findViewById(R.id.ytHomeList)
        ytHomeSearchInput = findViewById(R.id.ytHomeSearchInput)
        btnYtHomeSearchClear = findViewById(R.id.btnYtHomeSearchClear)
        ytMiniPlayer = findViewById(R.id.ytMiniPlayer)
        btnMiniClose = findViewById(R.id.btnMiniClose)
        miniPlayerView = findViewById(R.id.miniPlayerView)
        miniTitle = findViewById(R.id.miniTitle)

        miniPlayerView.useController = false
        miniPlayerView.keepScreenOn = true
        ytMiniPlayer.setOnClickListener { expandYtPlayer() }
        btnMiniClose.setOnClickListener { finish() }

        btnYtLike.setOnClickListener { onYtLikePressed() }
        btnYtDislike.setOnClickListener { onYtDislikePressed() }
        btnYtSubscribe.setOnClickListener { onYtSubscribePressed() }
        ytDetailMeta.setOnClickListener { openChannel(currentChannelId, currentChannelName, "") }

        playerView.useController = isTvMode
        playerView.keepScreenOn = true
    }

    private fun setupYtRelatedList() {
        ytRelatedAdapter = YouTubeFeedAdapter(
            { video -> playYouTubeByVideo(video) },
            { video -> openChannelFromVideo(video) },
            ytRelatedItems // share the backing list with the fullscreen queue
        )
        ytRelatedList.layoutManager = LinearLayoutManager(this)
        ytRelatedList.adapter = ytRelatedAdapter
    }

    /** GoTube-style fullscreen feed: a right-side vertical queue of the SAME related videos a
     *  user expects ("Berikutnya"). It only exists on-screen while a YouTube video is playing in
     *  fullscreen and the player controls are visible (like GoTube's mini panel). Tapping a row
     *  switches video immediately. */
    private fun setupYtFullscreenFeed() {
        ytFullscreenAdapter = YouTubeFeedAdapter(
            { video -> playYouTubeByVideo(video) },
            { video -> openChannelFromVideo(video) },
            ytRelatedItems // same backing list → stays in sync with the related feed
        )
        ytFullscreenList.layoutManager = LinearLayoutManager(this)
        ytFullscreenList.adapter = ytFullscreenAdapter
        ytFullscreenList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= ytRelatedItems.size - 4) {
                    loadMoreRelated()
                }
            }
        })
        btnYtFsClose.setOnClickListener { hideControls() }
    }

    private fun setupYtComments() {
        ytCommentAdapter = com.weebflix.app.ui.youtube.adapter.YouTubeCommentAdapter()
        ytCommentList.layoutManager = LinearLayoutManager(this)
        ytCommentList.adapter = ytCommentAdapter
        val toggle = View.OnClickListener { toggleYtComments() }
        ytCommentHeader.setOnClickListener(toggle)
        btnYtCommentToggle.setOnClickListener(toggle)
    }

    /** The whole below-video panel is one scroll feed now (title/meta/actions/comments/related
     *  scroll away together like real YouTube). The lists inside it are fully expanded
     *  (nestedScrollingEnabled=false), so infinite scroll fires from the feed's scroll position. */
    private fun setupYtFeedScroll() {
        ytFeedScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val child = ytFeedScroll.getChildAt(0) ?: return@setOnScrollChangeListener
            if (scrollY + ytFeedScroll.height >= child.height - 600) {
                loadMoreRelated()
                if (ytCommentsExpanded) loadMoreComments()
            }
        }
    }

    /** With the lists expanded inside the feed, a page that is shorter than the screen never
     *  reaches the scroll bottom — keep fetching until the feed actually fills the viewport. */
    private fun maybeAutoFillYtFeed() {
        ytFeedScroll.post {
            if (isFinishing || ytRelatedEnded) return@post
            val child = ytFeedScroll.getChildAt(0) ?: return@post
            if (child.height < ytFeedScroll.height) loadMoreRelated()
        }
    }

    // ===== Mini player (YouTube) =====
    // Pulling down on the video collapses it into a pinned overlay at the bottom-left while the
    // home feed fills the rest of the screen. The same exoPlayer keeps rendering in both views.

    private fun setupYtHomeList() {
        ytHomeAdapter = YouTubeFeedAdapter(
            { video -> playYouTubeFromMini(video) },
            { video -> openChannelFromVideo(video) }
        )
        ytHomeList.layoutManager = LinearLayoutManager(this)
        ytHomeList.adapter = ytHomeAdapter
        ytHomeList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 4) {
                    loadMoreYtHome()
                }
            }
        })
        ytHomeSwipe.setColorSchemeResources(R.color.netflix_red)
        ytHomeSwipe.setOnRefreshListener { refreshYtHome() }

        btnYtHomeSearchClear.setOnClickListener { ytHomeSearchInput.setText("") }
        ytHomeSearchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.action == android.view.KeyEvent.ACTION_DOWN &&
                    event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            ) {
                runYtHomeSearch(ytHomeSearchInput.text.toString().trim(), debounce = false)
                true
            } else {
                false
            }
        }
        ytHomeSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onYtHomeSearchChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /** Search toggle on the mini-player feed: typing shows search results in the same list,
     *  clearing the query restores the endless feed. The current video keeps playing in the
     *  mini player the whole time. */
    private fun onYtHomeSearchChanged() {
        val q = ytHomeSearchInput.text.toString().trim()
        btnYtHomeSearchClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
        if (q.isEmpty()) {
            if (ytHomeSearching) exitYtHomeSearch()
        } else {
            runYtHomeSearch(q)
        }
    }

    private fun runYtHomeSearch(query: String, debounce: Boolean = true) {
        if (query.isEmpty()) return
        ytHomeSearchJob?.cancel()
        ytHomeJob?.cancel()
        ytHomeSearching = true
        ytHomeSearchQuery = query
        ytHomeAdapter.clear()
        ytHomeAdapter.setLoading()
        ytHomeSearchJob = lifecycleScope.launch {
            if (debounce) delay(500)
            val results = try {
                withContext(Dispatchers.IO) { ytScraper.searchVideos(query) }
            } catch (e: Exception) {
                emptyList()
            }
            if (ytHomeSearching && ytHomeSearchQuery == query && !isFinishing) {
                ytHomeAdapter.append(results, endOfFeed = results.isEmpty())
            }
        }
    }

    private fun exitYtHomeSearch() {
        ytHomeSearchJob?.cancel()
        ytHomeSearching = false
        ytHomeSearchQuery = ""
        ytHomeAdapter.clear()
        ytHomeLoading = false
        ytHomeEnded = false
        ytScraper.resetFeed()
        loadMoreYtHome()
    }

    /** Pull-to-refresh on the mini-player feed: in search mode re-runs the current query,
     *  otherwise drops the seen-id dedup set so a fresh random batch of Indonesian uploads
     *  loads; clears the spinner once the new page lands. */
    private fun refreshYtHome() {
        if (ytHomeSearching) {
            runYtHomeSearch(ytHomeSearchQuery, debounce = false)
        } else {
            ytHomeJob?.cancel()
            ytHomeAdapter.clear()
            ytHomeLoading = false
            ytHomeEnded = false
            ytScraper.resetFeed()
            loadMoreYtHome()
        }
        lifecycleScope.launch {
            ytHomeJob?.join()
            ytHomeSearchJob?.join()
            if (!isFinishing) ytHomeSwipe.isRefreshing = false
        }
    }

    private fun loadMoreYtHome() {
        if (ytHomeSearching) return
        if (ytHomeLoading || ytHomeEnded || ytHomeJob?.isActive == true) return
        ytHomeLoading = true
        val job = lifecycleScope.launch {
            val page = try {
                withContext(Dispatchers.IO) { ytScraper.nextFeedPage() }
            } catch (e: Exception) {
                emptyList()
            }
            when {
                page.isNotEmpty() -> ytHomeAdapter.append(page, endOfFeed = false)
                ytHomeAdapter.isEmpty -> {
                    ytHomeEnded = true
                    ytHomeAdapter.setLoading()
                }
                else -> {
                    ytHomeEnded = true
                    ytHomeAdapter.setLoading()
                    ytHomeAdapter.append(emptyList(), endOfFeed = true)
                }
            }
            ytHomeLoading = false
        }
        ytHomeJob = job
        ytHomeList.post {
            if (job.isActive && !isFinishing) ytHomeAdapter.setLoading()
        }
    }

    private fun canMiniPlayer(): Boolean {
        if (activeProviderId != com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) return false
        if (ytFullscreen || ytMiniCollapsed) return false
        if (isWebViewPlayback) return false
        return exoPlayer != null
    }

    private fun collapseYtPlayer() {
        if (!canMiniPlayer()) return
        ytMiniCollapsed = true
        miniTitle.text = tvAnimeTitle.text
        playerView.player = null
        playerArea.visibility = View.GONE
        ytDetailPanel.visibility = View.GONE
        // Hide the (now empty) detail feed scroll too — otherwise it keeps its layout_weight
        // and splits the screen with the home feed, pushing it to the bottom half.
        ytFeedScroll.visibility = View.GONE
        ytHomeSwipe.visibility = View.VISIBLE
        ytBelowArea.visibility = View.VISIBLE
        ytMiniPlayer.visibility = View.VISIBLE
        miniPlayerView.player = exoPlayer
        hideControls()
        if (ytHomeAdapter.isEmpty) {
            ytHomeEnded = false
            loadMoreYtHome()
        }
    }

    private fun expandYtPlayer() {
        if (!ytMiniCollapsed) return
        ytMiniCollapsed = false
        ytMiniPlayer.visibility = View.GONE
        miniPlayerView.player = null
        playerView.player = exoPlayer
        playerArea.visibility = View.VISIBLE
        ytDetailPanel.visibility = View.VISIBLE
        ytFeedScroll.visibility = View.VISIBLE
        ytHomeSwipe.visibility = View.GONE
        applyYtArea()
        showControls()
        scheduleAutoHide()
    }

    private fun playYouTubeFromMini(video: YouTubeVideo) {
        if (video.videoId.isEmpty() || video.videoId == currentYtVideoId) return
        playYouTubeByVideo(video)
    }

    /** Expands/collapses the comments list so the recommendations below regain the full height. */
    private fun toggleYtComments() {
        ytCommentsExpanded = !ytCommentsExpanded
        ytCommentList.visibility = if (ytCommentsExpanded) View.VISIBLE else View.GONE
        btnYtCommentToggle.setImageResource(
            if (ytCommentsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )
    }

    /** Syncs the comments list visibility + chevron to the current [ytCommentsExpanded] state. */
    private fun updateYtCommentsUi() {
        if (ytCommentsExpanded) {
            ytCommentList.visibility = View.VISIBLE
            btnYtCommentToggle.setImageResource(R.drawable.ic_expand_less)
        } else {
            ytCommentList.visibility = View.GONE
            btnYtCommentToggle.setImageResource(R.drawable.ic_expand_more)
        }
    }

    private fun resetYtComments() {
        ytCommentContinuation = ""
        ytLoadingComments = false
        ytCommentsEnded = false
        ytFirstBundleLoaded = false
        ytCommentsExpanded = false
        ytCommentAdapter.submitList(emptyList())
        ytCommentHeader.visibility = View.GONE
        ytCommentList.visibility = View.GONE
    }

    private fun loadMoreComments() {
        if (ytLoadingComments || ytCommentsEnded) return
        // First page + content come from the watchNextBundle (single `next` + one ANDROID_VR
        // comments continuation) in loadMoreRelated. Firing a second `next` here at the same
        // time would burst the innertube request rate and flag the IP (HTTP 400) -> defer to
        // the bundle, and only fetch continuation pages here (scroll listener handles those).
        if (!ytFirstBundleLoaded || ytCommentContinuation.isEmpty()) return
        ytLoadingComments = true
        lifecycleScope.launch {
            val c = ytCommentContinuation
            val page = try {
                withContext(Dispatchers.IO) {
                    ytScraper.nextComments(c)
                }
            } catch (e: Exception) {
                com.weebflix.app.data.scraper.CommentPage()
            }
            val fresh = page.comments.filter { it.author.isNotEmpty() && it.text.isNotEmpty() }
            if (fresh.isNotEmpty()) {
                val all = ytCommentAdapter.currentList.toMutableList().apply { addAll(fresh) }
                ytCommentAdapter.submitList(all)
                ytCommentContinuation = page.continuation
                ytCommentHeader.visibility = View.VISIBLE
                updateYtCommentsUi()
            } else if (page.continuation.isEmpty()) {
                ytCommentsEnded = true
                if (ytCommentAdapter.currentList.isEmpty()) {
                    ytCommentHeader.visibility = View.GONE
                    ytCommentList.visibility = View.GONE
                }
            }
            ytLoadingComments = false
        }
    }

    private fun onYtLikePressed() {
        if (!com.weebflix.app.data.auth.YouTubeAuthManager.isLoggedIn()) {
            Toast.makeText(this, R.string.yt_login_required_like, Toast.LENGTH_SHORT).show()
            return
        }
        val target = !isYtLiked
        setLikeUi(target)
        setDislikeUi(false)
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    com.weebflix.app.data.scraper.YouTubeDataApi.rateVideo(
                        currentYtVideoId, if (target) "like" else "none"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "like error: ${e.message}")
                false
            }
            if (!ok) {
                setLikeUi(!target)
                setDislikeUi(false)
                Toast.makeText(this@PlayerActivity, R.string.yt_engagement_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onYtDislikePressed() {
        if (!com.weebflix.app.data.auth.YouTubeAuthManager.isLoggedIn()) {
            Toast.makeText(this, R.string.yt_login_required_like, Toast.LENGTH_SHORT).show()
            return
        }
        val target = !isYtDisliked
        setDislikeUi(target)
        setLikeUi(false)
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    com.weebflix.app.data.scraper.YouTubeDataApi.rateVideo(
                        currentYtVideoId, if (target) "dislike" else "none"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "dislike error: ${e.message}")
                false
            }
            if (!ok) {
                setDislikeUi(!target)
                setLikeUi(false)
                Toast.makeText(this@PlayerActivity, R.string.yt_engagement_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onYtSubscribePressed() {
        if (currentChannelId.isEmpty()) {
            Toast.makeText(this, "Channel tidak diketahui", Toast.LENGTH_SHORT).show()
            return
        }
        if (!com.weebflix.app.data.auth.YouTubeAuthManager.isLoggedIn()) {
            Toast.makeText(this, R.string.yt_login_required_subscribe, Toast.LENGTH_SHORT).show()
            return
        }
        val target = !isYtSubscribed
        setSubscribeUi(target)
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    com.weebflix.app.data.scraper.YouTubeDataApi.setSubscription(currentChannelId, target)
                }
            } catch (e: Exception) {
                Log.w(TAG, "subscribe error: ${e.message}")
                false
            }
            if (!ok) {
                setSubscribeUi(!target)
                Toast.makeText(this@PlayerActivity, R.string.yt_engagement_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLikeUi(liked: Boolean) {
        isYtLiked = liked
        if (liked) btnYtLike.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.netflix_red)) else btnYtLike.clearColorFilter()
    }

    private fun setDislikeUi(disliked: Boolean) {
        isYtDisliked = disliked
        if (disliked) btnYtDislike.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.netflix_red)) else btnYtDislike.clearColorFilter()
    }

    private fun setSubscribeUi(subscribed: Boolean) {
        isYtSubscribed = subscribed
        btnYtSubscribe.text = getString(if (subscribed) R.string.yt_subscribed else R.string.yt_subscribe)
        btnYtSubscribe.setBackgroundResource(
            if (subscribed) R.drawable.bg_yt_subscribed else R.drawable.bg_yt_subscribe
        )
    }

    /** After the first related bundle sets [currentChannelId], sync the subscribe/like/dislike
     *  buttons with the real server state (exact forChannelId lookup + videos/rate). */
    private fun syncYtEngagement() {
        if (!com.weebflix.app.data.auth.YouTubeAuthManager.isLoggedIn()) return
        lifecycleScope.launch {
            if (currentChannelId.isNotEmpty()) {
                val subscribed = try {
                    withContext(Dispatchers.IO) {
                        com.weebflix.app.data.scraper.YouTubeDataApi.isSubscribedExact(currentChannelId)
                    }
                } catch (e: Exception) {
                    false
                }
                if (currentChannelId.isNotEmpty()) setSubscribeUi(subscribed)
            }
            if (currentYtVideoId.isNotEmpty()) {
                val rating = try {
                    withContext(Dispatchers.IO) {
                        com.weebflix.app.data.scraper.YouTubeDataApi.getMyRating(currentYtVideoId)
                    }
                } catch (e: Exception) {
                    ""
                }
                if (currentYtVideoId.isNotEmpty()) {
                    setLikeUi(rating == "like")
                    setDislikeUi(rating == "dislike")
                }
            }
        }
    }

    /** Sizes the video area: fullscreen for normal providers, 16:9 at top for YouTube
     *  (recomputed on orientation change since the activity handles configChanges itself). */
    private fun applyYtArea() {
        if (!::playerArea.isInitialized) return
        val isYt = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID
        val lp = playerArea.layoutParams as LinearLayout.LayoutParams
        if (isYt && !ytFullscreen) {
            val w = resources.displayMetrics.widthPixels
            val h = resources.displayMetrics.heightPixels
            val videoH = minOf((w * 9f / 16f).toInt(), (h * 0.45f).toInt()).coerceAtLeast(180)
            lp.height = videoH
            lp.weight = 0f
            ytBelowArea.visibility = View.VISIBLE
        } else {
            lp.height = 0
            lp.weight = 1f
            ytBelowArea.visibility = View.GONE
        }
        playerArea.layoutParams = lp
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyYtArea()
    }

    // ===== Hidden WebView for resolving video URLs (bypasses Cloudflare) =====

    private var webViewInitialized = false

    private fun ensureWebView() {
        if (webViewInitialized) return
        webViewInitialized = true
        initWebView()
    }

    private fun initWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true
            settings.allowUniversalAccessFromFileURLs = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            settings.mediaPlaybackRequiresUserGesture = false
            settings.blockNetworkImage = false
            settings.loadsImagesAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false

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
                        ResolveMode.DRAKOR_KITA -> {
                            Log.d(TAG, "DrakorKita page loaded: $url")
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

                    val lower = reqUrl.lowercase()
                    if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                        return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                    }

                    val isBloggerVideoG = reqUrl.contains("blogger.com/video.g") || reqUrl.contains("video.g?token=")
                    val isVideoUrl = (reqUrl.contains("googlevideo.com") ||
                        reqUrl.contains("videoplayback") ||
                        reqUrl.contains(".m3u8") ||
                        reqUrl.contains(".mp4") ||
                        reqUrl.contains(".mpd") ||
                        reqUrl.contains("blogspot.com/v/") ||
                        reqUrl.contains("bp.blogspot.com") ||
                        reqUrl.contains("abysscdn.com") ||
                        reqUrl.contains("hydrax")) &&
                        !reqUrl.contains("google-analytics.com") &&
                        !reqUrl.contains("googletagmanager.com") &&
                        !reqUrl.contains("doubleclick.net") &&
                        !reqUrl.contains("/collect?")

                    if (isBloggerVideoG) {
                        Log.d(TAG, ">>> Intercepting video.g HTML to inject XHR interception: $reqUrl")
                        return interceptBloggerHtml(reqUrl)
                    }

                    if (isVideoUrl && webViewResolving) {
                        val isAbyssUrl = reqUrl.contains("abysscdn.com") || reqUrl.contains("hydrax")
                        if (webViewResolveMode == ResolveMode.DRAKOR_KITA && !isAbyssUrl) {
                            Log.e(TAG, "DrakorKita: ignoring non-Abyss URL in shouldIntercept: $reqUrl")
                        } else {
                            Log.e(TAG, "Intercepted video URL: $reqUrl")
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
                        }
                        return null
                    }

                    return null
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    val msg = consoleMessage?.message() ?: return false
                    val source = consoleMessage.sourceId() ?: ""
                    val line = consoleMessage.lineNumber()
                    if (msg.contains("[DrakorKita]") || msg.contains("DrakorKita")) {
                        Log.e(TAG, "JS: $msg (from $source:$line)")
                    } else {
                        Log.d(TAG, "JS: $msg")
                    }
                    return true
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 60 && webViewResolving && webViewResolveMode == ResolveMode.EMBED_FETCH) {
                        val currentUrl = view?.url ?: ""
                        val isBlogger = currentUrl.contains("blogger.com") || currentUrl.contains("bp.blogspot.com")
                        val isFiledon = currentUrl.contains("filedon.co")
                        val isFileLions = currentUrl.contains("minochinos.com") || currentUrl.contains("filelions")

                        if (isBlogger) {
                            view?.evaluateJavascript(extractBloggerVideoJs(), null)
                        } else if (isFiledon) {
                            view?.evaluateJavascript(extractFiledonVideoJs(), null)
                        } else if (isFileLions) {
                            view?.evaluateJavascript(extractFileLionsVideoJs(), null)
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

    private val AD_REMOVAL_JS = """
        (function() {
            try {
                var adPatterns = ['doubleclick', 'googlesyndication', 'adservice', 'googlead',
                    'popunder', 'popup', 'clicksure', 'exoclick',
                    'propellerads', 'trafficjunky', 'adsterra', 'popads',
                    'onclickads', 'adsrv', 'admedia', 'adserver',
                    'adpartner', 'adfox', 'shopee', 'yandex',
                    'metrika', 'analytics', 'egret', 'mvp789', 'zafn9604',
                    'install.js', 'cpm', 'revive', 'mgid', 'taboola',
                    'outbrain', 'infolinks', 'adtrue', 'adnow',
                    'adreactor', 'exosrv', 'juicyads', 'adbucks',
                    'clicksor', 'bidvertiser', 'popcash', 'adtiger',
                    'adgain', 'adf.ly', 'ouo.io', 'adfoc.us',
                    'linkbucks', 'admyad', 'adpushup', 'adthrive',
                    'media.net', 'adversal', 'adblade', 'adbutler',
                    'adventory', 'adglare', 'adk2', 'adnxs',
                    'criteo', 'pubmatic', 'rubicon', 'openx',
                    'casalemedia', 'sharethrough', 'sovrn',
                    'adsafeprotected', 'moatads', 'teads', 'adroll',
                    'bluekai', 'exelator', 'krxd', 'demdex',
                    'rlcdn', 'scorecardresearch', 'quantserve',
                    'comscore', 'parsely', 'nuffnang', 'clickky',
                    'adnium', 'nativeads', 'adspirit', 'adtng',
                    'bidfluence', '2mdn.net', 'adobedc.net',
                    'adacado', 'adform', 'adloox', 'adnami',
                    'adplus', 'adstir', 'adtelligent', 'adzerk',
                    'loopme', 'mediafuse', 'onetag', 'optimizely',
                    'pexo.co', 'plugrush', 'revcontent', 'skimresources',
                    'smartyads', 'spotxchange', 'stickyadstv',
                    'undertone', 'vidazoo', 'visx.net',
                    'adyoulike', 'emxdgt.com', 'lngtd.com',
                    'adhigh', 'adtima', 'admicro', 'dable',
                    'popin.cc', 'revenuehits', 'adkengage'];
                var adClassPatterns = ['ad-', 'ad_', 'ads-', 'ads_', 'adsbygoogle',
                    'adsblock', 'adblock', 'advert', 'ad-box',
                    'banner_ad', 'banner-ads', 'popup-', 'popup_',
                    'overlay-ads', 'overlay_ad', 'sticky-ad', 'stickyad',
                    'float-ads', 'floating-ads', 'floating_ad',
                    'sponsor', 'sponsored', 'promo-', 'promo_',
                    'dfp-', 'dfp_', 'gpt-', 'gpt_',
                    'google_ads', 'google-ads', 'ad-container',
                    'ad-wrapper', 'ad-holder', 'adslot',
                    'adplacement', 'adspot', 'adunit',
                    'adzone', 'banner-728', 'banner-300',
                    'skyscraper', 'leaderboard', 'mpu-', 'mpu_',
                    'widget-ads', 'widget_ad', 'side-ad',
                    'top-ad', 'bottom-ad', 'footer-ad',
                    'header-ad', 'mid-ad', 'post-ad',
                    'text-ad', 'textad', 'img-ad', 'imgad',
                    'video-ad', 'videoad', 'preroll', 'midroll',
                    'postroll', 'ad-overlay', 'adoverlay',
                    'layer-ad', 'layerad', 'pop-layer',
                    'dimmed-layer', 'modal-ad', 'modalad',
                    'btn-close-ad', 'btn_ad_close', 'close-ad',
                    'ad-close', 'adclose', 'ad-btn', 'adbtn',
                    'id-ad', 'ad-container-', 'ad-placement',
                    'advertisement', 'advertisement-',
                    'advertise', 'advertise-', 'ads-list',
                    'ads-item', 'ad-item', 'ad-list'];
                var adIdPatterns = ['ad-', 'ad_', 'ads-', 'ads_', 'adsbygoogle',
                    'adsblock', 'adblock', 'advert', 'ad-box',
                    'banner_ad', 'popup-', 'popup_',
                    'overlay_ad', 'sticky_ad', 'floating_ad',
                    'sponsor', 'sponsored', 'promo-', 'promo_',
                    'dfp-', 'gpt-', 'google_ads', 'google-ads',
                    'ad-container', 'ad-wrapper', 'adslot',
                    'adplacement', 'adspot', 'adunit', 'adzone',
                    'leaderboard', 'mpu', 'skyscraper',
                    'side-ad', 'top-ad', 'bottom-ad', 'footer-ad',
                    'header-ad', 'mid-ad', 'post-ad',
                    'text-ad', 'textad', 'video-ad', 'videoad',
                    'preroll', 'midroll', 'postroll',
                    'ad-overlay', 'adoverlay', 'layer-ad', 'layerad',
                    'pop-layer', 'dimmed-layer', 'modal-ad', 'modalad',
                    'close-ad', 'ad-close', 'adclose'];

                var playerSrcPatterns = ['turbovidhls', 'turbovid', 'emturbovid', 'turbosplayer',
                    'abysscdn', 'hydrax', 'drakor.bid', 'drakor.kita',
                    'bp.blogspot.com', 'blogger.com', 'googlevideo'];

                function removeEl(el) {
                    if (el && el.parentNode) el.parentNode.removeChild(el);
                }

                function isPlayerElement(el) {
                    if (!el) return false;
                    var src = (el.src || el.href || el.data || '').toLowerCase();
                    for (var p = 0; p < playerSrcPatterns.length; p++) {
                        if (src.indexOf(playerSrcPatterns[p]) !== -1) return true;
                    }
                    if (el.tagName === 'VIDEO') return true;
                    return false;
                }

                function removeAds(el) {
                    if (!el || el._adClean) return;
                    el._adClean = true;
                    if (el.nodeType !== 1) return;
                    var src = (el.src || el.href || el.data || '').toLowerCase();
                    var tag = el.tagName;
                    var cls = (el.className || '').toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var style = el.getAttribute ? (el.getAttribute('style') || '').toLowerCase() : '';

                    if (isPlayerElement(el)) return;

                    if (src.indexOf('javascript:') === 0 && tag === 'A') {
                        if (el.onclick || /^javascript:window\.open/.test(src)) {
                            removeEl(el); el.href = '#'; el.onclick = null; el.target = ''; return;
                        }
                    }

                    for (var p = 0; p < adPatterns.length; p++) {
                        if (src.indexOf(adPatterns[p]) !== -1) { removeEl(el); return; }
                    }

                    if (tag === 'IFRAME') {
                        var s = el.getAttribute('src') || '';
                        if (s === '' || s.indexOf('about:blank') === 0 || s.indexOf('javascript:') === 0) {
                            removeEl(el); return;
                        }
                        var w = el.width || el.getAttribute('width') || '';
                        var h = el.height || el.getAttribute('height') || '';
                        if ((w == '1' || w == '0' || w == '') && (h == '1' || h == '0' || h == '')) {
                            removeEl(el); return;
                        }
                    }

                    if (tag === 'A') {
                        if (el.target === '_blank' && !el.href) { removeEl(el); return; }
                        var href = (el.href || '').toLowerCase();
                        if (href && (href.indexOf('javascript:') === 0 || href === '#' || href === '')) {
                            if (!el.querySelector('img, video, iframe')) { removeEl(el); return; }
                        }
                    }

                    for (var p = 0; p < adClassPatterns.length; p++) {
                        if (cls.indexOf(adClassPatterns[p]) !== -1) { removeEl(el); return; }
                        if (id.indexOf(adIdPatterns[p]) !== -1) { removeEl(el); return; }
                    }

                    if (style.indexOf('display:none') !== -1 || style.indexOf('visibility:hidden') !== -1) {
                        return;
                    }

                    if (style.indexOf('position:fixed') !== -1 || style.indexOf('position: fixed') !== -1) {
                        var z = parseInt(el.style.zIndex) || 0;
                        if (z > 1000) {
                            if (!isPlayerElement(el)) { removeEl(el); return; }
                        }
                    }

                    var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                    if (rect && (rect.width < 5 || rect.height < 5)) {
                        if (tag !== 'VIDEO' && tag !== 'IFRAME') { removeEl(el); return; }
                    }
                }

                function cleanPage() {
                    var all = document.querySelectorAll('iframe, script, img, ins, embed, object, a, div, section, aside, span, table, tr, td');
                    for (var i = 0; i < all.length; i++) removeAds(all[i]);

                    var fixedEls = document.querySelectorAll('div, span, section, aside');
                    for (var i = 0; i < fixedEls.length; i++) {
                        var el = fixedEls[i];
                        if (el._adClean) continue;
                        var cs = window.getComputedStyle ? window.getComputedStyle(el) : null;
                        if (cs && cs.position === 'fixed') {
                            var z = parseInt(cs.zIndex) || 0;
                            if (z > 100 && !isPlayerElement(el)) {
                                var bg = cs.backgroundColor || '';
                                var op = parseFloat(cs.opacity) || 1;
                                if (bg.indexOf('rgb') !== -1 || op > 0.5) {
                                    if (!el.querySelector('video, iframe[src*=\"turbovid\"], iframe[src*=\"blogger\"]')) {
                                        removeEl(el);
                                    }
                                }
                            }
                        }
                    }
                }

                cleanPage();

                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) removeAds(node);
                        });
                        if (mutation.type === 'attributes' && mutation.attributeName === 'style') {
                            var t = mutation.target;
                            if (t && t.nodeType === 1) {
                                var cs = window.getComputedStyle ? window.getComputedStyle(t) : null;
                                if (cs && cs.position === 'fixed' && !isPlayerElement(t)) {
                                    var z = parseInt(cs.zIndex) || 0;
                                    if (z > 100) removeEl(t);
                                }
                            }
                        }
                    });
                });
                observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class', 'src'] });

                function makePlayerFullscreen() {
                    var iframe = document.querySelector('iframe[src*="turbovidhls"], iframe[src*="turbovid"], iframe[src*="emturbovid"], iframe[src*="turbosplayer"]');
                    if (iframe) {
                        iframe.style.width = '100vw';
                        iframe.style.height = '100vh';
                        iframe.style.border = 'none';
                        iframe.style.position = 'fixed';
                        iframe.style.top = '0';
                        iframe.style.left = '0';
                        iframe.style.zIndex = '99999';
                        iframe.style.background = '#000';
                        iframe.setAttribute('allowfullscreen', 'true');
                        iframe.setAttribute('webkitallowfullscreen', 'true');
                        iframe.setAttribute('mozallowfullscreen', 'true');
                        iframe.scrollIntoView();
                        return true;
                    }
                    var v = document.querySelector('video');
                    if (v) {
                        v.style.width = '100vw';
                        v.style.height = '100vh';
                        v.style.objectFit = 'contain';
                        v.style.background = '#000';
                        v.style.position = 'fixed';
                        v.style.top = '0';
                        v.style.left = '0';
                        v.style.zIndex = '99999';
                        v.setAttribute('playsinline', '');
                        v.setAttribute('webkit-playsinline', '');
                        v.scrollIntoView();
                        return true;
                    }
                    return false;
                }

                makePlayerFullscreen();

                var playerObs = new MutationObserver(function() {
                    if (makePlayerFullscreen()) playerObs.disconnect();
                });
                playerObs.observe(document.body || document.documentElement, { childList: true, subtree: true, attributes: true });
                setTimeout(function() { playerObs.disconnect(); }, 15000);

                function killPopups() {
                    document.querySelectorAll('a[onclick*="window.open"], a[onclick*="popup"], a[target="_blank"][href*="http"]').forEach(function(a) {
                        if (!isPlayerElement(a) && !a.querySelector('img[src*="turbovid"], img[src*="blogger"], img[src*="googlevideo"]')) {
                            a.removeAttribute('onclick');
                            a.target = '_self';
                            a.href = '#';
                        }
                    });
                    var overlayDivs = document.querySelectorAll('div[style*="z-index"][style*="fixed"], div[style*="z-index"][style*="absolute"]');
                    overlayDivs.forEach(function(d) {
                        if (d._adClean) return;
                        if (!d.querySelector('iframe') && !d.querySelector('video')) {
                            var z = parseInt(d.style.zIndex) || 0;
                            if (z > 5000) removeEl(d);
                        }
                    });
                }
                killPopups();
                setInterval(killPopups, 2000);

                function autoPlay() {
                    var v = document.querySelector('video');
                    if (v) {
                        v.muted = true;
                        v.setAttribute('playsinline', '');
                        v.setAttribute('webkit-playsinline', '');
                        v.play().catch(function(){});
                        setTimeout(function() { v.muted = false; v.play().catch(function(){}); }, 800);
                    }
                    document.querySelectorAll('iframe').forEach(function(ifr) {
                        try { ifr.contentWindow.postMessage({type: 'play'}, '*'); } catch(e) {}
                    });
                }
                autoPlay();
                setTimeout(autoPlay, 1500);
                setTimeout(autoPlay, 3000);
            } catch(e) {}
        })();
    """.trimIndent()

    private val WEBVIEW_VIDEO_BRIDGE_JS = """
        (function() {
            var bridgeAttached = false;
            function attachBridge(video) {
                if (!video || video._wwb) return;
                video._wwb = true;
                bridgeAttached = true;
                function st() { try { AndroidBridge.onTimeUpdate(video.currentTime, video.duration||0, video.paused); } catch(e) {} }
                function ss() { try { AndroidBridge.onPlaybackStateChanged(video.paused, video.ended, video.duration||0); } catch(e) {} }
                video.addEventListener('timeupdate', st);
                video.addEventListener('play', ss);
                video.addEventListener('pause', ss);
                video.addEventListener('ended', ss);
                video.addEventListener('loadedmetadata', st);
                video.addEventListener('durationchange', st);
                window.seekTo = function(p) { if(video) video.currentTime = p; };
                window.togglePlayPause = function() { if(video) { if(video.paused) video.play(); else video.pause(); } };
                window.seekRelative = function(d) { if(video) video.currentTime = Math.max(0, Math.min(video.duration||0, video.currentTime + d)); };
                try { AndroidBridge.onPlayerReady(); } catch(e) {}
                st();
            }
            var v = document.querySelector('video');
            if (v) attachBridge(v);
            var mo = new MutationObserver(function() { var v2 = document.querySelector('video'); if (v2) attachBridge(v2); });
            mo.observe(document.documentElement, { childList: true, subtree: true });
            var tries = 0;
            var iv = setInterval(function() {
                tries++;
                var v3 = document.querySelector('video');
                if (v3) attachBridge(v3);
                document.querySelectorAll('iframe').forEach(function(f) {
                    try { var fv = (f.contentDocument||f.contentWindow.document).querySelector('video'); if (fv) attachBridge(fv); } catch(e) {}
                });
                if (bridgeAttached || tries > 15) clearInterval(iv);
            }, 1500);
        })();
    """.trimIndent()

    private val REF_INJECT_POPUP_BLOCKER = """
        (function() {
            try {
                var blockedAdPatterns = [
                    'doubleclick', 'popads', 'propeller', 'exoclick',
                    'adsterra', 'trafficjunky', 'clicksure', 'adserver',
                    'shopee', 'yandex', 'metrika', 'googlead',
                    'popunder', 'popup', 'adpartner', 'adfox'
                ];
                function isAdUrl(u) {
                    if (!u || u === 'about:blank') return false;
                    var l = u.toLowerCase();
                    for (var i = 0; i < blockedAdPatterns.length; i++) {
                        if (l.indexOf(blockedAdPatterns[i]) !== -1) return true;
                    }
                    return false;
                }
                var origOpen = window.open;
                window.open = function(url, name, features, replace) {
                    if (isAdUrl(url)) return null;
                    var w = origOpen.call(window, url, name, features, replace);
                    return w;
                };
                document.addEventListener('click', function(e) {
                    var t = e.target;
                    while (t) {
                        if (t.tagName === 'A' && t.href) {
                            if (isAdUrl(t.href)) {
                                e.preventDefault();
                                e.stopPropagation();
                                return false;
                            }
                        }
                        t = t.parentElement;
                    }
                }, true);
                window.focus = function() {};
            } catch(e) {}
        })();
    """.trimIndent()

    private val REF_INJECT_ADBLOCK_ONLY = """
        (function() {
            try {
                var adPatterns = [
                    'googleads', 'doubleclick', 'googlesyndication', 'adservice',
                    'adserver', 'advertisement', 'adfox', 'adpartner',
                    'popunder', 'popup', 'clicksure', 'exoclick',
                    'propellerads', 'trafficjunky', 'adsterra', 'popads',
                    'onclickads', 'adsrv', 'admedia', 'adrevolution',
                    'shopee', 'yandex', 'metrika', 'analytics'
                ];
                function str(v) { return v ? String(v) : ''; }
                function removeEl(el) { if (el && el.parentNode) el.parentNode.removeChild(el); }
                document.querySelectorAll('iframe, script, img, ins, embed, object').forEach(function(el) {
                    var src = str(el.src || el.href || el.data || el.className || el.id);
                    adPatterns.forEach(function(p) {
                        if (src.toLowerCase().indexOf(p) !== -1) removeEl(el);
                    });
                    if (el.tagName === 'IFRAME') {
                        var s = el.getAttribute('src') || '';
                        if (s === '' || s === 'about:blank' || s.indexOf('javascript:') === 0) {
                            removeEl(el);
                        }
                    }
                    if (el.tagName === 'A' || el.tagName === 'DIV' || el.tagName === 'SECTION') {
                        if (el.className && str(el.className).toLowerCase().indexOf('ad-') !== -1) removeEl(el);
                        if (el.id && str(el.id).toLowerCase().indexOf('ad-') !== -1) removeEl(el);
                    }
                });
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) {
                                var src = str(node.src || node.href || node.data || node.className || node.id);
                                adPatterns.forEach(function(pattern) {
                                    if (src.toLowerCase().indexOf(pattern) !== -1) removeEl(node);
                                });
                            }
                        });
                    });
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
            } catch(e) {}
        })();
    """.trimIndent()

    private val REF_INJECT_CLEAN_PAGE = """
        (function() {
            try {
                if (window._dkControlsVisible) return;
                var adPatterns = [
                    'googleads', 'doubleclick', 'googlesyndication', 'adservice',
                    'adserver', 'advertisement', 'adfox', 'adpartner',
                    'popunder', 'popup', 'clicksure', 'exoclick',
                    'propellerads', 'trafficjunky', 'adsterra', 'popads',
                    'onclickads', 'adsrv', 'admedia', 'adrevolution',
                    'shopee', 'yandex', 'metrika', 'analytics'
                ];
                function str(v) { return v ? String(v) : ''; }
                function removeEl(el) {
                    if (el && el.parentNode) el.parentNode.removeChild(el);
                }
                function fullscreen(el) {
                    if (!el) return;
                    el.style.width = '100vw';
                    el.style.height = '100vh';
                    el.style.border = 'none';
                    el.style.position = 'fixed';
                    el.style.top = '0';
                    el.style.left = '0';
                    el.style.zIndex = '99999';
                    el.style.background = '#000';
                    el.style.objectFit = 'contain';
                    el.setAttribute('playsinline', '');
                    el.scrollIntoView();
                }
                function makeFullscreen() {
                    var els = document.querySelectorAll('iframe[src*="turbovidhls"], iframe[src*="turbovid"], iframe[src*="drakor"], iframe[id*="player"], iframe[class*="player"], #player, .player, video');
                    for (var i = 0; i < els.length; i++) { fullscreen(els[i]); }
                }
                document.querySelectorAll('iframe, script, img, ins, embed, object').forEach(function(el) {
                    var src = str(el.src || el.href || el.data || el.className || el.id);
                    adPatterns.forEach(function(p) {
                        if (src.toLowerCase().indexOf(p) !== -1) removeEl(el);
                    });
                    if (el.tagName === 'IFRAME') {
                        var s = el.getAttribute('src') || '';
                        if (s === '' || s === 'about:blank' || s.indexOf('javascript:') === 0) {
                            removeEl(el);
                        }
                    }
                    if (el.tagName === 'A' || el.tagName === 'DIV' || el.tagName === 'SECTION') {
                        if (el.className && str(el.className).toLowerCase().indexOf('ad-') !== -1) removeEl(el);
                        if (el.id && str(el.id).toLowerCase().indexOf('ad-') !== -1) removeEl(el);
                    }
                });
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) {
                                var src = str(node.src || node.href || node.data || node.className || node.id);
                                adPatterns.forEach(function(pattern) {
                                    if (src.toLowerCase().indexOf(pattern) !== -1) {
                                        removeEl(node);
                                    }
                                });
                            }
                        });
                    });
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
                makeFullscreen();
                setTimeout(makeFullscreen, 1000);
                setTimeout(makeFullscreen, 3000);
                var st = document.createElement('style');
                st.id = 'nf-clean';
                st.textContent = 'html,body{background:#000!important;margin:0!important;padding:0!important;height:100%!important;overflow:hidden!important}header,nav,footer,.header,.footer,.nav{display:none!important}';
                document.head.appendChild(st);
            } catch(e) {}
        })();
    """.trimIndent()

    private val REF_INJECT_AUTOPLAY = """
        (function() {
            try {
                function attemptPlay() {
                    var v = document.querySelector('video');
                    if (v) {
                        v.muted = true;
                        v.setAttribute('playsinline', '');
                        v.play().catch(function(){});
                        setTimeout(function() {
                            v.muted = false;
                            v.play().catch(function(){});
                        }, 800);
                    }
                    var iframes = document.querySelectorAll('iframe');
                    iframes.forEach(function(iframe) {
                        try { iframe.contentWindow.postMessage({type: 'play'}, '*'); } catch(e) {}
                    });
                }
                attemptPlay();
                setTimeout(attemptPlay, 1500);
                setTimeout(attemptPlay, 3000);
            } catch(e) {}
        })();
    """.trimIndent()

    private fun playEpisodePageViaWebView(episodeUrl: String, server: VideoServer? = null, autoSelectJs: String? = null, skipInjections: Boolean = false, customCleanJs: String? = null) {
        ensureWebView()
        resetVideoZoom()
        isWebViewPlayback = true
        webViewPlaybackUrl = episodeUrl
        exoPlayer?.pause()

        loadingPlayer.visibility = View.VISIBLE
        webView?.postDelayed({
            if (loadingPlayer.visibility == View.VISIBLE || wvLoadingSpinner.visibility == View.VISIBLE) {
                loadingPlayer.visibility = View.GONE
                wvLoadingSpinner.visibility = View.GONE
                Log.d(TAG, "EP-WEBVIEW: loading timeout after 15s")
            }
        }, 15000)
        playerView.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        webViewPlayerControls.visibility = View.VISIBLE
        gestureOverlay.visibility = View.GONE
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        centerControls.visibility = View.GONE

        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            blockNetworkImage = false
            loadsImagesAutomatically = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView?.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                webViewFullscreenView = view
                webViewFullscreenCallback = callback
                val container = FrameLayout(this@PlayerActivity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
                playerContainer.addView(container)
                container.visibility = View.VISIBLE
                hideControls()
                topBar.visibility = View.GONE
                bottomBar.visibility = View.GONE
            }

            override fun onHideCustomView() {
                webViewFullscreenView?.let { view ->
                    (view.parent as? FrameLayout)?.removeView(view)
                }
                webViewFullscreenView = null
                webViewFullscreenCallback?.onCustomViewHidden()
                webViewFullscreenCallback = null
                hideControls()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 0) {
                    if (isWebViewPlayback) {
                        wvLoadingSpinner.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    } else {
                        loadingPlayer.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: return false
                if (msg.contains("Error") || msg.contains("error") || msg.contains("HLS") || msg.contains("hls") || msg.contains("video")) {
                    Log.d(TAG, "EP-WEBVIEW: $msg")
                }
                return true
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                resultMsg?.obj = null
                return false
            }
        }

        webView?.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingPlayer.visibility = View.GONE
                wvLoadingSpinner.visibility = View.GONE
                view?.visibility = View.VISIBLE
                Log.d(TAG, "EP-WEBVIEW page loaded: $url")
                WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                view?.evaluateJavascript(REF_INJECT_POPUP_BLOCKER, null)
                if (customCleanJs != null) {
                    view?.evaluateJavascript(customCleanJs, null)
                } else if (!skipInjections) {
                    view?.evaluateJavascript(REF_INJECT_CLEAN_PAGE, null)
                    view?.evaluateJavascript(REF_INJECT_AUTOPLAY, null)
                }
                if (autoSelectJs != null) {
                    view?.postDelayed({
                        view.evaluateJavascript(autoSelectJs, null)
                        Log.d(TAG, "EP-WEBVIEW injected auto-select JS")
                        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                        view.postDelayed({
                            if (!skipInjections) {
                                view.evaluateJavascript(REF_INJECT_CLEAN_PAGE, null)
                                view.evaluateJavascript(REF_INJECT_AUTOPLAY, null)
                            }
                            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                            Log.d(TAG, "EP-WEBVIEW re-injected ref JS after server select")
                        }, 3000)
                    }, 2000)
                }
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "EP-WEBVIEW error: $description at $failingUrl")
                loadingPlayer.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                tvError.text = "Error: $description"
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val lower = url.lowercase()
                if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                    Log.d(TAG, "EP-WEBVIEW blocked ad navigation: $lower")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val lower = reqUrl.lowercase()
                if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                    return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                }
                if (request?.isForMainFrame == true && request?.method?.equals("GET", ignoreCase = true) == true) {
                    val host = request.url?.host ?: ""
                    if (host.contains("abyssplayer.com") || host.contains("rubyvidhub.com")) {
                        Log.d(TAG, "EP-WEBVIEW intercepting player page for rewrite: $reqUrl")
                        return rewriteAnichinPlayerPage(reqUrl)
                    }
                }
                return null
            }
        }

        webView?.visibility = View.VISIBLE
        val extraHeaders = HashMap<String, String>()
        if (episodeUrl.contains("filedon.co")) {
            try {
                val provider = com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId)
                extraHeaders["Referer"] = provider.baseUrl
                Log.d(TAG, "EP-WEBVIEW: added Referer ${provider.baseUrl} for filedon embed (server-side whitelist check)")
            } catch (e: Exception) {
                Log.e(TAG, "EP-WEBVIEW: failed to set Referer for filedon embed", e)
            }
        }
        webView?.loadUrl(episodeUrl, extraHeaders)
        Log.d(TAG, "EP-WEBVIEW: loading episode page $episodeUrl")

        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID ||
            activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID) {
            wvHideControls()
            Log.d(TAG, "EP-WEBVIEW: WebView controls hidden for provider: $activeProviderId")
        } else {
            wvScheduleAutoHide()
            Log.d(TAG, "EP-WEBVIEW: WebView controls auto-hide scheduled for provider: $activeProviderId")
        }

        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun playViaWebView(url: String) {
        ensureWebView()
        isWebViewPlayback = true
        webViewPlaybackUrl = url

        loadingPlayer.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        webViewPlayerControls.visibility = View.VISIBLE
        gestureOverlay.visibility = View.GONE
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        centerControls.visibility = View.GONE

        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            blockNetworkImage = false
            loadsImagesAutomatically = true
        }

        webView?.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                webViewFullscreenView = view
                webViewFullscreenCallback = callback
                val container = FrameLayout(this@PlayerActivity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
                playerContainer.addView(container)
                container.visibility = View.VISIBLE
                hideControls()
                topBar.visibility = View.GONE
                bottomBar.visibility = View.GONE
            }

            override fun onHideCustomView() {
                webViewFullscreenView?.let { view ->
                    (view.parent as? FrameLayout)?.removeView(view)
                }
                webViewFullscreenView = null
                webViewFullscreenCallback?.onCustomViewHidden()
                webViewFullscreenCallback = null
                hideControls()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 0) {
                    if (isWebViewPlayback) {
                        wvLoadingSpinner.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    } else {
                        loadingPlayer.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    }
                }
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                resultMsg?.obj = null
                return false
            }
        }

        webView?.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isWebViewPlayback) {
                    wvLoadingSpinner.visibility = View.GONE
                } else {
                    loadingPlayer.visibility = View.GONE
                }
                view?.visibility = View.VISIBLE
                view?.evaluateJavascript(AD_REMOVAL_JS, null)
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView playback error: $description at $failingUrl")
                loadingPlayer.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                tvError.text = "Error: $description"
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val lower = url.lowercase()
                if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                    Log.d(TAG, "WV-PLAYBACK blocked ad navigation: $lower")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val lower = reqUrl.lowercase()
                if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                    return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                }
                return null
            }
        }

        webView?.visibility = View.VISIBLE
        webView?.loadUrl(url)
        Log.d(TAG, "WebView playback mode: loading $url")

        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID ||
            activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID) {
            wvHideControls()
            Log.d(TAG, "WV-PLAYBACK: WebView controls hidden for provider: $activeProviderId")
        }
    }

    private fun playVideoUrlViaWebView(videoUrl: String) {
        ensureWebView()
        isWebViewPlayback = true
        webViewPlaybackUrl = videoUrl

        playerView.visibility = View.GONE
        webViewPlayerControls.visibility = View.VISIBLE
        gestureOverlay.visibility = View.GONE
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        centerControls.visibility = View.GONE
        wvLoadingSpinner.visibility = View.VISIBLE
        wvCenterControls.visibility = View.GONE

        tvWvAnimeTitle.text = animeTitle
        tvWvEpisodeTitle.text = episodeTitle
        tvWvServerBadge.text = servers.getOrNull(currentServerIndex)?.name ?: ""

        setupWebViewControls()

        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            loadsImagesAutomatically = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }

        val proxyClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        webView?.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                webViewFullscreenView = view
                webViewFullscreenCallback = callback
                val container = FrameLayout(this@PlayerActivity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
                playerContainer.addView(container)
                container.visibility = View.VISIBLE
                hideControls()
            }

            override fun onHideCustomView() {
                webViewFullscreenView?.let { view ->
                    (view.parent as? FrameLayout)?.removeView(view)
                }
                webViewFullscreenView = null
                webViewFullscreenCallback?.onCustomViewHidden()
                webViewFullscreenCallback = null
                hideControls()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 0) {
                    wvLoadingSpinner.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: return false
                if (msg.contains("Error") || msg.contains("error") || msg.contains("HLS") || msg.contains("hls") || msg.contains("video")) {
                    Log.d(TAG, "VIDEO-WEBVIEW: $msg")
                }
                return true
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                resultMsg?.obj = null
                return false
            }
        }

        webView?.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                wvLoadingSpinner.visibility = View.GONE
                WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                view?.evaluateJavascript(AD_REMOVAL_JS, null)
                Log.d(TAG, "VIDEO-WEBVIEW loaded: $url")
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "VIDEO-WEBVIEW error: $description at $failingUrl")
                loadingPlayer.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                tvError.text = "Error: $description"
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val lower = url.lowercase()
                if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                    Log.d(TAG, "VIDEO-WEBVIEW blocked ad navigation: $lower")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val method = request?.method ?: "GET"

                val lower = reqUrl.lowercase()
                if (BLOCKED_DOMAINS.any { lower.contains(it) }) {
                    return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                }

                val isCdnRequest = reqUrl.contains("turboviplay") || reqUrl.contains("turbovid") ||
                    reqUrl.contains("turbosplayer") || reqUrl.contains("abysscdn") ||
                    reqUrl.contains("hydrax") || reqUrl.contains("googlevideo") ||
                    reqUrl.contains("googleusercontent") || reqUrl.contains("cdn2.") || reqUrl.contains("cdn3.")

                if (!isCdnRequest || method != "GET") return null

                    try {
                        val builder = okhttp3.Request.Builder().url(reqUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .addHeader("Accept", "*/*")
                            .addHeader("Accept-Language", "en-US,en;q=0.9")
                        if (reqUrl.contains("turboviplay") || reqUrl.contains("turbovid") || reqUrl.contains("turbosplayer")) {
                            builder.addHeader("Referer", "https://turbovidhls.com/")
                            builder.addHeader("Origin", "https://turbovidhls.com")
                        } else if (reqUrl.contains("abysscdn") || reqUrl.contains("hydrax") || reqUrl.contains("drakor.bid")) {
                            builder.addHeader("Referer", "https://drakor.kita.mobi/")
                            builder.addHeader("Origin", "https://drakor.kita.mobi")
                        }
                        val response = proxyClient.newCall(builder.build()).execute()
                        val body = response.body ?: return null
                        var contentType = response.header("Content-Type") ?: "application/octet-stream"
                        var mimeType = contentType.split(";").firstOrNull()?.trim() ?: "application/octet-stream"
                        if (reqUrl.contains(".ts") || reqUrl.contains("/data3/") || reqUrl.contains("googleusercontent")) {
                            if (mimeType.startsWith("image/") || mimeType == "application/octet-stream") {
                                mimeType = "video/mp2t"
                            }
                        }
                        if (reqUrl.contains(".m3u8")) {
                            mimeType = "application/vnd.apple.mpegurl"
                        }
                        val bytes = body.bytes()
                        Log.d(TAG, "VIDEO-PROXY: ${reqUrl.take(80)} → ${response.code} $mimeType (${bytes.size} bytes)")
                        return android.webkit.WebResourceResponse(
                            mimeType, "ISO-8859-1", response.code,
                        response.message.ifEmpty { "OK" },
                        mutableMapOf("Access-Control-Allow-Origin" to "*"),
                        bytes.inputStream()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "VIDEO-PROXY error: ${e.message}")
                    return null
                }
            }
        }

        webView?.visibility = View.VISIBLE
        webView?.loadUrl(videoUrl)
        Log.d(TAG, "VIDEO-WEBVIEW: loading video URL: $videoUrl")

        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID ||
            activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID) {
            wvHideControls()
            Log.d(TAG, "VIDEO-WEBVIEW: WebView controls hidden for provider: $activeProviderId")
        }
    }

    private fun playVideoViaHtml5WebView(videoUrl: String) {
        ensureWebView()
        resetVideoZoom()
        isWebViewPlayback = true
        webViewPlaybackUrl = videoUrl

        playerView.visibility = View.GONE
        webViewPlayerControls.visibility = View.VISIBLE
        gestureOverlay.visibility = View.GONE
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        centerControls.visibility = View.GONE

        tvWvAnimeTitle.text = animeTitle
        tvWvEpisodeTitle.text = episodeTitle
        tvWvServerBadge.text = servers.getOrNull(currentServerIndex)?.name ?: ""
        wvLoadingSpinner.visibility = View.VISIBLE
        wvCenterControls.visibility = View.GONE

        setupWebViewControls()

        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            loadsImagesAutomatically = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }

        val proxyClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        webView?.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                webViewFullscreenView = view
                webViewFullscreenCallback = callback
                val container = FrameLayout(this@PlayerActivity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
                playerContainer.addView(container)
                container.visibility = View.VISIBLE
                hideControls()
            }

            override fun onHideCustomView() {
                webViewFullscreenView?.let { view ->
                    (view.parent as? FrameLayout)?.removeView(view)
                }
                webViewFullscreenView = null
                webViewFullscreenCallback?.onCustomViewHidden()
                webViewFullscreenCallback = null
                hideControls()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 0) {
                    loadingPlayer.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: return false
                val source = consoleMessage.sourceId() ?: ""
                val line = consoleMessage.lineNumber()
                if (msg.contains("HLS-ERROR") || msg.contains("HLS-FRAG")) {
                    Log.e(TAG, "HTML5-Player: $msg")
                } else if (msg.contains("HLS") || msg.contains("hls") || msg.contains("video") || msg.contains("Error") || msg.contains("error") || msg.contains("proxy") || msg.contains("PROXY")) {
                    Log.d(TAG, "HTML5-Player: $msg")
                }
                return true
            }
        }

        webView?.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingPlayer.visibility = View.GONE
                WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                Log.d(TAG, "HTML5 page loaded: $url")
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "HTML5 WebView error: $description at $failingUrl")
            }

            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val method = request?.method ?: "GET"

                Log.d(TAG, "INTERCEPT [$method]: ${reqUrl.take(120)}")

                val isGoogleCdn = reqUrl.contains("googleusercontent") || reqUrl.contains("googlevideo")
                if (isGoogleCdn) return null

                val isCdnRequest = reqUrl.contains("turboviplay") || reqUrl.contains("turbovid") ||
                    reqUrl.contains("turbosplayer") || reqUrl.contains("abysscdn") ||
                    reqUrl.contains("hydrax") || reqUrl.contains("cdn2.")
                val isHlsJs = reqUrl.contains("hls.js") || reqUrl.contains("hls.min.js")

                if (isCdnRequest || isHlsJs) {
                    if (request?.method == "OPTIONS") {
                        return android.webkit.WebResourceResponse(
                            "text/plain", "utf-8", 200, "OK",
                            mapOf(
                                "Access-Control-Allow-Origin" to "*",
                                "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                                "Access-Control-Allow-Headers" to "*",
                                "Access-Control-Max-Age" to "86400"
                            ),
                            "".byteInputStream()
                        )
                    }
                    try {
                        val builder = okhttp3.Request.Builder().url(reqUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

                        if (isCdnRequest) {
                            builder.addHeader("Referer", "https://turbovidhls.com/")
                            builder.addHeader("Origin", "https://turbovidhls.com")
                            builder.addHeader("Accept", "*/*")
                            builder.addHeader("Accept-Language", "en-US,en;q=0.9")
                        }

                        var response = proxyClient.newCall(builder.build()).execute()
                        var retryCount = 0
                        while (response.code == 429 && retryCount < 4) {
                            response.close()
                            val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: (2 + retryCount * 2)
                            Log.w(TAG, "PROXY 429 for ${reqUrl.take(60)}, retry #${retryCount+1} after ${retryAfter}s")
                            Thread.sleep(retryAfter * 1000L)
                            response = proxyClient.newCall(builder.build()).execute()
                            retryCount++
                        }
                        val body = response.body ?: return null
                        var contentType = response.header("Content-Type") ?: "application/octet-stream"
                        var mimeType = contentType.split(";").firstOrNull()?.trim() ?: "application/octet-stream"

                        if (reqUrl.contains(".ts") || reqUrl.contains("/data3/") || reqUrl.contains("googleusercontent")) {
                            if (mimeType.startsWith("image/") || mimeType == "application/octet-stream") {
                                Log.w(TAG, "PROXY MIME override: $mimeType → video/mp2t for ${reqUrl.take(60)}")
                                mimeType = "video/mp2t"
                            }
                        }
                        if (reqUrl.contains(".m3u8")) {
                            mimeType = "application/vnd.apple.mpegurl"
                        }
                        val isBinary = mimeType.startsWith("video/") || mimeType.startsWith("application/octet-stream") ||
                            mimeType == "application/vnd.apple.mpegurl" || mimeType == "application/x-mpegurl" ||
                            mimeType == "audio/mpeg" || reqUrl.contains(".ts") || reqUrl.contains(".m3u8") ||
                            reqUrl.contains("data3")

                        Log.d(TAG, "PROXY: ${reqUrl.take(80)} → ${response.code} $mimeType (len=${body.contentLength()})${if (retryCount > 0) " (retries=$retryCount)" else ""}")

                        val respHeaders = mutableMapOf<String, String>(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                            "Access-Control-Allow-Headers" to "*",
                            "Access-Control-Expose-Headers" to "*"
                        )
                        for ((k, v) in response.headers) {
                            val lk = k.lowercase()
                            if (lk != "access-control-allow-origin" &&
                                lk != "content-encoding" &&
                                lk != "content-length" &&
                                lk != "transfer-encoding") {
                                respHeaders[k] = v
                            }
                        }

                        val bytes = body.bytes()
                        val hexPreview = if (bytes.size >= 8) bytes.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) } else bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                        Log.d(TAG, "PROXY buffered: ${reqUrl.take(60)} → ${bytes.size} bytes, hex=$hexPreview")

                        return android.webkit.WebResourceResponse(
                            mimeType,
                            "ISO-8859-1",
                            response.code,
                            response.message.ifEmpty { "OK" },
                            respHeaders,
                            bytes.inputStream()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "PROXY error for $reqUrl: ${e.message}")
                        return null
                    }
                }
                return null
            }
        }

        val isHls = videoUrl.contains(".m3u8")

        val hlsJs = try {
            resources.openRawResource(R.raw.hls_min).bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled hls.js: ${e.message}")
            ""
        }
        Log.d(TAG, "Bundled hls.js size: ${hlsJs.length} chars")

        val html = if (isHls && hlsJs.isNotEmpty()) """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background: #000; display: flex; flex-direction: column; justify-content: center; align-items: center; height: 100vh; overflow: hidden; }
                    video { width: 100%; height: 100%; object-fit: cover; }
                </style>
            </head>
            <body>
                <video id="video" playsinline></video>
                <script>
                    $hlsJs
                </script>
                <script>
                    var video = document.getElementById('video');
                    var videoUrl = '${videoUrl.replace("'", "\\'")}';

                    function sendTime() {
                        if (video.duration && isFinite(video.duration)) {
                            try { AndroidBridge.onTimeUpdate(video.currentTime, video.duration, video.paused); } catch(e) {}
                        }
                    }

                    function sendState() {
                        try { AndroidBridge.onPlaybackStateChanged(video.paused, video.ended, video.duration || 0); } catch(e) {}
                    }

                    video.addEventListener('timeupdate', sendTime);
                    video.addEventListener('play', sendState);
                    video.addEventListener('pause', sendState);
                    video.addEventListener('ended', sendState);
                    video.addEventListener('loadedmetadata', sendTime);

                    window.seekTo = function(pos) { video.currentTime = pos; };
                    window.togglePlayPause = function() {
                        if (video.paused) video.play(); else video.pause();
                    };
                    window.seekRelative = function(delta) { video.currentTime = Math.max(0, Math.min(video.duration || 0, video.currentTime + delta)); };

                    try { AndroidBridge.onPlayerReady(); } catch(e) {}

                    if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                        var hls = new Hls({
                            maxBufferLength: 30,
                            maxMaxBufferLength: 60,
                            startFragPrefetch: false,
                            enableWorker: false,
                            maxParallelFrags: 1,
                            fragLoadingRetry: 15000,
                            startLevel: 0,
                            lowLatencyMode: false
                        });
                        hls.loadSource(videoUrl);
                        hls.attachMedia(video);
                        hls.on(Hls.Events.MANIFEST_PARSED, function(e, data) {
                            try { AndroidBridge.onHlsManifest(data.levels.length); } catch(e) {}
                            video.play().catch(function() {});
                        });
                        hls.on(Hls.Events.ERROR, function(event, data) {
                            var errType = data.type === Hls.ErrorTypes.NETWORK_ERROR ? 'NETWORK' : 'MEDIA';
                            var errDetails = data.details || 'unknown';
                            var errFatal = data.fatal ? 'FATAL' : 'non-fatal';
                            console.log('HLS-ERROR [' + errType + '/' + errDetails + '/' + errFatal + ']: ' + (data.response ? data.response.code + ' ' + data.response.url : data.error ? data.error.message : ''));
                            if (data.fatal) {
                                if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                                    setTimeout(function() { hls.startLoad(); }, 2000);
                                } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                                    hls.recoverMediaError();
                                }
                            }
                        });
                        hls.on(Hls.Events.FRAG_LOADED, function(e, data) {
                            console.log('HLS-FRAG-LOADED: seq=' + (data.frag ? data.frag.sn : '?') + ' size=' + (data.frag ? data.frag.stats.total : '?'));
                        });
                    } else {
                        try { AndroidBridge.onHlsError('Hls not supported'); } catch(e) {}
                    }
                </script>
            </body>
            </html>
        """.trimIndent() else """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background: #000; display: flex; justify-content: center; align-items: center; height: 100vh; overflow: hidden; }
                    video { width: 100%; height: 100%; object-fit: cover; }
                </style>
            </head>
            <body>
                <video id="video" autoplay playsinline src="${videoUrl.replace("'", "\\'")}"></video>
                <script>
                    var video = document.getElementById('video');
                    function sendTime() {
                        if (video.duration && isFinite(video.duration)) {
                            try { AndroidBridge.onTimeUpdate(video.currentTime, video.duration, video.paused); } catch(e) {}
                        }
                    }
                    function sendState() {
                        try { AndroidBridge.onPlaybackStateChanged(video.paused, video.ended, video.duration || 0); } catch(e) {}
                    }
                    video.addEventListener('timeupdate', sendTime);
                    video.addEventListener('play', sendState);
                    video.addEventListener('pause', sendState);
                    video.addEventListener('ended', sendState);
                    video.addEventListener('loadedmetadata', sendTime);
                    window.seekTo = function(pos) { video.currentTime = pos; };
                    window.togglePlayPause = function() {
                        if (video.paused) video.play(); else video.pause();
                    };
                    window.seekRelative = function(delta) { video.currentTime = Math.max(0, Math.min(video.duration || 0, video.currentTime + delta)); };
                    try { AndroidBridge.onPlayerReady(); } catch(e) {}
                </script>
            </body>
            </html>
        """.trimIndent()

        webView?.visibility = View.VISIBLE
        webView?.loadDataWithBaseURL("http://player.weebflix.app/", html, "text/html", "UTF-8", null)
        Log.d(TAG, "HTML5 WebView player: ${if (isHls) "hls.js+PROXY" else "direct"} → $videoUrl")

        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID ||
            activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID) {
            wvHideControls()
            Log.d(TAG, "WebView controls hidden for provider: $activeProviderId")
        }
    }

    private fun exitWebViewPlayback() {
        isWebViewPlayback = false
        webViewPlaybackUrl = ""

        webView?.stopLoading()
        webView?.loadUrl("about:blank")

        webViewFullscreenView?.let { view ->
            (view.parent as? FrameLayout)?.removeView(view)
        }
        webViewFullscreenView = null
        webViewFullscreenCallback?.onCustomViewHidden()
        webViewFullscreenCallback = null

        (webView?.parent as? android.view.ViewGroup)?.removeView(webView)
        webView?.removeAllViews()
        webView?.destroy()
        webView = null
        webViewInitialized = false

        webViewPlayerControls.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        centerControls.visibility = View.GONE
        loadingPlayer.visibility = View.GONE
        tvError.visibility = View.GONE
        wvControlsVisible = true

        showServerPickerDialog()
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

    private fun isDrakorKitaServer(server: VideoServer): Boolean {
        return activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID
    }

    private fun playDrakorKitaEpisodePage(server: VideoServer) {
        val baseUrl = server.url.substringBefore("?")
        val qParams = parseDrakorKitaUrl(server.url)
        val epNum = qParams["ep"] ?: "1"
        val tag = qParams["tag"]?.takeIf { it.isNotEmpty() } ?: server.dataNume.ifEmpty { "hs" }
        val cat = qParams["cat"]?.takeIf { it.isNotEmpty() } ?: server.dataType.ifEmpty { "ind" }
        val episodePageUrl = "${baseUrl.trimEnd('/')}/${tag}_${cat}/$epNum/"
        Log.d(TAG, "DrakorKita: episode page URL=$episodePageUrl")
        val nume = server.dataNume.replace("\\", "\\\\").replace("'", "\\'")
        val name = server.name.replace("\\", "\\\\").replace("'", "\\'")
        playEpisodePageViaWebView(episodePageUrl, server, skipInjections = true, customCleanJs = REF_INJECT_ADBLOCK_ONLY)
        webView?.postDelayed({
            val dkJs = """
                (function() {
                    if (window._dkSetupDone) return;
                    window._dkSetupDone = true;
                    var fsVideo = null, fsOrig = {}, hideTimer = null;
                    function exitFS() {
                        if (fsVideo) {
                            for (var k in fsOrig) fsVideo.style[k] = fsOrig[k];
                            var p = fsVideo.parentElement; if (p) { p.style.width = ''; p.style.height = ''; }
                            fsVideo = null; fsOrig = {};
                        }
                        document.body.style.overflow = ''; document.documentElement.style.overflow = '';
                    }
                    function enterFS(v) {
                        exitFS();
                        fsVideo = v;
                        fsOrig = { position: v.style.position, top: v.style.top, left: v.style.left, width: v.style.width, height: v.style.height, zIndex: v.style.zIndex, background: v.style.background, objectFit: v.style.objectFit };
                        v.style.position = 'fixed'; v.style.top = '0'; v.style.left = '0'; v.style.width = '100vw'; v.style.height = '100vh'; v.style.zIndex = '999999'; v.style.background = '#000'; v.style.objectFit = 'contain';
                        var p = v.parentElement; if (p) { p.style.width = '100vw'; p.style.height = '100vh'; }
                        document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden'; window.scrollTo(0, 0);
                    }
                    document.addEventListener('fullscreenchange', function() {
                        if (document.fullscreenElement) { try { document.exitFullscreen(); } catch(e) {} }
                    });
                    window.addEventListener('resize', function() {
                        if (fsVideo) { fsVideo.style.width = '100vw'; fsVideo.style.height = '100vh'; }
                    });
                    setTimeout(function() {
                        if (window._dkBtn) return;
                        var btn = document.createElement('div');
                        window._dkBtn = btn;
                        btn.textContent = '⛶';
                        btn.style.cssText = 'position:fixed;bottom:80px;right:16px;z-index:9999999;width:48px;height:48px;border-radius:24px;background:#E50914;color:#fff;font-size:22px;display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,0.5);opacity:0.8;transition:opacity 0.5s;';
                        var fs = false;
                        function resetHide() {
                            btn.style.opacity = '0.8';
                            if (hideTimer) clearTimeout(hideTimer);
                            hideTimer = setTimeout(function() { btn.style.opacity = '0.15'; }, 4000);
                        }
                        document.addEventListener('touchstart', resetHide);
                        btn.onclick = function(e) {
                            e.stopPropagation();
                            fs = !fs;
                            if (fs) {
                                var v = document.querySelector('video, [class*="drakor"] video, iframe[src*="drakor"]');
                                if (v && v.tagName === 'IFRAME') { try { var v2 = v.contentDocument ? v.contentDocument.querySelector('video') : null; if (v2) v = v2; } catch(e) {} }
                                if (!v) v = document.querySelector('video');
                                if (v) { enterFS(v); btn.textContent = '✕'; }
                                else { fs = false; return; }
                            } else { exitFS(); btn.textContent = '⛶'; }
                            btn.style.background = fs ? '#333' : '#E50914';
                            resetHide();
                        };
                        document.body.appendChild(btn);
                        resetHide();
                    }, 1500);
                    setTimeout(function() {
                        var v = document.querySelector('video');
                        if (v && !v.paused && v.readyState >= 2) return;
                        var el = document.querySelector('[data-nume="$nume"]');
                        if (el) { el.click(); return; }
                        var btns = document.querySelectorAll('.east_player_option, [data-nume], .btn-svx, button');
                        for (var i = 0; i < btns.length; i++) {
                            var txt = btns[i].textContent || '';
                            if (txt.indexOf('$name') !== -1 || btns[i].getAttribute('data-nume') === '$nume') {
                                btns[i].click(); return;
                            }
                        }
                        var first = document.querySelector('#server_lists .btn-svx, .east_player_option, [data-nume]');
                        if (first) first.click();
                    }, 20000);
                })();
            """.trimIndent()
            webView?.evaluateJavascript(dkJs, null)
            Log.d(TAG, "DrakorKita: injected toggle + auto-click JS")
        }, 4000)
    }

    private fun parseDrakorKitaUrl(url: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val queryString = url.substringAfter("?", "")
        if (queryString.isNotEmpty()) {
            queryString.split("&").forEach { param ->
                val kv = param.split("=", limit = 2)
                if (kv.size == 2) params[kv[0]] = kv[1]
            }
        }
        return params
    }

    private suspend fun resolveAbyssUrl(abyssUrl: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving Abyss URL: $abyssUrl")
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(abyssUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .build()
            val response = client.newCall(request).execute()
            val html = response.use { it.body?.string() ?: "" }
            Log.d(TAG, "Abyss page HTML length: ${html.length}")

            val atobPattern = Regex("""atob\(["']([^"']+)["']\)""")
            val atobMatch = atobPattern.find(html)
            if (atobMatch != null) {
                val encoded = atobMatch.groupValues[1]
                Log.d(TAG, "Found atob content, length: ${encoded.length}")
                val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
                Log.d(TAG, "Decoded Abyss payload: ${decoded.take(500)}")

                val domainMatch = Regex(""""domain"\s*:\s*"([^"]+)"""").find(decoded)
                val idMatch = Regex(""""id"\s*:\s*"([^"]+)"""").find(decoded)
                if (domainMatch != null && idMatch != null) {
                    val domain = domainMatch.groupValues[1]
                    val id = idMatch.groupValues[1]
                    val directUrl = "https://$domain/$id"
                    Log.d(TAG, "Abyss resolved: $directUrl")
                    return@withContext directUrl
                }
            }

            val patterns = listOf(
                Regex("""https?://[^\s"']+\.mp4[^\s"']*"""),
                Regex("""https?://[^\s"']+\.m3u8[^\s"']*"""),
                Regex("""https?://[^\s"']+\.mpd[^\s"']*"""),
                Regex("""https?://[^\s"']+googlevideo\.com[^\s"']*""")
            )
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val url = match.value.trim()
                    Log.d(TAG, "Abyss fallback pattern match: $url")
                    return@withContext url
                }
            }

            Log.d(TAG, "Could not resolve Abyss URL, returning embed URL for WebView")
            return@withContext abyssUrl
        } catch (e: Exception) {
            Log.e(TAG, "Abyss resolution failed: ${e.message}")
            return@withContext abyssUrl
        }
    }

    private fun resolveDrakorKitaForWebViewPlayback(server: VideoServer, serverIndex: Int) {
        Log.d(TAG, "DrakorKita: loading episode page for WebView playback...")
        ensureWebView()

        val epParams = parseDrakorKitaUrl(episodeUrl)
        val targetEid = epParams["eid"] ?: ""
        val targetMid = epParams["mid"] ?: server.dataPost

        webViewResolving = true
        webViewResolveMode = ResolveMode.DRAKOR_KITA
        resolveGeneration++
        val gen = resolveGeneration

        webViewResolveCallback = { finalUrl ->
            runOnUiThread {
                if (gen != resolveGeneration) return@runOnUiThread
                webViewResolving = false
                webViewResolveMode = ResolveMode.NONE
                if (!isFinishing && finalUrl.isNotEmpty()) {
                    Log.d(TAG, "DrakorKita embed resolved: $finalUrl → WebView playback")
                    playViaWebView(finalUrl)
                } else if (!isFinishing) {
                    showServerPickerDialog()
                    tvError.visibility = View.VISIBLE
                    tvError.text = getString(R.string.server_failed, server.name)
                }
            }
        }

        webView?.stopLoading()
        webView?.loadUrl(episodeUrl)

        webView?.postDelayed({
            if (webViewResolving && resolveGeneration == gen) {
                val movieId = server.dataPost.replace("\\", "\\\\").replace("'", "\\'")
                val serverType = server.dataNume.replace("\\", "\\\\").replace("'", "\\'")
                val lang = server.dataType.replace("\\", "\\\\").replace("'", "\\'")
                val eid = targetEid.replace("\\", "\\\\").replace("'", "\\'")

                val js = """
                    (function() {
                        var cVal = '', tVal = '';
                        try { cVal = (typeof c !== 'undefined') ? c : ''; } catch(e) {}
                        try { tVal = (typeof t !== 'undefined') ? t : ''; } catch(e) {}
                        var apiHost = '';
                        try { apiHost = (typeof c_api_host !== 'undefined') ? c_api_host : 'https://api.nonton.bid/c_api'; } catch(e) { apiHost = 'https://api.nonton.bid/c_api'; }

                        if (!cVal || !tVal) {
                            var html = document.documentElement.innerHTML;
                            try {
                                var cm = html.match(/var\s+c\s*=\s*['"]([^'"]+)['"]/);
                                var tm = html.match(/var\s+t\s*=\s*['"]([^'"]+)['"]/);
                                if (cm) cVal = cm[1];
                                if (tm) tVal = tm[1];
                            } catch(e) {}
                        }

                        window.AndroidBridge.onTokensFound(cVal, tVal, apiHost);
                    })();
                """.trimIndent()

                webView?.evaluateJavascript(js, null)
            }
        }, 5000)
    }

    private fun resolveDrakorKitaWithWebView(server: VideoServer, serverIndex: Int) {
        Log.e(TAG, "DrakorKita server: loading episode page for direct API resolution...")
        ensureWebView()
        webViewResolving = true
        webViewResolveMode = ResolveMode.DRAKOR_KITA
        pendingResolveServer = server
        pendingResolveServerIndex = serverIndex
        resolveGeneration++
        val gen = resolveGeneration

        val epParams = parseDrakorKitaUrl(episodeUrl)
        val targetEid = epParams["eid"] ?: ""
        val targetMid = epParams["mid"] ?: server.dataPost
        pendingDrakorKitaEid = targetEid
        Log.e(TAG, "DrakorKita parsed: eid=$targetEid, mid=$targetMid, serverType=${server.dataNume}, lang=${server.dataType}")

        webViewResolveCallback = { finalUrl ->
            runOnUiThread {
                if (gen != resolveGeneration) return@runOnUiThread
                webViewResolving = false
                webViewResolveMode = ResolveMode.NONE

                if (!isFinishing && finalUrl.isNotEmpty()) {
                    val isAbyss = finalUrl.contains("abysscdn.com") || finalUrl.contains("hydrax")
                    val isDrakorCdn = finalUrl.contains("drakor.bid") && (finalUrl.contains("init.mp4") || finalUrl.contains("m0v0") || finalUrl.contains("m1v0"))
                    if (isAbyss) {
                        Log.e(TAG, "DrakorKita found Abyss embed, resolving via OkHttp...")
                        lifecycleScope.launch {
                            val resolvedUrl = resolveAbyssUrl(finalUrl)
                            if (!isFinishing && gen == resolveGeneration) {
                                if (resolvedUrl.contains(".mp4") || resolvedUrl.contains(".m3u8") || resolvedUrl.contains(".mpd") || resolvedUrl.contains("googlevideo.com")) {
                                    Log.e(TAG, "Abyss resolved to direct URL: $resolvedUrl")
                                    resolvedUrlCache[serverIndex] = resolvedUrl
                                    loadingPlayer.visibility = View.GONE
                                    playVideoViaHtml5WebView(resolvedUrl)
                                } else {
                                    Log.e(TAG, "Abyss resolved to embed URL, loading in WebView: $resolvedUrl")
                                    loadingPlayer.visibility = View.GONE
                                    resolveEmbedUrlViaWebView(resolvedUrl, server, serverIndex)
                                }
                            }
                        }
                    } else if (isDrakorCdn) {
                        Log.e(TAG, "DrakorKita CDN fragment URL detected (not playable): $finalUrl")
                        scheduleAutoFail(server.name)
                    } else if (finalUrl.contains("embed") || finalUrl.contains("player")) {
                        Log.e(TAG, "DrakorKita found embed page, loading in WebView: $finalUrl")
                        loadingPlayer.visibility = View.GONE
                        resolveEmbedUrlViaWebView(finalUrl, server, serverIndex)
                    } else {
                        resolvedUrlCache[serverIndex] = finalUrl
                        loadingPlayer.visibility = View.GONE
                        playVideoViaHtml5WebView(finalUrl)
                    }
                } else if (!isFinishing) {
                    scheduleAutoFail(server.name)
                }
            }
        }

        webView?.stopLoading()
        webView?.loadUrl(episodeUrl)

        webView?.postDelayed({
            if (webViewResolving && resolveGeneration == gen) {
                Log.e(TAG, "DrakorKita: page loaded, injecting direct API fetch JS...")
                val movieId = server.dataPost.replace("\\", "\\\\").replace("'", "\\'")
                val serverType = server.dataNume.replace("\\", "\\\\").replace("'", "\\'")
                val lang = server.dataType.replace("\\", "\\\\").replace("'", "\\'")
                val eid = targetEid.replace("\\", "\\\\").replace("'", "\\'")

                val js = """
                    (function() {
                        var cVal = '';
                        var tVal = '';
                        try { cVal = (typeof c !== 'undefined') ? c : ''; } catch(e) {}
                        try { tVal = (typeof t !== 'undefined') ? t : ''; } catch(e) {}
                        var apiHost = '';
                        try { apiHost = (typeof c_api_host !== 'undefined') ? c_api_host : 'https://api.nonton.bid/c_api'; } catch(e) { apiHost = 'https://api.nonton.bid/c_api'; }

                        if (!cVal || !tVal) {
                            try {
                                var pageScripts = document.querySelectorAll('script');
                                for (var si = 0; si < pageScripts.length; si++) {
                                    var stxt = pageScripts[si].textContent || '';
                                    var cm = stxt.match(/var\s+c\s*=\s*['"]([^'"]+)['"]/);
                                    var tm = stxt.match(/var\s+t\s*=\s*['"]([^'"]+)['"]/);
                                    if (cm) cVal = cm[1];
                                    if (tm) tVal = tm[1];
                                }
                            } catch(e) {}
                        }

                        if (!cVal || !tVal) {
                            var html = document.documentElement.innerHTML;
                            try {
                                var cm2 = html.match(/var\s+c\s*=\s*['"]([^'"]+)['"]/);
                                var tm2 = html.match(/var\s+t\s*=\s*['"]([^'"]+)['"]/);
                                if (cm2) cVal = cm2[1];
                                if (tm2) tVal = tm2[1];
                            } catch(e) {}
                        }

                        window.AndroidBridge.onTokensFound(cVal, tVal, apiHost);
                    })();
                """.trimIndent()

                webView?.evaluateJavascript(js) { result ->
                    Log.e(TAG, "DrakorKita JS eval result: $result")
                }
            }
        }, 6000)
    }

    private suspend fun resolveDrakorKitaApi(
        server: VideoServer,
        gen: Long,
        cVal: String,
        tVal: String,
        apiHost: String
    ) = withContext(Dispatchers.IO) {
        Log.e(TAG, "DrakorKita API: starting OkHttp chain with c=$cVal, t=$tVal")

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val movieId = server.dataPost
        val serverType = server.dataNume
        val lang = server.dataType
        val eid = pendingDrakorKitaEid

        try {
            // Step 1: episode.php (GET)
            val epUrl = "$apiHost/episode.php?is_mob=0&is_uc=0&movie_id=$movieId&tag=$serverType&cat=$lang"
            Log.e(TAG, "DrakorKita API Step 1: $epUrl")

            val epReq = okhttp3.Request.Builder()
                .url(epUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .addHeader("Origin", "https://drakor.kita.mobi")
                .build()
            val epResp = client.newCall(epReq).execute()
            val epBody = epResp.body?.string() ?: ""
            Log.e(TAG, "DrakorKita API Step 1 response (${epResp.code}): ${epBody.take(300)}")

            if (epResp.code != 200 || epBody.isEmpty()) {
                Log.e(TAG, "DrakorKita API: episode.php failed")
                return@withContext
            }

            var serverXid = ""
            var firstEpId = eid
            try {
                val epJson = org.json.JSONObject(epBody)
                serverXid = epJson.optString("server_xid", "")
                firstEpId = epJson.optString("first_ep_id", eid)
            } catch (e: Exception) {
                val sxMatch = Regex(""""server_xid"\s*:\s*"([^"]+)"""").find(epBody)
                if (sxMatch != null) serverXid = sxMatch.groupValues[1]
                val feMatch = Regex(""""first_ep_id"\s*:\s*"([^"]+)"""").find(epBody)
                if (feMatch != null) firstEpId = feMatch.groupValues[1]
            }
            val targetEp = if (eid.isNotEmpty()) eid else firstEpId
            Log.e(TAG, "DrakorKita API: serverXid=$serverXid, targetEp=$targetEp")

            // Step 2: server.php (POST)
            val serverBody = "is_mob=0&is_uc=0" +
                "&episode_id=${java.net.URLEncoder.encode(targetEp, "UTF-8")}" +
                "&cat=${java.net.URLEncoder.encode(serverType, "UTF-8")}" +
                "&tag=${java.net.URLEncoder.encode(lang, "UTF-8")}" +
                "&server_xid=${java.net.URLEncoder.encode(serverXid, "UTF-8")}" +
                "&c=${java.net.URLEncoder.encode(cVal, "UTF-8")}" +
                "&t=${java.net.URLEncoder.encode(tVal, "UTF-8")}"
            Log.e(TAG, "DrakorKita API Step 2 POST body: $serverBody")

            val srvReq = okhttp3.Request.Builder()
                .url("$apiHost/server.php")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .addHeader("Origin", "https://drakor.kita.mobi")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .post(serverBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            val srvResp = client.newCall(srvReq).execute()
            val srvBody = srvResp.body?.string() ?: ""
            Log.e(TAG, "DrakorKita API Step 2 response (${srvResp.code}): length=${srvBody.length}")
            if (srvBody.isNotEmpty()) Log.e(TAG, "DrakorKita API Step 2 body: ${srvBody.take(500)}")

            if (srvResp.code != 200 || srvBody.isEmpty()) {
                Log.e(TAG, "DrakorKita API: server.php failed (${srvResp.code})")
                // Try video_hydrax.php directly
            } else {
                // Try to extract video URL from server.php response
                try {
                    val srvJson = org.json.JSONObject(srvBody)
                    val hydraxId = srvJson.optString("server_url", "")
                        .ifEmpty { srvJson.optString("url", "") }
                        .ifEmpty { srvJson.optString("embed_url", "") }
                        .ifEmpty { srvJson.optString("id", "") }
                        .ifEmpty { srvJson.optString("hydrax_id", "") }
                        .ifEmpty { srvJson.optString("file", "") }
                        .ifEmpty { srvJson.optString("video_url", "") }
                    Log.e(TAG, "DrakorKita API: hydraxId=$hydraxId")

                    if (hydraxId.startsWith("http")) {
                        if (hydraxId.contains("abysscdn.com") || hydraxId.contains("hydrax")) {
                            Log.e(TAG, "DrakorKita API: found Abyss URL: $hydraxId")
                            withContext(Dispatchers.Main) {
                                if (gen == resolveGeneration) {
                                    webViewResolving = false
                                    webViewResolveMode = ResolveMode.NONE
                                    webViewResolveCallback?.invoke(hydraxId)
                                    webViewResolveCallback = null
                                    pendingResolveServer = null
                                }
                            }
                            return@withContext
                        }
                    }

                    if (hydraxId.isNotEmpty() && !hydraxId.startsWith("http") && hydraxId.length > 3) {
                        val abyssUrl = "https://abysscdn.com/?v=$hydraxId"
                        Log.e(TAG, "DrakorKita API: resolved Abyss: $abyssUrl")
                        withContext(Dispatchers.Main) {
                            if (gen == resolveGeneration) {
                                webViewResolving = false
                                webViewResolveMode = ResolveMode.NONE
                                webViewResolveCallback?.invoke(abyssUrl)
                                webViewResolveCallback = null
                                pendingResolveServer = null
                            }
                        }
                        return@withContext
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DrakorKita API: server.php parse error: ${e.message}")
                }
            }

            // Step 3: video_hydrax.php (POST)
            val hydBody = "is_uc=0" +
                "&id=${java.net.URLEncoder.encode(targetEp, "UTF-8")}" +
                "&qua=hd&res=800x480" +
                "&server_id=${java.net.URLEncoder.encode(serverXid, "UTF-8")}" +
                "&cat=${java.net.URLEncoder.encode(serverType, "UTF-8")}" +
                "&tag=${java.net.URLEncoder.encode(lang, "UTF-8")}" +
                "&c=${java.net.URLEncoder.encode(cVal, "UTF-8")}" +
                "&t=${java.net.URLEncoder.encode(tVal, "UTF-8")}"
            Log.e(TAG, "DrakorKita API Step 3 POST body: $hydBody")

            val hydReq = okhttp3.Request.Builder()
                .url("$apiHost/video_hydrax.php")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .addHeader("Origin", "https://drakor.kita.mobi")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .post(hydBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            val hydResp = client.newCall(hydReq).execute()
            val hydBodyResp = hydResp.body?.string() ?: ""
            Log.e(TAG, "DrakorKita API Step 3 response (${hydResp.code}): length=${hydBodyResp.length}")
            Log.e(TAG, "DrakorKita API Step 3 first 500: ${hydBodyResp.take(500)}")
            Log.e(TAG, "DrakorKita API Step 3 last 500: ${hydBodyResp.takeLast(500)}")

            if (hydResp.code == 200 && hydBodyResp.isNotEmpty()) {
                try {
                    val hdJson = org.json.JSONObject(hydBodyResp)
                    val videoUrl = hdJson.optString("url", "")
                        .ifEmpty { hdJson.optString("file", "") }
                        .ifEmpty { hdJson.optString("video_url", "") }
                        .ifEmpty { hdJson.optString("link", "") }
                        .ifEmpty { hdJson.optString("playbackUrl", "") }
                    Log.e(TAG, "DrakorKita API: videoUrl=$videoUrl")

                    if (videoUrl.startsWith("http")) {
                        withContext(Dispatchers.Main) {
                            if (gen == resolveGeneration) {
                                webViewResolving = false
                                webViewResolveMode = ResolveMode.NONE
                                webViewResolveCallback?.invoke(videoUrl)
                                webViewResolveCallback = null
                                pendingResolveServer = null
                            }
                        }
                        return@withContext
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DrakorKita API: hydrax parse error: ${e.message}")
                }

                // Try regex fallback
                val mp4Match = Regex("https?://[^\\s\"']+\\.mp4[^\\s\"']*").find(hydBodyResp)
                if (mp4Match != null) {
                    val url = mp4Match.value
                    Log.e(TAG, "DrakorKita API: found mp4 URL: $url")
                    withContext(Dispatchers.Main) {
                        if (gen == resolveGeneration) {
                            webViewResolving = false
                            webViewResolveMode = ResolveMode.NONE
                            webViewResolveCallback?.invoke(url)
                            webViewResolveCallback = null
                            pendingResolveServer = null
                        }
                    }
                    return@withContext
                }
            }

            Log.e(TAG, "DrakorKita API: all steps exhausted, no video URL found")
        } catch (e: Exception) {
            Log.e(TAG, "DrakorKita API error: ${e.message}")
        }
    }

    private fun resolveWithWebView(server: VideoServer, callback: (String) -> Unit) {
        if (webViewResolving) {
            callback("")
            return
        }
        ensureWebView()
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
    private var pendingDrakorKitaEid: String = ""

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
                    var scanRoot = embed || document.body;
                    var iframes = scanRoot.querySelectorAll('iframe');
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
                    var vids = scanRoot.querySelectorAll('video, video source, source');
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
        fun onDebug(msg: String?) {
            Log.e(TAG, "DrakorKita JS: $msg")
        }

        @JavascriptInterface
        fun onTokensFound(c: String?, t: String?, apiHost: String?) {
            Log.e(TAG, "DrakorKita tokens: c=$c, t=$t, apiHost=$apiHost")
            var cVal = c ?: ""
            var tVal = t ?: ""
            val host = apiHost ?: "https://api.nonton.bid/c_api"

            if (cVal.isEmpty() || tVal.isEmpty()) {
                val pageUrl = pendingResolveServer?.url?.substringBefore("?") ?: ""
                if (pageUrl.isNotEmpty()) {
                    Log.e(TAG, "DrakorKita: tokens empty from JS, trying OkHttp fallback decode...")
                    try {
                        val req = okhttp3.Request.Builder().url(pageUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                            .build()
                        val resp = getOkHttpClient(cacheDir).newCall(req).execute()
                        val html = resp.use { it.body?.string() ?: "" }
                        if (html.isNotEmpty()) {
                            val encodedRegex = Regex("(\\w+)='([A-Za-z0-9+/=]{15,}\\.([A-Za-z0-9+/=]+\\.){5,}[A-Za-z0-9+/=]+)'")
                            val match = encodedRegex.find(html)
                            if (match != null) {
                                val encoded = match.groupValues[2]
                                val segments = encoded.split(".")
                                val allDigits = StringBuilder()
                                for (segment in segments) {
                                    try {
                                        var padded = segment
                                        while (padded.length % 4 != 0) padded += "="
                                        val bytes = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
                                        val text = String(bytes, Charsets.ISO_8859_1)
                                        for (ch in text) {
                                            if (ch in '0'..'9') allDigits.append(ch)
                                        }
                                    } catch (_: Exception) {}
                                }
                                val digitStr = allDigits.toString()
                                val decodedChars = mutableListOf<Char>()
                                var i = 0
                                while (i + 2 < digitStr.length) {
                                    val code = digitStr.substring(i, i + 3).toIntOrNull() ?: break
                                    if (code in 0..65535) decodedChars.add(code.toChar())
                                    i += 3
                                }
                                val decodedScript = decodedChars.joinToString("")
                                val cm3 = Regex("var\\s+c\\s*=\\s*'([^'']*)'").find(decodedScript)
                                val tm3 = Regex("var\\s+t\\s*=\\s*'([^'']*)'").find(decodedScript)
                                if (cm3 != null) cVal = cm3.groupValues[1]
                                if (tm3 != null) tVal = tm3.groupValues[1]
                                Log.e(TAG, "DrakorKita: OkHttp fallback tokens: c=${cVal.take(20)}, t=${tVal.take(20)}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DrakorKita: OkHttp fallback failed: ${e.message}")
                    }
                }
            }

            if (cVal.isNotEmpty() && tVal.isNotEmpty()) {
                val server = pendingResolveServer ?: return
                val gen = resolveGeneration
                lifecycleScope.launch {
                    resolveDrakorKitaApi(server, gen, cVal, tVal, host)
                }
            }
        }

        @JavascriptInterface
        fun onUrlFound(url: String?) {
            val resolvedUrl = url ?: ""
            if (resolvedUrl.isEmpty() || !resolvedUrl.startsWith("http")) {
                Log.d(TAG, "WebView resolved empty/non-http URL, ignoring")
                return
            }
            val isTrackingUrl = resolvedUrl.contains("yandex.") || resolvedUrl.contains("google-analytics") ||
                resolvedUrl.contains("doubleclick") || resolvedUrl.contains("facebook.com/tr") ||
                resolvedUrl.contains("mc.") || resolvedUrl.contains("analytics") ||
                resolvedUrl.contains("cdn-cgi") || resolvedUrl.contains("/rum") ||
                resolvedUrl.contains("cloudflare") || resolvedUrl.contains("challenges") ||
                resolvedUrl.contains("googletagmanager.com") || resolvedUrl.contains("/collect?")
            if (isTrackingUrl) {
                Log.d(TAG, "WebView resolved tracking/analytics URL, ignoring: $resolvedUrl")
                return
            }
            val isRealVideoUrl = resolvedUrl.contains(".mp4") || resolvedUrl.contains(".m3u8") ||
                resolvedUrl.contains(".mpd") || resolvedUrl.contains("googlevideo.com") ||
                resolvedUrl.contains("videoplayback") || resolvedUrl.contains("wibufile") ||
                resolvedUrl.contains("streamtape") || resolvedUrl.contains("doodstream") ||
                resolvedUrl.contains("fcdn") || resolvedUrl.contains("turboviplay") ||
                resolvedUrl.contains("turbovid") || resolvedUrl.contains("abysscdn") ||
                resolvedUrl.contains("hydrax") || resolvedUrl.contains("minochinos") ||
                resolvedUrl.contains("filelions")
            if (!isRealVideoUrl) {
                Log.w(TAG, "WebView resolved non-video URL, ignoring: $resolvedUrl")
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
                    val provider = com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId)
                    if (provider is com.weebflix.app.data.scraper.SamehadakuScraper) {
                        provider.resolveBloggerVideoG(url)
                    } else { "" }
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

        @JavascriptInterface
        fun onPlayerReady() {
            Log.d(TAG, "WV-Player: ready")
            runOnUiThread {
                wvLoadingSpinner.visibility = View.GONE
                wvCenterControls.visibility = View.GONE
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentTime: Double, duration: Double, paused: Boolean) {
            runOnUiThread {
                if (!isWebViewPlayback) return@runOnUiThread
                val curSec = currentTime.toFloat()
                val durSec = duration.toFloat()
                tvWvCurrentTime.text = formatTime(curSec)
                tvWvTotalTime.text = formatTime(durSec)
                if (!wvUserSeeking && durSec > 0) {
                    wvSeekBar.max = durSec.toInt()
                    wvSeekBar.progress = curSec.toInt()
                }
                val icon = if (paused) R.drawable.ic_player_play else R.drawable.ic_player_pause
                btnWvPlayPause.setImageResource(icon)
                btnWvCenterPlayPause.setImageResource(icon)
                if (!paused) wvScheduleAutoHide()
            }
        }

        @JavascriptInterface
        fun onPlaybackStateChanged(paused: Boolean, ended: Boolean, duration: Double) {
            runOnUiThread {
                if (!isWebViewPlayback) return@runOnUiThread
                val icon = if (paused) R.drawable.ic_player_play else R.drawable.ic_player_pause
                btnWvPlayPause.setImageResource(icon)
                btnWvCenterPlayPause.setImageResource(icon)
            }
        }

        @JavascriptInterface
        fun onHlsManifest(levels: Int) {
            Log.d(TAG, "WV-Player: HLS manifest, $levels levels")
        }

        @JavascriptInterface
        fun onHlsError(msg: String?) {
            Log.e(TAG, "WV-Player: HLS error: $msg")
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initExoPlayer(videoUrl: String) {
        val isTurboM3u8 = videoUrl.contains("turboviplay.com") && videoUrl.contains(".m3u8")
        if (isTurboM3u8 && !videoUrl.startsWith("file://")) {
            Log.d(TAG, "Pre-fetch: intercepting turboviplay m3u8, downloading to local cache...")
            pendingAutoFailRunnable?.let { autoHideHandler.removeCallbacks(it) }
            loadingPlayer.visibility = View.VISIBLE
            var playbackLaunched = false
            lifecycleScope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    prefetchTurboVideo(videoUrl) { readyPath ->
                        if (!playbackLaunched && !isFinishing) {
                            playbackLaunched = true
                            Log.d(TAG, "Pre-fetch: launching ExoPlayer with ready segments")
                            runOnUiThread {
                                resolvedUrlCache[currentServerIndex] = readyPath
                                loadingPlayer.visibility = View.GONE
                                initExoPlayer(readyPath)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (!playbackLaunched && !isFinishing) {
                        if (localPath != null) {
                            Log.d(TAG, "Pre-fetch: all done, playing from local: $localPath")
                            resolvedUrlCache[currentServerIndex] = localPath
                            initExoPlayer(localPath)
                        } else {
                            Log.e(TAG, "Pre-fetch: failed, falling back to remote URL")
                            initExoPlayerRemote(videoUrl)
                        }
                    }
                }
            }
            return
        }
        initExoPlayerRemote(videoUrl)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initExoPlayerRemote(videoUrl: String) {
        showExoPlayerUi()
        releaseMediaSession()
        exoPlayer?.release()
        resetDlProgress()

        val isLocal = videoUrl.startsWith("file://")

        val cache = getSimpleCache(this)
        val okHttpClient = getOkHttpClient(cacheDir)

        val isDrakorP2pHls = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID &&
            videoUrl.contains(".m3u8") && videoUrl.startsWith("http")
        if (isDrakorP2pHls) {
            try {
                val p2pHost = android.net.Uri.parse(videoUrl).host
                if (p2pHost != null) drakorP2pHosts.add(p2pHost)
            } catch (_: Exception) {}
        }

        val rawUpstreamFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            if (!isLocal) {
                if (videoUrl.contains("googlevideo.com")) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://www.blogger.com/",
                        "Origin" to "https://www.blogger.com"
                    ))
                } else if (videoUrl.contains("abysscdn.com") || videoUrl.contains("hydrax") || videoUrl.contains("drakor.bid")) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://drakor.kita.mobi/",
                        "Origin" to "https://drakor.kita.mobi"
                    ))
                } else if (videoUrl.contains("turboviplay.com")) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://turbovidhls.com/",
                        "Origin" to "https://turbovidhls.com"
                    ))
                } else if (videoUrl.contains("cloudflarestorage.com")) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://filedon.co/",
                        "Origin" to "https://filedon.co"
                    ))
                } else if (videoUrl.contains("anichin.stream") || videoUrl.contains("1a-1791.com")) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://anichin.stream/",
                        "Origin" to "https://anichin.stream"
                    ))
                } else if (isDrakorP2pHls) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://drakorkita.stream/",
                        "Origin" to "https://drakorkita.stream"
                    ))
                } else if (videoUrl.contains("surrit.com")) {
                    setDefaultRequestProperties(mapOf(
                        "Referer" to "https://missav.ws/",
                        "Origin" to "https://missav.ws"
                    ))
                }
            }
        }

        val upstreamFactory = object : androidx.media3.datasource.DataSource.Factory {
            override fun createDataSource(): androidx.media3.datasource.DataSource {
                if (videoUrl.startsWith("hydrax://")) {
                    return HydraxDataSource(okHttpClient)
                }
                val ds = rawUpstreamFactory.createDataSource()
                (ds as? androidx.media3.datasource.BaseDataSource)?.addTransferListener(progressTransferListener)
                return ds
            }
        }

        val cacheDataSourceFactory = if (isLocal) {
            androidx.media3.datasource.FileDataSource.Factory()
        } else {
            androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setCacheKeyFactory(object : androidx.media3.datasource.cache.CacheKeyFactory {
                    override fun buildCacheKey(dataSpec: androidx.media3.datasource.DataSpec): String {
                        val uri = dataSpec.uri.toString()
                        return if (uri.contains("turboviplay.com")) {
                            "turboviplay_no_cache_${uri.hashCode()}"
                        } else {
                            uri.hashCode().toString()
                        }
                    }
                })
        }

        val cleanHls = videoUrl.contains("/hls2/") || videoUrl.contains(".urlset/") ||
            videoUrl.contains("dramiyos-cdn.com") || videoUrl.contains("acek-cdn.com") ||
            videoUrl.contains("minochinos") || videoUrl.contains("anichin.stream") ||
            videoUrl.contains("1a-1791.com") || videoUrl.contains("surrit.com")
        val loadControl = if (cleanHls) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,    // minBufferMs (30s — smooth for clean HLS, no 429 risk)
                    120_000,   // maxBufferMs (120s)
                    15_000,    // bufferForPlaybackMs (15s initial buffer)
                    10_000     // bufferForPlaybackAfterRebufferMs (10s after rebuffer)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,    // minBufferMs (15s — buffer runway for 2x hold-seek; CDN sustains ~1.4-1.8x so a 10s head start drains in ~13s)
                    60_000,    // maxBufferMs (60s; grows when bandwidth exceeds consumption)
                    8_000,     // bufferForPlaybackMs (8s initial buffer before play)
                    5_000      // bufferForPlaybackAfterRebufferMs (5s after rebuffer)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

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
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
            .also { player ->
                playerView.player = player
                rebindMediaSession(player)

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
                                    if (dlTrackingActive && dlProgressTotal > 0) {
                                        tvLoadingProgress.visibility = View.VISIBLE
                                        tvLoadingHint.visibility = View.VISIBLE
                                    }
                                }
                                Player.STATE_READY -> {
                                    loadingPlayer.visibility = View.GONE
                                    tvLoadingProgress.visibility = View.GONE
                                    tvLoadingHint.visibility = View.GONE
                                    tvError.visibility = View.GONE
                                    syncByteRetryCount = 0
                                }
                                Player.STATE_ENDED -> {
                                    isPlaying = false
                                    btnPlayPause.setImageResource(R.drawable.ic_player_play)
                                    btnCenterPlayPause.setImageResource(R.drawable.ic_player_play)
                                    showControls()
                                    checkAutoPlay()
                                }
                                Player.STATE_IDLE -> {}
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        runOnUiThread {
                            loadingPlayer.visibility = View.GONE
                            tvLoadingProgress.visibility = View.GONE
                            tvLoadingHint.visibility = View.GONE
                            val errMsg = error.message ?: ""
                            val causeMsg = error.cause?.message ?: ""
                            val fullMsg = "$errMsg $causeMsg"
                            Log.e(TAG, "Player error: $errMsg | cause: $causeMsg")
                            val isSyncByteError = fullMsg.contains("Cannot find sync byte") || fullMsg.contains("Transport Stream") || fullMsg.contains("contentIsMalformed")
                            val isRateLimit = fullMsg.contains("429") || isSyncByteError
                            if (isRateLimit && currentServerIndex in servers.indices) {
                                val cachedUrl = resolvedUrlCache[currentServerIndex] ?: ""
                                if (cachedUrl.isNotEmpty() && syncByteRetryCount < maxSyncByteRetries) {
                                    syncByteRetryCount++
                                    val delayMs = if (isSyncByteError) 5000L * syncByteRetryCount else 3000L
                                    Log.w(TAG, "Rate limit / sync byte error, retry $syncByteRetryCount/$maxSyncByteRetries in ${delayMs}ms...")
                                    pendingAutoFailRunnable?.let { autoHideHandler.removeCallbacks(it) }
                                    pendingAutoFailRunnable = Runnable {
                                        if (!isFinishing && cachedUrl.isNotEmpty()) {
                                            Log.d(TAG, "Retrying ExoPlayer with cached URL (attempt $syncByteRetryCount)")
                                            loadingPlayer.visibility = View.VISIBLE
                                            initExoPlayer(cachedUrl)
                                        }
                                    }
                                    autoHideHandler.postDelayed(pendingAutoFailRunnable!!, delayMs)
                                } else {
                                    Log.w(TAG, "Rate limit: exhausted retries ($syncByteRetryCount/$maxSyncByteRetries), trying next server")
                                    syncByteRetryCount = 0
                                    resolvedUrlCache.remove(currentServerIndex)
                                    pendingAutoFailRunnable?.let { autoHideHandler.removeCallbacks(it) }
                                    val serverName = servers[currentServerIndex].name
                                    scheduleAutoFail(serverName)
                                }
                            } else {
                                val serverName = if (currentServerIndex in servers.indices) servers[currentServerIndex].name else "Unknown"
                                val failedServer = if (currentServerIndex in servers.indices) servers[currentServerIndex] else null
                                if (failedServer != null &&
                                    activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID &&
                                    failedServer.dataType == "dl" &&
                                    !drakorDlFallbackTried) {
                                    Log.w(TAG, "DrakorKita dl ExoPlayer failed, falling back to WebView path-based playback")
                                    drakorDlFallbackTried = true
                                    resolvedUrlCache.remove(currentServerIndex)
                                    pendingAutoFailRunnable?.let { autoHideHandler.removeCallbacks(it) }
                                    loadingPlayer.visibility = View.GONE
                                    playDrakorKitaEpisodePage(failedServer)
                                } else {
                                    scheduleAutoFail(serverName)
                                }
                            }
                        }
                    }
                })

                val mediaItem = MediaItem.fromUri(videoUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true

                progressUpdateHandler.postDelayed(progressUpdateRunnable, 500)
            }
    }

    private var gestureStartY: Float = 0f
    private var gestureStartX: Float = 0f
    private var isGestureActive: Boolean = false
    private var gestureType: Int = 0 // 0=none, 1=brightness, 2=volume, 3=seek, 4=collapse, 5=fullscreen-swipe, 6=hold-seek
    private var fullscreenSwipeFired = false

    // Press-and-hold seek (Telegram/YouTube-style): long-press on the RIGHT half of the video
    // starts ACTUALLY fast-forwarding the playback (e.g. 2x) — the video really speeds up like
    // Telegram. Releasing returns to the original speed and the video continues normally from
    // wherever it is, with NO artificial seek jump (which is why the earlier ticker version felt
    // wrong — it faked a jump on release while the video kept playing underneath).
    private var holdSeekActive = false
    private var holdSeekOriginalParams: PlaybackParameters? = null
    private var holdSeekStartMs = 0L
    private var holdSeekRampLevel = 2
    private val holdSeekRampMs = 3_000L
    private val holdSeekMaxLevel = 5
    private val holdSeekSpeed = 2f // x2 while held — Telegram's actual rate. Ramp steps up one
    // level every holdSeekRampMs of continuous holding (2x → 3x → 4x → 5x). Discrete steps at
    // ~3s intervals, NOT a per-tick ticker: changing playback parameters mid-hold repeatedly
    // (an earlier adaptive ticker eased the speed off/on with the buffer margin every 250ms)
    // makes ExoPlayer reconfigure the renderers → the video looked choppy/"loncat-loncat".
    // The hold is armed on ACTION_DOWN with an OWN short timer (320ms) instead of relying on
    // GestureDetector.onLongPress (500ms): GD cancels its long-press the instant the finger
    // crosses touch-slop and re-classifies the gesture as a volume swipe — the "hold-seek
    // kadang bentrok sama volume" bug. Our own timer claims the hold FIRST and blocks the
    // volume/brightness/seek classification, and any volume drift caused by pre-hold micro-jitter
    // is rolled back when the hold engages.
    private val holdSeekHandler = Handler(Looper.getMainLooper())
    private var holdSeekPending = false
    private var holdSeekDownX = 0f
    private var holdSeekDownY = 0f
    private var holdSeekDownVolumeFloat = 0f
    private val holdSeekFireRunnable = object : Runnable {
        override fun run() {
            holdSeekPending = false
            fireHoldSeek(holdSeekDownX, holdSeekDownY)
        }
    }
    // Diagnostic: while a hold is active, log position/speed/buffer every 250ms so a live
    // logcat can show whether 2x actually advances smoothly (each tick +~500ms media) or
    // stepwise/stalled. Removed later once the math is confirmed.
    private val holdSeekPosRunnable = object : Runnable {
        override fun run() {
            if (!holdSeekActive) return
            val player = exoPlayer
            if (player != null) {
                val buffered = player.bufferedPosition - player.currentPosition
                val speed = player.playbackParameters.speed
                Log.d(TAG, "hold-seek t+ pos=${player.currentPosition}ms speed=$speed buffered=${buffered}ms playing=${player.isPlaying}")
                // Ramp: one speed level up every holdSeekRampMs of CONTINUOUS holding (2x → 3x →
                // 4x → 5x). Discrete step per 3s — no per-tick reconfiguration → no frame jank.
                val heldMs = SystemClock.uptimeMillis() - holdSeekStartMs
                val targetLevel = when {
                    heldMs >= holdSeekRampMs * (holdSeekMaxLevel - 2) -> holdSeekMaxLevel
                    heldMs >= holdSeekRampMs * 2 -> 4
                    heldMs >= holdSeekRampMs -> 3
                    else -> 2
                }
                holdSeekRampLevel = targetLevel
                // Buffer guard: a long hold drains buffer ~0.6s/s wall (CDN sustains only ~1.4-1.8x),
                // so from a ~10-15s head start the buffer hits 0 after ~10-15s and playback FREEZES
                // ("nggak jalan sama sekali") even while the finger stays down. Step the speed down
                // ONCE (discrete, not a per-tick ticker → no reconfig jank) when the buffer runs thin,
                // and back up to the ramp target once it recovers (hysteresis 6s/10s). At 1.5x
                // consumption ~= CDN rate, so the hold can keep moving indefinitely instead of stalling.
                if (buffered < 6_000L && speed >= 1.9f) {
                    player.setPlaybackParameters(PlaybackParameters(1.5f, player.playbackParameters.pitch))
                    Log.d(TAG, "hold-seek buffer low (${buffered}ms) -> step down to 1.5x")
                } else if (buffered > 10_000L && speed <= 1.6f) {
                    player.setPlaybackParameters(PlaybackParameters(targetLevel.toFloat(), player.playbackParameters.pitch))
                    Log.d(TAG, "hold-seek buffer ok (${buffered}ms) -> x$targetLevel")
                } else if (speed >= 1.9f && Math.abs(speed - targetLevel) > 0.05f) {
                    player.setPlaybackParameters(PlaybackParameters(targetLevel.toFloat(), player.playbackParameters.pitch))
                    Log.d(TAG, "hold-seek ramp ${speed}x -> x$targetLevel (buffered=${buffered}ms)")
                }
                val shownSpeed = player.playbackParameters.speed
                val badge = when {
                    shownSpeed >= holdSeekMaxLevel - 0.05f -> "x$holdSeekMaxLevel"
                    shownSpeed >= 1.85f -> "x${(shownSpeed + 0.05f).toInt()}"
                    shownSpeed >= 1.45f -> "x1.5"
                    else -> "x${(shownSpeed + 0.05f).toInt()}"
                }
                seekText.text = "$badge ⏩"
                updateHoldSeekTime(player)
            }
            holdSeekHandler.postDelayed(this, 250)
        }
    }
    // While holding we ALSO drop the video track to <=480p (only for adaptive multi-format
    // sources; progressive MP4/TS stays untouched). At 2x the phone decoder can't keep up with
    // 1080p/720p, so rugby the player drops whole frames and the fast-forward "counts per second"
    // instead of moving smoothly. A <480p track decodes with huge headroom → buttery motion like
    // Telegram/YouTube's built-in fast-forward. The exact prior selection (incl. any gear override)
    // is restored on release.
    private var holdVideoRendererIndex = -1
    private var holdVideoPriorOverride: DefaultTrackSelector.SelectionOverride? = null

    // Pinch-to-zoom (all providers, runs anytime the video surface is visible). Applied to the
    // visible video surface (playerView for ExoPlayer, webView for WebView playback, or the
    // fullscreen WebView view). Range 1x..4x.
    private var pinchScaleDetector: ScaleGestureDetector? = null
    private var videoZoom = 1f
    private var videoZoomBase = 1f
    private var videoZoomPeak = 1f
    private var videoZoomOffsetX = 0f
    private var videoZoomOffsetY = 0f
    private var videoZoomFocusX = 0f
    private var videoZoomFocusY = 0f
    private var videoZoomDirty = false
    private val maxVideoZoom = 4f

    /** Bottom of the swipe/dead zone for the gesture overlay. When the bottom bar is a root-level
     *  view that sits below the gesture area (YouTube portrait: video is a 16:9 strip at the top)
     *  there is nothing to avoid, so the whole gesture area stays usable. In fullscreen players
     *  the bottom bar overlaps the gesture area and its height is reserved. */
    private fun gestureDeadZoneBottom(): Int {
        val gh = gestureOverlay.height
        return if (bottomBar.top >= gh) {
            (gh - 20).coerceAtLeast(0)
        } else {
            (gh - bottomBar.height - 20).coerceAtLeast(0)
        }
    }

    private fun setupGestureDetector() {
        if (isTvMode) {
            gestureOverlay.isClickable = false
            gestureOverlay.isFocusable = false
            gestureOverlay.setOnTouchListener(null)
            return
        }
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = gestureOverlay.width
                val tapX = e.x
                val centerX = viewWidth / 2f
                val deadZoneTop = topBar.height + 20
                val deadZoneBottom = gestureDeadZoneBottom()

                if (e.y in deadZoneTop.toFloat()..deadZoneBottom.toFloat()) {
                    if (tapX < centerX) {
                        seekBy(-10f)
                        showSeekIndicator(false, "-10s")
                    } else {
                        seekBy(10f)
                        showSeekIndicator(true, "+10s")
                    }
                }
                return true
            }

            // Hold-seek uses PlayerActivity's OWN short timer (armed on ACTION_DOWN in the touch
            // listener), NOT GestureDetector.onLongPress (500ms) — GD cancels its long-press the
            // moment the finger crosses touch-slop and mis-routes the intent into a volume swipe.

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (holdSeekActive) return true
                val isVertical = abs(velocityY) > abs(velocityX) * 1.5f

                // Swipe up → enter fullscreen (GoTube-style). YouTube portrait is the real
                // non-fullscreen state; other providers are already landscape fullscreen so this
                // only re-hides bars if the user previously revealed them. Fast flings only —
                // drags remain reserved for brightness/volume.
                if (velocityY < -1400f && isVertical) {
                    val startedOnVideo = e1 != null && e1.y <= playerArea.bottom
                    if (!startedOnVideo) return false
                    when {
                        activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID && !ytFullscreen -> {
                            toggleFullscreen()
                            return true
                        }
                        activeProviderId != com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID && !isSystemBarsHidden -> {
                            toggleFullscreen()
                            return true
                        }
                    }
                }

                // Swipe down while fullscreen → exit (YouTube landscape back to portrait).
                if (velocityY > 1400f && isVertical &&
                    activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID && ytFullscreen) {
                    toggleFullscreen()
                    return true
                }

                if (canMiniPlayer() && velocityY > 1200f && isVertical) {
                    collapseYtPlayer()
                    return true
                }
                return false
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false

                val deadZoneTop = topBar.height + 10
                val deadZoneBottom = gestureDeadZoneBottom()
                val startInDeadZone = e1.y < deadZoneTop || e1.y > deadZoneBottom

                // A downward drag in the mini-player-capable state is reserved as a collapse
                // intent. The classification is sticky for the whole gesture, so a slow pull
                // (below the speed gate) is never re-routed into handleVolumeGesture/
                // handleBrightnessGesture and can't drive STREAM_MUSIC down to 0. Fast enough
                // pulls still collapse when the gate is met.
                if (canMiniPlayer() && !startInDeadZone) {
                    if (!isGestureActive) {
                        val dX = e2.x - e1.x
                        val dY = e2.y - e1.y
                        if (dY > 20 && abs(dY) > abs(dX)) {
                            gestureStartY = e1.y
                            isGestureActive = true
                            gestureType = 4 // collapse intent
                        }
                    }
                    if (isGestureActive && gestureType == 4) {
                        val deltaX = e2.x - e1.x
                        val deltaY = e2.y - e1.y
                        val elapsed = (e2.eventTime - e1.downTime).coerceAtLeast(1L)
                        val pullSpeed = deltaY / elapsed.toFloat()
                        if (deltaY > 60 && abs(deltaY) > abs(deltaX) * 1.5f && pullSpeed > 0.8f) {
                            collapseYtPlayer()
                        }
                        return true
                    }
                }

                // Keep consuming once a gesture was reserved as a collapse intent, even after
                // the player has collapsed (the overlay is still in the touch path until the
                // feed above it takes over).
                if (isGestureActive && gestureType == 4) return true

                // A fast upward swipe in the non-fullscreen state is reserved as a fullscreen
                // intent (same sticky pattern as the collapse above). Without this, the series
                // of onScroll events that precede onFling classify as a volume gesture (right
                // half) and bump STREAM_MUSIC while the fling enters fullscreen — the "swipe
                // atas bentrok sama volume" bug. Slow upward drags still keep brightness/volume.
                val wantFullscreen = if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID)
                    !ytFullscreen else !isSystemBarsHidden
                if (wantFullscreen && e1.y <= playerArea.bottom) {
                    if (!isGestureActive) {
                        val dX = e2.x - e1.x
                        val dY = e2.y - e1.y
                        if (dY < -24 && abs(dY) > abs(dX) * 1.5f) {
                            gestureStartY = e1.y
                            isGestureActive = true
                            gestureType = 5 // fullscreen-swipe intent
                        }
                    }
                    if (isGestureActive && gestureType == 5) {
                        val deltaY = e2.y - e1.y
                        val elapsed = (e2.eventTime - e1.downTime).coerceAtLeast(1L)
                        val swipeSpeed = deltaY / elapsed.toFloat()
                        if (!fullscreenSwipeFired && swipeSpeed < -0.9f) {
                            fullscreenSwipeFired = true
                            toggleFullscreen()
                            showControls()
                        }
                        return true
                    }
                }

                if (startInDeadZone) return false

                if (!isGestureActive) {
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y
                    if (abs(deltaX) < 30 && abs(deltaY) < 20) return false

                    gestureStartX = e1.x
                    gestureStartY = e1.y
                    isGestureActive = true
                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 30) {
                        gestureType = 3 // seek
                    } else if (abs(deltaY) > abs(deltaX)) {
                        gestureType = if (e1.x < gestureOverlay.width / 2f) 1 else 2
                    } else {
                        isGestureActive = false
                        return false
                    }
                }

                when (gestureType) {
                    1 -> handleBrightnessGesture(distanceY)
                    2 -> handleVolumeGesture(distanceY)
                    3 -> handleSeekGesture(gestureStartX, e2.x)
                    // 6 = hold-seek: no drag handling, the video just plays at x3 while held
                }
                return true
            }
        })

        pinchScaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                // While a hold-seek is active the ENTIRE gesture area is reserved for the
                // fast-forward — a pinch attempt must never resize the video under the holding
                // finger (the "zoom bentrok saat hold" bug). Rejecting begin means onScale never
                // fires, so the video keeps its current zoom and the hold keeps speeding.
                if (holdSeekActive) return false
                videoZoomBase = videoZoom
                videoZoomPeak = videoZoom
                // Pinch focus is captured once at the START of the gesture (not re-read every
                // onScale) so a zoom-in that starts with fingers already spread far apart stays
                // centered on the initial point — the end focus can drift off-screen when fingers
                // spread wide, pivoting the zoom off the visible video.
                videoZoomFocusX = detector.focusX
                videoZoomFocusY = detector.focusY
                // Precompute the overlay-vs-surface offset once here (getLocationOnScreen per
                // onScale event is a sync traversal that throttles the gesture and makes the zoom
                // feel stuttery). During a single pinch the offset doesn't change.
                val ref = pinchOverlayRef ?: gestureOverlay
                val surface = currentVideoSurface()
                if (ref === surface) {
                    videoZoomOffsetX = 0f
                    videoZoomOffsetY = 0f
                } else if (surface != null) {
                    val refLoc = IntArray(2)
                    val surfLoc = IntArray(2)
                    ref.getLocationOnScreen(refLoc)
                    surface.getLocationOnScreen(surfLoc)
                    videoZoomOffsetX = (refLoc[0] - surfLoc[0]).toFloat()
                    videoZoomOffsetY = (refLoc[1] - surfLoc[1]).toFloat()
                }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                videoZoom = (videoZoomBase * detector.scaleFactor).coerceIn(1f, maxVideoZoom)
                if (videoZoom > videoZoomPeak) videoZoomPeak = videoZoom
                // Touch events arrive at 120Hz+ on modern devices while rendering is 60fps —
                // applying the transform on EVERY event would apply it twice within a frame and
                // the video visibly double-steps ("tersendat"). Instead only store the target and
                // let ONE coalesced frame callback (postOnAnimation) apply the latest value per
                // rendered frame, so zoom animates as smooth 60fps geometry like GoTube.
                videoZoomDirty = true
                val poster = pinchOverlayRef ?: gestureOverlay
                poster.removeCallbacks(zoomFrameRunnable)
                poster.postOnAnimation(zoomFrameRunnable)
                showZoomIndicator()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // Rubber-band commit: when the pinch peaked clearly above the start span, the user
                // spread-flicked forward then relaxed their fingers — commit the PEAK zoom reached
                // instead of the release ratio, otherwise spread-then-close pulses snap back to 1x
                // and the pinch reads as "not working". Pure close pinches (never opened beyond
                // base) fall through and commit the release value, so zoom-out still works.
                if (videoZoomPeak > videoZoomBase * 1.05f) {
                    videoZoom = videoZoomPeak
                }
                videoZoomBase = videoZoom
                videoZoomDirty = false
                (pinchOverlayRef ?: gestureOverlay).removeCallbacks(zoomFrameRunnable)
                applyVideoZoom(videoZoom, videoZoomFocusX, videoZoomFocusY)
                zoomHideHandler.removeCallbacks(zoomHideRunnable)
                zoomHideHandler.postDelayed(zoomHideRunnable, 1200)
            }
        })

        gestureOverlay.setOnTouchListener { _, event ->
            val action = event.actionMasked
            val pointerCount = event.pointerCount
            pinchOverlayRef = gestureOverlay

            // Two or more fingers down = pinch-zoom intent. Drop any in-progress single-finger
            // gesture (seek/volume/brightness/collapse/hold-seek) immediately so the pinch never
            // fires a seek on release and doesn't fight the seek indicator. Without this, a
            // 2-finger pinch that starts as a 1-finger drag keeps its sticky seek gesture and
            // seeks on lift — the "zoom conflicts with the duration swipe" bug.
            // An ACTIVE hold-seek is EXEMPT: Telegram/YouTube don't drop the fast-forward when a
            // second finger (palm/thumb graze, pinch attempt) lands. The extra finger is ignored,
            // the pinch is blocked (see onScaleBegin), and the hold runs until the PRIMARY finger
            // lifts. Before the hold has engaged (still pending) a second finger still aborts it
            // like any normal pinch intent.
            if (pointerCount >= 2) {
                suppressSingleAfterPinch = true
                cancelPendingHold()
                if (!holdSeekActive) {
                    cancelSingleFingerGesture()
                }
            }

            pinchScaleDetector?.onTouchEvent(event)
            val pinchActive = pinchScaleDetector?.isInProgress == true

            // Feed the single-finger gestures only while fewer than 2 fingers are down AND no
            // pinch happened earlier in this touch sequence. The second condition blocks the
            // leftover finger (after one pinch finger is lifted) from being misread as a fresh
            // horizontal drag that seeks — the other half of the "zoom vs rewind" conflict.
            if (pointerCount < 2 && !pinchActive && !suppressSingleAfterPinch) {
                gestureDetector.onTouchEvent(event)
            }

            // ARM the hold-seek on finger-down. We don't use GestureDetector.onLongPress because
            // it backs out the moment the finger crosses touch-slop (a ~500ms wait the mic-rotor
            // almost always loses → the hold intent steals a volume swipe instead). Our own 320ms
            // timer engages the hold first, then the volume/brightness/seek scroll classifier
            // never gets to claim the gesture. The timer is cancelled if the finger actually
            // moves (that's a real swipe) or lifts.
            if (action == MotionEvent.ACTION_DOWN && pointerCount == 1 && !isTvMode && !holdSeekPending) {
                holdSeekDownX = event.x
                holdSeekDownY = event.y
                holdSeekDownVolumeFloat = volumeFloat
                holdSeekPending = true
                holdSeekHandler.removeCallbacks(holdSeekFireRunnable)
                holdSeekHandler.postDelayed(holdSeekFireRunnable, 320)
            } else if (action == MotionEvent.ACTION_MOVE && holdSeekPending && !holdSeekActive) {
                val dx = event.x - holdSeekDownX
                val dy = event.y - holdSeekDownY
                if (dx * dx + dy * dy > HOLD_SLOP_UNSIGNED_SQUARED) cancelPendingHold()
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                cancelPendingHold()
                suppressSingleAfterPinch = false
                fullscreenSwipeFired = false
                if (isSeekingGesture) {
                    isSeekingGesture = false
                    seekBy(seekDelta.toFloat())
                    hideSeekIndicator()
                }
                if (holdSeekActive) {
                    Log.d(TAG, "hold-seek lift (primary finger up)")
                    holdSeekActive = false
                    restoreHoldSeekSpeed()
                    hideSeekIndicator()
                }
                hideBrightnessIndicator()
                hideVolumeIndicator()
                isGestureActive = false
                gestureType = 0
                seekDelta = 0L
                isSeekingGesture = false
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                // One finger lifted. If fewer than 2 remain the pinch is over — drop the stale
                // single-finger state so the leftover finger can't be mistaken for a seek. An
                // ACTIVE hold-seek is left running (the primary finger is still down — the lifted
                // one was the accidental extra finger).
                if (event.pointerCount - 1 < 2 && !holdSeekActive) {
                    cancelSingleFingerGesture()
                }
            }
            true
        }
    }

    /** Aborts an in-progress single-finger gesture (seek/volume/brightness/collapse/hold-seek)
     *  without applying any accumulated effect. Called when a second finger lands (pinch start)
     *  or a pinch finger lifts back to a single finger. */
    private fun cancelSingleFingerGesture() {
        if (!isGestureActive && !isSeekingGesture && gestureType == 0 && seekDelta == 0L && !holdSeekActive) return
        fullscreenSwipeFired = false
        if (holdSeekActive) {
            Log.d(TAG, "hold-seek abort (single-finger gesture cancelled)")
            holdSeekActive = false
            restoreHoldSeekSpeed()
        }
        isSeekingGesture = false
        seekDelta = 0L
        isGestureActive = false
        gestureType = 0
        hideSeekIndicator()
        hideBrightnessIndicator()
        hideVolumeIndicator()
    }

    private fun showZoomIndicator() {
        if (!::zoomIndicator.isInitialized) return
        zoomText.text = "${Math.round(videoZoom * 100)}%"
        zoomIndicator.visibility = View.VISIBLE
        zoomHideHandler.removeCallbacks(zoomHideRunnable)
    }

    /** The view that currently presents the video on screen. During WebView fullscreen
     *  (onShowCustomView) Chromium renders the video in a dedicated fullscreen view hosted in
     *  playerContainer — NOT the webView page itself — so zoom must target that view, otherwise
     *  the pinch silently does nothing in fullscreen. */
    private fun currentVideoSurface(): View? {
        val fs = webViewFullscreenView
        if (fs != null && fs.parent != null) return fs
        return if (isWebViewPlayback) webView else playerView
    }

    /** Applies the pinch zoom to the visible video surface. Pivot follows the pinch focal
     *  point so the zoom centers where the fingers are. Both surfaces (playerView for ExoPlayer,
     *  webView for WebView playback) live in playerContainer which fills playerArea, so the
     *  gestureOverlay's focus coordinates map 1:1 to them. For webViewPlayerControls (a root-level
     *  full-screen overlay) the focus conversion offset is precomputed once in onScaleBegin
     *  (videoZoomOffsetX/Y), not read via getLocationOnScreen on every event (sync traversal jank). */
    private fun applyVideoZoom(zoom: Float, focusX: Float, focusY: Float) {
        val surface = currentVideoSurface()
        if (surface == null || surface.visibility != View.VISIBLE) return
        surface.pivotX = focusX + videoZoomOffsetX
        surface.pivotY = focusY + videoZoomOffsetY
        surface.scaleX = zoom
        surface.scaleY = zoom
    }

    /** Applies the latest zoom+pivot once per rendered frame. onScale fires on every 120Hz+ touch
     *  event while rendering runs at 60fps — coalescing here keeps the visual at exactly one
     *  transform per frame so the zoom animates as smooth geometry (see onScale). */
    private val zoomFrameRunnable = Runnable {
        if (!videoZoomDirty) return@Runnable
        videoZoomDirty = false
        applyVideoZoom(videoZoom, videoZoomFocusX, videoZoomFocusY)
    }

    /** Resets pinch zoom back to 1x on both surfaces. */
    private fun resetVideoZoom() {
        videoZoom = 1f
        videoZoomBase = 1f
        videoZoomPeak = 1f
        videoZoomOffsetX = 0f
        videoZoomOffsetY = 0f
        videoZoomFocusX = 0f
        videoZoomFocusY = 0f
        videoZoomDirty = false
        gestureOverlay.removeCallbacks(zoomFrameRunnable)
        playerView.scaleX = 1f
        playerView.scaleY = 1f
        webView?.scaleX = 1f
        webView?.scaleY = 1f
        webViewFullscreenView?.scaleX = 1f
        webViewFullscreenView?.scaleY = 1f
        zoomHideHandler.removeCallbacks(zoomHideRunnable)
        if (::zoomIndicator.isInitialized) zoomIndicator.visibility = View.GONE
    }

    private fun setupControls() {
        btnBack.setOnClickListener { finish() }
        btnWebViewBack.setOnClickListener { exitWebViewPlayback() }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnCenterPlayPause.setOnClickListener { togglePlayPause() }

        btnRewind.setOnClickListener {
            seekBy(-10f)
            showSeekIndicator(false, "-10s")
            scheduleAutoHide()
        }

        btnForward.setOnClickListener {
            seekBy(10f)
            showSeekIndicator(true, "+10s")
            scheduleAutoHide()
        }

        btnPrevEpisodeNav.setOnClickListener {
            if (animeUrl.isNotEmpty()) {
                val intent = Intent(this, com.weebflix.app.ui.detail.AnimeDetailActivity::class.java).apply {
                    putExtra("url", animeUrl)
                }
                startActivity(intent)
                finish()
            }
        }

        btnNextEpisodeNav.setOnClickListener {
            navigateToNextEpisode()
        }

        btnYtPrev.setOnClickListener { playYtPrevVideo() }
        btnYtNext.setOnClickListener { playYtNextVideo() }

        btnPip.setOnClickListener { enterPipMode() }
        btnFullscreen.setOnClickListener { toggleFullscreen() }
        btnQuality.setOnClickListener {
            showYtResolutionDialog()
        }
        tvServerName.setOnClickListener { showServerPickerDialog() }
        tvError.setOnClickListener { if (servers.isNotEmpty()) showServerPickerDialog() }

        btnSkipOpening.setOnClickListener {
            val targetMs = if (activeSkipOpeningEndMs > 0L) activeSkipOpeningEndMs else skipOpeningEnd * 1000L
            exoPlayer?.seekTo(targetMs)
            btnSkipOpening.visibility = View.GONE
            scheduleAutoHide()
        }

        btnSkipOutro.setOnClickListener {
            val duration = exoPlayer?.duration?.takeIf { it > 0 } ?: return@setOnClickListener
            exoPlayer?.seekTo(duration - 5000L)
            btnSkipOutro.visibility = View.GONE
            scheduleAutoHide()
        }

        btnCancelAutoPlay.setOnClickListener { cancelAutoPlay() }
        btnPlayNow.setOnClickListener {
            autoPlayHandler.removeCallbacks(autoPlayRunnable)
            navigateToNextEpisode()
        }
    }

    private fun setupWebViewControls() {
        btnWebViewBack.setOnClickListener { exitWebViewPlayback() }
        btnWvPlayPause.setOnClickListener { webView?.evaluateJavascript("window.togglePlayPause()", null) }
        btnWvCenterPlayPause.setOnClickListener { webView?.evaluateJavascript("window.togglePlayPause()", null) }
        btnWvRewind.setOnClickListener { webView?.evaluateJavascript("window.seekRelative(-10)", null) }
        btnWvForward.setOnClickListener { webView?.evaluateJavascript("window.seekRelative(10)", null) }
        btnWvFullscreen.setOnClickListener { toggleFullscreen() }
        btnWvPip.setOnClickListener { enterPipMode() }
        tvWvServerBadge.setOnClickListener { showServerPickerDialog() }

        wvCenterControls.setOnClickListener { toggleWvControls() }
        wvBottomBar.setOnClickListener { wvScheduleAutoHide() }

        webViewPlayerControls.setOnTouchListener { _, event ->
            pinchOverlayRef = webViewPlayerControls
            pinchScaleDetector?.onTouchEvent(event)
            val pinchActive = pinchScaleDetector?.isInProgress == true
            if (event.actionMasked == MotionEvent.ACTION_UP && !pinchActive && !wvControlsVisible && isWebViewPlayback) {
                val isControlHidingProvider = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID ||
                    activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID
                if (!isControlHidingProvider) {
                    wvShowControls()
                }
            }
            pinchActive
        }

        wvSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvWvCurrentTime.text = formatTime(progress.toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wvUserSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val pos = seekBar?.progress ?: return
                webView?.evaluateJavascript("window.seekTo($pos)", null)
                wvUserSeeking = false
            }
        })
    }

    private fun toggleWvControls() {
        if (wvControlsVisible) {
            wvTopBar.visibility = View.GONE
            wvBottomBar.visibility = View.GONE
            wvCenterControls.visibility = View.VISIBLE
            wvControlsVisible = false
        } else {
            wvTopBar.visibility = View.VISIBLE
            wvBottomBar.visibility = View.VISIBLE
            wvCenterControls.visibility = View.GONE
            wvControlsVisible = true
            wvScheduleAutoHide()
        }
    }

    private fun wvShowControls() {
        wvTopBar.visibility = View.VISIBLE
        wvBottomBar.visibility = View.VISIBLE
        wvCenterControls.visibility = View.GONE
        wvControlsVisible = true
        wvScheduleAutoHide()
    }

    private fun wvHideControls() {
        wvTopBar.visibility = View.GONE
        wvBottomBar.visibility = View.GONE
        wvCenterControls.visibility = View.GONE
        wvControlsVisible = false
    }

    private fun wvScheduleAutoHide() {
        wvAutoHideHandler.removeCallbacks(wvAutoHideRunnable)
        wvAutoHideHandler.postDelayed(wvAutoHideRunnable, 4000)
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

    private fun handleBrightnessGesture(distanceY: Float) {
        val sensitivity = 0.004f
        val delta = distanceY * sensitivity
        currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = currentBrightness
        window.attributes = layoutParams
        val percent = (currentBrightness * 100).toInt()
        brightnessProgress.progress = percent
        brightnessText.text = "$percent%"
        showBrightnessIndicator()
    }

    private fun handleVolumeGesture(distanceY: Float) {
        val sensitivity = maxVolume.toFloat() / (gestureOverlay.height.toFloat() * 0.4f)
        // Accumulate as a float so fractional movements carry over between events (like the
        // brightness gesture) instead of being truncated to zero every event, which made the
        // volume feel stuttery/jerky on small movements.
        volumeFloat = (volumeFloat + distanceY * sensitivity).coerceIn(0f, maxVolume.toFloat())
        val newVolume = volumeFloat.toInt()
        if (newVolume != currentVolume) {
            currentVolume = newVolume
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
        }
        val percent = (volumeFloat * 100f / maxVolume).toInt()
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

    // ===== Hold-seek (Telegram-style press-and-hold on the right half) =====

    private fun fireHoldSeek(x: Float, y: Float) {
        if (holdSeekActive) return
        if (isTvMode || ytMiniCollapsed || isWebViewPlayback) return
        val player = exoPlayer ?: return
        // Long-press in the top/bottom bar dead zones is reserved for the bars themselves.
        val deadZoneTop = topBar.height + 10
        val deadZoneBottom = gestureDeadZoneBottom()
        if (y < deadZoneTop || y > deadZoneBottom) return
        // Only the RIGHT half starts the hold-seek (left half keeps double-tap quick seek, and
        // a quick stationary long-press here previously did nothing).
        if (x < gestureOverlay.width * 0.5f) return
        // Need actual playback to fast-forward — nothing happens while paused.
        if (!player.isPlaying) return
        // The finger is still and the hold engaged — undo any volume already nudged by the
        // gesture-detector's micro-jitter that sneaked in before our shorter timer fired
        // (the "hold-seek bentrok sama swipe volume" bug). Restores OUR accumulated float to the
        // value captured at touch-down and syncs the stream volume back to it.
        if (abs(volumeFloat - holdSeekDownVolumeFloat) > 0.01f) {
            volumeFloat = holdSeekDownVolumeFloat
            val restoreVol = volumeFloat.toInt().coerceIn(0, maxVolume)
            if (restoreVol != currentVolume) {
                currentVolume = restoreVol
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
            }
        }
        holdSeekActive = true
        holdSeekStartMs = SystemClock.uptimeMillis()
        holdSeekRampLevel = 2
        holdSeekOriginalParams = player.playbackParameters
        player.setPlaybackParameters(PlaybackParameters(holdSeekSpeed, player.playbackParameters.pitch))
        isGestureActive = true
        gestureType = 6
        Log.d(TAG, "hold-seek ON (pos=${player.currentPosition}ms) speed=$holdSeekSpeed buffered=${player.bufferedPosition - player.currentPosition}ms")
        seekText.text = "x${holdSeekSpeed.toInt()} ⏩"
        showSeekIndicator(true, seekText.text.toString())
        updateHoldSeekTime(player)
        applyHoldLowTrack()
        holdSeekHandler.removeCallbacks(holdSeekPosRunnable)
        holdSeekHandler.post(holdSeekPosRunnable)
    }

    /** Snap the video down to a <=480p rendition while holding so 2x decode stays smooth on
     *  mid-range phones. Adaptive streams only (groups with a single format, i.e. progressive
     *  MP4/TS, are left alone). */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyHoldLowTrack() {
        val ts = (exoPlayer?.trackSelector) as? DefaultTrackSelector ?: return
        val mapped = ts.currentMappedTrackInfo ?: return
        var videoRendererIndex = -1
        for (r in 0 until mapped.rendererCount) {
            if (mapped.getRendererType(r) == C.TRACK_TYPE_VIDEO) {
                videoRendererIndex = r
                break
            }
        }
        if (videoRendererIndex < 0) return
        val groups = mapped.getTrackGroups(videoRendererIndex)
        if (groups.length == 0 || groups.get(0).length <= 1) return

        holdVideoRendererIndex = videoRendererIndex
        // Snapshot the CURRENT selection for this group (user's gear choice or null = adaptive
        // auto) so releasing puts back exactly what was there.
        holdVideoPriorOverride = ts.parameters.getSelectionOverride(videoRendererIndex, groups)

        val group = groups.get(0)
        var pick = -1
        var pickHeight = Int.MAX_VALUE
        var under480 = -1
        var under480H = 0
        for (i in 0 until group.length) {
            val h = group.getFormat(i).height
            if (h in 1..480 && h > under480H) {
                under480 = i
                under480H = h
            }
            if (h in 1 until pickHeight) {
                pickHeight = h
                pick = i
            }
        }
        if (under480 >= 0) pick = under480
        if (pick < 0) {
            holdVideoRendererIndex = -1
            holdVideoPriorOverride = null
            return
        }
        try {
            ts.parameters = ts.parameters.buildUpon()
                .setSelectionOverride(videoRendererIndex, groups, DefaultTrackSelector.SelectionOverride(0, pick))
                .build()
            Log.d(TAG, "hold-seek track downgraded to ${group.getFormat(pick).height}p")
        } catch (e: Exception) {
            Log.e(TAG, "applyHoldLowTrack FAILED " + e.javaClass.simpleName + ": " + e.message)
            holdVideoRendererIndex = -1
            holdVideoPriorOverride = null
        }
    }

    /** Put back the video track selection from before the hold (restores a gear-locked format or
     *  re-enables adaptive auto). */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun restoreHoldTrack() {
        if (holdVideoRendererIndex < 0) return
        try {
            val ts = (exoPlayer?.trackSelector) as? DefaultTrackSelector
            if (ts != null) {
                val mapped = ts.currentMappedTrackInfo
                if (mapped != null && holdVideoRendererIndex < mapped.rendererCount) {
                    val groups = mapped.getTrackGroups(holdVideoRendererIndex)
                    val prior = holdVideoPriorOverride
                    val b = ts.parameters.buildUpon()
                    if (prior != null) b.setSelectionOverride(holdVideoRendererIndex, groups, prior)
                    else b.clearSelectionOverride(holdVideoRendererIndex, groups)
                    ts.parameters = b.build()
                    Log.d(TAG, "hold-seek track restored prior=${prior != null}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreHoldTrack FAILED " + e.javaClass.simpleName + ": " + e.message)
        }
        holdVideoRendererIndex = -1
        holdVideoPriorOverride = null
    }

    private fun cancelPendingHold() {
        holdSeekPending = false
        holdSeekHandler.removeCallbacks(holdSeekFireRunnable)
    }

    /** Back to the speed the video had before the hold (usually 1x). No seek is applied — the
     *  video simply continues at normal speed from where the fast-forward left it, exactly like
     *  Telegram/YouTube. */
    private fun restoreHoldSeekSpeed() {
        holdSeekHandler.removeCallbacks(holdSeekPosRunnable)
        holdSeekStartMs = 0L
        holdSeekRampLevel = 2
        val player = exoPlayer
        if (player != null) {
            val params = holdSeekOriginalParams ?: PlaybackParameters(1f, 1f)
            player.setPlaybackParameters(params)
            Log.d(TAG, "hold-seek OFF pos=${player.currentPosition}ms restoring speed=${params.speed}")
        }
        holdSeekOriginalParams = null
        restoreHoldTrack()
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

    /** Bind a media3 MediaSession to the current ExoPlayer. Exposes playback
     *  commands to the platform: PiP play/pause button, system media (volume panel /
     *  quick settings / lock screen), headset buttons, and the active state needed to keep
     *  audio alive while the screen is off/locked. */
    private fun rebindMediaSession(player: Player) {
        releaseMediaSession()
        try {
            mediaSession = MediaSession.Builder(this, player).build()
            Log.d(TAG, "rebindMediaSession built ok session=$mediaSession")
        } catch (e: Exception) {
            Log.e(TAG, "rebindMediaSession FAILED " + e.javaClass.simpleName + ": " + e.message, e)
        }
    }

    private fun releaseMediaSession() {
        Log.d(TAG, "releaseMediaSession releasing existed=${mediaSession != null}")
        mediaSession?.release()
        mediaSession = null
    }

    /** Wire the app's own PiP RemoteActions (com.weebflix.app.PIP_PLAY / PIP_PAUSE, created in
     *  enterPipMode) to the current player. On Android 10+ the system may surface these custom
     *  PiP buttons to the user. */
    private fun registerPipActionReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction("com.weebflix.app.PIP_PLAY")
            addAction("com.weebflix.app.PIP_PAUSE")
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                val player = exoPlayer ?: return
                when (intent?.action) {
                    "com.weebflix.app.PIP_PLAY" -> {
                        Log.d(TAG, "PiP action: play")
                        player.play()
                        player.playWhenReady = true
                    }
                    "com.weebflix.app.PIP_PAUSE" -> {
                        Log.d(TAG, "PiP action: pause")
                        player.pause()
                        player.playWhenReady = false
                    }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        pipActionReceiver = receiver
    }

    private var isSystemBarsHidden = false

    private fun toggleFullscreen() {
        isSystemBarsHidden = !isSystemBarsHidden
        if (isSystemBarsHidden) {
            if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
                ytFullscreen = true
                if (!isTvMode) {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
            btnFullscreen.setImageResource(R.drawable.ic_player_fullscreen_exit)
            btnWvFullscreen.setImageResource(R.drawable.ic_player_fullscreen_exit)
            if (isWebViewPlayback) {
                webView?.evaluateJavascript(
                    """;(function() {
                        var v = document.getElementById('video');
                        if (v && v.webkitSupportsFullscreen && !v.webkitDisplayingFullscreen) {
                            v.webkitEnterFullscreen();
                        }
                    })();""".trimIndent(), null
                )
            }
        } else {
            resetVideoZoom()
            if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
                ytFullscreen = false
                if (!isTvMode) {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            btnFullscreen.setImageResource(R.drawable.ic_player_fullscreen)
            btnWvFullscreen.setImageResource(R.drawable.ic_player_fullscreen)
            if (isWebViewPlayback) {
                webView?.evaluateJavascript(
                    """;(function() {
                        var v = document.getElementById('video');
                        if (v && v.webkitSupportsFullscreen && v.webkitDisplayingFullscreen) {
                            v.webkitExitFullscreen();
                        }
                    })();""".trimIndent(), null
                )
            }
        }
        if (isWebViewPlayback) {
            if (isSystemBarsHidden) {
                wvHideControls()
            } else {
                wvShowControls()
            }
        } else {
            showControls()
            scheduleAutoHide()
        }
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
        updateYtFullscreenFeedVisibility()
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
        zoomIndicator.visibility = View.GONE
        updateYtFullscreenFeedVisibility()
    }

    /** The GoTube-style fullscreen queue is shown ONLY for YouTube videos while the player is in
     *  fullscreen AND the controls are visible — tapping the screen to show the bar/seekbar also
     *  reveals the queue, hiding them hides it (like a mini-queue that doesn't block the video). */
    private fun updateYtFullscreenFeedVisibility() {
        if (!::ytFullscreenPanel.isInitialized) return
        val show = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID &&
            ytFullscreen && controlsVisible && !ytMiniCollapsed
        ytFullscreenPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (show) syncYtFullscreenFeed()
    }

    /** The related feed adapter and the fullscreen queue share one backing list; after any append
     *  (or clear) on the related list this refresh keeps the queue count/tems in step. */
    private fun syncYtFullscreenFeed() {
        if (!::ytFullscreenAdapter.isInitialized) return
        ytFullscreenAdapter.notifyDataSetChanged()
        ytFsFeedHeader.text = getString(
            com.weebflix.app.R.string.yt_fullscreen_feed_title,
            ytRelatedItems.size
        )
    }

    // Restore the custom ExoPlayer UI (playerView + topBar/bottomBar/center controls).
    // Required when switching from WebView playback (which hides these views) back to
    // ExoPlayer — e.g. OppaDrama FileLions/Hydrax after TurboVIP ran in the WebView.
    private fun showExoPlayerUi() {
        resetVideoZoom()
        isWebViewPlayback = false
        webViewPlayerControls.visibility = View.GONE
        webView?.stopLoading()
        webView?.evaluateJavascript(
            "(function(){var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){try{vs[i].pause();}catch(e){}}});",
            null
        )
        webView?.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        gestureOverlay.visibility = View.VISIBLE
        if (ytMiniCollapsed) {
            // Mini player is active: never show the big controls over the home feed.
            hideControls()
            return
        }
        showControls()
        scheduleAutoHide()
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
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
        seekTimeText.visibility = View.GONE
        seekIcon.setImageResource(if (forward) R.drawable.ic_player_skip_forward else R.drawable.ic_player_skip_backward)
    }

    private fun fmtMs(ms: Long): String {
        if (ms <= 0L || ms == Long.MIN_VALUE) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    private fun updateHoldSeekTime(player: androidx.media3.common.Player) {
        val time = fmtMs(player.currentPosition)
        val dur = player.duration
        val total = if (dur in 1..Long.MAX_VALUE) fmtMs(dur) else ""
        seekTimeText.text = if (total.isEmpty()) time else "$time / $total"
        seekTimeText.visibility = View.VISIBLE
    }

    private fun hideSeekIndicator() { seekIndicator.visibility = View.GONE }

    // ===== Skip Opening / Outro =====

    private fun checkSkipButtonsVisibility() {
        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
            btnSkipOpening.visibility = View.GONE
            btnSkipOutro.visibility = View.GONE
            return
        }
        val player = exoPlayer ?: return
        val duration = player.duration
        if (duration <= 0) return
        val currentMs = player.currentPosition
        val totalSec = duration / 1000f
        val currentSec = currentMs / 1000f

        val earlyEnd = minOf(120f, totalSec * 0.12f).coerceAtLeast(60f)
        val midStart = 210f
        val midEnd = minOf(330f, totalSec * 0.30f).coerceAtLeast(240f)
        val inEarly = currentSec < earlyEnd
        val inMid = totalSec >= 660f && currentSec >= midStart && currentSec < midEnd
        val showIntro = (inEarly || inMid) && controlsVisible
        if (showIntro) {
            activeSkipOpeningEndMs = ((if (inMid) midEnd else earlyEnd) * 1000).toLong()
        }
        btnSkipOpening.visibility = if (showIntro) View.VISIBLE else View.GONE

        val outroWindow = minOf(120f, totalSec * 0.08f).coerceAtLeast(45f)
        val dynamicOutroStart = (totalSec - outroWindow).coerceAtLeast(0f)
        btnSkipOutro.visibility = if (currentSec >= dynamicOutroStart && currentSec < totalSec && controlsVisible && nextEpisodeUrl.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // ===== Auto-play =====

    private fun checkAutoPlay() {
        val player = exoPlayer ?: return
        val duration = player.duration
        if (duration <= 0) return
        val timeRemaining = (duration - player.currentPosition) / 1000f
        val isYt = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID
        val hasNext = if (isYt) ytUpNext != null else nextEpisodeUrl.isNotEmpty()
        if (timeRemaining <= 10f && hasNext && !autoPlayActive) {
            startAutoPlayCountdown()
        }
    }

    private fun startAutoPlayCountdown() {
        autoPlayActive = true
        autoPlayCountdown = 10
        autoPlayOverlay.visibility = View.VISIBLE
        tvAutoPlayTitle.text = if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
            ytUpNext?.title?.takeIf { it.isNotEmpty() } ?: "Video Selanjutnya"
        } else {
            nextEpisodeTitle.ifEmpty { "Episode Selanjutnya" }
        }
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
        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
            val next = ytUpNext
            if (next != null) {
                playYouTubeByVideo(next)
            } else {
                cancelAutoPlay()
            }
            return
        }
        if (nextEpisodeUrl.isNotEmpty()) {
            val nextEpNum = Regex("""(\d+)""").find(nextEpisodeTitle)?.groupValues?.getOrElse(1) { "" } ?: ""
            val savedNextUrl = nextEpisodeUrl
            val savedNextTitle = nextEpisodeTitle
            // Hand the pre-fetched next-next episode along so the new activity already knows
            // its own "next" (the chain prefetch) instead of waiting for a fresh navigation fetch.
            val chainUrl = chainedNextEpisodeUrl
            val chainTitle = chainedNextEpisodeTitle
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", savedNextUrl)
                putExtra("title", savedNextTitle)
                putExtra("episodeNumber", nextEpNum)
                putExtra("animeTitle", animeTitle)
                putExtra("imageUrl", imageUrl)
                putExtra("animeUrl", animeUrl)
                putExtra("providerId", activeProviderId)
                putExtra("nextEpisodeUrl", chainUrl)
                putExtra("nextEpisodeTitle", chainTitle)
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, getString(R.string.next_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchNextEpisodeNavForChain() {
        if (nextEpisodeUrl.isEmpty()) return
        val url = nextEpisodeUrl
        lifecycleScope.launch {
            try {
                val nextNav = com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).getEpisodeNavigation(url)
                if (!isFinishing && nextNav.nextEpisodeUrl.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing) {
                            // Keep the immediate next episode in nextEpisodeUrl (used by the
                            // auto-play / next button). The next-next episode is stored separately
                            // and handed to the next activity so it knows its next immediately.
                            chainedNextEpisodeUrl = nextNav.nextEpisodeUrl
                            chainedNextEpisodeTitle = nextNav.nextEpisodeTitle
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ===== Navigation =====

    private fun fetchEpisodeNavigation() {
        lifecycleScope.launch {
            try {
                val nav = com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).getEpisodeNavigation(episodeUrl)
                if (!isFinishing && nav.nextEpisodeUrl.isNotEmpty() && nextEpisodeUrl.isEmpty()) {
                    nextEpisodeUrl = nav.nextEpisodeUrl
                    nextEpisodeTitle = nav.nextEpisodeTitle
                }
                if (nextEpisodeUrl.isNotEmpty()) {
                    fetchNextEpisodeNavForChain()
                }
                withContext(Dispatchers.Main) {
                    if (!isFinishing) {
                        updateEpisodeNavButtons()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun updateEpisodeNavButtons() {
        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
            btnPrevEpisodeNav.visibility = View.GONE
            btnNextEpisodeNav.visibility = View.GONE
            return
        }
        btnPrevEpisodeNav.visibility = if (animeUrl.isNotEmpty()) View.VISIBLE else View.GONE
        btnNextEpisodeNav.visibility = if (nextEpisodeUrl.isNotEmpty()) View.VISIBLE else View.GONE
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
        drakorDlFallbackTried = false
        lifecycleScope.launch {
            try {
                servers = com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).getEpisodeServers(episodeUrl)
                if (!isFinishing) {
                    if (servers.isNotEmpty()) {
                        val startIndex = initialServerIndex()
                        currentServerIndex = startIndex
                        updateServerUI()
                        loadServer(startIndex)
                    } else {
                        showError(getString(R.string.no_servers))
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing) showError("Error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        loadingPlayer.visibility = View.GONE
        tvLoadingProgress.visibility = View.GONE
        tvLoadingHint.visibility = View.GONE
        tvError.visibility = View.VISIBLE
        tvError.text = message
    }

    /**
     * TV auto-selection: prefer a server that plays via ExoPlayer (D-pad friendly) over a
     * WebView-only embed (Mega/OK.ru/Rumble/archive.org) which needs on-screen JS controls.
     */
    private fun initialServerIndex(): Int {
        if (servers.isEmpty()) return 0
        if (!isTvMode) return 0
        val preferred = servers.indexOfFirst { isTvExoPlayerPreferred(it) }
        if (preferred >= 0) {
            Log.d(TAG, "TV mode: auto-selected ExoPlayer-friendly server index $preferred (${servers[preferred].name})")
            return preferred
        }
        return 0
    }

    private fun isTvExoPlayerPreferred(server: VideoServer): Boolean {
        if (server.dataType == "dl" || server.dataType == "p2p") return true
        val v = server.videoUrl.lowercase()
        val u = server.url.lowercase()
        val n = server.name.lowercase()
        if (v.startsWith("hydrax://")) return true
        val directSuffixes = listOf(".mp4", ".m3u8", ".mpd", ".mkv", ".webm", ".m4v", "googlevideo.com")
        if (directSuffixes.any { v.contains(it) || u.contains(it) }) return true
        if (v.contains("filedon.co") || u.contains("filedon.co")) return true
        if (v.contains("anichin.stream") || u.contains("anichin.stream")) return true
        if (v.contains("minochinos.com") || u.contains("minochinos.com")) return true
        if (v.contains("filelions") || u.contains("filelions")) return true
        if (v.contains("wibufile") || u.contains("wibufile")) return true
        if ((v.contains("abyssplayer.com") || u.contains("abyssplayer.com")) &&
            server.name.contains("Hydrax", ignoreCase = true)) return true
        if (n.contains("blogspot") || n.contains("wibufile")) return true
        if (n.contains("vip") && n.contains("streaming")) return true
        if (n.contains("filelions") || n.contains("hydrax")) return true
        return false
    }

    private fun resetDlProgress() {
        dlProgressTotal = 0L
        dlProgressLoaded = 0L
        lastShownPct = -1
        dlTotalFetched = false
        lastProgressUiTime = 0L
    }

    private fun fetchDlTotalAsync() {
        val target = getActiveVideoUrl()
        if (target.isEmpty()) return
        dlTotalFetched = true
        lifecycleScope.launch {
            try {
                val ok = getOkHttpClient(cacheDir)
                val total = withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url(target).method("HEAD", null).build()
                    ok.newCall(req).execute().use { resp ->
                        resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    }
                }
                if (total > 0) {
                    dlProgressTotal = total
                    runOnUiThread { updateLoadingProgress() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchDlTotal failed: ${e.message}")
            }
        }
    }

    private fun getActiveVideoUrl(): String {
        val cached = resolvedUrlCache[currentServerIndex]
        if (!cached.isNullOrEmpty()) return cached
        return if (currentServerIndex in servers.indices) servers[currentServerIndex].videoUrl else ""
    }

    private fun updateLoadingProgress() {
        val total = dlProgressTotal
        val loaded = dlProgressLoaded
        if (total <= 0) return
        val pct = ((loaded * 100) / total).toInt().coerceIn(0, 100)
        if (pct != lastShownPct) {
            lastShownPct = pct
            tvLoadingProgress.text = "Preparing $pct% (${formatMb(loaded)} / ${formatMb(total)})"
        }
        if (dlTrackingActive && loadingPlayer.visibility == View.VISIBLE) {
            tvLoadingProgress.visibility = View.VISIBLE
            tvLoadingHint.visibility = View.VISIBLE
        }
    }

    private fun formatMb(bytes: Long): String {
        return String.format(java.util.Locale.US, "%.1fMB", bytes / 1048576.0)
    }

    private fun scheduleAutoFail(serverName: String) {
        pendingAutoFailRunnable?.let { tvError.removeCallbacks(it) }
        val nextIndex = currentServerIndex + 1
        if (nextIndex < servers.size) {
            tvError.visibility = View.VISIBLE
            tvError.text = getString(R.string.server_failed, serverName)
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(androidx.core.content.ContextCompat.getColor(this@PlayerActivity, R.color.yt_popup_bg))
                cornerRadius = 24f
            }
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.select_server)
            setTextColor(androidx.core.content.ContextCompat.getColor(this@PlayerActivity, R.color.netflix_red))
            textSize = 16f
            setPadding(8, 8, 8, 20)
        }
        container.addView(titleView)

        servers.forEachIndexed { index, server ->
            val item = TextView(this).apply {
                text = server.name
                setTextColor(if (index == currentServerIndex) androidx.core.content.ContextCompat.getColor(this@PlayerActivity, R.color.netflix_red) else 0xFFFFFFFF.toInt())
                textSize = 14f
                setPadding(16, 14, 16, 14)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (index == currentServerIndex) androidx.core.content.ContextCompat.getColor(this@PlayerActivity, R.color.yt_server_active_tint) else 0x00000000)
                    cornerRadius = 12f
                }
                isClickable = true
                isFocusable = true
            }
            item.setOnClickListener {
                popupWindow?.dismiss()
                currentServerIndex = index
                loadServer(index)
                updateServerUI()
            }
            container.addView(item)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            isVerticalScrollBarEnabled = true
        }

        val maxPopupHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        val wrapper = FrameLayout(this).apply {
            addView(scrollView)
        }

        popupWindow = android.widget.PopupWindow(
            wrapper,
            (resources.displayMetrics.widthPixels * 0.55f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f

            wrapper.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = wrapper.measuredHeight
            val location = IntArray(2)
            tvServerName.getLocationOnScreen(location)
            val yOff = -(popupHeight + tvServerName.height + 16)
            showAsDropDown(tvServerName, 0, yOff)

            wrapper.post {
                val h = wrapper.height
                if (h > maxPopupHeight) {
                    wrapper.layoutParams = wrapper.layoutParams.apply { height = maxPopupHeight }
                }
            }
        }
    }

    private var popupWindow: android.widget.PopupWindow? = null

    private fun loadServer(index: Int) {
        syncByteRetryCount = 0
        if (index in servers.indices) playServer(servers[index])
    }

    private fun playYouTubeVideo(videoId: String, seekMs: Long = startPositionMs) {
        currentYtVideoId = videoId
        updateYtNavButtons()
        loadingPlayer.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        tvLoadingProgress.visibility = View.GONE
        tvLoadingHint.visibility = View.GONE
        tvAnimeTitle.text = animeTitle.ifEmpty { "YouTube" }
        tvServerName.visibility = View.GONE
        btnPrevEpisodeNav.visibility = View.GONE
        btnNextEpisodeNav.visibility = View.GONE
        currentChannelId = ""
        currentChannelName = ""
        ytLikeCount.visibility = View.GONE
        setLikeUi(false)
        setDislikeUi(false)
        setSubscribeUi(false)
        resetYtComments()
        lifecycleScope.launch {
            val resolved = try {
                withContext(Dispatchers.IO) { com.weebflix.app.data.scraper.YouTubeResolver.resolve(videoId) }
            } catch (e: Exception) {
                Log.e(TAG, "YouTube resolve error: ${e.message}")
                null
            }
            if (isFinishing) return@launch
            if (resolved == null || resolved.isEmpty) {
                val msg = resolved?.blockReason?.takeIf { it.isNotEmpty() }?.let {
                    "Video diblokir YouTube (butuh login).\n$it"
                } ?: "Gagal memuat video. Coba lagi nanti."
                showError(msg)
            } else {
                // Personalize the home feed: remember the channel + title keywords of what plays.
                com.weebflix.app.data.model.YouTubeFeedPrefs.recordWatched(resolved.title, resolved.author)
                tvAnimeTitle.text = resolved.title
                if (ytMiniCollapsed) miniTitle.text = resolved.title
                val sub = buildString {
                    if (resolved.author.isNotEmpty()) append(resolved.author)
                    if (resolved.views.isNotEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append(YouTubeFormat.compactViewCount(resolved.views))
                    }
                    val published = YouTubeFormat.relativeIndonesian(resolved.published)
                    if (published.isNotEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append(published)
                    }
                }
                tvEpisodeTitle.text = sub.ifEmpty { "YouTube" }
                ytDetailTitle.text = resolved.title
                ytDetailMeta.text = sub.ifEmpty { "YouTube" }
                initExoPlayerYouTube(resolved, seekMs)
                if (ytRelatedAdapter.isEmpty) {
                    ytLoadingRelated = false
                    ytRelatedEnded = false
                    loadMoreRelated()
                }
                loadMoreComments()
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initExoPlayerYouTube(resolved: com.weebflix.app.data.scraper.ResolvedYouTube, seekMs: Long = 0L) {
        val video = com.weebflix.app.data.scraper.YouTubeResolver.pickVideo(resolved.videoFormats)
        val audio = com.weebflix.app.data.scraper.YouTubeResolver.pickAudio(resolved.audioFormats)
        if (video == null || audio == null) {
            showError("Stream video/audio tidak tersedia")
            return
        }
        Log.d(TAG, "YouTube streams: video=${video.height}p ${video.mimeType} bitrate=${video.bitrate} | audio=${audio.mimeType} bitrate=${audio.bitrate} lang=${audio.language} orig=${audio.isOriginalAudio} def=${audio.isDefaultAudio}")
        pendingYtSeekMs = seekMs

        showExoPlayerUi()
        releaseMediaSession()
        exoPlayer?.release()
        resetDlProgress()

        val okHttpClient = getOkHttpClient(cacheDir)
        val upstreamFactory = object : androidx.media3.datasource.DataSource.Factory {
            override fun createDataSource(): androidx.media3.datasource.DataSource {
                val ds = OkHttpDataSource.Factory(okHttpClient)
                    .setDefaultRequestProperties(mapOf(
                        "Referer" to "https://www.youtube.com/",
                        "Origin" to "https://www.youtube.com"
                    ))
                    .createDataSource()
                (ds as? androidx.media3.datasource.BaseDataSource)?.addTransferListener(progressTransferListener)
                return ds
            }
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 15_000, 10_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)
        val maxDefRes = ProviderConfig.getYtDefaultResolution()
        val params = trackSelector.buildUponParameters()
            .setPreferredVideoMimeTypes("video/avc")
            .apply {
                if (maxDefRes > 0) setMaxVideoSize(1920, maxDefRes)
            }
            .build()
        trackSelector.parameters = params
        ytTrackSelector = trackSelector
        ytResolutionOptions = resolved.videoFormats
            .map { it.height }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        ytCurrentResolution = 0
        btnQuality.visibility = View.VISIBLE

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(upstreamFactory)

        // ABR (YouTube-style): build an on-demand DASH manifest from all adaptive formats so
        // ExoPlayer's DefaultTrackSelector switches resolutions with the available bandwidth
        // (starts low on slow links, climbs automatically, drops when throttled). Falls back
        // to a single fixed format (MergingMediaSource) when formats lack byte ranges.
        val dashManifest = com.weebflix.app.data.scraper.YouTubeDashManifest.build(resolved)
        val merged: androidx.media3.exoplayer.source.MediaSource = if (dashManifest != null) {
            Log.d(TAG, "YouTube DASH ABR: video=${resolved.videoFormats.map { "${it.height}p" }.distinct().sorted()}")
            val dataUri = "data:application/dash+xml;base64," +
                android.util.Base64.encodeToString(dashManifest.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val dashFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, upstreamFactory)
            androidx.media3.exoplayer.dash.DashMediaSource.Factory(dashFactory)
                .createMediaSource(MediaItem.fromUri(dataUri))
        } else {
            Log.d(TAG, "YouTube DASH unavailable (no byte ranges), falling back to fixed format")
            val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(video.url))
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audio.url))
            androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
            .also { player ->
                if (ytMiniCollapsed) {
                    playerView.player = null
                    miniPlayerView.player = player
                } else {
                    playerView.player = player
                }
                rebindMediaSession(player)
                player.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        for (g in tracks.groups) {
                            if (g.type != C.TRACK_TYPE_VIDEO) continue
                            for (i in 0 until g.length) {
                                if (g.isTrackSelected(i)) {
                                    val f = g.getTrackFormat(i)
                                    Log.d(TAG, "YT ABR selected: ${f.width}x${f.height} codecs=${f.codecs} bitrate=${f.bitrate}")
                                }
                            }
                        }
                    }

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
                                    if (pendingYtSeekMs > 0) {
                                        player.seekTo(pendingYtSeekMs)
                                        pendingYtSeekMs = 0
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    isPlaying = false
                                    btnPlayPause.setImageResource(R.drawable.ic_player_play)
                                    btnCenterPlayPause.setImageResource(R.drawable.ic_player_play)
                                    showControls()
                                }
                                else -> {}
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        runOnUiThread {
                            loadingPlayer.visibility = View.GONE
                            val msg = error.message ?: ""
                            val causeMsg = error.cause?.message ?: ""
                            Log.e(TAG, "YouTube player error: $msg | cause: $causeMsg")
                            if ((msg + causeMsg).contains("403")) {
                                showError("Stream kedaluwarsa. Buka video lagi untuk stream baru.")
                            } else {
                                showError("Gagal memutar video: ${error.cause?.message ?: msg}")
                            }
                        }
                    }
                })
                player.setMediaSource(merged)
                player.prepare()
                player.playWhenReady = true
                progressUpdateHandler.postDelayed(progressUpdateRunnable, 500)
            }
    }

    /** Plays a video tapped from the related list (same activity, no re-launch). */
    private fun playYouTubeByVideo(video: YouTubeVideo, recordHistory: Boolean = true) {
        if (video.videoId.isEmpty() || video.videoId == currentYtVideoId) return
        // Persist the video we're leaving before its fields get overwritten below.
        saveWatchHistory()
        if (recordHistory && currentYtVideoId.isNotEmpty()) {
            ytPlayHistory.addLast(
                YouTubeVideo(
                    videoId = currentYtVideoId,
                    title = episodeTitle.ifEmpty { animeTitle },
                    channel = currentChannelName,
                    channelId = currentChannelId,
                    thumbnail = imageUrl
                )
            )
            if (ytPlayHistory.size > 50) ytPlayHistory.removeFirst()
        }
        cancelAutoPlay()
        pendingYtSeekMs = 0
        animeTitle = video.title
        episodeUrl = video.url
        imageUrl = video.thumbnail
        animeUrl = video.url
        episodeTitle = video.title
        episodeNumber = "1"
        ytUpNext = null
        ytRelatedContinuation = ""
        ytLoadingRelated = false
        ytRelatedEnded = false
        ytRelatedAdapter.clear()
        syncYtFullscreenFeed()
        ytFeedScroll.scrollTo(0, 0)
        playYouTubeVideo(video.videoId, 0L)
    }

    /** Skip-previous: replay the last video from this session's playback history. */
    private fun playYtPrevVideo() {
        val prev = ytPlayHistory.removeLastOrNull()
        if (prev == null || prev.videoId.isEmpty()) {
            Toast.makeText(this, "Tidak ada video sebelumnya", Toast.LENGTH_SHORT).show()
            return
        }
        playYouTubeByVideo(prev, recordHistory = false)
    }

    /** Skip-next: play the up-next video (same as the auto-play target). */
    private fun playYtNextVideo() {
        val next = ytUpNext
        if (next == null || next.videoId.isEmpty()) {
            Toast.makeText(this, "Video berikutnya tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        playYouTubeByVideo(next)
    }

    private fun updateYtNavButtons() {
        val isYt = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID
        if (!isYt) {
            btnYtPrev.visibility = View.GONE
            btnYtNext.visibility = View.GONE
            return
        }
        btnYtPrev.visibility = if (ytPlayHistory.isNotEmpty()) View.VISIBLE else View.GONE
        btnYtNext.visibility = if (ytUpNext != null) View.VISIBLE else View.GONE
    }

    private fun openChannelFromVideo(video: YouTubeVideo) {
        openChannel(video.channelId, video.channel, video.channelThumb)
    }

    private fun openChannel(channelId: String, channelName: String, channelThumb: String) {
        if (channelId.isEmpty()) return
        val intent = android.content.Intent(this, YouTubeChannelActivity::class.java).apply {
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_ID, channelId)
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_NAME, channelName)
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_THUMB, channelThumb)
        }
        startActivity(intent)
    }

    private fun loadMoreRelated() {
        if (ytLoadingRelated || ytRelatedEnded) return
        ytLoadingRelated = true
        val job = lifecycleScope.launch {
            val c = ytRelatedContinuation
            val page = try {
                withContext(Dispatchers.IO) {
                    if (c.isEmpty()) ytScraper.watchNextBundle(currentYtVideoId)
                    else ytScraper.watchNextBundleFromContinuation(c)
                }
            } catch (e: Exception) {
                com.weebflix.app.data.scraper.WatchNextBundle()
            }
            if (c.isEmpty()) {
                // The first-page bundle carries the owner renderer + like count AND the first
                // page of comments (one `next` + one ANDROID_VR continuation), so we never fire
                // a second concurrent `next` for comments (innertube rate-limit / IP flag).
                ytFirstBundleLoaded = true
                if (page.channelId.isNotEmpty()) {
                    currentChannelId = page.channelId
                    currentChannelName = page.channelName
                    btnYtSubscribe.contentDescription =
                        "Subscribe " + currentChannelName.ifEmpty { currentChannelId }
                }
                if (page.likeCount.isNotEmpty()) {
                    ytLikeCount.text = page.likeCount
                    ytLikeCount.visibility = View.VISIBLE
                }
                syncYtEngagement()
                ytCommentContinuation = page.commentContinuation
                val comFresh = page.comments.filter { it.author.isNotEmpty() && it.text.isNotEmpty() }
                if (comFresh.isNotEmpty()) {
                    ytCommentAdapter.submitList(comFresh)
                    ytCommentHeader.visibility = View.VISIBLE
                    updateYtCommentsUi()
                } else if (page.commentContinuation.isEmpty()) {
                    ytCommentsEnded = true
                }
            }
            val fresh = page.videos.filter { it.videoId.isNotEmpty() && it.videoId != currentYtVideoId }
            if (fresh.isNotEmpty()) {
                ytRelatedAdapter.append(fresh, endOfFeed = false)
                ytRelatedContinuation = page.continuation
            } else if (page.continuation.isEmpty()) {
                // Related endpoint failed or returned nothing -> fall back to the generic
                // endless feed so the list is never left stranded.
                val fallback = try {
                    withContext(Dispatchers.IO) { ytScraper.nextFeedPage() }
                } catch (e: Exception) {
                    emptyList()
                }
                val freshFb = fallback.filter { it.videoId.isNotEmpty() && it.videoId != currentYtVideoId }
                if (freshFb.isNotEmpty()) {
                    ytRelatedAdapter.append(freshFb, endOfFeed = false)
                } else {
                    ytRelatedEnded = true
                    ytRelatedAdapter.setLoading()
                }
            }
            ytLoadingRelated = false
            refreshYtUpNext()
            maybeAutoFillYtFeed()
            syncYtFullscreenFeed()
        }
        ytRelatedList.post {
            if (job.isActive && !isFinishing) ytRelatedAdapter.setLoading()
        }
    }

    private fun refreshYtUpNext() {
        ytUpNext = ytRelatedAdapter.peekFirst()
        updateYtNavButtons()
    }

    private fun showYtResolutionDialog() {
        val values = mutableListOf<Int>()
        val labels = mutableListOf<String>()
        labels.add("Auto")
        values.add(0)
        for (h in ytResolutionOptions) {
            labels.add("${h}p")
            values.add(h)
        }
        if (values.size <= 1) {
            Toast.makeText(this, "Resolusi manual tidak tersedia untuk video ini", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = values.indexOf(ytCurrentResolution).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Resolusi video")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, which ->
                applyYtResolution(values[which])
                d.dismiss()
            }
            .show()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyYtResolution(height: Int) {
        val ts = ytTrackSelector ?: return
        val defRes = ProviderConfig.getYtDefaultResolution()
        if (height <= 0) {
            ts.parameters = ts.parameters.buildUpon()
                .clearSelectionOverrides()
                .setMaxVideoSize(1920, if (defRes > 0) defRes else 1080)
                .build()
            ytCurrentResolution = 0
            Toast.makeText(this, "Resolusi: Auto", Toast.LENGTH_SHORT).show()
            return
        }
        val mapped = ts.currentMappedTrackInfo
        if (mapped == null) {
            Toast.makeText(this, "Track belum siap, coba beberapa detik lagi", Toast.LENGTH_SHORT).show()
            return
        }
        var videoRendererIndex = -1
        for (r in 0 until mapped.rendererCount) {
            if (mapped.getRendererType(r) == C.TRACK_TYPE_VIDEO) {
                videoRendererIndex = r
                break
            }
        }
        if (videoRendererIndex < 0) {
            Toast.makeText(this, "Track video belum siap", Toast.LENGTH_SHORT).show()
            return
        }
        val groups = mapped.getTrackGroups(videoRendererIndex)
        for (g in 0 until groups.length) {
            val group = groups.get(g)
            for (t in 0 until group.length) {
                if (group.getFormat(t).height == height) {
                    val override = androidx.media3.exoplayer.trackselection.DefaultTrackSelector.SelectionOverride(g, t)
                    ts.parameters = ts.parameters.buildUpon()
                        .setMaxVideoSize(1920, maxOf(height, defRes))
                        .setSelectionOverride(videoRendererIndex, groups, override)
                        .build()
                    ytCurrentResolution = height
                    Toast.makeText(this, "Resolusi: ${height}p", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
        Toast.makeText(this, "Resolusi ${height}p tidak tersedia di video ini", Toast.LENGTH_SHORT).show()
    }

    private fun playServer(server: VideoServer) {
        pendingAutoFailRunnable?.let { tvError.removeCallbacks(it) }
        pendingAutoFailRunnable = null
        turboRetryCount = 0
        dlTrackingActive = false

        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID) {
            val ytId = server.videoUrl.removePrefix("youtube://")
            if (ytId.isNotEmpty() && ytId != server.videoUrl) {
                Log.d(TAG, "YouTube provider detected, playing videoId=$ytId")
                playYouTubeVideo(ytId)
                return
            }
        }

        loadingPlayer.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        seekBar.progress = 0
        tvCurrentTime.text = "00:00"
        tvTotalTime.text = "00:00"

        val serverIndex = servers.indexOf(server).takeIf { it >= 0 } ?: currentServerIndex
        val cachedUrl = resolvedUrlCache[serverIndex]
        if (cachedUrl != null && cachedUrl.isNotEmpty() && !cachedUrl.contains("\\u00") && !cachedUrl.contains("\\=") && !cachedUrl.contains("\\&")) {
            val isRealVideo = cachedUrl.startsWith("hydrax://") ||
                cachedUrl.contains(".mp4") || cachedUrl.contains(".m3u8") ||
                cachedUrl.contains(".mpd") || cachedUrl.contains(".mkv") ||
                cachedUrl.contains(".webm") || cachedUrl.contains(".m4v") ||
                cachedUrl.contains("googlevideo.com") ||
                cachedUrl.contains("videoplayback") || cachedUrl.contains("turboviplay") ||
                cachedUrl.contains("turbovid") || cachedUrl.contains("abysscdn") ||
                cachedUrl.contains("hydrax") || cachedUrl.contains("wibufile") ||
                cachedUrl.contains("minochinos") || cachedUrl.contains("filelions") ||
                cachedUrl.contains("load.my.id") || cachedUrl.contains("uyeshare.cc") ||
                cachedUrl.contains("/1fichier/")
            if (!isRealVideo) {
                Log.w(TAG, "Cached URL is not a video URL, clearing: $cachedUrl")
                resolvedUrlCache.remove(serverIndex)
            } else {
                Log.d(TAG, "Playing cached URL: $cachedUrl")
                loadingPlayer.visibility = View.GONE
                if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID &&
                    !cachedUrl.startsWith("hydrax://") &&
                    !(cachedUrl.contains(".urlset/") || cachedUrl.contains("/hls2/"))) {
                    playVideoViaHtml5WebView(cachedUrl)
                } else {
                    initExoPlayer(cachedUrl)
                }
                return
            }
        }

        if (server.videoUrl.isNotEmpty()) {
            val isDrakorDl = activeProviderId == com.weebflix.app.data.provider.ProviderFactory.DRAKORKITA_ID &&
                server.dataType == "dl"
            dlTrackingActive = isDrakorDl
            val isDirectVideo = isDrakorDl ||
                server.videoUrl.startsWith("hydrax://") ||
                server.videoUrl.contains(".mp4") || server.videoUrl.contains(".m3u8") ||
                server.videoUrl.contains(".mpd") || server.videoUrl.contains(".mkv") ||
                server.videoUrl.contains(".webm") || server.videoUrl.contains(".m4v") ||
                server.videoUrl.contains("googlevideo.com")
            if (isDirectVideo) {
                Log.d(TAG, "Playing resolved URL: ${server.videoUrl}")
                resolvedUrlCache[serverIndex] = server.videoUrl
                if (!isDrakorDl) loadingPlayer.visibility = View.GONE
                if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID &&
                    !server.videoUrl.startsWith("hydrax://")) {
                    playVideoViaHtml5WebView(server.videoUrl)
                } else {
                    initExoPlayer(server.videoUrl)
                }
                return
            }
        }

        val url = server.url
        if (url.isNotEmpty() && (url.contains(".mp4") || url.contains(".m3u8") || url.contains(".mpd") || url.contains(".mkv") || url.contains(".webm") || url.contains(".m4v") || url.contains("googlevideo.com"))) {
            Log.d(TAG, "Playing direct video URL: $url")
            resolvedUrlCache[serverIndex] = url
            loadingPlayer.visibility = View.GONE
            if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID) {
                playVideoViaHtml5WebView(url)
            } else {
                initExoPlayer(url)
            }
            return
        }

        if (isDrakorKitaServer(server)) {
            Log.d(TAG, "DrakorKita server detected: ${server.name} → path-based URL playback (no autoSelectJs)")
            loadingPlayer.visibility = View.GONE
            playDrakorKitaEpisodePage(server)
            return
        }

        if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID) {
            Log.d(TAG, "OppaDrama server detected: ${server.name}, videoUrl=${server.videoUrl}")
            if (server.videoUrl.isNotEmpty()) {
                val isDirect = server.videoUrl.startsWith("hydrax://") ||
                    server.videoUrl.contains(".mp4") || server.videoUrl.contains(".m3u8") || server.videoUrl.contains(".mpd")
                if (isDirect) {
                    resolvedUrlCache[serverIndex] = server.videoUrl
                    loadingPlayer.visibility = View.GONE
                    if (server.videoUrl.startsWith("hydrax://") ||
                        server.videoUrl.contains(".urlset/") || server.videoUrl.contains("/hls2/")) {
                        Log.d(TAG, "OppaDrama direct CDN URL, playing in ExoPlayer: ${server.videoUrl}")
                        initExoPlayer(server.videoUrl)
                    } else {
                        playVideoViaHtml5WebView(server.videoUrl)
                    }
                } else {
                    val isHydrax = server.videoUrl.contains("abyssplayer.com") || server.videoUrl.contains("hydrax") || server.name.contains("Hydrax", ignoreCase = true)
                    val isTurboVip = server.videoUrl.contains("emturbovid.com") || server.name.contains("Turbo", ignoreCase = true)
                    if (isHydrax) {
                        Log.d(TAG, "OppaDrama Hydrax: scraping for encrypted MP4 (hydrax:// → ExoPlayer)")
                        loadingPlayer.visibility = View.VISIBLE
                        lifecycleScope.launch {
                            val videoUrl = try {
                                com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).resolveServerVideoUrl(server, episodeUrl)
                            } catch (e: Exception) {
                                Log.e(TAG, "OppaDrama Hydrax scrape error: ${e.message}")
                                ""
                            }
                            withContext(Dispatchers.Main) {
                                if (!isFinishing && videoUrl.startsWith("hydrax://")) {
                                    Log.d(TAG, "OppaDrama Hydrax resolved to encrypted MP4, playing in ExoPlayer")
                                    loadingPlayer.visibility = View.GONE
                                    resolvedUrlCache[serverIndex] = videoUrl
                                    initExoPlayer(videoUrl)
                                } else {
                                    Log.w(TAG, "OppaDrama Hydrax scrape failed, loading embed in WebView")
                                    loadingPlayer.visibility = View.GONE
                                    playEpisodePageViaWebView(server.videoUrl, server)
                                }
                            }
                        }
                    } else if (isTurboVip) {
                        Log.d(TAG, "OppaDrama TurboVIP: loading episode page (not embed) to preserve referrer/cookies context")
                        loadingPlayer.visibility = View.GONE
                        val autoSelectJs = """
                            (function() {
                                if (window._autoSelectDone) return;
                                window._autoSelectDone = true;
                                var sel = document.querySelector('select.mirror, .mirror select');
                                if (sel && sel.options.length > 0) {
                                    sel.selectedIndex = 0;
                                    sel.dispatchEvent(new Event('change', {bubbles: true}));
                                }
                            })();
                        """.trimIndent()
                        playEpisodePageViaWebView(episodeUrl, server, autoSelectJs)
                    } else {
                        Log.d(TAG, "OppaDrama: scraping video URL from embed: ${server.videoUrl}")
                        loadingPlayer.visibility = View.VISIBLE
                        lifecycleScope.launch {
                            val videoUrl = try {
                                com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).resolveServerVideoUrl(server, episodeUrl)
                            } catch (e: Exception) {
                                Log.e(TAG, "OppaDrama scrape error: ${e.message}")
                                ""
                            }
                            withContext(Dispatchers.Main) {
                                if (!isFinishing && videoUrl.isNotEmpty() && (videoUrl.startsWith("hydrax://") || videoUrl.contains(".mp4") || videoUrl.contains(".m3u8") || videoUrl.contains(".mpd") || videoUrl.contains("googlevideo.com"))) {
                                    Log.d(TAG, "OppaDrama scraped video URL: $videoUrl")
                                    loadingPlayer.visibility = View.GONE
                                    resolvedUrlCache[serverIndex] = videoUrl
                                    if ((activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID) &&
                                        (videoUrl.startsWith("hydrax://") || videoUrl.contains(".urlset/") || videoUrl.contains("/hls2/"))) {
                                        Log.d(TAG, "OppaDrama CDN detected, playing in ExoPlayer: $videoUrl")
                                        initExoPlayer(videoUrl)
                                    } else {
                                        playVideoViaHtml5WebView(videoUrl)
                                    }
                                } else {
                                    Log.w(TAG, "OppaDrama scrape failed, loading episode page in WebView")
                                    loadingPlayer.visibility = View.GONE
                                    val autoSelectMirrorJs = """
                                        (function() {
                                            if (window._autoSelectDone) return;
                                            window._autoSelectDone = true;
                                            var sel = document.querySelector('select.mirror, .mirror select');
                                            if (sel && sel.options.length > 0) {
                                                sel.selectedIndex = 0;
                                                sel.dispatchEvent(new Event('change', {bubbles: true}));
                                            }
                                        })();
                                    """.trimIndent()
                                    playEpisodePageViaWebView(episodeUrl, server, autoSelectMirrorJs)
                                }
                            }
                        }
                    }
                }
            } else {
                Log.d(TAG, "OppaDrama: no videoUrl, loading episode page in WebView")
                loadingPlayer.visibility = View.GONE
                val autoSelectMirrorJs = """
                    (function() {
                        if (window._autoSelectDone) return;
                        window._autoSelectDone = true;
                        var sel = document.querySelector('select.mirror, .mirror select');
                        if (sel && sel.options.length > 0) {
                            sel.selectedIndex = 0;
                            sel.dispatchEvent(new Event('change', {bubbles: true}));
                        }
                    })();
                """.trimIndent()
                playEpisodePageViaWebView(episodeUrl, server, autoSelectMirrorJs)
            }
            return
        }

        if (server.name.contains("Blogspot", ignoreCase = true) || server.url.contains("blogger.com") || server.url.contains("blogspot") || server.url.contains("bp.blogspot.com")) {
            Log.d(TAG, "Blogger server detected, using fast AJAX + XHR path...")
            lifecycleScope.launch {
                val ajaxUrl = com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).resolveServerVideoUrl(server, episodeUrl)
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
                    com.weebflix.app.data.provider.ProviderFactory.getProvider(activeProviderId).resolveServerVideoUrl(server, episodeUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Scraper resolution error: ${e.message}")
                ""
            }
            if (!isFinishing && scraperUrl.isNotEmpty()) {
                Log.d(TAG, "Scraper resolved: $scraperUrl")
                loadingPlayer.visibility = View.GONE
                tvError.visibility = View.GONE
                if (scraperUrl.contains(".mp4") || scraperUrl.contains(".m3u8") || scraperUrl.contains(".mpd") || scraperUrl.contains(".mkv") || scraperUrl.contains(".webm") || scraperUrl.contains(".m4v") || scraperUrl.contains("googlevideo.com")) {
                    resolvedUrlCache[serverIndex] = scraperUrl
                    initExoPlayer(scraperUrl)
                } else if ((activeProviderId == com.weebflix.app.data.provider.ProviderFactory.ANICHIN_ID ||
                    activeProviderId == com.weebflix.app.data.provider.ProviderFactory.SAMEHADAKU_ID) && isWebViewPlayableEmbed(scraperUrl)) {
                    Log.d(TAG, "$activeProviderId: playing embed page directly in WebView: $scraperUrl")
                    loadingPlayer.visibility = View.GONE
                    playEpisodePageViaWebView(scraperUrl, server, skipInjections = true)
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
                                if (resolvedUrl.contains(".mp4") || resolvedUrl.contains(".m3u8") || resolvedUrl.contains(".mpd") || resolvedUrl.contains(".mkv") || resolvedUrl.contains(".webm") || resolvedUrl.contains(".m4v") || resolvedUrl.contains("googlevideo.com") || resolvedUrl.contains("wibufile.com")) {
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

    private fun isWebViewPlayableEmbed(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("dailymotion.com") || lower.contains("archive.org") ||
            lower.contains("mega.nz") || lower.contains("ok.ru") ||
            lower.contains("rumble.com") || lower.contains("anichin-player.web.id") ||
            lower.contains("rubyvidhub") || lower.contains("abyssplayer") ||
            lower.contains("vk.com") || lower.contains("filedon.co")
    }

    private fun rewriteAnichinPlayerPage(url: String): android.webkit.WebResourceResponse? {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@use null
                val html = String(body.bytes(), Charsets.UTF_8)
                var modified = html
                val inject = StringBuilder()

                if (url.contains("abyssplayer")) {
                    val guard = "top.location == self.location && !/^(.+?)\\.abyss\\.to$/"
                    if (modified.contains(guard)) {
                        modified = modified.replace(guard, "false && !/^(.+?)\\.abyss\\.to$/")
                        Log.d(TAG, "ABYSS-REWRITE: abyss.to redirect guard disabled")
                    } else {
                        modified = modified.replace("top.location == self.location", "false")
                    }
                    inject.append(
                        "<script>(function(){try{window.open=function(){return {closed:true,focus:function(){}}};" +
                            "document.write=function(){};" +
                            "setTimeout(function(){var o=document.getElementById('overlay');if(o)o.remove();},600);" +
                            "setTimeout(function(){var o=document.getElementById('overlay');if(o)o.remove();},2500);" +
                            "}catch(e){}})();</script>"
                    )
                } else if (url.contains("rubyvidhub")) {
                    inject.append(
                        "<script>(function(){try{window.adbon=0;window.showADBOverlay=function(){};window.setADBFlag=function(){};" +
                            "var kill=function(){var els=document.querySelectorAll('.a965058,#adbd,.overdiv');" +
                            "for(var i=0;i<els.length;i++){var p=els[i].parentNode;if(p)p.removeChild(els[i]);}};" +
                            "kill();setInterval(kill,1500);}catch(e){}})();</script>"
                    )
                }

                if (inject.isNotEmpty()) {
                    val bodyClose = Regex("(?i)</body>")
                    val htmlClose = Regex("(?i)</html>")
                    if (bodyClose.containsMatchIn(modified)) {
                        modified = bodyClose.replaceFirst(modified, inject.toString() + "</body>")
                    } else if (htmlClose.containsMatchIn(modified)) {
                        modified = htmlClose.replaceFirst(modified, inject.toString() + "</html>")
                    } else {
                        modified = modified + inject.toString()
                    }
                }

                Log.d(TAG, "ABYSS-REWRITE: rewrote ${url.take(60)} -> ${modified.length} bytes (was ${html.length})")
                android.webkit.WebResourceResponse(
                    "text/html", "utf-8",
                    response.code,
                    response.message.ifEmpty { "OK" },
                    mutableMapOf("Access-Control-Allow-Origin" to "*"),
                    modified.toByteArray(Charsets.UTF_8).inputStream()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ABYSS-REWRITE error: ${e.message}")
            null
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
        val isTurboVip = embedUrl.contains("emturbovid") || embedUrl.contains("turbovidhls") || embedUrl.contains("turboviplay")

        if (isTurboVip) {
            Log.d(TAG, "TurboVip detected, using OkHttp extraction")
            val gen = resolveGeneration
            lifecycleScope.launch {
                val m3u8Url = withContext(Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build()
                        val req = okhttp3.Request.Builder().url(embedUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Referer", "https://turbovidhls.com/")
                            .header("Origin", "https://turbovidhls.com")
                            .build()
                        val response = client.newCall(req).execute()
                        val body = response.body?.string() ?: ""
                        response.close()
                        val patterns = listOf(
                            Regex("""https?://[^\s"'<>]+cdn2\.turboviplay\.com[^\s"'<>]+\.m3u8[^\s"'<>]*"""),
                            Regex("""https?://[^\s"'<>]+turboviplay[^\s"'<>]+\.m3u8[^\s"'<>]*"""),
                            Regex("""["']file["']\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)""", RegexOption.IGNORE_CASE),
                            Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
                        )
                        var result = ""
                        for (pattern in patterns) {
                            val match = pattern.find(body)
                            if (match != null) {
                                result = match.groupValues.getOrElse(1) { match.value }
                                if (result.startsWith("http")) break
                            }
                        }
                        result
                    } catch (e: Exception) { "" }
                }
                withContext(Dispatchers.Main) {
                    if (gen != resolveGeneration || isFinishing) return@withContext
                    if (m3u8Url.isNotEmpty()) {
                        resolvedUrlCache[serverIndex] = m3u8Url
                        loadingPlayer.visibility = View.GONE
                        playVideoViaHtml5WebView(m3u8Url)
                    } else {
                        Log.w(TAG, "TurboVip OkHttp failed, falling back to WebView")
                        resolveEmbedUrlViaWebViewFallback(embedUrl, server, serverIndex)
                    }
                }
            }
            return
        }

        resolveEmbedUrlViaWebViewFallback(embedUrl, server, serverIndex)
    }

    private fun resolveEmbedUrlViaWebViewFallback(embedUrl: String, server: VideoServer, serverIndex: Int) {
        ensureWebView()
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
                    if (activeProviderId == com.weebflix.app.data.provider.ProviderFactory.OPPADRAMA_ID) {
                        playVideoViaHtml5WebView(finalUrl)
                    } else {
                        initExoPlayer(finalUrl)
                    }
                } else if (!isFinishing) {
                    scheduleAutoFail(server.name)
                }
            }
        }

        Log.d(TAG, "Loading embed URL in WebView: $embedUrl")
        webView?.stopLoading()
        webView?.loadUrl(embedUrl)

        val isBlogger = embedUrl.contains("blogger.com") || embedUrl.contains("bp.blogspot.com") || embedUrl.contains("blogspot.com")
        val isFiledon = embedUrl.contains("filedon.co")
        val isFileLions = embedUrl.contains("minochinos.com") || embedUrl.contains("filelions")
        val isTurboVip = embedUrl.contains("emturbovid") || embedUrl.contains("turbovidhls") || embedUrl.contains("turboviplay")
        val timeoutMs = when {
            isBlogger -> 20000L
            isFiledon -> 15000L
            isFileLions -> 25000L
            isTurboVip -> 20000L
            else -> 15000L
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
                } else if (isFileLions) {
                    Log.d(TAG, "Trying FileLions-specific JS extraction before timeout...")
                    webView?.evaluateJavascript(extractFileLionsVideoJs(), null)
                    webView?.postDelayed({
                        if (webViewResolving && resolveGeneration == gen) {
                            Log.d(TAG, "FileLions JS extraction failed, enumerating iframes...")
                            webView?.evaluateJavascript("""
                                (function() {
                                    var iframes = document.querySelectorAll('iframe');
                                    var urls = [];
                                    for (var i = 0; i < iframes.length; i++) {
                                        var s = iframes[i].src || iframes[i].getAttribute('src') || '';
                                        if (s && s.indexOf('http') === 0 && s.indexOf('about:blank') === -1) {
                                            urls.push(s);
                                        }
                                    }
                                    return JSON.stringify(urls);
                                })();
                            """.trimIndent()) { iframeJson ->
                                if (webViewResolving && resolveGeneration == gen) {
                                    val urls = try {
                                        org.json.JSONArray(iframeJson?.removeSurrounding("\"") ?: "[]").let { arr ->
                                            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { u -> u.isNotEmpty() } }
                                        }
                                    } catch (_: Exception) { emptyList() }
                                    Log.d(TAG, "FileLions found ${urls.size} iframes: $urls")
                                    val targetIframe = urls.firstOrNull { u ->
                                        u.contains("wibufile") || u.contains("streamtape") || u.contains("doodstream") ||
                                        u.contains("fcdn") || u.contains("turboviplay") || u.contains("turbovid") ||
                                        u.contains("abysscdn") || u.contains("hydrax") || u.contains("minochinos")
                                    }
                                    if (targetIframe != null) {
                                        Log.d(TAG, "FileLions navigating to sub-iframe: $targetIframe")
                                        webView?.loadUrl(targetIframe)
                                        webView?.postDelayed({
                                            if (webViewResolving && resolveGeneration == gen) {
                                                Log.d(TAG, "FileLions sub-iframe extraction still failing, trying OkHttp on sub-iframe...")
                                                webViewResolving = false
                                                webViewResolveMode = ResolveMode.NONE
                                                webViewResolveCallback?.invoke("")
                                                webViewResolveCallback = null
                                                pendingResolveServer = null
                                                lifecycleScope.launch {
                                                    var found = ""
                                                    for (url in urls) {
                                                        found = extractFileLionsViaOkHttp(url)
                                                        if (found.isNotEmpty()) break
                                                    }
                                                    if (!isFinishing && found.isNotEmpty()) {
                                                        withContext(Dispatchers.Main) {
                                                            resolvedUrlCache[serverIndex] = found
                                                            loadingPlayer.visibility = View.GONE
                                                            initExoPlayer(found)
                                                        }
                                                    }
                                                }
                                            }
                                        }, 10000)
                                    } else {
                                        Log.d(TAG, "FileLions no video host iframe found, trying OkHttp on all iframes...")
                                        webViewResolving = false
                                        webViewResolveMode = ResolveMode.NONE
                                        webViewResolveCallback?.invoke("")
                                        webViewResolveCallback = null
                                        pendingResolveServer = null
                                        lifecycleScope.launch {
                                            var found = ""
                                            for (url in urls) {
                                                found = extractFileLionsViaOkHttp(url)
                                                if (found.isNotEmpty()) break
                                            }
                                            if (found.isEmpty() && embedUrl.isNotEmpty()) {
                                                found = extractFileLionsViaOkHttp(embedUrl)
                                            }
                                            if (!isFinishing && found.isNotEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    resolvedUrlCache[serverIndex] = found
                                                    loadingPlayer.visibility = View.GONE
                                                    initExoPlayer(found)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }, 8000)
                } else {
                    val cb = webViewResolveCallback
                    webViewResolving = false
                    webViewResolveMode = ResolveMode.NONE
                    webViewResolveCallback = null
                    pendingResolveServer = null
                    if ((activeProviderId == com.weebflix.app.data.provider.ProviderFactory.ANICHIN_ID ||
                        activeProviderId == com.weebflix.app.data.provider.ProviderFactory.SAMEHADAKU_ID) && isWebViewPlayableEmbed(embedUrl)) {
                        Log.d(TAG, "embed resolution timed out, playing embed page directly in WebView: $embedUrl")
                        playEpisodePageViaWebView(embedUrl, server, skipInjections = true)
                    } else {
                        cb?.invoke("")
                    }
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
                var found = false;
                function notifyUrl(url) {
                    if (found) return;
                    if (url && url.indexOf('http') === 0 && url.indexOf('about:blank') === -1) {
                        found = true;
                        window.AndroidBridge.onUrlFound(url);
                    }
                }
                function isVideoUrl(s) {
                    if (!s || s.indexOf('about:blank') !== -1 || s.indexOf('blob:') !== -1 || s.indexOf('data:') !== -1 || s.indexOf('javascript:') !== -1) return false;
                    if (s.indexOf('.css') !== -1 || s.indexOf('.js') !== -1 || s.indexOf('.png') !== -1 || s.indexOf('.jpg') !== -1 || s.indexOf('.gif') !== -1 || s.indexOf('.svg') !== -1 || s.indexOf('.ico') !== -1 || s.indexOf('.woff') !== -1) return false;
                    if (s.indexOf('yandex.') !== -1 || s.indexOf('google-analytics.com') !== -1 || s.indexOf('analytics') !== -1 || s.indexOf('doubleclick.net') !== -1 || s.indexOf('facebook.com/tr') !== -1 || s.indexOf('hotjar.com') !== -1 || s.indexOf('sentry.io') !== -1 || s.indexOf('/collect?') !== -1) return false;
                    return s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1 || s.indexOf('.mpd') !== -1 ||
                           s.indexOf('googlevideo.com') !== -1 || s.indexOf('videoplayback') !== -1 ||
                           s.indexOf('wibufile') !== -1 || s.indexOf('vipstream') !== -1 ||
                           (s.indexOf('filedon') !== -1 && (s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1 || s.indexOf('embed') !== -1));
                }
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    if (!found && typeof url === 'string' && isVideoUrl(url)) notifyUrl(url);
                    return origOpen.apply(this, arguments);
                };
                var origFetch = window.fetch;
                window.fetch = function(url) {
                    if (!found && typeof url === 'string' && isVideoUrl(url)) notifyUrl(url);
                    return origFetch.apply(this, arguments);
                };
                function scanDom() {
                    var vids = document.querySelectorAll('video, video source, source');
                    for (var i = 0; i < vids.length; i++) {
                        var s = vids[i].src || vids[i].getAttribute('src') || vids[i].currentSrc || '';
                        if (s && s.indexOf('http') === 0 && isVideoUrl(s)) { notifyUrl(s); return true; }
                    }
                    var iframes = document.querySelectorAll('iframe');
                    for (var j = 0; j < iframes.length; j++) {
                        var isrc = iframes[j].src || iframes[j].getAttribute('src') || '';
                        if (isrc && isVideoUrl(isrc)) { notifyUrl(isrc); return true; }
                    }
                    return false;
                }
                function scanScripts() {
                    var scripts = document.querySelectorAll('script');
                    for (var k = 0; k < scripts.length; k++) {
                        var txt = scripts[k].textContent || '';
                        var patterns = [
                            /["']file["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']source["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']src["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']url["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']video_url["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']videoUrl["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']playbackUrl["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']mediaUrl["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /(?:file|source|src|url)\s*[=:]\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /https?:\/\/[^\s"']+\.mp4[^\s"']*/i,
                            /https?:\/\/[^\s"']+\.m3u8[^\s"']*/i,
                            /https?:\/\/[^\s"']+googlevideo\.com[^\s"']*/i,
                            /https?:\/\/[^\s"']+wibufile[^\s"']+\.(?:mp4|m3u8)[^\s"']*/i,
                            /https?:\/\/[^\s"']+vipstream[^\s"']+\.(?:mp4|m3u8)[^\s"']*/i
                        ];
                        for (var p = 0; p < patterns.length; p++) {
                            var match = txt.match(patterns[p]);
                            if (match && match[1]) { notifyUrl(match[1]); return true; }
                            if (match && match[0] && match[0].indexOf('http') === 0) { notifyUrl(match[0]); return true; }
                        }
                    }
                    return false;
                }
                function scanGlobals() {
                    var keys = ['videoUrl', 'video_url', 'streamUrl', 'stream_url', 'file', 'source', 'src', 'mediaUrl', 'playbackUrl'];
                    for (var g = 0; g < keys.length; g++) {
                        try {
                            var val = window[keys[g]];
                            if (val && typeof val === 'string' && val.indexOf('http') === 0 && isVideoUrl(val)) {
                                notifyUrl(val); return true;
                            }
                        } catch(e) {}
                    }
                    try {
                        if (window.jwplayer) {
                            var item = window.jwplayer().getPlaylistItem ? window.jwplayer().getPlaylistItem() : null;
                            if (item && item.file) { notifyUrl(item.file); return true; }
                        }
                    } catch(e) {}
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
                    return false;
                }
                function scanPerformance() {
                    try {
                        var entries = performance.getEntriesByType('resource');
                        for (var i = 0; i < entries.length; i++) {
                            var name = entries[i].name || '';
                            if (isVideoUrl(name)) { notifyUrl(name); return true; }
                        }
                    } catch(e) {}
                    return false;
                }
                function tryExtract(attempt) {
                    if (found) return;
                    if (scanDom()) return;
                    if (scanScripts()) return;
                    if (scanGlobals()) return;
                    if (scanPerformance()) return;
                    if (attempt < 8) {
                        setTimeout(function() { tryExtract(attempt + 1); }, 1500);
                    } else {
                        notifyUrl('');
                    }
                }
                tryExtract(0);
            })();
        """.trimIndent()
    }

    private fun extractFileLionsVideoJs(): String {
        return """
            (function() {
                var found = false;
                function notifyUrl(url) {
                    if (found) return;
                    if (url && url.indexOf('http') === 0 && url.indexOf('about:blank') === -1) {
                        found = true;
                        window.AndroidBridge.onUrlFound(url);
                    }
                }
                function isVideoUrl(s) {
                    if (!s || s.indexOf('about:blank') !== -1 || s.indexOf('blob:') !== -1 || s.indexOf('data:') !== -1 || s.indexOf('javascript:') !== -1) return false;
                    if (s.indexOf('.css') !== -1 || s.indexOf('.js') !== -1 || s.indexOf('.png') !== -1 || s.indexOf('.jpg') !== -1 || s.indexOf('.gif') !== -1 || s.indexOf('.svg') !== -1 || s.indexOf('.ico') !== -1 || s.indexOf('.woff') !== -1) return false;
                    if (s.indexOf('yandex.') !== -1 || s.indexOf('google-analytics.com') !== -1 || s.indexOf('analytics') !== -1 || s.indexOf('doubleclick.net') !== -1 || s.indexOf('facebook.com/tr') !== -1 || s.indexOf('hotjar.com') !== -1 || s.indexOf('sentry.io') !== -1 || s.indexOf('/collect?') !== -1) return false;
                    try {
                        var h = new URL(s).hostname;
                        if (h.indexOf('minochinos') !== -1 || h.indexOf('filelions') !== -1) return true;
                        if (h.indexOf('turboviplay') !== -1 || h.indexOf('turbovid') !== -1) return true;
                    } catch(e) {}
                    return s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1 || s.indexOf('.mpd') !== -1 ||
                           s.indexOf('googlevideo.com') !== -1 || s.indexOf('videoplayback') !== -1 ||
                           s.indexOf('wibufile') !== -1 || s.indexOf('streamtape') !== -1 || s.indexOf('doodstream') !== -1 ||
                           s.indexOf('fcdn') !== -1;
                }
                function isVideoHostIframe(s) {
                    if (!s || s.indexOf('http') !== 0) return false;
                    return s.indexOf('.mp4') !== -1 || s.indexOf('.m3u8') !== -1 || s.indexOf('.mpd') !== -1 ||
                           s.indexOf('googlevideo.com') !== -1 || s.indexOf('videoplayback') !== -1 ||
                           s.indexOf('wibufile') !== -1 || s.indexOf('streamtape') !== -1 || s.indexOf('doodstream') !== -1 ||
                           s.indexOf('fcdn') !== -1;
                }
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    if (!found && typeof url === 'string' && isVideoUrl(url)) notifyUrl(url);
                    return origOpen.apply(this, arguments);
                };
                var origFetch = window.fetch;
                window.fetch = function(url) {
                    if (!found && typeof url === 'string' && isVideoUrl(url)) notifyUrl(url);
                    return origFetch.apply(this, arguments);
                };
                var origSetAttribute = Element.prototype.setAttribute;
                Element.prototype.setAttribute = function(name, value) {
                    if (!found && name === 'src' && typeof value === 'string' && isVideoUrl(value)) {
                        notifyUrl(value);
                    }
                    return origSetAttribute.apply(this, arguments);
                };
                function scanDom() {
                    var vids = document.querySelectorAll('video, video source, source');
                    for (var i = 0; i < vids.length; i++) {
                        var s = vids[i].src || vids[i].getAttribute('src') || vids[i].currentSrc || '';
                        if (s && s.indexOf('http') === 0 && isVideoUrl(s)) { notifyUrl(s); return true; }
                    }
                    var iframes = document.querySelectorAll('iframe');
                    for (var j = 0; j < iframes.length; j++) {
                        var isrc = iframes[j].src || iframes[j].getAttribute('src') || '';
                        if (isrc && isVideoHostIframe(isrc)) { notifyUrl(isrc); return true; }
                    }
                    return false;
                }
                function scanObjectEmbed() {
                    var objs = document.querySelectorAll('object[data], embed[src]');
                    for (var i = 0; i < objs.length; i++) {
                        var s = objs[i].getAttribute('data') || objs[i].getAttribute('src') || '';
                        if (s && isVideoUrl(s)) { notifyUrl(s); return true; }
                    }
                    return false;
                }
                function scanScripts() {
                    var scripts = document.querySelectorAll('script');
                    for (var k = 0; k < scripts.length; k++) {
                        var txt = scripts[k].textContent || '';
                        var patterns = [
                            /["']file["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']source["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']src["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']url["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']video_url["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']videoUrl["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']playbackUrl["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /["']mediaUrl["']\s*:\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8|mpd)[^"']*)/i,
                            /https?:\/\/[^\s"']+\.mp4[^\s"']*/i,
                            /https?:\/\/[^\s"']+\.m3u8[^\s"']*/i,
                            /https?:\/\/[^\s"']+googlevideo\.com[^\s"']*/i,
                            /https?:\/\/[^\s"']+wibufile[^\s"']+\.(?:mp4|m3u8)[^\s"']*/i,
                            /https?:\/\/[^\s"']+streamtape[^\s"']+/i,
                            /https?:\/\/[^\s"']+doodstream[^\s"']+/i,
                            /https?:\/\/[^\s"']+fcdn[^\s"']+\.(?:mp4|m3u8)[^\s"']*/i,
                            /https?:\/\/[^\s"']+turboviplay[^\s"']+\.m3u8[^\s"']*/i
                        ];
                        for (var p = 0; p < patterns.length; p++) {
                            var match = txt.match(patterns[p]);
                            if (match && match[1]) { notifyUrl(match[1]); return true; }
                            if (match && match[0] && match[0].indexOf('http') === 0) { notifyUrl(match[0]); return true; }
                        }
                    }
                    return false;
                }
                function scanGlobals() {
                    var keys = ['videoUrl', 'video_url', 'streamUrl', 'stream_url', 'file', 'source', 'src', 'mediaUrl', 'playbackUrl', 'jwSource', 'hlsUrl', 'dashUrl'];
                    for (var g = 0; g < keys.length; g++) {
                        try {
                            var val = window[keys[g]];
                            if (val && typeof val === 'string' && val.indexOf('http') === 0 && isVideoUrl(val)) {
                                notifyUrl(val); return true;
                            }
                        } catch(e) {}
                    }
                    try {
                        if (window.jwplayer) {
                            var item = window.jwplayer().getPlaylistItem ? window.jwplayer().getPlaylistItem() : null;
                            if (item && item.file) { notifyUrl(item.file); return true; }
                        }
                    } catch(e) {}
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
                    return false;
                }
                function scanPerformance() {
                    try {
                        var entries = performance.getEntriesByType('resource');
                        for (var i = 0; i < entries.length; i++) {
                            var name = entries[i].name || '';
                            if (isVideoUrl(name)) { notifyUrl(name); return true; }
                        }
                    } catch(e) {}
                    return false;
                }
                function tryExtract(attempt) {
                    if (found) return;
                    if (scanDom()) return;
                    if (scanObjectEmbed()) return;
                    if (scanScripts()) return;
                    if (scanGlobals()) return;
                    if (scanPerformance()) return;
                    if (attempt < 12) {
                        setTimeout(function() { tryExtract(attempt + 1); }, 1500);
                    } else {
                        notifyUrl('');
                    }
                }
                tryExtract(0);
            })();
        """.trimIndent()
    }

    private suspend fun extractFileLionsViaOkHttp(embedUrl: String): String {
        return try {
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }), java.security.SecureRandom())
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .sslSocketFactory(sslContext.socketFactory, object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                .hostnameVerifier { _, _ -> true }
                .build()
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://minochinos.com") ?: ""
            Log.d(TAG, "FileLions OkHttp: cookies from WebView: ${cookies.take(200)}")
            val req = okhttp3.Request.Builder().url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://minochinos.com/")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .apply { if (cookies.isNotEmpty()) header("Cookie", cookies) }
                .build()
            Log.d(TAG, "FileLions OkHttp: requesting $embedUrl")
            val response = client.newCall(req).execute()
            Log.d(TAG, "FileLions OkHttp: response code=${response.code}, redirect=${response.request.url}")
            val body = response.body?.string() ?: ""
            response.close()
            if (body.isEmpty()) {
                Log.e(TAG, "FileLions OkHttp: empty body from $embedUrl")
                return ""
            }
            Log.d(TAG, "FileLions OkHttp: fetched ${body.length} bytes from $embedUrl")
            if (body.length < 5000) {
                Log.d(TAG, "FileLions OkHttp: full HTML: $body")
            } else {
                Log.d(TAG, "FileLions OkHttp: first 2000 chars: ${body.take(2000)}")
            }
            val patterns = listOf(
                Regex("""["']file["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)""", RegexOption.IGNORE_CASE),
                Regex("""["']source["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)""", RegexOption.IGNORE_CASE),
                Regex("""["']src["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)""", RegexOption.IGNORE_CASE),
                Regex("""["']url["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)""", RegexOption.IGNORE_CASE),
                Regex("""["']video_url["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)""", RegexOption.IGNORE_CASE),
                Regex("""["']videoUrl["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)""", RegexOption.IGNORE_CASE),
                Regex("""https?://[^\s"']+\.m3u8[^\s"']*"""),
                Regex("""https?://[^\s"']+\.mp4[^\s"']*"""),
                Regex("""https?://[^\s"']+googlevideo\.com[^\s"']*"""),
                Regex("""https?://[^\s"']+turboviplay[^\s"']+\.m3u8[^\s"']*"""),
                Regex("""https?://[^\s"']+wibufile[^\s"']+\.(?:mp4|m3u8)[^\s"']*""")
            )
            for (pattern in patterns) {
                val match = pattern.find(body)
                if (match != null) {
                    val url = match.groupValues.getOrElse(1) { match.value }
                    if (url.startsWith("http")) {
                        Log.d(TAG, "FileLions OkHttp: found video URL: $url")
                        return url
                    }
                }
            }
            val iframePattern = Regex("""<iframe[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
            val videoHosts = listOf("wibufile", "streamtape", "doodstream", "fcdn", "turboviplay", "turbovid", "abysscdn", "hydrax")
            for (match in iframePattern.findAll(body)) {
                val iframeUrl = match.groupValues[1]
                if (videoHosts.any { iframeUrl.contains(it, ignoreCase = true) }) {
                    Log.d(TAG, "FileLions OkHttp: found video iframe: $iframeUrl")
                    return iframeUrl
                }
            }
            val minochinosIframe = iframePattern.find(body)
            if (minochinosIframe != null) {
                val subIframeUrl = minochinosIframe.groupValues[1]
                if (subIframeUrl.contains("minochinos.com") || subIframeUrl.contains("filelions")) {
                    Log.d(TAG, "FileLions OkHttp: found minochinos sub-iframe, following: $subIframeUrl")
                    val subReq = okhttp3.Request.Builder().url(subIframeUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Referer", "https://minochinos.com/")
                        .build()
                    val subResp = client.newCall(subReq).execute()
                    val subBody = subResp.body?.string() ?: ""
                    subResp.close()
                    for (pattern in patterns) {
                        val subMatch = pattern.find(subBody)
                        if (subMatch != null) {
                            val url = subMatch.groupValues.getOrElse(1) { subMatch.value }
                            if (url.startsWith("http")) {
                                Log.d(TAG, "FileLions OkHttp: found video URL in sub-iframe: $url")
                                return url
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "FileLions OkHttp: no video URL found in page")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "FileLions OkHttp extraction failed: ${e.javaClass.simpleName}: ${e.message}", e)
            ""
        }
    }

    // ===== TurboVI Pre-fetch =====
    private suspend fun prefetchTurboVideo(m3u8Url: String, onReady: ((String) -> Unit)? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = java.io.File(cacheDir, "turbo_prefetch")
                cacheDir.mkdirs()

                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }), java.security.SecureRandom())
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .sslSocketFactory(sslContext.socketFactory, object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    })
                    .hostnameVerifier { _, _ -> true }
                    .build()

                fun fetchUrl(url: String): String {
                    val req = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Referer", "https://turbovidhls.com/")
                        .header("Origin", "https://turbovidhls.com")
                        .build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    return body
                }

                Log.d(TAG, "Pre-fetch: downloading m3u8 from $m3u8Url")
                val m3u8Content = fetchUrl(m3u8Url)
                if (m3u8Content.isEmpty()) {
                    Log.e(TAG, "Pre-fetch: empty m3u8 response")
                    return@withContext null
                }
                Log.d(TAG, "Pre-fetch: m3u8 fetched (${m3u8Content.length} bytes)")

                if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                    Log.d(TAG, "Pre-fetch: master playlist detected, fetching variant")
                    val variantPattern = Regex("""^(?!#)(https?://\S+|\S+\.m3u8\S*)$""", RegexOption.MULTILINE)
                    val variantMatch = variantPattern.find(m3u8Content)
                    if (variantMatch != null) {
                        var variantUrl = variantMatch.groupValues[1]
                        if (!variantUrl.startsWith("http")) {
                            val baseUrl = m3u8Url.substringBeforeLast("/") + "/"
                            variantUrl = baseUrl + variantUrl
                        }
                        Log.d(TAG, "Pre-fetch: variant URL: $variantUrl")
                        val variantContent = fetchUrl(variantUrl)
                        if (variantContent.isNotEmpty()) {
                            return@withContext downloadHlsPlaylist(variantUrl, variantContent, cacheDir, client, onReady)
                        }
                    }
                    Log.e(TAG, "Pre-fetch: failed to resolve variant URL")
                    return@withContext null
                }

                return@withContext downloadHlsPlaylist(m3u8Url, m3u8Content, cacheDir, client, onReady)
            } catch (e: Exception) {
                Log.e(TAG, "Pre-fetch failed: ${e.javaClass.simpleName}: ${e.message}", e)
                null
            }
        }
    }

    private fun downloadHlsPlaylist(m3u8Url: String, m3u8Content: String, cacheDir: java.io.File, client: okhttp3.OkHttpClient, onReady: ((String) -> Unit)? = null): String? {
        val lines = m3u8Content.lines()
        val segmentUrls = mutableListOf<Pair<Int, String>>()
        var baseUrl = m3u8Url.substringBeforeLast("/") + "/"

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            var segUrl = line
            if (!segUrl.startsWith("http")) {
                segUrl = baseUrl + segUrl
            }
            segmentUrls.add(i to segUrl)
        }

        if (segmentUrls.isEmpty()) {
            Log.e(TAG, "Pre-fetch: no segments found in m3u8")
            return null
        }

        val minSegmentsToStart = 30
        Log.d(TAG, "Pre-fetch: found ${segmentUrls.size} segments, need $minSegmentsToStart to start playback")

        val downloadedSegments = mutableMapOf<Int, String>()
        var localM3u8: String? = null
        var playbackStarted = false

        for ((lineIdx, segUrl) in segmentUrls) {
            val segName = "seg_${lineIdx}_${java.io.File(segUrl).name}"
            val segFile = java.io.File(cacheDir, segName)
            if (segFile.exists() && segFile.length() > 0) {
                Log.d(TAG, "Pre-fetch: segment already cached: $segName")
                downloadedSegments[lineIdx] = segFile.absolutePath
                if (downloadedSegments.size >= minSegmentsToStart && !playbackStarted && onReady != null) {
                    playbackStarted = true
                    localM3u8 = writeLocalM3u8(lines, downloadedSegments, cacheDir)
                    Log.d(TAG, "Pre-fetch: starting playback early with ${downloadedSegments.size} segments")
                    onReady(localM3u8!!)
                }
                continue
            }
            var retries = 0
            while (retries < 5) {
                try {
                    val req = okhttp3.Request.Builder().url(segUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Referer", "https://turbovidhls.com/")
                        .header("Origin", "https://turbovidhls.com")
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.code == 200) {
                        val bytes = resp.body?.bytes() ?: byteArrayOf()
                        resp.close()
                        segFile.writeBytes(bytes)
                        downloadedSegments[lineIdx] = segFile.absolutePath
                        Log.d(TAG, "Pre-fetch: segment ${downloadedSegments.size}/${segmentUrls.size} OK ($segName, ${bytes.size} bytes)")
                        retries = 0
                        Thread.sleep(1500L)
                        break
                    } else {
                        retries++
                        val retryAfter = resp.header("Retry-After")?.toLongOrNull()
                        resp.close()
                        val backoff = if (retryAfter != null) {
                            (retryAfter * 1000L).coerceAtMost(30000L)
                        } else {
                            (retries * 5000L).coerceAtMost(40000L)
                        }
                        Log.w(TAG, "Pre-fetch: segment HTTP ${resp.code} on $segName, retry $retries/5 in ${backoff}ms")
                        Thread.sleep(backoff)
                    }
                } catch (e: Exception) {
                    retries++
                    val backoff = (retries * 5000L).coerceAtMost(30000L)
                    Log.w(TAG, "Pre-fetch: segment error on $segName: ${e.message}, retry $retries/5 in ${backoff}ms")
                    Thread.sleep(backoff)
                }
            }
            if (!downloadedSegments.containsKey(lineIdx)) {
                Log.e(TAG, "Pre-fetch: failed to download segment after 5 retries: $segName")
                if (downloadedSegments.size >= minSegmentsToStart) {
                    Log.w(TAG, "Pre-fetch: continuing with ${downloadedSegments.size} available segments")
                    break
                }
                return null
            }

            if (downloadedSegments.size >= minSegmentsToStart && !playbackStarted && onReady != null) {
                playbackStarted = true
                localM3u8 = writeLocalM3u8(lines, downloadedSegments, cacheDir)
                Log.d(TAG, "Pre-fetch: starting playback early with ${downloadedSegments.size} segments")
                onReady(localM3u8!!)
            }
        }

        val finalM3u8 = localM3u8 ?: writeLocalM3u8(lines, downloadedSegments, cacheDir)
        val totalSize = downloadedSegments.values.sumOf { java.io.File(it).length() }
        Log.d(TAG, "Pre-fetch: done — ${downloadedSegments.size}/${segmentUrls.size} segments, ${(totalSize / 1024)}KB total")
        return finalM3u8
    }

    private fun writeLocalM3u8(lines: List<String>, downloadedSegments: Map<Int, String>, cacheDir: java.io.File): String {
        val rewrittenLines = lines.toMutableList()
        for ((lineIdx, localPath) in downloadedSegments) {
            rewrittenLines[lineIdx] = "file://$localPath"
        }
        val localM3u8 = java.io.File(cacheDir, "local_playlist.m3u8")
        localM3u8.writeText(rewrittenLines.joinToString("\n"))
        return localM3u8.absolutePath
    }
    // ===== PiP =====

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val pauseAction = android.app.RemoteAction(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_player_pause),
                    "Pause", "Pause", PendingIntent.getBroadcast(this, 0,
                        Intent("com.weebflix.app.PIP_PAUSE").setPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE)
                )
                val playAction = android.app.RemoteAction(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_player_play),
                    "Play", "Play", PendingIntent.getBroadcast(this, 1,
                        Intent("com.weebflix.app.PIP_PLAY").setPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE)
                )

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(listOf(playAction, pauseAction))
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setAutoEnterEnabled(false)
                            setSeamlessResizeEnabled(true)
                        }
                    }
                    .build()
                enterPictureInPictureMode(params)
                isPipMode = true
                hideControls()
                showControlsDelayedInPip()
            } catch (e: Exception) {
                Log.e(TAG, "PiP failed: ${e.message}")
                Toast.makeText(this, getString(R.string.pip_not_supported), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showControlsDelayedInPip() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isPipMode && !isFinishing) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setAutoEnterEnabled(false)
                                setSeamlessResizeEnabled(true)
                            }
                        }
                        .build()
                    setPictureInPictureParams(params)
                } catch (_: Exception) {}
            }
        }, 300)
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
            playerView.useController = false
            exoPlayer?.playWhenReady = true
        } else {
            gestureOverlay.visibility = View.VISIBLE
            playerView.useController = false
            exoPlayer?.playWhenReady = true
            showControls()
            scheduleAutoHide()
        }
    }


    // ===== Lifecycle =====

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 500)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun saveWatchHistory() {
        val player = exoPlayer ?: return
        if (episodeUrl.isEmpty()) return
        val position = player.currentPosition
        val duration = player.duration
        if (duration < 10000) return
        WatchHistoryManager.saveProgress(
            context = this,
            episodeUrl = episodeUrl,
            animeTitle = animeTitle,
            episodeTitle = episodeTitle,
            episodeNumber = episodeNumber,
            imageUrl = imageUrl,
            animeUrl = animeUrl,
            providerId = activeProviderId,
            progressMs = position,
            durationMs = duration
        )
    }

    override fun onPause() {
        super.onPause()
        // JANGAN pause player di sini: biar audio tetap jalan saat app di-background layar
        // dikunci (bunyi layar dikunci audio mati) — perilaku seperti YouTube/GoTube.
        // Wakelock WAKE_MODE_NETWORK di player menjaga CPU/network selama playback berjalan.
        if (!isPipMode) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
            autoHideHandler.removeCallbacks(autoHideRunnable)
            autoPlayHandler.removeCallbacks(autoPlayRunnable)
        }
    }

    override fun onDestroy() {
        saveWatchHistory()
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
        pipActionReceiver?.let { unregisterReceiver(it) }
        pipActionReceiver = null
        releaseMediaSession()
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
