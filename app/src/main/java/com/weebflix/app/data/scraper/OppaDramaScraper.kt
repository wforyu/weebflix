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

class OppaDramaScraper : AnimeProvider {

    override val id: String = ProviderFactory.OPPADRAMA_ID
    override val name: String = "OppaDrama"
    override val defaultBaseUrl: String = "http://45.11.57.192"

    override var baseUrl: String
        get() = ProviderConfig.getBaseUrl(id)
        set(value) {
            ProviderConfig.setBaseUrl(id, value)
        }

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }
                override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .build()
    }

    private fun ensureCookieSession() {
        try {
            val request = Request.Builder()
                .url("$baseUrl/?verify_human=1")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private fun fetchDocument(url: String): Document {
        ensureCookieInitialized()
        val finalUrl = ensureVerifyHuman(url)
        val request = Request.Builder()
            .url(finalUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .addHeader("Referer", baseUrl)
            .build()

        val response = client.newCall(request).execute()
        return response.use {
            val html = it.body?.string() ?: throw Exception("Empty response")
            Jsoup.parse(html)
        }
    }

    @Volatile
    private var cookieInitialized = false

    private fun ensureCookieInitialized() {
        if (!cookieInitialized) {
            synchronized(this) {
                if (!cookieInitialized) {
                    ensureCookieSession()
                    cookieInitialized = true
                }
            }
        }
    }

    private fun ensureVerifyHuman(url: String): String {
        return if (url.contains("verify_human")) url
        else if (url.contains("?")) "$url&verify_human=1"
        else "$url?verify_human=1"
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "http:$url"
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

    private fun parseCardList(doc: Document): List<Anime> {
        val items = mutableListOf<Anime>()
        doc.select("article.bs .bsx").forEach { bsx ->
            try {
                val link = bsx.select("a[href]").firstOrNull() ?: return@forEach
                val href = link.attr("href")
                if (href.isEmpty()) return@forEach

                val title = link.select("h2[itemprop='headline']").text()
                    .ifEmpty { link.select(".tt h2").text() }
                    .ifEmpty { link.attr("title") }

                val img = link.select(".limit img.ts-post-image").firstOrNull()
                val imageUrl = img?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""

                val typeEl = link.select(".limit .typez").text()
                val epText = link.select(".limit .epx").text()
                val statusText = link.select(".limit .status").text()
                val subText = link.select(".limit .sb").text()
                val timeText = link.select(".timeago").text()

                if (title.isNotEmpty()) {
                    items.add(Anime(
                        title = title,
                        url = normalizeUrl(href),
                        imageUrl = imageUrl,
                        episode = epText,
                        type = typeEl,
                        status = statusText.ifEmpty { epText },
                        score = timeText,
                        studio = subText
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return items
    }

    data class HomeContent(
        val latestEpisodes: List<Anime>,
        val featured: List<Anime>
    )

    suspend fun getHomeContent(): HomeContent = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(baseUrl)
            val episodes = parseCardList(doc)

            val featured = episodes.take(10).map { anime ->
                anime.copy(type = anime.type.ifEmpty { "Drama" })
            }.ifEmpty {
                episodes.shuffled().take(10)
            }

            HomeContent(latestEpisodes = episodes, featured = featured)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error fetching home content", e)
            HomeContent(emptyList(), emptyList())
        }
    }

    suspend fun getDramaKorea(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?country%5B%5D=south-korea&status=&type=Drama&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getDramaKorea", e)
            emptyList()
        }
    }

    suspend fun getDramaChina(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?country%5B%5D=china&type=Drama&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getDramaChina", e)
            emptyList()
        }
    }

    suspend fun getDramaJepang(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?country%5B%5D=japan&type=Drama&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getDramaJepang", e)
            emptyList()
        }
    }

    suspend fun getFilmKorea(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?country%5B%5D=south-korea&status=&type=Movie&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getFilmKorea", e)
            emptyList()
        }
    }

    suspend fun getFilmChina(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?country%5B%5D=china&type=Movie&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getFilmChina", e)
            emptyList()
        }
    }

    suspend fun getFilmJepang(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?country%5B%5D=japan&type=Movie&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getFilmJepang", e)
            emptyList()
        }
    }

    suspend fun getNetflix(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/network/netflix/page/$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getNetflix", e)
            emptyList()
        }
    }

    override suspend fun getLatestEpisodes(page: Int): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?status=&type=&order=update&page=$page"
            val doc = fetchDocument(url)
            val items = parseCardList(doc)
            items.filter { anime ->
                val t = anime.type.lowercase()
                val title = anime.title.lowercase()
                t.contains("drama") || t.contains("movie") || t.contains("film") ||
                    title.contains("netflix") || t.contains("series") || t.isEmpty()
            }.map { anime ->
                Episode(
                    title = anime.title,
                    url = anime.url,
                    imageUrl = anime.imageUrl,
                    episodeNumber = anime.episode,
                    uploadDate = anime.score
                )
            }
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getLatestEpisodes", e)
            emptyList()
        }
    }

    override suspend fun getOngoingAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?status=Ongoing&type=&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getOngoingAnime", e)
            emptyList()
        }
    }

    override suspend fun getPopularAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/series/?status=&type=&order=update&page=$page"
            val doc = fetchDocument(url)
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getPopularAnime", e)
            emptyList()
        }
    }

    override suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val doc = fetchDocument("$baseUrl/?s=$encodedQuery")
            parseCardList(doc)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in searchAnime", e)
            emptyList()
        }
    }

    override suspend fun getAnimeDetail(url: String): AnimeDetail = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(url)

            val title = doc.select("h1.entry-title").text()
                .ifEmpty { doc.select(".infox h1").text() }

            val synopsis = doc.select(".desc, .mindesc").text()
                .ifEmpty { doc.select("meta[name=description]").attr("content") }

            val imageUrl = doc.select(".thumbook .thumb img").firstOrNull()?.let { img ->
                img.attr("src").ifEmpty { img.attr("data-src") }
            } ?: ""

            var status = ""
            var type = ""
            var totalEp = ""
            var network = ""
            var country = ""
            var duration = ""
            var releaseDate = ""
            val genres = mutableListOf<String>()

            doc.select(".info-content .spe span").forEach { span ->
                val text = span.text().lowercase()
                when {
                    text.contains("status") -> status = span.select("b").text()
                    text.contains("tipe") || text.contains("type") -> type = span.select("b").text()
                    text.contains("episode") -> totalEp = span.select("b").text()
                    text.contains("network") -> network = span.select("a").text().ifEmpty { span.select("b").text() }
                    text.contains("negara") || text.contains("country") -> country = span.select("a").text().ifEmpty { span.select("b").text() }
                    text.contains("durasi") || text.contains("duration") -> duration = span.select("b").text()
                    text.contains("dirilis") || text.contains("rilis") -> releaseDate = span.select("time").text().ifEmpty { span.select("b").text() }
                }
            }

            doc.select(".genxed a").forEach { a ->
                val genreText = a.text().trim()
                if (genreText.isNotEmpty()) genres.add(genreText)
            }

            val episodes = mutableListOf<Episode>()

            doc.select(".eplister ul li").forEach { li ->
                try {
                    val a = li.select("a[href]").firstOrNull() ?: return@forEach
                    val epUrl = a.attr("href")
                    val epNum = li.select(".epl-num").text()
                    val epTitle = li.select(".epl-title").text()
                    val epDate = li.select(".epl-date").text()

                    if (epUrl.isNotEmpty()) {
                        episodes.add(Episode(
                            title = epTitle.ifEmpty { "Episode $epNum" },
                            url = normalizeUrl(epUrl),
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
                url = normalizeUrl(url),
                imageUrl = imageUrl,
                episode = episodes.firstOrNull()?.episodeNumber ?: "",
                type = type.ifEmpty { "Drama" },
                status = status,
                score = "",
                studio = network,
                season = releaseDate,
                synopsis = synopsis,
                totalEpisodes = totalEp.ifEmpty { episodes.size.toString() },
                genres = genres
            )

            AnimeDetail(anime = anime, episodes = episodes)
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getAnimeDetail: ${e.message}", e)
            AnimeDetail(anime = Anime(title = "Error", synopsis = e.message ?: "Unknown error"))
        }
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val servers = mutableListOf<VideoServer>()

            doc.select(".mirror select.mirror option, select.mirror option").forEach { option ->
                val value = option.attr("value").trim()
                val name = option.text().trim()

                if (value.isNotEmpty() && name.isNotEmpty() && name != "Pilih Server Video") {
                    try {
                        val decodedBytes = Base64.decode(value, Base64.DEFAULT)
                        val decodedHtml = String(decodedBytes, Charsets.UTF_8)
                        val srcRegex = Regex("""src=["']([^"']+)["']""")
                        val srcMatch = srcRegex.find(decodedHtml)
                        val iframeSrc = srcMatch?.groupValues?.get(1) ?: ""

                        servers.add(VideoServer(
                            name = name,
                            url = normalizeUrl(episodeUrl),
                            videoUrl = normalizeUrl(iframeSrc)
                        ))
                    } catch (e: Exception) {
                        servers.add(VideoServer(
                            name = name,
                            url = normalizeUrl(episodeUrl),
                            videoUrl = value
                        ))
                    }
                }
            }

            if (servers.isEmpty()) {
                doc.select("iframe[src]").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.isNotEmpty() && !src.contains("about:blank")) {
                        servers.add(VideoServer(
                            name = iframe.attr("title").ifEmpty { "Server 1" },
                            url = normalizeUrl(episodeUrl),
                            videoUrl = normalizeUrl(src)
                        ))
                    }
                }
            }

            if (servers.isEmpty()) {
                servers.add(VideoServer(
                    name = "OppaDrama",
                    url = normalizeUrl(episodeUrl)
                ))
            }

            servers
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getEpisodeServers", e)
            emptyList()
        }
    }

    override suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            if (server.videoUrl.isNotEmpty() && (server.videoUrl.contains(".mp4") || server.videoUrl.contains(".m3u8") || server.videoUrl.contains(".mpd"))) {
                return@withContext server.videoUrl
            }

            if (server.videoUrl.isNotEmpty() && server.videoUrl.contains("http")) {
                val embedHtml = try {
                    val embedReq = Request.Builder()
                        .url(server.videoUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Referer", "https://turbovidhls.com/")
                        .addHeader("Origin", "https://turbovidhls.com")
                        .build()
                    val embedResp = client.newCall(embedReq).execute()
                    embedResp.use { it.body?.string() ?: "" }
                } catch (e: Exception) {
                    Log.e("OppaDrama", "Failed to fetch embed page: ${e.message}")
                    ""
                }

                if (embedHtml.isNotEmpty()) {
                    val m3u8Patterns = listOf(
                        Regex("""var\s+urlPlay\s*=\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]"""),
                        Regex("""data-hash=["'](https?://[^'"]+\.m3u8[^'"]*)["']"""),
                        Regex("""["']file["']\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']"""),
                        Regex("""https?://[^\s"'<>]+turboviplay[^\s"'<>]+\.m3u8[^\s"'<>]*"""),
                        Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
                    )
                    for (pattern in m3u8Patterns) {
                        val match = pattern.find(embedHtml)
                        if (match != null) {
                            val url = match.groupValues.getOrElse(1) { match.value }.trim()
                            if (url.startsWith("http") && url.contains("m3u8")) {
                                Log.d("OppaDrama", "Found m3u8 URL in embed page: $url")
                                return@withContext url
                            }
                        }
                    }

                    val mp4Match = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").find(embedHtml)
                    if (mp4Match != null) {
                        val url = mp4Match.value.trim()
                        Log.d("OppaDrama", "Found mp4 URL in embed page: $url")
                        return@withContext url
                    }
                }

                val doc = Jsoup.parse(embedHtml.ifEmpty {
                    val fallbackReq = Request.Builder()
                        .url(server.videoUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                        .build()
                    client.newCall(fallbackReq).execute().use { it.body?.string() ?: "" }
                })
                doc.select("iframe[src]").firstOrNull()?.attr("src")?.let {
                    return@withContext normalizeUrl(it)
                }
                doc.select("video source[src]").firstOrNull()?.attr("src")?.let {
                    return@withContext normalizeUrl(it)
                }
            }

            server.videoUrl.ifEmpty { server.url }
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error resolving server: ${server.name}", e)
            server.videoUrl.ifEmpty { server.url }
        }
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val prevEl = doc.select(".naveps .nvs a, .naveps .nvsl a, a.prev, a[rel='prev']").firstOrNull()
            val nextEl = doc.select(".naveps .nvs a, .naveps .nvsr a, a.next, a[rel='next']").firstOrNull()
            val prevUrl = prevEl?.attr("href") ?: ""
            val prevTitle = prevEl?.text() ?: ""
            val nextUrl = nextEl?.attr("href") ?: ""
            val nextTitle = nextEl?.text() ?: ""

            EpisodeNavigation(
                prevEpisodeUrl = if (prevUrl.isNotEmpty()) normalizeUrl(prevUrl) else "",
                prevEpisodeTitle = prevTitle,
                nextEpisodeUrl = if (nextUrl.isNotEmpty()) normalizeUrl(nextUrl) else "",
                nextEpisodeTitle = nextTitle
            )
        } catch (e: Exception) {
            Log.e("OppaDrama", "Error in getEpisodeNavigation", e)
            EpisodeNavigation()
        }
    }
}
