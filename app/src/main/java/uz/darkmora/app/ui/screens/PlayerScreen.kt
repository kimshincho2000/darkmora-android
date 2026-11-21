package uz.darkmora.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.Episode
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import java.util.Locale
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
    var activeBottomSheet by remember { mutableStateOf<String?>(null) } // "info", "insight", "continue", "subtitles", "audio", "speed"
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioBoostEnabled by remember { mutableStateOf(false) }
    var subtitleState by remember { mutableStateOf("O'zbekcha (Original)") }
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
            val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
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

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
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
                // 1. Left: Episode Subtitle + Bold Anime Title | Right: [Subtitles | AudioWave | Speed] Capsule
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

                        // Right: 3-Icon Capsule [Subtitles 💬] [Audio Wave  Equalizer] [Speedometer ⏱️]
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
                            // Icon 1: Subtitles / Captions
                            Icon(
                                imageVector = Icons.Outlined.Subtitles,
                                contentDescription = "Subtitrlar",
                                tint = if (activeBottomSheet == "subtitles") PrimaryGlow else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .bounceClick {
                                        activeBottomSheet = if (activeBottomSheet == "subtitles") null else "subtitles"
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

        // 4. Interactive Liquid Glass Popover Card for [Info / InSight / Continue Watching / Subtitles / Audio / Speed]
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

                    "subtitles" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr("Subtitr va Yozuvlar", "Subtitles & Captions", "Субтитры"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("O'zbekcha (Original)", "O'chirilgan").forEach { opt ->
                                    val selected = subtitleState == opt
                                    GlassBottomPill(
                                        text = opt,
                                        isSelected = selected,
                                        onClick = {
                                            subtitleState = opt
                                            activeBottomSheet = null
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
