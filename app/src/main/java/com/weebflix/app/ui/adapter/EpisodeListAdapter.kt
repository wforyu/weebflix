package com.weebflix.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.model.Episode

class EpisodeListAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, EpisodeListAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivEpisodeThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvEpTitle)
        private val tvInfo: TextView = itemView.findViewById(R.id.tvEpInfo)
        private val tvDate: TextView = itemView.findViewById(R.id.tvEpDate)

        fun bind(episode: Episode) {
            tvTitle.text = episode.title
            tvInfo.text = if (episode.episodeNumber.isNotEmpty()) "Episode ${episode.episodeNumber}" else ""
            tvDate.text = episode.uploadDate

            if (episode.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(episode.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(ivThumb)
            }

            itemView.setOnClickListener { onClick(episode) }
        }
    }

    class EpisodeDiffCallback : DiffUtil.ItemCallback<Episode>() {
        override fun areItemsTheSame(oldItem: Episode, newItem: Episode) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: Episode, newItem: Episode) = oldItem == newItem
    }
}
