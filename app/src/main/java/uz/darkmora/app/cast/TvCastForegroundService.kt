package uz.darkmora.app.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uz.darkmora.app.MainActivity
import uz.darkmora.app.R
import uz.darkmora.app.ui.screens.DlnaTvCastHelper
import uz.darkmora.app.ui.screens.DlnaTvDevice
import uz.darkmora.app.ui.screens.LocalTvStreamProxy
import uz.darkmora.app.ui.theme.tr

object TvCastState {
    var connectedTv by mutableStateOf<DlnaTvDevice?>(null)
    var castingTitle by mutableStateOf("")
}

class TvCastForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val activeTv = TvCastState.connectedTv
            TvCastState.connectedTv = null
            TvCastState.castingTitle = ""
            serviceScope.launch {
                if (activeTv != null) {
                    try {
                        DlnaTvCastHelper.stopCast(activeTv)
                    } catch (_: Exception) {}
                }
                LocalTvStreamProxy.stop()
            }
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val tvName = intent?.getStringExtra(EXTRA_TV_NAME)
            ?: TvCastState.connectedTv?.name
            ?: "Smart TV"
        val videoTitle = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
            ?: TvCastState.castingTitle.ifBlank { "DarkMora" }

        val notification = buildNotification(tvName, videoTitle)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DarkMora::TvCastWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours safety timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "DarkMora TV Cast",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Smart TV ga orqa fonda video uzatish xizmati"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(tvName: String, videoTitle: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TvCastForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLine = "$videoTitle • ${tr("Orqa fonda TV ga uzatilmoqda", "Streaming to TV in background", "Трансляция на ТВ в фоне")}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_darkmora)
            .setContentTitle("DarkMora TV • $tvName")
            .setContentText(statusLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusLine))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(
                0,
                tr("TV dan uzish", "Stop Cast", "Отключить ТВ"),
                stopPending
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "darkmora_tv_cast_channel"
        private const val NOTIFICATION_ID = 4409
        const val ACTION_STOP = "uz.darkmora.app.action.STOP_TV_CAST"
        private const val EXTRA_TV_NAME = "tv_name"
        private const val EXTRA_VIDEO_TITLE = "video_title"

        fun start(context: Context, tv: DlnaTvDevice, videoTitle: String) {
            TvCastState.connectedTv = tv
            TvCastState.castingTitle = videoTitle
            val intent = Intent(context, TvCastForegroundService::class.java).apply {
                putExtra(EXTRA_TV_NAME, tv.name)
                putExtra(EXTRA_VIDEO_TITLE, videoTitle)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, TvCastForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                TvCastState.connectedTv = null
                TvCastState.castingTitle = ""
                LocalTvStreamProxy.stop()
                try {
                    context.stopService(Intent(context, TvCastForegroundService::class.java))
                } catch (_: Exception) {}
            }
        }
    }
}
