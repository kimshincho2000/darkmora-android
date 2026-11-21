package uz.darkmora.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Episode
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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
    val scope = rememberCoroutineScope()

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
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }

    // Fetch episodes list in background if slug is provided so Next/Prev & Episodes drawer work inside player
    LaunchedEffect(slug) {
        if (slug.isNotBlank()) {
            sessionManager.saveLastWatchedEpisode(slug, currentEpNum)
            try {
                val res = DarkmoraApi.getAnimeDetail(slug)
                if (res.ok && res.data != null) {
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

    // Custom UI Overlay States
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var seekIndicatorText by remember { mutableStateOf<String?>(null) }
    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
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
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 DarkMora/2.2")
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

    // Load media source whenever currentVideoUrl changes
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

    // Update progress timer every 500ms
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            currentPosition = max(0L, exoPlayer.currentPosition)
            bufferedPosition = max(0L, exoPlayer.bufferedPosition)
            val d = exoPlayer.duration
            if (d > 0) duration = d
            delay(500)
        }
    }

    // Auto-hide controls after 3.8 seconds of inactivity while playing
    LaunchedEffect(showControls, isPlaying, lastInteractionTick, isUserDraggingSlider, showEpisodesDrawer) {
        if (showControls && isPlaying && !isUserDraggingSlider && !showEpisodesDrawer) {
            delay(3800)
            showControls = false
        }
    }

    // Auto-clear double-tap seek indicator
    LaunchedEffect(seekIndicatorText) {
        if (seekIndicatorText != null) {
            delay(850)
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
        showEpisodesDrawer = false
        lastInteractionTick++
    }

    val currentEpIndex = episodes.indexOfFirst { it.qism == currentEpNum }
    val hasPrevEp = currentEpIndex > 0
    val hasNextEp = currentEpIndex >= 0 && currentEpIndex < episodes.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked, showEpisodesDrawer) {
                detectTapGestures(
                    onTap = {
                        if (showEpisodesDrawer) {
                            showEpisodesDrawer = false
                        } else {
                            showControls = !showControls
                            lastInteractionTick++
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        val width = size.width
                        when {
                            offset.x < width * 0.35f -> {
                                val target = max(0L, exoPlayer.currentPosition - 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "⏪ -10 soniya"
                            }
                            offset.x > width * 0.65f -> {
                                val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = min(maxDur, exoPlayer.currentPosition + 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "⏩ +10 soniya"
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
        // 1. Video Surface (with stock controller DISABLED so our Liquid Glass UI is in full control)
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

        // 2. Double-Tap Seek Floating Feedback Pill
        AnimatedVisibility(
            visible = seekIndicatorText != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xCC0D1220))
                    .border(1.dp, PrimaryGlow, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = seekIndicatorText ?: "",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 3. Locked Screen Floating Pill
        if (isLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .bounceClick {
                            isLocked = false
                            lastInteractionTick++
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xDD0D111E))
                        .border(1.dp, PrimaryGlow, RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = PrimaryGlow, modifier = Modifier.size(18.dp))
                    Text("Ekran qulflangan • Ochish uchun bosing", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Full Custom Liquid Glass Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isLocked,
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
                                Color(0xCC05070D),
                                Color(0x3305070D),
                                Color(0x3305070D),
                                Color(0xDD05070D)
                            )
                        )
                    )
            ) {
                // --- TOP LIQUID GLASS BAR ---
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .bounceClick(onClick = onBackClick)
                                .clip(CircleShape)
                                .background(Color(0xBF101424))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Orqaga", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        Column {
                            Text(
                                text = currentTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "DarkMora Cinema • HD Oqim",
                                color = PrimaryGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Top Right Action Pills (Episodes, Speed, AspectRatio, Lock)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Episodes list button (if episodes available)
                        if (episodes.isNotEmpty()) {
                            GlassPlayerChip(
                                icon = Icons.Default.PlaylistPlay,
                                label = "Qismlar (${episodes.size})",
                                onClick = {
                                    showEpisodesDrawer = true
                                    lastInteractionTick++
                                }
                            )
                        }

                        // Speed Toggle Chip
                        GlassPlayerChip(
                            icon = Icons.Default.Speed,
                            label = "${playbackSpeed}x",
                            onClick = {
                                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                playbackSpeed = speeds[nextIdx]
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                lastInteractionTick++
                            }
                        )

                        // Aspect Ratio Toggle Chip
                        val resizeLabel = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "To'liq"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Cho'zish"
                            else -> "Asl"
                        }
                        GlassPlayerChip(
                            icon = Icons.Default.AspectRatio,
                            label = resizeLabel,
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                lastInteractionTick++
                            }
                        )

                        // Lock Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .bounceClick {
                                    isLocked = true
                                    lastInteractionTick++
                                }
                                .clip(CircleShape)
                                .background(Color(0xBF101424))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // --- CENTER PLAYBACK CONTROLS ---
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Previous Episode
                    if (episodes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .bounceClick {
                                    if (hasPrevEp) switchEpisode(episodes[currentEpIndex - 1])
                                }
                                .clip(CircleShape)
                                .background(Color(0x99101424))
                                .border(1.dp, if (hasPrevEp) Color(0x33FFFFFF) else Color(0x11FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Oldingi qism",
                                tint = if (hasPrevEp) Color.White else Color(0x44FFFFFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Rewind 10s
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .bounceClick {
                                val target = max(0L, exoPlayer.currentPosition - 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "⏪ -10s"
                                lastInteractionTick++
                            }
                            .clip(CircleShape)
                            .background(Color(0xBF101424))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    // Play / Pause Main Glowing Liquid Glass Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .bounceClick {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteractionTick++
                            }
                            .shadow(20.dp, CircleShape, spotColor = PrimaryGlow)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PrimaryBlue, Color(0xFF1D4ED8))
                                )
                            )
                            .border(1.5.dp, Color(0x8838BDF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Forward 10s
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .bounceClick {
                                val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = min(maxDur, exoPlayer.currentPosition + 10_000L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                seekIndicatorText = "⏩ +10s"
                                lastInteractionTick++
                            }
                            .clip(CircleShape)
                            .background(Color(0xBF101424))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    // Next Episode
                    if (episodes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .bounceClick {
                                    if (hasNextEp) switchEpisode(episodes[currentEpIndex + 1])
                                }
                                .clip(CircleShape)
                                .background(Color(0x99101424))
                                .border(1.dp, if (hasNextEp) Color(0x33FFFFFF) else Color(0x11FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Keyingi qism",
                                tint = if (hasNextEp) Color.White else Color(0x44FFFFFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // --- BOTTOM SEEKBAR & QUICK ACTIONS ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Quick Row: Skip OP (+85s) & Next Episode shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Opening (+85s) button
                        Row(
                            modifier = Modifier
                                .bounceClick {
                                    val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                    val target = min(maxDur, exoPlayer.currentPosition + 85_000L)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    seekIndicatorText = "⏩ OP O'tkazildi (+85s)"
                                    lastInteractionTick++
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xCC121728))
                                .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = PrimaryGlow, modifier = Modifier.size(16.dp))
                            Text("OP O'tkazish (+85s)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (hasNextEp) {
                            val nextEpObj = episodes[currentEpIndex + 1]
                            Row(
                                modifier = Modifier
                                    .bounceClick { switchEpisode(nextEpObj) }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryBlue)
                                    .border(1.dp, PrimaryGlow, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Keyingi: ${nextEpObj.qism}-qism", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time & Neon Slider Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val displayPos = if (isUserDraggingSlider) sliderDragPosition.toLong() else currentPosition
                        Text(
                            text = formatDurationMs(displayPos),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Slider(
                            value = if (isUserDraggingSlider) sliderDragPosition else currentPosition.toFloat(),
                            onValueChange = { newVal ->
                                isUserDraggingSlider = true
                                sliderDragPosition = newVal
                                lastInteractionTick++
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(sliderDragPosition.toLong())
                                currentPosition = sliderDragPosition.toLong()
                                isUserDraggingSlider = false
                                lastInteractionTick++
                            },
                            valueRange = 0f..max(1f, duration.toFloat()),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryGlow,
                                activeTrackColor = PrimaryBlue,
                                inactiveTrackColor = Color(0x44FFFFFF)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = formatDurationMs(duration),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 5. Buffering Indicator when controls are hidden
        if (isBuffering && !showControls && errorMessage == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGlow, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
        }

        // 6. In-Player Liquid Glass Episodes Drawer
        AnimatedVisibility(
            visible = showEpisodesDrawer,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(290.dp)
                    .background(Color(0xF20B0E1A))
                    .border(1.dp, Color(0x28FFFFFF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Qismlar (${episodes.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(onClick = { showEpisodesDrawer = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Yopish", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(episodes) { ep ->
                            val isCurrent = ep.qism == currentEpNum
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) PrimaryBlue else Color(0xFF14192B))
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) PrimaryGlow else Color(0x1FFFFFFF),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { switchEpisode(ep) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color.White else PrimaryGlow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${ep.qism}-qism",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isCurrent) {
                                    Text(
                                        text = "Ijroda",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Error Overlay
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
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF101424))
                        .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                        .padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = RedBadge,
                        modifier = Modifier.size(42.dp)
                    )

                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Orqaga", color = TextMuted)
                        }

                        Button(
                            onClick = {
                                errorMessage = null
                                isBuffering = true
                                retryKey++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Qayta yuklash", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPlayerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xBF101424))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = PrimaryGlow, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
