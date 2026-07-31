package com.weebflix.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.weebflix.app.R
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.adapter.AnimeAdapter
import com.weebflix.app.ui.adapter.ContinueWatchingAdapter
import com.weebflix.app.ui.adapter.LatestEpisodeAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnichinHomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvOngoingAnime: RecyclerView
    private lateinit var rvCompletedAnime: RecyclerView
    private lateinit var rvAllAnime: RecyclerView
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var continueWatchingSection: View

    private lateinit var latestAdapter: LatestEpisodeAdapter
    private lateinit var ongoingAdapter: AnimeAdapter
    private lateinit var completedAdapter: AnimeAdapter
    private lateinit var allAnimeAdapter: AnimeAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private val latestItems = mutableListOf<Episode>()
    private val ongoingItems = mutableListOf<Anime>()
    private val completedItems = mutableListOf<Anime>()
    private val allAnimeItems = mutableListOf<Anime>()

    private var latestPage = 1
    private var ongoingPage = 1
    private var completedPage = 1
    private var allAnimePage = 1

    private var latestLoading = false
    private var ongoingLoading = false
    private var completedLoading = false
    private var allAnimeLoading = false

    private var latestHasMore = true
    private var ongoingHasMore = true
    private var completedHasMore = true
    private var allAnimeHasMore = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_anichin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        scrollView = view.findViewById(R.id.scrollView)
        rvLatestEpisodes = view.findViewById(R.id.rvLatestEpisodes)
        rvOngoingAnime = view.findViewById(R.id.rvOngoingAnime)
        rvCompletedAnime = view.findViewById(R.id.rvCompletedAnime)
        rvAllAnime = view.findViewById(R.id.rvAllAnime)
        rvContinueWatching = view.findViewById(R.id.rvContinueWatching)
        continueWatchingSection = view.findViewById(R.id.continueWatchingSection)

        swipeRefresh.setColorSchemeResources(R.color.netflix_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.netflix_surface)

        setupRecyclerViews()

        swipeRefresh.setOnRefreshListener {
            resetAndLoad()
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (::continueWatchingAdapter.isInitialized) loadContinueWatching()
    }

    private fun resetAndLoad() {
        latestItems.clear()
        ongoingItems.clear()
        completedItems.clear()
        allAnimeItems.clear()
        latestPage = 1
        ongoingPage = 1
        completedPage = 1
        allAnimePage = 1
        latestHasMore = true
        ongoingHasMore = true
        completedHasMore = true
        allAnimeHasMore = true
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

        completedAdapter = AnimeAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }
        rvCompletedAnime.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = completedAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !completedLoading && completedHasMore) {
                            loadMoreCompletedAnime()
                        }
                    }
                }
            })
        }

        allAnimeAdapter = AnimeAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }
        rvAllAnime.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = allAnimeAdapter
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dx > 0) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                        val total = lm.itemCount
                        if (lastVisible >= total - 3 && !allAnimeLoading && allAnimeHasMore) {
                            loadMoreAllAnime()
                        }
                    }
                }
            })
        }

        continueWatchingAdapter = ContinueWatchingAdapter { entry ->
            val intent = Intent(requireContext(), com.weebflix.app.ui.player.PlayerActivity::class.java)
            intent.putExtra("url", entry.episodeUrl)
            intent.putExtra("title", entry.episodeTitle.ifEmpty { entry.episodeNumber })
            intent.putExtra("episodeNumber", entry.episodeNumber)
            intent.putExtra("animeTitle", entry.animeTitle)
            intent.putExtra("imageUrl", entry.imageUrl)
            intent.putExtra("animeUrl", entry.animeUrl)
            intent.putExtra("providerId", ProviderFactory.ANICHIN_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.ANICHIN_ID)

                val cached = com.weebflix.app.data.model.ProviderDataCache.getCachedData(ProviderFactory.ANICHIN_ID)
                if (cached != null && isAdded) {
                    applyAnichinData(cached.latestEpisodes.map {
                        Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, cached.category1.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, cached.category2.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, cached.category3.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshAnichinData(provider) }
                    return@launch
                }

                val diskCached = com.weebflix.app.data.model.ProviderDataCache.loadFromDisk(requireContext(), ProviderFactory.ANICHIN_ID)
                if (diskCached != null && isAdded) {
                    applyAnichinData(diskCached.latestEpisodes.map {
                        Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, diskCached.category1.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, diskCached.category2.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, diskCached.category3.map {
                        Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshAnichinData(provider) }
                    return@launch
                }

                refreshAnichinData(provider)
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyAnichinData(latest: List<Episode>, ongoing: List<Anime>, completed: List<Anime>, allAnime: List<Anime> = emptyList()) {
        if (!isAdded) return
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        latestItems.clear(); latestItems.addAll(latest)
        ongoingItems.clear(); ongoingItems.addAll(ongoing)
        completedItems.clear(); completedItems.addAll(completed)
        allAnimeItems.clear(); allAnimeItems.addAll(allAnime)
        latestHasMore = latest.isNotEmpty()
        ongoingHasMore = ongoing.isNotEmpty()
        completedHasMore = completed.isNotEmpty()
        allAnimeHasMore = allAnime.isNotEmpty()
        latestPage = 1
        ongoingPage = 1
        completedPage = 1
        allAnimePage = 1
        latestAdapter.submitList(latestItems.toList())
        ongoingAdapter.submitList(ongoingItems.toList())
        completedAdapter.submitList(completedItems.toList())
        allAnimeAdapter.submitList(allAnimeItems.toList())
        loadContinueWatching()
    }

    private suspend fun refreshAnichinData(provider: com.weebflix.app.data.provider.AnimeProvider) {
        val latest = withContext(Dispatchers.IO) { provider.getLatestEpisodes(1) }
        val ongoing = withContext(Dispatchers.IO) { provider.getOngoingAnime(1) }
        val completed = withContext(Dispatchers.IO) { provider.getPopularAnime(1) }
        val allAnime = withContext(Dispatchers.IO) {
            if (provider is com.weebflix.app.data.scraper.AnichinScraper) provider.getAllAnime(1) else emptyList()
        }
        if (!isAdded) return
        withContext(Dispatchers.Main) { applyAnichinData(latest, ongoing, completed, allAnime) }
        val cacheData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = emptyList(),
            latestEpisodes = latest.map { Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber, score = it.uploadDate) },
            category1 = ongoing, category2 = completed, category3 = allAnime, category4 = emptyList()
        )
        com.weebflix.app.data.model.ProviderDataCache.cacheData(ProviderFactory.ANICHIN_ID, cacheData)
        com.weebflix.app.data.model.ProviderDataCache.saveToDisk(requireContext(), ProviderFactory.ANICHIN_ID, cacheData)
    }

    private fun loadContinueWatching() {
        if (!isAdded) return
        val entries = WatchHistoryManager.getAll(requireContext())
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
                val provider = ProviderFactory.getProvider(ProviderFactory.ANICHIN_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.ANICHIN_ID)
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

    private fun loadMoreAllAnime() {
        if (allAnimeLoading || !allAnimeHasMore) return
        allAnimeLoading = true
        val nextPage = allAnimePage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.ANICHIN_ID)
                val newItems = withContext(Dispatchers.IO) {
                    if (provider is com.weebflix.app.data.scraper.AnichinScraper) provider.getAllAnime(nextPage) else emptyList()
                }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        allAnimeHasMore = false
                    } else {
                        allAnimeItems.addAll(newItems)
                        allAnimePage = nextPage
                        allAnimeAdapter.submitList(allAnimeItems.toList())
                    }
                    allAnimeLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    allAnimeHasMore = false
                    allAnimeLoading = false
                }
            }
        }
    }

    private fun loadMoreCompletedAnime() {
        if (completedLoading || !completedHasMore) return
        completedLoading = true
        val nextPage = completedPage + 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val provider = ProviderFactory.getProvider(ProviderFactory.ANICHIN_ID)
                val newItems = withContext(Dispatchers.IO) { provider.getPopularAnime(nextPage) }
                if (isAdded) {
                    if (newItems.isEmpty()) {
                        completedHasMore = false
                    } else {
                        completedItems.addAll(newItems)
                        completedPage = nextPage
                        completedAdapter.submitList(completedItems.toList())
                    }
                    completedLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    completedHasMore = false
                    completedLoading = false
                }
            }
        }
    }
}
