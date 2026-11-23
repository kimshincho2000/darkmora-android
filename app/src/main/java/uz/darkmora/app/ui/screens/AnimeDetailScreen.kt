package uz.darkmora.app.ui.screens

import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.AnimeComment
import uz.darkmora.app.data.Episode
import uz.darkmora.app.data.OfflineDownloadManager
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.ShimmerBox
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimeDetailScreen(
    slug: String,
    onBackClick: () -> Unit,
    onPlayEpisode: (String, String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }

    var anime by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var lastWatchedEp by remember { mutableIntStateOf(sessionManager.getLastWatchedEpisode(slug)) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingEpisodeToPlay by remember { mutableStateOf<Pair<Anime, Episode>?>(null) }

    // Minimal panel state: 0 = Qismlar, 1 = Izohlar
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGridMode by remember { mutableStateOf(false) }
    var deleteConfirmEp by remember { mutableStateOf<Int?>(null) }

    // Comments state
    var comments by remember { mutableStateOf<List<AnimeComment>>(emptyList()) }
    var isLoadingComments by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var isSendingComment by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        OfflineDownloadManager.init(context)
    }

    LaunchedEffect(slug) {
        lastWatchedEp = sessionManager.getLastWatchedEpisode(slug)
        scope.launch {
            try {
                val res = DarkmoraApi.getAnimeDetail(slug, token = sessionManager.getToken())
                if (res.ok && res.data != null) {
                    anime = res.data.anime
                    episodes = res.data.episodes ?: emptyList()
                    if (anime != null) {
                        isFavorite = sessionManager.isFavorite(anime!!.id)
                        isLoadingComments = true
                        try {
                            val cRes = DarkmoraApi.getComments(anime!!.id, 60)
                            if (cRes.ok && cRes.data != null) {
                                comments = cRes.data.comments ?: emptyList()
                            }
                        } catch (_: Exception) {
                        } finally {
                            isLoadingComments = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun openEpisodePlayer(a: Anime, ep: Episode) {
        val localEp = OfflineDownloadManager.getDownloadedEpisode(a.getEffectiveSlug(), ep.qism)
        val localFile = localEp?.filePath?.let { File(it) }
        val rawUrl = if (localFile != null && localFile.exists() && localFile.length() > 0L) {
            Uri.fromFile(localFile).toString()
        } else {
            ep.videoUrl ?: ep.embedUrl ?: ""
        }
        val rawTitle = "${a.displayTitle()} - ${ep.qism}-qism"
        val b64Url = Base64.encodeToString(rawUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val b64Title = Base64.encodeToString(rawTitle.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        sessionManager.saveLastWatchedEpisode(slug, ep.qism)
        lastWatchedEp = ep.qism
        onPlayEpisode(b64Url, b64Title, slug, ep.qism)
    }

    fun launchEpisode(a: Anime, ep: Episode) {
        val localEp = OfflineDownloadManager.getDownloadedEpisode(a.getEffectiveSlug(), ep.qism)
        val hasLocal = localEp != null && File(localEp.filePath).exists()
        if (!hasLocal && !sessionManager.isLoggedIn()) {
            pendingEpisodeToPlay = Pair(a, ep)
            showAuthDialog = true
            Toast.makeText(
                context,
                tr(
                    "Anime ko'rish uchun avval ro'yxatdan o'ting yoki kiring",
                    "Please register or sign in to watch anime",
                    "Войдите или зарегистрируйтесь для просмотра аниме"
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        openEpisodePlayer(a, ep)
    }

    fun handleDownloadClick(a: Anime, ep: Episode) {
        val effectiveSlug = a.getEffectiveSlug()
        val key = OfflineDownloadManager.keyOf(effectiveSlug, ep.qism)
        val active = OfflineDownloadManager.activeDownloads[key]
        if (active != null) {
            OfflineDownloadManager.cancelDownload(effectiveSlug, ep.qism)
            Toast.makeText(
                context,
                tr("Yuklab olish bekor qilindi", "Download cancelled", "Загрузка отменена"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (OfflineDownloadManager.isDownloaded(effectiveSlug, ep.qism)) {
            deleteConfirmEp = ep.qism
            return
        }
        if (!sessionManager.isLoggedIn()) {
            showAuthDialog = true
            Toast.makeText(
                context,
                tr(
                    "Yuklab olish uchun avval hisobingizga kiring",
                    "Please sign in to download episodes",
                    "Войдите в аккаунт, чтобы скачивать серии"
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        OfflineDownloadManager.startDownload(context, a, ep) { _, msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun submitComment(a: Anime) {
        val text = commentDraft.trim()
        if (text.isEmpty() || isSendingComment) return
        val tok = sessionManager.getToken()
        if (tok.isNullOrBlank()) {
            showAuthDialog = true
            Toast.makeText(
                context,
                tr(
                    "Izoh yozish uchun avval tizimga kiring",
                    "Please sign in to post a comment",
                    "Войдите, чтобы оставить комментарий"
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        isSendingComment = true
        scope.launch {
            try {
                val res = DarkmoraApi.addComment(tok, a.id, text)
                if (res.ok && res.data != null) {
                    comments = listOf(res.data) + comments
                    commentDraft = ""
                } else {
                    Toast.makeText(
                        context,
                        res.error ?: tr("Izoh yuborishda xatolik", "Failed to post comment", "Ошибка отправки"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message ?: tr("Tarmoq xatoligi", "Network error", "Ошибка сети"),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isSendingComment = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (isLoading) {
            Column(modifier = Modifier.fillMaxSize()) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    cornerRadius = 0.dp
                )
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(28.dp), cornerRadius = 10.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(20.dp), cornerRadius = 10.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(95.dp), cornerRadius = 18.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(52.dp), cornerRadius = 50.dp)
                }
            }
        } else if (anime != null) {
            val a = anime!!
            val effectiveSlug = a.getEffectiveSlug()

            val chunkSize = 30
            val chunks = remember(episodes) { episodes.chunked(chunkSize) }
            var selectedChunkIndex by remember { mutableIntStateOf(0) }
            val visibleEpisodes = if (chunks.isNotEmpty()) chunks.getOrElse(selectedChunkIndex) { episodes } else emptyList()

            val downloadedForThisAnime = OfflineDownloadManager.downloadedAnimes
                .firstOrNull { it.slug == effectiveSlug }
                ?.episodes
                ?.associateBy { it.qism }
                ?: emptyMap()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // ============================================================
                // 1. CLEAN V0.3.20 HERO BACKDROP + POSTER + META
                // ============================================================
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        AsyncImage(
                            model = a.banner ?: a.displayPoster(),
                            contentDescription = a.displayTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            BgDark.copy(alpha = 0.45f),
                                            BgDark.copy(alpha = 0.65f),
                                            BgDark.copy(alpha = 0.92f),
                                            BgDark
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .bounceClick(onClick = onBackClick)
                                    .clip(CircleShape)
                                    .background(BgCard)
                                    .border(0.8.dp, CardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Orqaga", tint = TextPrimary, modifier = Modifier.size(20.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .bounceClick {
                                        isFavorite = sessionManager.toggleFavoriteAnime(a)
                                        val tok = sessionManager.getToken()
                                        if (!tok.isNullOrBlank()) {
                                            scope.launch {
                                                try {
                                                    DarkmoraApi.toggleBookmark(tok, a.id)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        val msg = if (isFavorite) {
                                            tr("Sevimlilarga qo'shildi", "Added to Favorites", "Добавлено в избранное")
                                        } else {
                                            tr("Sevimlilardan olib tashlandi", "Removed from Favorites", "Удалено из избранного")
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    .clip(CircleShape)
                                    .background(if (isFavorite) PrimaryBlue else BgCard)
                                    .border(0.8.dp, if (isFavorite) PrimaryBlue else CardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Sevimlilar",
                                    tint = if (isFavorite) Color.White else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            AsyncImage(
                                model = a.displayPoster(),
                                contentDescription = a.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BgCard)
                                    .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = a.displayTitle(),
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 25.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(BgCard)
                                            .border(0.8.dp, CardBorder, RoundedCornerShape(50))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = a.displayRating(),
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text("•", color = TextDim, fontSize = 12.sp)
                                    Text(
                                        text = a.displayYear(),
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("•", color = TextDim, fontSize = 12.sp)
                                    Text(
                                        text = "${episodes.size} ${tr("qism", "ep", "сер.")}",
                                        color = PrimaryBlue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val genres = a.displayGenres()
                                if (genres.isNotEmpty()) {
                                    Text(
                                        text = genres.replaceFirstChar { it.uppercase() },
                                        color = TextMuted,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 2. DESCRIPTION + PLAY BUTTON + MINIMAL TAB BAR
                // ============================================================
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(BgCard)
                                .border(0.8.dp, CardBorder, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = a.displayDescription(),
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (episodes.isNotEmpty()) {
                            val targetEp = episodes.find { it.qism == lastWatchedEp } ?: episodes.first()
                            val buttonLabel = if (lastWatchedEp > 0) {
                                tr("${targetEp.qism}-Qismni Davom Ettirish", "Continue Episode ${targetEp.qism}", "Продолжить ${targetEp.qism} серию")
                            } else {
                                tr("1-Qismni Boshlash", "Start Episode 1", "Начать 1 серию")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick { launchEpisode(a, targetEp) }
                                    .clip(RoundedCornerShape(50))
                                    .background(TextPrimary)
                                    .padding(vertical = 15.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BgSurface, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buttonLabel,
                                    color = BgSurface,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Ultra-Clean Minimalist Header Bar: [Qismlar]  [Izohlar]      [List/Grid]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MinimalPillTab(
                                    selected = selectedTab == 0,
                                    label = tr("Qismlar", "Episodes", "Серии"),
                                    count = episodes.size,
                                    onClick = { selectedTab = 0 }
                                )
                                MinimalPillTab(
                                    selected = selectedTab == 1,
                                    label = tr("Izohlar", "Comments", "Отзывы"),
                                    count = comments.size,
                                    onClick = { selectedTab = 1 }
                                )
                            }

                            if (selectedTab == 0) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .bounceClick { isGridMode = !isGridMode }
                                        .clip(CircleShape)
                                        .background(BgCard)
                                        .border(0.8.dp, CardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGridMode) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = "Toggle Layout",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (selectedTab == 0 && chunks.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(chunks.indices.toList()) { idx ->
                                    val c = chunks[idx]
                                    val firstNum = c.first().qism
                                    val lastNum = c.last().qism
                                    val isSelected = selectedChunkIndex == idx
                                    Box(
                                        modifier = Modifier
                                            .bounceClick { selectedChunkIndex = idx }
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) TextPrimary else BgCard)
                                            .border(
                                                width = 0.8.dp,
                                                color = if (isSelected) TextPrimary else CardBorder,
                                                shape = RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "$firstNum – $lastNum",
                                            color = if (isSelected) BgSurface else TextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 3A. EPISODES TAB — MINIMALIST DARKMORA LIST OR GRID
                // ============================================================
                if (selectedTab == 0) {
                    if (!isGridMode) {
                        items(visibleEpisodes, key = { "ep_list_${it.qism}" }) { ep ->
                            val isLastWatched = ep.qism == lastWatchedEp
                            val downloadedEp = downloadedForThisAnime[ep.qism]
                            val isDownloaded = downloadedEp != null
                            val activeDl = OfflineDownloadManager.activeDownloads[OfflineDownloadManager.keyOf(effectiveSlug, ep.qism)]

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp)
                                    .bounceClick { launchEpisode(a, ep) }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isLastWatched) PrimaryBlue.copy(alpha = 0.10f) else BgCard)
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isLastWatched) PrimaryBlue.copy(alpha = 0.40f) else CardBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Minimalist Episode Circle
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (isLastWatched) TextPrimary else BgSurface)
                                                .border(0.7.dp, if (isLastWatched) TextPrimary else CardBorder, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLastWatched) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = BgSurface,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = ep.qism.toString().padStart(2, '0'),
                                                    color = TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${ep.qism}-${tr("qism", "episode", "серия")}",
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            when {
                                                activeDl != null -> {
                                                    val pct = (activeDl.progress * 100).toInt()
                                                    Text(
                                                        text = "${tr("Yuklanmoqda", "Downloading", "Загрузка")} • $pct%",
                                                        color = RatingGold,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                isDownloaded -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(GreenOnline)
                                                        )
                                                        Text(
                                                            text = "${tr("Oflayn", "Offline", "Офлайн")} • ${OfflineDownloadManager.formatFileSize(downloadedEp!!.sizeBytes)}",
                                                            color = TextMuted,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                isLastWatched -> {
                                                    Text(
                                                        text = tr("Oxirgi ko'rilgan", "Last watched", "Последний просмотр"),
                                                        color = PrimaryBlue,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                else -> {
                                                    Text(
                                                        text = a.displayTitle(),
                                                        color = TextDim,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Single Clean Download Action (Petal Vortex while downloading!)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .bounceClick { handleDownloadClick(a, ep) }
                                            .clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            activeDl != null -> {
                                                PetalVortexLoader(size = 38.dp)
                                            }
                                            isDownloaded -> {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Offline",
                                                    tint = GreenOnline,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Outlined.DownloadForOffline,
                                                    contentDescription = tr("Yuklab olish", "Download", "Скачать"),
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Sleek razor-thin bottom progress line while downloading
                                if (activeDl != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(DividerTone)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(activeDl.progress.coerceIn(0.03f, 1f))
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(RatingGold, TextPrimary)
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Ultra-Clean 4-Column Minimalist Grid
                        val rows = visibleEpisodes.chunked(4)
                        items(rows.indices.toList(), key = { "ep_grid_row_$it" }) { rowIdx ->
                            val rowEps = rows[rowIdx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowEps.forEach { ep ->
                                    val isLastWatched = ep.qism == lastWatchedEp
                                    val isDownloaded = downloadedForThisAnime.containsKey(ep.qism)
                                    val activeDl = OfflineDownloadManager.activeDownloads[OfflineDownloadManager.keyOf(effectiveSlug, ep.qism)]

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .bounceClick { launchEpisode(a, ep) }
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isLastWatched) TextPrimary else BgCard)
                                            .border(
                                                width = 0.8.dp,
                                                color = if (isLastWatched) TextPrimary else CardBorder,
                                                shape = RoundedCornerShape(14.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (activeDl != null) {
                                            PetalVortexLoader(size = 34.dp)
                                        } else {
                                            Text(
                                                text = ep.qism.toString().padStart(2, '0'),
                                                color = if (isLastWatched) BgSurface else TextPrimary,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (isDownloaded && activeDl == null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(GreenOnline)
                                            )
                                        }
                                    }
                                }
                                repeat(4 - rowEps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 3B. COMMENTS TAB — CLEAN MINIMALIST COMPOSER + GROUPED LIST
                // ============================================================
                if (selectedTab == 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(BgCard)
                                .border(0.8.dp, CardBorder, RoundedCornerShape(24.dp))
                                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (commentDraft.isEmpty()) {
                                    Text(
                                        text = tr("Izoh yozing...", "Write a comment...", "Напишите отзыв..."),
                                        color = TextDim,
                                        fontSize = 13.5.sp
                                    )
                                }
                                BasicTextField(
                                    value = commentDraft,
                                    onValueChange = { if (it.length <= 500) commentDraft = it },
                                    textStyle = TextStyle(
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp
                                    ),
                                    cursorBrush = SolidColor(TextPrimary),
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .bounceClick { submitComment(a) }
                                    .clip(CircleShape)
                                    .background(if (commentDraft.isNotBlank()) TextPrimary else BgSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSendingComment) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = BgSurface,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = tr("Yuborish", "Send", "Отправить"),
                                        tint = if (commentDraft.isNotBlank()) BgSurface else TextDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (isLoadingComments) {
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ShimmerBox(modifier = Modifier.fillMaxWidth().height(64.dp), cornerRadius = 16.dp)
                                ShimmerBox(modifier = Modifier.fillMaxWidth().height(64.dp), cornerRadius = 16.dp)
                            }
                        }
                    } else if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BgCard)
                                    .border(0.8.dp, CardBorder, RoundedCornerShape(18.dp))
                                    .padding(vertical = 26.dp, horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = TextDim,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Text(
                                        text = tr("Hozircha izohlar yo'q", "No comments yet", "Комментариев пока нет"),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = tr("Birinchi bo'lib fikr bildiring", "Be the first to share your thoughts", "Оставьте первый отзыв"),
                                        color = TextDim,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BgCard)
                                    .border(0.8.dp, CardBorder, RoundedCornerShape(20.dp))
                            ) {
                                comments.forEachIndexed { idx, c ->
                                    MinimalCommentRow(comment = c)
                                    if (idx < comments.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.6.dp,
                                            color = DividerTone
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAuthDialog) {
            AuthDialog(
                onDismiss = {
                    showAuthDialog = false
                    pendingEpisodeToPlay = null
                },
                onLoginSuccess = {
                    showAuthDialog = false
                    pendingEpisodeToPlay?.let { (a, ep) ->
                        pendingEpisodeToPlay = null
                        openEpisodePlayer(a, ep)
                    }
                }
            )
        }

        if (deleteConfirmEp != null && anime != null) {
            val qismToDelete = deleteConfirmEp!!
            val effectiveSlug = anime!!.getEffectiveSlug()
            AlertDialog(
                onDismissRequest = { deleteConfirmEp = null },
                containerColor = BgSurface,
                title = {
                    Text(
                        text = tr("Yuklangan qism", "Downloaded Episode", "Скачанная серия"),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = tr(
                            "$qismToDelete-qism oflayn xotirada saqlangan. Uni o'chirmoqchimisiz?",
                            "Episode $qismToDelete is saved offline. Remove it from device?",
                            "$qismToDelete серия сохранена офлайн. Удалить с устройства?"
                        ),
                        color = TextMuted
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            OfflineDownloadManager.deleteEpisode(context, effectiveSlug, qismToDelete)
                            deleteConfirmEp = null
                            Toast.makeText(
                                context,
                                tr("$qismToDelete-qism o'chirildi", "Episode $qismToDelete deleted", "$qismToDelete серия удалена"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(tr("O'chirish", "Delete", "Удалить"), color = RedBadge, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmEp = null }) {
                        Text(tr("Yopish", "Close", "Закрыть"), color = TextMuted)
                    }
                }
            )
        }
    }
}

/**
 * Custom 60fps Petal-Arc Vortex Download Progress Animation
 * Modeled after the luminous 6-petal rotating + breathing crescent-arc animation.
 */
@Composable
fun PetalVortexLoader(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val petalBase = TextPrimary
    val petalGold = RatingGold

    val infiniteTransition = rememberInfiniteTransition(label = "petal_vortex")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vortex_rot"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vortex_pulse"
    )

    val arcWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vortex_arc"
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val maxR = this.size.minDimension / 2f
        val count = 6

        // 1. Outer sweeping golden crescent trails radiating outward
        val arcRadius = maxR * (0.44f + 0.48f * arcWave)
        val arcAlpha = (sin(arcWave * PI).toFloat()).coerceIn(0f, 1f) * 0.85f
        val strokeW = (maxR * 0.085f).coerceAtLeast(1.2f)

        for (i in 0 until count) {
            val baseAngle = rotation * 0.85f + i * 60f + arcWave * 38f
            val sweep = 36f - 12f * arcWave
            drawArc(
                color = petalGold.copy(alpha = arcAlpha),
                startAngle = baseAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }

        // 2. Inner 6 orbiting & breathing luminous petals (alternating white & gold)
        val petalOrbit = maxR * (0.24f + 0.12f * pulse)
        val petalW = maxR * 0.24f
        val petalH = maxR * 0.13f

        for (i in 0 until count) {
            val angleDeg = rotation + i * 60f
            val rad = angleDeg * (PI / 180.0)
            val px = cx + (cos(rad) * petalOrbit).toFloat()
            val py = cy + (sin(rad) * petalOrbit).toFloat()

            // Smooth gold highlight travelling around the 6 petals
            val highlightPhase = ((rotation / 60f) + i) % 6f
            val goldFactor = if (highlightPhase < 1.5f) {
                (1f - highlightPhase / 1.5f).coerceIn(0f, 1f)
            } else {
                0f
            }
            val color = lerp(petalBase, petalGold, goldFactor)
            val scale = 1f + 0.18f * goldFactor

            rotate(degrees = angleDeg + 22f, pivot = Offset(px, py)) {
                drawOval(
                    color = color,
                    topLeft = Offset(px - (petalW * scale) / 2f, py - (petalH * scale) / 2f),
                    size = Size(petalW * scale, petalH * scale)
                )
            }
        }
    }
}

@Composable
private fun MinimalPillTab(
    selected: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) TextPrimary else BgCard)
            .border(0.8.dp, if (selected) TextPrimary else CardBorder, RoundedCornerShape(50))
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) BgSurface else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            color = if (selected) BgSurface.copy(alpha = 0.72f) else TextDim,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MinimalCommentRow(comment: AnimeComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!comment.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = comment.photoUrl,
                contentDescription = comment.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BgSurface)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BgSurface)
                    .border(0.8.dp, CardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.username.firstOrNull()?.uppercase() ?: "U",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.username,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    color = TextDim,
                    fontSize = 11.sp
                )
            }

            Text(
                text = comment.text,
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

private fun formatRelativeTime(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    val nowSec = System.currentTimeMillis() / 1000L
    val diff = (nowSec - epochSec).coerceAtLeast(0L)
    return when {
        diff < 60L -> tr("Hozirgina", "Just now", "Только что")
        diff < 3600L -> "${diff / 60L} ${tr("daq oldin", "m ago", "мин назад")}"
        diff < 86400L -> "${diff / 3600L} ${tr("soat oldin", "h ago", "ч назад")}"
        diff < 604800L -> "${diff / 86400L} ${tr("kun oldin", "d ago", "дн назад")}"
        else -> {
            try {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(epochSec * 1000L))
            } catch (_: Exception) {
                ""
            }
        }
    }
}
