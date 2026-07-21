package com.weebflix.app.data.scraper

import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class SamehadakuScraper {

    companion object {
        const val BASE_URL = "https://v2.samehadaku.how"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw Exception("Empty response")
        return Jsoup.parse(html)
    }

    suspend fun getLatestEpisodes(): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(BASE_URL)
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
            val doc = fetchDocument("$BASE_URL/daftar-anime-2")
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
            val doc = fetchDocument(BASE_URL)
            val animeList = mutableListOf<Anime>()

            doc.select(".widget.poplr ul li, .widget.populer ul li, .widgetseries.poplr ul li").forEach { element ->
                try {
                    val title = element.select("a").text()
                    val url = element.select("a").attr("href")
                    val imageUrl = element.select("img").attr("src")
                    val episode = element.select(".ctr, .numep").text()

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

            if (animeList.isEmpty()) {
                doc.select(".widgetschedule .widget-body .slider .items .item, .widget.schedule .widget-body .slider .items .item").forEach { element ->
                    try {
                        val title = element.select(".info .name, a").text()
                        val url = element.select("a").attr("href")
                        val imageUrl = element.select("img").attr("src")

                        if (title.isNotEmpty()) {
                            animeList.add(
                                Anime(
                                    title = title,
                                    url = url,
                                    imageUrl = imageUrl
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (animeList.isEmpty()) {
                doc.select(".post_taxs a, .filter a").forEach { element ->
                    val title = element.text()
                    val url = element.attr("href")
                    if (title.isNotEmpty() && url.isNotEmpty()) {
                        animeList.add(Anime(title = title, url = url))
                    }
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
            val doc = fetchDocument("$BASE_URL/?s=$query")
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
            doc.select(".eplister ul li, .elist ul li, #episode list ul li").forEach { element ->
                try {
                    val epTitle = element.select(".epl-title, .epl-name, a").text()
                    val epUrl = element.select("a").attr("href")
                    val epNum = element.select(".epl-num, .epnum").text()
                    val epDate = element.select(".epl-date, .newnime, .date").text()

                    if (epTitle.isNotEmpty()) {
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

            doc.select("#server .east_player_option, #embedserver li, .mirror option, #servers-list li").forEach { element ->
                try {
                    val name = element.select("span, .server-label, option").text()
                        .ifEmpty { element.attr("data-type") }
                        .ifEmpty { element.select("a").text() }
                    val dataUrl = element.attr("data-url")
                        .ifEmpty { element.select("a").attr("href") }
                        .ifEmpty { element.select("iframe").attr("src") }

                    if (name.isNotEmpty() && dataUrl.isNotEmpty()) {
                        servers.add(VideoServer(name = name, url = dataUrl))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty() && servers.none { it.url == src }) {
                    servers.add(VideoServer(name = "Server ${servers.size + 1}", url = src))
                }
            }

            if (servers.isEmpty()) {
                doc.select("iframe").forEach { iframe ->
                    val src = iframe.attr("src")
                        .ifEmpty { iframe.attr("data-src") }
                        .ifEmpty { iframe.attr("data-lazy-src") }
                    if (src.isNotEmpty()) {
                        servers.add(VideoServer(name = "Server ${servers.size + 1}", url = src))
                    }
                }
            }

            servers
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSchedule(): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$BASE_URL/jadwal-rilis/")
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
}
