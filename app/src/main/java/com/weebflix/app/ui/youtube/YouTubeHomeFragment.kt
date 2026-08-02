package com.weebflix.app.ui.youtube

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.weebflix.app.R
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.YouTubeScraper
import com.weebflix.app.data.scraper.YouTubeVideo
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.youtube.adapter.YouTubeFeedAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class YouTubeHomeFragment : Fragment() {

    private lateinit var ytFeed: RecyclerView
    private lateinit var ytError: TextView
    private lateinit var ytRefresh: SwipeRefreshLayout
    private lateinit var adapter: YouTubeFeedAdapter

    private val scraper by lazy { YouTubeScraper() }
    private var isLoading = false
    private var endReached = false
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_youtube, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ytFeed = view.findViewById(R.id.ytFeed)
        ytError = view.findViewById(R.id.ytError)
        ytRefresh = view.findViewById(R.id.ytRefresh)

        adapter = YouTubeFeedAdapter { video -> openVideo(video) }
        ytFeed.layoutManager = LinearLayoutManager(requireContext())
        ytFeed.adapter = adapter

        ytRefresh.setColorSchemeResources(R.color.netflix_red, R.color.white)
        ytRefresh.setOnRefreshListener { refreshFeed() }
        ytError.setOnClickListener { retry() }

        view.findViewById<ImageView>(R.id.ytBtnSearch).setOnClickListener {
            startActivity(Intent(requireContext(), YouTubeSearchActivity::class.java))
        }
        view.findViewById<ImageView>(R.id.ytBtnCast).setOnClickListener {
            Toast.makeText(requireContext(), "Cast belum tersedia di prototype", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<ImageView>(R.id.ytBtnNotifications).setOnClickListener {
            Toast.makeText(requireContext(), "Login diperlukan untuk notifikasi", Toast.LENGTH_SHORT).show()
        }

        ytFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 4) {
                    loadMore()
                }
            }
        })

        if (savedInstanceState == null) {
            loadMore()
        }
    }

    private fun loadMore() {
        if (isLoading || endReached || loadJob?.isActive == true) return
        isLoading = true
        val wasEmpty = adapter.isEmpty
        val job = lifecycleScope.launch {
            val page = try {
                scraper.nextFeedPage()
            } catch (e: Exception) {
                emptyList()
            }
            when {
                page.isNotEmpty() -> {
                    adapter.append(page, endOfFeed = false)
                    // When the list was empty (first load / after refresh), the RecyclerView
                    // pins the only visible view (the footer) as its scroll anchor, so inserting
                    // items above it leaves the viewport stuck at the END of the list. Force the
                    // feed back to the top so the newest items are visible immediately.
                    if (wasEmpty) ytFeed.scrollToPosition(0)
                }
                adapter.isEmpty -> {
                    endReached = true
                    adapter.setLoading()
                    ytError.visibility = View.VISIBLE
                    ytError.text = "Gagal memuat feed. Ketuk untuk coba lagi."
                }
                else -> {
                    endReached = true
                    adapter.setLoading()
                    adapter.append(emptyList(), endOfFeed = true)
                }
            }
            isLoading = false
        }
        loadJob = job
        // setLoading is posted so notifyItemChanged never fires from inside onScrolled
        // (RecyclerView "Cannot call this method in a scroll callback"). Only apply it if the
        // fetch hasn't already finished, otherwise a stale post would re-show the spinner.
        ytFeed.post {
            if (job.isActive) adapter.setLoading()
        }
    }

    private fun refreshFeed() {
        loadJob?.cancel()
        isLoading = false
        endReached = false
        ytError.visibility = View.GONE
        scraper.resetFeed()
        adapter.clear()
        ytRefresh.isRefreshing = false
        loadMore()
    }

    private fun retry() {
        endReached = false
        ytError.visibility = View.GONE
        loadMore()
    }

    private fun openVideo(video: YouTubeVideo) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("url", video.url)
            putExtra("title", video.title)
            putExtra("episodeNumber", "1")
            putExtra("animeTitle", video.title)
            putExtra("imageUrl", video.thumbnail)
            putExtra("animeUrl", video.url)
            putExtra("providerId", ProviderFactory.YOUTUBE_ID)
            putExtra("nextEpisodeUrl", "")
        }
        startActivity(intent)
    }
}
