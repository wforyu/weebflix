package com.weebflix.app.ui.youtube.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.scraper.YouTubeVideo

class YouTubeSearchAdapter(
    private val onVideoClick: (YouTubeVideo) -> Unit
) : RecyclerView.Adapter<YouTubeSearchAdapter.VH>() {

    private val items = mutableListOf<YouTubeVideo>()

    fun submit(list: List<YouTubeVideo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val duration: TextView = view.findViewById(R.id.duration)
        val title: TextView = view.findViewById(R.id.title)
        val channel: TextView = view.findViewById(R.id.channel)
        val meta: TextView = view.findViewById(R.id.meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_search, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        holder.title.text = v.title
        holder.duration.text = v.duration
        holder.channel.text = v.channel
        holder.meta.text = YouTubeFormat.searchMeta(v)
        YouTubeFormat.bindThumb(holder.thumb, v.thumbnail)
        holder.itemView.setOnClickListener { onVideoClick(v) }
    }
}
