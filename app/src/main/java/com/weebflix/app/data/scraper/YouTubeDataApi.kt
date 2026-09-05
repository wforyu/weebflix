package com.weebflix.app.data.scraper

import android.util.Log
import com.weebflix.app.data.auth.YouTubeAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Authenticated YouTube **Data API v3** client (www.googleapis.com/youtube/v3).
 *
 * Unlike the innertube endpoints (youtubei/v1) — which YouTube currently rejects with
 * HTTP 400 INVALID_ARGUMENT for OAuth bearer requests (open YouTube.js #916/#803, affects
 * every third-party client) — the Data API v3 accepts the SAME OAuth access token from
 * [YouTubeAuthManager]. This is what powers the GoTube-style account features that the
 * innertube path could never reach:
 *   - subscriptions list + subscribe/unsubscribe        (subscriptions.*)
 *   - the "Subscriptions" home feed                     (activities?home=true, 1 call)
 *   - watch-history sync                                (playlistItems?playlistId=HL)
 *   - like / dislike / remove like                      (videos/rate)
 */
object YouTubeDataApi {

    private const val TAG = "YouTubeDataApi"
    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private const val MAX_PER_PAGE = 50

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun token(): String? = YouTubeAuthManager.getAccessToken()

    // ---- Subscriptions ----

