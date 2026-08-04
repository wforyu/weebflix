package com.weebflix.app.ui.youtube

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.weebflix.app.R
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.scraper.YouTubeResolver

/**
 * Google OAuth consent screen hosted in a WebView.
 *
 * The Google loopback redirect (`http://localhost:8080/callback?code=..&state=..`,
 * or any configured [YouTubeAuthManager.redirectUri]) is intercepted here via
 * [WebViewClient.shouldOverrideUrlLoading] — no local HTTP server needed. The code is
 * exchanged for tokens and the activity finishes with [Activity.RESULT_OK].
 */
class YouTubeLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_login)

        webView = findViewById(R.id.ytLoginWeb)
        tvError = findViewById(R.id.ytLoginError)
        val back = findViewById<ImageView>(R.id.ytLoginBack)
        back.setOnClickListener { finish() }

        if (!YouTubeAuthManager.isConfigured()) {
            tvError.visibility = View.VISIBLE
            tvError.text = "OAuth belum dikonfigurasi.\nIsi Client ID di Settings → pilih provider YouTube, lalu Simpan OAuth."
            return
        }

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.setSupportMultipleWindows(false)
        // Desktop Chrome UA avoids Google's "unsupported browser" WebView block.
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return interceptCallback(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return interceptCallback(request.url.toString())
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tvError.visibility = View.GONE
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                if (failingUrl.isNotEmpty()) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Gagal memuat: $description"
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                onReceivedError(view, error.errorCode, error.description.toString(), request.url.toString())
            }
        }

        val url = YouTubeAuthManager.buildAuthUrl()
        webView.loadUrl(url)
    }

    private fun interceptCallback(url: String): Boolean {
        val base = YouTubeAuthManager.redirectUri.trimEnd('/')
        if (!url.startsWith(base)) return false
        val uri = Uri.parse(url)
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Toast.makeText(this, "Login dibatalkan atau ditolak: $error", Toast.LENGTH_LONG).show()
            finish()
            return true
        }
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) return false
        val fail = YouTubeAuthManager.exchangeCode(code, state)
        YouTubeResolver.clearMemo()
        if (fail == null) {
            Toast.makeText(this, "Login berhasil: ${YouTubeAuthManager.email()}", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
        } else {
            Toast.makeText(this, "Login gagal: $fail", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
        return true
    }
}
