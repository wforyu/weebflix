package com.weebflix.app.data.scraper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local cache of subscribed YouTube channels, keyed by the logged-in Google account email
 * (so each account keeps its own list even after logout). The Data API v3 ([YouTubeDataApi])
 * refreshes it from the server; the UI reads from here for instant subscribe-state in the
 * player and the home "Langganan" section without an extra API call.
 */
object YouTubeSubscriptionStore {

    private const val PREF = "weebflix_yt_subs"
    private const val KEY_PREFIX = "subs_"

    @Volatile private var prefs: SharedPreferences? = null

    /** Must be called once at app start (WeebFlixApp.onCreate) before any other method. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    private val key: String
        get() = KEY_PREFIX + (com.weebflix.app.data.auth.YouTubeAuthManager.email().ifEmpty { "default" })

    fun replaceAll(channels: List<YouTubeChannel>) {
        val p = prefs ?: return
        val arr = JSONArray()
        channels.forEach { arr.put(it.toJson()) }
        p.edit().putString(key, arr.toString()).apply()
    }

    fun add(ch: YouTubeChannel) {
        if (ch.channelId.isEmpty()) return
        val list = getAll().toMutableList()
        list.removeAll { it.channelId == ch.channelId }
        list.add(ch)
        replaceAll(list)
    }

    fun remove(channelId: String) {
        replaceAll(getAll().filterNot { it.channelId == channelId })
    }

    fun getAll(): List<YouTubeChannel> {
        val p = prefs ?: return emptyList()
        val json = p.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull {
                try { YouTubeChannel.fromJson(arr.getJSONObject(it)) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isSubscribed(channelId: String): Boolean =
        getAll().any { it.channelId == channelId }

    fun subscriptionIdOf(channelId: String): String =
        getAll().firstOrNull { it.channelId == channelId }?.subscriptionId ?: ""
}
