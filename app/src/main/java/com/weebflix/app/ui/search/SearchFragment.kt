package com.weebflix.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.ui.adapter.SearchGridAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var ivClear: ImageView
    private lateinit var rvResults: RecyclerView
    private lateinit var emptyLayout: LinearLayout
    private lateinit var adapter: SearchGridAdapter

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.etSearch)
        ivClear = view.findViewById(R.id.ivClear)
        rvResults = view.findViewById(R.id.rvSearchResults)
        emptyLayout = view.findViewById(R.id.emptyLayout)

        adapter = SearchGridAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }

        rvResults.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@SearchFragment.adapter
        }

        etSearch.addTextChangedListener { text ->
            ivClear.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (!text.isNullOrEmpty() && text.length >= 2) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    performSearch(text.toString())
                }
            } else {
                adapter.submitList(emptyList())
                emptyLayout.visibility = View.VISIBLE
                rvResults.visibility = View.GONE
            }
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString()
                if (query.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        performSearch(query)
                    }
                }
                true
            } else false
        }

        ivClear.setOnClickListener {
            etSearch.text.clear()
            adapter.submitList(emptyList())
            emptyLayout.visibility = View.VISIBLE
            rvResults.visibility = View.GONE
        }
    }

    private suspend fun performSearch(query: String) {
        try {
            val results = WeebFlixApp.instance.scraper.searchAnime(query)
            if (isAdded) {
                adapter.submitList(results)
                emptyLayout.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                rvResults.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
            }
        } catch (e: Exception) {
            if (isAdded) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
