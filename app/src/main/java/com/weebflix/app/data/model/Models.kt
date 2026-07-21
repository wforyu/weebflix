package com.weebflix.app.data.model

data class Anime(
    val title: String = "",
    val url: String = "",
    val imageUrl: String = "",
    val episode: String = "",
    val type: String = "",
    val status: String = "",
    val score: String = "",
    val studio: String = "",
    val season: String = "",
    val synopsis: String = "",
    val totalEpisodes: String = "",
    val genres: List<String> = emptyList(),
    val latestUpdate: String = ""
)

data class Episode(
    val title: String = "",
    val url: String = "",
    val imageUrl: String = "",
    val episodeNumber: String = "",
    val uploadDate: String = "",
    val postedBy: String = ""
)

data class VideoServer(
    val name: String,
    val url: String
)

data class AnimeDetail(
    val anime: Anime,
    val episodes: List<Episode> = emptyList()
)

data class EpisodeNavigation(
    val prevEpisodeUrl: String = "",
    val prevEpisodeTitle: String = "",
    val nextEpisodeUrl: String = "",
    val nextEpisodeTitle: String = ""
)
