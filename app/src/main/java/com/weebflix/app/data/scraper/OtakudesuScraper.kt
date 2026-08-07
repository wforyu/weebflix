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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OtakudesuScraper : AnimeProvider {

    override val id: String = ProviderFactory.OTAKUDESU_ID
    override val name: String = "Otakudesu"
    override val defaultBaseUrl: String = "https://otakudesu.blog"

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

    // Cards on home/ongoing/complete: div.venz > ul > li > div.detpost
    // .epz = "Episode 5" (ongoing) or "12 Episode" (complete), .epztipe = hari/rating,
    // .newnime = tanggal, .thumb > a[href], .thumbz img[src], h2.jdlflm = judul.
    private fun parseDetpostCards(doc: Document, onlyEpisodeCards: Boolean = false): List<Pair<Anime, String>> {
        val result = mutableListOf<Pair<Anime, String>>()
        doc.select("div.venz > ul > li > div.detpost").forEach { element ->
            try {
                val a = element.select("div.thumb > a").first()
                val url = a?.attr("href").orEmpty()
                val title = element.select("h2.jdlflm").text()
                if (title.isEmpty() || url.isEmpty()) return@forEach

                val epz = element.select("div.epz").text().replace("Episode", "").trim()
                val epztipe = element.select("div.epztipe").text().replace("fa-star", "").trim()
                val date = element.select("div.newnime").text()
                val imageUrl = element.select("div.thumbz img").attr("src")

                val isEpisodeCard = Regex("^Episode\\s+\\d+", RegexOption.IGNORE_CASE).matches(element.select("div.epz").text())
                if (onlyEpisodeCards && !isEpisodeCard) return@forEach

                result.add(
                    Anime(
                        title = title,
                        url = url,
                        imageUrl = imageUrl,
                        episode = epz,
                        status = if (isEpisodeCard) "Ongoing" else "Completed",
                        score = epztipe,
                        latestUpdate = date
                    ) to date
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    override suspend fun getLatestEpisodes(page: Int): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) baseUrl else "$baseUrl/page/$page/"
            val doc = fetchDocument(url)
            val episodes = mutableListOf<Episode>()

            parseDetpostCards(doc, onlyEpisodeCards = true).forEach { (anime, date) ->
                episodes.add(Episode(
                    title = anime.title,
                    url = anime.url,
                    imageUrl = anime.imageUrl,
                    episodeNumber = anime.episode,
                    uploadDate = date
                ))
            }

            episodes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getOngoingAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            // /ongoing-anime/ is a single long page (no pagination)
            if (page > 1) return@withContext emptyList()
            val doc = fetchDocument("$baseUrl/ongoing-anime/")
            parseDetpostCards(doc).map { it.first }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getPopularAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/complete-anime/" else "$baseUrl/complete-anime/page/$page/"
            val doc = fetchDocument(url)
            parseDetpostCards(doc).map { it.first }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val doc = fetchDocument("$baseUrl/?s=$encodedQuery&post_type=anime")
            val animeList = mutableListOf<Anime>()

            doc.select("ul.chivsrc > li").forEach { element ->
                try {
                    val a = element.select("h2 a").first()
                    val title = a?.text().orEmpty()
                    val url = a?.attr("href").orEmpty()
                    if (title.isEmpty() || url.isEmpty()) return@forEach

                    var status = ""
                    var score = ""
                    val genres = mutableListOf<String>()

                    element.select("div.set").forEach { set ->
                        val label = set.select("b").text().trim().lowercase()
                        when {
                            label.contains("status") -> status = set.text().replace(set.select("b").text(), "").removePrefix(":").trim()
                            label.contains("rating") -> score = set.text().replace(set.select("b").text(), "").removePrefix(":").trim()
                            label.contains("genre") -> genres.addAll(set.select("a").map { it.text() })
                        }
                    }

                    animeList.add(Anime(
                        title = title,
                        url = url,
                        imageUrl = element.select("img").attr("src"),
                        type = "",
                        status = status,
                        score = score,
                        genres = genres
                    ))
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

            // If no episode list (e.g. called with an episode URL), resolve the series page
            // via the "See All Episodes" link in .flir navigation.
            if (detail.episodes.isEmpty()) {
                val seriesUrl = doc.select("div.flir a[href*='/anime/']").first()?.attr("href").orEmpty()
                if (seriesUrl.isNotEmpty() && seriesUrl != url) {
                    Log.d("OtakudesuDetail", "No episode list, resolving to series: $seriesUrl")
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

    private fun parseAnimeDetail(doc: Document, url: String): AnimeDetail {
        val title = doc.select("h1").first()?.text().orEmpty()
        val synopsis = doc.select("div.sinopc").text()
        val imageUrl = doc.select("meta[property='og:image']").attr("content")

        var status = ""
        var type = ""
        var totalEp = ""
        var studio = ""
        var score = ""
        var released = ""
        var duration = ""
        val genres = mutableListOf<String>()

        // <div class="infozingle"><p><span><b>Judul</b>: ...</span></p>...
        doc.select("div.infozingle p span").forEach { span ->
            val label = span.select("b").text().trim().lowercase()
            val value = span.ownText().removePrefix(":").trim()
            when {
                label.contains("judul") -> {}
                label.contains("japanese") -> {}
                label.contains("skor") -> score = value
                label.contains("produser") -> {}
                label.contains("tipe") -> type = value
                label.contains("status") -> status = value
                label.contains("total episode") -> totalEp = value
                label.contains("durasi") -> duration = value
                label.contains("tanggal rilis") -> released = value
                label.contains("studio") -> studio = value
                label.contains("genre") -> genres.addAll(span.select("a").map { it.text() })
            }
        }

        val episodes = mutableListOf<Episode>()
        // Multiple .episodelist blocks (Batch / Episode List). Keep the first block that has
        // real episode links (/episode/).
        for (block in doc.select("div.episodelist")) {
            val found = mutableListOf<Episode>()
            block.select("ul li").forEach { li ->
                try {
                    val a = li.select("span a").first()
                    val epUrl = a?.attr("href").orEmpty()
                    if (!epUrl.contains("/episode/")) return@forEach

                    val epNum = Regex("-episode-(\\d+)").find(epUrl)?.groupValues?.get(1).orEmpty()
                    val epTitle = a?.text().orEmpty()
                    val epDate = li.select("span.zeebr").text()

                    found.add(Episode(
                        title = epTitle,
                        url = epUrl,
                        episodeNumber = epNum,
                        uploadDate = epDate
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (found.isNotEmpty()) {
                episodes.addAll(found)
                break
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
            season = if (released.isNotEmpty()) "Rilis: $released" else duration,
            synopsis = synopsis,
            totalEpisodes = totalEp,
            genres = genres,
            latestUpdate = released
        )

        return AnimeDetail(anime = anime, episodes = episodes)
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val servers = mutableListOf<VideoServer>()

            // Streaming iframe: #lightsVideo > iframe[src] (blogger.com/video.g?token=...)
            val iframeSrc = doc.select("#lightsVideo iframe").first()?.attr("src").orEmpty()
            if (iframeSrc.isNotEmpty()) {
                servers.add(VideoServer(name = "Blogspot", url = iframeSrc))
            }

            // Download mirrors: div.download ul li > <strong>Mp4 360p</strong> + <a href="...">Site</a>
            if (servers.isEmpty()) {
                doc.select("div.download ul li").forEach { li ->
                    try {
                        val quality = li.select("strong").text().trim()
                        val href = li.select("a").first()?.attr("href").orEmpty()
                        if (quality.isNotEmpty() && href.isNotEmpty()) {
                            servers.add(VideoServer(name = "$quality (Download)", url = href))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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
            // Blogger video.g URLs are handled by PlayerActivity's Blogspot pipeline directly.
            Log.d("OtakudesuResolve", "Resolving: ${server.name}, url=${server.url}")
            server.url
        } catch (e: Exception) {
            Log.e("OtakudesuResolve", "Error: ${e.message}")
            e.printStackTrace()
            server.url
        }
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val prevUrl = doc.select("div.flir a[title='Episode Sebelumnya']").attr("href")
            val nextUrl = doc.select("div.flir a[title='Episode Selanjutnya']").attr("href")

            EpisodeNavigation(
                prevEpisodeUrl = prevUrl,
                prevEpisodeTitle = deriveEpisodeTitle(prevUrl),
                nextEpisodeUrl = nextUrl,
                nextEpisodeTitle = deriveEpisodeTitle(nextUrl)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            EpisodeNavigation()
        }
    }

    private fun deriveEpisodeTitle(episodeUrl: String): String {
        if (episodeUrl.isEmpty()) return ""
        val num = Regex("-episode-(\\d+)").find(episodeUrl)?.groupValues?.get(1).orEmpty()
        return if (num.isNotEmpty()) "Episode $num" else ""
    }
}
