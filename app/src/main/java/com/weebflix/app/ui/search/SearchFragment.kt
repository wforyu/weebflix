package com.weebflix.app.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.ui.adapter.SearchGridAdapter
import com.weebflix.app.ui.adapter.SearchHistoryAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class SearchFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var ivClear: ImageView
    private lateinit var rvResults: RecyclerView
    private lateinit var emptyLayout: LinearLayout
    private lateinit var historyLayout: LinearLayout
    private lateinit var noHistoryLayout: LinearLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnClearHistory: TextView
    private lateinit var adapter: SearchGridAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter

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
        historyLayout = view.findViewById(R.id.historyLayout)
        noHistoryLayout = view.findViewById(R.id.noHistoryLayout)
        rvHistory = view.findViewById(R.id.rvHistory)
        btnClearHistory = view.findViewById(R.id.btnClearHistory)

        adapter = SearchGridAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }

        historyAdapter = SearchHistoryAdapter(
            onClick = { query ->
                etSearch.setText(query)
                etSearch.setSelection(query.length)
                viewLifecycleOwner.lifecycleScope.launch {
                    performSearch(query)
                }
            },
            onDelete = { query ->
                removeFromHistory(query)
                showHistory()
            }
        )

        rvResults.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@SearchFragment.adapter
        }

        rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchFragment.historyAdapter
            isNestedScrollingEnabled = false
        }

        btnClearHistory.setOnClickListener {
            clearHistory()
            showHistory()
        }

        etSearch.addTextChangedListener { text ->
            ivClear.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (!text.isNullOrEmpty() && text.length >= 2) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    performSearch(text.toString())
                }
            } else if (text.isNullOrEmpty()) {
                adapter.submitList(emptyList())
                rvResults.visibility = View.GONE
                showHistory()
            }
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString()
                if (query.isNotEmpty()) {
                    saveToHistory(query)
                    searchJob?.cancel()
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        performSearch(query)
                    }
                }
                true
            } else false
        }

        ivClear.setOnClickListener {
            etSearch.text.clear()
            adapter.submitList(emptyList())
            rvResults.visibility = View.GONE
            showHistory()
        }

        showHistory()
    }

    private fun showHistory() {
        val history = getHistory()
        if (history.isEmpty()) {
            historyLayout.visibility = View.GONE
            noHistoryLayout.visibility = View.VISIBLE
            rvResults.visibility = View.GONE
        } else {
            historyLayout.visibility = View.VISIBLE
            noHistoryLayout.visibility = View.GONE
            rvResults.visibility = View.GONE
            historyAdapter.submitList(history)
        }
    }

    private fun hideHistory() {
        historyLayout.visibility = View.GONE
        noHistoryLayout.visibility = View.GONE
    }

    private suspend fun performSearch(query: String) {
        try {
            val results = WeebFlixApp.instance.scraper.searchAnime(query)
            if (isAdded) {
                hideHistory()
                adapter.submitList(results)
                rvResults.visibility = View.VISIBLE
                emptyLayout.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            if (isAdded) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getHistory(): List<String> {
        val prefs = requireContext().getSharedPreferences("weebflix_search", Context.MODE_PRIVATE)
        val json = prefs.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val prefs = requireContext().getSharedPreferences("weebflix_search", Context.MODE_PRIVATE)
        val history = getHistory().toMutableList()
        history.remove(trimmed)
        history.add(0, trimmed)
        if (history.size > 20) {
            val trimmed2 = history.take(20)
            val arr = JSONArray(trimmed2)
            prefs.edit().putString("history", arr.toString()).apply()
        } else {
            val arr = JSONArray(history)
            prefs.edit().putString("history", arr.toString()).apply()
        }
    }

    private fun removeFromHistory(query: String) {
        val prefs = requireContext().getSharedPreferences("weebflix_search", Context.MODE_PRIVATE)
        val history = getHistory().toMutableList()
        history.remove(query)
        val arr = JSONArray(history)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun clearHistory() {
        val prefs = requireContext().getSharedPreferences("weebflix_search", Context.MODE_PRIVATE)
        prefs.edit().remove("history").apply()
    }
}
