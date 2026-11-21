package uz.darkmora.app.ui.screens

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.Episode
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.theme.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AnimeDetailScreen(
    slug: String,
    onBackClick: () -> Unit,
    onPlayEpisode: (String, String) -> Unit
) {
    var anime by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slug) {
        scope.launch {
            try {
                val res = DarkmoraApi.getAnimeDetail(slug)
                if (res.ok && res.data != null) {
                    anime = res.data.anime
                    episodes = res.data.episodes ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else if (anime != null) {
            val a = anime!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Backdrop Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
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
                                            Color(0x6608090E),
                                            Color(0xAA08090E),
                                            Color(0xFF08090E)
                                        )
                                    )
                                )
                        )

                        // Back Button
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color(0x8808090E))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Orqaga", tint = Color.White)
                        }
                    }
                }

                // Info Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = a.displayTitle(),
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgCard)
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(13.dp))
                                Text(a.displayRating(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Text("•", color = TextDim, fontSize = 12.sp)
                            Text(a.displayYear(), color = TextMuted, fontSize = 13.sp)
                            Text("•", color = TextDim, fontSize = 12.sp)
                            Text("${episodes.size} qism", color = PrimaryGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Description
                        Text(
                            text = a.displayDescription(),
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Play Episode 1 Button
                        if (episodes.isNotEmpty()) {
                            val firstEp = episodes.first()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PrimaryBlue)
                                    .clickable {
                                        val encodedUrl = URLEncoder.encode(firstEp.videoUrl ?: "", StandardCharsets.UTF_8.toString())
                                        val encodedTitle = URLEncoder.encode("${a.displayTitle()} - 1-qism", StandardCharsets.UTF_8.toString())
                                        onPlayEpisode(encodedUrl, encodedTitle)
                                    }
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1-Qismni Boshlash",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Qismlar ro'yxati (${episodes.size})",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Episodes Grid/List
                items(episodes) { ep ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                val encodedUrl = URLEncoder.encode(ep.videoUrl ?: "", StandardCharsets.UTF_8.toString())
                                val encodedTitle = URLEncoder.encode("${a.displayTitle()} - ${ep.qism}-qism", StandardCharsets.UTF_8.toString())
                                onPlayEpisode(encodedUrl, encodedTitle)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x332563EB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryGlow, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text(
                                        text = "${ep.qism}-qism",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = a.displayTitle(),
                                        color = TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Text(
                                text = "Tomosha",
                                color = PrimaryBlue,
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
