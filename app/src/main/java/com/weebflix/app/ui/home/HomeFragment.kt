package com.weebflix.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.Episode
import com.weebflix.app.ui.adapter.AnimeAdapter
import com.weebflix.app.ui.adapter.LatestEpisodeAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvOngoingAnime: RecyclerView
    private lateinit var rvPopularAnime: RecyclerView
    private lateinit var ivHero: android.widget.ImageView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroEpisode: TextView
    private lateinit var btnHeroPlay: TextView
    private lateinit var btnHeroDetail: TextView

    private lateinit var latestAdapter: LatestEpisodeAdapter
    private lateinit var ongoingAdapter: AnimeAdapter
    private lateinit var popularAdapter: AnimeAdapter

    private var heroEpisode: Episode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        scrollView = view.findViewById(R.id.scrollView)
        rvLatestEpisodes = view.findViewById(R.id.rvLatestEpisodes)
        rvOngoingAnime = view.findViewById(R.id.rvOngoingAnime)
        rvPopularAnime = view.findViewById(R.id.rvPopularAnime)
        ivHero = view.findViewById(R.id.ivHero)
        tvHeroTitle = view.findViewById(R.id.tvHeroTitle)
        tvHeroEpisode = view.findViewById(R.id.tvHeroEpisode)
        btnHeroPlay = view.findViewById(R.id.btnHeroPlay)
        btnHeroDetail = view.findViewById(R.id.btnHeroDetail)

        swipeRefresh.setColorSchemeResources(R.color.netflix_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.netflix_surface)

        setupRecyclerViews()

        swipeRefresh.setOnRefreshListener {
            loadData()
        }

        btnHeroPlay.setOnClickListener {
            heroEpisode?.let { ep ->
                val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
                intent.putExtra("url", ep.url)
                startActivity(intent)
            }
        }

        btnHeroDetail.setOnClickListener {
            heroEpisode?.let { ep ->
                val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
                intent.putExtra("url", ep.url)
                startActivity(intent)
            }
        }

        loadData()
    }

    private fun setupRecyclerViews() {
        latestAdapter = LatestEpisodeAdapter { episode ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", episode.url)
            startActivity(intent)
        }
        rvLatestEpisodes.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = latestAdapter
            isNestedScrollingEnabled = false
        }

        ongoingAdapter = AnimeAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }
        rvOngoingAnime.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = ongoingAdapter
            isNestedScrollingEnabled = false
        }

        popularAdapter = AnimeAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }
        rvPopularAnime.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = popularAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val scraper = WeebFlixApp.instance.scraper

                val latestDeferred = launch { scraper.getLatestEpisodes() }
                val ongoingDeferred = launch { scraper.getOngoingAnime() }
                val popularDeferred = launch { scraper.getPopularAnime() }

                val latest = latestDeferred.await()
                val ongoing = ongoingDeferred.await()
                val popular = popularDeferred.await()

                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false

                    if (latest.isNotEmpty()) {
                        heroEpisode = latest.first()
                        tvHeroTitle.text = heroEpisode?.title
                        tvHeroEpisode.text = "Episode ${heroEpisode?.episodeNumber} - ${heroEpisode?.uploadDate}"

                        if (heroEpisode?.imageUrl?.isNotEmpty() == true) {
                            Glide.with(requireContext())
                                .load(heroEpisode?.imageUrl)
                                .centerCrop()
                                .into(ivHero)
                        }
                    }

                    latestAdapter.submitList(latest)
                    ongoingAdapter.submitList(ongoing)
                    popularAdapter.submitList(popular)
                }
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
