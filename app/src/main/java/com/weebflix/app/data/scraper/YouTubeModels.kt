package com.weebflix.app.data.scraper

import org.json.JSONArray
import org.json.JSONObject

data class YouTubeVideo(
    val videoId: String = "",
    val title: String = "",
    val channel: String = "",
    val channelId: String = "",
    val channelThumb: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val views: String = "",
    val published: String = ""
) {
    val url: String get() = "https://youtu.be/$videoId"
}

data class YouTubeVideoDetail(
    val videoId: String = "",
    val title: String = "",
    val author: String = "",
    val channelThumb: String = "",
    val thumbnail: String = "",
    val views: String = "",
    val published: String = "",
    val likes: String = "",
    val description: String = ""
)

data class YouTubeHome(
    val recommended: List<YouTubeVideo> = emptyList(),
    val trending: List<YouTubeVideo> = emptyList(),
    val music: List<YouTubeVideo> = emptyList()
)

/** One page of related videos (youtubei/v1/next) + the continuation token for the next page.
 *  The first page also carries the owner renderer info (channel id/name) and the like count
 *  so the player's action row can be populated without an extra request. */
data class RelatedPage(
    val videos: List<YouTubeVideo> = emptyList(),
    val continuation: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val likeCount: String = ""
)

/** Everything the player needs from the watch page in one shot: the related/up-next list,
 *  the owner renderer (channel id/name) + like count, AND the first page of comments.
 *  Fetched via a single `next` (WEB) + one ANDROID_VR comments continuation, so the player
 *  never fires two concurrent `next` requests (which rate-limits the IP). */
data class WatchNextBundle(
    val videos: List<YouTubeVideo> = emptyList(),
    val continuation: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val likeCount: String = "",
    val comments: List<YouTubeComment> = emptyList(),
    val commentContinuation: String = ""
)

/** A subscribed YouTube channel, as returned by the Data API v3 subscriptions.list. */
data class YouTubeChannel(
    val channelId: String = "",
    val channelName: String = "",
    val channelThumb: String = "",
    val subscriptionId: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("channelId", channelId)
        put("channelName", channelName)
        put("channelThumb", channelThumb)
        put("subscriptionId", subscriptionId)
    }

    companion object {
        fun fromJson(json: JSONObject) = YouTubeChannel(
            channelId = json.optString("channelId", ""),
            channelName = json.optString("channelName", ""),
            channelThumb = json.optString("channelThumb", ""),
            subscriptionId = json.optString("subscriptionId", "")
        )
    }
}

/** One watch-history entry from the Data API v3 playlistItems?playlistId=HL. */
data class YouTubeHistoryItem(
    val video: YouTubeVideo = YouTubeVideo(),
    val watchedAtMs: Long = 0
)

/** A channel page: header info + the videos tab (all content by that owner), paginated. */
data class YouTubeChannelDetail(
    val channelId: String = "",
    val channelName: String = "",
    val channelThumb: String = "",
    val channelBanner: String = "",
    val subscriberCount: String = "",
    val videos: List<YouTubeVideo> = emptyList(),
    val continuation: String = ""
)

/** A single comment from youtubei/v1/next (commentThreadRenderer). */
data class YouTubeComment(
    val author: String = "",
    val authorThumb: String = "",
    val text: String = "",
    val likes: String = "",
    val published: String = ""
)

/** One page of comments + the continuation token for the next page. */
data class CommentPage(
    val comments: List<YouTubeComment> = emptyList(),
    val continuation: String = ""
)

/** One page of search results (youtubei/v1/search) + the continuation token for the next page.
 *  `searchVideos()` (accepted by the search screen) returns just `.videos` of the first page;
 *  the screen uses [SearchPage] + `nextSearchPage()` to keep scrolling the full result set. */
data class SearchPage(
    val videos: List<YouTubeVideo> = emptyList(),
    val continuation: String = ""
)

data class YouTubeStream(
    val url: String = "",
    val mimeType: String = "",
    val bitrate: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val contentLength: Long = 0,
    val itag: Int = 0,
    val isVideo: Boolean = false,
    val codecs: String = "",
    val frameRate: Int = 0,
    val initRange: String = "",
    val indexRange: String = "",
    val language: String = "",
    val isDefaultAudio: Boolean = false,
    val isOriginalAudio: Boolean = false
)

data class ResolvedYouTube(
    val videoId: String = "",
    val title: String = "",
    val author: String = "",
    val views: String = "",
    val published: String = "",
    val thumbnail: String = "",
    val durationMs: Long = 0,
    val videoFormats: List<YouTubeStream> = emptyList(),
    val audioFormats: List<YouTubeStream> = emptyList(),
    val blockReason: String = ""
) {
    val isEmpty: Boolean get() = videoFormats.isEmpty() || audioFormats.isEmpty()
}

/** Higher = more preferred audio track for playback: Indonesian language first, then the original
 *  soundtrack, then the video's default track, then codec (opus > mp4), then bitrate.
 *  Used by BOTH the fixed-format resolver path and the DASH ABR builder so they always pick the
 *  SAME language track (otherwise ExoPlayer's DefaultTrackSelector could grab an English dub by
 *  simply having a higher bitrate). */
fun youtubeAudioScore(f: YouTubeStream): Long {
    val idLang = f.language.startsWith("id", ignoreCase = true) || f.language.equals("in", ignoreCase = true)
    val langRank = when {
        idLang -> 8L
        f.isOriginalAudio -> 4L
        f.isDefaultAudio -> 2L
        else -> 0L
    }
    val codecRank = when {
        f.mimeType.contains("opus") -> 4L
        f.mimeType.contains("mp4") -> 2L
        else -> 0L
    }
    // Language/codec dominate; bitrate (max ~1e9) only breaks ties.
    return (langRank shl 40) + (codecRank shl 38) + f.bitrate
}
