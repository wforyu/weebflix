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

class NetflixCardAdapter(
    private val onClick: (Anime) -> Unit
) : ListAdapter<Anime, NetflixCardAdapter.CardViewHolder>(CardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_netflix_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivPoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvQuality: TextView = itemView.findViewById(R.id.tvQuality)

        fun bind(anime: Anime) {
            tvTitle.text = anime.title

            val subtitle = buildString {
                if (anime.episode.isNotEmpty()) append(anime.episode)
                if (anime.status.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(anime.status)
                }
            }
            if (subtitle.isNotEmpty()) {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = subtitle
            } else {
                tvSubtitle.visibility = View.GONE
            }

            if (anime.type.isNotEmpty()) {
                tvQuality.visibility = View.VISIBLE
                tvQuality.text = anime.type
            } else if (anime.status.isNotEmpty() && anime.status.length <= 5) {
                tvQuality.visibility = View.VISIBLE
                tvQuality.text = anime.status
            } else {
                tvQuality.visibility = View.GONE
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

    class CardDiffCallback : DiffUtil.ItemCallback<Anime>() {
        override fun areItemsTheSame(oldItem: Anime, newItem: Anime) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: Anime, newItem: Anime) = oldItem == newItem
    }
}
