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
    private val onVideoClick: (YouTubeVideo) -> Unit,
    private val onChannelClick: ((YouTubeVideo) -> Unit)? = null
) : RecyclerView.Adapter<YouTubeSearchAdapter.VH>() {

    private val itemsList = mutableListOf<YouTubeVideo>()

    val items: List<YouTubeVideo> get() = itemsList

    fun submit(list: List<YouTubeVideo>) {
        itemsList.clear()
        itemsList.addAll(list)
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

    override fun getItemCount(): Int = itemsList.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = itemsList[position]
        holder.title.text = v.title
        holder.duration.text = v.duration
        holder.channel.text = v.channel
        holder.meta.text = YouTubeFormat.searchMeta(v)
        YouTubeFormat.bindThumb(holder.thumb, v.thumbnail)
        holder.itemView.setOnClickListener { onVideoClick(v) }
        if (onChannelClick != null && v.channelId.isNotEmpty()) {
            holder.channel.isClickable = true
            holder.channel.isFocusable = true
            holder.channel.setOnClickListener { onChannelClick(v) }
        } else {
            holder.channel.isClickable = false
            holder.channel.isFocusable = false
            holder.channel.setOnClickListener(null)
        }
    }
}
