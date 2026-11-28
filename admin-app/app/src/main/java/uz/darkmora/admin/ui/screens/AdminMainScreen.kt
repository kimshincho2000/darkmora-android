package uz.darkmora.admin.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.darkmora.admin.R
import uz.darkmora.admin.data.*
import uz.darkmora.admin.network.AdminApi
import uz.darkmora.admin.ui.components.PulsingOnlineDot
import uz.darkmora.admin.ui.components.liquidGlass
import uz.darkmora.admin.ui.components.liquidGlassPill
import uz.darkmora.admin.ui.components.pressBounce
import uz.darkmora.admin.ui.components.staggeredAppear
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
    var crashes by remember { mutableStateOf<List<AdminCrashItem>>(emptyList()) }
    var userRequests by remember { mutableStateOf<List<AdminUserRequestItem>>(emptyList()) }
    var expandedCrashId by remember { mutableStateOf<Int?>(null) }

    // Broadcast form state
    var bcTitle by remember { mutableStateOf("") }
    var bcBody by remember { mutableStateOf("") }
    var bcSlug by remember { mutableStateOf("") }
    var bcImage by remember { mutableStateOf("") }
    var isSendingBroadcast by remember { mutableStateOf(false) }

    // Smooth spinning refresh icon animation
    val infiniteSpin = rememberInfiniteTransition(label = "refresh_spin")
    val refreshAngle by infiniteSpin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "refresh_deg"
    )

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
        try {
            val crRes = AdminApi.getCrashes(adminToken, 15)
            if (crRes.ok && crRes.data != null) {
                crashes = crRes.data.crashes ?: emptyList()
            }
        } catch (_: Exception) {}
        try {
            val reqRes = AdminApi.getRequests(adminToken, "all")
            if (reqRes.ok && reqRes.data != null) {
                userRequests = reqRes.data.requests ?: emptyList()
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
        containerColor = Color.Transparent,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(24.dp),
                            tint = Color.White,
                            glassAlpha = 0.11f,
                            borderAlpha = 0.36f,
                            elevation = 14.dp
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(8.dp, RoundedCornerShape(11.dp))
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.White)
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.darkmora_logo),
                                contentDescription = "DarkMora Admin",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                "DarkMora Admin",
                                color = TextPrimary,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            val online = dashboard?.overview?.onlineCount ?: 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PulsingOnlineDot(color = AccentEmerald)
                                Text(
                                    "$online onlayn • $adminLabel",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressBounce()
                                .liquidGlass(
                                    shape = CircleShape,
                                    tint = AccentCyan,
                                    glassAlpha = 0.12f,
                                    borderAlpha = 0.34f,
                                    elevation = 6.dp
                                )
                                .clickable { refreshTick++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yangilash",
                                tint = if (isRefreshing) AccentCyan else TextPrimary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer {
                                        rotationZ = if (isRefreshing) refreshAngle else 0f
                                    }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressBounce()
                                .liquidGlass(
                                    shape = CircleShape,
                                    tint = AccentRose,
                                    glassAlpha = 0.14f,
                                    borderAlpha = 0.38f,
                                    elevation = 6.dp
                                )
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
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassPill(
                            tint = Color.White,
                            glassAlpha = 0.14f,
                            borderAlpha = 0.42f
                        )
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { (it * 0.22f * dir).toInt() } +
                        fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { (-it * 0.18f * dir).toInt() } +
                            fadeOut(tween(180)))
                },
                label = "admin_tab_switch"
            ) { tab ->
                when (tab) {
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCard(
                                        title = "Kunlik faol (DAU)",
                                        value = "${ov?.todayUnique ?: 0}",
                                        sub = "Kecha: ${ov?.yesterdayUnique ?: 0} • Bot: ${analytics?.botTodayActive ?: 0}",
                                        accent = AccentEmerald,
                                        index = 0,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        title = "Foydalanuvchilar",
                                        value = "${ov?.totalUsers ?: 0}",
                                        sub = "Sessiyalar: ${ov?.totalSessions ?: 0}",
                                        accent = AccentBlue,
                                        index = 1,
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
                                        index = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        title = "Anime / Qismlar",
                                        value = "${ov?.totalAnime ?: 0}",
                                        sub = "${ov?.totalEpisodes ?: 0} ta qism • ${ov?.pendingRequests ?: 0} so'rov",
                                        accent = AccentAmber,
                                        index = 3,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // 7-Day Daily Active Liquid Glass Bar Chart
                            if (history.isNotEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .staggeredAppear(4)
                                            .liquidGlass(RoundedCornerShape(24.dp), tint = AccentCyan)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("7 kunlik faollik grafigi", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("DAU", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        val maxVal = max(1, history.maxOf { it.uniqueVisitors })
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(126.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            history.takeLast(7).forEachIndexed { idx, pt ->
                                                val targetFrac = (pt.uniqueVisitors.toFloat() / maxVal.toFloat()).coerceIn(0.08f, 1f)
                                                var showBar by remember(pt.date) { mutableStateOf(false) }
                                                LaunchedEffect(pt.date) { showBar = true }
                                                val animFrac by animateFloatAsState(
                                                    targetValue = if (showBar) targetFrac else 0.04f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.72f,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "bar_$idx"
                                                )
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Bottom,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("${pt.uniqueVisitors}", color = TextSecondary, fontSize = 10.sp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .width(22.dp)
                                                            .fillMaxHeight(animFrac * 0.72f)
                                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                            .background(
                                                                Brush.verticalGradient(
                                                                    colors = listOf(Color.White, AccentCyan.copy(alpha = 0.78f))
                                                                )
                                                            )
                                                            .border(
                                                                width = 0.8.dp,
                                                                color = Color.White.copy(alpha = 0.55f),
                                                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                                            )
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
                                            .staggeredAppear(5)
                                            .liquidGlass(RoundedCornerShape(24.dp), tint = AccentEmerald)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Bugun eng ko'p ko'rilayotgan (24s)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        val maxC = max(1, watchedToday.maxOf { it.count })
                                        watchedToday.forEach { item ->
                                            val targetRatio = (item.count.toFloat() / maxC.toFloat()).coerceIn(0.08f, 1f)
                                            val animRatio by animateFloatAsState(
                                                targetValue = targetRatio,
                                                animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow),
                                                label = "watched_${item.title}"
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        item.title,
                                                        color = TextPrimary,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text("${item.count} marta", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(animRatio)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(50))
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    colors = listOf(AccentEmerald, Color.White)
                                                                )
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Server Health Liquid Glass Card + Animated RAM Bar
                            item {
                                val mem = sys?.memory
                                val ramPct = ((mem?.usedPct ?: 0f) / 100f).coerceIn(0f, 1f)
                                val ramColor = when {
                                    ramPct > 0.85f -> AccentRose
                                    ramPct > 0.65f -> AccentAmber
                                    else -> AccentEmerald
                                }
                                val animRam by animateFloatAsState(
                                    targetValue = ramPct,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
                                    label = "ram_bar"
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(6)
                                        .liquidGlass(RoundedCornerShape(24.dp), tint = ramColor)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Server va Himoya", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${mem?.usedPct ?: 0f}% RAM",
                                            color = ramColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White.copy(alpha = 0.09f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animRam.coerceAtLeast(0.03f))
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(50))
                                                .background(
                                                    Brush.horizontalGradient(listOf(ramColor, Color.White.copy(alpha = 0.9f)))
                                                )
                                        )
                                    }
                                    Text(
                                        text = "RAM: ${mem?.usedMb ?: 0} / ${mem?.totalMb ?: 4096} MB • Pressure: ${sys?.pressure ?: "0.00"}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .liquidGlassPill(tint = AccentEmerald, glassAlpha = 0.12f, borderAlpha = 0.28f)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text("Shield: ${sys?.shieldStatus ?: "Faol"}", color = TextPrimary, fontSize = 11.sp)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .liquidGlassPill(tint = AccentRose, glassAlpha = 0.12f, borderAlpha = 0.28f)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text("Bloklangan IP: ${ov?.totalBannedIps ?: 0}", color = TextPrimary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // User Requests & Anime Orders Glass Card
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(7)
                                        .liquidGlass(RoundedCornerShape(24.dp), tint = AccentAmber)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val pendingCount = userRequests.count { it.status == "pending" }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Murojaat va Buyurtmalar", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (pendingCount > 0) "$pendingCount ta yangi" else "${userRequests.size} ta",
                                            color = if (pendingCount > 0) AccentAmber else AccentEmerald,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (userRequests.isEmpty()) {
                                        Text(
                                            text = "Hozircha foydalanuvchilardan murojaat yoki anime buyurtmalari yo'q.",
                                            color = TextMuted,
                                            fontSize = 12.5.sp
                                        )
                                    } else {
                                        userRequests.take(15).forEach { reqItem ->
                                            val isPending = reqItem.status == "pending"
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .liquidGlass(
                                                        shape = RoundedCornerShape(16.dp),
                                                        tint = if (isPending) AccentAmber else AccentEmerald,
                                                        glassAlpha = 0.08f,
                                                        borderAlpha = 0.22f,
                                                        elevation = 4.dp
                                                    )
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = reqItem.title.ifBlank { "Murojaat #${reqItem.id}" },
                                                        color = TextPrimary,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = if (isPending) "Kutilmoqda" else "Bajarildi",
                                                        color = if (isPending) AccentAmber else AccentEmerald,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (!reqItem.details.isNullOrBlank()) {
                                                    Text(
                                                        text = reqItem.details,
                                                        color = TextSecondary,
                                                        fontSize = 12.sp,
                                                        lineHeight = 17.sp
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val metaInfo = listOfNotNull(
                                                        reqItem.contact?.takeIf { it.isNotBlank() },
                                                        reqItem.ip?.takeIf { it.isNotBlank() },
                                                        reqItem.createdAt?.takeIf { it.isNotBlank() }
                                                    ).joinToString(" • ")
                                                    Text(metaInfo, color = TextMuted, fontSize = 10.5.sp, modifier = Modifier.weight(1f))

                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        if (isPending) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .pressBounce()
                                                                    .liquidGlassPill(AccentEmerald, glassAlpha = 0.16f, borderAlpha = 0.35f)
                                                                    .clickable {
                                                                        scope.launch {
                                                                            try {
                                                                                AdminApi.updateRequestStatus(adminToken, reqItem.id, "done")
                                                                                refreshTick++
                                                                            } catch (_: Exception) {}
                                                                        }
                                                                    }
                                                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                                            ) {
                                                                Text("✓ Bajarildi", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .pressBounce()
                                                                .liquidGlassPill(AccentRose, glassAlpha = 0.14f, borderAlpha = 0.32f)
                                                                .clickable {
                                                                    scope.launch {
                                                                        try {
                                                                            AdminApi.deleteRequest(adminToken, reqItem.id)
                                                                            refreshTick++
                                                                        } catch (_: Exception) {}
                                                                    }
                                                                }
                                                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("O'chirish", color = AccentRose, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Crash Reports Glass Card with Smooth Expand/Collapse
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(8)
                                        .liquidGlass(
                                            RoundedCornerShape(24.dp),
                                            tint = if (crashes.isEmpty()) AccentEmerald else AccentRose
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Ilova xatoliklari (Crash)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (crashes.isEmpty()) "0 ta • Barqaror" else "${crashes.size} ta",
                                            color = if (crashes.isEmpty()) AccentEmerald else AccentRose,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (crashes.isEmpty()) {
                                        Text(
                                            text = "Hozircha foydalanuvchilardan xatolik (crash) qayd etilmagan.",
                                            color = TextMuted,
                                            fontSize = 12.5.sp
                                        )
                                    } else {
                                        crashes.take(10).forEach { cr ->
                                            val isExpanded = expandedCrashId == cr.id
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .liquidGlass(
                                                        shape = RoundedCornerShape(16.dp),
                                                        tint = AccentRose,
                                                        glassAlpha = 0.08f,
                                                        borderAlpha = 0.24f,
                                                        elevation = 4.dp
                                                    )
                                                    .clickable { expandedCrashId = if (isExpanded) null else cr.id }
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = cr.errorTitle.ifBlank { "Noma'lum xato" },
                                                        color = AccentRose,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = if (isExpanded) 4 else 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(cr.createdAt, color = TextMuted, fontSize = 10.5.sp)
                                                }
                                                Text(
                                                    text = "v${cr.appVersion} (${cr.versionCode}) • ${cr.deviceModel} • ${cr.androidVersion}",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                AnimatedVisibility(
                                                    visible = isExpanded && cr.stackTrace.isNotBlank(),
                                                    enter = expandVertically() + fadeIn(),
                                                    exit = shrinkVertically() + fadeOut()
                                                ) {
                                                    Text(
                                                        text = cr.stackTrace,
                                                        color = TextMuted,
                                                        fontSize = 10.sp,
                                                        lineHeight = 13.sp,
                                                        modifier = Modifier.padding(top = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("Eng kuchli (Top) animelar", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            itemsIndexed(topAnimes) { idx, anime ->
                                val rankColor = when (idx) {
                                    0 -> AccentAmber
                                    1 -> Color(0xFFE2E8F0)
                                    2 -> Color(0xFFFB923C)
                                    else -> TextMuted
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(idx)
                                        .liquidGlass(
                                            shape = RoundedCornerShape(20.dp),
                                            tint = if (idx < 3) rankColor else Color.White,
                                            glassAlpha = if (idx < 3) 0.12f else 0.08f,
                                            borderAlpha = if (idx < 3) 0.36f else 0.24f,
                                            elevation = 8.dp
                                        )
                                        .padding(11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(rankColor.copy(alpha = if (idx < 3) 0.22f else 0.08f))
                                            .border(1.dp, rankColor.copy(alpha = 0.45f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("#${idx + 1}", color = rankColor, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    AsyncImage(
                                        model = anime.poster,
                                        contentDescription = anime.displayName(),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(46.dp)
                                            .height(62.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(BgCardElevated)
                                            .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            anime.displayName(),
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${anime.year?.ifBlank { "2026" } ?: "2026"} • ${anime.genres?.ifBlank { "Anime" } ?: "Anime"}",
                                            color = TextSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatCount(anime.views), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                        Text("ko'rish", color = TextMuted, fontSize = 10.5.sp)
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PulsingOnlineDot(color = AccentEmerald)
                                    Text("Jonli faoliyat (hozir nima ko'rilyapti)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            itemsIndexed(activities.take(35)) { idx, act ->
                                val badgeColor = when (act.actionType) {
                                    "watch", "stream" -> AccentEmerald
                                    "search" -> AccentAmber
                                    "login" -> AccentBlue
                                    else -> AccentViolet
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(idx.coerceAtMost(8))
                                        .liquidGlass(
                                            shape = RoundedCornerShape(18.dp),
                                            tint = badgeColor,
                                            glassAlpha = 0.08f,
                                            borderAlpha = 0.24f,
                                            elevation = 6.dp
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor.copy(alpha = 0.20f))
                                            .border(1.dp, badgeColor.copy(alpha = 0.45f), CircleShape),
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
                                        val actLabel = act.actionLabel?.takeIf { it.isNotBlank() } ?: act.label?.takeIf { it.isNotBlank() }
                                        Text(
                                            text = act.title?.ifBlank { actLabel ?: "Faoliyat" } ?: "Faoliyat",
                                            color = TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val who = act.username?.takeIf { it.isNotBlank() }?.let {
                                            if (it.startsWith("@") || it.startsWith("ID:")) it else "@$it"
                                        } ?: act.user?.takeIf { it.isNotBlank() && it != "Mehmon" } ?: (act.ip ?: "Mehmon")
                                        val labelPart = actLabel?.let { " • $it" } ?: ""
                                        val det = act.details?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                                        Text(
                                            "$who$labelPart$det • ${act.device ?: ""}",
                                            color = TextSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        act.timeAgo?.ifBlank { act.time?.ifBlank { act.createdAt?.takeLast(5) ?: "" } ?: "" } ?: "",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
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
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = userSearch,
                                onValueChange = { userSearch = it },
                                placeholder = { Text("Login, email yoki telefon qidirish...", color = TextMuted, fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentCyan, modifier = Modifier.size(19.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(50),
                                colors = broadcastGlassFieldColors(),
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
                                itemsIndexed(users, key = { _, it -> it.id }) { idx, u ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .staggeredAppear(idx.coerceAtMost(10))
                                            .liquidGlass(
                                                shape = RoundedCornerShape(20.dp),
                                                tint = AccentBlue,
                                                glassAlpha = 0.09f,
                                                borderAlpha = 0.26f,
                                                elevation = 7.dp
                                            )
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val initChar = (u.displayName?.firstOrNull() ?: u.username?.firstOrNull() ?: 'U').uppercaseChar().toString()
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color.White, Color(0xFFD8F1FF))
                                                    )
                                                )
                                                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(initChar, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(0)
                                        .liquidGlass(
                                            shape = RoundedCornerShape(26.dp),
                                            tint = AccentViolet,
                                            glassAlpha = 0.11f,
                                            borderAlpha = 0.34f,
                                            elevation = 14.dp
                                        )
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Barcha foydalanuvchilarga bildirishnoma", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
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
                                        shape = RoundedCornerShape(16.dp),
                                        colors = broadcastGlassFieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = bcBody,
                                        onValueChange = { bcBody = it },
                                        placeholder = { Text("Bildirishnoma matni...", color = TextMuted, fontSize = 13.5.sp) },
                                        minLines = 3,
                                        maxLines = 5,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = broadcastGlassFieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = bcSlug,
                                        onValueChange = { bcSlug = it },
                                        placeholder = { Text("Anime slug (ixtiyoriy, masalan: one-piece)", color = TextMuted, fontSize = 13.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = broadcastGlassFieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .pressBounce(enabled = !isSendingBroadcast)
                                            .shadow(14.dp, RoundedCornerShape(50), ambientColor = Color.White.copy(alpha = 0.25f))
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color.White, Color(0xFFE6F4FF), Color.White)
                                                )
                                            )
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
                                                Text("Hammaga yuborish", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text("Yuborilgan bildirishnomalar (${broadcasts.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            itemsIndexed(broadcasts, key = { _, it -> it.id }) { idx, bc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredAppear(idx.coerceAtMost(8))
                                        .liquidGlass(
                                            shape = RoundedCornerShape(20.dp),
                                            tint = AccentCyan,
                                            glassAlpha = 0.08f,
                                            borderAlpha = 0.24f,
                                            elevation = 6.dp
                                        )
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
                                        Text("Slug: ${bc.animeSlug}", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
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
    index: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .staggeredAppear(index)
            .pressBounce(pressedScale = 0.97f)
            .liquidGlass(
                shape = RoundedCornerShape(22.dp),
                tint = accent,
                glassAlpha = 0.11f,
                borderAlpha = 0.34f,
                elevation = 10.dp
            )
    ) {
        // Subtle corner accent light bleed inside the glass
        Box(
            modifier = Modifier
                .size(84.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
                Text(title, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdminNavPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Transparent,
        animationSpec = tween(240),
        label = "nav_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.Black else TextSecondary,
        animationSpec = tween(240),
        label = "nav_fg"
    )
    val hPad by animateDpAsState(
        targetValue = if (selected) 14.dp else 10.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "nav_pad"
    )

    Row(
        modifier = Modifier
            .pressBounce()
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
        )
        AnimatedVisibility(visible = selected) {
            Text(label, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun broadcastGlassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0x99FFFFFF),
    unfocusedBorderColor = Color(0x2EFFFFFF),
    focusedContainerColor = Color(0x73151B2C),
    unfocusedContainerColor = Color(0x520D111C),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentCyan
)

private fun formatCount(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
