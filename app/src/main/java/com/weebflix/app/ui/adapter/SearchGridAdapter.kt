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

class SearchGridAdapter(
    private val onClick: (Anime) -> Unit
) : ListAdapter<Anime, SearchGridAdapter.GridViewHolder>(GridDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anime_card, parent, false)
        return GridViewHolder(view)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivAnimePoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvAnimeTitle)
        private val tvEpBadge: TextView = itemView.findViewById(R.id.tvEpBadge)

        fun bind(anime: Anime) {
            tvTitle.text = anime.title
            if (anime.episode.isNotEmpty()) {
                tvEpBadge.visibility = View.VISIBLE
                tvEpBadge.text = anime.episode
            } else {
                tvEpBadge.visibility = View.GONE
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

    class GridDiffCallback : DiffUtil.ItemCallback<Anime>() {
        override fun areItemsTheSame(oldItem: Anime, newItem: Anime) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: Anime, newItem: Anime) = oldItem == newItem
    }
}
