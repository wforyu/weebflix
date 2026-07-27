package com.weebflix.app.data.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object ProviderDataCache {

    private const val TAG = "ProviderDataCache"
    private const val PREFS_NAME = "provider_data_cache"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private val memoryCache = ConcurrentHashMap<String, CachedHomeData>()

    data class CachedHomeData(
        val hero: List<Anime>,
        val latestEpisodes: List<Anime>,
        val category1: List<Anime>,
        val category2: List<Anime>,
        val category3: List<Anime>,
        val category4: List<Anime>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
        fun isFresh(): Boolean = !isExpired()
    }

    fun getCachedData(providerId: String): CachedHomeData? {
        val mem = memoryCache[providerId]
        if (mem != null && mem.isFresh()) {
            Log.d(TAG, "Memory cache hit for $providerId")
            return mem
        }
        return null
    }

    fun cacheData(providerId: String, data: CachedHomeData) {
        memoryCache[providerId] = data
    }

    suspend fun saveToDisk(context: Context, providerId: String, data: CachedHomeData) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject()
            json.put("timestamp", data.timestamp)
            json.put("hero", animeListToJson(data.hero))
            json.put("latest", animeListToJson(data.latestEpisodes))
            json.put("cat1", animeListToJson(data.category1))
            json.put("cat2", animeListToJson(data.category2))
            json.put("cat3", animeListToJson(data.category3))
            json.put("cat4", animeListToJson(data.category4))
            prefs.edit().putString("cache_$providerId", json.toString()).apply()
            memoryCache[providerId] = data
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache for $providerId", e)
        }
    }

    suspend fun loadFromDisk(context: Context, providerId: String): CachedHomeData? = withContext(Dispatchers.IO) {
        try {
            val mem = memoryCache[providerId]
            if (mem != null && mem.isFresh()) return@withContext mem

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("cache_$providerId", null) ?: return@withContext null
            val json = JSONObject(jsonStr)
            val timestamp = json.getLong("timestamp")
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                prefs.edit().remove("cache_$providerId").apply()
                return@withContext null
            }
            val data = CachedHomeData(
                hero = jsonToAnimeList(json.getJSONArray("hero")),
                latestEpisodes = jsonToAnimeList(json.getJSONArray("latest")),
                category1 = jsonToAnimeList(json.getJSONArray("cat1")),
                category2 = jsonToAnimeList(json.getJSONArray("cat2")),
                category3 = jsonToAnimeList(json.getJSONArray("cat3")),
                category4 = jsonToAnimeList(json.getJSONArray("cat4")),
                timestamp = timestamp
            )
            memoryCache[providerId] = data
            Log.d(TAG, "Disk cache hit for $providerId")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache for $providerId", e)
            null
        }
    }

    fun clearCache(context: Context, providerId: String) {
        memoryCache.remove(providerId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("cache_$providerId").apply()
    }

    fun clearAll(context: Context) {
        memoryCache.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun animeToJson(anime: Anime): JSONObject {
        return JSONObject().apply {
            put("title", anime.title)
            put("url", anime.url)
            put("imageUrl", anime.imageUrl)
            put("episode", anime.episode)
            put("type", anime.type)
            put("status", anime.status)
            put("score", anime.score)
            put("studio", anime.studio)
            put("season", anime.season)
            put("synopsis", anime.synopsis)
            put("totalEpisodes", anime.totalEpisodes)
            put("genres", JSONArray(anime.genres))
            put("latestUpdate", anime.latestUpdate)
        }
    }

    private fun animeListToJson(list: List<Anime>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(animeToJson(it)) }
        return arr
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
            genres = try { json.getJSONArray("genres").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } } catch (_: Exception) { emptyList() },
            latestUpdate = json.optString("latestUpdate", "")
        )
    }

    private fun jsonToAnimeList(arr: JSONArray): List<Anime> {
        return (0 until arr.length()).mapNotNull {
            try { jsonToAnime(arr.getJSONObject(it)) } catch (_: Exception) { null }
        }
    }
}
