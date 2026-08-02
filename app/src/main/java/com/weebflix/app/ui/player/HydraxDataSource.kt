package com.weebflix.app.ui.player

import android.net.Uri
import android.util.Base64
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * ExoPlayer DataSource for OppaDrama Hydrax servers.
 *
 * The Hydrax CDN (abyssplayer.com -> *.sssrr.org) serves MP4 files that are
 * encrypted with AES-256-CTR. The AES key is `md5hex(filename)` (32 ASCII hex
 * chars) and the initial counter block is the first 16 bytes of that key.
 * Because CTR is a stream cipher the byte layout of the ciphertext file is
 * identical to the plaintext.
 *
 * Audit finding (2026-08): ONLY the leading 65536 bytes of each file are
 * encrypted; everything from offset 65536 to EOF is already plaintext. This
 * DataSource therefore decrypts only positions < 65536 and passes the rest
 * through untouched.
 *
 * The URI scheme `hydrax://<base64url>` carries a JSON payload:
 *   {"u": <real CDN url>, "k": <32-char hex key>, "s": <plaintext size>}
 */
class HydraxDataSource(
    private val okHttpClient: OkHttpClient
) : DataSource {

    private var upstream: OkHttpDataSource? = null
    private var decoder: AesCtrDecoder? = null
    private var uri: Uri? = null
    private var position = 0L
    private val transferListeners = mutableListOf<TransferListener>()

    companion object {
        const val ENCRYPTED_BYTES = 65536L
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        val payload = String(
            Base64.decode(dataSpec.uri.toString().removePrefix("hydrax://"), Base64.URL_SAFE or Base64.NO_WRAP),
            Charsets.UTF_8
        )
        val json = JSONObject(payload)
        val realUrl = json.getString("u")
        val key = json.getString("k").toByteArray(Charsets.US_ASCII)
        require(key.size == 32) { "Invalid Hydrax AES key length ${key.size}" }
        val plaintextSize = json.getLong("s")

        decoder = AesCtrDecoder(key)
        uri = Uri.parse(realUrl)
        position = dataSpec.position

        val ds = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://abyssplayer.com/",
                "Origin" to "https://abyssplayer.com"
            ))
            .createDataSource()
        transferListeners.forEach { ds.addTransferListener(it) }
        upstream = ds
        return ds.open(dataSpec.buildUpon().setUri(realUrl).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val ds = upstream ?: throw IOException("HydraxDataSource not opened")
        val bytesRead = ds.read(buffer, offset, length)
        if (bytesRead > 0) {
            decoder?.decryptInPlace(buffer, offset, bytesRead, position)
            position += bytesRead
        }
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        upstream?.close()
        upstream = null
        decoder = null
        position = 0L
    }

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    private class AesCtrDecoder(private val key: ByteArray) {
        private val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        private val initialCounter = BigInteger(1, key.copyOfRange(0, 16))
        private var cachedBlock = -1L
        private var cachedKeystream = ByteArray(16)

        init {
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        }

        private fun keystream(block: Long): ByteArray {
            if (block == cachedBlock) return cachedKeystream
            val counterBytes = initialCounter.add(BigInteger.valueOf(block)).toByteArray()
            val padded = ByteArray(16)
            System.arraycopy(counterBytes, 0, padded, 16 - counterBytes.size, counterBytes.size)
            cachedKeystream = cipher.doFinal(padded)
            cachedBlock = block
            return cachedKeystream
        }

        fun decryptInPlace(buf: ByteArray, off: Int, len: Int, position: Long) {
            val end = minOf(len.toLong(), (ENCRYPTED_BYTES - position).coerceAtLeast(0L)).toInt()
            var i = 0
            while (i < end) {
                val pos = position + i
                val block = pos / 16
                val inBlock = (pos % 16).toInt()
                val ks = keystream(block)
                val n = minOf(end - i, 16 - inBlock)
                for (j in 0 until n) {
                    buf[off + i + j] = (buf[off + i + j].toInt() xor ks[inBlock + j].toInt()).toByte()
                }
                i += n
            }
        }
    }
}
