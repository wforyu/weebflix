package com.weebflix.app.data.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GitHubDataFetcher {

    private const val TAG = "GitHubDataFetcher"
    private const val RAW_BASE = "https://raw.githubusercontent.com/wforyu/weebflix/master/data"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchHomeData(providerId: String): ProviderDataCache.CachedHomeData? = withContext(Dispatchers.IO) {
        try {
            val url = "$RAW_BASE/${providerId}_home.json"
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "WeebFlix/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.d(TAG, "GitHub fetch failed for $providerId: ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val data = ProviderDataCache.CachedHomeData(
                hero = jsonToAnimeList(json.optJSONArray("hero")),
                latestEpisodes = jsonToAnimeList(json.optJSONArray("latest")),
                category1 = jsonToAnimeList(json.optJSONArray("cat1")),
                category2 = jsonToAnimeList(json.optJSONArray("cat2")),
                category3 = jsonToAnimeList(json.optJSONArray("cat3")),
                category4 = jsonToAnimeList(json.optJSONArray("cat4")),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
            Log.d(TAG, "GitHub data fetched for $providerId: hero=${data.hero.size}, latest=${data.latestEpisodes.size}")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching GitHub data for $providerId", e)
            null
        }
    }

    private fun jsonToAnime(json: JSONObject): Anime {
        return Anime(
            title = json.optString("title", ""),
            url = json.optString("url", ""),
            imageUrl = json.optString("imageUrl", ""),
            episode = json.optString("episode", ""),
            type = json.optString("type", ""),
            status = json.optString("status", ""),
            score = json.optString("score", ""),
            studio = json.optString("studio", ""),
            season = json.optString("season", ""),
            synopsis = json.optString("synopsis", ""),
            totalEpisodes = json.optString("totalEpisodes", ""),
            genres = try {
                json.getJSONArray("genres").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (_: Exception) { emptyList() },
            latestUpdate = json.optString("latestUpdate", "")
        )
    }

    private fun jsonToAnimeList(arr: JSONArray?): List<Anime> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull {
            try { jsonToAnime(arr.getJSONObject(it)) } catch (_: Exception) { null }
        }
    }
}
