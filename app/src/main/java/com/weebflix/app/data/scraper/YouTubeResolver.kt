package com.weebflix.app.data.scraper

import android.util.Log
import com.weebflix.app.data.auth.YouTubeAuthManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object YouTubeResolver {

    private const val TAG = "YouTubeResolver"
    const val INNERTUBE_WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val INNERTUBE_ANDROID_VR_KEY = "AIzaSyB9VGVgUmYc0HeBp5dHnjg1WxNb0qk2X3k"
    private const val API = "https://www.youtube.com/youtubei/v1/player?key=%s&prettyPrint=false"

    private val INVIOUS_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val memo = ConcurrentHashMap<String, ResolvedYouTube>()

    /** Drops cached resolutions (e.g. after login/logout so blocked results are re-fetched with auth). */
    fun clearMemo() {
        memo.clear()
    }

    @Volatile private var visitorData: String? = null
    private val visitorLock = Any()

    /** Fetches a fresh visitor id via a lightweight WEB search (works on flagged IPs where player is gated). */
    private fun ensureVisitor(): String? {
        visitorData?.let { return it }
        synchronized(visitorLock) {
            visitorData?.let { return it }
            try {
                val clientJson = JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", "2.20260731.00.00")
                    .put("hl", "en")
                    .put("gl", "US")
                val body = JSONObject()
                    .put("context", JSONObject().put("client", clientJson))
                    .put("query", "trending")
                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/search?key=$INNERTUBE_WEB_KEY&prettyPrint=false")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val json = JSONObject(resp.body?.string() ?: "")
                    visitorData = json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "visitor bootstrap failed: ${e.message}")
            }
            return visitorData
        }
    }

    fun resolve(videoId: String): ResolvedYouTube {
        memo[videoId]?.let { return it }

        val clients = listOf(
            ClientContext("ANDROID_VR", "1.55.3", 30, embed = false, key = INNERTUBE_ANDROID_VR_KEY, ua = "com.google.android.youtube/19.09.37 (Linux; U; Android 13; en_US)"),
            ClientContext(
                "ANDROID_MUSIC", "6.27.51", 30, embed = false, key = INNERTUBE_ANDROID_VR_KEY,
                ua = "com.google.android.apps.youtube.music/6.27.51 (Linux; U; Android 13; en_US)",
                osName = "Android", osVersion = "13", deviceModel = "Pixel 7"
            ),
            ClientContext(
                "IOS", "22.41.2", 0, embed = false, key = INNERTUBE_WEB_KEY,
                ua = "com.google.ios.youtube/22.41.2 (iPhone14,3; U; CPU iOS 17_0_0 like Mac OS X; en_US)",
                osName = "iPhone", osVersion = "17.0.0.20D5048c", deviceModel = "iPhone14,3"
            ),
            ClientContext("MWEB", "2.20260731.00.00", 0, embed = false, key = INNERTUBE_WEB_KEY, ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"),
            ClientContext("WEB_EMBEDDED_PLAYER", "1.20260731.00.00", 0, embed = true, key = INNERTUBE_WEB_KEY, ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        )

        var lastError = ""
        var blockReason = ""
        for (i in clients.indices) {
            val ctx = clients[i]
            try {
                val result = fetchPlayer(videoId, ctx)
                if (result.flagged) break
                if (result.blockReason.isNotEmpty()) blockReason = result.blockReason
                if (result.streams != null && !result.streams.isEmpty) {
                    memo[videoId] = result.streams
                    return result.streams
                }
                lastError = "empty for ${ctx.clientName}"
            } catch (e: Exception) {
                lastError = "${ctx.clientName}: ${e.message}"
                Log.w(TAG, "client ${ctx.clientName} failed: ${e.message}")
            }
            if (i < clients.lastIndex) Thread.sleep(2500)
        }

        Log.w(TAG, "innertube failed ($lastError), falling back to Invidious")
        val invidious = fetchInvidious(videoId)
        if (invidious != null) {
            memo[videoId] = invidious
            return invidious
        }

        return ResolvedYouTube(videoId = videoId, title = "", blockReason = blockReason)
    }

    // ---- innertube ----

    private data class ClientContext(
        val clientName: String,
        val clientVersion: String,
        val sdk: Int,
        val embed: Boolean,
        val key: String,
        val ua: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceModel: String? = null
    )

    private data class PlayerResult(val streams: ResolvedYouTube?, val flagged: Boolean, val blockReason: String = "")

    private fun fetchPlayer(videoId: String, ctx: ClientContext): PlayerResult {
        val visitor = ensureVisitor()
        val clientJson = JSONObject()
            .put("clientName", ctx.clientName)
            .put("clientVersion", ctx.clientVersion)
            .put("hl", "en")
            .put("gl", "US")
            .apply {
                if (visitor != null) put("visitorData", visitor)
                if (ctx.sdk > 0) put("androidSdkVersion", ctx.sdk)
                if (ctx.osName != null) put("osName", ctx.osName)
                if (ctx.osVersion != null) put("osVersion", ctx.osVersion)
                if (ctx.deviceModel != null) put("deviceModel", ctx.deviceModel)
            }
        val thirdParty = if (ctx.embed) JSONObject().put("embedUrl", "https://www.youtube.com") else null
        val body = JSONObject()
            .put("context", JSONObject().put("client", clientJson).apply { if (thirdParty != null) put("thirdParty", thirdParty) })
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

        val request = Request.Builder()
            .url(String.format(API, ctx.key))
            .addHeader("User-Agent", ctx.ua)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", "https://www.youtube.com")
            .apply { if (visitor != null) addHeader("X-Goog-Visitor-Id", visitor) }
            .apply {
                // Logged-in player requests bypass the LOGIN_REQUIRED bot-gate that blocks
                // Content-ID / embedding-disabled videos on plain (anonymous) requests.
                YouTubeAuthManager.getAccessToken()?.let { token ->
                    Log.d(TAG, "player ${ctx.clientName} auth=Bearer")
                    addHeader("Authorization", "Bearer $token")
                    addHeader("X-Goog-AuthUser", "0")
                }
            }
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val respJson = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "player ${ctx.clientName} HTTP ${resp.code}")
                return PlayerResult(null, flagged = resp.code == 400)
            }
            JSONObject(resp.body?.string() ?: "")
        }

        val status = respJson.optJSONObject("playabilityStatus")?.optString("status")
        Log.d(TAG, "player ${ctx.clientName} status=$status")
        if (status != "OK") {
            val reason = respJson.optJSONObject("playabilityStatus")?.optString("reason") ?: ""
            Log.w(TAG, "player ${ctx.clientName} status=$status reason=$reason")
            val blocked = status == "LOGIN_REQUIRED" || reason.contains("not a bot", ignoreCase = true) ||
                reason.contains("Sign in", ignoreCase = true) || reason.contains("age", ignoreCase = true)
            return PlayerResult(null, flagged = false, blockReason = if (blocked) reason else "")
        }

        val streaming = respJson.optJSONObject("streamingData") ?: return PlayerResult(null, flagged = false)
        val formats = streaming.optJSONArray("adaptiveFormats") ?: JSONObject.NULL
        if (formats !is org.json.JSONArray || formats.length() == 0) return PlayerResult(null, flagged = false)

        val ops = if (needsCipher(formats)) YouTubeCipher.getCipherOps(client) else null
        val video = mutableListOf<YouTubeStream>()
        val audio = mutableListOf<YouTubeStream>()
        for (i in 0 until formats.length()) {
            val f = formats.getJSONObject(i)
            val stream = parseFormat(f, ops) ?: continue
            if (stream.url.isNullOrEmpty() && stream.url.isEmpty()) continue
            if (stream.isVideo) video += stream else audio += stream
        }
        if (video.isEmpty() || audio.isEmpty()) {
            Log.w(TAG, "client ${ctx.clientName} incomplete: video=${video.size} audio=${audio.size}")
            return PlayerResult(null, flagged = false)
        }

        val details = respJson.optJSONObject("videoDetails")
        return PlayerResult(
            ResolvedYouTube(
                videoId = videoId,
                title = details?.optString("title") ?: "",
                author = details?.optString("author") ?: "",
                views = details?.optString("viewCount") ?: "",
                thumbnail = pickThumb(details?.optJSONObject("thumbnail")),
                durationMs = (details?.optLong("lengthSeconds", 0) ?: 0L) * 1000,
                videoFormats = video,
                audioFormats = audio
            ),
            flagged = false
        )
    }

    private fun needsCipher(formats: org.json.JSONArray): Boolean {
        for (i in 0 until formats.length()) {
            if (formats.getJSONObject(i).has("signatureCipher")) return true
        }
        return false
    }

    private fun parseFormat(f: JSONObject, ops: List<YouTubeCipher.Op>?): YouTubeStream? {
        val mimeType = f.optString("mimeType", "")
        val isVideo = mimeType.startsWith("video/")
        var url = f.optString("url", "")
        if (url.isEmpty() && f.has("signatureCipher")) {
            val cipher = f.optString("signatureCipher", "")
            if (cipher.isNotEmpty()) {
                val deciphered = ops?.let { YouTubeCipher.decipherCipher(cipher, it) } ?: ""
                if (deciphered.isEmpty()) return null
                url = deciphered
            }
        }
        if (url.isEmpty()) return null
        // android/vr clients carry no 'n'; web does — skip if present (would 403)
        if (url.contains("&n=") || url.contains("?n=")) return null
        val init = f.optJSONObject("initRange")
        val idx = f.optJSONObject("indexRange")
        return YouTubeStream(
            url = url,
            mimeType = mimeType,
            bitrate = f.optLong("bitrate", 0),
            width = f.optInt("width", 0),
            height = f.optInt("height", 0),
            contentLength = f.optLong("contentLength", 0),
            itag = f.optInt("itag", 0),
            isVideo = isVideo,
            codecs = Regex("codecs=\"([^\"]+)\"").find(mimeType)?.groupValues?.get(1) ?: "",
            frameRate = f.optInt("fps", 0).takeIf { it > 0 } ?: f.optInt("frameRate", 0),
            initRange = if (init != null) "${init.optString("start", "")}-${init.optString("end", "")}" else "",
            indexRange = if (idx != null) "${idx.optString("start", "")}-${idx.optString("end", "")}" else ""
        )
    }

    private fun rangeOf(f: JSONObject, startKey: String, endKey: String): String {
        val s = f.optLong(startKey, -1)
        val e = f.optLong(endKey, -1)
        return if (s >= 0 && e >= s) "$s-$e" else ""
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

    // ---- Invidious fallback ----

    private fun fetchInvidious(videoId: String): ResolvedYouTube? {
        for (base in INVIOUS_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url("$base/api/v1/videos/$videoId?fields=title,author,authorThumbnails,viewCount,thumbnailUrl,adaptiveFormats")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                    .build()
                val resp = client.newCall(request).execute()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "invidious $base HTTP ${resp.code}")
                    continue
                }
                val json = JSONObject(resp.body?.string() ?: "")
                val video = mutableListOf<YouTubeStream>()
                val audio = mutableListOf<YouTubeStream>()
                val formats = json.optJSONArray("adaptiveFormats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val f = formats.getJSONObject(i)
                        val type = f.optString("type", "")
                        val isVideo = type.startsWith("video/")
                        val stream = YouTubeStream(
                            url = f.optString("url", ""),
                            mimeType = type,
                            bitrate = f.optLong("bitrate", 0),
                            width = f.optInt("width", 0),
                            height = f.optInt("height", 0),
                            contentLength = f.optLong("clen", 0),
                            itag = f.optInt("itag", 0),
                            isVideo = isVideo,
                            codecs = Regex("codecs=\"([^\"]+)\"").find(type)?.groupValues?.get(1) ?: "",
                            frameRate = f.optInt("fps", 0),
                            initRange = rangeOf(f, "initStart", "initEnd"),
                            indexRange = rangeOf(f, "indexStart", "indexEnd")
                        )
                        if (isVideo) video += stream else audio += stream
                    }
                }
                if (video.isNotEmpty() && audio.isNotEmpty()) {
                    val thumbs = json.optJSONArray("authorThumbnails")
                    val channelThumb = if (thumbs != null && thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url", "") else ""
                    return ResolvedYouTube(
                        videoId = videoId,
                        title = json.optString("title", ""),
                        author = json.optString("author", ""),
                        views = json.optString("viewCount", ""),
                        thumbnail = json.optString("thumbnailUrl", ""),
                        durationMs = json.optLong("lengthSeconds", 0) * 1000,
                        videoFormats = video,
                        audioFormats = audio
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "invidious $base failed: ${e.message}")
            }
        }
        return null
    }

    /** Picks the best video format: prefer mp4, height <= 1080, highest height then bitrate. */
    fun pickVideo(formats: List<YouTubeStream>): YouTubeStream? {
        val candidate = formats
            .filter { it.height > 0 }
            .filter { it.height <= 1080 }
            .sortedWith(compareByDescending<YouTubeStream> { it.mimeType.contains("mp4") }
                .thenByDescending { it.height }
                .thenByDescending { it.bitrate })
            .firstOrNull()
        if (candidate != null) return candidate
        return formats.filter { it.height > 0 && it.height <= 1080 }.maxByOrNull { it.height }
            ?: formats.maxByOrNull { it.bitrate }
    }

    /** Picks the best audio format: prefer opus, then mp4, highest bitrate, sane size. */
    fun pickAudio(formats: List<YouTubeStream>): YouTubeStream? {
        return formats
            .sortedWith(compareByDescending<YouTubeStream> { it.mimeType.contains("opus") }
                .thenByDescending { it.mimeType.contains("mp4") }
                .thenByDescending { it.bitrate })
            .firstOrNull()
    }
}
