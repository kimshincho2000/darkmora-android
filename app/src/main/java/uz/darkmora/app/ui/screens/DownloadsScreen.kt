package uz.darkmora.app.ui.screens

import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import uz.darkmora.app.data.DownloadedAnime
import uz.darkmora.app.data.DownloadedEpisode
import uz.darkmora.app.data.OfflineDownloadManager
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.ui.components.bounceClick
import uz.darkmora.app.ui.theme.*
import java.io.File

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onOfflineAnimeClick: (String) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OfflineDownloadManager.init(context)
        OfflineDownloadManager.reload(context)
    }

    val downloadedList = OfflineDownloadManager.downloadedAnimes
    val totalBytes = downloadedList.sumOf { it.totalSizeBytes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = tr("Orqaga", "Back", "Назад"),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = tr("Yuklanmalar", "Downloads", "Загрузки"),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = tr(
                            "Oflayn saqlangan animelar",
                            "Saved for offline viewing",
                            "Сохранённые аниме офлайн"
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (totalBytes > 0L) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryBlue.copy(alpha = 0.14f))
                        .border(0.8.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = OfflineDownloadManager.formatFileSize(totalBytes),
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (downloadedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DownloadForOffline,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = tr("Yuklanmalar bo'sh", "No Downloads Yet", "Загрузок пока нет"),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            "Istalgan anime sahifasidan qismlarni yuklab oling va internetsiz tomosha qiling.",
                            "Download episodes from any anime page to watch them offline anytime.",
                            "Скачивайте серии на странице аниме, чтобы смотреть их без интернета."
                        ),
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(downloadedList, key = { it.slug }) { item ->
                    DownloadedAnimeCard(
                        item = item,
                        onClick = { onOfflineAnimeClick(item.slug) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedAnimeCard(
    item: DownloadedAnime,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(0.8.dp, CardBorder, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(BgSurface)
        ) {
            AsyncImage(
                model = item.displayPosterSource(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            // Offline badge (top-left)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GreenOnline.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OfflinePin,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = tr("Oflayn", "Offline", "Офлайн"),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Downloaded episodes count badge (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${item.episodes.size} ${tr("qism", "ep", "сер.")}",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Storage size at bottom-left of poster
            Text(
                text = OfflineDownloadManager.formatFileSize(item.totalSizeBytes()),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
            if (item.year.isNotBlank() || item.rating.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.rating.isNotBlank()) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(12.dp))
                        Text(
                            text = item.rating,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (item.year.isNotBlank()) {
                        Text("•", color = TextDim, fontSize = 11.sp)
                        Text(
                            text = item.year,
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineAnimeDetailScreen(
    slug: String,
    onBackClick: () -> Unit,
    onPlayEpisode: (String, String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }

    LaunchedEffect(Unit) {
        OfflineDownloadManager.init(context)
    }

    val downloadedAnime = OfflineDownloadManager.downloadedAnimes.firstOrNull { it.slug == slug }
    var lastWatchedEp by remember { mutableIntStateOf(sessionManager.getLastWatchedEpisode(slug)) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    fun playOfflineEpisode(anime: DownloadedAnime, ep: DownloadedEpisode) {
        val f = File(ep.filePath)
        if (!f.exists() || f.length() == 0L) {
            Toast.makeText(
                context,
                tr("Fayl topilmadi", "File not found", "Файл не найден"),
                Toast.LENGTH_SHORT
            ).show()
            OfflineDownloadManager.reload(context)
            return
        }
        val fileUri = Uri.fromFile(f).toString()
        val rawTitle = "${anime.title} - ${ep.qism}-qism"
        val b64Url = Base64.encodeToString(fileUri.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val b64Title = Base64.encodeToString(rawTitle.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        sessionManager.saveLastWatchedEpisode(slug, ep.qism)
        lastWatchedEp = ep.qism
        onPlayEpisode(b64Url, b64Title, slug, ep.qism)
    }

    if (downloadedAnime == null) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    val episodes = downloadedAnime.episodes

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    AsyncImage(
                        model = downloadedAnime.displayPosterSource(),
                        contentDescription = downloadedAnime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp)
                    )

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

                    // Top bar
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
                                .bounceClick { showDeleteAllConfirm = true }
                                .clip(CircleShape)
                                .background(RedBadge.copy(alpha = 0.16f))
                                .border(0.8.dp, RedBadge.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = tr("Hammasini o'chirish", "Delete all", "Удалить все"),
                                tint = RedBadge,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Poster + Title + Meta (same clean look as normal AnimeDetailScreen!)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        AsyncImage(
                            model = downloadedAnime.displayPosterSource(),
                            contentDescription = downloadedAnime.title,
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
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(GreenOnline.copy(alpha = 0.16f))
                                    .border(0.8.dp, GreenOnline.copy(alpha = 0.45f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.OfflinePin, contentDescription = null, tint = GreenOnline, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "${tr("Oflayn", "Offline", "Офлайн")} • ${OfflineDownloadManager.formatFileSize(downloadedAnime.totalSizeBytes())}",
                                    color = GreenOnline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = downloadedAnime.title,
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
                                if (downloadedAnime.rating.isNotBlank()) {
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
                                        Text(downloadedAnime.rating, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (downloadedAnime.year.isNotBlank()) {
                                    Text(downloadedAnime.year, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text("•", color = TextDim, fontSize = 12.sp)
                                Text(
                                    text = "${episodes.size} ${tr("qism", "ep", "сер.")}",
                                    color = PrimaryBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
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
                    if (downloadedAnime.description.isNotBlank() && downloadedAnime.description != "Tavsif mavjud emas.") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(BgCard)
                                .border(0.8.dp, CardBorder, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = downloadedAnime.description,
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (episodes.isNotEmpty()) {
                        val targetEp = episodes.find { it.qism == lastWatchedEp } ?: episodes.first()
                        val buttonLabel = if (episodes.any { it.qism == lastWatchedEp }) {
                            tr("${targetEp.qism}-Qismni Davom Ettirish", "Continue Episode ${targetEp.qism}", "Продолжить ${targetEp.qism} серию")
                        } else {
                            tr("${targetEp.qism}-Qismni Oflayn Ko'rish", "Watch Episode ${targetEp.qism} Offline", "Смотреть ${targetEp.qism} серию офлайн")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { playOfflineEpisode(downloadedAnime, targetEp) }
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "${tr("Yuklangan qismlar", "Downloaded Episodes", "Скачанные серии")} (${episodes.size})",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            items(episodes, key = { it.qism }) { ep ->
                val isLastWatched = ep.qism == lastWatchedEp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.5.dp)
                        .bounceClick { playOfflineEpisode(downloadedAnime, ep) }
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isLastWatched) PrimaryBlue.copy(alpha = 0.14f) else BgCard)
                        .border(
                            width = 0.8.dp,
                            color = if (isLastWatched) PrimaryBlue.copy(alpha = 0.45f) else CardBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isLastWatched) PrimaryBlue else GreenOnline.copy(alpha = 0.15f))
                                    .border(0.8.dp, if (isLastWatched) PrimaryBlue else GreenOnline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ep.qism.toString().padStart(2, '0'),
                                    color = if (isLastWatched) Color.White else GreenOnline,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ep.qism}-${tr("qism", "episode", "серия")}",
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${tr("Oflayn tayyor", "Ready offline", "Доступно офлайн")} • ${OfflineDownloadManager.formatFileSize(ep.sizeBytes)}",
                                    color = GreenOnline,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .bounceClick {
                                        OfflineDownloadManager.deleteEpisode(context, slug, ep.qism)
                                        Toast.makeText(
                                            context,
                                            tr("${ep.qism}-qism o'chirildi", "Episode ${ep.qism} deleted", "${ep.qism} серия удалена"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .clip(CircleShape)
                                    .background(RedBadge.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = tr("O'chirish", "Delete", "Удалить"),
                                    tint = RedBadge,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            containerColor = BgSurface,
            title = {
                Text(
                    text = tr("Yuklanmalarni o'chirish", "Delete Downloads", "Удалить загрузки"),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "${downloadedAnime.title} ning barcha yuklab olingan qismlarini o'chirmoqchimisiz?",
                        "Delete all downloaded episodes of ${downloadedAnime.title}?",
                        "Удалить все скачанные серии ${downloadedAnime.title}?"
                    ),
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirm = false
                        OfflineDownloadManager.deleteAnime(context, slug)
                        Toast.makeText(
                            context,
                            tr("O'chirildi", "Deleted", "Удалено"),
                            Toast.LENGTH_SHORT
                        ).show()
                        onBackClick()
                    }
                ) {
                    Text(tr("O'chirish", "Delete", "Удалить"), color = RedBadge, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(tr("Bekor qilish", "Cancel", "Отмена"), color = TextMuted)
                }
            }
        )
    }
}
