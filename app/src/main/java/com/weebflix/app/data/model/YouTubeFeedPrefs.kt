package com.weebflix.app.data.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's watched YouTube topics (derived from playback history) in SharedPreferences
 * and exposes them as search queries, so the endless home feed can follow what the user actually
 * watches (YouTube-home style personalization) instead of purely random queries.
 * Follows the same init(context) pattern as YouTubeSubscriptionStore (called from WeebFlixApp.onCreate).
 */
object YouTubeFeedPrefs {
    private const val PREFS = "weebflix_yt_feed_prefs"
    private const val KEY_INTERESTS = "yt_interest_queries"
    private const val DELIM = "\n"
    private const val MAX_QUERIES = 24

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Newest-first interest queries (max [MAX_QUERIES]), or empty when nothing watched yet. */
    fun getInterestQueries(): List<String> =
        prefs?.getString(KEY_INTERESTS, "")
            ?.split(DELIM)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** Records a watched video: derives interest queries from the channel + title keywords and
     *  moves them to the front (most recent interest = highest priority). */
    fun recordWatched(title: String, channel: String) {
        val prefs = prefs ?: return
        val newQueries = buildInterestQueries(title, channel)
        if (newQueries.isEmpty()) return
        val merged = ArrayList<String>()
        for (q in newQueries) if (q.isNotEmpty() && !merged.contains(q)) merged.add(q)
        for (q in getInterestQueries()) if (!merged.contains(q)) merged.add(q)
        while (merged.size > MAX_QUERIES) merged.removeAt(merged.size - 1)
        prefs.edit().putString(KEY_INTERESTS, merged.joinToString(DELIM)).apply()
    }

    /** Derives up to 2 search queries from a watched video: an exact channel query (bias toward
     *  that channel's new uploads) + the top keywords from the title (topic discovery), stripped
     *  of generic words that pollute the search. */
    fun buildInterestQueries(title: String, channel: String): List<String> {
        val out = mutableListOf<String>()
        val ch = channel.trim()
        if (ch.isNotEmpty()) out.add("$ch terbaru")
        val tokens = title.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter {
                it.length >= 4 &&
                    it !in STOPWORDS &&
                    !it.all { c -> c.isDigit() }
            }
        val keywords = tokens.distinct().take(3)
        if (keywords.isNotEmpty()) out.add(keywords.joinToString(" "))
        return out.distinct()
    }

    private val STOPWORDS = setOf(
        "a", "an", "the", "and", "or", "of", "to", "for", "in", "on", "with", "vs", "feat", "ft",
        "official", "music", "video", "hd", "full", "4k", "1080p", "720p", "480p", "2160p",
        "part", "episode", "episodes", "eps", "ep", "updated", "remastered", "lyrics", "sub",
        "terbaru", "yang", "dan", "dengan", "dari", "untuk", "lirik", "video", "clip", "short",
        "trailer", "teaser", "live", "premiere", "one", "two"
    )
}