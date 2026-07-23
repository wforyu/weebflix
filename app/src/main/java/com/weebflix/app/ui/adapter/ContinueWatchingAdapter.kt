package com.weebflix.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.model.WatchHistoryEntry

class ContinueWatchingAdapter(
    private val onClick: (WatchHistoryEntry) -> Unit
) : ListAdapter<WatchHistoryEntry, ContinueWatchingAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivPoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvEpBadge: TextView = itemView.findViewById(R.id.tvEpBadge)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

        fun bind(entry: WatchHistoryEntry) {
            tvTitle.text = entry.animeTitle

            val epText = if (entry.episodeTitle.isNotEmpty()) entry.episodeTitle
            else if (entry.episodeNumber.isNotEmpty()) "Episode ${entry.episodeNumber}"
            else ""
            if (epText.isNotEmpty()) {
                tvEpBadge.visibility = View.VISIBLE
                tvEpBadge.text = epText
            } else {
                tvEpBadge.visibility = View.GONE
            }

            progressBar.progress = entry.progressPercent

            if (entry.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(entry.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(ivPoster)
            } else {
                ivPoster.setImageResource(R.drawable.bg_card)
            }

            itemView.setOnClickListener { onClick(entry) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WatchHistoryEntry>() {
        override fun areItemsTheSame(old: WatchHistoryEntry, new: WatchHistoryEntry) =
            old.episodeUrl == new.episodeUrl
        override fun areContentsTheSame(old: WatchHistoryEntry, new: WatchHistoryEntry) =
            old == new
    }
}
