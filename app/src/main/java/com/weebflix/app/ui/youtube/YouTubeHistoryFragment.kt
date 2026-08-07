package com.weebflix.app.ui.youtube

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.model.WatchHistoryEntry
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.YouTubeDataApi
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.youtube.adapter.YouTubeHistoryAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** YouTube history tab: videos watched but not finished, resumable from last position.
 *  When logged in, server-side watch history (Data API playlistId=HL) is merged on top.
 *  Shown on the Ongoing tab position only while the YouTube provider is active. */
class YouTubeHistoryFragment : Fragment() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvHistorySubtitle: TextView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var adapter: YouTubeHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_youtube_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvHistory = view.findViewById(R.id.rvHistory)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvHistorySubtitle = view.findViewById(R.id.tvHistorySubtitle)
        loadingLayout = view.findViewById(R.id.loadingLayout)

        adapter = YouTubeHistoryAdapter { entry -> openVideo(entry) }
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) loadHistory()
    }

    private fun loadHistory() {
        if (!::adapter.isInitialized) return
        loadingLayout.visibility = View.VISIBLE
        val local = WatchHistoryManager.getAllByProvider(requireContext(), ProviderFactory.YOUTUBE_ID)
            .filterNot { it.isFinished }
        viewLifecycleOwner.lifecycleScope.launch {
            val server = if (YouTubeAuthManager.isLoggedIn()) {
                try {
                    withContext(Dispatchers.IO) { YouTubeDataApi.getWatchHistory() }
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            if (server.isNotEmpty()) {
                val serverEntries = server.map { item ->
                    WatchHistoryEntry(
                        episodeUrl = "youtube://${item.video.videoId}",
                        animeTitle = item.video.title,
                        episodeTitle = item.video.title,
                        imageUrl = item.video.thumbnail,
                        animeUrl = item.video.url,
                        providerId = ProviderFactory.YOUTUBE_ID,
                        timestamp = item.watchedAtMs
                    )
                }
                val byUrl = linkedMapOf<String, WatchHistoryEntry>()
                serverEntries.forEach { e -> byUrl.putIfAbsent(e.episodeUrl, e) }
                local.forEach { e -> byUrl[e.episodeUrl] = e }
                adapter.submitList(byUrl.values.sortedByDescending { it.timestamp })
            } else {
                adapter.submitList(local)
            }
            loadingLayout.visibility = View.GONE
            val empty = adapter.itemCount == 0
            tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            tvHistorySubtitle.text = if (YouTubeAuthManager.isLoggedIn()) {
                if (server.isNotEmpty()) {
                    "Diperbarui dari akun " + YouTubeAuthManager.email().substringBefore('@')
                } else {
                    "Riwayat dari akun Google + tontonan di perangkat ini"
                }
            } else {
                getString(R.string.continue_watching)
            }
        }
    }

    private fun openVideo(entry: WatchHistoryEntry) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("url", entry.episodeUrl)
            putExtra("title", entry.episodeTitle.ifEmpty { entry.animeTitle })
            putExtra("episodeNumber", "1")
            putExtra("animeTitle", entry.animeTitle)
            putExtra("imageUrl", entry.imageUrl)
            putExtra("animeUrl", entry.animeUrl)
            putExtra("providerId", ProviderFactory.YOUTUBE_ID)
            putExtra("nextEpisodeUrl", "")
            putExtra("startPositionMs", entry.progressMs)
        }
        startActivity(intent)
    }
}
