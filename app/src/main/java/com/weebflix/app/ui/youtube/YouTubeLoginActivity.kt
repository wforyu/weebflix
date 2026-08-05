package com.weebflix.app.ui.youtube

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.weebflix.app.R
import com.weebflix.app.data.auth.LoopbackOAuthServer
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.scraper.YouTubeResolver

/**
 * Google OAuth consent screen opened in the system browser (Google blocks
 * sign-in from embedded WebViews via the `disallowed_useragent` policy).
 *
 * A tiny loopback HTTP server on the redirect URI port (default 8080) captures
 * the `http://localhost:8080/callback?code=..&state=..` redirect, the code is
 * exchanged for tokens and the activity finishes with [Activity.RESULT_OK].
 */
class YouTubeLoginActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView
    private var server: LoopbackOAuthServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_login)

        tvStatus = findViewById(R.id.ytLoginStatus)
        tvError = findViewById(R.id.ytLoginError)
        findViewById<ImageView>(R.id.ytLoginBack).setOnClickListener { finish() }

        if (!YouTubeAuthManager.isConfigured()) {
            tvError.visibility = View.VISIBLE
            tvError.text = "OAuth belum dikonfigurasi.\nIsi Client ID di Settings → pilih provider YouTube, lalu Simpan OAuth."
            return
        }

        startLoopbackLogin()
    }

    private fun startLoopbackLogin() {
        val redirectUri = Uri.parse(YouTubeAuthManager.redirectUri)
        val port = if (redirectUri.port > 0) redirectUri.port else 8080
        server = LoopbackOAuthServer(port) { code, state, error ->
            val fail = if (error == null && code != null && state != null) {
                YouTubeAuthManager.exchangeCode(code, state)
            } else {
                null
            }
            runOnUiThread { onRedirectResult(code, state, error, fail) }
        }
        val err = server?.start()
        if (err != null) {
            tvError.visibility = View.VISIBLE
            tvError.text = "Gagal membuka server lokal (port $port): $err"
            return
        }
        tvStatus.text = "Meminta login... Buka browser Google jika belum terbuka."
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YouTubeAuthManager.buildAuthUrl())))
        } catch (e: Exception) {
            tvError.visibility = View.VISIBLE
            tvError.text = "Tidak ada browser terpasang: ${e.message}"
            server?.stop()
            server = null
        }
    }

    private fun onRedirectResult(code: String?, state: String?, error: String?, fail: String?) {
        server?.stop()
        server = null
        if (error != null) {
            Toast.makeText(this, "Login dibatalkan atau ditolak: $error", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        if (code == null || state == null) {
            Toast.makeText(this, "Login gagal: respons tidak valid dari Google", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        YouTubeResolver.clearMemo()
        if (fail == null) {
            Toast.makeText(this, "Login berhasil: ${YouTubeAuthManager.email()}", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
        } else {
            Toast.makeText(this, "Login gagal: $fail", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }
}
