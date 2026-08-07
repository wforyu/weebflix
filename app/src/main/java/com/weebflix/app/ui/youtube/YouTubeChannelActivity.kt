package com.weebflix.app.ui.youtube

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.scraper.YouTubeChannelDetail
import com.weebflix.app.data.scraper.YouTubeDataApi
import com.weebflix.app.data.scraper.YouTubeScraper
import com.weebflix.app.data.scraper.YouTubeVideo
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.util.Insets
import com.weebflix.app.ui.util.TvUtils
import com.weebflix.app.ui.util.padSystemBars
import com.weebflix.app.ui.youtube.adapter.YouTubeSearchAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A channel page (like the real YouTube): header with banner/avatar/name/subscriber count +
 *  a paginated list of every video uploaded by the owner. Opened from any channel thumb/name
 *  in the feed, search or player. */
class YouTubeChannelActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHANNEL_ID = "channelId"
        const val EXTRA_CHANNEL_NAME = "channelName"
        const val EXTRA_CHANNEL_THUMB = "channelThumb"
    }

    private lateinit var channelAvatar: ImageView
    private lateinit var channelName: TextView
    private lateinit var channelSubs: TextView
    private lateinit var channelTitleBar: TextView
    private lateinit var btnSubscribe: TextView
    private lateinit var ytLoading: ProgressBar
    private lateinit var ytEmpty: TextView
    private lateinit var videosList: RecyclerView

    private val scraper = YouTubeScraper()
    private lateinit var adapter: YouTubeSearchAdapter

    private var channelId = ""
    private var continuation = ""
    private var loadingMore = false
    private var ended = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvUtils.forceLandscapeOnTv(this)
        setContentView(R.layout.activity_youtube_channel)
        Insets.edgeToEdge(this)
        findViewById<View>(R.id.rootLayout).padSystemBars()

        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        val fallbackName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
        val fallbackThumb = intent.getStringExtra(EXTRA_CHANNEL_THUMB).orEmpty()

        channelAvatar = findViewById(R.id.channelAvatar)
        channelName = findViewById(R.id.channelName)
        channelSubs = findViewById(R.id.channelSubs)
        channelTitleBar = findViewById(R.id.channelTitleBar)
        btnSubscribe = findViewById(R.id.btnChannelSubscribe)
        ytLoading = findViewById(R.id.ytLoading)
        ytEmpty = findViewById(R.id.ytEmpty)
        videosList = findViewById(R.id.videosList)

        channelName.text = fallbackName
        channelTitleBar.text = fallbackName
        if (fallbackThumb.isNotEmpty()) {
            Glide.with(this).load(fallbackThumb)
                .placeholder(R.drawable.bg_card)
                .into(channelAvatar)
        }

        adapter = YouTubeSearchAdapter(
            { video -> openVideo(video) },
            { video -> openChannel(video) }
        )
        videosList.layoutManager = LinearLayoutManager(this)
        videosList.adapter = adapter

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        btnSubscribe.setOnClickListener { onSubscribePressed() }

        videosList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 4) loadMore()
            }
        })

        if (channelId.isEmpty()) {
            ytEmpty.visibility = View.VISIBLE
            ytEmpty.text = "Channel tidak valid"
            return
        }
        loadChannel()
    }

    private fun loadChannel() {
        ytLoading.visibility = View.VISIBLE
        ytEmpty.visibility = View.GONE
        lifecycleScope.launch {
            val detail: YouTubeChannelDetail = try {
                withContext(Dispatchers.IO) { scraper.getChannelDetail(channelId) }
            } catch (e: Exception) {
                YouTubeChannelDetail(channelId = channelId)
            }
            ytLoading.visibility = View.GONE
            bindHeader(detail)
            if (detail.videos.isNotEmpty()) {
                ytEmpty.visibility = View.GONE
                adapter.submit(detail.videos)
            } else {
                ytEmpty.visibility = View.VISIBLE
            }
            continuation = detail.continuation
            syncSubscribeState()
        }
    }

    private fun bindHeader(detail: YouTubeChannelDetail) {
        if (detail.channelName.isNotEmpty()) {
            channelName.text = detail.channelName
            channelTitleBar.text = detail.channelName
        }
        if (detail.channelThumb.isNotEmpty()) {
            Glide.with(this).load(detail.channelThumb)
                .placeholder(R.drawable.bg_card)
                .into(channelAvatar)
        }
        if (detail.channelBanner.isNotEmpty()) {
            Glide.with(this).load(detail.channelBanner)
                .placeholder(R.drawable.bg_card)
                .into(findViewById<ImageView>(R.id.channelBanner))
        }
        if (detail.subscriberCount.isNotEmpty()) {
            channelSubs.text = detail.subscriberCount
        }
    }

    private fun loadMore() {
        if (loadingMore || ended || continuation.isEmpty()) return
        loadingMore = true
        lifecycleScope.launch {
            val page = try {
                withContext(Dispatchers.IO) { scraper.getChannelNextPage(continuation) }
            } catch (e: Exception) {
                YouTubeChannelDetail()
            }
            loadingMore = false
            if (page.videos.isEmpty()) {
                ended = true
                return@launch
            }
            continuation = page.continuation
            adapter.submit(adapter.items + page.videos)
        }
    }

    private fun syncSubscribeState() {
        if (!YouTubeAuthManager.isLoggedIn()) {
            btnSubscribe.visibility = View.GONE
            return
        }
        btnSubscribe.visibility = View.VISIBLE
        lifecycleScope.launch {
            val subscribed = try {
                withContext(Dispatchers.IO) { YouTubeDataApi.isSubscribedExact(channelId) }
            } catch (e: Exception) {
                false
            }
            setSubscribeUi(subscribed)
        }
    }

    private fun onSubscribePressed() {
        if (!YouTubeAuthManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.yt_login_required_subscribe), Toast.LENGTH_SHORT).show()
            return
        }
        val subscribing = btnSubscribe.text.toString().equals(getString(R.string.yt_subscribe), ignoreCase = true)
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { YouTubeDataApi.setSubscription(channelId, subscribing) }
            } catch (e: Exception) {
                false
            }
            if (ok) {
                setSubscribeUi(!subscribing)
                Toast.makeText(
                    this@YouTubeChannelActivity,
                    if (subscribing) "Berlangganan" else "Berhenti berlangganan",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@YouTubeChannelActivity,
                    getString(R.string.yt_engagement_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setSubscribeUi(subscribed: Boolean) {
        btnSubscribe.text = getString(if (subscribed) R.string.yt_subscribed else R.string.yt_subscribe)
        btnSubscribe.setBackgroundResource(
            if (subscribed) R.drawable.bg_yt_subscribed else R.drawable.bg_yt_subscribe
        )
    }

    private fun openVideo(video: YouTubeVideo) {
        val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", video.url)
            putExtra("title", video.title)
            putExtra("episodeNumber", "1")
            putExtra("animeTitle", video.title)
            putExtra("imageUrl", video.thumbnail)
            putExtra("animeUrl", video.url)
            putExtra("providerId", com.weebflix.app.data.provider.ProviderFactory.YOUTUBE_ID)
            putExtra("nextEpisodeUrl", "")
        }
        startActivity(intent)
    }

    private fun openChannel(@Suppress("UNUSED_PARAMETER") video: YouTubeVideo) {
        // All videos here belong to the same channel — nothing to navigate to.
    }
}
