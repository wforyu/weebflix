package com.weebflix.app.data.scraper

import android.util.Log
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.AnimeDetail
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.EpisodeNavigation
import com.weebflix.app.data.model.VideoServer
import com.weebflix.app.data.provider.AnimeProvider
import com.weebflix.app.data.provider.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class YouTubeScraper : AnimeProvider {

    override val id: String = ProviderFactory.YOUTUBE_ID
    override val name: String = "YouTube"
    override val defaultBaseUrl: String = "https://www.youtube.com"

    override var baseUrl: String
        get() = ProviderConfig.getBaseUrl(id)
        set(value) {
            ProviderConfig.setBaseUrl(id, value)
        }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val TAG = "YouTubeScraper"

    private data class YtClient(val name: String, val version: String, val sdk: Int, val key: String, val ua: String)

    private val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private val clients = listOf(
        YtClient("WEB", "2.20260731.00.00", 0, "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8", WEB_UA),
        YtClient("ANDROID_VR", "1.55.3", 30, "AIzaSyB9VGVgUmYc0HeBp5dHnjg1WxNb0qk2X3k", "com.google.android.youtube/19.09.37 (Linux; U; Android 13; en_US)")
    )

    /** ANDROID_VR returns full `commentRenderer` content for the comments continuation,
     *  while WEB now only returns metadata (commentViewModel). */
    private val vrClient: YtClient = clients.first { it.name == "ANDROID_VR" }

    private data class YtResult(val json: JSONObject?, val flagged: Boolean)

    private fun context(c: YtClient) = JSONObject()
        .put("client", JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", c.version)
            .apply { if (c.sdk > 0) put("androidSdkVersion", c.sdk) }
            .put("hl", "id")
            .put("gl", "ID")
            .put("utcOffsetMinutes", 0))

    /**
     * Rate-limit aware innertube POST. YouTube flags the IP (HTTP 400 "invalid argument")
     * for ~60-90s after a burst of requests, so: try the primary client once; a 400 means
     * the IP is flagged -> do NOT retry with other clients (would extend the flag); only a
     * non-400 failure falls through to the secondary client.
     */
    private fun post(endpoint: String, fill: (JSONObject) -> Unit): YtResult {
        for (c in clients) {
            val r = postWith(c, endpoint, fill)
            if (r.flagged) return r
            if (r.json != null) return r
        }
        return YtResult(null, flagged = false)
    }

    /** Sends the request with a specific client. Used by the comments flow: the WEB client's
     *  comment continuation no longer carries comment content (new commentViewModel format),
     *  so comments must be fetched with ANDROID_VR which still returns full commentRenderer. */
    private fun postWith(c: YtClient, endpoint: String, fill: (JSONObject) -> Unit): YtResult {
        try {
            val body = JSONObject().put("context", context(c))
            fill(body)
            Log.d(TAG, "$endpoint[${c.name}] body: $body")
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/$endpoint?key=${c.key}&prettyPrint=false")
                .addHeader("User-Agent", c.ua)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://www.youtube.com")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.w(TAG, "$endpoint[${c.name}] HTTP ${resp.code}: ${bodyStr.take(120)}")
                    if (resp.code == 400) return YtResult(null, flagged = true)
                    return YtResult(null, flagged = false)
                }
                Log.d(TAG, "$endpoint[${c.name}] OK len=${bodyStr.length} head=${bodyStr.take(150)}")
                return YtResult(JSONObject(bodyStr), flagged = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "$endpoint[${c.name}] error: ${e.message}")
        }
        return YtResult(null, flagged = false)
    }

    private fun fetchSearch(query: String, params: String? = null): List<YouTubeVideo> {
        val res = post("search") { it.put("query", query); if (!params.isNullOrEmpty()) it.put("params", params) }
        val json = res.json ?: return emptyList()
        val renderers = mutableListOf<JSONObject>()
        val lockups = mutableListOf<JSONObject>()
        collectVideoRenderers(json, renderers)
        collectLockupViewModel(json, lockups)
        val out = renderers.mapNotNull { parseVideoRenderer(it) } + lockups.mapNotNull { parseLockupViewModel(it) }
        return out.distinctBy { it.videoId }
    }

    private fun fetchBrowse(browseId: String): List<YouTubeVideo> {
        val res = post("browse") { it.put("browseId", browseId) }
        val json = res.json ?: return emptyList()
        val out = mutableListOf<JSONObject>()
        collectVideoRenderers(json, out)
        if (out.isEmpty()) {
            val counts = Regex("""([a-zA-Z]+Renderer)""").findAll(json.toString())
                .map { it.groupValues[1] }
                .groupingBy { it }
                .eachCount()
            val top = counts.entries.sortedByDescending { it.value }.take(15)
                .joinToString { "${it.key}=${it.value}" }
            Log.w(TAG, "browse[$browseId] no renderers. keys: $top")
        }
        return out.mapNotNull { parseVideoRenderer(it) }.distinctBy { it.videoId }
    }

    private fun collectVideoRenderers(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "videoRenderer" || key == "compactVideoRenderer") {
                        node.optJSONObject(key)?.let { out += it }
                    } else {
                        collectVideoRenderers(node.opt(key), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectVideoRenderers(node.opt(i), out)
            }
        }
    }

    private fun parseVideoRenderer(r: JSONObject): YouTubeVideo? {
        val videoId = r.optString("videoId", "")
        if (videoId.isEmpty()) return null
        val byline = r.optJSONObject("ownerText")?.optJSONArray("runs")
            ?: r.optJSONObject("longBylineText")?.optJSONArray("runs")
        val channelId = r.optJSONObject("ownerText")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")?.optString("browseId", "").orEmpty()
        return YouTubeVideo(
            videoId = videoId,
            title = runsText(r.optJSONObject("title")),
            channel = byline?.optJSONObject(0)?.optString("text", "") ?: "",
            channelId = channelId,
            channelThumb = extractChannelThumb(r),
            thumbnail = pickThumb(r.optJSONObject("thumbnail")),
            duration = r.optJSONObject("lengthText")?.let { runsText(it) } ?: "",
            views = r.optJSONObject("viewCountText")?.let { runsText(it) } ?: "",
            published = r.optJSONObject("publishedTimeText")?.let { runsText(it) } ?: ""
        )
    }

    /** Avatar channel dari `videoRenderer` dengan beberapa struktur fallback — YouTube bergeser
     *  dari `thumbnail.thumbnails` klasik ke `image.sources` ala avatarViewModel di sebagian
     *  layout, jadi yang klasik saja sering kosong di feed. */
    private fun extractChannelThumb(r: JSONObject): String {
        val ctlr = r.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
        pickThumb(ctlr?.optJSONObject("thumbnail"))?.takeIf { it.isNotEmpty() }?.let { return it }
        pickFromSources(ctlr?.optJSONObject("image")?.optJSONArray("sources"))?.takeIf { it.isNotEmpty() }?.let { return it }
        pickThumb(r.optJSONObject("channelThumbnail"))?.takeIf { it.isNotEmpty() }?.let { return it }
        r.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("avatarSupportedRenderers")?.optJSONObject("avatarWithLinkRenderer")
            ?.let { avatar ->
                pickThumb(avatar.optJSONObject("thumbnail"))?.takeIf { it.isNotEmpty() }?.let { return it }
                pickFromSources(avatar.optJSONObject("image")?.optJSONArray("sources"))
                    ?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        return ""
    }

    private fun runsText(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "") ?: "")
        }
        return sb.toString()
    }

    private fun pickThumb(thumb: JSONObject?): String {
        val arr = thumb?.optJSONArray("thumbnails") ?: return ""
        var best = ""
        var bestW = 0
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val w = t.optInt("width", 0)
            if (w >= bestW) {
                bestW = w
                best = t.optString("url", "")
            }
        }
        return best
    }

    // ---- Related videos (youtubei/v1/next) ----

    /** Picks the widest source URL from an innertube `sources` array. */
    private fun pickFromSources(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        var best = ""
        var bestW = 0
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val w = t.optInt("width", 0)
            if (w >= bestW) {
                bestW = w
                best = t.optString("url", "")
            }
        }
        return best
    }

    /** The current watch-next layout uses `lockupViewModel` (not compactVideoRenderer) for
     *  related videos. contentType distinguishes videos from Shorts shelves. */
    private fun parseLockupViewModel(l: JSONObject): YouTubeVideo? {
        val videoId = l.optString("contentId", "")
        if (videoId.isEmpty()) return null
        val contentType = l.optString("contentType", "")
        if (contentType.isNotEmpty() && contentType != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val md = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = md.optJSONObject("title")?.optString("content", "").orEmpty()
        if (title.isEmpty()) return null
        val rows = md.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows") ?: JSONArray()
        fun part(row: Int, col: Int = 0): String {
            val r = rows.optJSONObject(row) ?: return ""
            val parts = r.optJSONArray("metadataParts") ?: return ""
            return parts.optJSONObject(col)?.optJSONObject("text")?.optString("content", "") ?: ""
        }
        val channel = part(0)
        val views = part(1)
        val published = part(1, 1)
        val avatarVm = md.optJSONObject("image")
            ?.optJSONObject("decoratedAvatarViewModel")?.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
        val channelId = avatarVm?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("browseEndpoint")?.optString("browseId", "").orEmpty()
        val img = l.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONObject("image")
        val thumbnail = pickFromSources(img?.optJSONArray("sources"))
        var channelThumb = pickFromSources(avatarVm?.optJSONObject("image")?.optJSONArray("sources"))
        if (channelThumb.isEmpty()) channelThumb = pickThumb(avatarVm?.optJSONObject("thumbnail")) ?: ""
        var duration = ""
        val overlays = img?.optJSONArray("overlays")
        if (overlays != null && overlays.length() > 0) {
            val badges = overlays.optJSONObject(0)
                ?.optJSONObject("thumbnailBottomOverlayViewModel")
                ?.optJSONArray("badges")
            if (badges != null && badges.length() > 0) {
                duration = badges.optJSONObject(0)
                    ?.optJSONObject("thumbnailBadgeViewModel")
                    ?.optString("text", "") ?: ""
            }
        }
        return YouTubeVideo(
            videoId = videoId,
            title = title,
            channel = channel,
            channelId = channelId,
            channelThumb = channelThumb,
            thumbnail = thumbnail,
            duration = duration,
            views = views,
            published = published
        )
    }

    /** Collects every `lockupViewModel` object anywhere in the JSON tree. */
    private fun collectLockupViewModel(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "lockupViewModel") {
                        node.optJSONObject(key)?.let { out += it }
                    } else {
                        collectLockupViewModel(node.opt(key), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectLockupViewModel(node.opt(i), out)
            }
        }
    }

    /** Recursively finds the `continuationCommand.token` used to page the related feed. */
    private fun findContinuationToken(node: Any?): String {
        when (node) {
            is JSONObject -> {
                val cir = node.optJSONObject("continuationItemRenderer")
                if (cir != null) {
                    val tok = cir.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token", "").orEmpty()
                    if (tok.isNotEmpty()) return tok
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val t = findContinuationToken(node.opt(keys.next()))
                    if (t.isNotEmpty()) return t
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val t = findContinuationToken(node.opt(i))
                    if (t.isNotEmpty()) return t
                }
            }
        }
        return ""
    }

    /** First page of real related videos for a videoId (YouTube's own "Up Next" list), so the
     *  related feed always matches the content being played (dangdut -> dangdut, game -> game...).
     *  Also extracts the owner renderer (channel id/name) + like count from the same response. */
    suspend fun relatedVideos(videoId: String): RelatedPage = withContext(Dispatchers.IO) {
        val res = post("next") { it.put("videoId", videoId) }
        val json = res.json ?: return@withContext RelatedPage()
        val twoCol = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?: return@withContext RelatedPage()
        val results = twoCol.optJSONObject("results")?.optJSONObject("results")
        val secondary = twoCol.optJSONObject("secondaryResults")?.optJSONObject("secondaryResults")
        val out = mutableListOf<JSONObject>()
        if (secondary != null) collectLockupViewModel(secondary, out)
        RelatedPage(
            videos = out.mapNotNull { parseLockupViewModel(it) }.distinctBy { it.videoId },
            continuation = if (secondary != null) findContinuationToken(secondary) else "",
            channelId = results?.let { findOwnerChannelId(it) } ?: "",
            channelName = results?.let { findOwnerChannelName(it) } ?: "",
            likeCount = results?.let { findLikeCount(it) } ?: ""
        )
    }

    /** Next page of related videos via the continuation token from [relatedVideos]. */
    suspend fun nextRelatedPage(continuation: String): RelatedPage = withContext(Dispatchers.IO) {
        if (continuation.isEmpty()) return@withContext RelatedPage()
        val res = post("next") { it.put("continuation", continuation) }
        val json = res.json ?: return@withContext RelatedPage()
        val items = json.optJSONArray("onResponseReceivedActions")?.optJSONObject(0)
            ?.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")
            ?: json.optJSONArray("onResponseReceivedEndpoints")?.optJSONObject(0)
                ?.optJSONObject("appendContinuationItemsAction")
                ?.optJSONArray("continuationItems")
            ?: return@withContext RelatedPage()
        val out = mutableListOf<JSONObject>()
        collectLockupViewModel(items, out)
        RelatedPage(
            videos = out.mapNotNull { parseLockupViewModel(it) }.distinctBy { it.videoId },
            continuation = findContinuationToken(items)
        )
    }

    /** Next page of related videos, wrapped as a [WatchNextBundle] so the player can treat the
     *  first page and continuation pages uniformly (first page also carries owner/comments). */
    suspend fun watchNextBundleFromContinuation(continuation: String): WatchNextBundle = withContext(Dispatchers.IO) {
        val page = nextRelatedPage(continuation)
        WatchNextBundle(videos = page.videos, continuation = page.continuation)
    }

    /** Everything the player needs in one shot: related videos + owner + like count + the first
     *  page of comments. Fires ONE `next` (WEB) for related/owner and, when the response exposes
     *  a comments-section continuation, ONE ANDROID_VR continuation for the comments — instead of
     *  two concurrent `next` calls (related + comments) that rate-limit the IP (HTTP 400 flag). */
    suspend fun watchNextBundle(videoId: String): WatchNextBundle = withContext(Dispatchers.IO) {
        val res = post("next") { it.put("videoId", videoId) }
        val json = res.json ?: return@withContext WatchNextBundle()
        val twoCol = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?: return@withContext WatchNextBundle()
        val results = twoCol.optJSONObject("results")?.optJSONObject("results")
        val secondary = twoCol.optJSONObject("secondaryResults")?.optJSONObject("secondaryResults")
        val out = mutableListOf<JSONObject>()
        if (secondary != null) collectLockupViewModel(secondary, out)
        val commentToken = results?.let { findCommentsSectionToken(it) } ?: ""
        val page = if (commentToken.isNotEmpty()) fetchComments(commentToken) else CommentPage()
        WatchNextBundle(
            videos = out.mapNotNull { parseLockupViewModel(it) }.distinctBy { it.videoId },
            continuation = if (secondary != null) findContinuationToken(secondary) else "",
            channelId = results?.let { findOwnerChannelId(it) } ?: "",
            channelName = results?.let { findOwnerChannelName(it) } ?: "",
            likeCount = results?.let { findLikeCount(it) } ?: "",
            comments = page.comments,
            commentContinuation = page.continuation
        )
    }

    // ---- Comments (youtubei/v1/next + a dedicated comments continuation) ----

    /** First page of comments for a videoId. The WEB `next` response no longer inlines comment
     *  content (new commentViewModel format) — it exposes a "comments-section" continuation
     *  token instead, which is then fetched with ANDROID_VR to get full `commentRenderer`s. */
    suspend fun firstComments(videoId: String): CommentPage = withContext(Dispatchers.IO) {
        val res = post("next") { it.put("videoId", videoId) }
        val json = res.json ?: return@withContext CommentPage()
        val results = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?.optJSONObject("results")
            ?.optJSONObject("results")
            ?: return@withContext CommentPage()
        val token = findCommentsSectionToken(results)
        if (token.isEmpty()) return@withContext CommentPage()
        fetchComments(token)
    }

    /** Next page of comments via the continuation token from [firstComments]. */
    suspend fun nextComments(continuation: String): CommentPage = withContext(Dispatchers.IO) {
        if (continuation.isEmpty()) return@withContext CommentPage()
        fetchComments(continuation)
    }

    /** Fetches one page of comments from a comments continuation token using ANDROID_VR.
     *  Response shape: `continuationContents.itemSectionContinuation` with `contents`
     *  (commentThreadRenderers) + `continuations[0].nextContinuationData.continuation`. */
    private suspend fun fetchComments(continuation: String): CommentPage {
        val res = postWith(vrClient, "next") { it.put("continuation", continuation) }
        val json = res.json ?: return CommentPage()
        val isc = json.optJSONObject("continuationContents")
            ?.optJSONObject("itemSectionContinuation")
            ?: return CommentPage()
        val contents = isc.optJSONArray("contents") ?: return CommentPage()
        val threads = mutableListOf<JSONObject>()
        collectCommentThreads(contents, threads)
        return CommentPage(
            comments = threads.mapNotNull { parseCommentThread(it) }.take(20),
            continuation = isc.optJSONArray("continuations")
                ?.optJSONObject(0)
                ?.optJSONObject("nextContinuationData")
                ?.optString("continuation", "") ?: ""
        )
    }

    /** Finds the "comments-section" continuation token in the `next` results column. The WEB
     *  format moved comments behind an itemSectionRenderer whose own `targetId` is
     *  "comments-section" (sectionIdentifier "comment-item-section"); its
     *  `contents[0].continuationItemRenderer.continuationEndpoint.continuationCommand.token`
     *  is then fetched with ANDROID_VR to get full `commentRenderer`s. */
    private fun findCommentsSectionToken(node: Any?): String {
        when (node) {
            is JSONObject -> {
                if (node.optString("targetId", "") == "comments-section") {
                    return node.optJSONArray("contents")
                        ?.optJSONObject(0)
                        ?.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token", "") ?: ""
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val t = findCommentsSectionToken(node.opt(keys.next()))
                    if (t.isNotEmpty()) return t
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val t = findCommentsSectionToken(node.opt(i))
                    if (t.isNotEmpty()) return t
                }
            }
        }
        return ""
    }

    /** Collects every `commentThreadRenderer` object anywhere in the JSON tree. */
    private fun collectCommentThreads(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "commentThreadRenderer") {
                        node.optJSONObject(key)?.let { out += it }
                    } else {
                        collectCommentThreads(node.opt(key), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectCommentThreads(node.opt(i), out)
            }
        }
    }

    private fun parseCommentThread(t: JSONObject): YouTubeComment? {
        val c = t.optJSONObject("comment")?.optJSONObject("commentRenderer") ?: return null
        val author = runsText(c.optJSONObject("authorText"))
        if (author.isEmpty()) return null
        val thumbs = c.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")
        val thumb = if (thumbs != null && thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url", "") else ""
        return YouTubeComment(
            author = author,
            authorThumb = thumb,
            text = runsText(c.optJSONObject("contentText")),
            likes = c.optJSONObject("voteCount")?.let { runsText(it) } ?: "",
            published = c.optJSONObject("publishedTimeText")?.let { runsText(it) } ?: ""
        )
    }

    // ---- Owner renderer + like count (from the `next` results column) ----

    /** Depth-first search for the first object with the given key. */
    private fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findFirst(node.opt(keys.next()), key)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findFirst(node.opt(i), key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findOwnerChannelId(results: JSONObject): String {
        val owner = findFirst(results, "videoOwnerRenderer") ?: return ""
        return owner.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId", "") ?: ""
    }

    private fun findOwnerChannelName(results: JSONObject): String {
        val owner = findFirst(results, "videoOwnerRenderer") ?: return ""
        return runsText(owner.optJSONObject("title"))
    }

    /** Like count from `videoPrimaryInfoRenderer`: modern layout hides it inside
     *  `likeButtonViewModel` (accessibilityText "12 jt suka"); older layout has `likeCount`. */
    private fun findLikeCount(results: JSONObject): String {
        val pri = findFirst(results, "videoPrimaryInfoRenderer") ?: return ""
        pri.optJSONObject("likeCount")?.let { return runsText(it) }
        val lb = pri.optJSONObject("likeButtonViewModel")
            ?.optJSONObject("likeButtonViewModel")
            ?.optJSONObject("toggleButtonViewModel")
            ?.optJSONObject("toggleButtonViewModel")
            ?: return ""
        val bvm = lb.optJSONObject("defaultButtonViewModel")?.optJSONObject("buttonViewModel")
        val acc = bvm?.optJSONObject("accessibilityText")?.optString("content", "").orEmpty()
        if (acc.isNotEmpty()) return acc
        return bvm?.optString("title", "") ?: ""
    }

    // ---- public API used by the YouTube UI ----

    private var homeCache: YouTubeHome? = null
    private var homeCacheTime = 0L

    /** Search `sp` param for upload-date "This year" — keeps the home feed fresh instead of
     *  surfacing 2-3 year old videos. Applies to the endless feed too. */
    private val UPLOAD_THIS_YEAR = "EgIIBQ%3D%3D"

    /** Keeps only recent uploads. `publishedTimeText` reads like "3 years ago" / "5 months ago"
     *  (hl=en) or "3 tahun yang lalu" / "5 bulan yang lalu" (hl=id); anything claiming N years
     *  / N tahun is dropped. Used as a safety net in case YouTube ignores the `sp` upload-date
     *  filter. */
    private fun isFresh(v: YouTubeVideo): Boolean =
        !(Regex("""(\d+)\s+year""").containsMatchIn(v.published) ||
            Regex("""(\d+)\s+tahun""").containsMatchIn(v.published))

    /** Search biased to fresh uploads: upload-date filter server-side, then client-side
     *  re-check; falls back to an unfiltered search when the filtered one comes up empty. */
    private fun searchFresh(query: String): List<YouTubeVideo> {
        val filtered = fetchSearch(query, UPLOAD_THIS_YEAR).filter(::isFresh)
        if (filtered.isNotEmpty()) return filtered
        return fetchSearch(query).filter(::isFresh)
    }

    /** Fetches the home feed with pacing: one section at a time, ~3s apart, and stops the
     *  moment YouTube flags the IP (HTTP 400) instead of hammering it. Cached for 5 min so
     *  returning to the tab never re-scrapes. Built from search rows: FEwhat_to_watch returns
     *  only a sign-in nudge (feedNudgeRenderer) and FEtrending is rejected (400 "invalid
     *  argument") for a logged-out user on this network — search is the reliable endpoint. */
    suspend fun getHome(): YouTubeHome = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        homeCache?.let { if (now - homeCacheTime < 5 * 60_000) return@withContext it }

        // Personalize the top row with the user's most recent watched topic when available.
        var recommended = searchFresh(com.weebflix.app.data.model.YouTubeFeedPrefs.getInterestQueries().firstOrNull() ?: "trending indonesia")
        if (recommended.isEmpty()) recommended = searchFresh("trending indonesia")
        if (recommended.isEmpty()) {
            Log.w(TAG, "home: search empty/flagged, aborting multi-section load")
            val home = YouTubeHome(recommended = emptyList(), trending = emptyList(), music = emptyList())
            homeCache = home; homeCacheTime = now
            return@withContext home
        }

        delay(700)
        val trending = searchFresh("viral youtube indonesia")

        delay(700)
        val music = searchFresh("musik indonesia terbaru")

        val home = YouTubeHome(
            recommended = recommended.take(15),
            trending = (trending.ifEmpty { recommended }).take(15),
            music = (music.ifEmpty { recommended }).take(15)
        )
        homeCache = home; homeCacheTime = now
        home
    }

    private val feedQueries = listOf(
        "trending indonesia", "viral terbaru", "musik indonesia terbaru",
        "berita terkini indonesia", "makanan enak", "resep masakan rumahan",
        "vlog harian", "komedi lucu", "gameplay indonesia", "sepak bola indonesia",
        "anime sub indo", "drakor sub indo", "film trailer terbaru", "tutorial",
        "tips android", "kucing lucu", "challenge seru", "prank lucu",
        "kecantikan dan skincare", "travel indonesia", "otomotif", "memasak mudah"
    )
    private val seenFeedIds = mutableSetOf<String>()

    /** Clears the endless-feed dedup set so a pull-to-refresh produces fresh content. */
    fun resetFeed() {
        seenFeedIds.clear()
    }

    /** Excludes the given video ids from the endless feed so they don't duplicate the top section. */
    fun markSeen(ids: Collection<String>) {
        seenFeedIds.addAll(ids)
    }

    /**
     * Loads the next batch of the endless home feed, YouTube-home style: one search per batch
     * (user scrolling paces the requests), query picked mostly from the user's watched interests
     * (personalized) and occasionally from a varied default pool, results deduped against
     * everything already shown and shuffled inside the batch so it feels mixed.
     * Returns empty when YouTube flags the IP (HTTP 400) or the pool is exhausted — the UI stops.
     */
    suspend fun nextFeedPage(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val interests = com.weebflix.app.data.model.YouTubeFeedPrefs.getInterestQueries()
        repeat(3) { attempt ->
            // Round 0: top interest (if any). Round 1: mostly interest, sometimes the pool.
            // Round 2: always the default pool, so the feed can't stall on a dead/flagged interest.
            val query = if (interests.isEmpty() || attempt >= 2) feedQueries.random()
            else if (attempt == 1 && Math.random() < 0.4) feedQueries.random()
            else interests.random()
            val fetched = searchFresh(query)
            if (fetched.isEmpty()) return@withContext emptyList()
            val fresh = fetched.filter { seenFeedIds.add(it.videoId) }
            if (fresh.isNotEmpty()) return@withContext fresh.shuffled().take(15)
            delay(700)
        }
        emptyList()
    }

    suspend fun searchVideos(query: String, params: String? = null): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        fetchSearch(query, params).take(30)
    }

    suspend fun getVideoDetail(videoId: String): YouTubeVideoDetail = withContext(Dispatchers.IO) {
        try {
            val res = post("player") {
                it.put("videoId", videoId)
                    .put("contentCheckOk", true)
                    .put("racyCheckOk", true)
            }
            val json = res.json
            val details = json?.optJSONObject("videoDetails") ?: return@withContext YouTubeVideoDetail(videoId = videoId)
            val microformat = json.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
            YouTubeVideoDetail(
                videoId = videoId,
                title = details.optString("title", ""),
                author = details.optString("author", ""),
                channelThumb = microformat?.optJSONArray("thumbnail")
                    ?.let { thumbArr -> if (thumbArr.length() > 0) thumbArr.getJSONObject(0).optString("url", "") else "" } ?: "",
                thumbnail = pickThumb(details.optJSONObject("thumbnail")),
                views = microformat?.optString("viewCount", "") ?: details.optString("viewCount", ""),
                published = microformat?.optString("publishDate", "") ?: "",
                likes = "",
                description = details.optString("shortDescription", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "getVideoDetail error: ${e.message}")
            YouTubeVideoDetail(videoId = videoId)
        }
    }

    /** The channel's "Videos" tab params (browse params `EgZ2aWRlb3PyBgQKAjoA`) — shows every
     *  upload by the owner, newest first, paginated via continuation. */
    private val CHANNEL_VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

    /** First page of a channel page: header (name/avatar/banner/subscriber count) + the Videos
     *  tab grid. YouTube removed `c4TabbedHeaderRenderer` (2026): the no-params browse now carries
     *  the header as `header.pageHeaderRenderer.content.pageHeaderViewModel`, and the Videos tab
     *  as `continuationContents.richGridContinuation` (lockupViewModel grid) when fetched with
     *  [CHANNEL_VIDEOS_PARAMS]. Two browse calls — one for the header, one for the videos. */
    suspend fun getChannelDetail(channelId: String): YouTubeChannelDetail = withContext(Dispatchers.IO) {
        if (channelId.isEmpty()) return@withContext YouTubeChannelDetail()

        // Header: name/avatar/banner/subscriber count from the new pageHeaderViewModel.
        val headerJson = post("browse") { it.put("browseId", channelId) }.json
        var name = ""
        var avatar = ""
        var banner = ""
        var subs = ""
        headerJson?.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")
            ?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")?.let { vm ->
                name = vm.optJSONObject("title")?.optJSONObject("dynamicTextViewModel")
                    ?.optJSONObject("text")?.optString("content", "").orEmpty()
                avatar = vm.optJSONObject("image")?.optJSONObject("decoratedAvatarViewModel")
                    ?.optJSONObject("avatar")?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")?.optJSONArray("sources")?.let { pickFromSources(it) }.orEmpty()
                banner = vm.optJSONObject("banner")?.optJSONObject("imageBannerViewModel")
                    ?.optJSONObject("image")?.optJSONArray("sources")?.let { pickFromSources(it) }.orEmpty()
                val rows = vm.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
                    ?.optJSONArray("metadataRows")
                subs = extractSubscriberFromRows(rows)
            }

        // Videos tab grid.
        val videosRes = post("browse") {
            it.put("browseId", channelId)
                .put("params", CHANNEL_VIDEOS_PARAMS)
                .put("continuation", "")
        }
        val videosJson = videosRes.json
        val videos = if (videosJson != null) collectChannelVideos(videosJson) else emptyList()
        val continuation = if (videosJson != null) findContinuationToken(videosJson) else ""

        // Fallback: `metadata.channelMetadataRenderer` (present in both responses) carries a
        // high-res avatar + name when the new header layout was not parsed.
        videosJson?.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")?.let { meta ->
            if (name.isEmpty()) name = meta.optString("title", "")
            if (avatar.isEmpty()) avatar = meta.optJSONObject("avatar")?.let { pickThumb(it) }.orEmpty()
        }

        YouTubeChannelDetail(
            channelId = channelId,
            channelName = name,
            channelThumb = avatar,
            channelBanner = banner,
            subscriberCount = subs,
            videos = videos,
            continuation = continuation
        )
    }

    /** Next page of a channel's Videos tab via the continuation token. YouTube switched the
     *  continuation response from `onResponseReceivedEndpoints[]` to `onResponseReceivedActions[]`
     *  (2026) — both layouts are handled. */
    suspend fun getChannelNextPage(continuation: String): YouTubeChannelDetail = withContext(Dispatchers.IO) {
        if (continuation.isEmpty()) return@withContext YouTubeChannelDetail()
        val res = post("browse") { it.put("continuation", continuation) }
        val json = res.json ?: return@withContext YouTubeChannelDetail()
        val items = json.optJSONArray("onResponseReceivedActions")?.optJSONObject(0)
            ?.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")
            ?: json.optJSONArray("onResponseReceivedEndpoints")?.optJSONObject(0)
                ?.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")
        val videos = if (items != null) {
            val out = mutableListOf<JSONObject>()
            collectVideoRenderers(items, out)
            val lockups = mutableListOf<JSONObject>()
            collectLockupViewModel(items, lockups)
            (out.mapNotNull { parseVideoRenderer(it) } + lockups.mapNotNull { parseLockupViewModel(it) })
                .distinctBy { it.videoId }
        } else emptyList()
        YouTubeChannelDetail(
            videos = videos,
            continuation = if (items != null) findContinuationToken(items) else ""
        )
    }

    /** Cari teks jumlah subscriber pada metadata channel baru (pageHeaderViewModel). Row parts
     *  bentuknya seperti [@handle, "1.2M subscribers", "250 videos"] — jumlah WAJIB dicari lewat
     *  keyword, bukan diambil as part[0] (itu @handle). Fallback = part non-kosong pertama. */
    private fun extractSubscriberFromRows(rows: JSONArray?): String {
        if (rows == null) return ""
        var fallback = ""
        for (i in 0 until rows.length()) {
            val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
            for (j in 0 until parts.length()) {
                val text = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content", "").orEmpty()
                if (text.isBlank()) continue
                if (fallback.isEmpty()) fallback = text
                if (text.contains("subscriber", ignoreCase = true)) return text
            }
        }
        return fallback
    }

    /** Collects every video (videoRenderer/compactVideoRenderer + lockupViewModel) in a JSON tree. */
    private fun collectChannelVideos(json: JSONObject): List<YouTubeVideo> {
        val out = mutableListOf<JSONObject>()
        collectVideoRenderers(json, out)
        val lockups = mutableListOf<JSONObject>()
        collectLockupViewModel(json, lockups)
        return (out.mapNotNull { parseVideoRenderer(it) } + lockups.mapNotNull { parseLockupViewModel(it) })
            .distinctBy { it.videoId }
    }

    companion object {
        fun extractVideoId(url: String): String {
            if (url.startsWith("youtube://")) return url.removePrefix("youtube://")
            val patterns = listOf(
                Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})"""),
                Regex("""^([A-Za-z0-9_-]{11})$""")
            )
            for (p in patterns) {
                p.find(url)?.let { return it.groupValues[1] }
            }
            return url
        }
    }

    // ---- AnimeProvider interface ----

    override suspend fun getLatestEpisodes(page: Int): List<Episode> {
        return getHome().recommended.map {
            Episode(
                title = it.title,
                url = it.url,
                imageUrl = it.thumbnail,
                episodeNumber = it.duration,
                uploadDate = it.published,
                postedBy = it.channel
            )
        }
    }

    override suspend fun getOngoingAnime(page: Int): List<Anime> {
        return getHome().music.map { it.toAnime() }
    }

    override suspend fun getPopularAnime(page: Int): List<Anime> {
        return getHome().trending.map { it.toAnime() }
    }

    override suspend fun searchAnime(query: String): List<Anime> {
        return searchVideos(query).map { it.toAnime() }
    }

    override suspend fun getAnimeDetail(url: String): AnimeDetail {
        val videoId = extractVideoId(url)
        val detail = getVideoDetail(videoId)
        val anime = Anime(
            title = detail.title,
            url = "https://youtu.be/$videoId",
            imageUrl = detail.thumbnail,
            episode = "1",
            type = detail.author,
            score = detail.views,
            synopsis = detail.description,
            latestUpdate = detail.published
        )
        return AnimeDetail(
            anime = anime,
            episodes = listOf(Episode(title = detail.title, url = "https://youtu.be/$videoId", imageUrl = detail.thumbnail, episodeNumber = "1"))
        )
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> {
        val videoId = extractVideoId(episodeUrl)
        if (videoId.isEmpty()) return emptyList()
        return listOf(
            VideoServer(
                name = "YouTube",
                url = "https://youtu.be/$videoId",
                videoUrl = "youtube://$videoId",
                dataType = "yt"
            )
        )
    }

    override suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String {
        return server.videoUrl
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation {
        return EpisodeNavigation()
    }

    private fun YouTubeVideo.toAnime() = Anime(
        title = title,
        url = url,
        imageUrl = thumbnail,
        episode = duration,
        type = channel,
        score = views,
        status = published,
        studio = channel,
        latestUpdate = published
    )
}
