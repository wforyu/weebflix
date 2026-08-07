package com.weebflix.app.ui.youtube.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weebflix.app.R
import com.weebflix.app.data.scraper.YouTubeVideo

object YouTubeFormat {

    fun views(v: String): String {
        if (v.isEmpty()) return ""
        val digits = v.replace(Regex("[^0-9]"), "")
        return if (digits.isNotEmpty()) "$digits x ditonton" else v
    }

    fun feedMeta(v: YouTubeVideo): String {
        return listOf(v.channel, v.views, v.published)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    fun compactMeta(v: YouTubeVideo): String {
        return listOf(v.channel, v.views)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    fun searchMeta(v: YouTubeVideo): String {
        return listOf(v.views, v.published)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    fun bindThumb(img: ImageView, url: String) {
        Glide.with(img)
            .load(url)
            .placeholder(R.drawable.bg_card)
            .into(img)
    }
}

/**
 * Flat endless home feed, YouTube-home style: one card per video + a trailing footer row
 * that shows a spinner while loading more or a "end of feed" marker. The fragment calls
 * [setLoading] before fetching the next batch and [append] with the result.
 *
 * Optionally renders a sticky-top **section header** (e.g. "Langganan" when logged in):
 * [setSection] places a label row + its videos above the endless feed, everything below
 * is offset by that section. Call [setSection] with a null title / empty list to remove it.
 */
class YouTubeFeedAdapter(
    private val onVideoClick: (YouTubeVideo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_VIDEO = 0
        const val TYPE_FOOTER = 1
        const val TYPE_SECTION = 2
    }

    private val videos = mutableListOf<YouTubeVideo>()
    private var loading = false
    private var ended = false

    private var sectionTitle: String? = null
    private val sectionVideos = mutableListOf<YouTubeVideo>()

    val isEmpty: Boolean get() = videos.isEmpty()

    /** Number of adapter rows occupied by the section (header + its videos), 0 when none. */
    private fun sectionOffset(): Int =
        if (sectionTitle != null && sectionVideos.isNotEmpty()) 1 + sectionVideos.size else 0

    /** Sets/removes the top section header + its video rows. */
    fun setSection(title: String?, section: List<YouTubeVideo>) {
        sectionTitle = title?.takeIf { section.isNotEmpty() }
        sectionVideos.clear()
        sectionVideos.addAll(section)
        notifyDataSetChanged()
    }

    fun setLoading() {
        loading = true
        ended = false
        notifyItemChanged(sectionOffset() + videos.size)
    }

    fun append(newVideos: List<YouTubeVideo>, endOfFeed: Boolean) {
        val start = sectionOffset() + videos.size
        videos.addAll(newVideos)
        loading = false
        ended = endOfFeed
        notifyItemRangeInserted(start, newVideos.size)
        notifyItemChanged(start + newVideos.size)
    }

    fun clear() {
        videos.clear()
        loading = false
        ended = false
        sectionTitle = null
        sectionVideos.clear()
        notifyDataSetChanged()
    }

    fun removeVideo(videoId: String) {
        val idx = videos.indexOfFirst { it.videoId == videoId }
        if (idx >= 0) {
            videos.removeAt(idx)
            notifyItemRemoved(sectionOffset() + idx)
        }
    }

    fun peekFirst(): YouTubeVideo? = videos.firstOrNull()

    override fun getItemCount(): Int = sectionOffset() + videos.size + 1

    override fun getItemViewType(position: Int): Int {
        val offset = sectionOffset()
        return when {
            offset > 0 && position == 0 -> TYPE_SECTION
            offset > 0 && position <= offset - 1 -> TYPE_VIDEO
            position - offset < videos.size -> TYPE_VIDEO
            else -> TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_VIDEO -> FeedVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_feed, parent, false),
                onVideoClick
            )
            TYPE_SECTION -> SectionVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_section, parent, false)
            )
            else -> FooterVH(LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_footer, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val offset = sectionOffset()
        when (holder) {
            is SectionVH -> holder.bind(sectionTitle ?: "")
            is FeedVH -> {
                val v = if (offset > 0 && position < offset) sectionVideos[position - 1]
                    else videos[position - offset]
                holder.bind(v)
            }
            is FooterVH -> holder.bind(loading, ended)
        }
    }

    class SectionVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.sectionTitle)
        fun bind(text: String) {
            title.text = text
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

    class FeedVH(view: View, private val onVideoClick: (YouTubeVideo) -> Unit) : RecyclerView.ViewHolder(view) {
        private val thumbFrame: View = view.findViewById(R.id.thumbFrame)
        private val thumb: ImageView = view.findViewById(R.id.thumb)
        private val duration: TextView = view.findViewById(R.id.duration)
        private val title: TextView = view.findViewById(R.id.title)
        private val meta: TextView = view.findViewById(R.id.meta)
        private val channelThumb: ImageView = view.findViewById(R.id.channelThumb)

        fun bind(v: YouTubeVideo) {
            title.text = v.title
            duration.text = v.duration
            meta.text = YouTubeFormat.feedMeta(v)
            val width = thumbFrame.resources.displayMetrics.widthPixels
            thumb.layoutParams = thumb.layoutParams.apply { height = width * 9 / 16 }
            YouTubeFormat.bindThumb(thumb, v.thumbnail)
            if (v.channelThumb.isNotEmpty()) {
                YouTubeFormat.bindThumb(channelThumb, v.channelThumb)
            } else {
                channelThumb.setImageDrawable(null)
            }
            itemView.setOnClickListener { onVideoClick(v) }
        }
    }
}
