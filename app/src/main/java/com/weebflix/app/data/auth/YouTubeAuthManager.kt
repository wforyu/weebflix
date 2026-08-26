package com.weebflix.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.scraper.YouTubeResolver
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Google OAuth (Authorization Code + PKCE) for the YouTube provider.
 *
 * Login flow: [buildAuthUrl] -> user signs in on accounts.google.com in the
 * system browser (Google blocks embedded WebViews) -> the browser redirects to
 * [redirectUri] carrying `?code=..&state=..`, which a loopback HTTP server
 * (LoopbackOAuthServer in YouTubeLoginActivity) captures -> [exchangeCode] swaps
 * it for an access + refresh token.
 *
 * The access token is injected as `Authorization: Bearer` on `youtubei/v1/player`
 * requests (see YouTubeResolver.fetchPlayer). A logged-in player request does NOT
 * hit the `LOGIN_REQUIRED` bot-gate, so Content-ID / embedding-disabled videos
 * (e.g. Ihtxx2s6RUE "LAPOR PAK!") become playable.
 *
 * Credentials come from Settings (ProviderConfig): OAuth Client ID (+ optional
 * Client Secret and Redirect URI override). PKCE is always used, so a Web-app or
 * Android OAuth client works with or without a secret.
 */
object YouTubeAuthManager {

    private const val TAG = "YouTubeAuth"
    private const val PREF = "weebflix_yt_auth_enc"
    private const val PREF_LEGACY = "weebflix_yt_auth"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_EXPIRES_AT = "expires_at_ms"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "user_name"
    private const val KEY_PICTURE = "user_picture"
    private const val KEY_YT_COOKIES = "yt_session_cookies"
    private const val KEY_YT_COOKIES_AT = "yt_session_cookies_at"

    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val INNERTUBE_WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    // `youtube` penuh untuk Data API v3; `openid` + `email` + `profile` agar
    // `oauth2/v2/userinfo` mengembalikan nama + foto akun (dipakai di header home).
    private const val SCOPE =
        "https://www.googleapis.com/auth/youtube openid email profile"

    private val rng = SecureRandom()

