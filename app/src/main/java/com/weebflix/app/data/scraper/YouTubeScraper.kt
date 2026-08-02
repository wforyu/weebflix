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
    private val INVIOUS_INSTANCES = listOf("https://inv.nadeko.net", "https://yewtu.be", "https://invidious.nerdvpn.de")

    private data class YtClient(val name: String, val version: String, val sdk: Int, val key: String, val ua: String)

    private val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private val clients = listOf(
        YtClient("WEB", "2.20260731.00.00", 0, "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8", WEB_UA),
        YtClient("ANDROID_VR", "1.55.3", 30, "AIzaSyB9VGVgUmYc0HeBp5dHnjg1WxNb0qk2X3k", "com.google.android.youtube/19.09.37 (Linux; U; Android 13; en_US)")
    )

    private data class YtResult(val json: JSONObject?, val flagged: Boolean)

    private fun context(c: YtClient) = JSONObject()
        .put("client", JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", c.version)
            .apply { if (c.sdk > 0) put("androidSdkVersion", c.sdk) }
            .put("hl", "en")
            .put("gl", "US")
            .put("utcOffsetMinutes", 0))

    /**
     * Rate-limit aware innertube POST. YouTube flags the IP (HTTP 400 "invalid argument")
     * for ~60-90s after a burst of requests, so: try the primary client once; a 400 means
     * the IP is flagged -> do NOT retry with other clients (would extend the flag); only a
     * non-400 failure falls through to the secondary client.
     */
    private fun post(endpoint: String, fill: (JSONObject) -> Unit): YtResult {
        for (c in clients) {
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
                        continue
                    }
                    Log.d(TAG, "$endpoint[${c.name}] OK len=${bodyStr.length} head=${bodyStr.take(150)}")
                    return YtResult(JSONObject(bodyStr), flagged = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "$endpoint[${c.name}] error: ${e.message}")
            }
        }
        return YtResult(null, flagged = false)
    }

    private fun fetchSearch(query: String, params: String? = null): List<YouTubeVideo> {
        val res = post("search") { it.put("query", query); if (!params.isNullOrEmpty()) it.put("params", params) }
        val json = res.json ?: return emptyList()
        val out = mutableListOf<JSONObject>()
        collectVideoRenderers(json, out)
        return out.mapNotNull { parseVideoRenderer(it) }.distinctBy { it.videoId }
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
        val channelThumb = r.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?: r.optJSONObject("channelThumbnail")
        return YouTubeVideo(
            videoId = videoId,
            title = runsText(r.optJSONObject("title")),
            channel = byline?.optJSONObject(0)?.optString("text", "") ?: "",
            channelThumb = channelThumb?.let { pickThumb(it) } ?: "",
            thumbnail = pickThumb(r.optJSONObject("thumbnail")),
            duration = r.optJSONObject("lengthText")?.let { runsText(it) } ?: "",
            views = r.optJSONObject("viewCountText")?.let { runsText(it) } ?: "",
            published = r.optJSONObject("publishedTimeText")?.let { runsText(it) } ?: ""
        )
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

    // ---- public API used by the YouTube UI ----

    private var homeCache: YouTubeHome? = null
    private var homeCacheTime = 0L

    /** Fetches the home feed with pacing: one section at a time, ~3s apart, and stops the
     *  moment YouTube flags the IP (HTTP 400) instead of hammering it. Cached for 5 min so
     *  returning to the tab never re-scrapes. Built from search rows: FEwhat_to_watch returns
     *  only a sign-in nudge (feedNudgeRenderer) and FEtrending is rejected (400 "invalid
     *  argument") for a logged-out user on this network — search is the reliable endpoint. */
    suspend fun getHome(): YouTubeHome = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        homeCache?.let { if (now - homeCacheTime < 5 * 60_000) return@withContext it }

        val recommended = fetchSearch("trending indonesia")
        if (recommended.isEmpty()) {
            Log.w(TAG, "home: search empty/flagged, aborting multi-section load")
            val home = YouTubeHome(recommended = emptyList(), trending = emptyList(), music = emptyList())
            homeCache = home; homeCacheTime = now
            return@withContext home
        }

        delay(3000)
        val trending = fetchSearch("viral youtube indonesia")

        delay(3000)
        val music = fetchSearch("musik indonesia terbaru")

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

    /**
     * Loads the next batch of the endless home feed, YouTube-home style: one search per batch
     * (user scrolling paces the requests), query picked at random from a varied pool, results
     * deduped against everything already shown and shuffled inside the batch so it feels mixed.
     * Returns empty when YouTube flags the IP (HTTP 400) or the pool is exhausted — the UI stops.
     */
    suspend fun nextFeedPage(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        repeat(3) {
            val query = feedQueries.random()
            val fetched = fetchSearch(query)
            if (fetched.isEmpty()) return@withContext emptyList()
            val fresh = fetched.filter { seenFeedIds.add(it.videoId) }
            if (fresh.isNotEmpty()) return@withContext fresh.shuffled().take(15)
            delay(1200)
        }
        emptyList()
    }

    suspend fun searchVideos(query: String, params: String? = null): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val direct = fetchSearch(query, params)
        if (direct.isNotEmpty()) {
            direct.take(30)
        } else {
            Log.w(TAG, "search innertube empty, trying Invidious")
            fetchSearchInvidious(query, params).take(30)
        }
    }

    private fun fetchSearchInvidious(query: String, params: String?): List<YouTubeVideo> {
        for (base in INVIOUS_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url("$base/api/v1/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=video")
                    .addHeader("User-Agent", WEB_UA)
                    .build()
                val resp = client.newCall(request).execute()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "invidious search $base HTTP ${resp.code}")
                    continue
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = mutableListOf<YouTubeVideo>()
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val videoId = v.optString("videoId", "")
                    if (videoId.isEmpty()) continue
                    val thumbs = v.optJSONArray("videoThumbnails")
                    val thumb = if (thumbs != null && thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url", "") else ""
                    val cThumbs = v.optJSONArray("authorThumbnails")
                    val cThumb = if (cThumbs != null && cThumbs.length() > 0) cThumbs.getJSONObject(cThumbs.length() - 1).optString("url", "") else ""
                    out += YouTubeVideo(
                        videoId = videoId,
                        title = v.optString("title", ""),
                        channel = v.optString("author", ""),
                        channelThumb = cThumb,
                        thumbnail = thumb,
                        duration = formatDuration(v.optLong("lengthSeconds", 0)),
                        views = formatCount(v.optLong("viewCount", 0)),
                        published = v.optString("publishedText", "")
                    )
                }
                if (out.isNotEmpty()) return out
            } catch (e: Exception) {
                Log.w(TAG, "invidious search $base failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun formatDuration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatCount(n: Long): String {
        if (n <= 0) return ""
        return when {
            n >= 1_000_000_000 -> "%.1f M ditonton".format(n / 1_000_000_000.0)
            n >= 1_000_000 -> "%.1f M ditonton".format(n / 1_000_000.0)
            n >= 1_000 -> "%.1f rb ditonton".format(n / 1_000.0)
            else -> "$n ditonton"
        }
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
