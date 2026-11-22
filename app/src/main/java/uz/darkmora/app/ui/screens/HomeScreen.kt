@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package uz.darkmora.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.app.data.Anime
import uz.darkmora.app.data.AppBroadcastItem
import uz.darkmora.app.data.HomeData
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.notifications.DarkmoraNotificationHelper
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
    var broadcasts by remember { mutableStateOf<List<AppBroadcastItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedMenuTab by remember { mutableIntStateOf(0) } // 0 = Barchasi, 1 = Yangi Qismlar, 2 = Top Xitlar
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var readNotifIds by remember { mutableStateOf(sessionManager.getReadNotificationIds()) }

    val scope = rememberCoroutineScope()

    fun fetchHome() {
        isLoading = true
        scope.launch {
            try {
                val res = DarkmoraApi.getHome(sessionManager.getToken())
                if (res.ok) {
                    homeData = res.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
        scope.launch {
            try {
                val bcRes = DarkmoraApi.getBroadcasts(10)
                if (bcRes.ok && bcRes.data != null) {
                    broadcasts = bcRes.data.broadcasts ?: emptyList()
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        fetchHome()
    }

    val latestItems = remember(homeData) {
        (homeData?.latest ?: homeData?.latestEpisodes ?: homeData?.trending ?: emptyList()).take(12)
    }

    val unreadCount = remember(latestItems, broadcasts, readNotifIds) {
        latestItems.count { !readNotifIds.contains(it.id) } +
            broadcasts.count { !readNotifIds.contains(-it.id) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ================================================================
            // 1. CLEAN PROPORTIONAL TOP HEADER (Greeting + Theme + Bell + Search + Avatar)
            // ================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand + Greeting Column (with weight(1f) so it never collides with icons)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TextPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "D",
                            color = BgSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DarkMora",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (user != null) {
                                "${tr("Salom", "Hello", "Привет")}, ${user.getEffectiveName().substringBefore(" ")}"
                            } else {
                                tr("Anime olami • Full HD", "Anime World • Full HD", "Мир Аниме • Full HD")
                            },
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right Utility Icons: [Theme Toggle] [Notifications Bell] [Search] [Avatar]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Day / Night Theme Switcher Pill
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick {
                                val nextDark = !AppThemeState.isDark
                                AppThemeState.isDark = nextDark
                                sessionManager.setDarkMode(nextDark)
                            }
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(0.8.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (AppThemeState.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Mavzu",
                            tint = if (AppThemeState.isDark) RatingGold else TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Notifications Bell with Unread Red Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick {
                                showNotificationsSheet = true
                            }
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(0.8.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Bildirishnomalar",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(7.5.dp)
                                    .clip(CircleShape)
                                    .background(RedBadge)
                            )
                        }
                    }

                    // Search Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick(onClick = onSeeAllClick)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(0.8.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Qidirish",
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Profile Avatar Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick(onClick = onProfileClick)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, CardBorder, CircleShape),
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
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profil",
                                tint = TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp, bottom = 110.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(330.dp),
                        cornerRadius = 28.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(42.dp),
                        cornerRadius = 50.dp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
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
                val trending = data.trending ?: emptyList()
                val latest = data.latest ?: data.latestEpisodes ?: emptyList()
                val topRated = data.topRated ?: data.top ?: emptyList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 2. 3D PARALLAX HERO CAROUSEL CARD
                    if (featuredList.isNotEmpty()) {
                        item {
                            HeroCarousel(
                                featuredList = featuredList,
                                onAnimeClick = onAnimeClick
                            )
                        }
                    }

                    // 3. TENSEII PILL TABS SWITCHER (Barchasi | Yangi Qismlar | Top Xitlar)
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        PillTabs(
                            titles = listOf(
                                tr("Barchasi", "All", "Все"),
                                tr("Yangi Qismlar", "New Episodes", "Новые серии"),
                                tr("Top Xitlar", "Top Hits", "Топ Хиты")
                            ),
                            selectedIndex = selectedMenuTab,
                            onSelect = { selectedMenuTab = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 4. ANIMATED CONTENT BY TAB
                    item {
                        AnimatedContent(
                            targetState = selectedMenuTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
                            },
                            label = "homeTabContent"
                        ) { tabIdx ->
                            when (tabIdx) {
                                0 -> {
                                    // ALL RAILS VIEW (Staggered Entrance)
                                    Column {
                                        if (trending.isNotEmpty()) {
                                            SectionHeader(title = tr("Trenddagi Animelar", "Trending Anime", "В тренде"), onSeeAllClick = onSeeAllClick)
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                                            ) {
                                                itemsIndexed(trending) { index, anime ->
                                                    AnimeCard(
                                                        anime = anime,
                                                        index = index,
                                                        onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                                    )
                                                }
                                            }
                                        }

                                        if (latest.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            SectionHeader(title = tr("So'nggi Qo'shilganlar", "Recently Added", "Недавно добавленные"), onSeeAllClick = onSeeAllClick)
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                                            ) {
                                                itemsIndexed(latest) { index, anime ->
                                                    AnimeCard(
                                                        anime = anime,
                                                        index = index,
                                                        onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                                    )
                                                }
                                            }
                                        }

                                        if (topRated.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            SectionHeader(title = tr("Yuqori Reyting", "Top Rated", "Высокий рейтинг"), onSeeAllClick = onSeeAllClick)
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                                            ) {
                                                itemsIndexed(topRated) { index, anime ->
                                                    AnimeCard(
                                                        anime = anime,
                                                        index = index,
                                                        onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                1 -> {
                                    // TENSEII RELEASE ROW FEED ("Yangi Qismlar")
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        latest.forEachIndexed { idx, anime ->
                                            ReleaseFeedRow(
                                                anime = anime,
                                                index = idx,
                                                badgeText = "${anime.displayEpisodes().ifBlank { tr("Yangi", "New", "Новое") }} ${tr("qism", "ep", "сер.")}",
                                                onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                            )
                                        }
                                    }
                                }

                                2 -> {
                                    // TOP RANKED RAILS ("Top Xitlar")
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        topRated.forEachIndexed { idx, anime ->
                                            ReleaseFeedRow(
                                                anime = anime,
                                                index = idx,
                                                badgeText = "#${idx + 1} TOP • ★ ${anime.displayRating()}",
                                                onClick = { onAnimeClick(anime.getEffectiveSlug()) }
                                            )
                                        }
                                    }
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
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = BgSurface)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Qayta urinish", color = BgSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ================================================================
        // NOTIFICATIONS DIALOG / SHEET
        // ================================================================
        if (showNotificationsSheet) {
            NotificationsDialog(
                notifications = latestItems,
                broadcasts = broadcasts,
                readIds = readNotifIds,
                onMarkAllRead = {
                    val allIds = latestItems.map { it.id } + broadcasts.map { -it.id }
                    sessionManager.markNotificationsRead(allIds)
                    readNotifIds = sessionManager.getReadNotificationIds()
                },
                onAnimeClick = { anime ->
                    sessionManager.markNotificationsRead(listOf(anime.id))
                    readNotifIds = sessionManager.getReadNotificationIds()
                    showNotificationsSheet = false
                    onAnimeClick(anime.getEffectiveSlug())
                },
                onBroadcastClick = { bc ->
                    sessionManager.markNotificationsRead(listOf(-bc.id))
                    readNotifIds = sessionManager.getReadNotificationIds()
                    val slug = bc.animeSlug?.trim().orEmpty()
                    if (slug.isNotBlank()) {
                        showNotificationsSheet = false
                        onAnimeClick(slug)
                    }
                },
                onDismiss = { showNotificationsSheet = false }
            )
        }
    }
}

// ============================================================================
// TENSEII-STYLE RELEASE FEED ROW CARD
// ============================================================================

@Composable
fun ReleaseFeedRow(
    anime: Anime,
    index: Int,
    badgeText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appear(index)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(0.8.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = anime.displayPoster(),
            contentDescription = anime.displayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(68.dp)
                .height(94.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgSurface)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryBlue.copy(alpha = 0.14f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = PrimaryBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = anime.displayYear(),
                    color = TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = anime.displayTitle(),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val genres = anime.displayGenres()
            if (genres.isNotEmpty()) {
                Text(
                    text = genres.replaceFirstChar { it.uppercase() },
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Ko'rish",
                tint = BgSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================================================
// NOTIFICATIONS CENTER DIALOG (SHARED BY HOME & PROFILE)
// ============================================================================

@Composable
fun NotificationsDialog(
    notifications: List<Anime>,
    readIds: Set<Int>,
    onMarkAllRead: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onDismiss: () -> Unit,
    broadcasts: List<AppBroadcastItem> = emptyList(),
    onBroadcastClick: (AppBroadcastItem) -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    val scope = rememberCoroutineScope()
    var pushEnabled by remember { mutableStateOf(sessionManager.isNotificationsEnabled()) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(BgSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Bildirishnomalar",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "O'qildi qilish",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onMarkAllRead() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // System Push Notification Toggle + Instant Test Push Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgCard)
                        .border(0.8.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Yuqori panel bildirishnomasi",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (pushEnabled) "Yangi qism chiqqanda telefon tepasidan keladi" else "O'chirilgan",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = pushEnabled,
                        onCheckedChange = { enabled ->
                            pushEnabled = enabled
                            sessionManager.setNotificationsEnabled(enabled)
                            if (enabled && notifications.isNotEmpty()) {
                                scope.launch {
                                    DarkmoraNotificationHelper.showEpisodeSystemNotification(
                                        context = context,
                                        anime = notifications.first(),
                                        isTest = true
                                    )
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue
                        )
                    )
                }

                if (notifications.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryBlue.copy(alpha = 0.12f))
                            .border(0.8.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    DarkmoraNotificationHelper.showEpisodeSystemNotification(
                                        context = context,
                                        anime = notifications.first(),
                                        isTest = true
                                    )
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔔 Telefon yuqori paneliga sinov bildirishnoma yuborish",
                            color = PrimaryBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (notifications.isEmpty() && broadcasts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hozircha yangi bildirishnomalar yo'q",
                            color = TextMuted,
                            fontSize = 13.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        itemsIndexed(broadcasts) { _, bc ->
                            val isUnread = !readIds.contains(-bc.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isUnread) PrimaryBlue.copy(alpha = 0.12f) else BgCard)
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isUnread) PrimaryBlue.copy(alpha = 0.4f) else CardBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onBroadcastClick(bc) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimaryBlue.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isUnread) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryBlue)
                                            )
                                        }
                                        Text(
                                            text = "DarkMora Admin • ${bc.createdAt ?: ""}",
                                            color = PrimaryBlue,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = bc.title,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bc.body,
                                        color = TextMuted,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        itemsIndexed(notifications) { _, anime ->
                            val isUnread = !readIds.contains(anime.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isUnread) PrimaryBlue.copy(alpha = 0.08f) else BgCard)
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isUnread) PrimaryBlue.copy(alpha = 0.3f) else CardBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onAnimeClick(anime) }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = anime.displayPoster(),
                                    contentDescription = anime.displayTitle(),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isUnread) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryBlue)
                                            )
                                        }
                                        Text(
                                            text = "Yangi qism qo'shildi!",
                                            color = PrimaryBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = anime.displayTitle(),
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${anime.displayEpisodes().ifBlank { "1" }} qism • Hoziroq tomosha qiling",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Yopish", color = BgSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
