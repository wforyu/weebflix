package com.weebflix.app.data.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryEntry(
    val episodeUrl: String,
    val animeTitle: String,
    val episodeTitle: String = "",
    val episodeNumber: String = "",
    val imageUrl: String = "",
    val animeUrl: String = "",
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((progressMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0

    val isFinished: Boolean
        get() = durationMs > 0 && progressMs >= durationMs - 5000

    fun toJson(): JSONObject = JSONObject().apply {
        put("episodeUrl", episodeUrl)
        put("animeTitle", animeTitle)
        put("episodeTitle", episodeTitle)
        put("episodeNumber", episodeNumber)
        put("imageUrl", imageUrl)
        put("animeUrl", animeUrl)
        put("progressMs", progressMs)
        put("durationMs", durationMs)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject) = WatchHistoryEntry(
            episodeUrl = json.optString("episodeUrl", ""),
            animeTitle = json.optString("animeTitle", ""),
            episodeTitle = json.optString("episodeTitle", ""),
            episodeNumber = json.optString("episodeNumber", ""),
            imageUrl = json.optString("imageUrl", ""),
            animeUrl = json.optString("animeUrl", ""),
            progressMs = json.optLong("progressMs", 0L),
            durationMs = json.optLong("durationMs", 0L),
            timestamp = json.optLong("timestamp", 0L)
        )
    }
}

object WatchHistoryManager {

    private const val PREF_NAME = "weebflix_watch_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 20

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveProgress(
        context: Context,
        episodeUrl: String,
        animeTitle: String,
        episodeTitle: String,
        episodeNumber: String,
        imageUrl: String,
        animeUrl: String,
        progressMs: Long,
        durationMs: Long
    ) {
        if (episodeUrl.isEmpty() || durationMs < 10000) return
        if (progressMs < 3000) return

        val entries = getAll(context).toMutableList()
        entries.removeAll { it.episodeUrl == episodeUrl }

        entries.add(
            WatchHistoryEntry(
                episodeUrl = episodeUrl,
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                episodeNumber = episodeNumber,
                imageUrl = imageUrl,
                animeUrl = animeUrl,
                progressMs = progressMs,
                durationMs = durationMs,
                timestamp = System.currentTimeMillis()
            )
        )

        entries.removeAll { it.isFinished }

        val sorted = entries.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
        saveAll(context, sorted)
    }

    fun getAll(context: Context): List<WatchHistoryEntry> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull {
                try { WatchHistoryEntry.fromJson(arr.getJSONObject(it)) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun remove(context: Context, episodeUrl: String) {
        val entries = getAll(context).toMutableList()
        entries.removeAll { it.episodeUrl == episodeUrl }
        saveAll(context, entries)
    }

    fun clear(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }

    private fun saveAll(context: Context, entries: List<WatchHistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
