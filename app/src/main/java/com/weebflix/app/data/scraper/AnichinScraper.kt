package com.weebflix.app.data.scraper

import android.util.Base64
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AnichinScraper : AnimeProvider {

    override val id: String = ProviderFactory.ANICHIN_ID
    override val name: String = "Anichin"
    override val defaultBaseUrl: String = "https://anichin.cafe"

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
            val url = if (page <= 1) baseUrl else "$baseUrl/page/$page/"
            val doc = fetchDocument(url)
            val episodes = mutableListOf<Episode>()

            val latestSection = doc.select("div.releases.latesthome").first()
            val articles = if (latestSection != null) {
                latestSection.parent()?.select("div.listupd article.bs") ?: doc.select("div.listupd article.bs")
            } else {
                doc.select("div.listupd article.bs")
            }

            articles.forEach { element ->
                try {
                    val a = element.select("div.bsx > a").first()
                    val title = a?.attr("title") ?: element.select(".tt").text()
                    val url = a?.attr("href") ?: ""
                    val imageUrl = element.select(".limit img.ts-post-image").attr("src")
                    val epNum = element.select(".limit .bt span.epx").text()

                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        episodes.add(Episode(
                            title = title,
                            url = url,
                            imageUrl = imageUrl,
                            episodeNumber = epNum,
                            uploadDate = ""
                        ))
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
            val url = if (page <= 1) "$baseUrl/ongoing/" else "$baseUrl/ongoing/page/$page/"
            val doc = fetchDocument(url)
            val animeList = mutableListOf<Anime>()

            doc.select("div.listupd.cp > article.bs, div.listupd article.bs").forEach { element ->
                try {
                    val a = element.select("div.bsx > a").first()
                    val title = a?.attr("title") ?: element.select(".tt").text()
                    val url = a?.attr("href") ?: ""
                    val imageUrl = element.select(".limit img.ts-post-image").attr("src")
                    val epNum = element.select(".limit .bt span.epx").text()
                    val type = element.select(".limit .typez").text()
                    val status = if (epNum.contains("Ongoing", ignoreCase = true) || epNum.contains("Completed", ignoreCase = true)) epNum else ""

                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        animeList.add(Anime(
                            title = title,
                            url = url,
                            imageUrl = imageUrl,
                            episode = if (status.isEmpty()) epNum else "",
                            type = type,
                            status = status
                        ))
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

    override suspend fun getPopularAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/completed/" else "$baseUrl/completed/page/$page/"
            val doc = fetchDocument(url)
            val animeList = mutableListOf<Anime>()

            doc.select("div.listupd article.bs").forEach { element ->
                try {
                    val a = element.select("div.bsx > a").first()
                    val title = a?.attr("title") ?: element.select(".tt").text()
                    val url = a?.attr("href") ?: ""
                    val imageUrl = element.select(".limit img.ts-post-image").attr("src")
                    val type = element.select(".limit .typez").text()

                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        animeList.add(Anime(
                            title = title,
                            url = url,
                            imageUrl = imageUrl,
                            type = type,
                            status = "Completed"
                        ))
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

    suspend fun getAllAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/seri/" else "$baseUrl/seri/?page=$page"
            val doc = fetchDocument(url)
            val animeList = mutableListOf<Anime>()

            doc.select("div.listupd article.bs").forEach { element ->
                try {
                    val a = element.select("div.bsx > a").first()
                    val title = a?.attr("title") ?: element.select(".tt").text()
                    val url = a?.attr("href") ?: ""
                    val imageUrl = element.select(".limit img.ts-post-image").attr("src")
                    val epNum = element.select(".limit .bt span.epx").text()
                    val type = element.select(".limit .typez").text()
                    val status = if (epNum.contains("Ongoing", ignoreCase = true) || epNum.contains("Completed", ignoreCase = true)) epNum else ""

                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        animeList.add(Anime(
                            title = title,
                            url = url,
                            imageUrl = imageUrl,
                            episode = if (status.isEmpty()) epNum else "",
                            type = type,
                            status = status
                        ))
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

            doc.select("div.listupd article.bs").forEach { element ->
                try {
                    val a = element.select("div.bsx > a").first()
                    val title = a?.attr("title") ?: element.select(".tt").text()
                    val url = a?.attr("href") ?: ""
                    val imageUrl = element.select(".limit img.ts-post-image").attr("src")
                    val epNum = element.select(".limit .bt span.epx").text()
                    val type = element.select(".limit .typez").text()
                    val status = if (epNum.contains("Ongoing", ignoreCase = true) || epNum.contains("Completed", ignoreCase = true)) epNum else ""

                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        animeList.add(Anime(
                            title = title,
                            url = url,
                            imageUrl = imageUrl,
                            episode = if (status.isEmpty()) epNum else "",
                            type = type,
                            status = status
                        ))
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
            var doc = fetchDocument(url)
            var detail = parseAnimeDetail(doc, url)

            if (detail.episodes.isEmpty()) {
                val seriesUrl = doc.select(".ts-breadcrumb ol li a[href*='/seri/'], .breadcrumb ol li a[href*='/seri/']").first()?.attr("href").orEmpty()
                if (seriesUrl.isNotEmpty() && seriesUrl != url) {
                    Log.d("AnichinDetail", "No episode list found (episode page?), resolving to series: $seriesUrl")
                    doc = fetchDocument(seriesUrl)
                    detail = parseAnimeDetail(doc, seriesUrl)
                }
            }

            detail
        } catch (e: Exception) {
            e.printStackTrace()
            AnimeDetail(anime = Anime(title = "Error", synopsis = e.message ?: "Unknown error"))
        }
    }

    private fun parseAnimeDetail(doc: org.jsoup.nodes.Document, url: String): AnimeDetail {
        val title = doc.select("h1.entry-title").text()
        val synopsis = doc.select("div.desc, div.entry-content p").text()
        val imageUrl = doc.select("div.thumb img, div.thumbook img").attr("src")

        var status = ""
        var type = ""
        var totalEp = ""
        var studio = ""
        var score = ""
        var duration = ""
        var released = ""

        doc.select(".info-content .spe span, .infox .spe span").forEach { span ->
            val text = span.text().lowercase()
            when {
                text.contains("status") -> {
                    val a = span.select("a").first()
                    status = a?.text() ?: span.text()
                }
                text.contains("tipe") || text.contains("type") -> {
                    val a = span.select("a").first()
                    type = a?.text() ?: span.text()
                }
                text.contains("episode") -> totalEp = span.text().replace("Episodes:", "").trim()
                text.contains("studio") -> {
                    val a = span.select("a").first()
                    studio = a?.text() ?: span.text()
                }
                text.contains("skor") || text.contains("score") -> score = span.text()
                text.contains("duration") -> duration = span.text().replace("Duration:", "").trim()
                text.contains("released") -> released = span.text().replace("Released:", "").trim()
            }
        }

        val genres = doc.select("div.genxed a").map { it.text() }

        val episodes = mutableListOf<Episode>()
        doc.select("div.eplister > ul > li").forEach { element ->
            try {
                val epUrl = element.select("a").attr("href")
                val epNum = element.select("div.epl-num").text()
                val epTitle = element.select("div.epl-title").text()
                val epDate = element.select("div.epl-date").text()

                if (epUrl.isNotEmpty()) {
                    val displayName = if (epTitle.isNotEmpty()) "Episode $epNum - $epTitle" else "Episode $epNum"
                    episodes.add(Episode(
                        title = displayName,
                        url = epUrl,
                        episodeNumber = epNum,
                        uploadDate = epDate
                    ))
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
            synopsis = synopsis,
            totalEpisodes = totalEp,
            genres = genres
        )

        return AnimeDetail(anime = anime, episodes = episodes)
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val servers = mutableListOf<VideoServer>()

            doc.select("select.mirror option").forEach { option ->
                try {
                    val name = option.text().trim()
                    val encodedValue = option.attr("value")
                    if (name.isEmpty() || encodedValue.isEmpty()) return@forEach

                    val decoded = try {
                        String(Base64.decode(encodedValue, Base64.DEFAULT), Charsets.UTF_8)
                    } catch (e: Exception) { null }

                    val iframeSrc = if (decoded != null) {
                        val iframeDoc = Jsoup.parse(decoded)
                        iframeDoc.select("iframe").first()?.attr("src") ?: ""
                    } else ""

                    val directUrl = extractDirectUrl(decoded ?: encodedValue)

                    servers.add(VideoServer(
                        name = name,
                        url = iframeSrc.ifEmpty { directUrl },
                        dataType = "mirror"
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (servers.isEmpty()) {
                doc.select("div.select_serv ul li a").forEach { a ->
                    val name = a.text().trim()
                    val href = a.attr("href")
                    if (name.isNotEmpty() && href.isNotEmpty()) {
                        servers.add(VideoServer(name = name, url = href))
                    }
                }
            }

            servers
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun isBrowserPlayableEmbed(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("dailymotion.com") || lower.contains("archive.org") ||
            lower.contains("mega.nz") || lower.contains("ok.ru") ||
            lower.contains("rumble.com") || lower.contains("anichin-player.web.id") ||
            lower.contains("rubyvidhub") || lower.contains("vk.com")
    }

    private fun extractDirectUrl(html: String): String {
        val patterns = listOf(
            Regex("""https?://[^\s"'>]+\.(?:mp4|m3u8|mpd)[^\s"'>]*"""),
            Regex("""src=["'](https?://[^"']+)["']""")
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val url = match.groupValues.getOrElse(1) { match.value }
                if (url.startsWith("http")) return url
            }
        }
        return ""
    }

    override suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val embedUrl = server.url
            if (embedUrl.isEmpty()) return@withContext ""

            Log.d("AnichinResolve", "Resolving: ${server.name}, url=$embedUrl")

            if (embedUrl.contains("anichin.stream")) {
                Log.d("AnichinResolve", "Anichin stream detected, trying to extract m3u8")
                val html = fetchHtml(embedUrl)
                val unpacked = unpackPackedJs(html)
                val searchHtml = unpacked + "\n" + html

                val hlsPatterns = listOf(
                    Regex("""["']file["']\s*:\s*["']([^"']+\.m3u8[^"']*)["']"""),
                    Regex("""file\s*[=:]\s*["']([^"']+\.m3u8[^"']*)["']"""),
                    Regex("""(?:https?:)?//[^\s'"<>]+\.m3u8[^\s'"<>]*"""),
                    Regex("""["']file["']\s*:\s*["']([^"']+)["']""")
                )

                for (pattern in hlsPatterns) {
                    val match = pattern.find(searchHtml)
                    if (match != null) {
                        var file = match.groupValues.getOrElse(1) { match.value }.trim()
                        if (file.startsWith("//")) file = "https:$file"
                        else if (file.startsWith("/")) file = "https://anichin.stream$file"
                        else if (!file.startsWith("http")) file = "https://anichin.stream/$file"
                        if (file.startsWith("http")) {
                            Log.d("AnichinResolve", "Found HLS URL: $file")
                            return@withContext file
                        }
                    }
                }

                val directUrl = extractDirectVideoUrlFromHtml(searchHtml, embedUrl)
                if (directUrl.isNotEmpty()) {
                    Log.d("AnichinResolve", "Found direct URL: $directUrl")
                    return@withContext directUrl
                }

                Log.d("AnichinResolve", "No m3u8 found, returning stream URL for WebView")
                return@withContext embedUrl
            }

            if (embedUrl.contains("abysscdn.com") || embedUrl.contains("abyssplayer")) {
                Log.d("AnichinResolve", "Abyss player: $embedUrl")
                return@withContext embedUrl
            }

            if (isBrowserPlayableEmbed(embedUrl)) {
                Log.d("AnichinResolve", "Browser-playable embed (plays in WebView): $embedUrl")
                return@withContext embedUrl
            }

            val directUrl = extractDirectVideoUrlFromHtml(fetchHtml(embedUrl), embedUrl)
            if (directUrl.isNotEmpty()) {
                Log.d("AnichinResolve", "Found direct video: $directUrl")
                return@withContext directUrl
            }

            embedUrl
        } catch (e: Exception) {
            Log.e("AnichinResolve", "Error: ${e.message}")
            e.printStackTrace()
            server.url
        }
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val prevUrl = doc.select("a[rel=prev]").attr("href")
            val prevTitle = doc.select("a[rel=prev]").text()
            val nextUrl = doc.select("a[rel=next]").attr("href")
            val nextTitle = doc.select("a[rel=next]").text()

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

    private fun extractDirectVideoUrlFromHtml(html: String, embedUrl: String): String {
        val unpacked = unpackPackedJs(html)
        val searchHtml = unpacked + "\n" + html

        val patterns = listOf(
            Regex("""["']video_url["']\s*:\s*["'](https?://[^"']+)["']"""),
            Regex("""["']file["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""["']src["']\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""file\s*[=:]\s*["'](https?://[^"']+\.(?:mp4|m3u8|mpd)[^"']*)["']"""),
            Regex("""(?:https?:)?//[^\s'"<>]+\.(?:mp4|m3u8|mpd)(?:\?[^\s'"<>]*)?"""),
            Regex("""sources\s*:\s*\[\s*\{[^}]*src\s*:\s*["']([^"']+)["']"""),
            Regex("""["'](https?://[^"']*googlevideo\.com[^"']*)["']"""),
            Regex("""["'](https?://[^"']*wibufile[^"']*\.(?:mp4|m3u8)[^"']*)["']""")
        )

        for (pattern in patterns) {
            val match = pattern.find(searchHtml)
            if (match != null) {
                var url = match.groupValues.getOrElse(1) { match.value }
                if (url.startsWith("//")) url = "https:$url"
                else if (url.startsWith("/")) {
                    try {
                        val base = java.net.URL(embedUrl)
                        url = "${base.protocol}://${base.host}$url"
                    } catch (_: Exception) {}
                }
                if (url.startsWith("http")) return url
            }
        }

        return ""
    }
}
