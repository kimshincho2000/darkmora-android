package uz.darkmora.app.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.darkmora.app.MainActivity
import uz.darkmora.app.R
import uz.darkmora.app.ui.screens.DlnaTvCastHelper
import uz.darkmora.app.ui.screens.DlnaTvDevice
import uz.darkmora.app.ui.screens.LocalTvStreamProxy
import uz.darkmora.app.ui.theme.tr
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

object TvCastState {
    var connectedTv by mutableStateOf<DlnaTvDevice?>(null)
    var castingTitle by mutableStateOf("")
    var castingPosterUrl by mutableStateOf("")
    var isTvPlaying by mutableStateOf(true)
    var durationMs by mutableLongStateOf(0L)
    var positionMs by mutableLongStateOf(0L)
    var lastPositionUpdateElapsedMs by mutableLongStateOf(0L)

    fun estimatedPositionMs(): Long {
        if (!isTvPlaying) return positionMs
        val delta = max(0L, SystemClock.elapsedRealtime() - lastPositionUpdateElapsedMs)
        val est = positionMs + delta
        return if (durationMs > 0L) min(est, durationMs) else est
    }
}

class TvCastForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null
    private var currentPosterBitmap: Bitmap? = null
    private var lastLoadedPosterUrl: String = ""

    private val posterHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
        initMediaSession()
    }

    private fun initMediaSession() {
        val session = MediaSession(this, "DarkMoraTvCast")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                val tv = TvCastState.connectedTv ?: return
                serviceScope.launch {
                    if (DlnaTvCastHelper.resumeCast(tv)) {
                        TvCastState.isTvPlaying = true
                        TvCastState.lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()
                        updateMediaSessionAndNotification()
                    }
                }
            }

            override fun onPause() {
                val tv = TvCastState.connectedTv ?: return
                serviceScope.launch {
                    TvCastState.positionMs = TvCastState.estimatedPositionMs()
                    if (DlnaTvCastHelper.pauseCast(tv)) {
                        TvCastState.isTvPlaying = false
                        TvCastState.lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()
                        updateMediaSessionAndNotification()
                    }
                }
            }

            override fun onSeekTo(pos: Long) {
                val tv = TvCastState.connectedTv ?: return
                val clamped = max(0L, pos)
                serviceScope.launch {
                    if (DlnaTvCastHelper.seekCast(tv, clamped)) {
                        TvCastState.positionMs = clamped
                        TvCastState.lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()
                        updateMediaSessionAndNotification()
                    }
                }
            }

            override fun onStop() {
                stopCastingAndService()
            }
        })
        session.isActive = true
        mediaSession = session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCastingAndService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                val tv = TvCastState.connectedTv
                if (tv != null) {
                    serviceScope.launch {
                        if (TvCastState.isTvPlaying) {
                            TvCastState.positionMs = TvCastState.estimatedPositionMs()
                            if (DlnaTvCastHelper.pauseCast(tv)) {
                                TvCastState.isTvPlaying = false
                                TvCastState.lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()
                                updateMediaSessionAndNotification()
                            }
                        } else {
                            if (DlnaTvCastHelper.resumeCast(tv)) {
                                TvCastState.isTvPlaying = true
                                TvCastState.lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()
                                updateMediaSessionAndNotification()
                            }
                        }
                    }
                }
                return START_STICKY
            }
        }

        val tvName = intent?.getStringExtra(EXTRA_TV_NAME)
            ?: TvCastState.connectedTv?.name
            ?: "Smart TV"
        val videoTitle = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
            ?: TvCastState.castingTitle.ifBlank { "DarkMora" }
        val posterUrl = intent?.getStringExtra(EXTRA_POSTER_URL)
            ?: TvCastState.castingPosterUrl

        updateMediaSessionMetadata(tvName, videoTitle, currentPosterBitmap)
        updatePlaybackState()

        val notification = buildMediaNotification(tvName, videoTitle, currentPosterBitmap)
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

        if (posterUrl.isNotBlank() && posterUrl != lastLoadedPosterUrl) {
            lastLoadedPosterUrl = posterUrl
            serviceScope.launch {
                val bmp = fetchPosterBitmap(posterUrl)
                if (bmp != null && TvCastState.connectedTv != null) {
                    currentPosterBitmap = bmp
                    updateMediaSessionAndNotification()
                }
            }
        }

        return START_STICKY
    }

    private fun stopCastingAndService() {
        val activeTv = TvCastState.connectedTv
        TvCastState.connectedTv = null
        TvCastState.castingTitle = ""
        TvCastState.castingPosterUrl = ""
        TvCastState.isTvPlaying = false
        serviceScope.launch {
            if (activeTv != null) {
                try {
                    DlnaTvCastHelper.stopCast(activeTv)
                } catch (_: Exception) {}
            }
            LocalTvStreamProxy.stop()
        }
        try {
            mediaSession?.isActive = false
        } catch (_: Exception) {}
        stopForegroundCompat()
        stopSelf()
    }

    private fun updateMediaSessionAndNotification() {
        val tv = TvCastState.connectedTv ?: return
        val title = TvCastState.castingTitle.ifBlank { "DarkMora" }
        updateMediaSessionMetadata(tv.name, title, currentPosterBitmap)
        updatePlaybackState()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildMediaNotification(tv.name, title, currentPosterBitmap))
        } catch (_: Exception) {}
    }

    private fun updateMediaSessionMetadata(tvName: String, videoTitle: String, poster: Bitmap?) {
        val session = mediaSession ?: return
        val dur = if (TvCastState.durationMs > 0L) TvCastState.durationMs else 24 * 60 * 1000L
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, videoTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "DarkMora TV • $tvName")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, tvName)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, dur)
        if (poster != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, poster)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, poster)
        }
        session.setMetadata(builder.build())
    }

    private fun updatePlaybackState() {
        val session = mediaSession ?: return
        val state = if (TvCastState.isTvPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val speed = if (TvCastState.isTvPlaying) 1.0f else 0.0f
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, TvCastState.estimatedPositionMs(), speed, SystemClock.elapsedRealtime())
            .build()
        session.setPlaybackState(playbackState)
    }

    private fun fetchPosterBitmap(url: String): Bitmap? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Referer", "https://darkmora.uz/")
                .build()
            posterHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val maxSide = 512
                if (raw.width > maxSide || raw.height > maxSide) {
                    val ratio = min(maxSide.toFloat() / raw.width, maxSide.toFloat() / raw.height)
                    val w = max(1, (raw.width * ratio).toInt())
                    val h = max(1, (raw.height * ratio).toInt())
                    Bitmap.createScaledBitmap(raw, w, h, true)
                } else {
                    raw
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {}
        mediaSession = null
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
                wakeLock?.acquire(4 * 60 * 60 * 1000L)
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
                    description = "Smart TV ga orqa fonda media uzatish boshqaruvi"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildMediaNotification(tvName: String, videoTitle: String, poster: Bitmap?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, TvCastForegroundService::class.java).apply {
            action = ACTION_TOGGLE_PLAY_PAUSE
        }
        val togglePending = PendingIntent.getService(
            this,
            103,
            toggleIntent,
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

        val playPauseIcon = if (TvCastState.isTvPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (TvCastState.isTvPlaying) {
            tr("Pauza", "Pause", "Пауза")
        } else {
            tr("Davom ettirish", "Play", "Играть")
        }

        val toggleAction = Notification.Action.Builder(
            Icon.createWithResource(this, playPauseIcon),
            playPauseLabel,
            togglePending
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            tr("TV dan uzish", "Stop Cast", "Отключить ТВ"),
            stopPending
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1)
        mediaSession?.sessionToken?.let { mediaStyle.setMediaSession(it) }

        builder
            .setSmallIcon(R.drawable.ic_stat_darkmora)
            .setContentTitle(videoTitle)
            .setContentText("DarkMora TV • $tvName")
            .setSubText(tr("TV da ijro etilmoqda", "Casting to TV", "Трансляция на ТВ"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setContentIntent(openPending)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(mediaStyle)

        if (poster != null) {
            builder.setLargeIcon(poster)
        }

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "darkmora_tv_cast_channel"
        private const val NOTIFICATION_ID = 4409
        const val ACTION_STOP = "uz.darkmora.app.action.STOP_TV_CAST"
        const val ACTION_TOGGLE_PLAY_PAUSE = "uz.darkmora.app.action.TOGGLE_TV_CAST"
        private const val EXTRA_TV_NAME = "tv_name"
        private const val EXTRA_VIDEO_TITLE = "video_title"
        private const val EXTRA_POSTER_URL = "poster_url"

        fun start(
            context: Context,
            tv: DlnaTvDevice,
            videoTitle: String,
            posterUrl: String = "",
            durationMs: Long = 0L
        ) {
            TvCastState.connectedTv = tv
            TvCastState.castingTitle = videoTitle
            TvCastState.castingPosterUrl = posterUrl
            TvCastState.isTvPlaying = true
            TvCastState.durationMs = durationMs
            TvCastState.positionMs = 0L
            TvCastState.lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()

            val intent = Intent(context, TvCastForegroundService::class.java).apply {
                putExtra(EXTRA_TV_NAME, tv.name)
                putExtra(EXTRA_VIDEO_TITLE, videoTitle)
                putExtra(EXTRA_POSTER_URL, posterUrl)
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
                TvCastState.castingPosterUrl = ""
                TvCastState.isTvPlaying = false
                LocalTvStreamProxy.stop()
                try {
                    context.stopService(Intent(context, TvCastForegroundService::class.java))
                } catch (_: Exception) {}
            }
        }
    }
}
