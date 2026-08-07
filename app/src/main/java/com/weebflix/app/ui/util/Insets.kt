package com.weebflix.app.ui.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Membuat tampilan konsisten dengan system bars Android (navigasi 3 tombol
 * maupun gestur, Android lama & baru): konten digambar edge-to-edge dengan bar
 * transparan, lalu view di-pad pakai inset asli dari sistem. Saat bar
 * disembunyikan (immersive) inset bernilai 0 sehingga padding kembali ke nilai
 * asli layout.
 */
object Insets {

    /** Jadikan window edge-to-edge dengan status/nav bar transparan. */
    fun edgeToEdge(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.navigationBarDividerColor = Color.TRANSPARENT
        }
    }
}

/**
 * Tambahkan padding = inset system bars pada view (atas = status bar,
 * bawah = navigation bar). Padding lama dipertahankan (dijumlahkan).
 */
fun View.padSystemBars(top: Boolean = true, bottom: Boolean = true) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(
            baseLeft,
            if (top) baseTop + bars.top else baseTop,
            baseRight,
            if (bottom) baseBottom + bars.bottom else baseBottom
        )
        insets
    }
}
