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
import com.weebflix.app.data.model.Anime

class DramaCardAdapter(
    private val onClick: (Anime) -> Unit
) : ListAdapter<Anime, DramaCardAdapter.DramaViewHolder>(DramaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DramaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drama_card, parent, false)
        return DramaViewHolder(view)
    }

    override fun onBindViewHolder(holder: DramaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DramaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivDramaPoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvDramaTitle)
        private val tvQuality: TextView = itemView.findViewById(R.id.tvDramaQuality)
        private val tvRating: TextView = itemView.findViewById(R.id.tvDramaRating)
        private val tvEpisode: TextView = itemView.findViewById(R.id.tvDramaEpisode)

        fun bind(anime: Anime) {
            tvTitle.text = anime.title

            if (anime.type.isNotEmpty()) {
                tvQuality.visibility = View.VISIBLE
                tvQuality.text = anime.type
            } else {
                tvQuality.visibility = View.GONE
            }

            if (anime.score.isNotEmpty()) {
                tvRating.visibility = View.VISIBLE
                tvRating.text = "\u2605 ${anime.score}"
            } else {
                tvRating.visibility = View.GONE
            }

            if (anime.episode.isNotEmpty()) {
                tvEpisode.visibility = View.VISIBLE
                tvEpisode.text = anime.episode
            } else if (anime.status.isNotEmpty()) {
                tvEpisode.visibility = View.VISIBLE
                tvEpisode.text = anime.status
            } else {
                tvEpisode.visibility = View.GONE
            }

            if (anime.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(anime.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(ivPoster)
            }

            itemView.setOnClickListener { onClick(anime) }
        }
    }

    class DramaDiffCallback : DiffUtil.ItemCallback<Anime>() {
        override fun areItemsTheSame(oldItem: Anime, newItem: Anime) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: Anime, newItem: Anime) = oldItem == newItem
    }
}
