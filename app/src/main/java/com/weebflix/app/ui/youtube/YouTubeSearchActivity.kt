package com.weebflix.app.ui.youtube

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.YouTubeScraper
import com.weebflix.app.data.scraper.YouTubeVideo
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.util.Insets
import com.weebflix.app.ui.util.TvUtils
import com.weebflix.app.ui.util.padSystemBars
import com.weebflix.app.ui.youtube.adapter.YouTubeSearchAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class YouTubeSearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var ytLoading: ProgressBar
    private lateinit var ytEmpty: TextView
    private lateinit var chipContainer: LinearLayout
    private lateinit var adapter: YouTubeSearchAdapter

    private val scraper by lazy { YouTubeScraper() }
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var currentParams: String? = null
    private var searchContinuation = ""
    private var searchLoading = false
    private var searchEnded = false

    private val seenIds = mutableSetOf<String>()
    private var searchGeneration = 0

    private val filters = listOf(
        "Semua" to null,
        "Video" to "EgIQAQ%3D%3D",
        "Film" to "EgIYAQ%3D%3D",
        "Live" to "EgJAAQ%3D%3D",
        "Durasi Panjang" to "EgIYBA%3D%3D",
        "Durasi Pendek" to "EgIYAw%3D%3D",
        "Minggu Ini" to "EgIIAw%3D%3D"
    )

    private val chipViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvUtils.forceLandscapeOnTv(this)
        setContentView(R.layout.activity_youtube_search)
        Insets.edgeToEdge(this)
        findViewById<View>(R.id.rootLayout).padSystemBars()

        searchInput = findViewById(R.id.searchInput)
        ytLoading = findViewById(R.id.ytLoading)
        ytEmpty = findViewById(R.id.ytEmpty)
        chipContainer = findViewById(R.id.chipContainer)
        val resultsList: RecyclerView = findViewById(R.id.resultsList)

        adapter = YouTubeSearchAdapter(
            { video -> openVideo(video) },
            { video -> openChannel(video) }
        )
        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = adapter
        resultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                val total = adapter.itemCount
                if (lastVisible >= total - 6) loadMoreSearch()
            }
        })

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        buildChips()

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                val gen = ++searchGeneration
                searchJob = lifecycleScope.launch {
                    delay(500)
                    if (searchGeneration != gen) return@launch
                    performSearch()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        ytEmpty.visibility = View.VISIBLE
    }

    private fun buildChips() {
        chipContainer.removeAllViews()
        chipViews.clear()
        filters.forEachIndexed { index, (label, params) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 13f
                setPadding(28, 20, 28, 20)
                background = if (index == 0) resources.getDrawable(R.drawable.bg_chip_yt_active) else resources.getDrawable(R.drawable.bg_chip_yt)
                setTextColor(if (index == 0) androidx.core.content.ContextCompat.getColor(this@YouTubeSearchActivity, R.color.yt_bg) else 0xFFFFFFFF.toInt())
                setOnClickListener { selectChip(index) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            chipContainer.addView(chip, lp)
            chipViews.add(chip)
        }
    }

    private fun selectChip(index: Int) {
        chipViews.forEachIndexed { i, chip ->
            chip.background = if (i == index) resources.getDrawable(R.drawable.bg_chip_yt_active) else resources.getDrawable(R.drawable.bg_chip_yt)
            chip.setTextColor(if (i == index) androidx.core.content.ContextCompat.getColor(this@YouTubeSearchActivity, R.color.yt_bg) else 0xFFFFFFFF.toInt())
        }
        currentParams = filters[index].second
        performSearch()
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            searchJob?.cancel()
            adapter.submitPage(emptyList(), endOfFeed = true)
            ytLoading.visibility = View.GONE
            ytEmpty.visibility = View.VISIBLE
            ytEmpty.text = "Ketik untuk mencari video"
            searchContinuation = ""
            searchLoading = false
            searchEnded = true
            seenIds.clear()
            return
        }
        ytLoading.visibility = View.VISIBLE
        ytEmpty.visibility = View.GONE
        seenIds.clear()
        searchContinuation = ""
        searchLoading = false
        searchEnded = false
        searchJob?.cancel()
        loadMoreJob?.cancel()
        searchJob = lifecycleScope.launch {
            val page: com.weebflix.app.data.scraper.SearchPage = try {
                scraper.searchPage(query, currentParams)
            } catch (e: Exception) {
                com.weebflix.app.data.scraper.SearchPage()
            }
            ytLoading.visibility = View.GONE
            if (page.videos.isEmpty()) {
                ytEmpty.visibility = View.VISIBLE
                ytEmpty.text = "Tidak ada hasil untuk \"$query\""
                searchEnded = true
            } else {
                ytEmpty.visibility = View.GONE
                seenIds.addAll(page.videos.map { it.videoId })
                searchContinuation = page.continuation
                searchEnded = page.continuation.isEmpty()
                adapter.submitPage(page.videos, endOfFeed = page.continuation.isEmpty())
            }
        }
    }

    private fun loadMoreSearch() {
        if (searchLoading || searchEnded || searchContinuation.isEmpty()) return
        searchLoading = true
        adapter.setLoading()
        val continuation = searchContinuation
        loadMoreJob?.cancel()
        loadMoreJob = lifecycleScope.launch {
            val page: com.weebflix.app.data.scraper.SearchPage = try {
                scraper.nextSearchPage(continuation)
            } catch (e: Exception) {
                com.weebflix.app.data.scraper.SearchPage()
            }
            val fresh = page.videos.filter { it.videoId.isNotEmpty() && seenIds.add(it.videoId) }
            searchLoading = false
            searchContinuation = page.continuation
            val ended = page.continuation.isEmpty() || fresh.isEmpty()
            searchEnded = ended
            adapter.append(fresh, endOfFeed = ended)
        }
    }

    private fun openVideo(video: YouTubeVideo) {
        val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", video.url)
            putExtra("title", video.title)
            putExtra("episodeNumber", "1")
            putExtra("animeTitle", video.title)
            putExtra("imageUrl", video.thumbnail)
            putExtra("animeUrl", video.url)
            putExtra("providerId", ProviderFactory.YOUTUBE_ID)
            putExtra("nextEpisodeUrl", "")
        }
        startActivity(intent)
    }

    private fun openChannel(video: YouTubeVideo) {
        val intent = android.content.Intent(this, YouTubeChannelActivity::class.java).apply {
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_ID, video.channelId)
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_NAME, video.channel)
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_THUMB, video.channelThumb)
        }
        startActivity(intent)
    }
}
