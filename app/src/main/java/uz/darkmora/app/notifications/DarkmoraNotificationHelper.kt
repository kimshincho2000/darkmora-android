package uz.darkmora.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.darkmora.app.MainActivity
import uz.darkmora.app.R
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.AppBroadcastItem
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import java.net.URL
import java.util.concurrent.TimeUnit

object DarkmoraNotificationHelper {
    const val CHANNEL_ID = "darkmora_new_episodes"
    private const val WORK_NAME = "darkmora_episode_sync_worker"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Yangi Anime va Qismlar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yangi chiqqan anime qismlari haqida bildirishnomalar"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun schedulePeriodicCheck(context: Context) {
        createNotificationChannel(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<EpisodeNotificationWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    suspend fun showEpisodeSystemNotification(
        context: Context,
        anime: Anime,
        isTest: Boolean = false
    ) {
        val sessionManager = UserSessionManager(context)
        if (!sessionManager.isNotificationsEnabled() && !isTest) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannel(context)

        val slug = anime.getEffectiveSlug()
        val epText = anime.displayEpisodes().ifBlank { "Yangi" }
        val title = "🎬 Yangi qism: ${anime.displayTitle()}"
        val body = "$epText-qism qo'shildi! Hoziroq Full HD sifatda tomosha qiling."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_anime_slug", slug)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            anime.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Try loading poster bitmap in IO for rich notification banner
        val posterBitmap = withContext(Dispatchers.IO) {
            try {
                val posterUrl = anime.displayPoster()
                if (posterUrl.startsWith("http")) {
                    val conn = URL(posterUrl).openConnection()
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.getInputStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_darkmora)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (posterBitmap != null) {
            builder.setLargeIcon(posterBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(posterBitmap)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        try {
            NotificationManagerCompat.from(context).notify(
                if (isTest) 99999 else (10000 + anime.id),
                builder.build()
            )
        } catch (_: SecurityException) {}
    }

    suspend fun showBroadcastSystemNotification(
        context: Context,
        bc: AppBroadcastItem
    ) {
        val sessionManager = UserSessionManager(context)
        if (!sessionManager.isNotificationsEnabled()) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannel(context)

        val slug = bc.animeSlug?.trim().orEmpty()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (slug.isNotBlank()) {
                putExtra("target_anime_slug", slug)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            50000 + bc.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val imageBitmap = withContext(Dispatchers.IO) {
            try {
                val imgUrl = bc.imageUrl?.trim().orEmpty()
                if (imgUrl.startsWith("http")) {
                    val conn = URL(imgUrl).openConnection()
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.getInputStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_darkmora)
            .setContentTitle(bc.title.ifBlank { "DarkMora Xabari" })
            .setContentText(bc.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (imageBitmap != null) {
            builder.setLargeIcon(imageBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .setSummaryText(bc.body)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bc.body))
        }

        try {
            NotificationManagerCompat.from(context).notify(
                50000 + bc.id,
                builder.build()
            )
        } catch (_: SecurityException) {}
    }

    suspend fun checkAndDispatchNewNotifications(context: Context) {
        val sessionManager = UserSessionManager(context)
        if (!sessionManager.isNotificationsEnabled()) return
        if (!hasNotificationPermission(context)) return

        // 1. Check Admin Broadcasts first
        try {
            val bcRes = DarkmoraApi.getBroadcasts(10)
            if (bcRes.ok && bcRes.data != null) {
                val bcList = bcRes.data.broadcasts ?: emptyList()
                val alreadyNotified = sessionManager.getNotifiedSystemKeys()
                for (bc in bcList) {
                    val bcKey = "bc_${bc.id}"
                    if (bc.id > 0 && !alreadyNotified.contains(bcKey)) {
                        showBroadcastSystemNotification(context, bc)
                        sessionManager.addNotifiedSystemKey(bcKey)
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Check New Anime Episodes
        try {
            val res = DarkmoraApi.getHome(sessionManager.getToken())
            if (res.ok && res.data != null) {
                val latestList = (res.data.latest ?: res.data.latestEpisodes ?: emptyList()).take(5)
                val alreadyNotified = sessionManager.getNotifiedSystemKeys()
                val hasEpisodeKeys = alreadyNotified.any { !it.startsWith("bc_") }

                if (!hasEpisodeKeys && latestList.isNotEmpty()) {
                    // First launch: notify the top newest episode & seed the rest so we don't spam 5 at once
                    val newest = latestList.first()
                    showEpisodeSystemNotification(context, newest)
                    latestList.forEach { a ->
                        sessionManager.addNotifiedSystemKey("${a.id}_${a.displayEpisodes()}")
                    }
                } else {
                    for (anime in latestList) {
                        val key = "${anime.id}_${anime.displayEpisodes()}"
                        if (!alreadyNotified.contains(key)) {
                            showEpisodeSystemNotification(context, anime)
                            sessionManager.addNotifiedSystemKey(key)
                            break // Dispatch 1 top notification per check cycle so status bar stays clean
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

class EpisodeNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        DarkmoraNotificationHelper.checkAndDispatchNewNotifications(applicationContext)
        return Result.success()
    }
}