    /** The logged-in user's own channel (channels.list?mine=true). Returns null if the account
     *  has no channel or the request fails. */
    suspend fun getMyChannel(): YouTubeChannel? = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext null
        val json = authGet(t, "$BASE/channels?part=snippet&mine=true") ?: return@withContext null
        val items = json.optJSONArray("items") ?: return@withContext null
        if (items.length() == 0) return@withContext null
        val item = items.getJSONObject(0)
        val sn = item.optJSONObject("snippet")
        YouTubeChannel(
            channelId = item.optString("id", ""),
            channelName = sn?.optString("title", "") ?: "",
            channelThumb = bestThumb(sn?.optJSONObject("thumbnails"))
        )
    }

    /** The authenticated user's subscribed channels. Also refreshes [YouTubeSubscriptionStore]. */
    suspend fun getMySubscriptions(maxResults: Int = 40): List<YouTubeChannel> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext emptyList()
        val json = authGet(t, "$BASE/subscriptions?part=snippet&mine=true&maxResults=$maxResults")
            ?: return@withContext emptyList()
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        val channels = (0 until items.length()).mapNotNull { i ->
            items.optJSONObject(i)?.let { parseSubscription(it) }
        }.filter { it.channelId.isNotEmpty() }
        Log.i(TAG, "getMySubscriptions: ${channels.size} channels")
        if (channels.isNotEmpty()) {
            com.weebflix.app.data.scraper.YouTubeSubscriptionStore.replaceAll(channels)
        }
        channels
    }

    /** The "Subscriptions" feed: the latest uploads from channels the user subscribed to.
     *  `activities?home=true` is unreliable (often returns empty), so we instead:
     *  1. list the user's subscriptions (mine=true) — 1 call
     *  2. fetch each channel's uploads playlist id (channels.list contentDetails) — 1 call
     *  3. fetch the newest item of each uploads playlist (playlistItems.list maxResults=1) — N calls
     *  Falls back to [getMySubscriptions] (channel list) if uploads can't be fetched. */
    suspend fun getSubscriptionsFeed(maxResults: Int = 25): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val subs = getMySubscriptions()
        if (subs.isEmpty()) {
            Log.i(TAG, "getSubscriptionsFeed: no subscriptions")
            return@withContext emptyList()
        }

        val t = token() ?: return@withContext emptyList()

        val channelIds = subs.map { it.channelId }.distinct().take(50)
        val idsParam = channelIds.joinToString(",") { enc(it) }
        val channelsJson = authGet(t, "$BASE/channels?part=contentDetails&id=$idsParam&maxResults=50")
            ?: return@withContext emptyList()
        val uploadsByChannel = HashMap<String, String>()
        val channelItems = channelsJson.optJSONArray("items")
        if (channelItems != null) {
            for (i in 0 until channelItems.length()) {
                val it = channelItems.optJSONObject(i) ?: continue
                val cid = it.optString("id", "")
                val uploads = it.optJSONObject("contentDetails")
                    ?.optJSONObject("relatedPlaylists")?.optString("uploads", "") ?: ""
                if (cid.isNotEmpty() && uploads.isNotEmpty()) uploadsByChannel[cid] = uploads
            }
        }

        val results = ArrayList<YouTubeVideo>()
        for ((cid, playlistId) in uploadsByChannel.entries) {
            if (results.size >= maxResults) break
            val json = authGet(t, "$BASE/playlistItems?part=snippet&playlistId=${enc(playlistId)}&maxResults=1")
                ?: continue
            val first = json.optJSONArray("items")?.optJSONObject(0) ?: continue
            val sn = first.optJSONObject("snippet") ?: continue
            val videoId = sn.optJSONObject("resourceId")?.optString("videoId", "") ?: ""
            val title = sn.optString("title", "")
            if (videoId.isEmpty() || title.isEmpty()) continue
            results += YouTubeVideo(
                videoId = videoId,
                title = title,
                channel = sn.optString("channelTitle", ""),
                thumbnail = bestThumb(sn.optJSONObject("thumbnails")),
                published = relTime(sn.optString("publishedAt", ""))
            )
        }
        Log.i(TAG, "getSubscriptionsFeed: ${results.size} uploads from ${uploadsByChannel.size} channels")
        results
    }

    /** Exact "is this channel subscribed?" — the forChannelId lookup returns the subscription
     *  (or nothing) regardless of list page size. Caches the result in [YouTubeSubscriptionStore]
     *  and drops a stale cache entry when the user unsubscribed elsewhere. */
    suspend fun isSubscribedExact(channelId: String): Boolean = withContext(Dispatchers.IO) {
        if (channelId.isEmpty()) return@withContext false
        val t = token() ?: return@withContext false
        val lookup = authGet(t, "$BASE/subscriptions?part=snippet&mine=true&forChannelId=${enc(channelId)}")
        val items = lookup?.optJSONArray("items")
        val item = items?.optJSONObject(0)
        if (item != null) {
            val sn = item.optJSONObject("snippet")
            val resource = sn?.optJSONObject("resourceId")
            com.weebflix.app.data.scraper.YouTubeSubscriptionStore.add(
                YouTubeChannel(
                    channelId = resource?.optString("channelId", "") ?: channelId,
                    channelName = sn?.optString("title", "") ?: "",
                    channelThumb = bestThumb(sn?.optJSONObject("thumbnails")),
                    subscriptionId = item.optString("id", "")
                )
            )
            true
        } else {
            com.weebflix.app.data.scraper.YouTubeSubscriptionStore.remove(channelId)
            false
        }
    }

    /** Subscribes/unsubscribes a channel. Keeps [YouTubeSubscriptionStore] in sync. */
    suspend fun setSubscription(channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (channelId.isEmpty()) return@withContext false
        val t = token() ?: return@withContext false
        if (subscribe) {
            val body = JSONObject().put(
                "snippet", JSONObject().put(
                    "resourceId", JSONObject()
                        .put("kind", "youtube#channel")
                        .put("channelId", channelId)
                )
            )
            val resp = authPostJson(t, "$BASE/subscriptions?part=snippet", body)
            if (resp != null) {
                val newId = resp.optString("id", "")
                if (newId.isNotEmpty()) {
                    com.weebflix.app.data.scraper.YouTubeSubscriptionStore.add(
                        YouTubeChannel(
                            channelId = channelId,
                            channelName = resp.optJSONObject("snippet")?.optString("title", "") ?: "",
                            channelThumb = bestThumb(resp.optJSONObject("snippet")?.optJSONObject("thumbnails")),
                            subscriptionId = newId
                        )
                    )
                }
                true
            } else {
                // e.g. HTTP 400 "The subscription that you are trying to create already exists" —
                // the user is already subscribed server-side. Verify + sync the store, treat as success.
                isSubscribedExact(channelId)
            }
        } else {
            var ok = false
            val subId = com.weebflix.app.data.scraper.YouTubeSubscriptionStore.subscriptionIdOf(channelId)
            if (subId.isNotEmpty()) {
                ok = authDelete(t, "$BASE/subscriptions?id=${enc(subId)}")
            }
            if (!ok) {
                val lookup = authGet(t, "$BASE/subscriptions?part=id&mine=true&forChannelId=${enc(channelId)}")
                val id = lookup?.optJSONArray("items")?.optJSONObject(0)?.optString("id", "") ?: ""
                if (id.isNotEmpty()) ok = authDelete(t, "$BASE/subscriptions?id=${enc(id)}")
            }
            if (ok) {
                com.weebflix.app.data.scraper.YouTubeSubscriptionStore.remove(channelId)
            }
            ok
        }
    }

    // ---- Engagement (like / dislike / none) ----

    /** Sets the authenticated user's rating: "like", "dislike" or "none". Returns true on success. */
    suspend fun rateVideo(videoId: String, rating: String): Boolean = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext false
        authPostForm(t, "$BASE/videos/rate?id=${enc(videoId)}&rating=${enc(rating)}")
    }

    /** Best-effort "did the user like this video?" — "like" when liked, "" otherwise.
     *  `videos.list` forbids combining `id` with `myRating` (HTTP 400), so we page
     *  through the user's liked videos and scan for the target id. */
    suspend fun getMyRating(videoId: String): String = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext ""
        var pageToken = ""
        while (true) {
            val url = "$BASE/videos?part=statistics&myRating=like&maxResults=50" +
                (if (pageToken.isEmpty()) "" else "&pageToken=${enc(pageToken)}")
            val json = authGet(t, url) ?: return@withContext ""
            val items = json.optJSONArray("items") ?: return@withContext ""
            for (i in 0 until items.length()) {
                val id = items.optJSONObject(i)?.optString("id", "") ?: ""
                if (id == videoId) return@withContext "like"
            }
            pageToken = json.optString("nextPageToken", "")
            if (pageToken.isEmpty()) return@withContext ""
        }
        "" // unreachable — keeps the withContext lambda's type as String
    }

    // ---- Watch history (playlistId=HL = the account's watch history) ----

    /** The authenticated user's server-side watch history (most recent first). */
    suspend fun getWatchHistory(maxResults: Int = MAX_PER_PAGE): List<YouTubeHistoryItem> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext emptyList()
        val json = authGet(t, "$BASE/playlistItems?part=snippet&playlistId=HL&maxResults=$maxResults")
            ?: return@withContext emptyList()
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
            val videoId = snippet.optJSONObject("resourceId")?.optString("videoId", "") ?: ""
            val title = snippet.optString("title", "")
            if (videoId.isEmpty() || title.isEmpty()) return@mapNotNull null
            val publishedAt = snippet.optString("publishedAt", "")
            YouTubeHistoryItem(
                video = YouTubeVideo(
                    videoId = videoId,
                    title = title,
                    channel = snippet.optString("channelTitle", ""),
                    thumbnail = bestThumb(snippet.optJSONObject("thumbnails")),
                    published = relTime(publishedAt)
                ),
                watchedAtMs = parseIso(publishedAt)
            )
        }
    }

    // ---- HTTP helpers ----

    private fun authGet(token: String, url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GET ${resp.code} ${url.take(110)}: ${body.take(120)}")
                    null
                } else {
                    JSONObject(body)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "authGet error: ${e.message}")
            null
        }
    }

    private fun authPostJson(token: String, url: String, body: JSONObject): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.w(TAG, "POST ${resp.code} ${url.take(110)}: ${bodyStr.take(120)}")
                    null
                } else {
                    JSONObject(bodyStr)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "authPostJson error: ${e.message}")
            null
        }
    }

    private fun authPostForm(token: String, url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "POST ${resp.code} ${url.take(110)}: ${resp.body?.string()?.take(120)}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "authPostForm error: ${e.message}")
            false
        }
    }

    private fun authDelete(token: String, url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "DELETE ${resp.code} ${url.take(110)}: ${resp.body?.string()?.take(120)}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "authDelete error: ${e.message}")
            false
        }
    }

    // ---- parsing helpers ----

    private fun parseSubscription(sub: JSONObject): YouTubeChannel {
        val sn = sub.optJSONObject("snippet")
        val resource = sn?.optJSONObject("resourceId")
        return YouTubeChannel(
            channelId = resource?.optString("channelId", "") ?: "",
            channelName = sn?.optString("title", "") ?: "",
            channelThumb = bestThumb(sn?.optJSONObject("thumbnails")),
            subscriptionId = sub.optString("id", "")
        )
    }

    /** Picks the best thumbnail from a Data API `thumbnails` object (high > medium > default). */
    private fun bestThumb(thumbs: JSONObject?): String {
        if (thumbs == null) return ""
        for (k in listOf("high", "medium", "default", "maxres", "standard")) {
            val u = thumbs.optJSONObject(k)?.optString("url", "") ?: ""
            if (u.isNotEmpty()) return u
        }
        return ""
    }

    private fun parseIso(iso: String): Long {
        if (iso.isEmpty()) return 0L
        return try {
            val clean = if (iso.contains('.')) iso.substringBeforeLast('.') + "Z" else iso
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(clean)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun relTime(iso: String): String {
        val t = parseIso(iso)
        if (t <= 0L) return ""
        val diff = System.currentTimeMillis() - t
        if (diff < 60_000L) return "Baru saja"
        val min = diff / 60_000L
        return when {
            min < 60L -> "$min menit lalu"
            min < 1440L -> "${min / 60} jam lalu"
            min < 10080L -> "${min / 1440} hari lalu"
            min < 43200L -> "${min / 10080} minggu lalu"
            min < 525600L -> "${min / 43200} bulan lalu"
            else -> "${min / 525600} tahun lalu"
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
