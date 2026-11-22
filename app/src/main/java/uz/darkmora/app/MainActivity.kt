@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package uz.darkmora.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import uz.darkmora.app.data.UpdateInfo
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.notifications.DarkmoraNotificationHelper
import uz.darkmora.app.ui.components.IntroOverlay
import uz.darkmora.app.ui.screens.*
import uz.darkmora.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var pendingTargetSlug by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = UserSessionManager(this)
        AppThemeState.isDark = sessionManager.isDarkMode()
        AppThemeState.language = sessionManager.getLanguage()

        pendingTargetSlug = extractTargetSlug(intent)
        DarkmoraNotificationHelper.schedulePeriodicCheck(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DarkMoraTheme(isDarkTheme = AppThemeState.isDark) {
                MainApp(
                    initialTargetSlug = pendingTargetSlug,
                    onTargetSlugConsumed = { pendingTargetSlug = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val slug = extractTargetSlug(intent)
        if (!slug.isNullOrBlank()) {
            pendingTargetSlug = slug
        }
    }

    private fun extractTargetSlug(intent: Intent?): String? {
        val extraSlug = intent?.getStringExtra("target_anime_slug")?.trim()
        if (!extraSlug.isNullOrBlank()) return extraSlug

        val uri = intent?.data ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host == "darkmora.uz" || host == "www.darkmora.uz") {
            val segments = uri.pathSegments
            if (segments.size >= 2 && segments[0].equals("anime", ignoreCase = true)) {
                return segments[1].trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}

@Composable
fun MainApp(
    initialTargetSlug: String? = null,
    onTargetSlugConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var showIntroOverlay by remember { mutableStateOf(initialTargetSlug.isNullOrBlank()) }
    var autoUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                DarkmoraNotificationHelper.checkAndDispatchNewNotifications(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !DarkmoraNotificationHelper.hasNotificationPermission(context)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DarkmoraNotificationHelper.checkAndDispatchNewNotifications(context)
        }

        try {
            val res = DarkmoraApi.checkUpdate()
            if (res.ok && res.data != null && res.data.versionCode > BuildConfig.VERSION_CODE) {
                autoUpdateInfo = res.data
                showAutoUpdateDialog = true
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(initialTargetSlug) {
        if (!initialTargetSlug.isNullOrBlank()) {
            showIntroOverlay = false
            navController.navigate("detail/$initialTargetSlug")
            onTargetSlugConsumed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        NavHost(
            navController = navController,
            startDestination = "main_tabs",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("main_tabs") {
                MainTabsScreen(
                    onAnimeClick = { slug ->
                        navController.navigate("detail/$slug")
                    }
                )
            }

            composable(
                route = "detail/{slug}",
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("slug") ?: ""
                AnimeDetailScreen(
                    slug = slug,
                    onBackClick = { navController.popBackStack() },
                    onPlayEpisode = { b64Url, b64Title, animeSlug, epNum ->
                        navController.navigate("player/$b64Url/$b64Title?slug=$animeSlug&ep=$epNum")
                    }
                )
            }

            composable(
                route = "player/{videoUrl}/{title}?slug={slug}&ep={ep}",
                arguments = listOf(
                    navArgument("videoUrl") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("slug") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("ep") {
                        type = NavType.IntType
                        defaultValue = 1
                    }
                )
            ) { backStackEntry ->
                val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                val slug = backStackEntry.arguments?.getString("slug") ?: ""
                val ep = backStackEntry.arguments?.getInt("ep") ?: 1
                PlayerScreen(
                    videoUrlEncoded = videoUrl,
                    titleEncoded = title,
                    slug = slug,
                    initialEpisode = ep,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // Tenseii-style Animated Calligraphy Splash / Intro Overlay on App Launch
        if (showIntroOverlay) {
            IntroOverlay(
                onFinished = { showIntroOverlay = false }
            )
        }

        if (showAutoUpdateDialog && autoUpdateInfo != null) {
            UpdateAppDialog(
                info = autoUpdateInfo!!,
                onDismiss = { showAutoUpdateDialog = false }
            )
        }
    }
}

// ============================================================================
// MAIN TABS + FLUID CURVED LIQUID DROPLET BOTTOM NAVIGATION BAR
// ============================================================================

data class NavItem(
    val title: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

@Composable
fun MainTabsScreen(
    onAnimeClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    val navItems = remember {
        listOf(
            NavItem("Asosiy", Icons.Filled.Home, Icons.Outlined.Home),
            NavItem("Katalog", Icons.Filled.GridView, Icons.Outlined.GridView),
            NavItem("Sevimlilar", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
            NavItem("Profil", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    val pagerState = rememberPagerState(pageCount = { navItems.size })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onAnimeClick = onAnimeClick,
                    onSeeAllClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 1,
                                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                            )
                        }
                    },
                    onProfileClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 3,
                                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                )
                1 -> CatalogScreen(
                    onAnimeClick = onAnimeClick
                )
                2 -> FavoritesScreen(
                    onAnimeClick = onAnimeClick,
                    onExploreClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 1,
                                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                )
                3 -> ProfileScreen(
                    onNavigateToFavorites = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 2,
                                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                            )
                        }
                    },
                    onAnimeClick = onAnimeClick
                )
            }
        }

        // Soft ambient bottom gradient behind the curved navigation bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BgDark.copy(alpha = 0.7f),
                            BgDark
                        )
                    )
                )
        )

        // Fluid Curved Liquid Droplet Bottom Navigation Bar (Adapts to Light & Dark themes!)
        FluidCurvedBottomNav(
            items = navItems,
            pagerState = pagerState,
            onItemSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun FluidCurvedBottomNav(
    items: List<NavItem>,
    pagerState: PagerState,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tabCount = items.size
    val isDark = AppThemeState.isDark

    val rawContinuousPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (tabCount - 1).toFloat())

    val animatedPage by animateFloatAsState(
        targetValue = rawContinuousPage,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fluidNotchPage"
    )

    val nearestTab = animatedPage.roundToInt()
    val fractionalDist = (abs(animatedPage - nearestTab) * 2f).coerceIn(0f, 1f)
    val transitPhase = ((1f - cos(fractionalDist * Math.PI.toFloat())) * 0.5f).coerceIn(0f, 1f)

    val barTopOffsetDp = 24.dp
    val barHeightDp = 64.dp
    val orbSizeDp = 52.dp

    val barTopColor = NavSurface
    val barBottomColor = if (isDark) Color(0xFF10121C) else Color(0xFFF3F4F6)
    val activeOrbColor = NavActiveOrb
    val activeIconColor = NavActiveIcon
    val inactiveIconColor = NavInactiveIcon

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val tabWidthPx = totalWidthPx / tabCount
        val barTopPx = with(density) { barTopOffsetDp.toPx() }
        val orbSizePx = with(density) { orbSizeDp.toPx() }
        val orbRadiusPx = orbSizePx / 2f

        val notchCenterX = (animatedPage + 0.5f) * tabWidthPx

        val settledDepthPx = with(density) { 33.dp.toPx() }
        val transitPeakPx = with(density) { (-9).dp.toPx() }
        val currentNotchDepthPx = settledDepthPx * (1f - transitPhase) + transitPeakPx * transitPhase

        val notchHalfWidthPx = with(density) { 47.dp.toPx() } * (1f - 0.15f * transitPhase)

        val orbCenterYSettled = barTopPx + with(density) { 3.dp.toPx() }
        val orbCenterYTransit = barTopPx + with(density) { 26.dp.toPx() }
        val currentOrbCenterY = orbCenterYSettled * (1f - transitPhase) + orbCenterYTransit * transitPhase
        val currentOrbScale = 1f - 0.35f * transitPhase

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barTopOffsetDp + barHeightDp)
            ) {
                // 1. LIQUID CURVED BAR CANVAS
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width
                    val h = size.height

                    val leftStart = (notchCenterX - notchHalfWidthPx).coerceAtLeast(0f)
                    val rightEnd = (notchCenterX + notchHalfWidthPx).coerceAtMost(w)
                    val c1Offset = notchHalfWidthPx * 0.46f
                    val c2Offset = notchHalfWidthPx * 0.42f

                    val barPath = Path().apply {
                        moveTo(0f, barTopPx)
                        if (leftStart > 0f) {
                            lineTo(leftStart, barTopPx)
                        }

                        cubicTo(
                            x1 = leftStart + c1Offset,
                            y1 = barTopPx,
                            x2 = notchCenterX - c2Offset,
                            y2 = barTopPx + currentNotchDepthPx,
                            x3 = notchCenterX,
                            y3 = barTopPx + currentNotchDepthPx
                        )

                        cubicTo(
                            x1 = notchCenterX + c2Offset,
                            y1 = barTopPx + currentNotchDepthPx,
                            x2 = rightEnd - c1Offset,
                            y2 = barTopPx,
                            x3 = rightEnd,
                            y3 = barTopPx
                        )

                        if (rightEnd < w) {
                            lineTo(w, barTopPx)
                        }
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }

                    drawPath(
                        path = barPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(barTopColor, barBottomColor),
                            startY = barTopPx,
                            endY = h
                        ),
                        style = Fill
                    )

                    val topRimPath = Path().apply {
                        moveTo(0f, barTopPx)
                        if (leftStart > 0f) lineTo(leftStart, barTopPx)
                        cubicTo(
                            x1 = leftStart + c1Offset,
                            y1 = barTopPx,
                            x2 = notchCenterX - c2Offset,
                            y2 = barTopPx + currentNotchDepthPx,
                            x3 = notchCenterX,
                            y3 = barTopPx + currentNotchDepthPx
                        )
                        cubicTo(
                            x1 = notchCenterX + c2Offset,
                            y1 = barTopPx + currentNotchDepthPx,
                            x2 = rightEnd - c1Offset,
                            y2 = barTopPx,
                            x3 = rightEnd,
                            y3 = barTopPx
                        )
                        if (rightEnd < w) lineTo(w, barTopPx)
                    }

                    val rimColors = if (isDark) {
                        listOf(Color(0x18FFFFFF), Color(0x4560A5FA), Color(0x18FFFFFF))
                    } else {
                        listOf(Color(0x12000000), Color(0x22000000), Color(0x12000000))
                    }

                    drawPath(
                        path = topRimPath,
                        brush = Brush.horizontalGradient(colors = rimColors),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 2. FLOATING LIQUID DROPLET ORB
                val activeItem = items[nearestTab.coerceIn(0, tabCount - 1)]
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (notchCenterX - orbRadiusPx).roundToInt(),
                                y = (currentOrbCenterY - orbRadiusPx).roundToInt()
                            )
                        }
                        .size(orbSizeDp)
                        .graphicsLayer {
                            scaleX = currentOrbScale
                            scaleY = currentOrbScale
                            rotationZ = (rawContinuousPage - animatedPage) * -18f
                        }
                        .shadow(
                            elevation = if (isDark) 14.dp else 6.dp,
                            shape = CircleShape,
                            ambientColor = if (isDark) PrimaryGlow else Color(0x26000000),
                            spotColor = if (isDark) PrimaryBlue else Color(0x33000000)
                        )
                        .clip(CircleShape)
                        .background(activeOrbColor)
                        .border(1.5.dp, activeOrbColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activeItem.activeIcon,
                        contentDescription = activeItem.title,
                        tint = activeIconColor,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                val iconPop = (1f - transitPhase * 1.15f).coerceIn(0f, 1f)
                                scaleX = 0.6f + 0.4f * iconPop
                                scaleY = 0.6f + 0.4f * iconPop
                                alpha = iconPop
                            }
                    )
                }

                // 3. INACTIVE TAB ICONS ROW ON THE BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeightDp)
                        .align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val distFromNotch = abs(animatedPage - index)
                        val inBarVisibility = ((distFromNotch - 0.25f) / 0.55f).coerceIn(0f, 1f)

                        val iconTint by animateColorAsState(
                            targetValue = if (distFromNotch < 0.5f) TextPrimary else inactiveIconColor,
                            animationSpec = tween(250),
                            label = "inactiveIconTint"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onItemSelected(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.inactiveIcon,
                                contentDescription = item.title,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        alpha = inBarVisibility
                                        val s = 0.75f + 0.25f * inBarVisibility
                                        scaleX = s
                                        scaleY = s
                                        translationY = (1f - inBarVisibility) * -14f
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBottomColor)
                    .navigationBarsPadding()
            )
        }
    }
}
