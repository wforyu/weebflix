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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DrakorKitaScraper : AnimeProvider {

    override val id: String = ProviderFactory.DRAKORKITA_ID
    override val name: String = "DrakorKita"
    override val defaultBaseUrl: String = "https://drakor.kita.mobi"

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
            .build()

        val response = client.newCall(request).execute()
        return response.use {
            val html = it.body?.string() ?: throw Exception("Empty response")
            Jsoup.parse(html)
        }
    }

    data class HomeContent(
        val latestEpisodes: List<Episode>,
        val movies: List<Anime>,
        val series: List<Anime>,
        val featured: List<Anime>
    )

    suspend fun getHomeContent(): HomeContent = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(baseUrl)
            val colLg8 = doc.select(".col-lg-8").firstOrNull() ?: doc

            val sections = colLg8.select("h4.heading1")
            val episodes = mutableListOf<Episode>()
            val movies = mutableListOf<Anime>()
            val series = mutableListOf<Anime>()

            for (heading in sections) {
                val headingText = heading.text().lowercase()
                val row = heading.nextElementSibling() ?: continue
                val cards = row.select(".bungkus")

                for (bungkus in cards) {
                    val card = bungkus.parent()?.parent() ?: continue
                    val link = card.select("a[href*='detail/']").firstOrNull() ?: continue
                    val href = link.attr("href")
                    if (href.isEmpty()) continue

                    val title = bungkus.select(".titit").text().ifEmpty { link.attr("title") }
                    val posterImg = bungkus.select("img.poster").firstOrNull()
                    val imageUrl = posterImg?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""
                    val epText = bungkus.select(".rate").text()
                    val quality = bungkus.select(".qua").text()
                    val rating = bungkus.select(".rat").text().replace(Regex("[^\\d.]"), "").trim()
                    val duration = bungkus.select(".type").text()

                    if (headingText.contains("eps terbaru")) {
                        val epNum = Regex("(\\d+/\\d+|\\d+ END)").find(epText)?.groupValues?.get(1) ?: epText
                        if (title.isNotEmpty()) {
                            episodes.add(Episode(
                                title = title,
                                url = normalizeUrl(href),
                                imageUrl = imageUrl,
                                episodeNumber = epNum,
                                uploadDate = quality
                            ))
                        }
                    } else if (headingText.contains("movie terbaru")) {
                        if (title.isNotEmpty()) {
                            movies.add(Anime(
                                title = title,
                                url = normalizeUrl(href),
                                imageUrl = imageUrl,
                                episode = duration,
                                type = "Movie",
                                score = rating,
                                status = quality
                            ))
                        }
                    } else if (headingText.contains("serie terbaru")) {
                        val epNum = Regex("(\\d+/\\d+|\\d+ END)").find(epText)?.groupValues?.get(1) ?: epText
                        if (title.isNotEmpty()) {
                            series.add(Anime(
                                title = title,
                                url = normalizeUrl(href),
                                imageUrl = imageUrl,
                                episode = epNum,
                                type = "TV",
                                score = rating,
                                status = quality
                            ))
                        }
                    }
                }
            }

            val featured = (episodes.take(5).map { ep ->
                Anime(title = ep.title, url = ep.url, imageUrl = ep.imageUrl, episode = ep.episodeNumber, type = "TV")
            } + movies.take(3) + series.take(2)).shuffled().take(10)

            HomeContent(latestEpisodes = episodes, movies = movies, series = series, featured = featured)
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error fetching home content", e)
            HomeContent(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    override suspend fun getLatestEpisodes(page: Int): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val home = getHomeContent()
            home.latestEpisodes
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error in getLatestEpisodes", e)
            emptyList()
        }
    }

    override suspend fun getOngoingAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/all?media_type=movie" else "$baseUrl/all?media_type=movie&page=$page"
            val doc = fetchDocument(url)
            parseAnimeGrid(doc)
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error in getOngoingAnime", e)
            emptyList()
        }
    }

    override suspend fun getPopularAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/all?media_type=tv" else "$baseUrl/all?media_type=tv&page=$page"
            val doc = fetchDocument(url)
            parseAnimeGrid(doc)
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error in getPopularAnime", e)
            emptyList()
        }
    }

    suspend fun getAllAnime(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$baseUrl/all" else "$baseUrl/all?page=$page"
            val doc = fetchDocument(url)
            parseAnimeGrid(doc)
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error in getAllAnime", e)
            emptyList()
        }
    }

    override suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val doc = fetchDocument("$baseUrl/all?q=$encodedQuery")
            parseAnimeGrid(doc)
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error in searchAnime", e)
            emptyList()
        }
    }

    private fun rewriteToCurrentDomain(url: String): String {
        return try {
            val baseHost = java.net.URL(defaultBaseUrl).host
            val u = java.net.URL(url)
            if (oldDomains.any { u.host?.contains(it) == true || it == u.host }) {
                val newUrl = url.replace(u.host!!, baseHost)
                android.util.Log.e("DEBUG_DRAKOR", "Rewrote URL: $url -> $newUrl")
                newUrl
            } else url
        } catch (_: Exception) { url }
    }

    override suspend fun getAnimeDetail(url: String): AnimeDetail = withContext(Dispatchers.IO) {
        try {
            val fixedUrl = rewriteToCurrentDomain(url)
            android.util.Log.e("DEBUG_DRAKOR", "Fetching detail: $fixedUrl (was: $url)")
            val doc = fetchDocument(fixedUrl)
            android.util.Log.e("DEBUG_DRAKOR", "Doc fetched, title elements: ${doc.select("h1").size}")

            val title = doc.select(".infox h1[itemprop='headline'], h1[itemprop='headline']").text()
                .ifEmpty { doc.select(".infox h1").text() }
                .ifEmpty { doc.select("h1").text() }
                .let { t ->
                    t.replace(Regex("^Nonton\\s+"), "")
                        .replace(Regex("\\[.*?\\].*"), "")
                        .replace(Regex("\\d+p.*"), "")
                        .replace(Regex("\\s*Subtitle\\s+Indonesia.*"), "")
                        .trim()
                }

            val synopsis = doc.select(".mv-description, .entry-content, .sinopsis, .infox .desc, .description").text()
                .ifEmpty { doc.select("meta[name=description]").attr("content") }

            val imageUrl = doc.select(".thumb img, .bigcontent .thumb img, .poster img, .infox img").firstOrNull()?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            } ?: ""

            var status = ""
            var type = ""
            var totalEp = ""
            var studio = ""
            var season = ""
            var score = ""
            var duration = ""
            val genres = mutableListOf<String>()

            doc.select(".infox .spe span, .info-content .spe span, .info-content .spe, .spe span, .dataleft span, .data span").forEach { span ->
                val text = span.text().lowercase()
                when {
                    text.contains("status") -> status = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Status:", "").trim() }
                    text.contains("type") || text.contains("tipe") || text.contains("format") -> type = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Type:", "").trim() }
                    text.contains("episode") || text.contains("eps") -> totalEp = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Episode:", "").trim() }
                    text.contains("studio") || text.contains("network") -> studio = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Studio:", "").trim() }
                    text.contains("season") -> season = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Season:", "").trim() }
                    text.contains("rating") || text.contains("score") || text.contains("skor") -> score = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Rating:", "").trim() }
                    text.contains("duration") || text.contains("durasi") -> duration = span.select("span:last-child, b:last-child").text().ifEmpty { span.text().replace("Duration:", "").trim() }
                }
            }

            doc.select(".gnr a, .genrez a, .mgen a, .infox .genrez a, .genre a, a[href*='genre']").forEach { a ->
                val genreText = a.text().trim()
                if (genreText.isNotEmpty()) genres.add(genreText)
            }

            val animeType = type.ifEmpty {
                val ogType = doc.select("meta[property='og:type']").attr("content")
                if (ogType.contains("video.movie")) "Movie" else if (ogType.contains("video.tv")) "TV" else ""
            }

            val isMovie = animeType.equals("Movie", ignoreCase = true) ||
                duration.isNotEmpty() && !duration.contains("EP") && status.lowercase().contains("released")

            val episodes = mutableListOf<Episode>()

            val html = doc.html()

            val loadEpisodeRegex = Regex("loadEpisode\\('([^']+)'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'\\)")

            val loadEpisodeMatches = mutableListOf<Triple<String, String, String>>()

            doc.select("a.btn-svx[onclick], a.btn-sv[onclick]").forEach { el ->
                val onclick = el.attr("onclick")
                val match = loadEpisodeRegex.find(onclick)
                if (match != null) {
                    loadEpisodeMatches.add(Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3]))
                }
            }
            android.util.Log.e("DEBUG_DRAKOR", "Found ${loadEpisodeMatches.size} loadEpisode from onclick attrs")

            if (loadEpisodeMatches.isEmpty()) {
                val allScriptTexts = doc.select("script").map { it.html() }
                val loadEpisodeScript = allScriptTexts.firstOrNull { it.contains("loadEpisode(") }
                android.util.Log.e("DEBUG_DRAKOR", "Fallback to script tag: ${loadEpisodeScript != null}")
                if (loadEpisodeScript != null) {
                    loadEpisodeRegex.findAll(loadEpisodeScript).forEach { match ->
                        loadEpisodeMatches.add(Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3]))
                    }
                }
            }

            val decodedTokens = decodePageTokens(html)
            android.util.Log.e("DEBUG_DRAKOR", "Decoded tokens: c=${decodedTokens.c.take(20)}..., t=${decodedTokens.t.take(20)}..., ver=${decodedTokens.ver.take(10)}..., movieId=${decodedTokens.movieId}")

            val firstMatch = loadEpisodeMatches.firstOrNull()
            val movieId = firstMatch?.first ?: decodedTokens.movieId
            val tag = firstMatch?.second ?: decodedTokens.tag
            val cat = firstMatch?.third ?: decodedTokens.cat

            android.util.Log.e("DEBUG_DRAKOR", "Using: movieId=$movieId, tag=$tag, cat=$cat")

            if (movieId.isNotEmpty()) {
                try {
                    val cParam = if (decodedTokens.c.isNotEmpty()) "&c=${decodedTokens.c}" else ""
                    val tParam = if (decodedTokens.t.isNotEmpty()) "&t=${decodedTokens.t}" else ""
                    val verParam = if (decodedTokens.ver.isNotEmpty()) "&ver=${decodedTokens.ver}" else ""
                    val apiUrl = "https://api.nonton.bid/c_api/episode.php?is_mob=0&is_uc=0&movie_id=$movieId&tag=$tag&cat=$cat$cParam$tParam$verParam"
                    android.util.Log.e("DEBUG_DRAKOR", "Calling API: $apiUrl")
                    val apiRequest = Request.Builder()
                        .url(apiUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .addHeader("Referer", fixedUrl)
                        .addHeader("Origin", fixedUrl)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val apiResponse = client.newCall(apiRequest).execute()
                    val apiBody = apiResponse.use { it.body?.string() ?: "" }
                    android.util.Log.e("DEBUG_DRAKOR", "API code=${apiResponse.code}, len=${apiBody.length}, has_episode_lists=${apiBody.contains("episode_lists")}")

                    if (apiBody.isNotEmpty() && apiBody.contains("episode_lists")) {
                        val epListsHtml = Regex("\"episode_lists\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                            .find(apiBody)?.groupValues?.get(1)
                            ?.replace("\\/", "/")
                            ?.replace("\\\"", "\"")
                            ?.replace("\\n", "\n") ?: ""

                        android.util.Log.e("DEBUG_DRAKOR", "episode_lists HTML len: ${epListsHtml.length}")

                        if (epListsHtml.isNotEmpty()) {
                            val epDoc = Jsoup.parseBodyFragment(epListsHtml)
                            val btnSvrElements = epDoc.select("a.btn-svr[data-epid]")
                            android.util.Log.e("DEBUG_DRAKOR", "btn-svr[data-epid] count: ${btnSvrElements.size}")

                            val activeCat = Regex("\"active_cat\"\\s*:\\s*\"([^\"]*?)\"").find(apiBody)?.groupValues?.get(1) ?: cat
                            val activeTag = Regex("\"active_tag\"\\s*:\\s*\"([^\"]*?)\"").find(apiBody)?.groupValues?.get(1) ?: tag

                            var hasMovieServerBtn = false
                            btnSvrElements.forEach { a ->
                                val epId = a.attr("data-epid")
                                val epNum = a.text().trim()
                                val epCat = a.attr("data-cat").ifBlank { activeCat }
                                val epTag = a.attr("data-tag").ifBlank { activeTag }
                                if (epNum.equals("unnamed", ignoreCase = true) ||
                                    a.classNames().any { it.contains("unnamed", ignoreCase = true) }) {
                                    hasMovieServerBtn = true
                                }
                                if (epId.isNotEmpty() && epNum.isNotEmpty()) {
                                    val encodedUrl = "$fixedUrl?mid=$movieId&eid=$epId&tag=$epTag&cat=$epCat&ep=$epNum&sv=$epTag"
                                    episodes.add(Episode(
                                        title = "Episode $epNum",
                                        url = encodedUrl,
                                        episodeNumber = epNum
                                    ))
                                }
                            }
                            if (hasMovieServerBtn) {
                                android.util.Log.e("DEBUG_DRAKOR", "Movie detected via unnamed server button, using eid=movie download pipeline")
                                val firstBtn = btnSvrElements.firstOrNull()
                                val mTag = firstBtn?.attr("data-tag")?.ifBlank { tag } ?: tag
                                val mCat = firstBtn?.attr("data-cat")?.ifBlank { cat } ?: cat
                                episodes.clear()
                                episodes.add(Episode(
                                    title = title,
                                    url = "$fixedUrl?mid=$movieId&eid=movie&tag=$mTag&cat=$mCat&ep=1&sv=$mTag",
                                    episodeNumber = "1"
                                ))
                            }
                            android.util.Log.e("DEBUG_DRAKOR", "Parsed ${episodes.size} episodes from API")
                        }
                    } else {
                        android.util.Log.e("DEBUG_DRAKOR", "API missing episode_lists. Body: ${apiBody.take(300)}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DEBUG_DRAKOR", "API error: ${e.message}", e)
                }
            }

            android.util.Log.e("DEBUG_DRAKOR", "After API: ${episodes.size} episodes, isMovie=$isMovie")

            if (episodes.isEmpty()) {
                android.util.Log.e("DEBUG_DRAKOR", "Trying HTML fallback selectors...")
                doc.select(".listeps ul li, .eplister ul li, .elist ul li, #episode_lists li").forEach { element ->
                    try {
                        val epTitle = element.select(".lchx a, .epl-title, .epl-name, a").text()
                        val epUrl = element.select("a").attr("href")
                        val epNum = element.select(".eps a, .epl-num, .epnum").text()
                        val epDate = element.select(".date, .epl-date, .newnime").text()

                        if (epTitle.isNotEmpty() && epUrl.isNotEmpty()) {
                            episodes.add(Episode(
                                title = epTitle,
                                url = normalizeUrl(epUrl),
                                episodeNumber = epNum,
                                uploadDate = epDate
                            ))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (isMovie && episodes.isEmpty() && movieId.isNotEmpty()) {
                val mTag = tag.ifEmpty { "hs" }
                val mCat = cat.ifEmpty { "ind" }
                episodes.add(Episode(
                    title = title,
                    url = "$fixedUrl?mid=$movieId&eid=movie&tag=$mTag&cat=$mCat&ep=1&sv=$mTag",
                    episodeNumber = "1"
                ))
            }

            val anime = Anime(
                title = title,
                url = fixedUrl,
                imageUrl = imageUrl,
                episode = episodes.firstOrNull()?.episodeNumber ?: "",
                type = animeType,
                status = status,
                score = score,
                studio = studio,
                season = season,
                synopsis = synopsis,
                totalEpisodes = totalEp,
                genres = genres
            )

            android.util.Log.e("DEBUG_DRAKOR", "Final: ${episodes.size} episodes, title=$title, type=$animeType")
            AnimeDetail(anime = anime, episodes = episodes.reversed())
        } catch (e: Exception) {
            android.util.Log.e("DEBUG_DRAKOR", "Error in getAnimeDetail: ${e.message}", e)
            Log.e("DrakorKita", "Error in getAnimeDetail", e)
            AnimeDetail(anime = Anime(title = "Error", synopsis = e.message ?: "Unknown error"))
        }
    }

    override suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            val servers = mutableListOf<VideoServer>()

            if (episodeUrl.contains("mid=") && episodeUrl.contains("eid=")) {
                val params = parseEpisodeUrl(episodeUrl)
                val movieId = params["mid"] ?: ""
                val epId = params["eid"] ?: ""
                val tag = params["tag"] ?: "hs"
                val cat = params["cat"] ?: "ind"

                if (movieId.isNotEmpty()) {
                    val downloadServers = resolveDownloadServers(episodeUrl, movieId, params["ep"] ?: "1", cat, epId == "movie")
                    if (downloadServers.isNotEmpty()) {
                        return@withContext downloadServers
                    }
                    val doc = fetchDocument(episodeUrl.substringBefore("?"))

                    doc.select("#server_lists .btn-svx, .pagination .btn-svx").forEach { element ->
                        try {
                            val name = element.text().trim()
                            val onclick = element.attr("onclick")
                            val allMatches = Regex("'([^']+)'").findAll(onclick).map { it.groupValues[1] }.toList()
                            val srvMovieId = allMatches.getOrElse(0) { movieId }
                            val serverType = allMatches.getOrElse(1) { tag }
                            val lang = allMatches.getOrElse(2) { cat }

                            if (name.isNotEmpty()) {
                                servers.add(VideoServer(
                                    name = name,
                                    url = episodeUrl,
                                    dataPost = srvMovieId,
                                    dataNume = serverType,
                                    dataType = lang
                                ))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (servers.isEmpty()) {
                        val scripts = doc.select("script").map { it.html() }
                        val loadEpisodeScript = scripts.firstOrNull { it.contains("loadEpisode(") }
                        if (loadEpisodeScript != null) {
                            val scriptMatches = Regex("loadEpisode\\('([^']+)'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'\\)").findAll(loadEpisodeScript)
                            val seenTypes = mutableSetOf<String>()
                            for (match in scriptMatches) {
                                val srvMovieId = match.groupValues[1]
                                val serverType = match.groupValues[2]
                                val lang = match.groupValues[3]
                                val key = "$serverType|$lang"
                                if (key in seenTypes) continue
                                seenTypes.add(key)
                                servers.add(VideoServer(
                                    name = "DrakorKita ${serverType.uppercase()} ($lang)",
                                    url = episodeUrl,
                                    dataPost = srvMovieId,
                                    dataNume = serverType,
                                    dataType = lang
                                ))
                            }
                        }
                    }

                    if (servers.isEmpty()) {
                        servers.add(VideoServer(
                            name = "DrakorKita",
                            url = episodeUrl,
                            dataPost = movieId,
                            dataNume = tag,
                            dataType = cat
                        ))
                    }

                    return@withContext servers
                }
            }

            val doc = fetchDocument(episodeUrl)

            doc.select("#server_lists .btn-svx, #server_lists button, .server-list button, #servers-list .btn, .mirrorstream .btn, .player-servers button, .servers button").forEach { element ->
                try {
                    val name = element.text().trim()
                    val onclick = element.attr("onclick")

                    val allMatches = Regex("'([^']+)'").findAll(onclick).map { it.groupValues[1] }.toList()
                    val movieId = allMatches.getOrElse(0) { "" }
                    val serverType = allMatches.getOrElse(1) { "" }
                    val lang = allMatches.getOrElse(2) { "" }

                    if (name.isNotEmpty() && movieId.isNotEmpty()) {
                        servers.add(VideoServer(
                            name = name,
                            url = episodeUrl,
                            dataPost = movieId,
                            dataNume = serverType,
                            dataType = lang
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (servers.isEmpty()) {
                val scripts = doc.select("script").map { it.html() }
                val loadEpisodeScript = scripts.firstOrNull { it.contains("loadEpisode(") }
                if (loadEpisodeScript != null) {
                    val matches = Regex("loadEpisode\\('([^']+)'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'\\)").findAll(loadEpisodeScript)
                    val seenTypes = mutableSetOf<String>()
                    for (match in matches) {
                        val movieId = match.groupValues[1]
                        val serverType = match.groupValues[2]
                        val lang = match.groupValues[3]
                        val key = "$serverType|$lang"
                        if (key in seenTypes) continue
                        seenTypes.add(key)
                        servers.add(VideoServer(
                            name = "DrakorKita ${serverType.uppercase()} ($lang)",
                            url = episodeUrl,
                            dataPost = movieId,
                            dataNume = serverType,
                            dataType = lang
                        ))
                    }
                }
            }

            if (servers.isEmpty()) {
                doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                    val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                    if (src.isNotEmpty() && !src.contains("about:blank")) {
                        val serverName = iframe.attr("title").ifEmpty { "Server ${servers.size + 1}" }
                        servers.add(VideoServer(
                            name = serverName,
                            url = normalizeUrl(src)
                        ))
                    }
                }
            }

            servers
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error in getEpisodeServers", e)
            emptyList()
        }
    }

    private fun parseEpisodeUrl(url: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val queryString = url.substringAfter("?", "")
        if (queryString.isNotEmpty()) {
            queryString.split("&").forEach { param ->
                val kv = param.split("=", limit = 2)
                if (kv.size == 2) {
                    params[kv[0]] = kv[1]
                }
            }
        }
        return params
    }

    override suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String = withContext(Dispatchers.IO) {
        try {
            if (server.dataType == "dl" && server.dataPost.isNotEmpty()) {
                if (server.videoUrl.isNotEmpty() && (server.videoUrl.contains(".mp4") || server.videoUrl.contains(".m3u8"))) {
                    return@withContext server.videoUrl
                }
                val dlUrl = resolveDlFileMob(server.dataPost)
                if (dlUrl.isNotEmpty()) return@withContext dlUrl
            }

            if (server.url.isNotEmpty() && (server.url.contains(".mp4") || server.url.contains(".m3u8") || server.url.contains(".mpd"))) {
                return@withContext server.url
            }

            if (server.dataPost.isNotEmpty() && server.dataNume.isNotEmpty()) {
                Log.d("DrakorKita", "Resolving via API chain: movieId=${server.dataPost}, type=${server.dataNume}, lang=${server.dataType}")

                val epDoc = fetchDocument(episodeUrl.substringBefore("?"))
                var cVal = ""
                var tVal = ""
                val scripts = epDoc.select("script")
                for (script in scripts) {
                    val txt = script.html()
                    val cm = Regex("""var\s+c\s*=\s*['"]([^'"]+)['"]""").find(txt)
                    val tm = Regex("""var\s+t\s*=\s*['"]([^'"]+)['"]""").find(txt)
                    if (cm != null) cVal = cm.groupValues[1]
                    if (tm != null) tVal = tm.groupValues[1]
                }
                Log.d("DrakorKita", "Extracted tokens (simple): c=$cVal, t=$tVal")

                if (cVal.isEmpty() || tVal.isEmpty()) {
                    val html = try {
                        val req = Request.Builder().url(episodeUrl.substringBefore("?"))
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                            .build()
                        client.newCall(req).execute().use { it.body?.string() ?: "" }
                    } catch (e: Exception) { "" }
                    val cm2 = Regex("""var\s+c\s*=\s*['"]([^'"]+)['"]""").find(html)
                    val tm2 = Regex("""var\s+t\s*=\s*['"]([^'"]+)['"]""").find(html)
                    if (cm2 != null) cVal = cm2.groupValues[1]
                    if (tm2 != null) tVal = tm2.groupValues[1]
                }

                if (cVal.isEmpty() || tVal.isEmpty()) {
                    val pageHtml = try {
                        val req = Request.Builder().url(episodeUrl.substringBefore("?"))
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                            .build()
                        client.newCall(req).execute().use { it.body?.string() ?: "" }
                    } catch (e: Exception) { "" }
                    if (pageHtml.isNotEmpty()) {
                        val decoded = decodePageTokens(pageHtml)
                        if (decoded.c.isNotEmpty()) cVal = decoded.c
                        if (decoded.t.isNotEmpty()) tVal = decoded.t
                        Log.d("DrakorKita", "Extracted tokens (decodePageTokens): c=${cVal.take(20)}, t=${tVal.take(20)}")
                    }
                }

                val apiHost = "https://api.nonton.bid/c_api"
                val params = parseEpisodeUrl(episodeUrl)
                val targetEid = params["eid"] ?: ""
                val movieId = server.dataPost
                val serverType = server.dataNume
                val lang = server.dataType

                val epUrl = "$apiHost/episode.php?is_mob=0&is_uc=0&movie_id=$movieId&tag=$serverType&cat=$lang"
                Log.d("DrakorKita", "Step 1: $epUrl")
                val epReq = Request.Builder().url(epUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                    .addHeader("Referer", "https://drakor.kita.mobi/")
                    .addHeader("Origin", "https://drakor.kita.mobi")
                    .build()
                val epResp = client.newCall(epReq).execute()
                val epBody = epResp.use { it.body?.string() ?: "" }
                Log.d("DrakorKita", "Step 1 response (${epResp.code}): ${epBody.take(300)}")

                var serverXid = ""
                var firstEpId = targetEid
                try {
                    val epJson = org.json.JSONObject(epBody)
                    serverXid = epJson.optString("server_xid", "")
                    firstEpId = epJson.optString("first_ep_id", targetEid)
                } catch (e: Exception) {
                    val sxMatch = Regex(""""server_xid"\s*:\s*"([^"]+)"""").find(epBody)
                    if (sxMatch != null) serverXid = sxMatch.groupValues[1]
                    val feMatch = Regex(""""first_ep_id"\s*:\s*"([^"]+)"""").find(epBody)
                    if (feMatch != null) firstEpId = feMatch.groupValues[1]
                }
                val targetEp = if (targetEid.isNotEmpty()) targetEid else firstEpId
                Log.d("DrakorKita", "serverXid=$serverXid, targetEp=$targetEp")

                val srvBody = "is_mob=0&is_uc=0" +
                    "&episode_id=${java.net.URLEncoder.encode(targetEp, "UTF-8")}" +
                    "&cat=${java.net.URLEncoder.encode(serverType, "UTF-8")}" +
                    "&tag=${java.net.URLEncoder.encode(lang, "UTF-8")}" +
                    "&server_xid=${java.net.URLEncoder.encode(serverXid, "UTF-8")}" +
                    "&c=${java.net.URLEncoder.encode(cVal, "UTF-8")}" +
                    "&t=${java.net.URLEncoder.encode(tVal, "UTF-8")}"
                Log.d("DrakorKita", "Step 2: server.php")
                val srvReq = Request.Builder().url("$apiHost/server.php")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer", "https://drakor.kita.mobi/")
                    .addHeader("Origin", "https://drakor.kita.mobi")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .post(srvBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()
                val srvResp = client.newCall(srvReq).execute()
                val srvRespBody = srvResp.use { it.body?.string() ?: "" }
                Log.d("DrakorKita", "Step 2 response (${srvResp.code}): ${srvRespBody.take(500)}")

                if (srvResp.code == 200 && srvRespBody.isNotEmpty()) {
                    try {
                        val srvJson = org.json.JSONObject(srvRespBody)
                        val serverUrl = srvJson.optString("server_url", "")
                            .ifEmpty { srvJson.optString("url", "") }
                            .ifEmpty { srvJson.optString("embed_url", "") }
                            .ifEmpty { srvJson.optString("id", "") }
                            .ifEmpty { srvJson.optString("hydrax_id", "") }
                            .ifEmpty { srvJson.optString("file", "") }
                            .ifEmpty { srvJson.optString("video_url", "") }
                        Log.d("DrakorKita", "serverUrl from API: $serverUrl")

                        if (serverUrl.startsWith("http") && (serverUrl.contains(".mp4") || serverUrl.contains(".m3u8"))) {
                            Log.d("DrakorKita", "Found direct video URL: $serverUrl")
                            return@withContext serverUrl
                        }

                        if (serverUrl.isNotEmpty() && !serverUrl.startsWith("http") && serverUrl.length > 3) {
                            val abyssUrl = "https://abysscdn.com/?v=$serverUrl"
                            Log.d("DrakorKita", "Resolved Abyss URL: $abyssUrl")
                            val resolvedAbyss = resolveAbyssUrl(abyssUrl)
                            if (resolvedAbyss.contains(".mp4") || resolvedAbyss.contains(".m3u8")) {
                                return@withContext resolvedAbyss
                            }
                        }

                        if (serverUrl.startsWith("http") && (serverUrl.contains("abysscdn") || serverUrl.contains("hydrax"))) {
                            val resolvedAbyss = resolveAbyssUrl(serverUrl)
                            if (resolvedAbyss.contains(".mp4") || resolvedAbyss.contains(".m3u8")) {
                                return@withContext resolvedAbyss
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DrakorKita", "Step 2 parse error: ${e.message}")
                    }
                }

                val hydBody = "is_uc=0" +
                    "&id=${java.net.URLEncoder.encode(targetEp, "UTF-8")}" +
                    "&qua=hd&res=800x480" +
                    "&server_id=${java.net.URLEncoder.encode(serverXid, "UTF-8")}" +
                    "&cat=${java.net.URLEncoder.encode(serverType, "UTF-8")}" +
                    "&tag=${java.net.URLEncoder.encode(lang, "UTF-8")}" +
                    "&c=${java.net.URLEncoder.encode(cVal, "UTF-8")}" +
                    "&t=${java.net.URLEncoder.encode(tVal, "UTF-8")}"
                Log.d("DrakorKita", "Step 3: video_hydrax.php")
                val hydReq = Request.Builder().url("$apiHost/video_hydrax.php")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer", "https://drakor.kita.mobi/")
                    .addHeader("Origin", "https://drakor.kita.mobi")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .post(hydBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()
                val hydResp = client.newCall(hydReq).execute()
                val hydRespBody = hydResp.use { it.body?.string() ?: "" }
                Log.d("DrakorKita", "Step 3 response (${hydResp.code}): ${hydRespBody.take(500)}")

                if (hydResp.code == 200 && hydRespBody.isNotEmpty()) {
                    try {
                        val hdJson = org.json.JSONObject(hydRespBody)
                        val videoUrl = hdJson.optString("url", "")
                            .ifEmpty { hdJson.optString("file", "") }
                            .ifEmpty { hdJson.optString("video_url", "") }
                            .ifEmpty { hdJson.optString("link", "") }
                        Log.d("DrakorKita", "hydrax videoUrl: $videoUrl")
                        if (videoUrl.startsWith("http") && (videoUrl.contains(".mp4") || videoUrl.contains(".m3u8"))) {
                            return@withContext videoUrl
                        }
                    } catch (e: Exception) {
                        Log.e("DrakorKita", "Step 3 parse error: ${e.message}")
                    }

                    val mp4Match = Regex("https?://[^\\s\"']+\\.mp4[^\\s\"']*").find(hydRespBody)
                    if (mp4Match != null) {
                        return@withContext mp4Match.value
                    }
                }

                Log.d("DrakorKita", "API chain exhausted, no video URL found")
                return@withContext ""
            }

            server.url
        } catch (e: Exception) {
            Log.e("DrakorKita", "Error resolving server: ${server.name}", e)
            ""
        }
    }

    private suspend fun resolveAbyssUrl(abyssUrl: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("DrakorKita", "Resolving Abyss: $abyssUrl")
            val request = Request.Builder().url(abyssUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .build()
            val response = client.newCall(request).execute()
            val html = response.use { it.body?.string() ?: "" }

            val atobMatch = Regex("""atob\(["']([^"']+)["']\)""").find(html)
            if (atobMatch != null) {
                val encoded = atobMatch.groupValues[1]
                val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
                Log.d("DrakorKita", "Decoded Abyss payload: ${decoded.take(500)}")

                val domainMatch = Regex(""""domain"\s*:\s*"([^"]+)"""").find(decoded)
                val idMatch = Regex(""""id"\s*:\s*"([^"]+)"""").find(decoded)
                if (domainMatch != null && idMatch != null) {
                    val directUrl = "https://${domainMatch.groupValues[1]}/${idMatch.groupValues[1]}"
                    Log.d("DrakorKita", "Abyss resolved: $directUrl")
                    return@withContext directUrl
                }
            }

            val patterns = listOf(
                Regex("""https?://[^\s"']+\.mp4[^\s"']*"""),
                Regex("""https?://[^\s"']+\.m3u8[^\s"']*"""),
                Regex("""https?://[^\s"']+googlevideo\.com[^\s"']*""")
            )
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    Log.d("DrakorKita", "Abyss fallback match: ${match.value}")
                    return@withContext match.value.trim()
                }
            }

            abyssUrl
        } catch (e: Exception) {
            Log.e("DrakorKita", "Abyss resolution failed: ${e.message}")
            abyssUrl
        }
    }

    private suspend fun resolveDlFileMob(downloadId: String): String {
        return try {
            val url = "https://api.nonton.bid/c_api/dlfilemob.php?id=$downloadId&is_mob=1"
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .build()
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string() ?: "" }
            if (response.code != 200 || body.isEmpty()) return ""
            val json = org.json.JSONObject(body)
            val link = json.optString("link", "")
            Log.d("DrakorKita", "dlfilemob $downloadId -> ${link.take(90)}")
            link
        } catch (e: Exception) {
            Log.e("DrakorKita", "dlfilemob failed: ${e.message}")
            ""
        }
    }

    private suspend fun resolveDownloadServers(
        episodeUrl: String,
        movieId: String,
        epNum: String,
        cat: String,
        isMovie: Boolean
    ): List<VideoServer> {
        try {
            val mediaType = if (isMovie) "movie" else "tv"
            val dlUrl = "https://api.nonton.bid/c_api/ajax_dl_all.php?media_type=$mediaType&id=$movieId&tag=$cat"
            Log.d("DrakorKita", "Download pipeline: $dlUrl")
            val request = Request.Builder().url(dlUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .addHeader("Referer", "https://drakor.kita.mobi/")
                .build()
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string() ?: "" }
            if (response.code != 200 || body.isEmpty()) return emptyList()

            val doc = Jsoup.parseBodyFragment(body)
            val targetNum = epNum.trim().toIntOrNull()
            val cardCount = doc.select("div.card").size
            val servers = mutableListOf<VideoServer>()
            var matchedAny = false

            doc.select("div.card").forEach { card ->
                val header = card.select(".card-header").text().trim()
                val headerNum = Regex("Episode\\s*([\\d.]+)", RegexOption.IGNORE_CASE)
                    .find(header)?.groupValues?.get(1)?.trim()
                val cardNum = headerNum?.toIntOrNull()

                val isTarget = when {
                    isMovie -> true
                    cardNum != null && targetNum != null -> cardNum == targetNum
                    cardNum == null && cardCount == 1 -> true
                    else -> false
                }
                if (!isTarget) return@forEach

                card.select("a[href^='/download/']").forEach { a ->
                    val downloadId = a.attr("href").substringAfterLast("/")
                    if (downloadId.isEmpty()) return@forEach
                    val label = a.text().trim()
                    val quality = Regex("(480p|720p|1080p)").find(label)?.groupValues?.get(1) ?: "HD"
                    val videoUrl = resolveDlFileMob(downloadId)
                    matchedAny = true
                    servers.add(VideoServer(
                        name = "DrakorKita $quality",
                        url = episodeUrl,
                        videoUrl = videoUrl,
                        dataPost = downloadId,
                        dataNume = quality,
                        dataType = "dl"
                    ))
                }
            }

            if (matchedAny) {
                Log.d("DrakorKita", "Download pipeline: ${servers.size} servers for ep=$epNum")
                val qRank = mapOf("480p" to 0, "720p" to 1, "1080p" to 2, "HD" to 3)
                servers.sortBy { s -> qRank[s.dataNume] ?: 4 }
            }
            return servers
        } catch (e: Exception) {
            Log.e("DrakorKita", "Download pipeline failed: ${e.message}")
            return emptyList()
        }
    }

    companion object {
        fun isDrakorKitaUrl(url: String): Boolean {
            return url.contains("drakor.kita.mobi") || url.contains("drakor.kita") || url.contains("nicewap.sbs")
        }
    }

    override suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(episodeUrl)
            val prevEl = doc.select(".epnav .prev a, .episodelist .prev a, a.prev, a[rel='prev']").firstOrNull()
            val nextEl = doc.select(".epnav .next a, .episodelist .next a, a.next, a[rel='next']").firstOrNull()
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
            Log.e("DrakorKita", "Error in getEpisodeNavigation", e)
            EpisodeNavigation()
        }
    }

    private fun parseAnimeGrid(doc: Document): List<Anime> {
        val animeList = mutableListOf<Anime>()

        doc.select(".bungkus").forEach { bungkus ->
            try {
                val card = bungkus.parent()?.parent() ?: return@forEach
                val link = card.select("a[href*='detail/']").firstOrNull() ?: return@forEach
                val href = link.attr("href")
                if (href.isEmpty()) return@forEach

                val title = bungkus.select(".titit").text().ifEmpty { link.attr("title") }
                val posterImg = bungkus.select("img.poster").firstOrNull()
                val imageUrl = posterImg?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""
                val epText = bungkus.select(".type").text().ifEmpty { bungkus.select(".qua").text() }
                val quality = bungkus.select(".qua").text()
                val rating = bungkus.select(".rat").text().replace(Regex("[^\\d.]"), "").trim()

                if (title.isNotEmpty()) {
                    animeList.add(Anime(
                        title = title,
                        url = normalizeUrl(href),
                        imageUrl = imageUrl,
                        episode = epText,
                        status = quality,
                        score = rating
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return animeList
    }

    private data class PageTokens(
        val c: String = "", val t: String = "", val ver: String = "",
        val movieId: String = "", val cat: String = "", val tag: String = ""
    )

    private fun decodePageTokens(html: String): PageTokens {
        try {
            val encodedTokenRegex = Regex("(\\w+)='([A-Za-z0-9+/=]{15,}\\.([A-Za-z0-9+/=]+\\.){5,}[A-Za-z0-9+/=]+)'")
            val match = encodedTokenRegex.find(html) ?: return PageTokens()
            val encoded = match.groupValues[2]
            val segments = encoded.split(".")
            val allDigits = StringBuilder()
            for (segment in segments) {
                try {
                    var padded = segment
                    while (padded.length % 4 != 0) padded += "="
                    val bytes = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
                    val text = String(bytes, Charsets.ISO_8859_1)
                    for (ch in text) {
                        if (ch in '0'..'9') allDigits.append(ch)
                    }
                } catch (_: Exception) {}
            }
            val digitStr = allDigits.toString()
            val decodedChars = mutableListOf<Char>()
            var i = 0
            while (i + 2 < digitStr.length) {
                val code = digitStr.substring(i, i + 3).toIntOrNull() ?: break
                if (code in 0..65535) decodedChars.add(code.toChar())
                i += 3
            }
            val decodedScript = decodedChars.joinToString("")
            android.util.Log.e("DEBUG_DRAKOR", "Decoded script len: ${decodedScript.length}, preview: ${decodedScript.take(200)}")

            val cMatch = Regex("var\\s+c\\s*=\\s*'([^'']*)'").find(decodedScript)
            val tMatch = Regex("var\\s+t\\s*=\\s*'([^'']*)'").find(decodedScript)
            val verMatch = Regex("var\\s+ver\\s*=\\s*'([^'']*)'").find(decodedScript)
            val initMatch = Regex("initEpisodeList\\('([^']*)'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'\\)").find(decodedScript)
            val movieIdMatch = Regex("var\\s+movie_id\\s*=\\s*'([^'']*)'").find(decodedScript)

            return PageTokens(
                c = cMatch?.groupValues?.get(1) ?: "",
                t = tMatch?.groupValues?.get(1) ?: "",
                ver = verMatch?.groupValues?.get(1) ?: "",
                movieId = movieIdMatch?.groupValues?.get(1) ?: initMatch?.groupValues?.get(1) ?: "",
                cat = initMatch?.groupValues?.get(2) ?: "",
                tag = initMatch?.groupValues?.get(3) ?: ""
            )
        } catch (e: Exception) {
            android.util.Log.e("DEBUG_DRAKOR", "decodePageTokens error: ${e.message}", e)
            return PageTokens()
        }
    }

    private val oldDomains = listOf(
        "xdrakor33.nicewap.sbs", "xdrakor33.nicewap.win", "xdrakor33.nicewap.xyz",
        "drakorita.com", "drakorita.net", "drakorkita.cyou", "drakorkita.cfd"
    )

    private fun normalizeUrl(url: String): String {
        val result = when {
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
        return try {
            val baseHost = java.net.URL(baseUrl).host
            val resultUrl = java.net.URL(result)
            if (resultUrl.host != baseHost && oldDomains.any { resultUrl.host?.contains(it) == true || it == resultUrl.host }) {
                result.replace(resultUrl.host!!, baseHost)
            } else {
                result
            }
        } catch (_: Exception) {
            result
        }
    }
}
