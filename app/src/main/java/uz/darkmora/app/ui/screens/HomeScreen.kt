package uz.darkmora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.darkmora.app.data.HomeData
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.AnimeCard
import uz.darkmora.app.ui.components.HeroBanner
import uz.darkmora.app.ui.components.SectionHeader
import uz.darkmora.app.ui.theme.BgDark
import uz.darkmora.app.ui.theme.PrimaryBlue
import uz.darkmora.app.ui.theme.TextMuted

@Composable
fun HomeScreen(
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: () -> Unit
) {
    var homeData by remember { mutableStateOf<HomeData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = DarkmoraApi.getHome()
                if (res.ok) {
                    homeData = res.data
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
        } else if (homeData != null) {
            val data = homeData!!
            val featuredAnime = data.featured?.firstOrNull() ?: data.trending?.firstOrNull()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // Hero Banner
                if (featuredAnime != null) {
                    item {
                        HeroBanner(
                            anime = featuredAnime,
                            onWatchClick = { onAnimeClick(featuredAnime.getEffectiveSlug()) }
                        )
                    }
                }

                // Trending Row
                val trending = data.trending ?: emptyList()
                if (trending.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Trenddagi Animelar 🔥", onSeeAllClick = onSeeAllClick)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(trending) { anime ->
                                AnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                )
                            }
                        }
                    }
                }

                // Latest Episodes Row
                val latest = data.latestEpisodes ?: data.latest ?: emptyList()
                if (latest.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader(title = "Yangi Qismlar ⚡", onSeeAllClick = onSeeAllClick)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(latest) { anime ->
                                AnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                )
                            }
                        }
                    }
                }

                // Top Rated Row
                val topRated = data.topRated ?: data.top ?: emptyList()
                if (topRated.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader(title = "Eng Yuqori Reyting 👑", onSeeAllClick = onSeeAllClick)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(topRated) { anime ->
                                AnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Server bilan aloqa yo'q", color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}
