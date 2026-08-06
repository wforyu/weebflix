package com.weebflix.app.data.scraper

data class YouTubeVideo(
    val videoId: String = "",
    val title: String = "",
    val channel: String = "",
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
    val indexRange: String = ""
)

data class ResolvedYouTube(
    val videoId: String = "",
    val title: String = "",
    val author: String = "",
    val views: String = "",
    val thumbnail: String = "",
    val durationMs: Long = 0,
    val videoFormats: List<YouTubeStream> = emptyList(),
    val audioFormats: List<YouTubeStream> = emptyList(),
    val blockReason: String = ""
) {
    val isEmpty: Boolean get() = videoFormats.isEmpty() || audioFormats.isEmpty()
}
