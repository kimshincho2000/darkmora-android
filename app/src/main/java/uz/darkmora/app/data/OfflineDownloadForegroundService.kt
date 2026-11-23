package uz.darkmora.app.data

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import uz.darkmora.app.MainActivity
import uz.darkmora.app.R

class OfflineDownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CANCEL_ALL) {
            OfflineDownloadManager.cancelAll()
            stopSelfSafely()
            return START_NOT_STICKY
        }
        if (action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "DarkMora"
        val qism = intent?.getIntExtra(EXTRA_QISM, 1) ?: 1
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val downloaded = intent?.getLongExtra(EXTRA_DOWNLOADED, 0L) ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL, -1L) ?: -1L
        val queued = intent?.getIntExtra(EXTRA_QUEUED, 0) ?: 0

        val notif = buildProgressNotification(
            context = this,
            title = title,
            qism = qism,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            queuedCount = queued
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIF_ID, notif)
            } catch (_: Exception) {}
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        releaseWakeLock()
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DarkMora::OfflineDownloadLock")
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L) // Up to 4 hours safety timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Oflayn yuklanmalar",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Anime qismlarini orqa fonda yuklab olish jarayoni"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "darkmora_offline_downloads_ch"
        private const val NOTIF_ID = 4401
        private const val ACTION_START_OR_UPDATE = "uz.darkmora.app.action.DL_START_UPDATE"
        private const val ACTION_STOP = "uz.darkmora.app.action.DL_STOP"
        private const val ACTION_CANCEL_ALL = "uz.darkmora.app.action.DL_CANCEL_ALL"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_QISM = "extra_qism"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_DOWNLOADED = "extra_downloaded"
        private const val EXTRA_TOTAL = "extra_total"
        private const val EXTRA_QUEUED = "extra_queued"

        fun start(
            context: Context,
            title: String,
            qism: Int,
            queuedCount: Int
        ) {
            val intent = Intent(context, OfflineDownloadForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_QISM, qism)
                putExtra(EXTRA_PROGRESS, 0)
                putExtra(EXTRA_DOWNLOADED, 0L)
                putExtra(EXTRA_TOTAL, -1L)
                putExtra(EXTRA_QUEUED, queuedCount)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun updateProgress(
            context: Context,
            title: String,
            qism: Int,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            queuedCount: Int
        ) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val notif = buildProgressNotification(
                    context = context,
                    title = title,
                    qism = qism,
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    queuedCount = queuedCount
                )
                nm.notify(NOTIF_ID, notif)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, OfflineDownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        private fun buildProgressNotification(
            context: Context,
            title: String,
            qism: Int,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            queuedCount: Int
        ): Notification {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context,
                4402,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, OfflineDownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            val cancelPi = PendingIntent.getService(
                context,
                4403,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sizeText = if (totalBytes > 0L) {
                "${OfflineDownloadManager.formatFileSize(downloadedBytes)} / ${OfflineDownloadManager.formatFileSize(totalBytes)}"
            } else if (downloadedBytes > 0L) {
                OfflineDownloadManager.formatFileSize(downloadedBytes)
            } else {
                "Tayyorlanmoqda..."
            }

            val queueSuffix = if (queuedCount > 0) " • +$queuedCount navbatda" else ""
            val contentText = if (totalBytes > 0L) {
                "$progress% • $sizeText$queueSuffix"
            } else {
                "$sizeText$queueSuffix"
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_darkmora)
                .setContentTitle("$title — $qism-qism")
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPi)
                .setProgress(100, progress.coerceIn(0, 100), totalBytes <= 0L)
                .addAction(0, "Bekor qilish", cancelPi)
                .build()
        }
    }
}
