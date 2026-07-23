package com.weebflix.app.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.model.AnimeDetail
import com.weebflix.app.data.model.Episode
import com.weebflix.app.ui.adapter.EpisodeListAdapter
import com.weebflix.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class AnimeDetailActivity : AppCompatActivity() {

    private lateinit var ivBanner: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvSynopsis: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvType: TextView
    private lateinit var tvTotalEp: TextView
    private lateinit var tvStudio: TextView
    private lateinit var tvSeason: TextView
    private lateinit var llPlayContainer: LinearLayout
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var spinnerEpisodeRange: Spinner
    private lateinit var episodeAdapter: EpisodeListAdapter

    private var animeUrl: String = ""
    private var detail: AnimeDetail? = null
    private var allSortedEpisodes: List<Episode> = emptyList()
    private var episodeRanges: List<String> = emptyList()
    private var currentRangeIndex: Int = 0
    private val EPISODES_PER_RANGE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anime_detail)

        animeUrl = intent.getStringExtra("url") ?: ""

        ivBanner = findViewById(R.id.ivDetailBanner)
        tvTitle = findViewById(R.id.tvDetailTitle)
        tvSubtitle = findViewById(R.id.tvDetailSubtitle)
        tvSynopsis = findViewById(R.id.tvSynopsis)
        tvStatus = findViewById(R.id.tvStatus)
        tvType = findViewById(R.id.tvType)
        tvTotalEp = findViewById(R.id.tvTotalEp)
        tvStudio = findViewById(R.id.tvStudio)
        tvSeason = findViewById(R.id.tvSeason)
        llPlayContainer = findViewById(R.id.llPlayContainer)
        rvEpisodes = findViewById(R.id.rvEpisodes)
        loadingLayout = findViewById(R.id.loadingLayout)
        spinnerEpisodeRange = findViewById(R.id.spinnerEpisodeRange)

        val ivBack = findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener { finish() }

        episodeAdapter = EpisodeListAdapter { episode ->
            val episodeIndex = allSortedEpisodes.indexOfFirst { it.url == episode.url }
            val nextEp = if (episodeIndex >= 0 && episodeIndex < allSortedEpisodes.size - 1) {
                allSortedEpisodes[episodeIndex + 1]
            } else null

            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("url", episode.url)
            intent.putExtra("title", episode.title)
            intent.putExtra("episodeNumber", episode.episodeNumber)
            intent.putExtra("animeTitle", detail?.anime?.title ?: "")
            intent.putExtra("imageUrl", detail?.anime?.imageUrl ?: "")
            intent.putExtra("animeUrl", detail?.anime?.url ?: "")
            nextEp?.let {
                intent.putExtra("nextEpisodeUrl", it.url)
                intent.putExtra("nextEpisodeTitle", it.title)
            }
            startActivity(intent)
        }

        rvEpisodes.apply {
            layoutManager = LinearLayoutManager(this@AnimeDetailActivity)
            adapter = episodeAdapter
            isNestedScrollingEnabled = false
        }

        llPlayContainer.setOnClickListener {
            detail?.let { d ->
                val latestEp = d.episodes.firstOrNull()
                if (latestEp != null) {
                    val latestIndex = d.episodes.indexOfFirst { it.url == latestEp.url }
                    val nextEp = if (latestIndex >= 0 && latestIndex < d.episodes.size - 1) {
                        d.episodes[latestIndex + 1]
                    } else null

                    val intent = Intent(this, PlayerActivity::class.java)
                    intent.putExtra("url", latestEp.url)
                    intent.putExtra("title", latestEp.title)
                    intent.putExtra("episodeNumber", latestEp.episodeNumber)
                    intent.putExtra("animeTitle", d.anime.title)
                    intent.putExtra("imageUrl", d.anime.imageUrl)
                    intent.putExtra("animeUrl", d.anime.url)
                    nextEp?.let {
                        intent.putExtra("nextEpisodeUrl", it.url)
                        intent.putExtra("nextEpisodeTitle", it.title)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.no_episodes), Toast.LENGTH_SHORT).show()
                }
            }
        }

        spinnerEpisodeRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != currentRangeIndex) {
                    currentRangeIndex = position
                    showEpisodeRange(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (animeUrl.isNotEmpty()) {
            loadDetail()
        }
    }

    private fun loadDetail() {
        lifecycleScope.launch {
            try {
                val result = WeebFlixApp.instance.scraper.getAnimeDetail(animeUrl)
                detail = result

                if (!isFinishing) {
                    loadingLayout.visibility = View.GONE

                    val anime = result.anime
                    tvTitle.text = anime.title
                    tvSubtitle.text = "${anime.type} ${anime.episode}"
                    tvSynopsis.text = anime.synopsis.ifEmpty { getString(R.string.no_synopsis) }
                    tvStatus.text = anime.status.ifEmpty { "-" }
                    tvType.text = anime.type.ifEmpty { "-" }
                    tvTotalEp.text = anime.totalEpisodes.ifEmpty { anime.episode.ifEmpty { "-" } }
                    tvStudio.text = anime.studio.ifEmpty { "-" }
                    tvSeason.text = anime.season.ifEmpty { "-" }

                    if (anime.imageUrl.isNotEmpty()) {
                        Glide.with(this@AnimeDetailActivity)
                            .load(anime.imageUrl)
                            .centerCrop()
                            .into(ivBanner)
                    }

                    if (result.episodes.isNotEmpty()) {
                        allSortedEpisodes = result.episodes.sortedBy { ep ->
                            Regex("""(\d+)""").find(ep.episodeNumber)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        detail = AnimeDetail(anime = result.anime, episodes = allSortedEpisodes)
                        setupEpisodeRange()
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(this@AnimeDetailActivity, getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupEpisodeRange() {
        val total = allSortedEpisodes.size
        if (total <= EPISODES_PER_RANGE) {
            spinnerEpisodeRange.visibility = View.GONE
            episodeAdapter.submitList(allSortedEpisodes)
            return
        }

        val rangeCount = (total + EPISODES_PER_RANGE - 1) / EPISODES_PER_RANGE
        episodeRanges = (0 until rangeCount).map { i ->
            val start = i * EPISODES_PER_RANGE + 1
            val end = minOf((i + 1) * EPISODES_PER_RANGE, total)
            "Ep $start - $end"
        }

        currentRangeIndex = 0
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, episodeRanges).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerEpisodeRange.adapter = spinnerAdapter
        spinnerEpisodeRange.visibility = View.VISIBLE
        showEpisodeRange(0)
    }

    private fun showEpisodeRange(rangeIndex: Int) {
        val start = rangeIndex * EPISODES_PER_RANGE
        val end = minOf(start + EPISODES_PER_RANGE, allSortedEpisodes.size)
        if (start < allSortedEpisodes.size) {
            episodeAdapter.submitList(allSortedEpisodes.subList(start, end))
        }
    }
}
