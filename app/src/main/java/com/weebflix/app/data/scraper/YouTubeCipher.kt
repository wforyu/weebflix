package com.weebflix.app.data.scraper

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

object YouTubeCipher {

    private const val TAG = "YouTubeCipher"

    sealed class Op {
        object Reverse : Op()
        data class RemoveFirst(val n: Int) : Op()
        data class KeepFirst(val n: Int) : Op()
        data class RemoveLast(val n: Int) : Op()
        data class Swap(val n: Int) : Op()
    }

    fun applyOps(signature: String, ops: List<Op>): String {
        var s = signature
        for (op in ops) {
            s = when (op) {
                is Op.Reverse -> s.reversed()
                is Op.RemoveFirst -> s.substring(op.n.coerceIn(0, s.length))
                is Op.KeepFirst -> s.substring(0, op.n.coerceIn(0, s.length))
                is Op.RemoveLast -> s.substring(0, (s.length - op.n).coerceIn(0, s.length))
                is Op.Swap -> {
                    val c = s.toCharArray()
                    val idx = ((op.n % c.size) + c.size) % c.size
                    val tmp = c[0]
                    c[0] = c[idx]
                    c[idx] = tmp
                    String(c)
                }
            }
        }
        return s
    }

    /** Extracts the base.js URL from a watch/embed page (ytcfg "jsUrl"). */
    fun extractBaseJsUrl(page: String): String? {
        val m = Regex("\"jsUrl\"\\s*:\\s*\"([^\"]+)\"").find(page)
            ?: Regex("assets\\s*:\\s*\"([^\"]+)\"").find(page)
            ?: return null
        var url = m.groupValues[1].replace("\\/", "/").replace("\\\\", "\\")
        if (!url.startsWith("http")) {
            url = if (url.startsWith("//")) "https:$url" else "https://www.youtube.com$url"
        }
        return url
    }

    /** Parses decipher ops from base.js source. Best-effort; null when not parseable. */
    fun parseOps(baseJs: String): List<Op>? {
        try {
            val funcBody = Regex(
                """(?:function\s+\w+|\(?(\w+)\)?\s*=\s*function|\w+\s*=\s*\(a\)\s*=>)\s*\(a\)\s*\{\s*a=a\.split\(""\);(.*?)return a\.join\(""\)\s*\}""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).find(baseJs) ?: run {
                Log.w(TAG, "decipher function not found")
                return null
            }
            val body = funcBody.groupValues[2]
            Log.d(TAG, "decipher body: $body")

            val calls = Regex("""([A-Za-z0-9_$]+)\.([A-Za-z0-9_$]+)\(a,(-?\d+)\)""").findAll(body)
            if (!calls.iterator().hasNext()) return null

            val ops = calls.map { m ->
                val helper = m.groupValues[2]
                val arg = m.groupValues[3].toInt()
                val def = findHelperDef(baseJs, helper)
                classify(def, arg)
            }.toList()
            Log.d(TAG, "decipher ops: $ops")
            return ops
        } catch (e: Exception) {
            Log.w(TAG, "parseOps failed: ${e.message}")
            return null
        }
    }

    private fun findHelperDef(baseJs: String, helper: String): String {
        val patterns = listOf(
            Regex("""\b$helper\s*:\s*function\(a,?b?\)\s*\{([^}]*?)\}"""),
            Regex("""\bfunction\s+$helper\(a,?b?\)\s*\{([^}]*?)\}"""),
            Regex("""\b$helper\s*=\s*function\(a,?b?\)\s*\{([^}]*?)\}""")
        )
        for (p in patterns) {
            p.find(baseJs)?.let { return it.groupValues[1] }
        }
        return ""
    }

    private fun classify(body: String, arg: Int): Op {
        return when {
            body.contains("reverse") -> Op.Reverse
            body.contains("splice") || body.contains("slice") -> {
                when {
                    body.contains("splice(0, b") || body.contains("splice(0,b") -> Op.RemoveFirst(arg)
                    body.contains("splice(-b") || body.contains("splice(- b") -> Op.RemoveLast(arg)
                    body.contains("splice(b") -> Op.KeepFirst(arg)
                    body.contains("slice(0, b") -> Op.KeepFirst(arg)
                    else -> Op.RemoveFirst(arg)
                }
            }
            body.contains("a[0]") && body.contains("b%a.length") -> Op.Swap(arg)
            else -> Op.RemoveFirst(arg)
        }
    }

    /** Deciphers a full signatureCipher/cipher value into a playable URL. */
    fun decipherCipher(cipher: String, ops: List<Op>): String {
        val params = cipher.split("&").associate { kv ->
            val idx = kv.indexOf("=")
            if (idx >= 0) kv.substring(0, idx) to kv.substring(idx + 1) else kv to ""
        }
        val url = params["url"] ?: return ""
        val s = params["s"] ?: return ""
        val sp = params["sp"] ?: "sig"
        val deciphered = applyOps(URLDecoder.decode(s, "UTF-8"), ops)
        val separator = if (url.contains("?")) "&" else "?"
        return URLDecoder.decode(url, "UTF-8") + separator + sp + "=" + deciphered
    }

    /** Fetches base.js via OkHttp and returns cached ops. */
    fun getCipherOps(client: OkHttpClient): List<Op>? {
        if (cachedOps != null) return cachedOps
        val page = fetch(client, "https://www.youtube.com/embed/jNQXAC9IVRw")
            ?: return null
        val jsUrl = extractBaseJsUrl(page) ?: run {
            Log.w(TAG, "no jsUrl found")
            return null
        }
        val js = fetch(client, jsUrl) ?: run {
            Log.w(TAG, "base.js fetch failed: $jsUrl")
            return null
        }
        cachedOps = parseOps(js)
        return cachedOps
    }

    private var cachedOps: List<Op>? = null

    private fun fetch(client: OkHttpClient, url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed: ${e.message}")
            null
        }
    }
}
