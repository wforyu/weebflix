package com.weebflix.app.ui.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.weebflix.app.R
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.DrakorKitaScraper
import com.weebflix.app.ui.adapter.ContinueWatchingAdapter
import com.weebflix.app.ui.adapter.HeroPagerAdapter
import com.weebflix.app.ui.adapter.NetflixCardAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import com.weebflix.app.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrakorKitaHomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var vpHero: ViewPager2
    private lateinit var dotContainer: LinearLayout
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvLatestMovies: RecyclerView
    private lateinit var rvLatestSeries: RecyclerView
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var continueWatchingSection: View
    private lateinit var headerEpsTerbaru: View
    private lateinit var headerMovieTerbaru: View
    private lateinit var headerSerieTerbaru: View

    private lateinit var episodesAdapter: NetflixCardAdapter
    private lateinit var moviesAdapter: NetflixCardAdapter
    private lateinit var seriesAdapter: NetflixCardAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private val heroItems = mutableListOf<Anime>()
    private val episodeItems = mutableListOf<Anime>()
    private val movieItems = mutableListOf<Anime>()
    private val seriesItems = mutableListOf<Anime>()

    private var moviesPage = 1
    private var seriesPage = 1
    private var moviesLoading = false
    private var seriesLoading = false
    private var moviesHasMore = true
    private var seriesHasMore = true

    private val heroHandler = Handler(Looper.getMainLooper())
    private val heroRunnable = object : Runnable {
        override fun run() {
            if (vpHero.adapter != null && heroItems.isNotEmpty()) {
                val next = (vpHero.currentItem + 1) % heroItems.size
                vpHero.currentItem = next
            }
            heroHandler.postDelayed(this, 4000L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home_drakorkita, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        scrollView = view.findViewById(R.id.scrollView)
        vpHero = view.findViewById(R.id.vpHero)
        dotContainer = view.findViewById(R.id.dotContainer)
        rvLatestEpisodes = view.findViewById(R.id.rvLatestEpisodes)
        rvLatestMovies = view.findViewById(R.id.rvLatestMovies)
        rvLatestSeries = view.findViewById(R.id.rvLatestSeries)
        rvContinueWatching = view.findViewById(R.id.rvContinueWatching)
        continueWatchingSection = view.findViewById(R.id.continueWatchingSection)
        headerEpsTerbaru = view.findViewById(R.id.headerEpsTerbaru)
        headerMovieTerbaru = view.findViewById(R.id.headerMovieTerbaru)
        headerSerieTerbaru = view.findViewById(R.id.headerSerieTerbaru)

        val openCategory = { cat: String, title: String ->
            startActivity(Intent(requireContext(), com.weebflix.app.ui.detail.CategoryGridActivity::class.java).apply {
                putExtra(com.weebflix.app.ui.detail.CategoryGridActivity.EXTRA_CATEGORY, cat)
                putExtra(com.weebflix.app.ui.detail.CategoryGridActivity.EXTRA_TITLE, title)
            })
        }
        headerEpsTerbaru.setOnClickListener { openCategory(com.weebflix.app.ui.detail.CategoryGridActivity.CATEGORY_EPISODES, "Eps Terbaru") }
        headerMovieTerbaru.setOnClickListener { openCategory(com.weebflix.app.ui.detail.CategoryGridActivity.CATEGORY_MOVIES, "Movie Terbaru") }
        headerSerieTerbaru.setOnClickListener { openCategory(com.weebflix.app.ui.detail.CategoryGridActivity.CATEGORY_SERIES, "Serie Terbaru") }

        swipeRefresh.setColorSchemeResources(R.color.netflix_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.netflix_surface)

        setupRecyclerViews()
        setupHero()

        swipeRefresh.setOnRefreshListener { loadData() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        heroHandler.postDelayed(heroRunnable, 4000L)
        if (::continueWatchingAdapter.isInitialized) loadContinueWatching()
    }

    override fun onPause() {
        super.onPause()
        heroHandler.removeCallbacks(heroRunnable)
    }

    private fun setupHero() {
        vpHero.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
    }

    private fun updateDots(position: Int) {
        dotContainer.removeAllViews()
        val count = heroItems.size.coerceAtMost(8)
        if (count <= 1) { dotContainer.visibility = View.GONE; return }
        dotContainer.visibility = View.VISIBLE
        for (i in 0 until count) {
            val dot = View(requireContext()).apply {
                val size = 8.dpToPx()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = if (i == 0) 0 else 4.dpToPx()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == position) 0xFFE50914.toInt() else 0x80FFFFFF.toInt())
                }
            }
            dotContainer.addView(dot)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupRecyclerViews() {
        val openDetail = { anime: Anime ->
            startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply {
                putExtra("url", anime.url)
            })
        }

        episodesAdapter = NetflixCardAdapter(openDetail)
        rvLatestEpisodes.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = episodesAdapter
            isNestedScrollingEnabled = false
        }

        moviesAdapter = NetflixCardAdapter(openDetail)
        rvLatestMovies.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !moviesLoading && moviesHasMore) {
                            loadMoreMovies()
                        }
                    }
                }
            })
        }

        seriesAdapter = NetflixCardAdapter(openDetail)
        rvLatestSeries.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !seriesLoading && seriesHasMore) {
                            loadMoreSeries()
                        }
                    }
                }
            })
        }

        continueWatchingAdapter = ContinueWatchingAdapter { entry ->
            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("url", entry.episodeUrl)
                putExtra("title", entry.episodeTitle.ifEmpty { entry.episodeNumber })
                putExtra("episodeNumber", entry.episodeNumber)
                putExtra("animeTitle", entry.animeTitle)
                putExtra("imageUrl", entry.imageUrl)
                putExtra("animeUrl", entry.animeUrl)
                putExtra("providerId", ProviderFactory.DRAKORKITA_ID)
            })
        }
        rvContinueWatching.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = continueWatchingAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.DRAKORKITA_ID) as DrakorKitaScraper

                val cached = com.weebflix.app.data.model.ProviderDataCache.getCachedData(ProviderFactory.DRAKORKITA_ID)
                if (cached != null && isAdded) {
                    applyDrakorKitaData(cached)
                    launch(Dispatchers.IO) { refreshDrakorKitaData(provider) }
                    return@launch
                }

                val diskCached = com.weebflix.app.data.model.ProviderDataCache.loadFromDisk(requireContext(), ProviderFactory.DRAKORKITA_ID)
                if (diskCached != null && isAdded) {
                    applyDrakorKitaData(diskCached)
                    launch(Dispatchers.IO) { refreshDrakorKitaData(provider) }
                    return@launch
                }

                val ghData = withContext(Dispatchers.IO) { com.weebflix.app.data.model.GitHubDataFetcher.fetchHomeData(ProviderFactory.DRAKORKITA_ID) }
                if (ghData != null && isAdded) {
                    applyDrakorKitaData(ghData)
                    launch(Dispatchers.IO) { refreshDrakorKitaData(provider) }
                    return@launch
                }

                refreshDrakorKitaData(provider)
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyDrakorKitaData(cached: com.weebflix.app.data.model.ProviderDataCache.CachedHomeData) {
        if (!isAdded) return
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        moviesPage = 1; seriesPage = 1; moviesHasMore = true; seriesHasMore = true
        heroItems.clear(); heroItems.addAll(cached.hero)
        episodeItems.clear(); episodeItems.addAll(cached.latestEpisodes)
        movieItems.clear(); movieItems.addAll(cached.category1)
        seriesItems.clear(); seriesItems.addAll(cached.category2)
        vpHero.adapter = HeroPagerAdapter(heroItems,
            onClick = { anime -> startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply { putExtra("url", anime.url) }) },
            onPlay = { anime -> startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("url", anime.url); putExtra("title", anime.title); putExtra("episodeNumber", anime.episode)
                putExtra("animeTitle", anime.title); putExtra("imageUrl", anime.imageUrl); putExtra("animeUrl", anime.url)
                putExtra("providerId", ProviderFactory.DRAKORKITA_ID)
            }) },
            onInfo = { anime -> startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply { putExtra("url", anime.url) }) }
        )
        if (heroItems.size > 1) { vpHero.setCurrentItem(1, false); heroHandler.removeCallbacks(heroRunnable); heroHandler.postDelayed(heroRunnable, 4000L) }
        episodesAdapter.submitList(episodeItems.toList()); moviesAdapter.submitList(movieItems.toList()); seriesAdapter.submitList(seriesItems.toList())
        loadContinueWatching()
    }

    private suspend fun refreshDrakorKitaData(provider: DrakorKitaScraper) {
        val home = withContext(Dispatchers.IO) { provider.getHomeContent() }
        if (!isAdded) return
        val cachedData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = home.featured,
            latestEpisodes = home.latestEpisodes.map { ep -> com.weebflix.app.data.model.Anime(title = ep.title, url = ep.url, imageUrl = ep.imageUrl, episode = ep.episodeNumber, score = ep.uploadDate) },
            category1 = home.movies, category2 = home.series, category3 = emptyList(), category4 = emptyList()
        )
        withContext(Dispatchers.Main) { applyDrakorKitaData(cachedData) }
        val cacheData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = home.featured,
            latestEpisodes = home.latestEpisodes.map { ep -> com.weebflix.app.data.model.Anime(title = ep.title, url = ep.url, imageUrl = ep.imageUrl, episode = ep.episodeNumber, score = ep.uploadDate) },
            category1 = home.movies, category2 = home.series, category3 = emptyList(), category4 = emptyList()
        )
        com.weebflix.app.data.model.ProviderDataCache.cacheData(ProviderFactory.DRAKORKITA_ID, cacheData)
        com.weebflix.app.data.model.ProviderDataCache.saveToDisk(requireContext(), ProviderFactory.DRAKORKITA_ID, cacheData)
    }

    private fun loadContinueWatching() {
        if (!isAdded) return
        val entries = WatchHistoryManager.getAllByProvider(requireContext(), ProviderFactory.DRAKORKITA_ID)
        if (entries.isNotEmpty()) {
            continueWatchingSection.visibility = View.VISIBLE
            continueWatchingAdapter.submitList(entries)
        } else {
            continueWatchingSection.visibility = View.GONE
        }
    }

    private fun loadMoreMovies() {
        if (moviesLoading || !moviesHasMore) return
        moviesLoading = true
        val nextPage = moviesPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.DRAKORKITA_ID) as DrakorKitaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getOngoingAnime(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        moviesHasMore = false
                    } else {
                        movieItems.addAll(newItems)
                        moviesPage = nextPage
                        moviesAdapter.submitList(movieItems.toList())
                    }
                    moviesLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    moviesHasMore = false
                    moviesLoading = false
                }
            }
        }
    }

    private fun loadMoreSeries() {
        if (seriesLoading || !seriesHasMore) return
        seriesLoading = true
        val nextPage = seriesPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.DRAKORKITA_ID) as DrakorKitaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getPopularAnime(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        seriesHasMore = false
                    } else {
                        seriesItems.addAll(newItems)
                        seriesPage = nextPage
                        seriesAdapter.submitList(seriesItems.toList())
                    }
                    seriesLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    seriesHasMore = false
                    seriesLoading = false
                }
            }
        }
    }
}
