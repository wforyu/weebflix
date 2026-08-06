package com.weebflix.app.ui.youtube.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weebflix.app.R
import com.weebflix.app.data.scraper.YouTubeComment

class YouTubeCommentAdapter : ListAdapter<YouTubeComment, YouTubeCommentAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_comment, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val authorThumb: ImageView = view.findViewById(R.id.authorThumb)
        private val author: TextView = view.findViewById(R.id.author)
        private val published: TextView = view.findViewById(R.id.published)
        private val text: TextView = view.findViewById(R.id.text)
        private val likes: TextView = view.findViewById(R.id.likes)

        fun bind(c: YouTubeComment) {
            author.text = c.author
            published.text = c.published
            text.text = c.text
            if (c.likes.isEmpty()) {
                likes.visibility = View.GONE
            } else {
                likes.visibility = View.VISIBLE
                likes.text = c.likes
            }
            if (c.authorThumb.isNotEmpty()) {
                YouTubeFormat.bindThumb(authorThumb, c.authorThumb)
            } else {
                authorThumb.setImageDrawable(null)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<YouTubeComment>() {
            override fun areItemsTheSame(oldItem: YouTubeComment, newItem: YouTubeComment): Boolean =
                oldItem.author == newItem.author && oldItem.text == newItem.text

            override fun areContentsTheSame(oldItem: YouTubeComment, newItem: YouTubeComment): Boolean =
                oldItem == newItem
        }
    }
}
