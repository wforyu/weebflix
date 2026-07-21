package com.weebflix.app.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.VideoServer
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var playerContainer: LinearLayout
    private lateinit var loadingPlayer: ProgressBar
    private lateinit var tvEpisodeTitle: TextView
    private lateinit var tvAnimeTitle: TextView
    private lateinit var llServerButtons: LinearLayout
    private lateinit var btnPrevEp: TextView
    private lateinit var btnNextEp: TextView
    private lateinit var controlPanel: LinearLayout

    private var episodeUrl: String = ""
    private var episodeTitle: String = ""
    private var episodeNumber: String = ""
    private var animeTitle: String = ""
    private var servers: List<VideoServer> = emptyList()
    private var currentServerIndex: Int = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        episodeUrl = intent.getStringExtra("url") ?: ""
        episodeTitle = intent.getStringExtra("title") ?: ""
        episodeNumber = intent.getStringExtra("episodeNumber") ?: ""
        animeTitle = intent.getStringExtra("animeTitle") ?: ""

        playerContainer = findViewById(R.id.playerContainer)
        loadingPlayer = findViewById(R.id.loadingPlayer)
        tvEpisodeTitle = findViewById(R.id.tvEpisodeTitle)
        tvAnimeTitle = findViewById(R.id.tvAnimeTitle)
        llServerButtons = findViewById(R.id.llServerButtons)
        btnPrevEp = findViewById(R.id.btnPrevEp)
        btnNextEp = findViewById(R.id.btnNextEp)
        controlPanel = findViewById(R.id.controlPanel)

        tvEpisodeTitle.text = if (episodeTitle.isNotEmpty()) episodeTitle else "Episode $episodeNumber"
        tvAnimeTitle.text = animeTitle

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loadingPlayer.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadingPlayer.visibility = View.GONE
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        loadingPlayer.visibility = View.GONE
                    }
                }
            }
        }
        playerContainer.addView(webView)

        btnPrevEp.setOnClickListener {
            if (currentServerIndex > 0) {
                currentServerIndex--
                loadServer(currentServerIndex)
                updateServerButtons()
            }
        }

        btnNextEp.setOnClickListener {
            if (currentServerIndex < servers.size - 1) {
                currentServerIndex++
                loadServer(currentServerIndex)
                updateServerButtons()
            }
        }

        if (episodeUrl.isNotEmpty()) {
            loadServers()
        }
    }

    private fun loadServers() {
        lifecycleScope.launch {
            try {
                servers = WeebFlixApp.instance.scraper.getEpisodeServers(episodeUrl)
                if (!isFinishing) {
                    if (servers.isNotEmpty()) {
                        setupServerButtons()
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

    @SuppressLint("SetTextI18n")
    private fun setupServerButtons() {
        llServerButtons.removeAllViews()
        servers.forEachIndexed { index, server ->
            val btn = TextView(this).apply {
                text = server.name
                setTextColor(resources.getColor(R.color.white, null))
                textSize = 12f
                setPadding(32, 16, 32, 16)
                setBackgroundResource(
                    if (index == currentServerIndex) R.drawable.bg_chip_active
                    else R.drawable.bg_chip
                )
                setOnClickListener {
                    currentServerIndex = index
                    loadServer(index)
                    updateServerButtons()
                }
            }
            llServerButtons.addView(btn)

            val params = btn.layoutParams as LinearLayout.LayoutParams
            params.marginEnd = 16
            btn.layoutParams = params
        }
    }

    private fun updateServerButtons() {
        for (i in 0 until llServerButtons.childCount) {
            val btn = llServerButtons.getChildAt(i) as TextView
            btn.setBackgroundResource(
                if (i == currentServerIndex) R.drawable.bg_chip_active
                else R.drawable.bg_chip
            )
        }
    }

    private fun loadServer(index: Int) {
        if (index in servers.indices) {
            val server = servers[index]
            if (server.url.contains("embed") || server.url.contains("player") || server.url.contains("http")) {
                loadUrl(server.url)
            } else {
                val fullUrl = if (server.url.startsWith("http")) server.url
                else "${ProviderConfig.baseUrl}${server.url}"
                loadUrl(fullUrl)
            }
        }
    }

    private fun loadUrl(url: String) {
        loadingPlayer.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
