package com.weebflix.app.ui.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.adapter.AnimeAdapter
import com.weebflix.app.ui.adapter.ContinueWatchingAdapter
import com.weebflix.app.ui.adapter.HeroPagerAdapter
import com.weebflix.app.ui.adapter.LatestEpisodeAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import com.weebflix.app.ui.detail.CategoryGridActivity
import com.weebflix.app.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OtakudesuHomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvOngoingAnime: RecyclerView
    private lateinit var rvPopularAnime: RecyclerView
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var continueWatchingSection: View
    private lateinit var headerLatestEpisodes: View
    private lateinit var headerOngoingAnime: View
    private lateinit var headerPopularAnime: View
    private lateinit var vpHero: ViewPager2
    private lateinit var dotContainer: LinearLayout

    private lateinit var latestAdapter: LatestEpisodeAdapter
    private lateinit var ongoingAdapter: AnimeAdapter
    private lateinit var popularAdapter: AnimeAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private val heroItems = mutableListOf<Anime>()

    private val latestItems = mutableListOf<Episode>()
    private val ongoingItems = mutableListOf<Anime>()
    private val popularItems = mutableListOf<Anime>()

    private var latestPage = 1
    private var ongoingPage = 1
    private var popularPage = 1

    private var latestLoading = false
    private var ongoingLoading = false
    private var popularLoading = false

    private var latestHasMore = true
    private var ongoingHasMore = true
    private var popularHasMore = true

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_samehadaku, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        rvLatestEpisodes = view.findViewById(R.id.rvLatestEpisodes)
        rvOngoingAnime = view.findViewById(R.id.rvOngoingAnime)
        rvPopularAnime = view.findViewById(R.id.rvPopularAnime)
        rvContinueWatching = view.findViewById(R.id.rvContinueWatching)
        continueWatchingSection = view.findViewById(R.id.continueWatchingSection)
        headerLatestEpisodes = view.findViewById(R.id.headerLatestEpisodes)
        headerOngoingAnime = view.findViewById(R.id.headerOngoingAnime)
        headerPopularAnime = view.findViewById(R.id.headerPopularAnime)
        vpHero = view.findViewById(R.id.vpHero)
        dotContainer = view.findViewById(R.id.dotContainer)

        swipeRefresh.setColorSchemeResources(R.color.netflix_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.netflix_surface)

        setupRecyclerViews()
        setupHero()

        swipeRefresh.setOnRefreshListener {
            resetAndLoad()
        }

        val openCategory = { cat: String, title: String ->
            startActivity(Intent(requireContext(), CategoryGridActivity::class.java).apply {
                putExtra(CategoryGridActivity.EXTRA_CATEGORY, cat)
                putExtra(CategoryGridActivity.EXTRA_TITLE, title)
            })
        }
        headerLatestEpisodes.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_EPISODES, "Eps Terbaru") }
        headerOngoingAnime.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_ONGOING, "Anime Ongoing") }
        headerPopularAnime.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_COMPLETED, "Anime Completed") }

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
        vpHero.isFocusable = true
        vpHero.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && heroItems.size > 1) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        vpHero.setCurrentItem((vpHero.currentItem + 1) % heroItems.size, true)
                        restartHeroAutoScroll()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        vpHero.setCurrentItem((vpHero.currentItem - 1 + heroItems.size) % heroItems.size, true)
                        restartHeroAutoScroll()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        val rv = vpHero.getChildAt(0) as? RecyclerView
                        val current = rv?.layoutManager?.findViewByPosition(vpHero.currentItem)
                        current?.performClick()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun restartHeroAutoScroll() {
        heroHandler.removeCallbacks(heroRunnable)
        heroHandler.postDelayed(heroRunnable, 4000L)
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

    private fun resetAndLoad() {
        latestItems.clear()
        ongoingItems.clear()
        popularItems.clear()
        latestPage = 1
        ongoingPage = 1
        popularPage = 1
        latestHasMore = true
        ongoingHasMore = true
        popularHasMore = true
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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !latestLoading && latestHasMore) {
                            loadMoreLatestEpisodes()
                        }
                    }
                }
            })
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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !ongoingLoading && ongoingHasMore) {
                            loadMoreOngoingAnime()
                        }
                    }
                }
            })
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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !popularLoading && popularHasMore) {
                            loadMorePopularAnime()
                        }
                    }
                }
            })
        }

        continueWatchingAdapter = ContinueWatchingAdapter { entry ->
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("url", entry.episodeUrl)
            intent.putExtra("title", entry.episodeTitle.ifEmpty { entry.episodeNumber })
            intent.putExtra("episodeNumber", entry.episodeNumber)
            intent.putExtra("animeTitle", entry.animeTitle)
            intent.putExtra("imageUrl", entry.imageUrl)
            intent.putExtra("animeUrl", entry.animeUrl)
            intent.putExtra("providerId", ProviderFactory.OTAKUDESU_ID)
            startActivity(intent)
        }
        rvContinueWatching.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = continueWatchingAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OTAKUDESU_ID)

                val diskCached = com.weebflix.app.data.model.ProviderDataCache.loadFromDisk(requireContext(), ProviderFactory.OTAKUDESU_ID)
                if (diskCached != null && isAdded) {
                    applyOtakudesuData(diskCached.hero, diskCached.latestEpisodes.map {
                        Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, diskCached.category1.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, diskCached.category2.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshOtakudesuData(provider) }
                    return@launch
                }

                val ghData = withContext(Dispatchers.IO) { com.weebflix.app.data.model.GitHubDataFetcher.fetchHomeData(ProviderFactory.OTAKUDESU_ID) }
                if (ghData != null && isAdded) {
                    applyOtakudesuData(ghData.hero, ghData.latestEpisodes.map {
                        Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, ghData.category1.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, ghData.category2.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshOtakudesuData(provider) }
                    return@launch
                }

                refreshOtakudesuData(provider)
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyOtakudesuData(hero: List<Anime>, latest: List<Episode>, ongoing: List<Anime>, popular: List<Anime>) {
        if (!isAdded) return
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        latestItems.clear(); latestItems.addAll(latest)
        ongoingItems.clear(); ongoingItems.addAll(ongoing)
        popularItems.clear(); popularItems.addAll(popular)
        latestHasMore = latest.isNotEmpty(); ongoingHasMore = ongoing.isNotEmpty(); popularHasMore = popular.isNotEmpty()
        latestPage = 1; ongoingPage = 1; popularPage = 1
        val heroList = if (hero.isNotEmpty()) hero else latest.take(10).map {
            Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber)
        }
        heroItems.clear(); heroItems.addAll(heroList)
        vpHero.adapter = HeroPagerAdapter(heroItems,
            onClick = { anime -> startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply { putExtra("url", anime.url) }) },
            onPlay = { anime -> startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("url", anime.url); putExtra("title", anime.title); putExtra("episodeNumber", anime.episode)
                putExtra("animeTitle", anime.title); putExtra("imageUrl", anime.imageUrl); putExtra("animeUrl", anime.url)
                putExtra("providerId", ProviderFactory.OTAKUDESU_ID)
            }) },
            onInfo = { anime -> startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply { putExtra("url", anime.url) }) }
        )
        if (heroList.size > 1) { vpHero.setCurrentItem(1, false); heroHandler.removeCallbacks(heroRunnable); heroHandler.postDelayed(heroRunnable, 4000L) }
        latestAdapter.submitList(latestItems.toList()); ongoingAdapter.submitList(ongoingItems.toList()); popularAdapter.submitList(popularItems.toList())
        loadContinueWatching()
    }

    private suspend fun refreshOtakudesuData(provider: com.weebflix.app.data.provider.AnimeProvider) {
        val latest = withContext(Dispatchers.IO) { provider.getLatestEpisodes(1) }
        val ongoing = withContext(Dispatchers.IO) { provider.getOngoingAnime(1) }
        val popular = withContext(Dispatchers.IO) { provider.getPopularAnime(1) }
        if (!isAdded) return
        val hero = latest.take(10).map { Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber) }
        withContext(Dispatchers.Main) { applyOtakudesuData(hero, latest, ongoing, popular) }
        val cacheData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = hero,
            latestEpisodes = latest.map { Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber, score = it.uploadDate) },
            category1 = ongoing, category2 = popular, category3 = emptyList(), category4 = emptyList()
        )
        com.weebflix.app.data.model.ProviderDataCache.cacheData(ProviderFactory.OTAKUDESU_ID, cacheData)
        com.weebflix.app.data.model.ProviderDataCache.saveToDisk(requireContext(), ProviderFactory.OTAKUDESU_ID, cacheData)
    }

    private fun loadContinueWatching() {
        if (!isAdded) return
        val entries = WatchHistoryManager.getAllByProvider(requireContext(), ProviderFactory.OTAKUDESU_ID)
            .filterNot { it.isFinished }
        if (entries.isNotEmpty()) {
            continueWatchingSection.visibility = View.VISIBLE
            continueWatchingAdapter.submitList(entries)
        } else {
            continueWatchingSection.visibility = View.GONE
        }
    }

    private fun loadMoreLatestEpisodes() {
        if (latestLoading || !latestHasMore) return
        latestLoading = true
        val nextPage = latestPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OTAKUDESU_ID)
                val newItems = withContext(Dispatchers.IO) { provider.getLatestEpisodes(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        latestHasMore = false
                    } else {
                        latestItems.addAll(newItems)
                        latestPage = nextPage
                        latestAdapter.submitList(latestItems.toList())
                    }
                    latestLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    latestHasMore = false
                    latestLoading = false
                }
            }
        }
    }

    private fun loadMoreOngoingAnime() {
        if (ongoingLoading || !ongoingHasMore) return
        ongoingLoading = true
        val nextPage = ongoingPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OTAKUDESU_ID)
                val newItems = withContext(Dispatchers.IO) { provider.getOngoingAnime(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        ongoingHasMore = false
                    } else {
                        ongoingItems.addAll(newItems)
                        ongoingPage = nextPage
                        ongoingAdapter.submitList(ongoingItems.toList())
                    }
                    ongoingLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    ongoingHasMore = false
                    ongoingLoading = false
                }
            }
        }
    }

    private fun loadMorePopularAnime() {
        if (popularLoading || !popularHasMore) return
        popularLoading = true
        val nextPage = popularPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OTAKUDESU_ID)
                val newItems = withContext(Dispatchers.IO) { provider.getPopularAnime(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        popularHasMore = false
                    } else {
                        popularItems.addAll(newItems)
                        popularPage = nextPage
                        popularAdapter.submitList(popularItems.toList())
                    }
                    popularLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    popularHasMore = false
                    popularLoading = false
                }
            }
        }
    }
}
