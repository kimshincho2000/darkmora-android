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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    fun launchEpisode(a: Anime, ep: Episode) {
        val rawUrl = ep.videoUrl ?: ep.embedUrl ?: ""
        val rawTitle = "${a.displayTitle()} - ${ep.qism}-qism"
        val b64Url = Base64.encodeToString(rawUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val b64Title = Base64.encodeToString(rawTitle.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        sessionManager.saveLastWatchedEpisode(slug, ep.qism)
        lastWatchedEp = ep.qism
        onPlayEpisode(b64Url, b64Title, slug, ep.qism)
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
                            .height(325.dp)
                    ) {
                        AsyncImage(
                            model = a.banner ?: a.displayPoster(),
                            contentDescription = a.displayTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x660A0B10),
                                            Color(0x330A0B10),
                                            Color(0xCC0A0B10),
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
                                    .background(Color(0xBF11131C))
                                    .border(0.8.dp, Color(0x22FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Orqaga", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .bounceClick {
                                        isFavorite = sessionManager.toggleFavoriteAnime(a)
                                        val msg = if (isFavorite) "Sevimlilarga qo'shildi" else "Sevimlilardan olib tashlandi"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    .clip(CircleShape)
                                    .background(if (isFavorite) Color.White else Color(0xBF11131C))
                                    .border(0.8.dp, if (isFavorite) Color.White else Color(0x22FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Sevimlilar",
                                    tint = if (isFavorite) BgDark else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Poster + Meta overlay
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            AsyncImage(
                                model = a.displayPoster(),
                                contentDescription = a.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(106.dp)
                                    .height(152.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(0.8.dp, Color(0x28FFFFFF), RoundedCornerShape(18.dp))
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = a.displayTitle(),
                                    color = TextPrimary,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 26.sp
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
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(13.dp))
                                        Text(a.displayRating(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text("•", color = TextDim, fontSize = 12.sp)
                                    Text(a.displayYear(), color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("•", color = TextDim, fontSize = 12.sp)
                                    Text("${episodes.size} qism", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                val genres = a.displayGenres()
                                if (genres.isNotEmpty()) {
                                    Text(
                                        text = genres.replaceFirstChar { it.uppercase() },
                                        color = TextMuted,
                                        fontSize = 12.sp
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
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        // Clean Soft Description Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(BgSurface)
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

                        Spacer(modifier = Modifier.height(18.dp))

                        // Clean White Pill Play / Resume Episode Button
                        if (episodes.isNotEmpty()) {
                            val targetEp = episodes.find { it.qism == lastWatchedEp } ?: episodes.first()
                            val buttonLabel = if (lastWatchedEp > 0) "${targetEp.qism}-Qismni Davom Ettirish" else "1-Qismni Boshlash"

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

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Qismlar (${episodes.size})",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (lastWatchedEp > 0) {
                                Text(
                                    text = "Oxirgi: $lastWatchedEp-qism",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
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
                                        .background(if (isLastWatched) PrimaryBlue else BgSurface),
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
                                        text = "${ep.qism}-qism",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isLastWatched) "Oxirgi ko'rilgan" else a.displayTitle(),
                                        color = if (isLastWatched) PrimaryBlue else TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isLastWatched) "Davom etish" else "Ko'rish",
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
    }
}
