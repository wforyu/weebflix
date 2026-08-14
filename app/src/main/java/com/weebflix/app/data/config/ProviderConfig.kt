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

    private const val KEY_BASE_URL_YOUTUBE = "base_url_youtube"
    private const val DEFAULT_BASE_URL_YOUTUBE = "https://www.youtube.com"

    private const val KEY_BASE_URL_OTAKUDESU = "base_url_otakudesu"
    private const val DEFAULT_BASE_URL_OTAKUDESU = "https://otakudesu.blog"

    private const val KEY_BASE_URL_LEGACY = "base_url"

    private const val KEY_YT_OAUTH_CLIENT_ID = "yt_oauth_client_id"
    private const val KEY_YT_OAUTH_CLIENT_SECRET = "yt_oauth_client_secret"
    private const val KEY_YT_OAUTH_REDIRECT = "yt_oauth_redirect"
    private const val DEFAULT_YT_OAUTH_REDIRECT = "http://localhost:8080/callback"

    private const val KEY_YT_DEFAULT_RESOLUTION = "yt_default_resolution"

    // Built-in OAuth credentials (Google Cloud "Web application" client) so users can log
    // in without touching Settings. NOTE: a client secret embedded in an APK can be
    // extracted — acceptable for a private/friends build, NOT for public distribution
    // (rotate + use a public "Android" OAuth client instead if the app goes public).
    private const val BUILTIN_YT_OAUTH_CLIENT_ID =
        "914593639860-r86nbk1r34f2eb46rl0c4l5rmqo7ijch.apps.googleusercontent.com"
    private const val BUILTIN_YT_OAUTH_CLIENT_SECRET = "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"

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
            "youtube" -> prefs.getString(KEY_BASE_URL_YOUTUBE, DEFAULT_BASE_URL_YOUTUBE) ?: DEFAULT_BASE_URL_YOUTUBE
            "otakudesu" -> prefs.getString(KEY_BASE_URL_OTAKUDESU, DEFAULT_BASE_URL_OTAKUDESU) ?: DEFAULT_BASE_URL_OTAKUDESU
            else -> prefs.getString(KEY_BASE_URL_ANICHIN, DEFAULT_BASE_URL_ANICHIN) ?: DEFAULT_BASE_URL_ANICHIN
        }
    }

    fun setBaseUrl(providerId: String, url: String) {
        val key = when (providerId) {
            "samehadaku" -> KEY_BASE_URL_SAMEHADAKU
            "drakorkita" -> KEY_BASE_URL_DRAKORKITA
            "oppadrama" -> KEY_BASE_URL_OPPADRAMA
            "anichin" -> KEY_BASE_URL_ANICHIN
            "youtube" -> KEY_BASE_URL_YOUTUBE
            "otakudesu" -> KEY_BASE_URL_OTAKUDESU
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
            "youtube" -> KEY_BASE_URL_YOUTUBE
            "otakudesu" -> KEY_BASE_URL_OTAKUDESU
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
            "youtube" -> DEFAULT_BASE_URL_YOUTUBE
            "otakudesu" -> DEFAULT_BASE_URL_OTAKUDESU
            else -> DEFAULT_BASE_URL_ANICHIN
        }
    }

    var baseUrl: String
        get() = getBaseUrl(activeProviderId)
        set(value) = setBaseUrl(activeProviderId, value)

    // ---- YouTube OAuth credentials (set from Settings) ----

    fun getYtOAuthClientId(): String =
        prefs.getString(KEY_YT_OAUTH_CLIENT_ID, BUILTIN_YT_OAUTH_CLIENT_ID) ?: BUILTIN_YT_OAUTH_CLIENT_ID

    fun setYtOAuthClientId(value: String) {
        val v = value.trim()
        if (v.isEmpty()) prefs.edit().remove(KEY_YT_OAUTH_CLIENT_ID).apply()
        else prefs.edit().putString(KEY_YT_OAUTH_CLIENT_ID, v).apply()
    }

    fun getYtOAuthClientSecret(): String =
        prefs.getString(KEY_YT_OAUTH_CLIENT_SECRET, BUILTIN_YT_OAUTH_CLIENT_SECRET) ?: BUILTIN_YT_OAUTH_CLIENT_SECRET

    fun setYtOAuthClientSecret(value: String) {
        val v = value.trim()
        if (v.isEmpty()) prefs.edit().remove(KEY_YT_OAUTH_CLIENT_SECRET).apply()
        else prefs.edit().putString(KEY_YT_OAUTH_CLIENT_SECRET, v).apply()
    }

    fun getYtOAuthRedirectUri(): String =
        prefs.getString(KEY_YT_OAUTH_REDIRECT, DEFAULT_YT_OAUTH_REDIRECT) ?: DEFAULT_YT_OAUTH_REDIRECT

    fun setYtOAuthRedirectUri(value: String) {
        prefs.edit().putString(KEY_YT_OAUTH_REDIRECT, value.trim().ifEmpty { DEFAULT_YT_OAUTH_REDIRECT }).apply()
    }

    // ---- YouTube default max resolution (0 = Auto / no limit) ----

    fun getYtDefaultResolution(): Int = prefs.getInt(KEY_YT_DEFAULT_RESOLUTION, 0)

    fun setYtDefaultResolution(height: Int) {
        prefs.edit().putInt(KEY_YT_DEFAULT_RESOLUTION, height.coerceIn(0, 2160)).apply()
    }

    fun reset() {
        resetBaseUrl(activeProviderId)
    }
}
