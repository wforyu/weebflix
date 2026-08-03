package com.weebflix.app.ui.youtube

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.model.WatchHistoryEntry
import com.weebflix.app.data.model.WatchHistoryManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.youtube.adapter.YouTubeHistoryAdapter

/** YouTube history tab: videos watched but not finished, resumable from last position.
 *  Shown on the Ongoing tab position only while the YouTube provider is active. */
class YouTubeHistoryFragment : Fragment() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
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
        val entries = WatchHistoryManager.getAllByProvider(requireContext(), ProviderFactory.YOUTUBE_ID)
            .filterNot { it.isFinished }
        adapter.submitList(entries)
        loadingLayout.visibility = View.GONE
        tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
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
