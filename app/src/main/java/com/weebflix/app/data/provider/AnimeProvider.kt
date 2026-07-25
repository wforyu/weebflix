package com.weebflix.app.data.provider

import com.weebflix.app.data.model.Anime
import com.weebflix.app.data.model.AnimeDetail
import com.weebflix.app.data.model.Episode
import com.weebflix.app.data.model.EpisodeNavigation
import com.weebflix.app.data.model.VideoServer

interface AnimeProvider {

    val id: String
    val name: String
    val defaultBaseUrl: String

    var baseUrl: String

    suspend fun getLatestEpisodes(page: Int = 1): List<Episode>

    suspend fun getOngoingAnime(page: Int = 1): List<Anime>

    suspend fun getPopularAnime(page: Int = 1): List<Anime>

    suspend fun searchAnime(query: String): List<Anime>

    suspend fun getAnimeDetail(url: String): AnimeDetail

    suspend fun getEpisodeServers(episodeUrl: String): List<VideoServer>

    suspend fun resolveServerVideoUrl(server: VideoServer, episodeUrl: String): String

    suspend fun getEpisodeNavigation(episodeUrl: String): EpisodeNavigation
}
