package uz.darkmora.app.ui.screens

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
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
                        .height(300.dp),
                    cornerRadius = 0.dp
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(28.dp), cornerRadius = 8.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(20.dp), cornerRadius = 8.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(90.dp), cornerRadius = 12.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(52.dp), cornerRadius = 14.dp)
                }
            }
        } else if (anime != null) {
            val a = anime!!

            // Episode chunks if more than 30 episodes
            val chunkSize = 30
            val chunks = remember(episodes) { episodes.chunked(chunkSize) }
            var selectedChunkIndex by remember { mutableIntStateOf(0) }
            val visibleEpisodes = if (chunks.isNotEmpty()) chunks.getOrElse(selectedChunkIndex) { episodes } else emptyList()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // Backdrop Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
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
                                            Color(0x8808090E),
                                            Color(0x4408090E),
                                            Color(0xCC08090E),
                                            BgDark
                                        )
                                    )
                                )
                        )

                        // Top Action Buttons (Back & Favorite)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .bounceClick(onClick = onBackClick)
                                    .clip(CircleShape)
                                    .background(Color(0xBF0D111E))
                                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
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
                                    .background(if (isFavorite) Color(0x3338BDF8) else Color(0xBF0D111E))
                                    .border(1.dp, if (isFavorite) PrimaryGlow else Color(0x33FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Sevimlilar",
                                    tint = if (isFavorite) PrimaryGlow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Poster + Meta overlay at bottom of header
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            AsyncImage(
                                model = a.displayPoster(),
                                contentDescription = a.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(105.dp)
                                    .height(150.dp)
                                    .shadow(16.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(16.dp))
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
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 26.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF14192B))
                                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(13.dp))
                                        Text(a.displayRating(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text("•", color = TextDim, fontSize = 12.sp)
                                    Text(a.displayYear(), color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("•", color = TextDim, fontSize = 12.sp)
                                    Text("${episodes.size} qism", color = PrimaryGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

                // Info & Main Play Button Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Description Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(BgSurface)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = a.displayDescription(),
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Play / Resume Episode Button
                        if (episodes.isNotEmpty()) {
                            val targetEp = episodes.find { it.qism == lastWatchedEp } ?: episodes.first()
                            val buttonLabel = if (lastWatchedEp > 0) "${targetEp.qism}-Qismni Davom Ettirish" else "1-Qismni Boshlash"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick { launchEpisode(a, targetEp) }
                                    .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = PrimaryGlow)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(PrimaryBlue, Color(0xFF1D4ED8))
                                        )
                                    )
                                    .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(16.dp))
                                    .padding(vertical = 15.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buttonLabel,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
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
                                text = "Qismlar ro'yxati (${episodes.size})",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (lastWatchedEp > 0) {
                                Text(
                                    text = "Oxirgi: $lastWatchedEp-qism",
                                    color = PrimaryGlow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Range Chunks Filter if > 1 chunk
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
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) PrimaryBlue else BgCard)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) PrimaryGlow else CardBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
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

                // Episodes List
                items(visibleEpisodes) { ep ->
                    val isLastWatched = ep.qism == lastWatchedEp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .bounceClick { launchEpisode(a, ep) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isLastWatched) Color(0xFF17223B) else BgCard)
                            .border(
                                width = 1.dp,
                                color = if (isLastWatched) PrimaryGlow else CardBorder,
                                shape = RoundedCornerShape(14.dp)
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
                                        .background(if (isLastWatched) PrimaryBlue else Color(0x332563EB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isLastWatched) Color.White else PrimaryGlow,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${ep.qism}-qism",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isLastWatched) "Oxirgi ko'rilgan qism" else a.displayTitle(),
                                        color = if (isLastWatched) PrimaryGlow else TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isLastWatched) "Davom etish" else "Tomosha",
                                    color = if (isLastWatched) PrimaryGlow else PrimaryBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (isLastWatched) PrimaryGlow else PrimaryBlue,
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
