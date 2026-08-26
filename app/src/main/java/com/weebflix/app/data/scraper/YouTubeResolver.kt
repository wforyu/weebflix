package com.weebflix.app.data.scraper

import android.util.Log
import com.weebflix.app.data.auth.YouTubeAuthManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
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
    private const val INNERTUBE_ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_MzhuOLXZ0"
    private const val INNERTUBE_ANDROID_MUSIC_KEY = "AIzaSyAOghZGza2MQSZkYuz4VlJ4v5wZ7Y4W4sQ"
    private const val INNERTUBE_IOS_KEY = "AIzaSyB-63vPrnThHnHxe9cQ9QZQN9QZ9QZQZQ"
    private const val API = "https://www.youtube.com/youtubei/v1/player?key=%s&prettyPrint=false"
    private var poTokenManagerInit = false

    /** In-memory cookie jar to persist session cookies (VISITOR_INFO1_LIVE etc.) across requests. */
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val key = url.host
                    val existing = cookieStore[key]?.toMutableList() ?: mutableListOf()
                    for (c in cookies) {
                        existing.removeAll { it.name == c.name }
                        existing.add(c)
                    }
                    cookieStore[key] = existing
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .build()
    }

    private val memo = ConcurrentHashMap<String, ResolvedYouTube>()

    /** Drops cached resolutions (e.g. after login/logout so blocked results are re-fetched with auth). */
    fun clearMemo() {
        memo.clear()
    }

    @Volatile private var visitorData: String? = null
    private val visitorLock = Any()

    /** Drops the cached visitor id so the next resolve bootstraps a fresh one (e.g. after a bot-gate). */
    fun resetVisitor() {
        visitorData = null
    }

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

    fun initPoToken(context: android.content.Context) {
        if (poTokenManagerInit) return
        poTokenManagerInit = true
        Thread {
            try {
                PoTokenManager.init(context)
            } catch (e: Exception) {
                Log.w(TAG, "PoToken init failed: ${e.message}")
            }
        }.start()
    }

    fun resolve(videoId: String): ResolvedYouTube {
        memo[videoId]?.let { return it }

        val clients = listOf(
            // VISIONOS: Apple Vision Pro client — new default (yt-dlp 2026.08.19).
            // Returns direct adaptive URLs + m3u8. NO PO token needed. Best no-auth client.
            ClientContext(
                "VISIONOS", "1.02", 0, embed = false, key = INNERTUBE_WEB_KEY,
                ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                deviceMake = "Apple", deviceModel = "RealityDevice17,1",
                osName = "visionOS", osVersion = "26.5.23O471"
            ),
            // ANDROID 21.x: still returns direct URLs (not SABR) with correct UA
            ClientContext(
                "ANDROID", "21.26.364", 30, embed = false, key = INNERTUBE_ANDROID_KEY,
                ua = "com.google.android.youtube/21.26.364 (Linux; U; Android 14; en_US; sdk_gphone64_x86_64 Build/SE1A.220630.002.A1)",
                osName = "Android", osVersion = "14", deviceModel = "sdk_gphone64_x86_64",
                usePoToken = true
            ),
            // ANDROID 19.x: older, may still return direct URLs
            ClientContext(
                "ANDROID", "19.29.37", 30, embed = false, key = INNERTUBE_ANDROID_KEY,
                ua = "com.google.android.youtube/19.29.37 (Linux; U; Android 14; en_US; sdk_gphone64_x86_64 Build/SE1A.220630.002.A1)",
                osName = "Android", osVersion = "14", deviceModel = "sdk_gphone64_x86_64",
                usePoToken = true
            ),
            // TVHTML5 (Cobalt): may return f=18 (360p muxed)
            ClientContext(
                "TVHTML5", "7.20260707.07.00", 0, embed = false, key = INNERTUBE_ANDROID_KEY,
                ua = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
            ),
            // MWEB: needs GVS PO token
            ClientContext(
                "MWEB", "2.20260731.00.00", 0, embed = false, key = INNERTUBE_WEB_KEY,
                ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
                usePoToken = true
            ),
            // IOS: may work on some networks
            ClientContext(
                "IOS", "19.43.2", 0, embed = false, key = INNERTUBE_IOS_KEY,
                ua = "com.google.ios.youtube/19.43.2 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                osName = "iPhone", osVersion = "18.3.2.22D82", deviceModel = "iPhone16,2", deviceMake = "Apple",
                usePoToken = true
            ),
            // WEB_EMBEDDED: returns f=18 muxed for embeddable videos
            ClientContext("WEB_EMBEDDED_PLAYER", "1.20260731.00.00", 0, embed = true, key = INNERTUBE_WEB_KEY, ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"),
            // ANDROID_MUSIC: needs login
            ClientContext(
                "ANDROID_MUSIC", "6.27.51", 30, embed = false, key = INNERTUBE_ANDROID_MUSIC_KEY,
                ua = "com.google.android.apps.youtube.music/6.27.51 (Linux; U; Android 13; en_US)",
                osName = "Android", osVersion = "13", deviceModel = "Pixel 7",
                usePoToken = true
            ),
            // ANDROID_VR: DEAD since 2026-08-17 (CDN 403s all segments). Last resort only.
            ClientContext(
                "ANDROID_VR", "1.65.10", 32, embed = false, key = INNERTUBE_ANDROID_VR_KEY,
                ua = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android", osVersion = "12L", deviceModel = "Quest 3", deviceMake = "Oculus",
                usePoToken = true,
                skipStreamingPot = true
            )
        )

        fun tryClients(auth: Boolean): Pair<ResolvedYouTube?, String> {
            var blockReason = ""
            for (i in clients.indices) {
                val ctx = clients[i]
                try {
                    val result = fetchPlayer(videoId, ctx, auth)
                    if (result.flagged) return null to blockReason
                    if (result.blockReason.isNotEmpty()) blockReason = result.blockReason
                    if (result.streams != null && !result.streams.isEmpty) return result.streams to blockReason
                } catch (e: Exception) {
                    Log.w(TAG, "client ${ctx.clientName} failed: ${e.message}")
                }
                if (i < clients.lastIndex) Thread.sleep(2500)
            }
            return null to blockReason
        }

        var blockReason = ""
        if (YouTubeAuthManager.getAccessToken() != null) {
            val (authed, authedBr) = tryClients(auth = true)
            blockReason = authedBr
            if (authed != null) {
                memo[videoId] = authed
                return authed
            }
            Log.w(TAG, "authenticated player failed (likely HTTP 400 INVALID_ARGUMENT), retrying anonymous")
        }

        val (anon, anonBr) = tryClients(auth = false)
        if (anon != null) {
            memo[videoId] = anon
            return anon
        }
        if (anonBr.isNotEmpty()) blockReason = anonBr

        // Bot-gate (LOGIN_REQUIRED / "not a bot"): the visitor id can be stale or flagged. Force a
        // fresh visitor bootstrap and retry the first client once before giving up.
        if (blockReason.isNotEmpty()) {
            Log.w(TAG, "bot-gate ($blockReason), re-bootstrapping visitor and retrying ${clients[0].clientName}")
            resetVisitor()
            Thread.sleep(2500)
            val retried = try {
                fetchPlayer(videoId, clients[0], auth = false)
            } catch (e: Exception) {
                null
            }
            if (retried?.streams != null && !retried.streams.isEmpty) {
                memo[videoId] = retried.streams
                return retried.streams
            }
            if (retried != null && retried.blockReason.isNotEmpty()) blockReason = retried.blockReason
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
        val deviceModel: String? = null,
        val deviceMake: String? = null,
        val params: String? = null,
        val usePoToken: Boolean = false,
        val skipStreamingPot: Boolean = false
    )

    private data class PlayerResult(val streams: ResolvedYouTube?, val flagged: Boolean, val blockReason: String = "")

    private fun fetchPlayer(videoId: String, ctx: ClientContext, auth: Boolean): PlayerResult {
        val visitor = ensureVisitor()

        // Always generate streaming PO token (GVS auth) when PoTokenManager is ready
        var poToken: String? = null
        var streamingPot: String? = null
        if (PoTokenManager.isReady()) {
            try {
                val tokens = PoTokenManager.getTokens(videoId, visitor)
                streamingPot = tokens?.streamingPot?.takeIf { it.isNotEmpty() }
                if (ctx.usePoToken) {
                    poToken = tokens?.playerPot?.takeIf { it.isNotEmpty() }
                    if (poToken != null) Log.d(TAG, "PO token obtained for ${ctx.clientName}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "PO token failed for ${ctx.clientName}: ${e.message}")
            }
        }

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
                if (ctx.deviceMake != null) put("deviceMake", ctx.deviceMake)
            }
        val thirdParty = if (ctx.embed) JSONObject().put("embedUrl", "https://www.youtube.com") else null
        val body = JSONObject()
            .put("context", JSONObject().put("client", clientJson).apply { if (thirdParty != null) put("thirdParty", thirdParty) })
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("html5Preference", "HTML5_PREF_WANTS")))
            .apply {
                if (ctx.params != null) put("params", ctx.params)
                if (poToken != null) {
                    put("serviceIntegrityDimensions", JSONObject().put("poToken", poToken))
                }
            }

        val clientNameNumber = when (ctx.clientName) {
            "WEB", "WEB_EMBEDDED_PLAYER", "MWEB" -> 1
            "ANDROID_VR" -> 28
            "ANDROID" -> 3
            "ANDROID_MUSIC" -> 18
            "IOS" -> 5
            "TVHTML5" -> 7
            "VISIONOS" -> 101
            else -> 1
        }

        val request = Request.Builder()
            .url(String.format(API, ctx.key))
            .addHeader("User-Agent", ctx.ua)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("X-Youtube-Client-Name", clientNameNumber.toString())
            .addHeader("X-Youtube-Client-Version", ctx.clientVersion)
            .apply { if (visitor != null) addHeader("X-Goog-Visitor-Id", visitor) }
            .apply {
                if (auth) {
                    // Cookie-based auth (preferred): YouTube blocks Bearer on innertube player,
                    // but accepts Cookie + SAPISIDHASH which is how the web browser authenticates.
                    val cookies = YouTubeAuthManager.getYouTubeCookies()
                    val sapisid = YouTubeAuthManager.getSapisid()
                    if (cookies != null && sapisid != null) {
                        Log.d(TAG, "player ${ctx.clientName} auth=Cookie+SAPISIDHASH")
                        addHeader("Cookie", cookies)
                        addHeader("Authorization", YouTubeAuthManager.buildSapisidHash(sapisid))
                        addHeader("X-Goog-AuthUser", "0")
                    } else {
                        // Fallback: Bearer auth (may fail with HTTP 400 on innertube player)
                        YouTubeAuthManager.getAccessToken()?.let { token ->
                            Log.d(TAG, "player ${ctx.clientName} auth=Bearer (no cookies)")
                            addHeader("Authorization", "Bearer $token")
                            addHeader("X-Goog-AuthUser", "0")
                        }
                    }
                }
            }
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val respJson = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "player ${ctx.clientName} HTTP ${resp.code}")
                // HTTP 400 from non-auth = dead client (IOS/WEB common).
                // 401/403 with cookie auth = stale cookies → clear and continue (not flagged).
                // 401/403 with Bearer auth = account-level flag → re-bootstrap visitor.
                if (auth && (resp.code == 401 || resp.code == 403)) {
                    val hasCookies = YouTubeAuthManager.getYouTubeCookies() != null
                    if (hasCookies) {
                        Log.w(TAG, "Cookie auth rejected (${resp.code}), clearing stale cookies")
                        YouTubeAuthManager.clearYouTubeCookies()
                        // Re-fetch cookies for next attempt
                        YouTubeAuthManager.fetchYouTubeCookies()
                    } else {
                        return PlayerResult(null, flagged = true)
                    }
                }
                return PlayerResult(null, flagged = false)
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

        val streaming = respJson.optJSONObject("streamingData") ?: run {
            Log.w(TAG, "client ${ctx.clientName}: streamingData is null")
            return PlayerResult(null, flagged = false)
        }
        val formats = streaming.optJSONArray("adaptiveFormats") ?: JSONObject.NULL
        if (formats !is org.json.JSONArray || formats.length() == 0) {
            val hls = streaming.optString("hlsManifestUrl", "")
            val dash = streaming.optString("dashManifestUrl", "")
            Log.w(TAG, "client ${ctx.clientName}: adaptiveFormats missing/empty (keys=${streaming.keys().asSequence().toList()} hls=${hls.isNotEmpty()} dash=${dash.isNotEmpty()})")
            // If we have HLS/DASH manifest, use it directly via ExoPlayer
            if (hls.isNotEmpty() || dash.isNotEmpty()) {
                val manifest = if (hls.isNotEmpty()) hls else dash
                val details = respJson.optJSONObject("videoDetails")
                return PlayerResult(
                    ResolvedYouTube(
                        videoId = videoId,
                        title = details?.optString("title") ?: "",
                        author = details?.optString("author") ?: "",
                        views = details?.optString("viewCount") ?: "",
                        thumbnail = pickThumb(details?.optJSONObject("thumbnail")),
                        durationMs = (details?.optLong("lengthSeconds", 0) ?: 0L) * 1000,
                        videoFormats = listOf(YouTubeStream(url = manifest, mimeType = "application/x-mpegURL", isVideo = true)),
                        audioFormats = emptyList()
                    ), flagged = false
                )
            }
            return PlayerResult(null, flagged = false)
        }

        val ops = if (needsCipher(formats)) YouTubeCipher.getCipherOps(client) else null
        val video = mutableListOf<YouTubeStream>()
        val audio = mutableListOf<YouTubeStream>()
        if (formats.length() > 0) {
            val sample = formats.getJSONObject(0)
            val hasUrl = sample.has("url") && sample.opt("url") != null && sample.optString("url", "").isNotEmpty()
            val hasCipher = sample.has("signatureCipher")
            val hasSabr = sample.has("protobufAdaptiveFormat") || sample.has("sabrStreamUrl")
            Log.d(TAG, "client ${ctx.clientName} fmt[0]: itag=${sample.optInt("itag")} mime=${sample.optString("mimeType")} url=$hasUrl signatureCipher=$hasCipher sabr=$hasSabr keys=${sample.keys().asSequence().toList()}")
        }
        for (i in 0 until formats.length()) {
            val f = formats.getJSONObject(i)
            val stream = parseFormat(f, ops, if (ctx.skipStreamingPot) null else streamingPot) ?: continue
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

    private fun parseFormat(f: JSONObject, ops: List<YouTubeCipher.Op>?, streamingPot: String? = null): YouTubeStream? {
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
        // Append streaming PO token (pot) to googlevideo.com URLs for WEB/MWEB clients
        if (streamingPot != null && url.contains("googlevideo.com")) {
            url = if (url.contains("&pot=") || url.contains("?pot=")) url
            else "$url&pot=$streamingPot"
        }
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
