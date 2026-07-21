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

class LatestEpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, LatestEpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anime_card, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivAnimePoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvAnimeTitle)
        private val tvEpBadge: TextView = itemView.findViewById(R.id.tvEpBadge)
        private val ivPlay: ImageView = itemView.findViewById(R.id.ivPlayOverlay)

        fun bind(episode: Episode) {
            tvTitle.text = episode.title

            if (episode.episodeNumber.isNotEmpty()) {
                tvEpBadge.visibility = View.VISIBLE
                tvEpBadge.text = "Ep ${episode.episodeNumber}"
            } else {
                tvEpBadge.visibility = View.GONE
            }

            ivPlay.visibility = View.VISIBLE

            if (episode.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(episode.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(ivPoster)
            }

            itemView.setOnClickListener { onClick(episode) }
        }
    }

    class EpisodeDiffCallback : DiffUtil.ItemCallback<Episode>() {
        override fun areItemsTheSame(oldItem: Episode, newItem: Episode) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: Episode, newItem: Episode) = oldItem == newItem
    }
}
