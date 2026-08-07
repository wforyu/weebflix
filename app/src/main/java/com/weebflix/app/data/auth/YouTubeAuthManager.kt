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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
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
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREF,
            masterKey,
            PrefKeyEncryptionScheme.AES256_SIV,
            PrefValueEncryptionScheme.AES256_GCM
        )
        migrateLegacyTokens(appContext)
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

    fun logout() {
        prefs.edit().clear().apply()
        YouTubeResolver.clearMemo()
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
