package com.weebflix.app.ui.youtube.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.scraper.YouTubeVideo

class YouTubeSearchAdapter(
    private val onVideoClick: (YouTubeVideo) -> Unit,
    private val onChannelClick: ((YouTubeVideo) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_VIDEO = 0
        const val TYPE_FOOTER = 1
    }

    private val itemsList = mutableListOf<YouTubeVideo>()
    private var loading = false
    private var ended = false

    val items: List<YouTubeVideo> get() = itemsList

    fun submit(list: List<YouTubeVideo>) {
        itemsList.clear()
        itemsList.addAll(list)
        loading = false
        ended = false
        notifyDataSetChanged()
    }

    /** Replaces the whole list from a fresh query (footer reset to idle). */
    fun submitPage(page: List<YouTubeVideo>, endOfFeed: Boolean) {
        itemsList.clear()
        itemsList.addAll(page)
        loading = false
        ended = endOfFeed
        notifyDataSetChanged()
    }

    /** Shows the footer spinner while the next continuation page is being fetched. */
    fun setLoading() {
        loading = true
        notifyItemChanged(itemsList.size)
    }

    /** Appends the next continuation page; keeps the spinner until the feed is exhausted. */
    fun append(newVideos: List<YouTubeVideo>, endOfFeed: Boolean) {
        val start = itemsList.size
        itemsList.addAll(newVideos)
        loading = false
        ended = endOfFeed
        if (newVideos.isNotEmpty()) notifyItemRangeInserted(start, newVideos.size)
        notifyItemChanged(itemsList.size)
    }

    override fun getItemCount(): Int = itemsList.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position < itemsList.size) TYPE_VIDEO else TYPE_FOOTER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_VIDEO) {
            VH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_search, parent, false),
                onVideoClick,
                onChannelClick
            )
        } else {
            FooterVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_footer, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VH) {
            holder.bind(itemsList[position])
        } else {
            (holder as FooterVH).bind(loading, ended)
        }
    }

    class FooterVH(view: View) : RecyclerView.ViewHolder(view) {
        private val loadingView: ProgressBar = view.findViewById(R.id.footerLoading)
        private val endView: TextView = view.findViewById(R.id.footerEnd)
        fun bind(loading: Boolean, ended: Boolean) {
            loadingView.visibility = if (loading) View.VISIBLE else View.GONE
            endView.visibility = if (ended) View.VISIBLE else View.GONE
        }
    }

    class VH(
        view: View,
        private val onVideoClick: (YouTubeVideo) -> Unit,
        private val onChannelClick: ((YouTubeVideo) -> Unit)?
    ) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val duration: TextView = view.findViewById(R.id.duration)
        val title: TextView = view.findViewById(R.id.title)
        val channel: TextView = view.findViewById(R.id.channel)
        val meta: TextView = view.findViewById(R.id.meta)

        fun bind(v: YouTubeVideo) {
            title.text = v.title
            duration.text = v.duration
            channel.text = v.channel
            meta.text = YouTubeFormat.searchMeta(v)
            YouTubeFormat.bindThumb(thumb, v.thumbnail)
            itemView.setOnClickListener { onVideoClick(v) }
            if (onChannelClick != null && v.channelId.isNotEmpty()) {
                channel.isClickable = true
                channel.isFocusable = true
                channel.setOnClickListener { onChannelClick(v) }
            } else {
                channel.isClickable = false
                channel.isFocusable = false
                channel.setOnClickListener(null)
            }
        }
    }
}