package com.weebflix.app.data.config

import android.content.Context
import android.content.SharedPreferences

object ProviderConfig {

    private const val PREF_NAME = "weebflix_provider"
    private const val KEY_BASE_URL = "base_url"
    private const val DEFAULT_BASE_URL = "https://v2.samehadaku.how"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, value.trimEnd('/')).apply()
        }

    fun reset() {
        prefs.edit().remove(KEY_BASE_URL).apply()
    }
}
