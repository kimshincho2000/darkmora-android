package uz.darkmora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.AnimeCard
import uz.darkmora.app.ui.components.ShimmerBox
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*

@Composable
fun CatalogScreen(
    onAnimeClick: (String) -> Unit
) {
    var animes by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("") }

    val genres = listOf("Barchasi", "sarguzasht", "jangari", "komediya", "fantastika", "drama", "romantika", "sehrli")
    val scope = rememberCoroutineScope()

    fun loadCatalog() {
        isLoading = true
        scope.launch {
            try {
                val g = if (selectedGenre == "Barchasi") "" else selectedGenre
                val res = if (searchQuery.isNotEmpty()) {
                    DarkmoraApi.search(searchQuery)
                } else {
                    DarkmoraApi.getCatalog(page = 1, genre = g)
                }
                if (res.ok) {
                    animes = res.data?.items ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedGenre) {
        loadCatalog()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Clean Pill Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(BgSurface)
                .border(0.8.dp, CardBorder, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Anime qidirish...", color = TextDim, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { loadCatalog() }),
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; loadCatalog() }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Minimalist High-Contrast Genre Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            items(genres) { genre ->
                val isSelected = (genre == "Barchasi" && selectedGenre.isEmpty()) || genre == selectedGenre
                Box(
                    modifier = Modifier
                        .bounceClick {
                            selectedGenre = if (genre == "Barchasi") "" else genre
                        }
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) TextPrimary else BgCard)
                        .border(0.8.dp, if (isSelected) TextPrimary else CardBorder, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = genre.replaceFirstChar { it.uppercase() },
                        color = if (isSelected) BgSurface else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 115.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(6) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(235.dp),
                        cornerRadius = 20.dp
                    )
                }
            }
        } else if (animes.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 115.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(animes) { index, anime ->
                    AnimeCard(
                        anime = anime,
                        index = index,
                        onClick = { onAnimeClick(anime.getEffectiveSlug()) },
                        width = 170,
                        height = 238
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Hech narsa topilmadi", color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}
