package com.weebflix.app.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.DrakorKitaScraper
import com.weebflix.app.data.scraper.OppaDramaScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryGridActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_TITLE = "title"
        const val CATEGORY_EPISODES = "episodes"
        const val CATEGORY_MOVIES = "movies"
        const val CATEGORY_SERIES = "series"
        const val CATEGORY_ALL = "all"
    }

    private lateinit var rvGrid: RecyclerView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvCategoryTitle: TextView

    private val items = mutableListOf<Anime>()
    private var isLoading = false
    private var currentPage = 1
    private var hasMore = true
    private var category = ""

    private val gridAdapter by lazy {
        object : RecyclerView.Adapter<GridCardHolder>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridCardHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_netflix_card, parent, false)
                return GridCardHolder(view)
            }
            override fun onBindViewHolder(holder: GridCardHolder, position: Int) {
                holder.bind(items[position])
            }
        }
    }

    inner class GridCardHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivPoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvQuality: TextView = itemView.findViewById(R.id.tvQuality)

        fun bind(anime: Anime) {
            tvTitle.text = anime.title
            val subtitle = buildString {
                if (anime.episode.isNotEmpty()) append(anime.episode)
                if (anime.status.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(anime.status)
                }
            }
            if (subtitle.isNotEmpty()) {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = subtitle
            } else {
                tvSubtitle.visibility = View.GONE
            }
            if (anime.type.isNotEmpty()) {
                tvQuality.visibility = View.VISIBLE
                tvQuality.text = anime.type
            } else if (anime.status.isNotEmpty() && anime.status.length <= 5) {
                tvQuality.visibility = View.VISIBLE
                tvQuality.text = anime.status
            } else {
                tvQuality.visibility = View.GONE
            }
            if (anime.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(anime.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(ivPoster)
            }
            itemView.setOnClickListener {
                startActivity(Intent(this@CategoryGridActivity, AnimeDetailActivity::class.java).apply {
                    putExtra("url", anime.url)
                })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_grid)

        category = intent.getStringExtra(EXTRA_CATEGORY) ?: CATEGORY_ALL
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Semua"

        rvGrid = findViewById(R.id.rvGrid)
        loadingLayout = findViewById(R.id.loadingLayout)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)
        tvCategoryTitle.text = title

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        rvGrid.apply {
            layoutManager = GridLayoutManager(this@CategoryGridActivity, 3)
            adapter = gridAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    if (dy <= 0) return
                    val lm = rv.layoutManager as GridLayoutManager
                    val totalItems = lm.itemCount
                    val lastVisible = lm.findLastVisibleItemPosition()
                    if (!isLoading && hasMore && lastVisible >= totalItems - 6) {
                        loadMore()
                    }
                }
            })
        }

        loadMore()
    }

    private fun loadMore() {
        if (isLoading || !hasMore) return
        isLoading = true

        lifecycleScope.launch {
            try {
                val activeProvider = ProviderFactory.getActiveProvider()
                val isOppaDrama = activeProvider.id == ProviderFactory.OPPADRAMA_ID
                val isDrakorKita = activeProvider.id == ProviderFactory.DRAKORKITA_ID

                val newItems = withContext(Dispatchers.IO) {
                    if (isOppaDrama) {
                        val provider = activeProvider as OppaDramaScraper
                        when (category) {
                            CATEGORY_EPISODES -> provider.getLatestEpisodes(currentPage).map { ep ->
                                Anime(title = ep.title, url = ep.url, imageUrl = ep.imageUrl, episode = ep.episodeNumber, score = ep.uploadDate)
                            }
                            CATEGORY_MOVIES -> provider.getFilmKorea(currentPage)
                            CATEGORY_SERIES -> provider.getDramaKorea(currentPage)
                            else -> provider.getOngoingAnime(currentPage)
                        }
                    } else if (isDrakorKita) {
                        val provider = activeProvider as DrakorKitaScraper
                        when (category) {
                            CATEGORY_EPISODES -> provider.getHomeContent().latestEpisodes.map { ep ->
                                Anime(title = ep.title, url = ep.url, imageUrl = ep.imageUrl, episode = ep.episodeNumber, score = ep.uploadDate)
                            }
                            CATEGORY_MOVIES -> {
                                if (currentPage <= 1) provider.getHomeContent().movies
                                else provider.getOngoingAnime(currentPage)
                            }
                            CATEGORY_SERIES -> {
                                if (currentPage <= 1) provider.getHomeContent().series
                                else provider.getPopularAnime(currentPage)
                            }
                            else -> provider.getAllAnime(currentPage)
                        }
                    } else {
                        activeProvider.getOngoingAnime(currentPage)
                    }
                }

                if (!isFinishing) {
                    loadingLayout.visibility = View.GONE

                    if (newItems.isEmpty() && items.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else if (newItems.isEmpty()) {
                        hasMore = false
                    } else {
                        val start = items.size
                        items.addAll(newItems)
                        gridAdapter.notifyItemRangeInserted(start, newItems.size)
                        currentPage++

                        if (category == CATEGORY_EPISODES) {
                            hasMore = false
                        }
                    }

                    isLoading = false
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    loadingLayout.visibility = View.GONE
                    isLoading = false
                    if (items.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@CategoryGridActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
