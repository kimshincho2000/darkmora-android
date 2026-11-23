package uz.darkmora.app.ui.screens

import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.Episode
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.ShimmerBox
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*

@Composable
fun AnimeDetailScreen(
    slug: String,
    onBackClick: () -> Unit,
    onPlayEpisode: (String, String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val listState = rememberLazyListState()

    var anime by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var lastWatchedEp by remember { mutableIntStateOf(sessionManager.getLastWatchedEpisode(slug)) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingEpisodeToPlay by remember { mutableStateOf<Pair<Anime, Episode>?>(null) }
    val scope = rememberCoroutineScope()

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
        val rawUrl = ep.videoUrl ?: ep.embedUrl ?: ""
        val rawTitle = "${a.displayTitle()} - ${ep.qism}-qism"
        val b64Url = Base64.encodeToString(rawUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val b64Title = Base64.encodeToString(rawTitle.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        sessionManager.saveLastWatchedEpisode(slug, ep.qism)
        lastWatchedEp = ep.qism
        onPlayEpisode(b64Url, b64Title, slug, ep.qism)
    }

    fun launchEpisode(a: Anime, ep: Episode) {
        if (!sessionManager.isLoggedIn()) {
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

    fun shareAnime(a: Anime) {
        try {
            val url = "https://darkmora.uz/anime/${a.getEffectiveSlug()}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, a.displayTitle())
                putExtra(Intent.EXTRA_TEXT, "${a.displayTitle()}\n$url")
            }
            context.startActivity(Intent.createChooser(sendIntent, tr("Ulashish", "Share", "Поделиться")))
        } catch (_: Exception) {}
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
                        .height(210.dp),
                    cornerRadius = 0.dp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .height(310.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .weight(0.70f)
                            .fillMaxHeight(),
                        cornerRadius = 18.dp
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.30f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().weight(1f), cornerRadius = 16.dp)
                        ShimmerBox(modifier = Modifier.fillMaxWidth().weight(1f), cornerRadius = 16.dp)
                        ShimmerBox(modifier = Modifier.fillMaxWidth().weight(1f), cornerRadius = 16.dp)
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 16.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(22.dp), cornerRadius = 8.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(42.dp), cornerRadius = 24.dp)
                }
            }
        } else if (anime != null) {
            val a = anime!!

            val chunkSize = 30
            val chunks = remember(episodes) { episodes.chunked(chunkSize) }
            var selectedChunkIndex by remember { mutableIntStateOf(0) }
            val visibleEpisodes = if (chunks.isNotEmpty()) chunks.getOrElse(selectedChunkIndex) { episodes } else emptyList()

            val genreList = remember(a.genres, a.janri) {
                a.displayGenres()
                    .split(",", "•", "/", ";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
            }

            val localizedStatus = remember(a.status, AppThemeState.language) {
                val raw = a.displayStatus().lowercase()
                when {
                    raw.contains("tugallangan") || raw.contains("released") || raw.contains("finished") ->
                        tr("Tugallangan", "RELEASED", "ВЫШЕЛ")
                    raw.contains("davom") || raw.contains("ongoing") ->
                        tr("Ongoin", "ONGOING", "ОНГОИНГ")
                    else -> a.displayStatus().uppercase()
                }
            }

            val episodeCountLabel = remember(episodes.size, a.epCount, a.lastQism, a.qism, a.episodesCount, a.qismlarSoni) {
                when {
                    episodes.isNotEmpty() -> episodes.size.toString()
                    a.displayEpisodes().isNotEmpty() -> a.displayEpisodes()
                    else -> "—"
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                // ============================================================
                // 1. HERO BACKDROP + HEADER + TITLE/BADGES + POSTER & 3 STATS
                // ============================================================
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Blurred atmospheric top backdrop fading smoothly into BgDark
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                        ) {
                            AsyncImage(
                                model = a.banner ?: a.displayPoster(),
                                contentDescription = a.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(12.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                BgDark.copy(alpha = 0.30f),
                                                BgDark.copy(alpha = 0.55f),
                                                BgDark.copy(alpha = 0.90f),
                                                BgDark
                                            )
                                        )
                                    )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                            // Top Bar: Back (left) + Share & Bookmark (right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .bounceClick(onClick = onBackClick)
                                        .clip(CircleShape)
                                        .background(BgCard.copy(alpha = 0.78f))
                                        .border(0.8.dp, CardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBackIosNew,
                                        contentDescription = tr("Orqaga", "Back", "Назад"),
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .bounceClick { shareAnime(a) }
                                            .clip(CircleShape)
                                            .background(BgCard.copy(alpha = 0.78f))
                                            .border(0.8.dp, CardBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = tr("Ulashish", "Share", "Поделиться"),
                                            tint = TextPrimary,
                                            modifier = Modifier.size(19.dp)
                                        )
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
                                            .background(if (isFavorite) PrimaryBlue else BgCard.copy(alpha = 0.78f))
                                            .border(0.8.dp, if (isFavorite) PrimaryBlue else CardBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = tr("Sevimlilar", "Favorites", "Избранное"),
                                            tint = if (isFavorite) Color.White else TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Right-aligned Title + Rating & Views Badges
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = a.displayTitle(),
                                    color = TextPrimary,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 28.sp,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rating Badge (Accent Gold pill matching DarkMora palette)
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(RatingGold)
                                            .padding(horizontal = 11.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFF0A0B10),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = a.displayRating(),
                                            color = Color(0xFF0A0B10),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    // Views / Audience Badge (High-contrast adaptive pill)
                                    val viewsBg = if (AppThemeState.isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                                    val viewsFg = if (AppThemeState.isDark) Color(0xFF0A0B10) else Color(0xFFF8FAFC)
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(viewsBg)
                                            .padding(horizontal = 11.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = viewsFg,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = a.displayViews(),
                                            color = viewsFg,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Main Poster (70%) + 3 Vertical Info Cards (30%)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .height(335.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Left: Large Vertical Poster with Diagonal Corner Ribbon
                                Box(
                                    modifier = Modifier
                                        .weight(0.71f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(BgCard)
                                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                                ) {
                                    AsyncImage(
                                        model = a.displayPoster(),
                                        contentDescription = a.displayTitle(),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Top-right diagonal type ribbon ("TV", "FILM", "OVA", ...)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 26.dp, y = 14.dp)
                                            .graphicsLayer { rotationZ = 45f }
                                            .width(96.dp)
                                            .background(PrimaryBlue)
                                            .padding(vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = a.displayShortType(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }

                                // Right: 3 Equal Stacked Info Cards (Status, Episodes, Year)
                                Column(
                                    modifier = Modifier
                                        .weight(0.29f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AnimeMetaStatCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.DonutLarge,
                                        label = tr("Holati", "Status", "Статус"),
                                        value = localizedStatus,
                                        isCompactValue = true
                                    )
                                    AnimeMetaStatCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.Movie,
                                        label = tr("Qismlar", "Episodes", "Эпизоды"),
                                        value = episodeCountLabel,
                                        isCompactValue = false
                                    )
                                    AnimeMetaStatCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.CalendarMonth,
                                        label = tr("Yili", "Year", "Год"),
                                        value = a.displayYear(),
                                        isCompactValue = false
                                    )
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 2. SIGNATURE SPLIT "KO'RISH / WATCH" ACTION BUTTON
                // ============================================================
                item {
                    Spacer(modifier = Modifier.height(18.dp))

                    if (episodes.isNotEmpty()) {
                        val targetEp = episodes.find { it.qism == lastWatchedEp } ?: episodes.first()
                        val buttonLabel = if (lastWatchedEp > 0) {
                            tr(
                                "${targetEp.qism}-QISMNI DAVOM ETTIRISH",
                                "CONTINUE EPISODE ${targetEp.qism}",
                                "ПРОДОЛЖИТЬ ${targetEp.qism} СЕРИЮ"
                            )
                        } else {
                            tr("KO'RISH", "WATCH", "СМОТРЕТЬ")
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(56.dp)
                                .bounceClick { launchEpisode(a, targetEp) }
                                .clip(RoundedCornerShape(16.dp))
                                .background(BgCard)
                                .border(1.4.dp, PrimaryBlue, RoundedCornerShape(16.dp))
                        ) {
                            // Left angled accent block (trapezoid) matching the reference button
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val topW = 74.dp.toPx()
                                val bottomW = 60.dp.toPx()
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(topW, 0f)
                                    lineTo(bottomW, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    brush = Brush.linearGradient(
                                        colors = listOf(PrimaryBlue, PrimaryGlow)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(66.dp)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = buttonLabel,
                                        color = TextPrimary,
                                        fontSize = if (lastWatchedEp > 0) 14.sp else 16.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.1.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 3. GENRES PILLS ROW + DESCRIPTION + EPISODES HEADER
                // ============================================================
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp)
                    ) {
                        if (genreList.isNotEmpty()) {
                            Text(
                                text = tr("Janrlar:", "Genres:", "Жанры:"),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(genreList) { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(BgCard)
                                            .border(0.9.dp, CardBorder, RoundedCornerShape(50))
                                            .padding(horizontal = 16.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            text = genre.replaceFirstChar { it.uppercase() },
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // Soft Description Card
                        val desc = a.displayDescription().trim()
                        if (desc.isNotEmpty() && desc != "Tavsif mavjud emas.") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BgCard)
                                    .border(0.8.dp, CardBorder, RoundedCornerShape(18.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = desc,
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Episodes Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tr("Qismlar", "Episodes", "Серии")} (${episodes.size})",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (lastWatchedEp > 0) {
                                Text(
                                    text = "${tr("Oxirgi", "Last", "Посл.")}: $lastWatchedEp-${tr("qism", "ep", "сер.")}",
                                    color = PrimaryBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (chunks.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 18.dp),
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

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // ============================================================
                // 4. EPISODES LIST
                // ============================================================
                items(visibleEpisodes) { ep ->
                    val isLastWatched = ep.qism == lastWatchedEp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.5.dp)
                            .bounceClick { launchEpisode(a, ep) }
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isLastWatched) PrimaryBlue.copy(alpha = 0.14f) else BgCard)
                            .border(
                                width = 0.8.dp,
                                color = if (isLastWatched) PrimaryBlue.copy(alpha = 0.45f) else CardBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isLastWatched) PrimaryBlue else BgSurface)
                                        .border(0.6.dp, CardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isLastWatched) Color.White else TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${ep.qism}-${tr("qism", "episode", "серия")}",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isLastWatched) tr("Oxirgi ko'rilgan", "Last watched", "Последний просмотр") else a.displayTitle(),
                                        color = if (isLastWatched) PrimaryBlue else TextDim,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isLastWatched) tr("Davom etish", "Resume", "Продолжить") else tr("Ko'rish", "Watch", "Смотреть"),
                                    color = if (isLastWatched) PrimaryBlue else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (isLastWatched) PrimaryBlue else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Quick-Jump Button to Episodes (matching the circular FAB in reference, in DarkMora PrimaryBlue)
            if (episodes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 22.dp)
                        .size(56.dp)
                        .shadow(10.dp, CircleShape, ambientColor = PrimaryBlue, spotColor = PrimaryBlue)
                        .bounceClick {
                            scope.launch {
                                listState.animateScrollToItem(3)
                            }
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(PrimaryBlue, PrimaryGlow))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = tr("Qismlar", "Episodes", "Серии"),
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
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
private fun AnimeMetaStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isCompactValue: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(0.9.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = if (isCompactValue) 11.sp else 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
