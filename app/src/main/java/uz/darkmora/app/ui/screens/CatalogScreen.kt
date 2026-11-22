package uz.darkmora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.components.AnimeCard
import uz.darkmora.app.ui.components.ShimmerBox
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*

@Composable
fun CatalogScreen(
    onAnimeClick: (String) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }

    var animes by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("") }

    val genres = listOf("Barchasi", "sarguzasht", "jangari", "komediya", "fantastika", "drama", "romantika", "sehrli")
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    suspend fun fetchPage(pageToLoad: Int, reset: Boolean) {
        if (reset) {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        try {
            val g = if (selectedGenre == "Barchasi") "" else selectedGenre
            val qClean = searchQuery.trim()
            val tok = sessionManager.getToken()
            val res = DarkmoraApi.getCatalog(
                page = pageToLoad,
                genre = g,
                q = qClean,
                perPage = 24,
                token = tok
            )
            if (res.ok && res.data != null) {
                val pageItems = res.data.items ?: emptyList()
                totalCount = res.data.total
                totalPages = maxOf(1, res.data.totalPages)
                currentPage = pageToLoad
                animes = if (reset) {
                    pageItems
                } else {
                    (animes + pageItems).distinctBy { it.id }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
            isLoadingMore = false
        }
    }

    // Automatic debounced search & genre filter reload
    LaunchedEffect(selectedGenre, searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(300)
        }
        fetchPage(pageToLoad = 1, reset = true)
    }

    // Infinite scroll: load next page when reaching near the bottom of the grid
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore, currentPage, totalPages, isLoading, isLoadingMore) {
        if (shouldLoadMore && !isLoading && !isLoadingMore && currentPage < totalPages) {
            fetchPage(pageToLoad = currentPage + 1, reset = false)
        }
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
                    placeholder = { Text(tr("Anime qidirish...", "Search anime...", "Поиск аниме..."), color = TextDim, fontSize = 14.sp) },
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
                    keyboardActions = KeyboardActions(onSearch = {
                        scope.launch { fetchPage(1, reset = true) }
                    }),
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
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

        if (!isLoading && totalCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tr("Jami", "Total", "Всего")}: $totalCount ${tr("ta anime", "anime", "аниме")}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${animes.size} / $totalCount",
                    color = TextDim,
                    fontSize = 11.5.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

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
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 115.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(animes, key = { _, a -> a.id }) { index, anime ->
                    AnimeCard(
                        anime = anime,
                        index = index % 12,
                        onClick = { onAnimeClick(anime.getEffectiveSlug()) },
                        width = 170,
                        height = 238
                    )
                }

                if (isLoadingMore) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                strokeWidth = 2.2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = tr("Hech narsa topilmadi", "No results found", "Ничего не найдено"),
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        }
    }
}
