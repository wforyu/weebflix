package com.weebflix.app.data.scraper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Generates YouTube PO Tokens via BotGuard running in a hidden WebView.
 *
 * Flow:
 * 1. Load po_token.html + fetch challenge from youtube.com/api/jnn/v1/Create
 * 2. Descramble challenge, run in WebView BotGuard VM -> get botguardResponse
 * 3. Submit botguardResponse to GenerateIT -> get integrityToken
 * 4. Store webPoSignalOutput + integrityToken in JS scope (functions can't cross bridge)
 * 5. When getTokens() called, JS mints PO tokens and returns only the base64 strings
 */
object PoTokenManager {

    private const val TAG = "PoTokenManager"
    private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val JS_INTERFACE = "PoTokenBridge"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var webView: WebView? = null
    @Volatile private var ready = false
    @Volatile private var initFailed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initLock = CountDownLatch(1)
    private val tokenLock = Any()

    @Volatile private var pendingTokenCallback: ((String?, String?) -> Unit)? = null
    @Volatile private var pendingInitCallback: ((Boolean) -> Unit)? = null

    data class PoTokenResult(
        val playerPot: String = "",
        val streamingPot: String = ""
    )

    fun init(context: Context, timeoutMs: Long = 15000) {
        if (ready) return
        if (initFailed) return
        mainHandler.post { initInternal(context.applicationContext) }
        initLock.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun initInternal(context: Context) {
        try {
            val wv = WebView(context)
            val settings = wv.settings
            settings.javaScriptEnabled = true
            settings.userAgentString = USER_AGENT
            settings.blockNetworkLoads = true

            wv.addJavascriptInterface(Bridge(), JS_INTERFACE)

            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.message().contains("Uncaught")) {
                        Log.e(TAG, "JS syntax error: ${m.message()}")
                        initFailed = true
                        initLock.countDown()
                    }
                    return super.onConsoleMessage(m)
                }
            }

            webView = wv

            val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            val inject = "\n$JS_INTERFACE.onHtmlLoaded()</script>"
            wv.loadDataWithBaseURL(
                "https://www.youtube.com",
                html.replaceFirst("</script>", inject),
                "text/html",
                "utf-8",
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "init failed: ${e.message}")
            initFailed = true
            initLock.countDown()
        }
    }

    /**
     * Generate PO tokens for a given video ID.
     * All BotGuard state (webPoSignalOutput, integrityToken) stays in JS.
     * Only base64-encoded token strings cross the bridge.
     */
    fun getTokens(videoId: String, visitorData: String?, timeoutMs: Long = 10000): PoTokenResult? {
        if (!ready || initFailed) {
            Log.w(TAG, "not ready (ready=$ready, failed=$initFailed)")
            return null
        }
        synchronized(tokenLock) {
            val latch = CountDownLatch(1)
            var playerToken: String? = null
            var streamingToken: String? = null

            pendingTokenCallback = { playerPot, streamingPot ->
                playerToken = playerPot
                streamingToken = streamingPot
                latch.countDown()
            }

            // Content-bound token uses the full video URL as content binding
            val contentId = "https://www.youtube.com/watch?v=$videoId"
            // Session-bound token uses visitor data
            val sessionId = visitorData ?: ""

            // Build JS to mint both tokens entirely in JS scope
            val js = """
try {
    var potState = window._potState;
    if (!potState || !potState.webPoSignalOutput || !potState.integrityToken) {
        $JS_INTERFACE.onTokenError('Missing potState');
    } else {
        var contentId = ${jsStringLiteral(contentId)};
        var u8Content = new Uint8Array(Array.from(contentId).map(function(c){return c.charCodeAt(0)}));
        var playerTokenU8 = obtainPoToken(potState.webPoSignalOutput, potState.integrityToken, u8Content);
        var playerArr = [];
        for (var i = 0; i < playerTokenU8.length; i++) playerArr.push(playerTokenU8[i]);

        var sessionArr = null;
        var sessionId = ${jsStringLiteral(sessionId)};
        if (sessionId !== '') {
            var u8Session = new Uint8Array(Array.from(sessionId).map(function(c){return c.charCodeAt(0)}));
            var streamTokenU8 = obtainPoToken(potState.webPoSignalOutput, potState.integrityToken, u8Session);
            sessionArr = [];
            for (var j = 0; j < streamTokenU8.length; j++) sessionArr.push(streamTokenU8[j]);
        }

        $JS_INTERFACE.onTokenResult(playerArr.join(','), sessionArr ? sessionArr.join(',') : '');
    }
} catch(e) {
    $JS_INTERFACE.onTokenError(e + '');
}
            """.trimIndent()

            mainHandler.post {
                webView?.evaluateJavascript(js, null)
            }

            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            pendingTokenCallback = null
            if (playerToken != null) {
                return PoTokenResult(playerPot = playerToken!!, streamingPot = streamingToken ?: "")
            }
            return null
        }
    }

    fun isReady(): Boolean = ready && !initFailed

    fun destroy() {
        mainHandler.post {
            webView?.let {
                it.loadUrl("about:blank")
                it.clearHistory()
                it.clearCache(true)
                it.onPause()
                it.removeAllViews()
                it.destroy()
            }
            webView = null
            ready = false
        }
    }

    /** JS bridge — called from WebView JavaScript */
    class Bridge {
        @JavascriptInterface
        fun onHtmlLoaded() {
            Log.d(TAG, "HTML loaded, fetching challenge...")
            mainHandler.post { webView?.evaluateJavascript("$JS_INTERFACE.downloadAndRunBotguard()", null) }
        }

        @JavascriptInterface
        fun downloadAndRunBotguard() {
            Log.d(TAG, "Fetching BotGuard challenge...")
            try {
                val body = """[ "$REQUEST_KEY" ]"""
                val request = Request.Builder()
                    .url("https://www.youtube.com/api/jnn/v1/Create")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json+protobuf")
                    .addHeader("x-goog-api-key", GOOGLE_API_KEY)
                    .addHeader("x-user-agent", "grpc-web-javascript/0.1")
                    .post(body.toRequestBody("application/json+protobuf".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Challenge response len=${responseBody.length}")

                if (!response.isSuccessful || responseBody.isEmpty()) {
                    Log.e(TAG, "Challenge failed: HTTP ${response.code}")
                    initFailed = true
                    initLock.countDown()
                    return
                }

                val challengeData = parseChallengeData(responseBody)
                Log.d(TAG, "Challenge parsed OK, length=${challengeData.length}")
                mainHandler.post {
                    webView?.evaluateJavascript(
                        """try {
                            var data = $challengeData;
                            runBotGuard.call(this, data).then(function(result) {
                                $JS_INTERFACE.onBotguardResult(result.botguardResponse);
                            }, function(err) {
                                $JS_INTERFACE.onBotguardError(err + '');
                            });
                        } catch(e) {
                            $JS_INTERFACE.onBotguardError(e + '');
                        }""",
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Challenge error: ${e.message}")
                initFailed = true
                initLock.countDown()
            }
        }

        @JavascriptInterface
        fun onBotguardResult(botguardResponse: String) {
            Log.d(TAG, "BotGuard OK, fetching integrity token...")
            try {
                val body = """[ "$REQUEST_KEY", "$botguardResponse" ]"""
                val request = Request.Builder()
                    .url("https://www.youtube.com/api/jnn/v1/GenerateIT")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json+protobuf")
                    .addHeader("x-goog-api-key", GOOGLE_API_KEY)
                    .addHeader("x-user-agent", "grpc-web-javascript/0.1")
                    .post(body.toRequestBody("application/json+protobuf".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful || responseBody.isEmpty()) {
                    Log.e(TAG, "GenerateIT failed: HTTP ${response.code}")
                    initFailed = true
                    initLock.countDown()
                    return
                }

                val integrityData = JSONArray(responseBody)
                val integrityToken = integrityData.getString(0)
                val ttlSeconds = integrityData.getLong(1)
                Log.d(TAG, "Integrity token obtained, TTL=${ttlSeconds}s")

                // Store webPoSignalOutput + integrityToken in JS scope (functions can't cross bridge!)
                // webPoSignalOutput is already on window._potState from runBotGuard
                // integrityToken needs to be decoded to Uint8Array and stored
                mainHandler.post {
                    webView?.evaluateJavascript(
                        """try {
                            // Decode integrity token to Uint8Array
                            var b64 = "$integrityToken";
                            var binary = atob(b64);
                            var bytes = new Uint8Array(binary.length);
                            for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
                            window._potState = window._potState || {};
                            window._potState.integrityToken = bytes;
                            window._potState.ttl = $ttlSeconds;
                            $JS_INTERFACE.onReady($ttlSeconds);
                        } catch(e) {
                            $JS_INTERFACE.onBotguardError('Store integrity token failed: ' + e);
                        }""",
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "GenerateIT error: ${e.message}")
                initFailed = true
                initLock.countDown()
            }
        }

        @JavascriptInterface
        fun onBotguardError(error: String) {
            Log.e(TAG, "BotGuard error: $error")
            initFailed = true
            initLock.countDown()
        }

        @JavascriptInterface
        fun onReady(ttlSeconds: Long) {
            Log.d(TAG, "PO Token ready! TTL=${ttlSeconds}s")
            ready = true
            initLock.countDown()
        }

        @JavascriptInterface
        fun onTokenResult(u8PlayerComma: String, u8StreamComma: String) {
            Log.d(TAG, "Token generated (player u8 len=${u8PlayerComma.split(",").size}, stream u8 len=${u8StreamComma.split(",").size})")
            val playerBase64 = u8ToBase64(u8PlayerComma)
            val streamBase64 = if (u8StreamComma.isNotEmpty()) u8ToBase64(u8StreamComma) else ""
            pendingTokenCallback?.invoke(playerBase64, streamBase64)
        }

        @JavascriptInterface
        fun onTokenError(error: String) {
            Log.e(TAG, "Token error: $error")
            pendingTokenCallback?.invoke(null, null)
        }
    }

    // ---- Parsing helpers (based on BgUtils/NewPipe) ----

    private fun isJsonString(arr: JSONArray, index: Int): Boolean {
        return try {
            arr.get(index) is String
        } catch (_: Exception) { false }
    }

    private fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = JSONArray(rawChallengeData)
        val challengeArr = if (scrambled.length() > 1 && isJsonString(scrambled, 1)) {
            val descrambled = descramble(scrambled.getString(1))
            JSONArray(descrambled)
        } else {
            scrambled.getJSONArray(0)
        }

        val messageId = challengeArr.getString(0)
        val interpreterHash = challengeArr.getString(3)
        val program = challengeArr.getString(4)
        val globalName = challengeArr.getString(5)
        val clientExperimentsStateBlob = challengeArr.getString(7)

        // wrappedScript is at index 1 — array of strings, find the actual JS code
        val privArr = challengeArr.optJSONArray(1)
        val interpreterJs = findStringInArray(privArr)

        // wrappedUrl is at index 2 — may contain a URL to fetch the interpreter
        val urlArr = challengeArr.optJSONArray(2)
        val interpreterUrl = findStringInArray(urlArr)

        return JSONObject().apply {
            put("messageId", messageId)
            put("interpreterJavascript", JSONObject().apply {
                if (interpreterJs != null) {
                    put("privateDoNotAccessOrElseSafeScriptWrappedValue", interpreterJs)
                }
            })
            put("interpreterHash", interpreterHash)
            put("program", program)
            put("globalName", globalName)
            put("clientExperimentsStateBlob", clientExperimentsStateBlob)
            if (interpreterUrl != null) {
                put("interpreterUrl", JSONObject().apply {
                    put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", interpreterUrl)
                })
            }
        }.toString()
    }

    private fun findStringInArray(arr: JSONArray?): String? {
        if (arr == null) return null
        for (i in 0 until arr.length()) {
            if (isJsonString(arr, i)) return arr.getString(i)
        }
        return null
    }

    private fun descramble(scrambled: String): String {
        val decoded = base64ToBytes(scrambled)
        val shifted = decoded.map { (it.toInt() + 97).toByte() }.toByteArray()
        val result = String(shifted, Charsets.US_ASCII)
        Log.d(TAG, "descramble: ${decoded.size} bytes -> result first80=${result.take(80)}")
        return result
    }

    private fun base64ToBytes(base64: String): ByteArray {
        val mod = base64.replace('-', '+').replace('_', '/').replace('.', '=')
        return Base64.decode(mod, Base64.NO_WRAP)
    }

    private fun u8ToBase64(u8Comma: String): String {
        val bytes = u8Comma.split(",").map { it.trim().toUByte().toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
            .replace("+", "-").replace("/", "_")
    }

    private fun jsStringLiteral(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        return "\"$escaped\""
    }
}
