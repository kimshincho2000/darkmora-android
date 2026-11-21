@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package uz.darkmora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import uz.darkmora.app.data.UpdateInfo
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.screens.*
import uz.darkmora.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DarkMoraTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()

    var autoUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val res = DarkmoraApi.checkUpdate()
            if (res.ok && res.data != null && res.data.versionCode > 16) {
                autoUpdateInfo = res.data
                showAutoUpdateDialog = true
            }
        } catch (_: Exception) {}
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

        if (showAutoUpdateDialog && autoUpdateInfo != null) {
            UpdateAppDialog(
                info = autoUpdateInfo!!,
                onDismiss = { showAutoUpdateDialog = false }
            )
        }
    }
}

// ============================================================================
// MAIN TABS + FLUID CURVED LIQUID DROPLET BOTTOM NAVIGATION BAR (VIDEO REPLICA)
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
        // Ultra-smooth swipeable Horizontal Pager
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
                    }
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

        // Fluid Curved Liquid Droplet Bottom Navigation Bar (1:1 Video Animation)
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

// ============================================================================
// FLUID CURVED LIQUID DROPLET BOTTOM NAVIGATION (EXACT VIDEO ANIMATION)
// ============================================================================

@Composable
fun FluidCurvedBottomNav(
    items: List<NavItem>,
    pagerState: PagerState,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tabCount = items.size

    // Continuous page float: tracks finger drag or tab click transition smoothly from 0f to (N-1)f
    val rawContinuousPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (tabCount - 1).toFloat())

    // Buttery spring interpolation for the notch center position
    val animatedPage by animateFloatAsState(
        targetValue = rawContinuousPage,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fluidNotchPage"
    )

    // How far are we mid-transition between two integer tabs?
    // 0f = settled exactly on a tab (deep concave cradle, floating orb popped up)
    // 1f = halfway between two tabs (liquid droplet sinks into bar & morphs into a wave!)
    val nearestTab = animatedPage.roundToInt()
    val fractionalDist = (abs(animatedPage - nearestTab) * 2f).coerceIn(0f, 1f)
    // Smooth cosine curve for organic liquid morphing
    val transitPhase = ((1f - cos(fractionalDist * Math.PI.toFloat())) * 0.5f).coerceIn(0f, 1f)

    // Bar dimensions
    val barTopOffsetDp = 24.dp // Extra headroom above bar for the floating orb & liquid dome
    val barHeightDp = 64.dp
    val orbSizeDp = 52.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val tabWidthPx = totalWidthPx / tabCount
        val barTopPx = with(density) { barTopOffsetDp.toPx() }
        val barHeightPx = with(density) { barHeightDp.toPx() }
        val orbSizePx = with(density) { orbSizeDp.toPx() }
        val orbRadiusPx = orbSizePx / 2f

        // Center X of the active notch
        val notchCenterX = (animatedPage + 0.5f) * tabWidthPx

        // Dynamic notch depth:
        // When settled (transitPhase = 0f): deep concave cradle (+32dp down into bar)
        // When mid-transit (transitPhase = 1f): liquid wave / dome (-8dp upward bulge as orb merges!)
        val settledDepthPx = with(density) { 33.dp.toPx() }
        val transitPeakPx = with(density) { (-9).dp.toPx() }
        val currentNotchDepthPx = settledDepthPx * (1f - transitPhase) + transitPeakPx * transitPhase

        // Notch half-width on the bar top edge
        val notchHalfWidthPx = with(density) { 47.dp.toPx() } * (1f - 0.15f * transitPhase)

        // Floating Orb Y & Scale:
        // When settled: orb floats high inside the cradle (center near barTopPx + 4dp)
        // When mid-transit: orb dives down into the liquid bar and shrinks, then pops out at target!
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
                // 1. LIQUID CURVED BAR CANVAS (With smooth cubic bezier cradle / wave)
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

                        // Left shoulder curve into the notch bottom/peak
                        cubicTo(
                            x1 = leftStart + c1Offset,
                            y1 = barTopPx,
                            x2 = notchCenterX - c2Offset,
                            y2 = barTopPx + currentNotchDepthPx,
                            x3 = notchCenterX,
                            y3 = barTopPx + currentNotchDepthPx
                        )

                        // Right shoulder curve from notch bottom/peak back to top edge
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

                    // Fill bar with clean, deep velvety surface
                    drawPath(
                        path = barPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                NavSurface,
                                Color(0xFF10121C)
                            ),
                            startY = barTopPx,
                            endY = h
                        ),
                        style = Fill
                    )

                    // Top subtle specular rim along the curved contour
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

                    drawPath(
                        path = topRimPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x18FFFFFF),
                                Color(0x4560A5FA),
                                Color(0x18FFFFFF)
                            )
                        ),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                // 2. FLOATING LIQUID DROPLET ORB (Active Tab Circle that pops out of the cradle)
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
                            // Slight elastic rotation / tilt during fast liquid movement
                            rotationZ = (rawContinuousPage - animatedPage) * -18f
                        }
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = PrimaryGlow,
                            spotColor = PrimaryBlue
                        )
                        .clip(CircleShape)
                        .background(NavActiveOrb)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Active Icon inside the floating white orb (just like the video's crisp dark icon on white circle!)
                    Icon(
                        imageVector = activeItem.activeIcon,
                        contentDescription = activeItem.title,
                        tint = NavActiveIcon,
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
                        // Hide the in-bar icon when the floating orb is docked at this tab
                        val inBarVisibility = ((distFromNotch - 0.25f) / 0.55f).coerceIn(0f, 1f)

                        val iconTint by animateColorAsState(
                            targetValue = if (distFromNotch < 0.5f) Color.White else NavInactiveIcon,
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

            // System gesture navigation bar inset spacer matching the bar bottom color
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10121C))
                    .navigationBarsPadding()
            )
        }
    }
}
