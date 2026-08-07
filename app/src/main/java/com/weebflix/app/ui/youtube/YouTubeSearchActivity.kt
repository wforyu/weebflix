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
    private var currentParams: String? = null

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

        adapter = YouTubeSearchAdapter { video -> openVideo(video) }
        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = adapter

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
                searchJob = lifecycleScope.launch {
                    delay(500)
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
                setTextColor(if (index == 0) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt())
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
            chip.setTextColor(if (i == index) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt())
        }
        currentParams = filters[index].second
        performSearch()
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            searchJob?.cancel()
            adapter.submit(emptyList())
            ytLoading.visibility = View.GONE
            ytEmpty.visibility = View.VISIBLE
            ytEmpty.text = "Ketik untuk mencari video"
            return
        }
        ytLoading.visibility = View.VISIBLE
        ytEmpty.visibility = View.GONE
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val results: List<YouTubeVideo> = try {
                scraper.searchVideos(query, currentParams)
            } catch (e: Exception) {
                emptyList()
            }
            ytLoading.visibility = View.GONE
            if (results.isEmpty()) {
                ytEmpty.visibility = View.VISIBLE
                ytEmpty.text = "Tidak ada hasil untuk \"$query\""
            } else {
                ytEmpty.visibility = View.GONE
                adapter.submit(results)
            }
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
}
