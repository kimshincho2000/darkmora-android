package uz.darkmora.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                val res = DarkmoraApi.getAnimeDetail(slug)
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

            val chunkSize = 30
            val chunks = remember(episodes) { episodes.chunked(chunkSize) }
            var selectedChunkIndex by remember { mutableIntStateOf(0) }
            val visibleEpisodes = if (chunks.isNotEmpty()) chunks.getOrElse(selectedChunkIndex) { episodes } else emptyList()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        // Blurred atmospheric backdrop so any text baked into the poster never clashes with the foreground UI!
                        AsyncImage(
                            model = a.banner ?: a.displayPoster(),
                            contentDescription = a.displayTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                        )

                        // Strong smooth theme-aware fade gradient
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

                        // Clean Top Action Buttons
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

                        // Foreground Sharp Poster + Clean High-Contrast Meta Info
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
                                    // High-contrast Rating Pill (TextPrimary in both Dark & Light modes!)
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

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        // Clean Soft Description Card
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

                        // Play / Resume Episode Button
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tr("Qismlar", "Episodes", "Серии")} (${episodes.size})",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
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
                                            .padding(horizontal = 16.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = "$firstNum - $lastNum",
                                            color = if (isSelected) BgSurface else TextMuted,
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

                // Clean Minimalist Episodes List
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
