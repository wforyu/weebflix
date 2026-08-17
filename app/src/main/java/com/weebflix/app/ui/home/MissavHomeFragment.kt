package com.weebflix.app.ui.home

import android.content.Intent
import android.os.Bundle
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
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.adapter.ContinueWatchingAdapter
import com.weebflix.app.ui.adapter.NetflixCardAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import com.weebflix.app.ui.detail.CategoryGridActivity
import com.weebflix.app.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MissavHomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvOngoingAnime: RecyclerView
    private lateinit var rvPopularAnime: RecyclerView
    private lateinit var rvUncensored: RecyclerView
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var continueWatchingSection: View
    private lateinit var headerLatestEpisodes: View
    private lateinit var headerOngoingAnime: View
    private lateinit var headerPopularAnime: View
    private lateinit var headerUncensored: View
    private lateinit var sectionUncensored: View
    private lateinit var ivHero: android.widget.ImageView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroEpisode: TextView

    private lateinit var latestAdapter: NetflixCardAdapter
    private lateinit var ongoingAdapter: NetflixCardAdapter
    private lateinit var popularAdapter: NetflixCardAdapter
    private lateinit var uncensoredAdapter: NetflixCardAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private var heroEpisode: Episode? = null

    private val latestItems = mutableListOf<Anime>()
    private val ongoingItems = mutableListOf<Anime>()
    private val popularItems = mutableListOf<Anime>()
    private val uncensoredItems = mutableListOf<Anime>()

    private var latestPage = 1
    private var ongoingPage = 1
    private var popularPage = 1
    private var uncensoredPage = 1

    private var latestLoading = false
    private var ongoingLoading = false
    private var popularLoading = false
    private var uncensoredLoading = false

    private var latestHasMore = true
    private var ongoingHasMore = true
    private var popularHasMore = true
    private var uncensoredHasMore = true

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
        rvUncensored = view.findViewById(R.id.rvUncensored)
        rvContinueWatching = view.findViewById(R.id.rvContinueWatching)
        continueWatchingSection = view.findViewById(R.id.continueWatchingSection)
        headerLatestEpisodes = view.findViewById(R.id.headerLatestEpisodes)
        headerOngoingAnime = view.findViewById(R.id.headerOngoingAnime)
        headerPopularAnime = view.findViewById(R.id.headerPopularAnime)
        headerUncensored = view.findViewById(R.id.headerUncensored)
        sectionUncensored = view.findViewById(R.id.sectionUncensored)
        ivHero = view.findViewById(R.id.ivHero)
        tvHeroTitle = view.findViewById(R.id.tvHeroTitle)
        tvHeroEpisode = view.findViewById(R.id.tvHeroEpisode)

        swipeRefresh.setColorSchemeResources(R.color.netflix_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.netflix_surface)

        setupRecyclerViews()

        swipeRefresh.setOnRefreshListener {
            resetAndLoad()
        }

        view.findViewById<View>(R.id.btnHeroPlay)?.setOnClickListener {
            heroEpisode?.let { ep ->
                val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
                intent.putExtra("url", ep.url)
                startActivity(intent)
            }
        }

        view.findViewById<View>(R.id.btnHeroDetail)?.setOnClickListener {
            heroEpisode?.let { ep ->
                val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
                intent.putExtra("url", ep.url)
                startActivity(intent)
            }
        }

        val openCategory = { cat: String, title: String ->
            startActivity(Intent(requireContext(), CategoryGridActivity::class.java).apply {
                putExtra(CategoryGridActivity.EXTRA_CATEGORY, cat)
                putExtra(CategoryGridActivity.EXTRA_TITLE, title)
            })
        }
        headerLatestEpisodes.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_EPISODES, "Rilis Terbaru") }
        headerOngoingAnime.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_ONGOING, "Update Terbaru") }
        headerPopularAnime.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_POPULAR, "Populer Mingguan") }
        headerUncensored.setOnClickListener { openCategory(CategoryGridActivity.CATEGORY_UNCENSORED, "Uncensored") }

        loadData()
    }

    private fun resetAndLoad() {
        latestItems.clear()
        ongoingItems.clear()
        popularItems.clear()
        uncensoredItems.clear()
        latestPage = 1
        ongoingPage = 1
        popularPage = 1
        uncensoredPage = 1
        latestHasMore = true
        ongoingHasMore = true
        popularHasMore = true
        uncensoredHasMore = true
        sectionUncensored.visibility = View.GONE
        loadData()
    }

    private fun setupRecyclerViews() {
        latestAdapter = NetflixCardAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
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

        ongoingAdapter = NetflixCardAdapter { anime ->
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

        popularAdapter = NetflixCardAdapter { anime ->
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

        uncensoredAdapter = NetflixCardAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }
        rvUncensored.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = uncensoredAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !uncensoredLoading && uncensoredHasMore) {
                            loadMoreUncensored()
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
            intent.putExtra("providerId", ProviderFactory.MISSAV_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.MISSAV_ID)

                val cached = com.weebflix.app.data.model.ProviderDataCache.getCachedData(ProviderFactory.MISSAV_ID)
                if (cached != null && isAdded) {
                    applyMissavData(cached.latestEpisodes.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = "JAV")
                    }, cached.category1.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = "JAV")
                    }, cached.category2.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = "JAV")
                    }, emptyList())
                    launch(Dispatchers.IO) { refreshMissavData(provider) }
                    return@launch
                }

                refreshMissavData(provider)
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyMissavData(latest: List<Anime>, ongoing: List<Anime>, popular: List<Anime>, uncensored: List<Anime>) {
        if (!isAdded) return
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        latestItems.clear(); latestItems.addAll(latest)
        ongoingItems.clear(); ongoingItems.addAll(ongoing)
        popularItems.clear(); popularItems.addAll(popular)
        uncensoredItems.clear(); uncensoredItems.addAll(uncensored)
        latestHasMore = latest.isNotEmpty(); ongoingHasMore = ongoing.isNotEmpty(); popularHasMore = popular.isNotEmpty(); uncensoredHasMore = uncensored.isNotEmpty()
        latestPage = 1; ongoingPage = 1; popularPage = 1; uncensoredPage = 1
        if (uncensored.isNotEmpty()) { sectionUncensored.visibility = View.VISIBLE }
        if (latestItems.isNotEmpty()) {
            heroEpisode = Episode(
                title = latestItems.first().title,
                url = latestItems.first().url,
                imageUrl = latestItems.first().imageUrl,
                episodeNumber = latestItems.first().episode,
                uploadDate = ""
            )
            tvHeroTitle.text = heroEpisode?.title
            val epNum = heroEpisode?.episodeNumber?.takeIf { it.isNotEmpty() }
            tvHeroEpisode.text = if (epNum != null) "Durasi $epNum" else ""
            if (heroEpisode?.imageUrl?.isNotEmpty() == true) { Glide.with(requireContext()).load(heroEpisode?.imageUrl).centerCrop().into(ivHero) }
        }
        latestAdapter.submitList(latestItems.toList()); ongoingAdapter.submitList(ongoingItems.toList()); popularAdapter.submitList(popularItems.toList()); uncensoredAdapter.submitList(uncensoredItems.toList())
        loadContinueWatching()
    }

    private suspend fun refreshMissavData(provider: com.weebflix.app.data.provider.AnimeProvider) {
        val latest = withContext(Dispatchers.IO) { provider.getLatestEpisodes(1).map {
            Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episodeNumber, uploadDate = it.uploadDate)
        } }
        val ongoing = withContext(Dispatchers.IO) { provider.getOngoingAnime(1) }
        val popular = withContext(Dispatchers.IO) { provider.getPopularAnime(1) }
        val uncensored = withContext(Dispatchers.IO) {
            (provider as? com.weebflix.app.data.scraper.MissavScraper)?.getUncensoredAnime(1) ?: emptyList()
        }
        if (!isAdded) return
        withContext(Dispatchers.Main) { applyMissavData(latest.map {
            Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber, type = "JAV")
        }, ongoing, popular, uncensored) }
        val cacheData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = latest.map { Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber) },
            latestEpisodes = latest.map { Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber, score = it.uploadDate) },
            category1 = ongoing, category2 = popular, category3 = emptyList(), category4 = emptyList()
        )
        com.weebflix.app.data.model.ProviderDataCache.cacheData(ProviderFactory.MISSAV_ID, cacheData)
        com.weebflix.app.data.model.ProviderDataCache.saveToDisk(requireContext(), ProviderFactory.MISSAV_ID, cacheData)
    }

    private fun loadContinueWatching() {
        if (!isAdded) return
        val entries = WatchHistoryManager.getAllByProvider(requireContext(), ProviderFactory.MISSAV_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.MISSAV_ID)
                val newItems = withContext(Dispatchers.IO) { provider.getLatestEpisodes(nextPage).map {
                    Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber, type = "JAV")
                } }
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
                val provider = ProviderFactory.getProvider(ProviderFactory.MISSAV_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.MISSAV_ID)
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

    private fun loadMoreUncensored() {
        if (uncensoredLoading || !uncensoredHasMore) return
        uncensoredLoading = true
        val nextPage = uncensoredPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.MISSAV_ID)
                val newItems = withContext(Dispatchers.IO) {
                    (provider as? com.weebflix.app.data.scraper.MissavScraper)?.getUncensoredAnime(nextPage) ?: emptyList()
                }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        uncensoredHasMore = false
                    } else {
                        uncensoredItems.addAll(newItems)
                        uncensoredPage = nextPage
                        uncensoredAdapter.submitList(uncensoredItems.toList())
                    }
                    uncensoredLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    uncensoredHasMore = false
                    uncensoredLoading = false
                }
            }
        }
    }
}
