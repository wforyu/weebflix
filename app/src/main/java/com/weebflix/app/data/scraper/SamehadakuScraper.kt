package com.weebflix.app.data.scraper

import android.util.Log
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.AnimeDetail
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.EpisodeNavigation
import com.weebflix.app.data.model.VideoServer
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

class SamehadakuScraper {

    private val baseUrl: String
        get() = ProviderConfig.baseUrl

    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

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

    suspend fun getLatestEpisodes(): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(baseUrl)
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

    suspend fun getOngoingAnime(): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/daftar-anime-2")
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

    suspend fun getPopularAnime(): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/daftar-anime-2/?order=popular")
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

    suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
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

    suspend fun getAnimeDetail(url: String): AnimeDetail = withContext(Dispatchers.IO) {
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
                    val epTitle = element.select(".lchx a, .epl-title, .epl-name, a").text()
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

            AnimeDetail(anime = anime, episodes = episodes)
        } catch (e: Exception) {
            e.printStackTrace()
            AnimeDetail(anime = Anime(title = "Error", synopsis = e.message ?: "Unknown error"))
        }
    }

    suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val servers = mutableListOf<VideoServer>()

            doc.select("#server .east_player_option").forEach { element ->
                try {
                    val name = element.select("span").text().trim()
                        .ifEmpty { element.attr("data-type") }
                        .ifEmpty { element.select("a").text() }
                    if (name.isEmpty()) return@forEach

                    val dataPost = element.attr("data-post")
                    val dataNume = element.attr("data-nume")
                    val dataType = element.attr("data-type").ifEmpty { "schtml" }

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

    suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"
            var embedUrl = server.url
            Log.d("Scraper", "Resolving server: ${server.name}, dataPost=${server.dataPost}, dataNume=${server.dataNume}, url=$embedUrl")

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

            Log.d("Scraper", "Failed to find video URL for server: ${server.name}")
            ""
        } catch (e: Exception) {
            Log.e("Scraper", "Error resolving server: ${server.name}", e)
            e.printStackTrace()
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
        val pattern = Regex("""eval\(function\(p,a,c,k,e,d\).+?\}\)""", RegexOption.DOT_MATCHES_ALL)
        val packedMatch = pattern.find(html) ?: return html
        return try {
            val packed = packedMatch.value
            val base64Pattern = Regex("""}\'(.+)\',(\d+),(\d+),\'([^\']+)\'\.(split|slice)""")
            val m = base64Pattern.find(packed) ?: return html
            val payload = m.groupValues[1]
            val wordsBase = m.groupValues[4]

            val a = m.groupValues[2].toInt()
            val c = m.groupValues[3].toInt()
            val words = wordsBase.split('|')

            val dictionary = HashMap<String, String>()
            for (i in 0 until c) {
                val key = if (i < words.size) words[i] else ""
                val value = toString26(i, a)
                dictionary[value] = key
            }

            var result = payload
            for ((key, value) in dictionary) {
                result = result.replace(key, value)
            }
            result
        } catch (_: Exception) {
            html
        }
    }

    private fun toString26(num: Int, base: Int): String {
        val sb = StringBuilder()
        var n = num
        while (n > 0) {
            n--
            sb.append(('a' + n % base))
            n /= base
        }
        return sb.reverse().toString()
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
            Regex("""(?:https?:)?//[^"'\s<>]*bp\.blogspot\.com[^"'\s<>]*\.(?:mp4|m3u8)[^"'\s<>]*"""),
            Regex("""["'](https?://[^"']*blogspot[^"']*\.mp4[^"']*)["']"""),
            Regex("""["'](https?://[^"']*googlevideo[^"']*)["']"""),
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
            if (nestedSrc.isNotEmpty()) {
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

    suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val prevUrl = doc.select(".epnav .prev a, .episodelist .prev a, a.prev").attr("href")
            val prevTitle = doc.select(".epnav .prev a, .episodelist .prev a, a.prev").text()
            val nextUrl = doc.select(".epnav .next a, .episodelist .next a, a.next").attr("href")
            val nextTitle = doc.select(".epnav .next a, .episodelist .next a, a.next").text()

            EpisodeNavigation(
                prevEpisodeUrl = prevUrl,
                prevEpisodeTitle = prevTitle,
                nextEpisodeUrl = nextUrl,
                nextEpisodeTitle = nextTitle
            )
        } catch (e: Exception) {
            e.printStackTrace()
            EpisodeNavigation()
        }
    }
}
