package uz.darkmora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.app.data.HomeData
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.*
import uz.darkmora.app.ui.theme.*

@Composable
fun HomeScreen(
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val user = remember { sessionManager.getUser() }

    var homeData by remember { mutableStateOf<HomeData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun fetchHome() {
        isLoading = true
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

    LaunchedEffect(Unit) {
        fetchHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp),
                    cornerRadius = 0.dp
                )
                Spacer(modifier = Modifier.height(22.dp))
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .width(180.dp)
                        .height(22.dp),
                    cornerRadius = 10.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(152.dp)
                                .height(218.dp),
                            cornerRadius = 20.dp
                        )
                    }
                }
            }
        } else if (homeData != null) {
            val data = homeData!!
            val featuredList = (data.featured?.takeIf { it.isNotEmpty() } ?: data.trending ?: emptyList())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (featuredList.isNotEmpty()) {
                    item {
                        HeroCarousel(
                            featuredList = featuredList,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                val trending = data.trending ?: emptyList()
                if (trending.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Trenddagi Animelar", onSeeAllClick = onSeeAllClick)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
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

                val latest = data.latestEpisodes ?: data.latest ?: emptyList()
                if (latest.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader(title = "Yangi Qismlar", onSeeAllClick = onSeeAllClick)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
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

                val topRated = data.topRated ?: data.top ?: emptyList()
                if (topRated.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader(title = "Yuqori Reyting", onSeeAllClick = onSeeAllClick)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Server bilan aloqa o'rnatilmadi", color = TextMuted, fontSize = 14.sp)
                    Button(
                        onClick = { fetchHome() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = BgDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Qayta urinish", color = BgDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Clean Minimalist Top Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xBF11131C))
                    .border(0.8.dp, Color(0x1FFFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = "DarkMora",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .bounceClick(onClick = onSeeAllClick)
                        .clip(CircleShape)
                        .background(Color(0xBF11131C))
                        .border(0.8.dp, Color(0x1FFFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Qidirish", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .bounceClick(onClick = onProfileClick)
                        .clip(CircleShape)
                        .background(Color(0xBF11131C))
                        .border(0.8.dp, Color(0x28FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user!!.photoUrl,
                            contentDescription = "Profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = "Profil", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
