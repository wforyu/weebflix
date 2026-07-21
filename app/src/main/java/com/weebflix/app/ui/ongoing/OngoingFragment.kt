package com.weebflix.app.ui.ongoing

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.WeebFlixApp
import com.weebflix.app.ui.adapter.SearchGridAdapter
import com.weebflix.app.ui.detail.AnimeDetailActivity
import kotlinx.coroutines.launch

class OngoingFragment : Fragment() {

    private lateinit var rvOngoing: RecyclerView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var adapter: SearchGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ongoing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvOngoing = view.findViewById(R.id.rvOngoing)
        loadingLayout = view.findViewById(R.id.loadingLayout)

        adapter = SearchGridAdapter { anime ->
            val intent = Intent(requireContext(), AnimeDetailActivity::class.java)
            intent.putExtra("url", anime.url)
            startActivity(intent)
        }

        rvOngoing.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@OngoingFragment.adapter
        }

        loadOngoing()
    }

    private fun loadOngoing() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ongoing = WeebFlixApp.instance.scraper.getOngoingAnime()
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    adapter.submitList(ongoing)
                    if (ongoing.isEmpty()) {
                        Toast.makeText(requireContext(), "Tidak ada data ongoing", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
