package com.weebflix.app.ui.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import com.weebflix.app.data.scraper.OppaDramaScraper
import com.weebflix.app.ui.adapter.ContinueWatchingAdapter
import com.weebflix.app.ui.adapter.HeroPagerAdapter
import com.weebflix.app.ui.adapter.NetflixCardAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.detail.CategoryGridActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OppaDramaHomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var vpHero: ViewPager2
    private lateinit var dotContainer: LinearLayout
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvDramaKorea: RecyclerView
    private lateinit var rvDramaChina: RecyclerView
    private lateinit var rvFilmKorea: RecyclerView
    private lateinit var rvNetflix: RecyclerView
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var continueWatchingSection: View
    private lateinit var headerEpsTerbaru: View
    private lateinit var headerDramaKorea: View
    private lateinit var headerDramaChina: View
    private lateinit var headerFilmKorea: View
    private lateinit var headerNetflix: View

    private lateinit var episodesAdapter: NetflixCardAdapter
    private lateinit var dramaKoreaAdapter: NetflixCardAdapter
    private lateinit var dramaChinaAdapter: NetflixCardAdapter
    private lateinit var filmKoreaAdapter: NetflixCardAdapter
    private lateinit var netflixAdapter: NetflixCardAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private val heroItems = mutableListOf<Anime>()
    private val episodeItems = mutableListOf<Anime>()
    private val dramaKoreaItems = mutableListOf<Anime>()
    private val dramaChinaItems = mutableListOf<Anime>()
    private val filmKoreaItems = mutableListOf<Anime>()
    private val netflixItems = mutableListOf<Anime>()

    private var epsPage = 1
    private var dramaKoreaPage = 1
    private var dramaChinaPage = 1
    private var filmKoreaPage = 1
    private var netflixPage = 1
    private var epsLoading = false
    private var dramaKoreaLoading = false
    private var dramaChinaLoading = false
    private var filmKoreaLoading = false
    private var netflixLoading = false
    private var epsHasMore = true
    private var dramaKoreaHasMore = true
    private var dramaChinaHasMore = true
    private var filmKoreaHasMore = true
    private var netflixHasMore = true

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
        return inflater.inflate(R.layout.fragment_home_oppadrama, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        scrollView = view.findViewById(R.id.scrollView)
        vpHero = view.findViewById(R.id.vpHero)
        dotContainer = view.findViewById(R.id.dotContainer)
        rvLatestEpisodes = view.findViewById(R.id.rvLatestEpisodes)
        rvDramaKorea = view.findViewById(R.id.rvDramaKorea)
        rvDramaChina = view.findViewById(R.id.rvDramaChina)
        rvFilmKorea = view.findViewById(R.id.rvFilmKorea)
        rvNetflix = view.findViewById(R.id.rvNetflix)
        rvContinueWatching = view.findViewById(R.id.rvContinueWatching)
        continueWatchingSection = view.findViewById(R.id.continueWatchingSection)
        headerEpsTerbaru = view.findViewById(R.id.headerEpsTerbaru)
        headerDramaKorea = view.findViewById(R.id.headerDramaKorea)
        headerDramaChina = view.findViewById(R.id.headerDramaChina)
        headerFilmKorea = view.findViewById(R.id.headerFilmKorea)
        headerNetflix = view.findViewById(R.id.headerNetflix)

        val openCategory = { cat: String, title: String ->
            startActivity(Intent(requireContext(), CategoryGridActivity::class.java).apply {
                putExtra(CategoryGridActivity.EXTRA_CATEGORY, cat)
                putExtra(CategoryGridActivity.EXTRA_TITLE, title)
            })
        }
        headerEpsTerbaru.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_EPISODES, "Eps Terbaru") }
        headerDramaKorea.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_DRAMA_KOREA, "Drama Korea") }
        headerDramaChina.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_DRAMA_CHINA, "Drama China") }
        headerFilmKorea.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_FILM_KOREA, "Film Korea") }
        headerNetflix.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_NETFLIX, "Netflix") }

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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        if (lm.findLastCompletelyVisibleItemPosition() >= lm.itemCount - 3 && !epsLoading && epsHasMore) {
                            loadMoreEps()
                        }
                    }
                }
            })
        }

        dramaKoreaAdapter = NetflixCardAdapter(openDetail)
        rvDramaKorea.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = dramaKoreaAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        if (lm.findLastCompletelyVisibleItemPosition() >= lm.itemCount - 3 && !dramaKoreaLoading && dramaKoreaHasMore) {
                            loadMoreDramaKorea()
                        }
                    }
                }
            })
        }

        dramaChinaAdapter = NetflixCardAdapter(openDetail)
        rvDramaChina.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = dramaChinaAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        if (lm.findLastCompletelyVisibleItemPosition() >= lm.itemCount - 3 && !dramaChinaLoading && dramaChinaHasMore) {
                            loadMoreDramaChina()
                        }
                    }
                }
            })
        }

        filmKoreaAdapter = NetflixCardAdapter(openDetail)
        rvFilmKorea.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = filmKoreaAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        if (lm.findLastCompletelyVisibleItemPosition() >= lm.itemCount - 3 && !filmKoreaLoading && filmKoreaHasMore) {
                            loadMoreFilmKorea()
                        }
                    }
                }
            })
        }

        netflixAdapter = NetflixCardAdapter(openDetail)
        rvNetflix.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = netflixAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        if (lm.findLastCompletelyVisibleItemPosition() >= lm.itemCount - 3 && !netflixLoading && netflixHasMore) {
                            loadMoreNetflix()
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
                putExtra("providerId", ProviderFactory.OPPADRAMA_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.OPPADRAMA_ID) as OppaDramaScraper

                val cached = com.weebflix.app.data.model.ProviderDataCache.getCachedData(ProviderFactory.OPPADRAMA_ID)
                if (cached != null && isAdded) {
                    applyOppaData(cached)
                    launch(Dispatchers.IO) { refreshOppaData(provider) }
                    return@launch
                }

                val diskCached = com.weebflix.app.data.model.ProviderDataCache.loadFromDisk(requireContext(), ProviderFactory.OPPADRAMA_ID)
                if (diskCached != null && isAdded) {
                    applyOppaData(diskCached)
                    launch(Dispatchers.IO) { refreshOppaData(provider) }
                    return@launch
                }

                val ghData = withContext(Dispatchers.IO) { com.weebflix.app.data.model.GitHubDataFetcher.fetchHomeData(ProviderFactory.OPPADRAMA_ID) }
                if (ghData != null && isAdded) {
                    applyOppaData(ghData)
                    launch(Dispatchers.IO) { refreshOppaData(provider) }
                    return@launch
                }

                refreshOppaData(provider)
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyOppaData(cached: com.weebflix.app.data.model.ProviderDataCache.CachedHomeData) {
        if (!isAdded) return
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        epsPage = 1; dramaKoreaPage = 1; dramaChinaPage = 1; filmKoreaPage = 1; netflixPage = 1
        epsHasMore = true; dramaKoreaHasMore = true; dramaChinaHasMore = true; filmKoreaHasMore = true; netflixHasMore = true
        heroItems.clear(); heroItems.addAll(cached.hero)
        episodeItems.clear(); episodeItems.addAll(cached.latestEpisodes)
        dramaKoreaItems.clear(); dramaKoreaItems.addAll(cached.category1)
        dramaChinaItems.clear(); dramaChinaItems.addAll(cached.category2)
        filmKoreaItems.clear(); filmKoreaItems.addAll(cached.category3)
        netflixItems.clear(); netflixItems.addAll(cached.category4)
        vpHero.adapter = HeroPagerAdapter(heroItems,
            onClick = { anime -> startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply { putExtra("url", anime.url) }) },
            onPlay = { anime -> startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("url", anime.url); putExtra("title", anime.title); putExtra("episodeNumber", anime.episode)
                putExtra("animeTitle", anime.title); putExtra("imageUrl", anime.imageUrl); putExtra("animeUrl", anime.url)
                putExtra("providerId", ProviderFactory.OPPADRAMA_ID)
            }) },
            onInfo = { anime -> startActivity(Intent(requireContext(), AnimeDetailActivity::class.java).apply { putExtra("url", anime.url) }) }
        )
        if (heroItems.size > 1) { vpHero.setCurrentItem(1, false); heroHandler.removeCallbacks(heroRunnable); heroHandler.postDelayed(heroRunnable, 4000L) }
        episodesAdapter.submitList(episodeItems.toList()); dramaKoreaAdapter.submitList(dramaKoreaItems.toList())
        dramaChinaAdapter.submitList(dramaChinaItems.toList()); filmKoreaAdapter.submitList(filmKoreaItems.toList())
        netflixAdapter.submitList(netflixItems.toList())
        loadContinueWatching()
    }

    private suspend fun refreshOppaData(provider: OppaDramaScraper) {
        val home = withContext(Dispatchers.IO) { provider.getHomeContent() }
        val dramaKorea = withContext(Dispatchers.IO) { provider.getDramaKorea() }
        val dramaChina = withContext(Dispatchers.IO) { provider.getDramaChina() }
        val filmKorea = withContext(Dispatchers.IO) { provider.getFilmKorea() }
        val netflix = withContext(Dispatchers.IO) { provider.getNetflix() }
        if (!isAdded) return
        withContext(Dispatchers.Main) {
            applyOppaData(com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
                hero = home.featured, latestEpisodes = home.latestEpisodes,
                category1 = dramaKorea, category2 = dramaChina, category3 = filmKorea, category4 = netflix
            ))
        }
        val cacheData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = home.featured, latestEpisodes = home.latestEpisodes,
            category1 = dramaKorea, category2 = dramaChina, category3 = filmKorea, category4 = netflix
        )
        com.weebflix.app.data.model.ProviderDataCache.cacheData(ProviderFactory.OPPADRAMA_ID, cacheData)
        com.weebflix.app.data.model.ProviderDataCache.saveToDisk(requireContext(), ProviderFactory.OPPADRAMA_ID, cacheData)
    }

    private fun loadContinueWatching() {
        if (!isAdded) return
        val entries = WatchHistoryManager.getAllByProvider(requireContext(), ProviderFactory.OPPADRAMA_ID)
            .filterNot { it.isFinished }
        if (entries.isNotEmpty()) {
            continueWatchingSection.visibility = View.VISIBLE
            continueWatchingAdapter.submitList(entries)
        } else {
            continueWatchingSection.visibility = View.GONE
        }
    }

    private fun loadMoreEps() {
        if (epsLoading || !epsHasMore) return
        epsLoading = true
        val nextPage = epsPage + 1
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OPPADRAMA_ID) as OppaDramaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getLatestEpisodes(nextPage).map { ep -> Anime(title = ep.title, url = ep.url, imageUrl = ep.imageUrl, episode = ep.episodeNumber, score = ep.uploadDate) } }
                if (isAdded) {
                    if (newItems.isEmpty()) epsHasMore = false
                    else { episodeItems.addAll(newItems); epsPage = nextPage; episodesAdapter.submitList(episodeItems.toList()) }
                    epsLoading = false
                }
            } catch (e: Exception) { if (isAdded) { epsHasMore = false; epsLoading = false } }
        }
    }

    private fun loadMoreDramaKorea() {
        if (dramaKoreaLoading || !dramaKoreaHasMore) return
        dramaKoreaLoading = true
        val nextPage = dramaKoreaPage + 1
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OPPADRAMA_ID) as OppaDramaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getDramaKorea(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) dramaKoreaHasMore = false
                    else { dramaKoreaItems.addAll(newItems); dramaKoreaPage = nextPage; dramaKoreaAdapter.submitList(dramaKoreaItems.toList()) }
                    dramaKoreaLoading = false
                }
            } catch (e: Exception) { if (isAdded) { dramaKoreaHasMore = false; dramaKoreaLoading = false } }
        }
    }

    private fun loadMoreDramaChina() {
        if (dramaChinaLoading || !dramaChinaHasMore) return
        dramaChinaLoading = true
        val nextPage = dramaChinaPage + 1
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OPPADRAMA_ID) as OppaDramaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getDramaChina(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) dramaChinaHasMore = false
                    else { dramaChinaItems.addAll(newItems); dramaChinaPage = nextPage; dramaChinaAdapter.submitList(dramaChinaItems.toList()) }
                    dramaChinaLoading = false
                }
            } catch (e: Exception) { if (isAdded) { dramaChinaHasMore = false; dramaChinaLoading = false } }
        }
    }

    private fun loadMoreFilmKorea() {
        if (filmKoreaLoading || !filmKoreaHasMore) return
        filmKoreaLoading = true
        val nextPage = filmKoreaPage + 1
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OPPADRAMA_ID) as OppaDramaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getFilmKorea(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) filmKoreaHasMore = false
                    else { filmKoreaItems.addAll(newItems); filmKoreaPage = nextPage; filmKoreaAdapter.submitList(filmKoreaItems.toList()) }
                    filmKoreaLoading = false
                }
            } catch (e: Exception) { if (isAdded) { filmKoreaHasMore = false; filmKoreaLoading = false } }
        }
    }

    private fun loadMoreNetflix() {
        if (netflixLoading || !netflixHasMore) return
        netflixLoading = true
        val nextPage = netflixPage + 1
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.OPPADRAMA_ID) as OppaDramaScraper
                val newItems = withContext(Dispatchers.IO) { provider.getNetflix(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) netflixHasMore = false
                    else { netflixItems.addAll(newItems); netflixPage = nextPage; netflixAdapter.submitList(netflixItems.toList()) }
                    netflixLoading = false
                }
            } catch (e: Exception) { if (isAdded) { netflixHasMore = false; netflixLoading = false } }
        }
    }
}
