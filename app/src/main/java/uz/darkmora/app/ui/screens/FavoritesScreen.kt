package uz.darkmora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.darkmora.app.data.Anime
import uz.darkmora.app.ui.components.AnimeCard
import uz.darkmora.app.ui.theme.*

@Composable
fun FavoritesScreen(
    favorites: List<Anime>,
    onAnimeClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "Sevimlilar Ro'yxati",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Hozircha sevimlilar yo'q",
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Yoqtirgan animelaringizni shu yerga saqlang",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime.getEffectiveSlug()) },
                        width = 170,
                        height = 240
                    )
                }
            }
        }
    }
}
