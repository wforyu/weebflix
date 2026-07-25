package com.weebflix.app.ui.ongoing

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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.adapter.SearchGridAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OngoingFragment : Fragment() {

    private lateinit var tvOngoingTitle: TextView
    private lateinit var rvOngoing: RecyclerView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var footerLayout: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: SearchGridAdapter

    private val allItems = mutableListOf<Anime>()
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private var lastProviderId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ongoing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOngoingTitle = view.findViewById(R.id.tvOngoingTitle)
        rvOngoing = view.findViewById(R.id.rvOngoing)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        footerLayout = view.findViewById(R.id.footerLayout)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressBar = view.findViewById(R.id.progressBar)

        adapter = SearchGridAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }

        val gridLayoutManager = GridLayoutManager(requireContext(), 3)
        rvOngoing.apply {
            layoutManager = gridLayoutManager
            adapter = this@OngoingFragment.adapter
        }

        rvOngoing.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    val lastVisible = gridLayoutManager.findLastVisibleItemPosition()
                    val total = gridLayoutManager.itemCount
                    if (lastVisible >= total - 6 && !isLoading && hasMore) {
                        loadMore()
                    }
                }
            }
        })

        updateTitleForProvider()
        loadInitial()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            val currentProvider = ProviderConfig.activeProviderId
            if (currentProvider != lastProviderId) {
                updateTitleForProvider()
                loadInitial()
            }
        }
    }

    private fun updateTitleForProvider() {
        val providerId = ProviderConfig.activeProviderId
        lastProviderId = providerId
        when (providerId) {
            ProviderFactory.DRAKORKITA_ID -> tvOngoingTitle.text = getString(R.string.all_movies)
            else -> tvOngoingTitle.text = getString(R.string.ongoing_anime_list)
        }
    }

    private fun loadInitial() {
        currentPage = 1
        allItems.clear()
        hasMore = true
        loadPage(1)
    }

    private fun loadMore() {
        if (isLoading || !hasMore) return
        currentPage++
        loadPage(currentPage)
    }

    private fun loadPage(page: Int) {
        isLoading = true
        if (page == 1) {
            loadingLayout.visibility = View.VISIBLE
        } else {
            footerLayout.visibility = View.VISIBLE
            tvStatus.text = getString(R.string.loading_more)
            progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    ProviderFactory.getActiveProvider().getOngoingAnime(page)
                }
                if (isAdded) {
                    if (items.isEmpty()) {
                        hasMore = false
                        if (page == 1) {
                            loadingLayout.visibility = View.GONE
                            Toast.makeText(requireContext(), getString(R.string.no_ongoing_data), Toast.LENGTH_SHORT).show()
                        } else {
                            tvStatus.text = getString(R.string.no_more_data)
                            progressBar.visibility = View.GONE
                            view?.postDelayed({ footerLayout.visibility = View.GONE }, 2000)
                        }
                    } else {
                        allItems.addAll(items)
                        adapter.submitList(allItems.toList())
                        loadingLayout.visibility = View.GONE
                        if (items.size < 18) {
                            hasMore = false
                            tvStatus.text = getString(R.string.no_more_data)
                            progressBar.visibility = View.GONE
                        } else {
                            footerLayout.visibility = View.GONE
                        }
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    footerLayout.visibility = View.GONE
                    isLoading = false
                    Toast.makeText(requireContext(), getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
