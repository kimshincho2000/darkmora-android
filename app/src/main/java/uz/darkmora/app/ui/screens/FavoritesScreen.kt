package uz.darkmora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.AnimeCard
import uz.darkmora.app.ui.theme.*

@Composable
fun FavoritesScreen(
    favorites: List<Anime> = emptyList(),
    onAnimeClick: (String) -> Unit,
    onExploreClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    var favoriteList by remember { mutableStateOf(sessionManager.getFavoriteAnimes()) }

    LaunchedEffect(Unit) {
        val stored = sessionManager.getFavoriteAnimes()
        if (stored.isNotEmpty()) {
            favoriteList = stored
        } else {
            val favIds = sessionManager.getFavorites()
            if (favIds.isNotEmpty()) {
                try {
                    val res = DarkmoraApi.getCatalog(page = 1)
                    if (res.ok) {
                        val matched = res.data?.items?.filter { favIds.contains(it.id) } ?: emptyList()
                        favoriteList = matched
                    }
                } catch (_: Exception) {}
            } else {
                favoriteList = favorites
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sevimlilar",
                color = TextPrimary,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BgCard)
                    .border(0.8.dp, CardBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${favoriteList.size} ta",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (favoriteList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(0.8.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Sevimlilar ro'yxati bo'sh",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Yoqtirgan animelaringiz sahifasidagi saqlash tugmasini bosib, ularni shu yerga qo'shishingiz mumkin.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = BgDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Katalogga o'tish", color = BgDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 115.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favoriteList) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime.getEffectiveSlug()) },
                        width = 170,
                        height = 238
                    )
                }
            }
        }
    }
}
