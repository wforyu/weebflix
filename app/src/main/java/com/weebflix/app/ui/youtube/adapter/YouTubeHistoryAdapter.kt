package com.weebflix.app.ui.youtube.adapter

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

class YouTubeHistoryAdapter(
    private val onClick: (WatchHistoryEntry) -> Unit
) : ListAdapter<WatchHistoryEntry, YouTubeHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_youtube_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbFrame: View = itemView.findViewById(R.id.thumbFrame)
        private val thumb: ImageView = itemView.findViewById(R.id.thumb)
        private val duration: TextView = itemView.findViewById(R.id.duration)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val meta: TextView = itemView.findViewById(R.id.meta)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

        fun bind(entry: WatchHistoryEntry) {
            title.text = entry.animeTitle
            val percent = entry.progressPercent
            meta.text = if (entry.progressMs > 0 && entry.durationMs > 0) {
                "${percent}% ditonton"
            } else {
                "Ditonton"
            }
            progressBar.progress = percent
            duration.visibility = View.GONE
            val width = thumbFrame.resources.displayMetrics.widthPixels
            thumb.layoutParams = thumb.layoutParams.apply { height = width * 9 / 16 }
            if (entry.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(entry.imageUrl)
                    .placeholder(R.drawable.bg_card)
                    .into(thumb)
            } else {
                thumb.setImageResource(R.drawable.bg_card)
            }
            itemView.setOnClickListener { onClick(entry) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WatchHistoryEntry>() {
        override fun areItemsTheSame(old: WatchHistoryEntry, new: WatchHistoryEntry) =
            old.episodeUrl == new.episodeUrl
        override fun areContentsTheSame(old: WatchHistoryEntry, new: WatchHistoryEntry) =
            old.progressPercent == new.progressPercent && old.animeTitle == new.animeTitle
    }
}
