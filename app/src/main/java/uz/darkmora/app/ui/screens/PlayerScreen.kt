package uz.darkmora.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.Episode
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// APPLE TV+ / iOS 26 LIQUID GLASS 1:1 PLAYER (EXACT MATCH TO REFERENCE PHOTO)
// ============================================================================

private val GlassFill = Brush.verticalGradient(
    colors = listOf(
        Color(0x75383532),
        Color(0x881E1C1A)
    )
)

private val GlassActiveFill = Brush.verticalGradient(
    colors = listOf(
        Color(0x99524E4A),
        Color(0xAA2D2A27)
    )
)

private val GlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x66FFFFFF),
        Color(0x18FFFFFF),
        Color(0x38FFFFFF)
    )
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrlEncoded: String,
    titleEncoded: String,
    slug: String = "",
    initialEpisode: Int = 1,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val sessionManager = remember { UserSessionManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val initialUrl = remember(videoUrlEncoded) {
        try {
            val bytes = Base64.decode(videoUrlEncoded, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            videoUrlEncoded
        }
    }

    val initialTitle = remember(titleEncoded) {
        try {
            val bytes = Base64.decode(titleEncoded, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            titleEncoded
        }
    }

    var currentVideoUrl by remember { mutableStateOf(initialUrl) }
    var currentTitle by remember { mutableStateOf(initialTitle) }
    var currentEpNum by remember { mutableIntStateOf(initialEpisode) }
    var animeBaseTitle by remember { mutableStateOf(initialTitle.substringBefore(" - ")) }
    var animeDetail by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }

    LaunchedEffect(slug) {
        if (slug.isNotBlank()) {
            sessionManager.saveLastWatchedEpisode(slug, currentEpNum)
            try {
                val res = DarkmoraApi.getAnimeDetail(slug)
                if (res.ok && res.data != null) {
                    animeDetail = res.data.anime
                    episodes = res.data.episodes ?: emptyList()
                    res.data.anime?.displayTitle()?.let { animeBaseTitle = it }
                }
            } catch (_: Exception) {}
        }
    }

    // Player states
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    // Hardware Volume State (synced with top-right Volume Capsule)
    val maxStreamVol = remember { max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
    var volumeFraction by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxStreamVol.toFloat()
        )
    }

    // Custom UI Overlay States matching the photo
    var showControls by remember { mutableStateOf(true) }
    var activeBottomSheet by remember { mutableStateOf<String?>(null) } // "info", "insight", "continue", "cast", "audio", "speed"
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioBoostEnabled by remember { mutableStateOf(false) }
    var discoveredTvs by remember { mutableStateOf<List<DlnaTvDevice>>(emptyList()) }
    var isScanningTvs by remember { mutableStateOf(false) }
    var isCastingToTv by remember { mutableStateOf(false) }
    var connectedTv by remember { mutableStateOf<DlnaTvDevice?>(null) }
    var tvScanTick by remember { mutableIntStateOf(0) }
    var seekIndicatorText by remember { mutableStateOf<String?>(null) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var dragSeekFraction by remember { mutableFloatStateOf(0f) }
    var lastInteractionTick by remember { mutableIntStateOf(0) }

    // Lock to sensor landscape & enter true immersive fullscreen mode
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val window = activity?.window
        val insetsController = if (window != null) WindowCompat.getInsetsController(window, view) else null
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 DarkMora/2.4")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(25000)
            .setReadTimeoutMs(35000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://darkmora.uz/",
                    "Origin" to "https://darkmora.uz"
                )
            )
    }

    val exoPlayer = remember(retryKey) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        errorMessage = null
                        duration = max(0L, this@apply.duration)
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    errorMessage = "Video oqimini yuklashda xatolik (${error.errorCodeName}). Qayta urinib ko'ring."
                }
            })
        }
    }

    LaunchedEffect(currentVideoUrl, exoPlayer) {
        if (currentVideoUrl.isNotBlank()) {
            isBuffering = true
            errorMessage = null
            val mediaSource = DefaultMediaSourceFactory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(currentVideoUrl)))
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            isBuffering = false
            errorMessage = "Ushbu qism uchun video manzili topilmadi."
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            currentPosition = max(0L, exoPlayer.currentPosition)
            bufferedPosition = max(0L, exoPlayer.bufferedPosition)
            val d = exoPlayer.duration
            if (d > 0) duration = d
            delay(400)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity while playing
    LaunchedEffect(showControls, isPlaying, lastInteractionTick, isDraggingSeek, activeBottomSheet) {
        if (showControls && isPlaying && !isDraggingSeek && activeBottomSheet == null) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(seekIndicatorText) {
        if (seekIndicatorText != null) {
            delay(800)
            seekIndicatorText = null
        }
    }

    LaunchedEffect(activeBottomSheet, tvScanTick) {
        if (activeBottomSheet == "cast") {
            isScanningTvs = true
            try {
                discoveredTvs = DlnaTvCastHelper.discoverDevices()
            } catch (_: Exception) {
                discoveredTvs = emptyList()
            } finally {
                isScanningTvs = false
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            LocalTvStreamProxy.stop()
        }
    }

    fun switchEpisode(ep: Episode) {
        val nextUrl = ep.videoUrl ?: ep.embedUrl ?: ""
        currentVideoUrl = nextUrl
        currentEpNum = ep.qism
        currentTitle = "$animeBaseTitle - ${ep.qism}-qism"
        if (slug.isNotBlank()) {
            sessionManager.saveLastWatchedEpisode(slug, ep.qism)
        }
        val activeTv = connectedTv
        if (activeTv != null && nextUrl.isNotBlank()) {
            val nextTitle = currentTitle
            scope.launch {
                isCastingToTv = true
                val (ok, _) = DlnaTvCastHelper.castVideo(activeTv, nextUrl, nextTitle)
                isCastingToTv = false
                if (ok) {
                    exoPlayer.pause()
                    Toast.makeText(
                        context,
                        "${activeTv.name}: ${tr("Keyingi qism TV ga uzatildi", "Next episode sent to TV", "Следующая серия отправлена на ТВ")}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        activeBottomSheet = null
        lastInteractionTick++
    }

    fun setSystemVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        volumeFraction = clamped
        val targetVol = (clamped * maxStreamVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        exoPlayer.volume = if (audioBoostEnabled) min(1f, clamped * 1.2f) else clamped
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(activeBottomSheet) {
                detectTapGestures(
                    onTap = {
                        if (activeBottomSheet != null) {
                            activeBottomSheet = null
                        } else {
                            showControls = !showControls
                            lastInteractionTick++
                        }
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        when {
                            offset.x < width * 0.35f -> {
                                val target = max(0L, exoPlayer.currentPosition - 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "-10s"
                            }
                            offset.x > width * 0.65f -> {
                                val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = min(maxDur, exoPlayer.currentPosition + 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "+10s"
                            }
                            else -> {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                        lastInteractionTick++
                    }
                )
            }
    ) {
        // 1. Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Subtle Double-Tap Feedback Pill
        AnimatedVisibility(
            visible = seekIndicatorText != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassFill)
                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = seekIndicatorText ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. 1:1 APPLE TV+ LIQUID GLASS OVERLAY (EXACTLY AS IN PHOTO)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x88000000),
                                Color(0x18000000),
                                Color(0x22000000),
                                Color(0xB3000000)
                            )
                        )
                    )
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                // ============================================================
                // TOP ROW: [X] + [PiP | ScreenFit/Cast | Share] ... [Volume Bar]
                // ============================================================
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top-Left Group: Close Circle + 3-Icon Utility Capsule
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Circular Close [X] Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .bounceClick(onClick = onBackClick)
                                .clip(CircleShape)
                                .background(GlassFill)
                                .border(1.dp, GlassBorderBrush, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Yopish",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. 3-Icon Glass Capsule: [PiP]  [Screen Display/Cast]  [Share]
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(50))
                                .background(GlassFill)
                                .border(1.dp, GlassBorderBrush, RoundedCornerShape(50))
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Icon 1: Picture-in-Picture (PiP)
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "PiP",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                            try {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .build()
                                                activity.enterPictureInPictureMode(params)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "PiP rejimi qo'llab-quvvatlanmaydi", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        lastInteractionTick++
                                    }
                            )

                            // Icon 2: TV / Display Aspect Ratio Fit
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Ekran o'lchami",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        resizeMode = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                                Toast.makeText(context, "Ekran: To'ldirish (Zoom)", Toast.LENGTH_SHORT).show()
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            }
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                                                Toast.makeText(context, "Ekran: Cho'zish (Stretch)", Toast.LENGTH_SHORT).show()
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            }
                                            else -> {
                                                Toast.makeText(context, "Ekran: Asl o'lcham (Original)", Toast.LENGTH_SHORT).show()
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        }
                                        lastInteractionTick++
                                    }
                            )

                            // Icon 3: Share (iOS style share action)
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = "Ulashish",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        val shareUrl = if (slug.isNotBlank()) "https://darkmora.uz/anime/$slug" else "https://darkmora.uz"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$animeBaseTitle ($currentEpNum-qism) — DarkMora'da tomosha qiling: $shareUrl")
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Ulashish"))
                                        lastInteractionTick++
                                    }
                            )
                        }
                    }

                    // Top-Right Group: Volume Slider Capsule [ ━━━─── 🔊 ]
                    Row(
                        modifier = Modifier
                            .width(176.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(GlassFill)
                            .border(1.dp, GlassBorderBrush, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Smooth Touch/Drag Volume Track (White active bar on translucent grey track, no knob)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        setSystemVolume(offset.x / size.width.toFloat())
                                        lastInteractionTick++
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, _ ->
                                        change.consume()
                                        setSystemVolume(change.position.x / size.width.toFloat())
                                        lastInteractionTick++
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            ) {
                                val trackRadius = CornerRadius(size.height / 2f, size.height / 2f)
                                // Inactive background track
                                drawRoundRect(
                                    color = Color(0x55FFFFFF),
                                    size = size,
                                    cornerRadius = trackRadius
                                )
                                // Active white fill
                                val activeW = (size.width * volumeFraction.coerceIn(0f, 1f))
                                if (activeW > 0f) {
                                    drawRoundRect(
                                        color = Color.White,
                                        size = Size(activeW, size.height),
                                        cornerRadius = trackRadius
                                    )
                                }
                            }
                        }

                        // Speaker Icon (Tap to mute/unmute)
                        Icon(
                            imageVector = if (volumeFraction <= 0.02f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Ovoz",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .bounceClick {
                                    if (volumeFraction > 0.02f) {
                                        setSystemVolume(0f)
                                    } else {
                                        setSystemVolume(0.7f)
                                    }
                                    lastInteractionTick++
                                }
                        )
                    }
                }

                // ============================================================
                // CENTER ROW: [ (10) Rewind ]   [  || Pause  ]   [ (10) Forward ]
                // ============================================================
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(34.dp)
                ) {
                    // Left Circle: Rewind 10s
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .bounceClick {
                                val target = max(0L, exoPlayer.currentPosition - 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "-10s"
                                lastInteractionTick++
                            }
                            .clip(CircleShape)
                            .background(GlassFill)
                            .border(1.dp, GlassBorderBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(29.dp)
                        )
                    }

                    // Center Circle: Large Play / Pause Glass Orb
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .bounceClick {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteractionTick++
                            }
                            .clip(CircleShape)
                            .background(GlassFill)
                            .border(1.2.dp, GlassBorderBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.8.dp,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    // Right Circle: Forward 10s
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .bounceClick {
                                val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = min(maxDur, exoPlayer.currentPosition + 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "+10s"
                                lastInteractionTick++
                            }
                            .clip(CircleShape)
                            .background(GlassFill)
                            .border(1.dp, GlassBorderBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "+10s",
                            tint = Color.White,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                }

                // ============================================================
                // BOTTOM SECTION:
                // 1. Left: Episode Subtitle + Bold Anime Title | Right: [TV Cast | AudioWave | Speed] Capsule
                // 2. Full-width Rounded Minimalist Progress Bar (White fill on frosted track)
                // 3. Bottom Left Pills: [Info]  [InSight]  [Continue Watching]
                // ============================================================
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row above progress bar: Titles on Left, 3-Icon Media Capsule on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Left: Episode label ("The Promotion" style) + Large Bold Series Title ("The Studio" style)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "$currentEpNum-${tr("qism", "ep", "сер.")} • ${formatDurationMs(currentPosition)} / ${formatDurationMs(duration)}",
                                color = Color(0xCCFFFFFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = animeBaseTitle.ifBlank { currentTitle },
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right: 3-Icon Capsule [TV Cast 📺] [Audio Wave  Equalizer] [Speedometer ⏱️]
                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(50))
                                .background(GlassFill)
                                .border(1.dp, GlassBorderBrush, RoundedCornerShape(50))
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            // Icon 1: Cast to TV (DLNA / Miracast / External Player)
                            Icon(
                                imageVector = if (connectedTv != null) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = tr("TV ga ulash", "Cast to TV", "Подключить к ТВ"),
                                tint = if (activeBottomSheet == "cast" || connectedTv != null) PrimaryGlow else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        activeBottomSheet = if (activeBottomSheet == "cast") null else "cast"
                                        lastInteractionTick++
                                    }
                            )

                            // Icon 2: Audio Track / Sound Wave Boost
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio Oqim",
                                tint = if (activeBottomSheet == "audio" || audioBoostEnabled) PrimaryGlow else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        activeBottomSheet = if (activeBottomSheet == "audio") null else "audio"
                                        lastInteractionTick++
                                    }
                            )

                            // Icon 3: Playback Speedometer
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Tezlik",
                                tint = if (activeBottomSheet == "speed" || playbackSpeed != 1.0f) PrimaryGlow else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        activeBottomSheet = if (activeBottomSheet == "speed") null else "speed"
                                        lastInteractionTick++
                                    }
                            )
                        }
                    }

                    // Full-Width Thick Pill Progress Bar (Exact match to photo: clean white fill on grey track, no thumb circle)
                    val progressFraction = if (isDraggingSeek) {
                        dragSeekFraction
                    } else if (duration > 0L) {
                        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val bufferedFraction = if (duration > 0L) {
                        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (duration > 0L) {
                                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetMs = (frac * duration).toLong()
                                        exoPlayer.seekTo(targetMs)
                                        currentPosition = targetMs
                                        lastInteractionTick++
                                    }
                                }
                            }
                            .pointerInput(duration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        isDraggingSeek = true
                                        dragSeekFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        lastInteractionTick++
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        dragSeekFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        lastInteractionTick++
                                    },
                                    onDragEnd = {
                                        if (duration > 0L) {
                                            val targetMs = (dragSeekFraction * duration).toLong()
                                            exoPlayer.seekTo(targetMs)
                                            currentPosition = targetMs
                                        }
                                        isDraggingSeek = false
                                        lastInteractionTick++
                                    },
                                    onDragCancel = {
                                        isDraggingSeek = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isDraggingSeek) 10.dp else 7.dp)
                        ) {
                            val r = CornerRadius(size.height / 2f, size.height / 2f)
                            // Background translucent track
                            drawRoundRect(
                                color = Color(0x52FFFFFF),
                                size = size,
                                cornerRadius = r
                            )
                            // Buffered track
                            val bufW = size.width * bufferedFraction
                            if (bufW > 0f) {
                                drawRoundRect(
                                    color = Color(0x38FFFFFF),
                                    size = Size(bufW, size.height),
                                    cornerRadius = r
                                )
                            }
                            // Active solid white progress fill
                            val actW = size.width * progressFraction
                            if (actW > 0f) {
                                drawRoundRect(
                                    color = Color.White,
                                    size = Size(actW, size.height),
                                    cornerRadius = r
                                )
                            }
                        }
                    }

                    // Bottom Pills Row: [Ma'lumot]   [Tafsilotlar]   [Qismlar ro'yxati]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassBottomPill(
                            text = tr("Ma'lumot", "Info", "Инфо"),
                            isSelected = activeBottomSheet == "info",
                            onClick = {
                                activeBottomSheet = if (activeBottomSheet == "info") null else "info"
                                lastInteractionTick++
                            }
                        )

                        GlassBottomPill(
                            text = tr("Tafsilotlar", "InSight", "Детали"),
                            isSelected = activeBottomSheet == "insight",
                            onClick = {
                                activeBottomSheet = if (activeBottomSheet == "insight") null else "insight"
                                lastInteractionTick++
                            }
                        )

                        GlassBottomPill(
                            text = tr("Qismlar ro'yxati", "Continue Watching", "Список серий"),
                            isSelected = activeBottomSheet == "continue",
                            onClick = {
                                activeBottomSheet = if (activeBottomSheet == "continue") null else "continue"
                                lastInteractionTick++
                            }
                        )
                    }
                }
            }
        }

        // 4. Interactive Liquid Glass Popover Card for [Info / InSight / Continue Watching / TV Cast / Audio / Speed]
        AnimatedVisibility(
            visible = activeBottomSheet != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 26.dp, vertical = 72.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xE6282624),
                                Color(0xF2181715)
                            )
                        )
                    )
                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                when (activeBottomSheet) {
                    "info" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (animeDetail != null) {
                                AsyncImage(
                                    model = animeDetail!!.displayPoster(),
                                    contentDescription = animeBaseTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(84.dp)
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = animeBaseTitle,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "★ ${animeDetail?.displayRating() ?: "9.2"} • ${animeDetail?.displayYear() ?: "2026"}",
                                        color = Color(0xCCFFFFFF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = animeDetail?.displayDescription()
                                        ?: "$animeBaseTitle — $currentEpNum-qism. Yuqori sifatli HD striming.",
                                    color = Color(0xBBFFFFFF),
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    "insight" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = tr("Tafsilotlar • Sahna va Oqim Ma'lumotlari", "InSight • Scene & Stream Details", "Детали • Информация о потоке"),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                InSightBadge(
                                    label = tr("Janrlar", "Genres", "Жанры"),
                                    value = animeDetail?.displayGenres()?.replaceFirstChar { it.uppercase() }?.ifBlank { "Anime, Ekshn" } ?: "Anime, Ekshn",
                                    modifier = Modifier.weight(1f)
                                )
                                InSightBadge(
                                    label = tr("Video Sifati", "Video Quality", "Качество видео"),
                                    value = "Full HD 1080p • 60fps",
                                    modifier = Modifier.weight(1f)
                                )
                                InSightBadge(
                                    label = tr("Ovoz Studiyasi", "Audio Studio", "Студия озвучки"),
                                    value = "DarkMora O'zbekcha Dublyaj",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    "continue" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${tr("Qismlar ro'yxati", "Continue Watching", "Список серий")} (${episodes.size})",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (episodes.isEmpty()) {
                                Text(
                                    text = tr(
                                        "Hozircha faqat joriy $currentEpNum-qism mavjud.",
                                        "Currently only episode $currentEpNum is available.",
                                        "Сейчас доступна только $currentEpNum серия."
                                    ),
                                    color = Color(0xAAFFFFFF),
                                    fontSize = 13.sp
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(episodes) { ep ->
                                        val isCurrent = ep.qism == currentEpNum
                                        Box(
                                            modifier = Modifier
                                                .bounceClick { switchEpisode(ep) }
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isCurrent) Color.White else Color(0x44FFFFFF))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCurrent) Color.White else Color(0x28FFFFFF),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${ep.qism}-${tr("qism", "ep", "сер.")}",
                                                    color = if (isCurrent) Color.Black else Color.White,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isCurrent) tr("Ijroda", "Playing", "Играет") else tr("Ko'rish", "Watch", "Смотреть"),
                                                    color = if (isCurrent) Color(0xCC000000) else Color(0xAAFFFFFF),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "cast" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr(
                                            "TV ga ulash • Smart TV va Miracast",
                                            "Cast to TV • Smart TV & Miracast",
                                            "Подключить к ТВ • Smart TV и Miracast"
                                        ),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusSubtitle = when {
                                        connectedTv != null -> "${tr("Ulangan", "Connected", "Подключено")}: ${connectedTv!!.name} (${connectedTv!!.host})"
                                        isScanningTvs -> tr("Wi-Fi tarmoqdan Smart TV qidirilmoqda...", "Scanning Wi-Fi for Smart TVs...", "Поиск Smart TV в сети Wi-Fi...")
                                        discoveredTvs.isNotEmpty() -> "${discoveredTvs.size} ${tr("ta Smart TV topildi", "Smart TV(s) found", "Smart TV найдено")}"
                                        else -> tr("Bitta Wi-Fi dagi Smart TV topilmadi. Pastdagi usullardan foydalaning.", "No Wi-Fi Smart TV found. Use options below.", "Smart TV не найден в Wi-Fi. Используйте кнопки ниже.")
                                    }
                                    Text(
                                        text = statusSubtitle,
                                        color = if (connectedTv != null) PrimaryGlow else Color(0xAAFFFFFF),
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isScanningTvs || isCastingToTv) {
                                    CircularProgressIndicator(
                                        color = PrimaryGlow,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .bounceClick {
                                                tvScanTick++
                                                lastInteractionTick++
                                            }
                                            .clip(CircleShape)
                                            .background(Color(0x28FFFFFF))
                                            .border(0.8.dp, Color(0x33FFFFFF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = tr("Qayta qidirish", "Rescan", "Обновить"),
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            if (discoveredTvs.isNotEmpty() || connectedTv != null) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    connectedTv?.let { activeTv ->
                                        item(key = "disconnect_${activeTv.controlUrl}") {
                                            GlassBottomPill(
                                                text = tr("TV dan uzish", "Disconnect TV", "Отключить ТВ"),
                                                isSelected = true,
                                                onClick = {
                                                    scope.launch {
                                                        DlnaTvCastHelper.stopCast(activeTv)
                                                        LocalTvStreamProxy.stop()
                                                        connectedTv = null
                                                        Toast.makeText(
                                                            context,
                                                            tr("TV dan uzildi", "Disconnected from TV", "Отключено от ТВ"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    lastInteractionTick++
                                                }
                                            )
                                        }
                                    }

                                    items(discoveredTvs, key = { it.controlUrl }) { tv ->
                                        val isCurrentTv = connectedTv?.controlUrl == tv.controlUrl
                                        Row(
                                            modifier = Modifier
                                                .height(40.dp)
                                                .bounceClick {
                                                    if (isCastingToTv) return@bounceClick
                                                    if (currentVideoUrl.isBlank()) {
                                                        Toast.makeText(
                                                            context,
                                                            tr("Video manzili topilmadi", "Video URL not available", "URL видео не найден"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@bounceClick
                                                    }
                                                    scope.launch {
                                                        isCastingToTv = true
                                                        val (ok, errReason) = DlnaTvCastHelper.castVideo(tv, currentVideoUrl, currentTitle)
                                                        isCastingToTv = false
                                                        if (ok) {
                                                            connectedTv = tv
                                                            exoPlayer.pause()
                                                            activeBottomSheet = null
                                                            Toast.makeText(
                                                                context,
                                                                "${tv.name}: ${tr("TV ga uzatildi!", "Casting to TV!", "Трансляция на ТВ!")}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            val suffix = if (!errReason.isNullOrBlank()) " ($errReason)" else ""
                                                            Toast.makeText(
                                                                context,
                                                                "${tr("TV ga ulab bo'lmadi", "Could not cast to TV", "Не удалось отправить видео на ТВ")}$suffix",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                    lastInteractionTick++
                                                }
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isCurrentTv) Color.White else Color(0x33FFFFFF))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCurrentTv) Color.White else Color(0x28FFFFFF),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isCurrentTv) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = tv.name,
                                                    color = if (isCurrentTv) Color.Black else Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = tv.host,
                                                    color = if (isCurrentTv) Color(0xAA000000) else Color(0x99FFFFFF),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    GlassBottomPill(
                                        text = tr("Ekran ulashish (Smart View)", "Screen Mirroring (Cast)", "Трансляция экрана"),
                                        isSelected = false,
                                        onClick = {
                                            val intents = listOf(
                                                Intent(Settings.ACTION_CAST_SETTINGS),
                                                Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
                                                Intent(Settings.ACTION_WIFI_SETTINGS)
                                            )
                                            var launched = false
                                            for (intent in intents) {
                                                try {
                                                    context.startActivity(intent)
                                                    launched = true
                                                    break
                                                } catch (_: Exception) {}
                                            }
                                            if (!launched) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Telefon sozlamalaridan Smart View / Cast ni oching", "Open Cast / Smart View from phone settings", "Откройте трансляцию экрана в настройках"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            lastInteractionTick++
                                        }
                                    )
                                }

                                item {
                                    GlassBottomPill(
                                        text = tr("Tashqi Cast pleyer", "External Cast App", "Внешний Cast-плеер"),
                                        isSelected = false,
                                        onClick = {
                                            if (currentVideoUrl.isBlank()) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Video manzili topilmadi", "Video URL not available", "URL видео не найден"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@GlassBottomPill
                                            }
                                            try {
                                                val mimeType = if (currentVideoUrl.contains(".m3u8", ignoreCase = true)) {
                                                    "application/x-mpegURL"
                                                } else {
                                                    "video/*"
                                                }
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(currentVideoUrl), mimeType)
                                                    putExtra("title", currentTitle)
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        viewIntent,
                                                        tr("Cast pleyer tanlang", "Choose Cast App", "Выберите плеер")
                                                    )
                                                )
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    tr("Mos tashqi pleyer topilmadi", "No compatible external player found", "Совместимый внешний плеер не найден"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            lastInteractionTick++
                                        }
                                    )
                                }

                                item {
                                    GlassBottomPill(
                                        text = tr("Wi-Fi TV qidirish", "Scan Wi-Fi TV", "Поиск Wi-Fi ТВ"),
                                        isSelected = isScanningTvs,
                                        onClick = {
                                            tvScanTick++
                                            lastInteractionTick++
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "audio" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr("Audio va Tovush Kuchaytirgich", "Audio & Sound Enhancer", "Аудио и усиление звука"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassBottomPill(
                                    text = tr("Standart Stereo", "Standard Stereo", "Стандартное стерео"),
                                    isSelected = !audioBoostEnabled,
                                    onClick = {
                                        audioBoostEnabled = false
                                        exoPlayer.volume = volumeFraction
                                        activeBottomSheet = null
                                    }
                                )
                                GlassBottomPill(
                                    text = tr("Ovozni kuchaytirish", "Dialogue Boost", "Усиление голоса"),
                                    isSelected = audioBoostEnabled,
                                    onClick = {
                                        audioBoostEnabled = true
                                        exoPlayer.volume = 1.0f
                                        activeBottomSheet = null
                                    }
                                )
                            }
                        }
                    }

                    "speed" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr("Ijro Tezligi", "Playback Speed", "Скорость воспроизведения"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    GlassBottomPill(
                                        text = "${spd}x",
                                        isSelected = playbackSpeed == spd,
                                        onClick = {
                                            playbackSpeed = spd
                                            exoPlayer.playbackParameters = PlaybackParameters(spd)
                                            activeBottomSheet = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Buffering Indicator when controls are hidden
        if (isBuffering && !showControls && errorMessage == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.8.dp, modifier = Modifier.size(42.dp))
            }
        }

        // 6. Error Overlay
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD05060A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(GlassFill)
                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(24.dp))
                        .padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )

                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassBottomPill(
                            text = "Orqaga",
                            isSelected = false,
                            onClick = onBackClick
                        )

                        GlassBottomPill(
                            text = "Qayta yuklash",
                            isSelected = true,
                            onClick = {
                                errorMessage = null
                                isBuffering = true
                                retryKey++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassBottomPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) GlassActiveFill else GlassFill)
            .border(
                width = 1.dp,
                brush = if (isSelected) {
                    Brush.verticalGradient(listOf(Color.White, Color(0x66FFFFFF)))
                } else {
                    GlassBorderBrush
                },
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InSightBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x28FFFFFF))
            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, color = Color(0x99FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

data class DlnaTvDevice(
    val name: String,
    val host: String,
    val controlUrl: String,
    val locationUrl: String,
    val serviceType: String = "urn:schemas-upnp-org:service:AVTransport:1"
)

object LocalTvStreamProxy {
    private val generation = AtomicInteger(0)
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var currentUpstreamUrl: String = ""
    @Volatile
    private var running = false

    private val okHttp = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun startOrUpdate(upstreamUrl: String, targetTvHost: String): String? {
        if (upstreamUrl.isBlank()) return null
        val localIp = getLocalIpForTarget(targetTvHost) ?: return null
        currentUpstreamUrl = upstreamUrl

        var srv = serverSocket
        if (srv == null || srv.isClosed) {
            try {
                srv = ServerSocket(0)
                serverSocket = srv
                running = true
                val activeSrv = srv
                Thread {
                    while (running && !activeSrv.isClosed) {
                        try {
                            val client = activeSrv.accept()
                            Thread {
                                handleClient(client)
                            }.apply {
                                isDaemon = true
                                start()
                            }
                        } catch (_: Exception) {
                            if (!running) break
                        }
                    }
                }.apply {
                    isDaemon = true
                    name = "DarkMoraTvProxy"
                    start()
                }
            } catch (_: Exception) {
                return null
            }
        }

        val ext = if (upstreamUrl.contains(".m3u8", ignoreCase = true)) "m3u8" else "mp4"
        val gen = generation.incrementAndGet()
        return "http://$localIp:${srv.localPort}/darkmora_$gen.$ext"
    }

    @Synchronized
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun getLocalIpForTarget(targetHost: String): String? {
        return try {
            DatagramSocket().use { s ->
                s.connect(InetAddress.getByName(targetHost), 80)
                s.localAddress?.hostAddress?.takeIf { !it.isNullOrBlank() && it != "0.0.0.0" && it != "::" }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.use { sock ->
                sock.soTimeout = 15000
                val input = sock.getInputStream()
                val reqLine = readLine(input) ?: return
                val parts = reqLine.trim().split(Regex("\\s+"))
                val method = parts.getOrNull(0)?.uppercase(Locale.US) ?: "GET"

                var rangeHeader: String? = null
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val name = line.substring(0, idx).trim().lowercase(Locale.US)
                        val value = line.substring(idx + 1).trim()
                        if (name == "range") rangeHeader = value
                    }
                }

                val targetUrl = currentUpstreamUrl
                if (targetUrl.isBlank()) return

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 DarkMora/2.4")
                    .header("Referer", "https://darkmora.uz/")
                    .header("Origin", "https://darkmora.uz")

                if (!rangeHeader.isNullOrBlank()) {
                    reqBuilder.header("Range", rangeHeader)
                }
                if (method == "HEAD") {
                    reqBuilder.head()
                } else {
                    reqBuilder.get()
                }

                okHttp.newCall(reqBuilder.build()).execute().use { upstream ->
                    val statusText = upstream.message.ifBlank {
                        if (upstream.code == 206) "Partial Content" else "OK"
                    }
                    val contentType = upstream.header("Content-Type")
                        ?: if (targetUrl.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/mp4"
                    val contentLength = upstream.header("Content-Length")
                    val contentRange = upstream.header("Content-Range")

                    val headerBytes = buildString {
                        append("HTTP/1.1 ${upstream.code} $statusText\r\n")
                        append("Content-Type: $contentType\r\n")
                        append("Accept-Ranges: bytes\r\n")
                        if (!contentLength.isNullOrBlank()) {
                            append("Content-Length: $contentLength\r\n")
                        }
                        if (!contentRange.isNullOrBlank()) {
                            append("Content-Range: $contentRange\r\n")
                        }
                        append("transferMode.dlna.org: Streaming\r\n")
                        append("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    val out = sock.getOutputStream()
                    out.write(headerBytes)
                    out.flush()

                    if (method != "HEAD") {
                        val bodyStream = upstream.body?.byteStream()
                        if (bodyStream != null) {
                            val buf = ByteArray(32768)
                            while (running) {
                                val read = bodyStream.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                            }
                            out.flush()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Client closed connection or seeked to a new range
        }
    }

    private fun readLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            if (baos.size() > 8192) break
            b = input.read()
        }
        return baos.toString("UTF-8")
    }
}

object DlnaTvCastHelper {
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900

    private val LOCATION_REGEX = Regex("(?im)^LOCATION:\\s*(.+?)\\s*$")
    private val FRIENDLY_NAME_REGEX = Regex("<friendlyName[^>]*>(.*?)</friendlyName>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val URL_BASE_REGEX = Regex("<URLBase[^>]*>(.*?)</URLBase>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val SERVICE_BLOCK_REGEX = Regex("<service[^>]*>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val SERVICE_TYPE_REGEX = Regex("<serviceType[^>]*>(.*?)</serviceType>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CONTROL_URL_REGEX = Regex("<controlURL[^>]*>(.*?)</controlURL>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val UPNP_ERR_CODE_REGEX = Regex("<errorCode[^>]*>(.*?)</errorCode>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val UPNP_ERR_DESC_REGEX = Regex("<errorDescription[^>]*>(.*?)</errorDescription>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    suspend fun discoverDevices(): List<DlnaTvDevice> = withContext(Dispatchers.IO) {
        val locations = LinkedHashSet<String>()
        val searchTargets = listOf(
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        )

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 350
            }
            val group = InetAddress.getByName(SSDP_ADDRESS)

            for (st in searchTargets) {
                val query = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: $st\r\n")
                    append("\r\n")
                }.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(query, query.size, group, SSDP_PORT)
                try {
                    socket.send(packet)
                } catch (_: Exception) {}
            }

            val deadline = System.currentTimeMillis() + 1800L
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val respPacket = DatagramPacket(buf, buf.size)
                    socket.receive(respPacket)
                    val msg = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
                    val match = LOCATION_REGEX.find(msg)
                    val loc = match?.groupValues?.getOrNull(1)?.trim()
                    if (!loc.isNullOrBlank() && loc.startsWith("http://", ignoreCase = true)) {
                        locations.add(loc)
                    }
                } catch (_: Exception) {
                    // Timeout slice elapsed; keep listening until deadline
                }
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }

        if (locations.isEmpty()) return@withContext emptyList()

        val parsed = coroutineScope {
            locations.take(16).map { locationUrl ->
                async { parseDeviceFromLocation(locationUrl) }
            }.awaitAll().filterNotNull()
        }

        val uniqueByControl = LinkedHashMap<String, DlnaTvDevice>()
        for (dev in parsed) {
            uniqueByControl.putIfAbsent(dev.controlUrl, dev)
        }
        uniqueByControl.values.toList()
    }

    suspend fun castVideo(device: DlnaTvDevice, videoUrl: String, title: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (videoUrl.isBlank()) return@withContext Pair(false, "Bo'sh URL")

        val proxyUrl = LocalTvStreamProxy.startOrUpdate(videoUrl, device.host)
        val svc = device.serviceType.ifBlank { "urn:schemas-upnp-org:service:AVTransport:1" }
        val safeTitle = xmlEscape(title.ifBlank { "DarkMora Video" })

        // Best-effort Stop before loading new URI
        try {
            if (stopCast(device)) {
                delay(150)
            }
        } catch (_: Exception) {}

        fun buildDidl(targetUrl: String, fullDlnaFlags: Boolean): String {
            val escapedTarget = xmlEscape(targetUrl)
            val mime = if (targetUrl.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/mp4"
            val proto = if (fullDlnaFlags) {
                "http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            } else {
                "http-get:*:$mime:*"
            }
            return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                "<dc:title>$safeTitle</dc:title>" +
                "<upnp:class>object.item.videoItem</upnp:class>" +
                "<res protocolInfo=\"$proto\">$escapedTarget</res>" +
                "</item></DIDL-Lite>"
        }

        fun trySetUri(targetUrl: String, metaXml: String): Pair<Int, String>? {
            val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:SetAVTransportURI xmlns:u=\"$svc\">" +
                "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${xmlEscape(targetUrl)}</CurrentURI>" +
                "<CurrentURIMetaData>$metaXml</CurrentURIMetaData>" +
                "</u:SetAVTransportURI>" +
                "</s:Body>" +
                "</s:Envelope>"
            return httpRawRequest(
                urlStr = device.controlUrl,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "text/xml; charset=\"utf-8\"",
                    "SOAPAction" to "\"$svc#SetAVTransportURI\""
                ),
                body = body,
                readTimeoutMs = 12000
            )
        }

        // Progressive fallback cascade for maximum Smart TV compatibility:
        // 1) Local HTTP proxy + simple DIDL-Lite
        // 2) Local HTTP proxy + DLNA flags DIDL-Lite
        // 3) Local HTTP proxy + empty metadata
        // 4) Direct stream URL + simple DIDL-Lite (if router AP isolation blocks LAN proxy)
        // 5) Direct stream URL + empty metadata
        val attempts = mutableListOf<Pair<String, String>>()
        if (!proxyUrl.isNullOrBlank()) {
            attempts.add(Pair(proxyUrl, xmlEscape(buildDidl(proxyUrl, false))))
            attempts.add(Pair(proxyUrl, xmlEscape(buildDidl(proxyUrl, true))))
            attempts.add(Pair(proxyUrl, ""))
        }
        attempts.add(Pair(videoUrl, xmlEscape(buildDidl(videoUrl, false))))
        attempts.add(Pair(videoUrl, ""))

        var setRes: Pair<Int, String>? = null
        for ((candidateUrl, candidateMeta) in attempts) {
            setRes = trySetUri(candidateUrl, candidateMeta)
            if (setRes != null && setRes.first in 200..299) break
        }

        if (setRes == null) return@withContext Pair(false, "TV javob bermadi")
        if (setRes.first !in 200..299) {
            return@withContext Pair(false, formatUpnpFault("URI", setRes.first, setRes.second))
        }

        delay(450)

        val playBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:Play xmlns:u=\"$svc\">" +
            "<InstanceID>0</InstanceID>" +
            "<Speed>1</Speed>" +
            "</u:Play>" +
            "</s:Body>" +
            "</s:Envelope>"

        var playRes = httpRawRequest(
            urlStr = device.controlUrl,
            method = "POST",
            headers = mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPAction" to "\"$svc#Play\""
            ),
            body = playBody,
            readTimeoutMs = 8000
        )

        if (playRes == null || playRes.first !in 200..299) {
            delay(900)
            playRes = httpRawRequest(
                urlStr = device.controlUrl,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "text/xml; charset=\"utf-8\"",
                    "SOAPAction" to "\"$svc#Play\""
                ),
                body = playBody,
                readTimeoutMs = 8000
            )
        }

        if (playRes == null) return@withContext Pair(false, "Play javobsiz")
        if (playRes.first in 200..299 || playRes.second.contains("701")) {
            return@withContext Pair(true, null)
        }
        Pair(false, formatUpnpFault("Play", playRes.first, playRes.second))
    }

    private fun formatUpnpFault(stage: String, httpCode: Int, xmlBody: String): String {
        val code = UPNP_ERR_CODE_REGEX.find(xmlBody)?.groupValues?.getOrNull(1)?.trim()
        val desc = UPNP_ERR_DESC_REGEX.find(xmlBody)?.groupValues?.getOrNull(1)?.trim()
        return when {
            !code.isNullOrBlank() && !desc.isNullOrBlank() -> "$stage $httpCode ($code: $desc)"
            !code.isNullOrBlank() -> "$stage $httpCode (UPnP $code)"
            else -> "$stage HTTP $httpCode"
        }
    }

    suspend fun stopCast(device: DlnaTvDevice): Boolean = withContext(Dispatchers.IO) {
        val svc = device.serviceType.ifBlank { "urn:schemas-upnp-org:service:AVTransport:1" }
        val stopBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:Stop xmlns:u=\"$svc\">" +
            "<InstanceID>0</InstanceID>" +
            "</u:Stop>" +
            "</s:Body>" +
            "</s:Envelope>"

        val res = httpRawRequest(
            urlStr = device.controlUrl,
            method = "POST",
            headers = mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPAction" to "\"$svc#Stop\""
            ),
            body = stopBody,
            readTimeoutMs = 3000
        )
        res != null && res.first in 200..299
    }

    private fun parseDeviceFromLocation(locationUrl: String): DlnaTvDevice? {
        return try {
            val locUri = URI(locationUrl)
            val host = locUri.host ?: return null
            val res = httpRawRequest(locationUrl, "GET", emptyMap(), null, readTimeoutMs = 3000) ?: return null
            if (res.first !in 200..299 || res.second.isBlank()) return null

            val xml = res.second
            val avServiceBlock = SERVICE_BLOCK_REGEX.findAll(xml)
                .map { it.groupValues[1] }
                .firstOrNull { it.contains("AVTransport", ignoreCase = true) }
                ?: return null

            val rawControlUrl = CONTROL_URL_REGEX.find(avServiceBlock)?.groupValues?.getOrNull(1)?.trim()
                ?: return null
            if (rawControlUrl.isBlank()) return null

            val serviceType = SERVICE_TYPE_REGEX.find(avServiceBlock)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotBlank() } ?: "urn:schemas-upnp-org:service:AVTransport:1"

            val urlBase = URL_BASE_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            val resolvedControlUrl = URI(urlBase ?: locationUrl).resolve(rawControlUrl).toString()
            val ctrlHost = URI(resolvedControlUrl).host ?: return null
            if (!InetAddress.getByName(ctrlHost).isSiteLocalAddress) return null

            val rawName = FRIENDLY_NAME_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()
            val friendlyName = rawName?.let { xmlUnescape(it) }?.ifBlank { null } ?: "Smart TV ($host)"

            DlnaTvDevice(
                name = friendlyName,
                host = host,
                controlUrl = resolvedControlUrl,
                locationUrl = locationUrl,
                serviceType = serviceType
            )
        } catch (_: Exception) {
            null
        }
    }

    // Performs HTTP/1.1 strictly over raw TCP sockets restricted to private site-local LAN IPs,
    // allowing local DLNA Smart TV control while keeping usesCleartextTraffic="false" globally.
    private fun httpRawRequest(
        urlStr: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        readTimeoutMs: Int = 4000
    ): Pair<Int, String>? {
        return try {
            val uri = URI(urlStr)
            if (!uri.scheme.equals("http", ignoreCase = true)) return null
            val host = uri.host ?: return null
            val addr = InetAddress.getByName(host)
            if (!addr.isSiteLocalAddress) return null

            val port = if (uri.port > 0) uri.port else 80
            val path = buildString {
                append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
                if (!uri.rawQuery.isNullOrEmpty()) {
                    append("?")
                    append(uri.rawQuery)
                }
            }

            val bodyBytes = body?.toByteArray(Charsets.UTF_8)
            val requestBytes = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: $host${if (port != 80) ":$port" else ""}\r\n")
                append("User-Agent: DarkMora-Android/1.0 UPnP/1.1\r\n")
                append("Connection: close\r\n")
                for ((k, v) in headers) {
                    append("$k: $v\r\n")
                }
                if (bodyBytes != null) {
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("\r\n")
            }.toByteArray(Charsets.UTF_8)

            Socket().use { socket ->
                socket.connect(InetSocketAddress(addr, port), 2500)
                socket.soTimeout = readTimeoutMs

                val out = socket.getOutputStream()
                out.write(requestBytes)
                if (bodyBytes != null) {
                    out.write(bodyBytes)
                }
                out.flush()

                val input = socket.getInputStream()
                val statusLine = readLineFromStream(input) ?: return null
                val statusCode = statusLine.trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: return null

                var contentLength = -1
                var isChunked = false
                while (true) {
                    val headerLine = readLineFromStream(input) ?: break
                    if (headerLine.isEmpty()) break
                    val colonIdx = headerLine.indexOf(':')
                    if (colonIdx > 0) {
                        val name = headerLine.substring(0, colonIdx).trim().lowercase(Locale.US)
                        val value = headerLine.substring(colonIdx + 1).trim()
                        if (name == "content-length") {
                            contentLength = value.toIntOrNull() ?: -1
                        } else if (name == "transfer-encoding" && value.contains("chunked", ignoreCase = true)) {
                            isChunked = true
                        }
                    }
                }

                val bodyData = ByteArrayOutputStream()
                val maxRead = 262144
                if (isChunked) {
                    while (bodyData.size() < maxRead) {
                        val sizeLine = readLineFromStream(input) ?: break
                        val chunkLen = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
                        if (chunkLen <= 0) break
                        var remaining = min(chunkLen, maxRead - bodyData.size())
                        val buf = ByteArray(min(4096, remaining))
                        while (remaining > 0) {
                            val read = input.read(buf, 0, min(buf.size, remaining))
                            if (read <= 0) break
                            bodyData.write(buf, 0, read)
                            remaining -= read
                        }
                        readLineFromStream(input) // Trailing CRLF after chunk
                    }
                } else if (contentLength >= 0) {
                    var remaining = min(contentLength, maxRead)
                    val buf = ByteArray(4096)
                    while (remaining > 0) {
                        val read = input.read(buf, 0, min(buf.size, remaining))
                        if (read <= 0) break
                        bodyData.write(buf, 0, read)
                        remaining -= read
                    }
                } else {
                    val buf = ByteArray(4096)
                    while (bodyData.size() < maxRead) {
                        val read = input.read(buf, 0, min(buf.size, maxRead - bodyData.size()))
                        if (read <= 0) break
                        bodyData.write(buf, 0, read)
                    }
                }

                Pair(statusCode, bodyData.toString("UTF-8"))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readLineFromStream(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            if (baos.size() > 8192) break
            b = input.read()
        }
        return baos.toString("UTF-8")
    }

    private fun xmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun xmlUnescape(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
