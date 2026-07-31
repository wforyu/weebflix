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
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SamehadakuScraper : AnimeProvider {

    override val id: String = ProviderFactory.SAMEHADAKU_ID
    override val name: String = "Samehadaku"
    override val defaultBaseUrl: String = "https://v2.samehadaku.how"

    override var baseUrl: String
        get() = ProviderConfig.getBaseUrl(id)
        set(value) {
            ProviderConfig.setBaseUrl(id, value)
        }

    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .cookieJar(cookieJar)
            .build()
    }

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        return response.use {
            val html = it.body?.string() ?: throw Exception("Empty response")
            Jsoup.parse(html)
        }
    }

    private fun postAjax(url: String, body: RequestBody, referer: String = baseUrl): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("Referer", referer)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        return response.use {
            it.body?.string() ?: ""
        }
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .addHeader("Referer", baseUrl)
            .build()

        val response = client.newCall(request).execute()
        return response.use {
            it.body?.string() ?: ""
        }
    }

    override suspend fun getLatestEpisodes(page: Int): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) baseUrl else "$baseUrl/anime-terbaru/page/$page/"
            val doc = fetchDocument(url)
            val episodes = mutableListOf<Episode>()

            doc.select("ul > li[itemscope]").forEach { element ->
                try {
                    val title = element.select("h2.entry-title a").text()
                    val url = element.select("h2.entry-title a").attr("href")
                    val imageUrl = element.select("img.npws, img").first()?.attr("src") ?: ""
                    val episodeNum = element.select("span:has(dashicons-controls-play) author").text()
                    val postedBy = element.select("span:has(dashicons-admin-users) author").text()
                    val date = element.select("span:has(dashicons-calendar)").text()
                        .replace("Released on:", "").trim()

                    if (title.isNotEmpty()) {
                        episodes.add(
                            Episode(
                                title = title,
                                url = url,
                                imageUrl = imageUrl,
                                episodeNumber = episodeNum,
                                uploadDate = date,
                                postedBy = postedBy
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            episodes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getOngoingAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/daftar-anime-2" else "$baseUrl/daftar-anime-2/page/$page/"
            val doc = fetchDocument(url)
            val animeList = mutableListOf<Anime>()

            doc.select(".bs").forEach { element ->
                try {
                    val title = element.select(".tt h2, .tt").text()
                    val url = element.select("a").attr("href")
                    val imageUrl = element.select("img").attr("src")
                    val episode = element.select(".epx, .ep").text()
                    val status = element.select(".Status, .sttp").text()

                    if (title.isNotEmpty()) {
                        animeList.add(
                            Anime(
                                title = title,
                                url = url,
                                imageUrl = imageUrl,
                                episode = episode,
                                status = status
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (animeList.isEmpty()) {
                doc.select(".animepost, .listupd .bsx, .bsx").forEach { element ->
                    try {
                        val title = element.select(".tt h2, .tt, .title").text()
                        val url = element.select("a").attr("href")
                        val imageUrl = element.select("img").attr("src")
                        val episode = element.select(".epx, .ep, .episode").text()

                        if (title.isNotEmpty()) {
                            animeList.add(
                                Anime(
                                    title = title,
                                    url = url,
                                    imageUrl = imageUrl,
                                    episode = episode
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (animeList.isEmpty()) {
                doc.select(".animepost").forEach { element ->
                    try {
                        val title = element.select(".animposx a").attr("title")
                            .ifEmpty { element.select(".data .title").text() }
                        val url = element.select(".animposx a").attr("href")
                        val imageUrl = element.select("img").attr("src")
                        val episode = element.select(".type").text()

                        if (title.isNotEmpty()) {
                            animeList.add(
                                Anime(
                                    title = title,
                                    url = url,
                                    imageUrl = imageUrl,
                                    episode = episode
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            animeList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getPopularAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/daftar-anime-2/?order=popular" else "$baseUrl/daftar-anime-2/page/$page/?order=popular"
            val doc = fetchDocument(url)
            val animeList = mutableListOf<Anime>()

            doc.select("article.animpost").forEach { element ->
                try {
                    val a = element.select(".animposx a").first()
                    val title = element.select(".data .title h2").text()
                    val url = a?.attr("href") ?: ""
                    val imageUrl = element.select(".content-thumb img").let { imgs ->
                        imgs.first()?.attr("data-src")?.ifEmpty { null }
                            ?: imgs.first()?.attr("src") ?: ""
                    }
                    val type = element.select(".content-thumb .type").text()
                    val score = element.select(".score").text()
                        .replace("★", "").replace("Score:", "").trim()
                    val status = element.select(".data .type").last()?.text() ?: ""

                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        animeList.add(
                            Anime(
                                title = title,
                                url = url,
                                imageUrl = imageUrl,
                                episode = type,
                                type = type,
                                status = status,
                                score = score
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            animeList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val doc = fetchDocument("$baseUrl/?s=$encodedQuery")
            val animeList = mutableListOf<Anime>()

            doc.select(".bs, .bsx, .animepost").forEach { element ->
                try {
                    val title = element.select(".tt h2, .tt, .title, h2 a, h2").text()
                    val url = element.select("a").attr("href")
                    val imageUrl = element.select("img").attr("src")
                    val episode = element.select(".epx, .ep, .episode, .type").text()
                    val status = element.select(".Status, .sttp").text()

                    if (title.isNotEmpty()) {
                        animeList.add(
                            Anime(
                                title = title,
                                url = url,
                                imageUrl = imageUrl,
                                episode = episode,
                                status = status
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            animeList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getAnimeDetail(url: String): AnimeDetail = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(url)

            val title = doc.select(".seriestitle, h1.titless, h1.entry-title, .infox h1").text()
            val synopsis = doc.select(".entry-content p, .sinopsis p, .infox .desc").text()
            val imageUrl = doc.select(".thumb img, .poster img, .infox img, .thumbwoad img").first()?.attr("src") ?: ""

            val infoItems = doc.select(".infox .spe span, .info-content .spe span, .info-content .spe")
            var status = ""
            var type = ""
            var totalEp = ""
            var studio = ""
            var season = ""
            var score = ""

            infoItems.forEach { span ->
                val text = span.text().lowercase()
                when {
                    text.contains("status") -> status = span.select("span:last-child, b:last-child").text().ifEmpty { span.text() }
                    text.contains("tipe") || text.contains("type") -> type = span.select("span:last-child, b:last-child").text().ifEmpty { span.text() }
                    text.contains("episode") -> totalEp = span.select("span:last-child, b:last-child").text().ifEmpty { span.text() }
                    text.contains("studio") -> studio = span.select("span:last-child, b:last-child").text().ifEmpty { span.text() }
                    text.contains("season") -> season = span.select("span:last-child, b:last-child").text().ifEmpty { span.text() }
                    text.contains("skor") || text.contains("score") -> score = span.select("span:last-child, b:last-child").text().ifEmpty { span.text() }
                }
            }

            val genres = doc.select(".genrez a, .mgen a, .infox .genrez a").map { it.text() }

            val episodes = mutableListOf<Episode>()
            doc.select(".listeps ul li, .lstepsiode ul li, .eplister ul li, .elist ul li").forEach { element ->
                try {
                    val epTitleEl = element.select(".lchx a, .epl-title, .epl-name").firstOrNull()
                        ?: element.select("a").firstOrNull()
                    val epTitle = epTitleEl?.text().orEmpty().trim()
                    val epUrl = element.select("a").attr("href")
                    val epNum = element.select(".eps a, .epl-num, .epnum").text()
                    val epDate = element.select(".date, .epl-date, .newnime").text()

                    if (epTitle.isNotEmpty() && epUrl.isNotEmpty()) {
                        episodes.add(
                            Episode(
                                title = epTitle,
                                url = epUrl,
                                episodeNumber = epNum,
                                uploadDate = epDate
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val anime = Anime(
                title = title,
                url = url,
                imageUrl = imageUrl,
                episode = episodes.firstOrNull()?.episodeNumber ?: "",
                type = type,
                status = status,
                score = score,
                studio = studio,
                season = season,
                synopsis = synopsis,
                totalEpisodes = totalEp,
                genres = genres
            )

            AnimeDetail(anime = anime, episodes = episodes.reversed())
        } catch (e: Exception) {
            e.printStackTrace()
            AnimeDetail(anime = Anime(title = "Error", synopsis = e.message ?: "Unknown error"))
        }
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val servers = mutableListOf<VideoServer>()

            doc.select("#server .east_player_option").forEach { element ->
                try {
                    val style = element.attr("style")
                    val dataPost = element.attr("data-post")
                    val dataNume = element.attr("data-nume")
                    val dataType = element.attr("data-type").ifEmpty { "schtml" }

                    if (style.contains("pointer-events: none") || (dataType == "schtml" && (dataPost.isEmpty() || dataNume.isEmpty()))) {
                        Log.d("Scraper", "Skipping disabled/unavailable server: ${element.select("span").text().trim()}")
                        return@forEach
                    }

                    val name = element.select("span").text().trim()
                        .ifEmpty { element.attr("data-type") }
                        .ifEmpty { element.select("a").text() }
                    if (name.isEmpty()) return@forEach

                    servers.add(VideoServer(
                        name = name,
                        dataPost = dataPost,
                        dataNume = dataNume,
                        dataType = dataType
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (servers.isEmpty()) {
                doc.select("iframe[src]").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.isNotEmpty()) {
                        val fullUrl = normalizeUrl(src, episodeUrl)
                        servers.add(VideoServer(name = "Server ${servers.size + 1}", url = fullUrl))
                    }
                }
            }

            servers
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"
            var embedUrl = server.url
            Log.d("Scraper", "Resolving server: ${server.name}, dataPost=${server.dataPost}, dataNume=${server.dataNume}, url=$embedUrl")

            if (embedUrl.isNotEmpty() && isDirectVideoUrl(embedUrl)) {
                Log.d("Scraper", "Server URL is already a direct video: $embedUrl")
                return@withContext embedUrl
            }

            if (server.dataPost.isNotEmpty() && server.dataNume.isNotEmpty() && embedUrl.isEmpty()) {
                val body = FormBody.Builder()
                    .add("action", "player_ajax")
                    .add("post", server.dataPost)
                    .add("nume", server.dataNume)
                    .add("type", server.dataType)
                    .build()

                val responseHtml = postAjax(ajaxUrl, body, referer = episodeUrl)
                Log.d("Scraper", "AJAX response (${responseHtml.length} chars): ${responseHtml.take(500)}")

                if (isCloudflareChallenge(responseHtml)) {
                    Log.w("Scraper", "Cloudflare challenge detected on AJAX for server: ${server.name}")
                    return@withContext ""
                }

                val directVideo = extractVideoUrlFromHtml(responseHtml, ajaxUrl)
                if (directVideo.isNotEmpty()) {
                    Log.d("Scraper", "Found direct video in AJAX response: $directVideo")
                    return@withContext directVideo
                }

                val responseDoc = Jsoup.parse(responseHtml)

                responseDoc.select("source[src]").firstOrNull()?.attr("src")?.let { src ->
                    if (src.isNotEmpty()) {
                        Log.d("Scraper", "Found source tag in AJAX: $src")
                        return@withContext normalizeUrl(src, episodeUrl)
                    }
                }

                val iframeSrc = responseDoc.select("iframe").first()?.attr("src") ?: ""
                Log.d("Scraper", "AJAX iframe src: $iframeSrc")
                if (iframeSrc.isNotEmpty()) {
                    embedUrl = normalizeUrl(iframeSrc, episodeUrl)

                    if (isDirectVideoUrl(embedUrl)) {
                        Log.d("Scraper", "Iframe src is a direct video, returning for ExoPlayer: $embedUrl")
                        return@withContext embedUrl
                    }

                    if (embedUrl.contains("blogger.com") || embedUrl.contains("bp.blogspot.com")) {
                        Log.d("Scraper", "Blogger embed detected, returning URL for WebView XHR extraction: $embedUrl")
                        return@withContext embedUrl
                    }

                    if (embedUrl.contains("wibuu.info")) {
                        val innerUrl = extractQueryParam(embedUrl, "url")
                        if (innerUrl.isNotEmpty()) {
                            Log.d("Scraper", "wibuu.info wrapper detected, extracted inner URL: $innerUrl")
                            embedUrl = innerUrl
                            if (innerUrl.contains("blogspot.com") || innerUrl.contains("blogger.com")) {
                                Log.d("Scraper", "Inner URL is blogspot, returning for WebView: $innerUrl")
                                return@withContext innerUrl
                            }
                        }
                    }
                }

                val scriptSrc = responseDoc.select("script[src]").firstOrNull()?.attr("src") ?: ""
                if (scriptSrc.isNotEmpty() && embedUrl.isEmpty()) {
                    Log.d("Scraper", "Script-based embed detected: $scriptSrc")
                    if (scriptSrc.contains("file.fm")) {
                        Log.d("Scraper", "file.fm player detected, returning embed URL for WebView resolution")
                        return@withContext scriptSrc
                    }
                }
            }

            if (embedUrl.isEmpty()) {
                Log.d("Scraper", "No embed URL found for server: ${server.name}")
                return@withContext ""
            }

            Log.d("Scraper", "Fetching embed page: $embedUrl")
            val html = fetchHtml(embedUrl)
            Log.d("Scraper", "Embed page (${html.length} chars): ${html.take(500)}")

            if (isCloudflareChallenge(html)) {
                Log.w("Scraper", "Cloudflare challenge on embed page: $embedUrl")
                return@withContext ""
            }

            if (embedUrl.contains("filedon.co")) {
                val filedonDirect = extractFiledonDirectUrl(html)
                if (filedonDirect.isNotEmpty()) {
                    Log.d("Scraper", "Found filedon direct URL: ${filedonDirect.take(160)}")
                    return@withContext filedonDirect
                }
            }

            val videoUrl = extractVideoUrlFromHtml(html, embedUrl)
            if (videoUrl.isNotEmpty()) {
                Log.d("Scraper", "Found video in embed page: $videoUrl")
                return@withContext videoUrl
            }

            val doc = Jsoup.parse(html)
            doc.select("iframe[src]").firstOrNull()?.attr("src")?.let { nestedSrc ->
                if (nestedSrc.isNotEmpty()) {
                    val nestedUrl = normalizeUrl(nestedSrc, embedUrl)
                    Log.d("Scraper", "Following nested iframe: $nestedUrl")
                    val nestedHtml = fetchHtml(nestedUrl)
                    if (isCloudflareChallenge(nestedHtml)) {
                        Log.w("Scraper", "Cloudflare challenge on nested iframe: $nestedUrl")
                    } else {
                        val nestedVideo = extractVideoUrlFromHtml(nestedHtml, nestedUrl)
                        if (nestedVideo.isNotEmpty()) {
                            Log.d("Scraper", "Found video in nested iframe: $nestedVideo")
                            return@withContext nestedVideo
                        }
                    }
                }
            }

            Log.d("Scraper", "Failed to find video URL for server: ${server.name}, returning embed URL for WebView: $embedUrl")
            if (embedUrl.isNotEmpty() && (embedUrl.contains("embed") || embedUrl.contains("iframe") || embedUrl.contains("wibufile") || embedUrl.contains("filedon") || embedUrl.contains("mega.nz"))) {
                embedUrl
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("Scraper", "Error resolving server: ${server.name}", e)
            e.printStackTrace()
            ""
        }
    }

    private fun isDirectVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mpd") ||
            lower.contains(".mkv") || lower.contains(".webm") || lower.contains(".m4v")
    }

    private fun extractFiledonDirectUrl(html: String): String {
        return try {
            val appDiv = Jsoup.parse(html).select("div#app[data-page]").first() ?: return ""
            val dataPage = appDiv.attr("data-page")
            val root = org.json.JSONObject(dataPage)
            val props = root.optJSONObject("props") ?: return ""
            val media = props.optJSONObject("media")
            val hlsUrl = media?.optString("hls_url", "") ?: ""
            if (hlsUrl.isNotEmpty()) return hlsUrl
            props.optString("url", "")
        } catch (e: Exception) {
            Log.w("Scraper", "Failed to parse filedon data-page: ${e.message}")
            ""
        }
    }

    private fun extractVideoFromEmbed(embedUrl: String): String {
        try {
            val html = fetchHtml(embedUrl)
            return extractVideoUrlFromHtml(html, embedUrl)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun isCloudflareChallenge(html: String): Boolean {
        return html.contains("challenge-platform") || html.contains("Enable JavaScript and cookies")
            || html.contains("cf-challenge") || html.contains("Checking your browser")
            || html.contains("Just a moment")
    }

    private fun unpackPackedJs(html: String): String {
        val pattern = Regex(
            """eval\(function\(p,a,c,k,e,d\)\s*\{.*?\}\(\s*'((?:[^'\\]|\\.)*)',(\d+),(\d+),\s*'((?:[^'\\]|\\.)*)'(?:\.split\('[^']*'\))?,\d+,\{\}\s*\)\s*\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val m = pattern.find(html) ?: return html
        return try {
            val payload = m.groupValues[1]
            val a = m.groupValues[2].toInt()
            val c = m.groupValues[3].toInt()
            val words = m.groupValues[4].split('|')

            val dictionary = HashMap<String, String>()
            for (i in c - 1 downTo 0) {
                val word = if (i < words.size) words[i] else ""
                if (word.isNotEmpty()) {
                    dictionary[packerToken(i, a)] = word
                }
            }

            var result = payload
            for ((key, value) in dictionary) {
                result = result.replace(Regex("""\b${Regex.escape(key)}\b"""), value)
            }
            result
        } catch (_: Exception) {
            html
        }
    }

    private fun packerToken(index: Int, base: Int): String {
        return if (index < base) packerChar(index)
        else packerToken(index / base, base) + packerChar(index % base)
    }

    private fun packerChar(c: Int): String {
        return if (c > 35) (c + 29).toChar().toString()
        else "0123456789abcdefghijklmnopqrstuvwxyz"[c].toString()
    }

    private fun extractVideoUrlFromHtml(html: String, embedUrl: String): String {
        val unpacked = unpackPackedJs(html)
        val searchHtml = unpacked + "\n" + html
        val doc = Jsoup.parse(searchHtml)

        doc.select("video source, video").firstOrNull()?.attr("src")?.let { src ->
            if (src.isNotEmpty() && (src.contains(".mp4") || src.contains(".m3u8") || src.contains(".mpd"))) {
                return normalizeUrl(src, embedUrl)
            }
        }

        doc.select("video").firstOrNull()?.attr("src")?.let { src ->
            if (src.isNotEmpty() && (src.contains(".mp4") || src.contains(".m3u8") || src.contains(".mpd"))) {
                return normalizeUrl(src, embedUrl)
            }
        }

        val patterns = listOf(
            Regex("""["']video_url["']\s*:\s*["'](https?://[^"']+)["']"""),
            Regex("""["']file["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""["']src["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""["']source["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""["']url["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""sources\s*:\s*\[\s*\{[^}]*src\s*:\s*["']([^"']+)["']"""),
            Regex("""file\s*[=:]\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""(?:https?:)?//[^\s'"<>]+\.(?:mp4|m3u8|mpd)(?:\?[^\s'"<>]*)?"""),
            Regex("""["'](?:videoUrl|mp4Url|m3u8Url|stream)["']\s*:\s*["'](https?://[^"']+)["']"""),
            Regex("""src\s*[=:]\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""["'](https?://[^"']*googleusercontent\.com[^"']*\.(?:mp4|m3u8)[^"']*)["']"""),
            Regex("""["'](https?://[^"']*bp\.blogspot\.com[^"']*\.(?:mp4|m3u8)[^"']*)["']"""),
            Regex("""["'](https?://[^"']*blogspot[^"']*\.mp4[^"']*)["']"""),
            Regex("""["'](https?://[^"']*googlevideo[^"']*)["']"""),
            Regex("""["'](https?://[^"']*wibufile[^"']*\.(?:mp4|m3u8)[^"']*)["']"""),
            Regex("""["'](https?://[^"']*vipstream[^"']*\.(?:mp4|m3u8)[^"']*)["']"""),
            Regex("""(?:file|source|src|video)\s*[=:]\s*["']([^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']""")
        )

        for (pattern in patterns) {
            val match = pattern.find(searchHtml)
            if (match != null) {
                val url = match.groupValues.getOrElse(1) { match.value }
                if (url.isNotEmpty() && url.startsWith("http")) {
                    return url
                } else if (url.isNotEmpty()) {
                    return normalizeUrl(url, embedUrl)
                }
            }
        }

        doc.select("iframe[src]").firstOrNull()?.attr("src")?.let { nestedSrc ->
            if (nestedSrc.isNotEmpty() && (nestedSrc.startsWith("http://") || nestedSrc.startsWith("https://") || nestedSrc.startsWith("//"))) {
                val nestedUrl = normalizeUrl(nestedSrc, embedUrl)
                return extractVideoFromEmbed(nestedUrl)
            }
        }

        return ""
    }

    private fun normalizeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                try {
                    val base = java.net.URL(baseUrl)
                    "${base.protocol}://${base.host}$url"
                } catch (_: Exception) {
                    url
                }
            }
            else -> url
        }
    }

    private fun extractQueryParam(url: String, param: String): String {
        return try {
            val uri = java.net.URI(url)
            val query = uri.query ?: return ""
            query.split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it[0] == param }
                ?.getOrNull(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractBloggerVideo(bloggerUrl: String): String {
        try {
            val html = fetchHtml(bloggerUrl)
            Log.d("Scraper", "Blogger page (${html.length} chars): ${html.take(300)}")

            // Extract video.g?token= URL from iframes or script content
            val tokenPatterns = listOf(
                Regex("""["']?(https?://[^"'\s]*blogger\.com/video\.g\?token=[^"'\s&]+)[&"'\s]"""),
                Regex("""["'](https?://[^"']*blogger\.com/video\.g\?token=[^"']+)"""),
                Regex("""src=(https?://[^"'\s]*blogger\.com/video\.g\?token=[^"'\s>]+)""")
            )
            for (pattern in tokenPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val videoGUrl = match.groupValues[1]
                    Log.d("Scraper", "Found video.g URL: $videoGUrl")
                    val videoFromBatch = fetchBloggerVideoG(videoGUrl)
                    if (videoFromBatch.isNotEmpty()) return videoFromBatch
                }
            }

            // Search in iframe tags
            val doc = Jsoup.parse(html)
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.contains("blogger.com/video.g") || src.contains("blogger.com/video-embed") || src.contains("blogger.com/video-play")) {
                    Log.d("Scraper", "Found blogger iframe: $src")
                    val fullUrl = if (src.startsWith("http")) src else "https:$src"
                    val videoFromIframe = fetchBloggerVideoG(fullUrl)
                    if (videoFromIframe.isNotEmpty()) return videoFromIframe
                }
            }

            doc.select("iframe[data-src]").forEach { iframe ->
                val src = iframe.attr("data-src")
                if (src.contains("blogger.com/video.g") || src.contains("blogger.com/video-embed")) {
                    Log.d("Scraper", "Found blogger data-src iframe: $src")
                    val fullUrl = if (src.startsWith("http")) src else "https:$src"
                    val videoFromIframe = fetchBloggerVideoG(fullUrl)
                    if (videoFromIframe.isNotEmpty()) return videoFromIframe
                }
            }

            // Search for token in JS
            val videoGJsPattern = Regex("""video\.g\?token=([A-Za-z0-9_-]+)""")
            val jsTokenMatch = videoGJsPattern.find(html)
            if (jsTokenMatch != null) {
                val token = jsTokenMatch.groupValues[1]
                val videoGUrl = "https://www.blogger.com/video.g?token=$token"
                Log.d("Scraper", "Found video.g token in JS: $token")
                val videoFromVideoG = fetchBloggerVideoG(videoGUrl)
                if (videoFromVideoG.isNotEmpty()) return videoFromVideoG
            }

            val gvMatch = Regex("""https?://[^\s"'<>]*googlevideo\.com[^\s"'<>]*""").find(html)
            if (gvMatch != null) return gvMatch.value

            val bpMatch = Regex("""https?://[^\s"'<>]*bp\.blogspot\.com[^\s"'<>]*""").find(html)
            if (bpMatch != null) return bpMatch.value

            Log.d("Scraper", "No Blogger video URL found")
        } catch (e: Exception) {
            Log.e("Scraper", "Blogger extraction failed: ${e.message}")
        }
        return ""
    }

    suspend fun resolveBloggerVideoG(videoGUrl: String): String = withContext(Dispatchers.IO) {
        fetchBloggerVideoG(videoGUrl)
    }

    private fun fetchBloggerVideoG(videoGUrl: String): String {
        try {
            Log.d("Scraper", "=== Blogger batchexecute extraction ===")
            Log.d("Scraper", "Fetching video.g page: $videoGUrl")

            val html = fetchHtml(videoGUrl)
            Log.d("Scraper", "video.g HTML: ${html.length} chars")

            val token = Regex("""token=([A-Za-z0-9_-]+)""").find(videoGUrl)
                ?.groupValues?.get(1)
            val sid = Regex(""""FdrFJe"\s*:\s*"([^"]+)"""").find(html)
                ?.groupValues?.get(1)
            val bh = Regex(""""cfb2h"\s*:\s*"([^"]+)"""").find(html)
                ?.groupValues?.get(1)
            val at = Regex(""""SNlM0e"\s*:\s*"([^"]+)"""").find(html)
                ?.groupValues?.get(1) ?: ""

            Log.d("Scraper", "token=${token?.take(30)}... sid=${sid?.take(30)}... bh=$bh at=${at.take(30)}")

            if (token == null || sid == null || bh == null) {
                Log.e("Scraper", "Failed to extract token/sid/bh from video.g page")
                Log.d("Scraper", "HTML preview: ${html.take(500)}")
                return ""
            }

            val innerJsonArr = org.json.JSONArray().apply {
                put(token)
                put("")
                put(0)
            }
            val innerStr = innerJsonArr.toString()
            Log.d("Scraper", "innerJson: $innerStr")

            val rpcArr = org.json.JSONArray().apply {
                put("WcwnYd")
                put(innerStr)
                put(org.json.JSONObject.NULL)
                put("generic")
            }
            val fReqArr = org.json.JSONArray().apply {
                put(org.json.JSONArray().apply { put(rpcArr) })
            }
            val fReq = fReqArr.toString()

            val batchUrl = "https://www.blogger.com/_/BloggerVideoPlayerUi/data/batchexecute?" +
                "rpcids=WcwnYd&source-path=%2Fvideo.g" +
                "&f.sid=${java.net.URLEncoder.encode(sid, "UTF-8")}" +
                "&bl=${java.net.URLEncoder.encode(bh, "UTF-8")}" +
                "&hl=en-US&_reqid=100001&rt=c"

            Log.d("Scraper", "batchexecute URL: $batchUrl")
            Log.d("Scraper", "f.req: $fReq")

            val formBody = FormBody.Builder()
                .add("f.req", fReq)
            if (at.isNotEmpty()) {
                formBody.add("at", at)
            }

            val request = Request.Builder()
                .url(batchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("X-Same-Domain", "1")
                .addHeader("Origin", "https://www.blogger.com")
                .addHeader("Referer", videoGUrl)
                .addHeader("Accept", "*/*")
                .post(formBody.build())
                .build()

            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string() ?: "" }

            Log.d("Scraper", "batchexecute response: ${body.length} chars")
            Log.d("Scraper", "batchexecute response preview: ${body.take(1000)}")

            if (body.isEmpty()) {
                Log.e("Scraper", "Empty batchexecute response")
                return ""
            }

            val videoUrl = parseBatchexecuteResponse(body)
            if (videoUrl.isNotEmpty()) {
                Log.d("Scraper", "Found video URL from batchexecute: ${videoUrl.take(200)}")
                return videoUrl
            }

            val gvMatch = Regex("""https?://[^"'\s<>]+?\.googlevideo\.com/[^"'\s<>]+""").find(body)
            if (gvMatch != null) {
                Log.d("Scraper", "Fallback googlevideo URL: ${gvMatch.value.take(200)}")
                return gvMatch.value
            }

            Log.e("Scraper", "No video URL found in batchexecute response")
            Log.d("Scraper", "Full response: $body")

        } catch (e: Exception) {
            Log.e("Scraper", "fetchBloggerVideoG failed: ${e.message}", e)
        }
        return ""
    }

    private fun parseBatchexecuteResponse(body: String): String {
        var bestUrl = ""
        var bestQuality = 0

        for (line in body.split("\n")) {
            if (!line.contains("wrb.fr")) continue
            try {
                val outer = org.json.JSONArray(line)
                for (i in 0 until outer.length()) {
                    val entry = outer.optJSONArray(i) ?: continue
                    if (entry.length() < 3) continue
                    if (entry.optString(0) != "wrb.fr" || entry.optString(1) != "WcwnYd") continue

                    val innerStr = entry.optString(2, "")
                    if (innerStr.isEmpty()) continue

                    val inner = try { org.json.JSONArray(innerStr) } catch (e: Exception) { continue }
                    val streamsUrl = findStreamsInArray(inner)
                    if (streamsUrl != null) {
                        return streamsUrl
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return bestUrl
    }

    private fun findStreamsInArray(data: org.json.JSONArray): String? {
        for (i in 0 until data.length()) {
            val elem = data.optJSONArray(i) ?: continue
            if (elem.length() > 0 && elem.optJSONArray(0) != null) {
                val streams = elem
                for (j in 0 until streams.length()) {
                    val streamArr = streams.optJSONArray(j) ?: continue
                    if (streamArr.length() < 1) continue
                    val url = streamArr.optString(0, "")
                    if (url.contains("googlevideo.com") && (url.contains("mime=video%2Fmp4") || url.contains("mime=video/mp4"))) {
                        if (url.contains("itag=22")) {
                            Log.d("Scraper", "Found 720p stream (itag=22)")
                            return url
                        }
                    }
                }
                for (j in 0 until streams.length()) {
                    val streamArr = streams.optJSONArray(j) ?: continue
                    if (streamArr.length() < 1) continue
                    val url = streamArr.optString(0, "")
                    if (url.contains("googlevideo.com")) {
                        Log.d("Scraper", "Found stream URL (fallback)")
                        return url
                    }
                }
            }
        }
        return null
    }

    suspend fun getSchedule(): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/jadwal-rilis/")
            val animeList = mutableListOf<Anime>()

            doc.select(".widget.schedule .widget-body .timeline .item, .jadwal-item, .schedule-item").forEach { element ->
                try {
                    val title = element.select(".info .name, a").text()
                    val url = element.select("a").attr("href")
                    val episode = element.select(".info .episode, .eps").text()

                    if (title.isNotEmpty()) {
                        animeList.add(
                            Anime(
                                title = title,
                                url = url,
                                episode = episode
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            animeList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val prevEl = doc.select(".naveps .nvs a, a[rel='prev'], a.prev").firstOrNull()
            val nextEl = doc.select(".naveps .nvs.rght a, a[rel='next'], a.next").firstOrNull()
            val prevUrl = cleanNavUrl(prevEl?.attr("href") ?: "")
            val nextUrl = cleanNavUrl(nextEl?.attr("href") ?: "")
            val prevTitle = prevEl?.text().orEmpty().ifEmpty { deriveEpisodeTitle(prevUrl) }
            val nextTitle = nextEl?.text().orEmpty().ifEmpty { deriveEpisodeTitle(nextUrl) }

            EpisodeNavigation(
                prevEpisodeUrl = if (prevUrl.isNotEmpty()) normalizeUrl(prevUrl, baseUrl) else "",
                prevEpisodeTitle = prevTitle,
                nextEpisodeUrl = if (nextUrl.isNotEmpty()) normalizeUrl(nextUrl, baseUrl) else "",
                nextEpisodeTitle = nextTitle
            )
        } catch (e: Exception) {
            e.printStackTrace()
            EpisodeNavigation()
        }
    }

    private fun cleanNavUrl(url: String): String {
        if (url.isEmpty() || url == "#" || url.startsWith("javascript:")) return ""
        return url
    }

    private fun deriveEpisodeTitle(url: String): String {
        if (url.isEmpty()) return ""
        val slug = url.trimEnd('/').substringAfterLast('/')
        val match = Regex("""[-_]episode[-_](\d+)""", RegexOption.IGNORE_CASE).find(slug)
        return if (match != null) "Episode ${match.groupValues[1]}" else slug
    }
}
