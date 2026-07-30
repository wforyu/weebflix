package com.weebflix.app.data.config

import android.content.Context
import android.content.SharedPreferences

object ProviderConfig {

    private const val PREF_NAME = "weebflix_provider"
    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val DEFAULT_ACTIVE_PROVIDER = "samehadaku"

    private const val KEY_BASE_URL_SAMEHADAKU = "base_url_samehadaku"
    private const val DEFAULT_BASE_URL_SAMEHADAKU = "https://v2.samehadaku.how"

    private const val KEY_BASE_URL_DRAKORKITA = "base_url_drakorkita"
    private const val DEFAULT_BASE_URL_DRAKORKITA = "https://drakor.kita.mobi"

    private const val KEY_BASE_URL_OPPADRAMA = "base_url_oppadrama"
    private const val DEFAULT_BASE_URL_OPPADRAMA = "http://45.11.57.192"

    private const val KEY_BASE_URL_ANICHIN = "base_url_anichin"
    private const val DEFAULT_BASE_URL_ANICHIN = "https://anichin.cafe"

    private const val KEY_BASE_URL_LEGACY = "base_url"

    private lateinit var prefs: SharedPreferences

    private val oldDrakorDomains = listOf(
        "xdrakor33.nicewap.sbs", "xdrakor33.nicewap.win", "xdrakor33.nicewap.xyz",
        "drakorita.com", "drakorita.net", "drakorkita.cyou", "drakorkita.cfd"
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val legacyUrl = prefs.getString(KEY_BASE_URL_LEGACY, null)
        if (legacyUrl != null && prefs.getString(KEY_BASE_URL_SAMEHADAKU, null) == null) {
            prefs.edit().putString(KEY_BASE_URL_SAMEHADAKU, legacyUrl).apply()
            prefs.edit().remove(KEY_BASE_URL_LEGACY).apply()
        }

        val storedDrakorUrl = prefs.getString(KEY_BASE_URL_DRAKORKITA, null)
        if (storedDrakorUrl != null && oldDrakorDomains.any { storedDrakorUrl.contains(it) }) {
            prefs.edit().remove(KEY_BASE_URL_DRAKORKITA).apply()
        }
    }

    var activeProviderId: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, DEFAULT_ACTIVE_PROVIDER) ?: DEFAULT_ACTIVE_PROVIDER
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply()
        }

    fun getBaseUrl(providerId: String): String {
        return when (providerId) {
            "samehadaku" -> prefs.getString(KEY_BASE_URL_SAMEHADAKU, DEFAULT_BASE_URL_SAMEHADAKU) ?: DEFAULT_BASE_URL_SAMEHADAKU
            "drakorkita" -> prefs.getString(KEY_BASE_URL_DRAKORKITA, DEFAULT_BASE_URL_DRAKORKITA) ?: DEFAULT_BASE_URL_DRAKORKITA
            "oppadrama" -> prefs.getString(KEY_BASE_URL_OPPADRAMA, DEFAULT_BASE_URL_OPPADRAMA) ?: DEFAULT_BASE_URL_OPPADRAMA
            "anichin" -> prefs.getString(KEY_BASE_URL_ANICHIN, DEFAULT_BASE_URL_ANICHIN) ?: DEFAULT_BASE_URL_ANICHIN
            else -> prefs.getString(KEY_BASE_URL_ANICHIN, DEFAULT_BASE_URL_ANICHIN) ?: DEFAULT_BASE_URL_ANICHIN
        }
    }

    fun setBaseUrl(providerId: String, url: String) {
        val key = when (providerId) {
            "samehadaku" -> KEY_BASE_URL_SAMEHADAKU
            "drakorkita" -> KEY_BASE_URL_DRAKORKITA
            "oppadrama" -> KEY_BASE_URL_OPPADRAMA
            "anichin" -> KEY_BASE_URL_ANICHIN
            else -> return
        }
        prefs.edit().putString(key, url.trimEnd('/')).apply()
    }

    fun resetBaseUrl(providerId: String) {
        val key = when (providerId) {
            "samehadaku" -> KEY_BASE_URL_SAMEHADAKU
            "drakorkita" -> KEY_BASE_URL_DRAKORKITA
            "oppadrama" -> KEY_BASE_URL_OPPADRAMA
            "anichin" -> KEY_BASE_URL_ANICHIN
            else -> return
        }
        prefs.edit().remove(key).apply()
    }

    fun getDefaultBaseUrl(providerId: String): String {
        return when (providerId) {
            "samehadaku" -> DEFAULT_BASE_URL_SAMEHADAKU
            "drakorkita" -> DEFAULT_BASE_URL_DRAKORKITA
            "oppadrama" -> DEFAULT_BASE_URL_OPPADRAMA
            "anichin" -> DEFAULT_BASE_URL_ANICHIN
            else -> DEFAULT_BASE_URL_ANICHIN
        }
    }

    var baseUrl: String
        get() = getBaseUrl(activeProviderId)
        set(value) = setBaseUrl(activeProviderId, value)

    fun reset() {
        resetBaseUrl(activeProviderId)
    }
}
