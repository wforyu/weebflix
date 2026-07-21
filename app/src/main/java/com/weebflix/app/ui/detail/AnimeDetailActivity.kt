package com.weebflix.app.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.model.AnimeDetail
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
    private lateinit var episodeAdapter: EpisodeListAdapter

    private var animeUrl: String = ""
    private var detail: AnimeDetail? = null

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

        val ivBack = findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener { finish() }

        episodeAdapter = EpisodeListAdapter { episode ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("url", episode.url)
            intent.putExtra("title", episode.title)
            intent.putExtra("episodeNumber", episode.episodeNumber)
            intent.putExtra("animeTitle", detail?.anime?.title ?: "")
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
                    val intent = Intent(this, PlayerActivity::class.java)
                    intent.putExtra("url", latestEp.url)
                    intent.putExtra("title", latestEp.title)
                    intent.putExtra("episodeNumber", latestEp.episodeNumber)
                    intent.putExtra("animeTitle", d.anime.title)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Belum ada episode", Toast.LENGTH_SHORT).show()
                }
            }
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
                    tvSynopsis.text = anime.synopsis.ifEmpty { "Sinopsis tidak tersedia" }
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
                        episodeAdapter.submitList(result.episodes)
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(this@AnimeDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
