package com.weebflix.app.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.weebflix.app.R

/**
 * Lightweight foreground service that keeps network alive during background playback.
 * On Android 12+ (especially 16), apps without a foreground service get their DNS/network
 * connections suspended when the screen is locked. This service shows a persistent notification
 * so the system treats us as foreground-priority, preventing UnknownHostException on
 * googlevideo.com / surrit.com / etc. while screen is off.
 */
class PlayerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "weebflix_playback"
        const val NOTIFICATION_ID = 9999
        const val ACTION_START = "com.weebflix.app.PLAYER_SERVICE_START"
        const val ACTION_STOP = "com.weebflix.app.PLAYER_SERVICE_STOP"
        const val EXTRA_TITLE = "title"
        private const val TAG = "PlayerFgService"

        fun start(context: Context, title: String) {
            val intent = Intent(context, PlayerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlayerForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
                startForeground(NOTIFICATION_ID, buildNotification(title))
            }
        }
        return START_STICKY
    }

    private fun buildNotification(title: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Memutar: $title")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemutaran",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga playback tetap aktif saat layar mati"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
