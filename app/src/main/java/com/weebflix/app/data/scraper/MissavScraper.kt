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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MissavScraper : AnimeProvider {

    override val id: String = ProviderFactory.MISSAV_ID
    override val name: String = "MissAV"
    override val defaultBaseUrl: String = "https://missav.ws"

    override var baseUrl: String
        get() = ProviderConfig.getBaseUrl(id)
        set(value) {
            ProviderConfig.setBaseUrl(id, value)
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
            .build()
    }

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .addHeader("Referer", baseUrl)
            .build()
        val response = client.newCall(request).execute()
        return response.use {
            val html = it.body?.string() ?: throw Exception("Empty response")
            Log.d("Missav", "fetchDocument ${response.code} len=${html.length} url=$url")
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
            val html = it.body?.string() ?: ""
            Log.d("Missav", "fetchHtml ${response.code} len=${html.length} url=$url")
            html
        }
    }

    // Card: .thumbnail.group > .relative > a[href*='/id/'] (video + cover-t.jpg) + span (duration)
    //       + .my-2 a (full title). Grid on /id/release and /id/search/{q} pages.
    private fun parseThumbnailCards(doc: Document): List<Anime> {
        val result = mutableListOf<Anime>()
        doc.select(".thumbnail.group").forEach { element ->
            try {
                val a = element.select("a[href*='/id/']").first()
                val href = a?.attr("href").orEmpty()
                val title = element.select(".my-2 a").first()?.text().orEmpty()
                    .ifEmpty { a?.attr("alt").orEmpty() }
                    .ifEmpty { element.select("img").attr("alt") }
                if (title.isEmpty() || href.isEmpty()) return@forEach

                val url = normalizeDetailUrl(href)
                val imageUrl = element.select("img").attr("data-src").ifEmpty { element.select("img").attr("src") }
                val duration = element.select("span").firstOrNull { it.text().matches(Regex("^\\d{1,3}:\\d{2}:\\d{2}$")) }
                    ?.text().orEmpty()

                result.add(Anime(
                    title = title,
                    url = url,
                    imageUrl = imageUrl,
                    episode = duration,
                    type = "JAV"
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Log.d("Missav", "parseThumbnailCards -> ${result.size} cards")
        return result
    }

    private fun normalizeDetailUrl(href: String): String {
        if (href.isEmpty()) return ""
        val slug = Regex("/(?:[a-z]{2}/)?id/([^/?]+)").find(href)?.groupValues?.getOrNull(1).orEmpty()
        return if (slug.isNotEmpty()) "$baseUrl/id/$slug" else href
    }

    override suspend fun getLatestEpisodes(page: Int): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/id/release?page=$page")
            parseThumbnailCards(doc).map {
                Episode(
                    title = it.title,
                    url = it.url,
                    imageUrl = it.imageUrl,
                    episodeNumber = it.episode,
                    uploadDate = it.episode
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getOngoingAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/id/release?sort=published_at&page=$page")
            parseThumbnailCards(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getPopularAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/id/release?sort=weekly_views&page=$page")
            parseThumbnailCards(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getUncensoredAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$baseUrl/id/uncensored-leak?page=$page")
            parseThumbnailCards(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val doc = fetchDocument("$baseUrl/id/search/$encodedQuery")
            parseThumbnailCards(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getAnimeDetail(url: String): AnimeDetail = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(url)
            parseAnimeDetail(doc, url)
        } catch (e: Exception) {
            e.printStackTrace()
            AnimeDetail(anime = Anime(title = "Error", synopsis = e.message ?: "Unknown error"))
        }
    }

    private fun parseAnimeDetail(doc: Document, url: String): AnimeDetail {
        val title = doc.select("meta[property='og:title']").attr("content")
            .ifEmpty { doc.select("h1").first()?.text().orEmpty() }
        val imageUrl = doc.select("meta[property='og:image']").attr("content")
        val durationSeconds = doc.select("meta[property='og:video:duration']").attr("content")
            .toLongOrNull() ?: 0L
        val duration = formatDuration(durationSeconds)

        var maker = ""
        var released = ""
        val genres = mutableListOf<String>()

        doc.select("div.text-secondary").forEach { block ->
            try {
                val label = block.select("span").first()?.text().orEmpty().lowercase()
                when {
                    label.contains("genre") -> genres.addAll(block.select("a").map { it.text() })
                    label.contains("pembuat") -> maker = block.select("a").first()?.text().orEmpty()
                    label.contains("tanggal rilis") -> released = block.select("time").attr("datetime").take(10)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val episode = Episode(
            title = title,
            url = url,
            imageUrl = imageUrl,
            episodeNumber = "1",
            uploadDate = duration
        )

        val anime = Anime(
            title = title,
            url = url,
            imageUrl = imageUrl,
            episode = duration,
            type = "JAV",
            studio = maker,
            season = if (released.isNotEmpty()) "Rilis: $released" else "",
            synopsis = "",
            totalEpisodes = "1",
            genres = genres,
            latestUpdate = released
        )

        return AnimeDetail(anime = anime, episodes = listOf(episode))
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(episodeUrl)
            val unpacked = unpackPackedJs(html)
            val m3u8 = Regex("""source\s*=\s*'([^']+playlist\.m3u8)'""")
                .find(unpacked)?.groupValues?.getOrNull(1)
                ?: Regex("""source\s*=\s*'([^']+\.m3u8)'""")
                    .find(unpacked)?.groupValues?.getOrNull(1)
            if (m3u8.isNullOrEmpty()) {
                Log.d("Missav", "No m3u8 found for $episodeUrl")
                return@withContext emptyList()
            }
            listOf(VideoServer(
                name = "MissAV",
                url = m3u8,
                videoUrl = m3u8,
                dataType = "hls"
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            if (server.videoUrl.contains(".m3u8")) return@withContext server.videoUrl
            val html = fetchHtml(episodeUrl)
            val unpacked = unpackPackedJs(html)
            Regex("""source\s*=\s*'([^']+playlist\.m3u8)'""")
                .find(unpacked)?.groupValues?.getOrNull(1)
                ?: Regex("""source\s*=\s*'([^']+\.m3u8)'""")
                    .find(unpacked)?.groupValues?.getOrNull(1)
                ?: ""
        } catch (e: Exception) {
            Log.e("Missav", "resolve error: ${e.message}")
            e.printStackTrace()
            server.videoUrl
        }
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        // MissAV is single-video per page — no prev/next episode navigation.
        EpisodeNavigation()
    }

    private fun unpackPackedJs(html: String): String {
        val pattern = Regex(
            """eval\(function\(p,a,c,k,e,d\)\s*\{.*?\}\(\s*'((?:[^'\\]|\\.)*)',(\d+),(\d+),\s*'((?:[^'\\]|\\.)*)'(?:\.split\('[^']*'\))?,\d+,\{\}\s*\)\s*\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val m = pattern.find(html) ?: return html
        return try {
            // MissAV packs its m3u8 URLs inside a single-quoted JS literal, so quotes are
            // escaped as \' — unescape before token replacement or the downstream regex misses.
            val payload = m.groupValues[1].replace("\\'", "'")
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
}
