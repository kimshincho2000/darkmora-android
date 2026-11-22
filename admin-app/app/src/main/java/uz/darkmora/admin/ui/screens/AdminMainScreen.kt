package uz.darkmora.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.admin.data.*
import uz.darkmora.admin.network.AdminApi
import uz.darkmora.admin.ui.theme.*
import kotlin.math.max

@Composable
fun AdminMainScreen(
    adminToken: String,
    adminLabel: String,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 0 = Statistika, 1 = Jonli & Top, 2 = Foydalanuvchilar, 3 = Bildirishnoma
    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    var dashboard by remember { mutableStateOf<AdminDashboardData?>(null) }
    var activities by remember { mutableStateOf<List<AdminActivityItem>>(emptyList()) }
    var users by remember { mutableStateOf<List<AdminUserItem>>(emptyList()) }
    var userSearch by remember { mutableStateOf("") }
    var broadcasts by remember { mutableStateOf<List<AdminBroadcastItem>>(emptyList()) }

    // Broadcast form state
    var bcTitle by remember { mutableStateOf("") }
    var bcBody by remember { mutableStateOf("") }
    var bcSlug by remember { mutableStateOf("") }
    var bcImage by remember { mutableStateOf("") }
    var isSendingBroadcast by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        isRefreshing = true
        try {
            val dashRes = AdminApi.getDashboardStats(adminToken)
            if (dashRes.ok && dashRes.data != null) {
                dashboard = dashRes.data
            }
        } catch (_: Exception) {}
        try {
            val actRes = AdminApi.getActivities(adminToken, 60)
            if (actRes.ok && actRes.data != null) {
                activities = actRes.data.activities ?: emptyList()
            }
        } catch (_: Exception) {}
        try {
            val bcRes = AdminApi.getBroadcasts(adminToken, 25)
            if (bcRes.ok && bcRes.data != null) {
                broadcasts = bcRes.data.broadcasts ?: emptyList()
            }
        } catch (_: Exception) {}
        isRefreshing = false
    }

    LaunchedEffect(userSearch, refreshTick) {
        try {
            val uRes = AdminApi.getUsers(adminToken, userSearch.trim(), 80)
            if (uRes.ok && uRes.data != null) {
                users = uRes.data.users ?: emptyList()
            }
        } catch (_: Exception) {}
    }

    Scaffold(
        containerColor = BgBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                    }
                    Column {
                        Text("DarkMora Admin", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        val online = dashboard?.overview?.onlineCount ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentEmerald))
                            Text("$online onlayn • $adminLabel", color = TextSecondary, fontSize = 11.5.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BgCardElevated)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable { refreshTick++ },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Refresh, "Yangilash", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BgCardElevated)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable {
                                scope.launch {
                                    try { AdminApi.logout(adminToken) } catch (_: Exception) {}
                                    onLogout()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Logout, "Chiqish", tint = AccentRose, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgBlack)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF141416))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminNavPill("Statistika", Icons.Default.BarChart, selectedTab == 0) { selectedTab = 0 }
                AdminNavPill("Jonli", Icons.Default.Whatshot, selectedTab == 1) { selectedTab = 1 }
                AdminNavPill("Userlar", Icons.Default.People, selectedTab == 2) { selectedTab = 2 }
                AdminNavPill("Xabar", Icons.Default.NotificationsActive, selectedTab == 3) { selectedTab = 3 }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgBlack)
        ) {
            when (selectedTab) {
                // ============================================================
                // TAB 0: STATISTIKA & SERVER HOLATI
                // ============================================================
                0 -> {
                    val ov = dashboard?.overview
                    val sys = dashboard?.system
                    val analytics = dashboard?.dailyAnalytics
                    val history = (analytics?.history ?: emptyList()).reversed()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard(
                                    title = "Kunlik faol (DAU)",
                                    value = "${ov?.todayUnique ?: 0}",
                                    sub = "Kecha: ${ov?.yesterdayUnique ?: 0} • Bot: ${analytics?.botTodayActive ?: 0}",
                                    accent = AccentEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Foydalanuvchilar",
                                    value = "${ov?.totalUsers ?: 0}",
                                    sub = "Sessiyalar: ${ov?.totalSessions ?: 0}",
                                    accent = AccentBlue,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard(
                                    title = "Jami ko'rishlar",
                                    value = formatCount(ov?.totalViews ?: 0),
                                    sub = "Bugun harakat: ${ov?.todayTotal ?: 0}",
                                    accent = AccentViolet,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Anime / Qismlar",
                                    value = "${ov?.totalAnime ?: 0}",
                                    sub = "${ov?.totalEpisodes ?: 0} ta qism • ${ov?.pendingRequests ?: 0} so'rov",
                                    accent = AccentAmber,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 7-Day Daily Active Bar Chart
                        if (history.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("7 kunlik faollik grafigi", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    val maxVal = max(1, history.maxOf { it.uniqueVisitors })
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        history.takeLast(7).forEach { pt ->
                                            val frac = (pt.uniqueVisitors.toFloat() / maxVal.toFloat()).coerceIn(0.08f, 1f)
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Bottom,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("${pt.uniqueVisitors}", color = TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .width(20.dp)
                                                        .fillMaxHeight(frac * 0.72f)
                                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                        .background(Color.White)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = pt.date.takeLast(5),
                                                    color = TextMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Top Watched Today (Last 24h)
                        val watchedToday = analytics?.topWatchedToday ?: emptyList()
                        if (watchedToday.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Bugun eng ko'p ko'rilayotgan (24s)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    val maxC = max(1, watchedToday.maxOf { it.count })
                                    watchedToday.forEach { item ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(item.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Text("${item.count} marta", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(BgCardElevated)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth((item.count.toFloat() / maxC.toFloat()).coerceIn(0.08f, 1f))
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Color.White)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Server Health Card
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Server va Himoya", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val mem = sys?.memory
                                Text(
                                    text = "RAM: ${mem?.usedMb ?: 0} / ${mem?.totalMb ?: 4096} MB (${mem?.usedPct ?: 0f}%) • Pressure: ${sys?.pressure ?: "0.00"}",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    text = "Shield: ${sys?.shieldStatus ?: "Faol"} • Bloklangan IP: ${ov?.totalBannedIps ?: 0}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ============================================================
                // TAB 1: JONLI KO'RILAYOTGAN & ENG KUCHLI (TOP) ANIMELAR
                // ============================================================
                1 -> {
                    val topAnimes = dashboard?.topAnime ?: emptyList()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("Eng kuchli (Top) animelar", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        itemsIndexed(topAnimes) { idx, anime ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("#${idx + 1}", color = if (idx < 3) Color.White else TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                AsyncImage(
                                    model = anime.poster,
                                    contentDescription = anime.displayName(),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(46.dp)
                                        .height(62.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BgCardElevated)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(anime.displayName(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${anime.year?.ifBlank { "2026" } ?: "2026"} • ${anime.genres?.ifBlank { "Anime" } ?: "Anime"}",
                                        color = TextMuted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatCount(anime.views), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("ko'rish", color = TextMuted, fontSize = 10.5.sp)
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Jonli faoliyat (hozir nima ko'rilyapti)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        items(activities.take(35)) { act ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val badgeColor = when (act.actionType) {
                                    "watch", "stream" -> AccentEmerald
                                    "search" -> AccentAmber
                                    "login" -> AccentBlue
                                    else -> AccentViolet
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (act.actionType) {
                                            "watch", "stream" -> Icons.Default.PlayArrow
                                            "search" -> Icons.Default.Search
                                            "login" -> Icons.Default.Login
                                            else -> Icons.Default.Visibility
                                        },
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = act.title?.ifBlank { act.label ?: "Faoliyat" } ?: "Faoliyat",
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val who = act.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: (act.ip ?: "Mehmon")
                                    val det = act.details?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                                    Text("$who$det • ${act.device ?: ""}", color = TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(act.timeAgo?.ifBlank { act.createdAt?.takeLast(5) ?: "" } ?: "", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // ============================================================
                // TAB 2: RO'YXATDAN O'TGAN FOYDALANUVCHILAR
                // ============================================================
                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = userSearch,
                            onValueChange = { userSearch = it },
                            placeholder = { Text("Login, email yoki telefon qidirish...", color = TextMuted, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(19.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0x66FFFFFF),
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgCard,
                                unfocusedContainerColor = BgCard,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Ro'yxatdan o'tganlar (${users.size} / jami ${dashboard?.overview?.totalUsers ?: users.size})",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(users, key = { it.id }) { u ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val initChar = (u.displayName?.firstOrNull() ?: u.username?.firstOrNull() ?: 'U').uppercaseChar().toString()
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(initChar, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = u.displayName?.ifBlank { u.username ?: "User #${u.id}" } ?: "User #${u.id}",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val contact = listOfNotNull(
                                            u.username?.takeIf { it.isNotBlank() }?.let { "@$it" },
                                            u.email?.takeIf { it.isNotBlank() },
                                            u.phone?.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        Text(contact.ifBlank { "ID: ${u.id}" }, color = TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "Ro'yxat: ${u.createdAt?.ifBlank { "-" } ?: "-"} • Oxirgi: ${u.lastLogin?.ifBlank { "-" } ?: "-"}",
                                            color = TextMuted,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // TAB 3: BARCHA FOYDALANUVCHILARGA BILDIRISHNOMA YUBORISH
                // ============================================================
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Barcha foydalanuvchilarga bildirishnoma", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Bu yerdan yuborilgan xabar barcha DarkMora ilovasi foydalanuvchilariga push-bildirishnoma sifatida boradi.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )

                                OutlinedTextField(
                                    value = bcTitle,
                                    onValueChange = { bcTitle = it },
                                    placeholder = { Text("Sarlavha (masalan: Yangi qism chiqdi!)", color = TextMuted, fontSize = 13.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = broadcastFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = bcBody,
                                    onValueChange = { bcBody = it },
                                    placeholder = { Text("Bildirishnoma matni...", color = TextMuted, fontSize = 13.5.sp) },
                                    minLines = 3,
                                    maxLines = 5,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = broadcastFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = bcSlug,
                                    onValueChange = { bcSlug = it },
                                    placeholder = { Text("Anime slug (ixtiyoriy, masalan: one-piece)", color = TextMuted, fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = broadcastFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                        .clickable(enabled = !isSendingBroadcast) {
                                            if (bcTitle.isBlank() || bcBody.isBlank()) {
                                                Toast.makeText(context, "Sarlavha va matnni kiriting", Toast.LENGTH_SHORT).show()
                                                return@clickable
                                            }
                                            scope.launch {
                                                isSendingBroadcast = true
                                                try {
                                                    val res = AdminApi.sendBroadcast(
                                                        adminToken,
                                                        bcTitle.trim(),
                                                        bcBody.trim(),
                                                        bcSlug.trim(),
                                                        bcImage.trim()
                                                    )
                                                    if (res.ok) {
                                                        bcTitle = ""
                                                        bcBody = ""
                                                        bcSlug = ""
                                                        bcImage = ""
                                                        refreshTick++
                                                        Toast.makeText(context, "Bildirishnoma yuborildi!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, res.error ?: "Yuborishda xatolik", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Xato: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isSendingBroadcast = false
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSendingBroadcast) {
                                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.2.dp, modifier = Modifier.size(20.dp))
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Send, null, tint = Color.Black, modifier = Modifier.size(17.dp))
                                            Text("Hammaga yuborish", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Yuborilgan bildirishnomalar (${broadcasts.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        items(broadcasts, key = { it.id }) { bc ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(bc.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(bc.createdAt ?: "", color = TextMuted, fontSize = 11.sp)
                                }
                                Text(bc.body, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
                                if (!bc.animeSlug.isNullOrBlank()) {
                                    Text("Slug: ${bc.animeSlug}", color = AccentBlue, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(title, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AdminNavPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.Black else TextSecondary,
            modifier = Modifier.size(19.dp)
        )
        if (selected) {
            Text(label, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun broadcastFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0x88FFFFFF),
    unfocusedBorderColor = BorderSubtle,
    focusedContainerColor = BgCardElevated,
    unfocusedContainerColor = Color(0xFF141416),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Color.White
)

private fun formatCount(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
