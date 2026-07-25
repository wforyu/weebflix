package com.weebflix.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.model.Anime

class HeroPagerAdapter(
    private val items: List<Anime>,
    private val onClick: (Anime) -> Unit,
    private val onPlay: ((Anime) -> Unit)? = null,
    private val onInfo: ((Anime) -> Unit)? = null
) : RecyclerView.Adapter<HeroPagerAdapter.HeroViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hero_banner, parent, false)
        return HeroViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivHero: ImageView = itemView.findViewById(R.id.ivHeroImage)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeroTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvHeroSubtitle)
        private val tvQuality: TextView = itemView.findViewById(R.id.tvHeroQuality)
        private val tvRating: TextView = itemView.findViewById(R.id.tvHeroRating)
        private val btnHeroPlay: TextView? = itemView.findViewById(R.id.btnHeroPlay)
        private val btnHeroDetail: TextView? = itemView.findViewById(R.id.btnHeroDetail)

        fun bind(anime: Anime) {
            tvTitle.text = anime.title

            val subtitle = buildString {
                if (anime.type.isNotEmpty()) append(anime.type)
                if (anime.episode.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(anime.episode)
                }
                if (anime.score.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("★ ${anime.score}")
                }
            }
            tvSubtitle.text = subtitle

            if (anime.type.isNotEmpty() && anime.type != "TV") {
                tvQuality.visibility = View.VISIBLE
                tvQuality.text = anime.type
            } else {
                tvQuality.visibility = View.GONE
            }

            if (anime.score.isNotEmpty()) {
                tvRating.visibility = View.VISIBLE
                tvRating.text = "★ ${anime.score}"
            } else {
                tvRating.visibility = View.GONE
            }

            if (anime.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(anime.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(ivHero)
            }

            itemView.setOnClickListener { onClick(anime) }
            btnHeroPlay?.setOnClickListener { onPlay?.invoke(anime) ?: onClick(anime) }
            btnHeroDetail?.setOnClickListener { onInfo?.invoke(anime) ?: onClick(anime) }
        }
    }
}
