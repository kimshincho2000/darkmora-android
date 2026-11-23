package uz.darkmora.app.ui.screens

import android.net.Uri
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    // Panel tabs: 0 = Qismlar, 1 = Izohlar
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGridMode by remember { mutableStateOf(false) }

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
                        // Fetch comments in background
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
        // If already downloaded locally, play directly from local file (instant + offline!)
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
            Toast.makeText(
                context,
                tr(
                    "Yuklangan! Sozlamalar → Yuklanmalar bo'limida ham mavjud",
                    "Downloaded! Also available in Preferences → Downloads",
                    "Скачано! Доступно в Настройки → Загрузки"
                ),
                Toast.LENGTH_SHORT
            ).show()
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
        Toast.makeText(
            context,
            tr("${ep.qism}-qism yuklanmoqda...", "Downloading episode ${ep.qism}...", "Загрузка ${ep.qism} серии..."),
            Toast.LENGTH_SHORT
        ).show()
        OfflineDownloadManager.startDownload(context, a, ep) { ok, msg ->
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
                    Toast.makeText(
                        context,
                        tr("Izohingiz qo'shildi", "Comment posted", "Комментарий добавлен"),
                        Toast.LENGTH_SHORT
                    ).show()
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
                // 2. DESCRIPTION + PRIMARY PLAY BUTTON + REDESIGNED PANEL TABS
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

                        // Modern Segmented Tab Switcher: Qismlar | Izohlar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(BgCard)
                                .border(0.9.dp, CardBorder, RoundedCornerShape(18.dp))
                                .padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DetailPanelTab(
                                modifier = Modifier.weight(1f),
                                selected = selectedTab == 0,
                                icon = Icons.Default.PlaylistPlay,
                                label = tr("Qismlar", "Episodes", "Серии"),
                                count = episodes.size,
                                onClick = { selectedTab = 0 }
                            )
                            DetailPanelTab(
                                modifier = Modifier.weight(1f),
                                selected = selectedTab == 1,
                                icon = Icons.Outlined.ChatBubbleOutline,
                                label = tr("Izohlar", "Comments", "Комменты"),
                                count = comments.size,
                                onClick = { selectedTab = 1 }
                            )
                        }

                        if (selectedTab == 0) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Episodes Panel Toolbar (Title + Offline count + List/Grid toggle)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = tr("Qismni tanlang", "Select Episode", "Выберите серию"),
                                        color = TextPrimary,
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    val subInfo = buildString {
                                        if (lastWatchedEp > 0) {
                                            append("${tr("Oxirgi", "Last", "Посл.")}: $lastWatchedEp-${tr("qism", "ep", "сер.")}")
                                        }
                                        if (downloadedForThisAnime.isNotEmpty()) {
                                            if (isNotEmpty()) append(" • ")
                                            append("${downloadedForThisAnime.size} ${tr("ta yuklangan", "downloaded", "скачано")}")
                                        }
                                    }
                                    if (subInfo.isNotEmpty()) {
                                        Text(
                                            text = subInfo,
                                            color = if (downloadedForThisAnime.isNotEmpty()) GreenOnline else PrimaryBlue,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // List / Grid View Toggle Pill
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BgCard)
                                        .border(0.8.dp, CardBorder, RoundedCornerShape(12.dp))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .bounceClick { isGridMode = false }
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (!isGridMode) PrimaryBlue else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewAgenda,
                                            contentDescription = "List",
                                            tint = if (!isGridMode) Color.White else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .bounceClick { isGridMode = true }
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (isGridMode) PrimaryBlue else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GridView,
                                            contentDescription = "Grid",
                                            tint = if (isGridMode) Color.White else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (chunks.size > 1) {
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
                                                .background(if (isSelected) PrimaryBlue else BgCard)
                                                .border(
                                                    width = 0.8.dp,
                                                    color = if (isSelected) PrimaryBlue else CardBorder,
                                                    shape = RoundedCornerShape(50)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "$firstNum - $lastNum",
                                                color = if (isSelected) Color.White else TextMuted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 3A. EPISODES TAB CONTENT (STYLISH LIST OR COMPACT 3-COL GRID)
                // ============================================================
                if (selectedTab == 0) {
                    if (!isGridMode) {
                        items(visibleEpisodes, key = { "ep_list_${it.qism}" }) { ep ->
                            val isLastWatched = ep.qism == lastWatchedEp
                            val downloadedEp = downloadedForThisAnime[ep.qism]
                            val isDownloaded = downloadedEp != null
                            val activeDl = OfflineDownloadManager.activeDownloads[OfflineDownloadManager.keyOf(effectiveSlug, ep.qism)]

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 5.dp)
                                    .bounceClick { launchEpisode(a, ep) }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        when {
                                            isLastWatched -> PrimaryBlue.copy(alpha = 0.14f)
                                            isDownloaded -> GreenOnline.copy(alpha = 0.08f)
                                            else -> BgCard
                                        }
                                    )
                                    .border(
                                        width = 0.9.dp,
                                        color = when {
                                            isLastWatched -> PrimaryBlue.copy(alpha = 0.50f)
                                            isDownloaded -> GreenOnline.copy(alpha = 0.35f)
                                            else -> CardBorder
                                        },
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Stylized Episode Number Tile
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    when {
                                                        isLastWatched -> PrimaryBlue
                                                        isDownloaded -> GreenOnline.copy(alpha = 0.16f)
                                                        else -> BgSurface
                                                    }
                                                )
                                                .border(
                                                    0.8.dp,
                                                    when {
                                                        isLastWatched -> PrimaryBlue
                                                        isDownloaded -> GreenOnline.copy(alpha = 0.45f)
                                                        else -> CardBorder
                                                    },
                                                    RoundedCornerShape(14.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ep.qism.toString().padStart(2, '0'),
                                                color = when {
                                                    isLastWatched -> Color.White
                                                    isDownloaded -> GreenOnline
                                                    else -> TextPrimary
                                                },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${ep.qism}-${tr("qism", "episode", "серия")}",
                                                    color = TextPrimary,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isLastWatched) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(PrimaryBlue.copy(alpha = 0.2f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = tr("Oxirgi", "Last", "Посл."),
                                                            color = PrimaryBlue,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            when {
                                                activeDl != null -> {
                                                    val pct = (activeDl.progress * 100).toInt()
                                                    val sizeTxt = if (activeDl.totalBytes > 0L) {
                                                        "${OfflineDownloadManager.formatFileSize(activeDl.downloadedBytes)} / ${OfflineDownloadManager.formatFileSize(activeDl.totalBytes)}"
                                                    } else {
                                                        OfflineDownloadManager.formatFileSize(activeDl.downloadedBytes)
                                                    }
                                                    Text(
                                                        text = "${tr("Yuklanmoqda", "Downloading", "Загрузка")} $pct% • $sizeTxt",
                                                        color = PrimaryBlue,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                isDownloaded -> {
                                                    Text(
                                                        text = "${tr("Oflayn tayyor", "Saved offline", "Доступно офлайн")} • ${OfflineDownloadManager.formatFileSize(downloadedEp!!.sizeBytes)}",
                                                        color = GreenOnline,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                else -> {
                                                    Text(
                                                        text = a.displayTitle(),
                                                        color = TextDim,
                                                        fontSize = 11.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Download / Progress / Downloaded Button
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .bounceClick { handleDownloadClick(a, ep) }
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        activeDl != null -> PrimaryBlue.copy(alpha = 0.14f)
                                                        isDownloaded -> GreenOnline.copy(alpha = 0.16f)
                                                        else -> BgSurface
                                                    }
                                                )
                                                .border(
                                                    0.8.dp,
                                                    when {
                                                        activeDl != null -> PrimaryBlue.copy(alpha = 0.4f)
                                                        isDownloaded -> GreenOnline.copy(alpha = 0.45f)
                                                        else -> CardBorder
                                                    },
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when {
                                                activeDl != null -> {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator(
                                                            progress = { activeDl.progress.coerceIn(0.05f, 1f) },
                                                            modifier = Modifier.size(28.dp),
                                                            color = PrimaryBlue,
                                                            trackColor = CardBorder,
                                                            strokeWidth = 2.5.dp
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Cancel",
                                                            tint = PrimaryBlue,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                                isDownloaded -> {
                                                    Icon(
                                                        imageVector = Icons.Default.OfflinePin,
                                                        contentDescription = "Downloaded",
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

                                        // Delete button if already downloaded
                                        if (isDownloaded) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .bounceClick {
                                                        OfflineDownloadManager.deleteEpisode(context, effectiveSlug, ep.qism)
                                                        Toast.makeText(
                                                            context,
                                                            tr("${ep.qism}-qism yuklanmalardan o'chirildi", "Episode ${ep.qism} removed", "${ep.qism} серия удалена"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .clip(CircleShape)
                                                    .background(RedBadge.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = RedBadge,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Play Circle
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isLastWatched) PrimaryBlue else BgSurface)
                                                .border(0.7.dp, if (isLastWatched) PrimaryBlue else CardBorder, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isLastWatched) Color.White else TextPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Compact 3-Column Episode Grid
                        val rows = visibleEpisodes.chunked(3)
                        items(rows.indices.toList(), key = { "ep_grid_row_$it" }) { rowIdx ->
                            val rowEps = rows[rowIdx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowEps.forEach { ep ->
                                    val isLastWatched = ep.qism == lastWatchedEp
                                    val isDownloaded = downloadedForThisAnime.containsKey(ep.qism)
                                    val activeDl = OfflineDownloadManager.activeDownloads[OfflineDownloadManager.keyOf(effectiveSlug, ep.qism)]

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .bounceClick { launchEpisode(a, ep) }
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                when {
                                                    isLastWatched -> PrimaryBlue.copy(alpha = 0.16f)
                                                    isDownloaded -> GreenOnline.copy(alpha = 0.10f)
                                                    else -> BgCard
                                                }
                                            )
                                            .border(
                                                width = 0.9.dp,
                                                color = when {
                                                    isLastWatched -> PrimaryBlue
                                                    isDownloaded -> GreenOnline.copy(alpha = 0.45f)
                                                    else -> CardBorder
                                                },
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${ep.qism}-${tr("qism", "ep", "сер.")}",
                                                    color = if (isLastWatched) PrimaryBlue else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .bounceClick { handleDownloadClick(a, ep) }
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isDownloaded -> GreenOnline.copy(alpha = 0.16f)
                                                                activeDl != null -> PrimaryBlue.copy(alpha = 0.16f)
                                                                else -> BgSurface
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    when {
                                                        activeDl != null -> CircularProgressIndicator(
                                                            progress = { activeDl.progress.coerceIn(0.05f, 1f) },
                                                            modifier = Modifier.size(16.dp),
                                                            color = PrimaryBlue,
                                                            strokeWidth = 2.dp
                                                        )
                                                        isDownloaded -> Icon(
                                                            Icons.Default.OfflinePin,
                                                            contentDescription = null,
                                                            tint = GreenOnline,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        else -> Icon(
                                                            Icons.Outlined.DownloadForOffline,
                                                            contentDescription = null,
                                                            tint = TextMuted,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = when {
                                                    activeDl != null -> "${(activeDl.progress * 100).toInt()}%"
                                                    isDownloaded -> tr("Oflayn", "Offline", "Офлайн")
                                                    isLastWatched -> tr("Davom etish", "Resume", "Продолжить")
                                                    else -> tr("Ko'rish", "Watch", "Смотреть")
                                                },
                                                color = when {
                                                    activeDl != null -> PrimaryBlue
                                                    isDownloaded -> GreenOnline
                                                    isLastWatched -> PrimaryBlue
                                                    else -> TextDim
                                                },
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                repeat(3 - rowEps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 3B. COMMENTS TAB CONTENT (COMPOSER + REAL-TIME COMMENTS LIST)
                // ============================================================
                if (selectedTab == 1) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                        ) {
                            // Comment Input Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BgCard)
                                    .border(0.9.dp, CardBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (commentDraft.isEmpty()) {
                                        Text(
                                            text = tr(
                                                "Anime haqida fikringizni yozing...",
                                                "Write your thoughts about this anime...",
                                                "Напишите комментарий об аниме..."
                                            ),
                                            color = TextDim,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = commentDraft,
                                        onValueChange = { if (it.length <= 500) commentDraft = it },
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        ),
                                        cursorBrush = SolidColor(PrimaryBlue),
                                        maxLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .bounceClick { submitComment(a) }
                                        .clip(CircleShape)
                                        .background(if (commentDraft.isNotBlank()) PrimaryBlue else BgSurface)
                                        .border(0.8.dp, if (commentDraft.isNotBlank()) PrimaryBlue else CardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSendingComment) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = tr("Yuborish", "Send", "Отправить"),
                                            tint = if (commentDraft.isNotBlank()) Color.White else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    if (isLoadingComments) {
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp), cornerRadius = 16.dp)
                                ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp), cornerRadius = 16.dp)
                            }
                        }
                    } else if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 20.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BgCard)
                                    .border(0.8.dp, CardBorder, RoundedCornerShape(20.dp))
                                    .padding(vertical = 28.dp, horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = TextDim,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = tr("Hozircha izohlar yo'q", "No comments yet", "Комментариев пока нет"),
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = tr(
                                            "Birinchi bo'lib fikr bildiring!",
                                            "Be the first to share your thoughts!",
                                            "Будьте первым, кто оставит отзыв!"
                                        ),
                                        color = TextMuted,
                                        fontSize = 12.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(comments, key = { "comment_${it.id}_${it.createdAt}" }) { c ->
                            CommentCard(comment = c)
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
    }
}

@Composable
private fun DetailPanelTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PrimaryBlue else Color.Transparent)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) Color.White else TextMuted,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) Color.White.copy(alpha = 0.22f) else BgSurface)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                color = if (selected) Color.White else TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun CommentCard(comment: AnimeComment) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(0.8.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!comment.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = comment.photoUrl,
                    contentDescription = comment.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BgSurface)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.18f))
                        .border(0.8.dp, PrimaryBlue.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.username.firstOrNull()?.uppercase() ?: "U",
                        color = PrimaryBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.username,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
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
