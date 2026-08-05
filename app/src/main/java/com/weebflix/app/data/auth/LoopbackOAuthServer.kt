package com.weebflix.app.data.auth

import android.net.Uri
import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Minimal loopback HTTP server used to receive the Google OAuth redirect
 * (`http://localhost:8080/callback?code=..&state=..`).
 *
 * Google blocks sign-in from embedded WebViews (`disallowed_useragent`), so the
 * consent screen is opened in the system browser instead. This server listens on
 * the redirect URI's port and captures the loopback redirect when the browser
 * completes the flow.
 *
 * [callback] is invoked on a background thread with the parsed redirect params.
 */
class LoopbackOAuthServer(
    private val port: Int,
    private val callback: (code: String?, state: String?, error: String?) -> Unit
) {

    private val TAG = "LoopbackAuth"
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    /** Starts listening. Returns a user-facing error message, or null on success. */
    fun start(): String? {
        return try {
            val socket = ServerSocket(port, 50, InetAddress.getLoopbackAddress())
            serverSocket = socket
            executor = Executors.newSingleThreadExecutor().also { it.execute { acceptLoop(socket) } }
            Log.i(TAG, "listening on 127.0.0.1:$port")
            null
        } catch (e: Exception) {
            Log.w(TAG, "start failed: ${e.message}")
            e.message
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                break
            }
            try {
                handle(client)
            } catch (e: Exception) {
                Log.w(TAG, "handle error: ${e.message}")
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15000
        val reader = client.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val path = parts[1]
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }

        val uri = Uri.parse("http://localhost:$port$path")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")

        val body = "<!DOCTYPE html><html><body style=\"background:#141414;color:#fff;font-family:sans-serif;text-align:center;padding-top:40vh\">" +
            "<h2>Login berhasil.</h2><p>Silakan kembali ke aplikasi WeebFlix.</p></body></html>"
        val resp = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=UTF-8\r\n" +
            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n" +
            "Connection: close\r\n\r\n" + body
        client.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
        client.getOutputStream().flush()

        Log.i(TAG, "redirect received code=${code != null} state=${state != null} error=$error")
        callback(code, state, error)
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        try { executor?.shutdownNow() } catch (_: Exception) {}
        serverSocket = null
        executor = null
    }
}
