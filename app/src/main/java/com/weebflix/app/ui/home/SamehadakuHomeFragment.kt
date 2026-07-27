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
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.adapter.AnimeAdapter
import com.weebflix.app.ui.adapter.ContinueWatchingAdapter
import com.weebflix.app.ui.adapter.LatestEpisodeAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import com.weebflix.app.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SamehadakuHomeFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var rvLatestEpisodes: RecyclerView
    private lateinit var rvOngoingAnime: RecyclerView
    private lateinit var rvPopularAnime: RecyclerView
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var continueWatchingSection: View
    private lateinit var ivHero: android.widget.ImageView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroEpisode: TextView
    private lateinit var btnHeroPlay: TextView

    private lateinit var latestAdapter: LatestEpisodeAdapter
    private lateinit var ongoingAdapter: AnimeAdapter
    private lateinit var popularAdapter: AnimeAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private var heroEpisode: Episode? = null

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
        scrollView = view.findViewById(R.id.scrollView)
        rvLatestEpisodes = view.findViewById(R.id.rvLatestEpisodes)
        rvOngoingAnime = view.findViewById(R.id.rvOngoingAnime)
        rvPopularAnime = view.findViewById(R.id.rvPopularAnime)
        rvContinueWatching = view.findViewById(R.id.rvContinueWatching)
        continueWatchingSection = view.findViewById(R.id.continueWatchingSection)
        ivHero = view.findViewById(R.id.ivHero)
        tvHeroTitle = view.findViewById(R.id.tvHeroTitle)
        tvHeroEpisode = view.findViewById(R.id.tvHeroEpisode)
        btnHeroPlay = view.findViewById(R.id.btnHeroPlay)

        swipeRefresh.setColorSchemeResources(R.color.netflix_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.netflix_surface)

        setupRecyclerViews()

        swipeRefresh.setOnRefreshListener {
            resetAndLoad()
        }

        btnHeroPlay.setOnClickListener {
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

        loadData()
    }

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
            intent.putExtra("providerId", com.weebflix.app.data.provider.ProviderFactory.SAMEHADAKU_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.SAMEHADAKU_ID)

                val cached = com.weebflix.app.data.model.ProviderDataCache.getCachedData(ProviderFactory.SAMEHADAKU_ID)
                if (cached != null && isAdded) {
                    applySamehadakuData(cached.latestEpisodes.map {
                        com.weebflix.app.data.model.Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, cached.category1.map {
                        com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, cached.category2.map {
                        com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshSamehadakuData(provider) }
                    return@launch
                }

                val diskCached = com.weebflix.app.data.model.ProviderDataCache.loadFromDisk(requireContext(), ProviderFactory.SAMEHADAKU_ID)
                if (diskCached != null && isAdded) {
                    applySamehadakuData(diskCached.latestEpisodes.map {
                        com.weebflix.app.data.model.Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, diskCached.category1.map {
                        com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, diskCached.category2.map {
                        com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshSamehadakuData(provider) }
                    return@launch
                }

                val ghData = withContext(Dispatchers.IO) { com.weebflix.app.data.model.GitHubDataFetcher.fetchHomeData(ProviderFactory.SAMEHADAKU_ID) }
                if (ghData != null && isAdded) {
                    applySamehadakuData(ghData.latestEpisodes.map {
                        com.weebflix.app.data.model.Episode(title = it.title, url = it.url, imageUrl = it.imageUrl, episodeNumber = it.episode, uploadDate = it.score)
                    }, ghData.category1.map {
                        com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    }, ghData.category2.map {
                        com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episode, type = it.type, score = it.score)
                    })
                    launch(Dispatchers.IO) { refreshSamehadakuData(provider) }
                    return@launch
                }

                refreshSamehadakuData(provider)
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applySamehadakuData(latest: List<com.weebflix.app.data.model.Episode>, ongoing: List<com.weebflix.app.data.model.Anime>, popular: List<com.weebflix.app.data.model.Anime>) {
        if (!isAdded) return
        loadingLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        latestItems.clear(); latestItems.addAll(latest)
        ongoingItems.clear(); ongoingItems.addAll(ongoing)
        popularItems.clear(); popularItems.addAll(popular)
        latestHasMore = latest.isNotEmpty(); ongoingHasMore = ongoing.isNotEmpty(); popularHasMore = popular.isNotEmpty()
        latestPage = 1; ongoingPage = 1; popularPage = 1
        if (latestItems.isNotEmpty()) {
            heroEpisode = latestItems.first()
            tvHeroTitle.text = heroEpisode?.title
            val epNum = heroEpisode?.episodeNumber?.takeIf { it.isNotEmpty() }
            val epDate = heroEpisode?.uploadDate?.takeIf { it.isNotEmpty() }
            tvHeroEpisode.text = when { epNum != null && epDate != null -> "Episode $epNum - $epDate"; epNum != null -> "Episode $epNum"; else -> "" }
            if (heroEpisode?.imageUrl?.isNotEmpty() == true) { Glide.with(requireContext()).load(heroEpisode?.imageUrl).centerCrop().into(ivHero) }
        }
        latestAdapter.submitList(latestItems.toList()); ongoingAdapter.submitList(ongoingItems.toList()); popularAdapter.submitList(popularItems.toList())
        loadContinueWatching()
    }

    private suspend fun refreshSamehadakuData(provider: com.weebflix.app.data.provider.AnimeProvider) {
        val latest = withContext(Dispatchers.IO) { provider.getLatestEpisodes(1) }
        val ongoing = withContext(Dispatchers.IO) { provider.getOngoingAnime(1) }
        val popular = withContext(Dispatchers.IO) { provider.getPopularAnime(1) }
        if (!isAdded) return
        withContext(Dispatchers.Main) { applySamehadakuData(latest, ongoing, popular) }
        val cacheData = com.weebflix.app.data.model.ProviderDataCache.CachedHomeData(
            hero = latest.take(10).map { com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber) },
            latestEpisodes = latest.map { com.weebflix.app.data.model.Anime(title = it.title, url = it.url, imageUrl = it.imageUrl, episode = it.episodeNumber, score = it.uploadDate) },
            category1 = ongoing, category2 = popular, category3 = emptyList(), category4 = emptyList()
        )
        com.weebflix.app.data.model.ProviderDataCache.cacheData(ProviderFactory.SAMEHADAKU_ID, cacheData)
        com.weebflix.app.data.model.ProviderDataCache.saveToDisk(requireContext(), ProviderFactory.SAMEHADAKU_ID, cacheData)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.SAMEHADAKU_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.SAMEHADAKU_ID)
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
                val provider = ProviderFactory.getProvider(ProviderFactory.SAMEHADAKU_ID)
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