    private lateinit var prefs: SharedPreferences

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // PKCE state for the in-flight login flow (YouTubeLoginActivity).
    private var pendingVerifier: String? = null
    private var pendingState: String? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = buildEncryptedPrefs(appContext)
        migrateLegacyTokens(appContext)
    }

    /**
     * Opens the EncryptedSharedPreferences store. The keyset on disk is tied to the app's
     * Android Keystore master key — if the app is reinstalled with a different signing key
     * (debug vs release, or a rebuild) that key no longer matches, so create() throws
     * (e.g. `javax.crypto.AEADBadTagException`). Never let that crash the Application:
     * wipe the corrupt store and rebuild it (the user just has to re-login), falling back
     * to a plain file as a last resort.
     */
    private fun buildEncryptedPrefs(appContext: Context): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                appContext,
                PREF,
                masterKey,
                PrefKeyEncryptionScheme.AES256_SIV,
                PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e1: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences init failed (${e1.javaClass.simpleName}: ${e1.message}); wiping corrupt store")
            appContext.deleteSharedPreferences(PREF)
            return try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREF,
                    masterKey,
                    PrefKeyEncryptionScheme.AES256_SIV,
                    PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                Log.w(TAG, "Rebuild failed too (${e2.javaClass.simpleName}: ${e2.message}); falling back to plain SharedPreferences")
                appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            }
        }
    }

    /** One-time copy of tokens stored by the old plain SharedPreferences (pre-EncryptedSharedPreferences),
     *  then the plain file is wiped. */
    private fun migrateLegacyTokens(context: Context) {
        val legacy = context.getSharedPreferences(PREF_LEGACY, Context.MODE_PRIVATE)
        if (!legacy.contains(KEY_REFRESH) && !legacy.contains(KEY_ACCESS) && !legacy.contains(KEY_EMAIL)) return
        val refresh = legacy.getString(KEY_REFRESH, "") ?: ""
        if (refresh.isNotEmpty() && (prefs.getString(KEY_REFRESH, "") ?: "").isEmpty()) {
            prefs.edit()
                .putString(KEY_REFRESH, refresh)
                .putString(KEY_ACCESS, legacy.getString(KEY_ACCESS, "") ?: "")
                .putLong(KEY_EXPIRES_AT, legacy.getLong(KEY_EXPIRES_AT, 0L))
                .putString(KEY_EMAIL, legacy.getString(KEY_EMAIL, "") ?: "")
                .apply()
        }
        legacy.edit().clear().apply()
    }

    val clientId: String get() = ProviderConfig.getYtOAuthClientId()
    val clientSecret: String get() = ProviderConfig.getYtOAuthClientSecret()
    val redirectUri: String get() = ProviderConfig.getYtOAuthRedirectUri()

    fun isConfigured(): Boolean = clientId.isNotBlank()

    fun isLoggedIn(): Boolean = getRefreshToken().isNotEmpty()

    fun email(): String = prefs.getString(KEY_EMAIL, "") ?: ""

    /** Nama tampilan akun: full name dari userinfo, fallback ke bagian sebelum `@` email. */
    fun displayName(): String =
        (prefs.getString(KEY_NAME, "") ?: "").takeIf { it.isNotBlank() }
            ?: email().substringBefore('@').takeIf { it.isNotBlank() }
            ?: "Akun"

    /** Foto profil Google (URL) — diisi oleh [fetchUserInfo]. */
    fun picture(): String = prefs.getString(KEY_PICTURE, "") ?: ""

    private fun getRefreshToken() = prefs.getString(KEY_REFRESH, "") ?: ""

    /** Builds the Google consent URL, storing the PKCE verifier + state for the flow. */
    fun buildAuthUrl(): String {
        val verifier = base64Url(ByteArray(32).also { rng.nextBytes(it) })
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        val state = base64Url(ByteArray(16).also { rng.nextBytes(it) })
        pendingVerifier = verifier
        pendingState = state
        return AUTH_URL + "?" +
            "client_id=" + enc(clientId) +
            "&redirect_uri=" + enc(redirectUri) +
            "&response_type=code" +
            "&scope=" + enc(SCOPE) +
            "&access_type=offline" +
            "&prompt=consent" +
            "&code_challenge=" + enc(challenge) +
            "&code_challenge_method=S256" +
            "&state=" + enc(state)
    }

    /**
     * Swaps an authorization code (from the redirect callback) for tokens.
     * Returns a user-facing error message, or null on success.
     */
    fun exchangeCode(code: String, state: String): String? {
        if (pendingState == null || pendingState != state) return "state tidak cocok (flow kedaluwarsa?)"
        val verifier = pendingVerifier ?: return "PKCE verifier hilang, ulangi login"
        pendingVerifier = null
        pendingState = null
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .apply { if (clientSecret.isNotBlank()) add("client_secret", clientSecret) }
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .add("code_verifier", verifier)
            .build()
        return tokenRequest(form)
    }

    /**
     * Returns a valid access token, refreshing first if expired. Null when not
     * logged in or refresh failed.
     */
    fun getAccessToken(): String? {
        val at = prefs.getString(KEY_ACCESS, "") ?: ""
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (at.isNotEmpty() && expiresAt > System.currentTimeMillis() + 60_000) return at
        if (getRefreshToken().isEmpty()) return null
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", getRefreshToken())
            .add("client_id", clientId)
            .apply { if (clientSecret.isNotBlank()) add("client_secret", clientSecret) }
            .build()
        val err = tokenRequest(form)
        if (err != null) {
            Log.w(TAG, "token refresh failed: $err")
            return null
        }
        return prefs.getString(KEY_ACCESS, "")
    }

    private fun tokenRequest(form: FormBody): String? {
        return try {
            val request = Request.Builder()
                .url(TOKEN_URL)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(form)
                .build()
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.w(TAG, "token HTTP ${resp.code}: ${bodyStr.take(200)}")
                    return "HTTP ${resp.code}"
                }
                val json = JSONObject(bodyStr)
                val at = json.optString("access_token", "")
                if (at.isEmpty()) return "tidak ada access_token"
                val expiresIn = json.optLong("expires_in", 3600)
                prefs.edit()
                    .putString(KEY_ACCESS, at)
                    .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
                    .apply()
                json.optString("refresh_token", "").takeIf { it.isNotEmpty() }
                    ?.let { prefs.edit().putString(KEY_REFRESH, it).apply() }
                fetchEmail(at)
                fetchUserInfo()
                fetchYouTubeCookies()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "token request error: ${e.message}")
            "error: ${e.message}"
        }
    }

    private fun fetchEmail(accessToken: String) {
        try {
            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/tokeninfo?access_token=$accessToken")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val email = JSONObject(resp.body?.string() ?: "").optString("email", "")
                    if (email.isNotEmpty()) {
                        prefs.edit().putString(KEY_EMAIL, email).apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "email fetch failed: ${e.message}")
        }
    }

    /** Fetches the profile (name/email/picture) from the Google userinfo endpoint and caches it.
     *  Requires the `openid email profile` scopes (added to [SCOPE]) — old refresh tokens from a
     *  pre-scope login return HTTP 403 here; the header then just falls back to the email prefix. */
    fun fetchUserInfo(): Boolean {
        val at = getAccessToken() ?: return false
        return try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v2/userinfo?alt=json")
                .addHeader("Authorization", "Bearer $at")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "userinfo HTTP ${resp.code}")
                    return false
                }
                val json = JSONObject(resp.body?.string() ?: "")
                prefs.edit()
                    .putString(KEY_NAME, json.optString("name", ""))
                    .putString(KEY_PICTURE, json.optString("picture", ""))
                    .putString(KEY_EMAIL, json.optString("email", "").ifEmpty { prefs.getString(KEY_EMAIL, "") })
                    .apply()
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "userinfo failed: ${e.message}")
            false
        }
    }

    /** Fetches YouTube session cookies by bootstrapping a web session with the OAuth access token.
     *  YouTube blocks Bearer auth on innertube player, but accepts cookie-based auth.
     *  This method hits YouTube endpoints that set session cookies in response, then stores them
     *  for use by the resolver (Cookie header + SAPISIDHASH instead of Authorization: Bearer). */
    fun fetchYouTubeCookies() {
        val at = getAccessToken() ?: return
        Thread {
            try {
                val cookieMap = mutableMapOf<String, String>()
                val jar = object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        for (c in cookies) cookieMap[c.name] = c.value
                    }
                    override fun loadForRequest(url: HttpUrl) = emptyList<Cookie>()
                }
                val httpClient = client.newBuilder()
                    .cookieJar(jar)
                    .followRedirects(true)
                    .build()
                val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

                // Step 1: Hit YouTube homepage with Bearer to capture Set-Cookie headers
                try {
                    val req1 = Request.Builder()
                        .url("https://www.youtube.com/")
                        .addHeader("Authorization", "Bearer $at")
                        .addHeader("User-Agent", ua)
                        .build()
                    httpClient.newCall(req1).execute().close()
                    Log.d(TAG, "Cookie bootstrap step 1: ${cookieMap.size} cookies captured")
                } catch (e: Exception) {
                    Log.w(TAG, "Cookie bootstrap step 1 failed: ${e.message}")
                }

                // Step 2: Hit Google OAuth endpoint with prompt=none to auto-authorize & set cookies
                val email = email()
                if (email.isNotEmpty()) {
                    try {
                        val oauthUrl = "$AUTH_URL?" +
                            "client_id=${enc(clientId)}" +
                            "&redirect_uri=${enc(redirectUri)}" +
                            "&response_type=token" +
                            "&scope=${enc("https://www.googleapis.com/auth/youtube")}" +
                            "&login_hint=${enc(email)}" +
                            "&prompt=none"
                        val req2 = Request.Builder()
                            .url(oauthUrl)
                            .addHeader("Authorization", "Bearer $at")
                            .addHeader("User-Agent", ua)
                            .build()
                        httpClient.newCall(req2).execute().close()
                        Log.d(TAG, "Cookie bootstrap step 2: ${cookieMap.size} cookies total")
                    } catch (e: Exception) {
                        Log.w(TAG, "Cookie bootstrap step 2 failed: ${e.message}")
                    }
                }

                // Step 3: Hit YouTube again to pick up cross-domain cookies
                try {
                    val req3 = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/browse?key=$INNERTUBE_WEB_KEY&prettyPrint=false")
                        .addHeader("User-Agent", ua)
                        .addHeader("Content-Type", "application/json")
                        .post("""{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260731.00.00"}}}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    httpClient.newCall(req3).execute().close()
                    Log.d(TAG, "Cookie bootstrap step 3: ${cookieMap.size} cookies total")
                } catch (e: Exception) {
                    Log.w(TAG, "Cookie bootstrap step 3 failed: ${e.message}")
                }

                // Filter for auth-relevant cookies
                val authCookieNames = setOf(
                    "SID", "HSID", "SSID", "APISID", "SAPISID",
                    "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PSIDTS", "__Secure-3PSIDTS",
                    "__Secure-1PAPISID", "__Secure-3PAPISID",
                    "LOGIN_INFO", "SIDCC", "__Secure-1PSIDCC", "__Secure-3PSIDCC"
                )
                val authCookies = cookieMap.filter { (name, _) -> name in authCookieNames }
                if (authCookies.isNotEmpty()) {
                    val cookieStr = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    prefs.edit()
                        .putString(KEY_YT_COOKIES, cookieStr)
                        .putLong(KEY_YT_COOKIES_AT, System.currentTimeMillis())
                        .apply()
                    Log.d(TAG, "YouTube cookies saved: ${authCookies.keys}")
                } else {
                    Log.w(TAG, "No auth cookies captured (got ${cookieMap.keys})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchYouTubeCookies failed: ${e.message}")
            }
        }.start()
    }

    /** Returns stored YouTube session cookies, or null if not available / expired (>6h). */
    fun getYouTubeCookies(): String? {
        val cookies = prefs.getString(KEY_YT_COOKIES, "") ?: ""
        if (cookies.isEmpty()) return null
        val savedAt = prefs.getLong(KEY_YT_COOKIES_AT, 0)
        if (System.currentTimeMillis() - savedAt > 6 * 3600 * 1000) {
            prefs.edit().remove(KEY_YT_COOKIES).remove(KEY_YT_COOKIES_AT).apply()
            return null
        }
        return cookies
    }

    /** Clears stored YouTube session cookies (e.g. when they're rejected by server). */
    fun clearYouTubeCookies() {
        prefs.edit().remove(KEY_YT_COOKIES).remove(KEY_YT_COOKIES_AT).apply()
        Log.d(TAG, "YouTube cookies cleared")
    }

    /** Checks if we have a SAPISID cookie (needed for SAPISIDHASH auth header). */
    fun getSapisid(): String? {
        val cookies = getYouTubeCookies() ?: return null
        for (part in cookies.split("; ")) {
            val kv = part.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == "SAPISID") return kv[1]
        }
        return null
    }

    /** Generates SAPISIDHASH header value: "SAPISIDHASH <timestamp>_<sha1(timestamp + " " + SAPISID + " " + origin)>" */
    fun buildSapisidHash(sapisid: String, origin: String = "https://www.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapisid $origin"
        val sha1 = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.US_ASCII))
        val hex = sha1.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    fun logout() {
        prefs.edit().clear().apply()
        YouTubeResolver.clearMemo()
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
