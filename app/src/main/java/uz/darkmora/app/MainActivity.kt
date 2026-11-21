@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package uz.darkmora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            if (res.ok && res.data != null && res.data.versionCode > 13) {
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
// MAIN TABS WITH HORIZONTAL PAGER + iOS TELEGRAM LIQUID GLASS BOTTOM BAR
// ============================================================================

data class NavItem(val title: String, val icon: ImageVector)

@Composable
fun MainTabsScreen(
    onAnimeClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    val navItems = remember {
        listOf(
            NavItem("Asosiy", Icons.Default.Home),
            NavItem("Katalog", Icons.Default.Widgets),
            NavItem("Sevimlilar", Icons.Default.Bookmark),
            NavItem("Profil", Icons.Default.Person)
        )
    }

    val pagerState = rememberPagerState(pageCount = { navItems.size })

    Box(modifier = Modifier.fillMaxSize()) {
        // Swiping Horizontal Pager (iOS Telegram style smooth hand-swipe)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onAnimeClick = onAnimeClick,
                    onSeeAllClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onProfileClick = {
                        scope.launch { pagerState.animateScrollToPage(3) }
                    }
                )
                1 -> CatalogScreen(
                    onAnimeClick = onAnimeClick
                )
                2 -> FavoritesScreen(
                    onAnimeClick = onAnimeClick,
                    onExploreClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                3 -> ProfileScreen(
                    onNavigateToFavorites = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
            }
        }

        // Bottom Vignette Gradient behind floating bar for readability
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(105.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x9906080E),
                            Color(0xE606080E)
                        )
                    )
                )
        )

        // iOS Telegram Liquid Glass Floating Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            LiquidGlassBottomBar(
                items = navItems,
                pagerState = pagerState,
                onItemSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    }
}

// ============================================================================
// iOS TELEGRAM "LIQUID GLASS" FLOATING PILL WITH 1:1 CONTINUOUS SLIDING CAPSULE
// ============================================================================

@Composable
fun LiquidGlassBottomBar(
    items: List<NavItem>,
    pagerState: PagerState,
    onItemSelected: (Int) -> Unit
) {
    // Outer Floating Liquid Glass Container
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(34.dp),
                ambientColor = Color.Black,
                spotColor = PrimaryBlue
            )
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xE614192B), // Frosted translucent obsidian top
                        Color(0xF20A0D18)  // Deep obsidian bottom
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x55FFFFFF), // Specular top glass highlight
                        Color(0x2838BDF8), // Subtle cyan refraction
                        Color(0x12FFFFFF),
                        Color(0x3338BDF8)
                    )
                ),
                shape = RoundedCornerShape(34.dp)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        val tabCount = items.size
        val tabWidth = maxWidth / tabCount

        // Continuous page position (tracks finger swipe 1:1 in real time!)
        val continuousPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, (tabCount - 1).toFloat())

        val pillOffset by animateDpAsState(
            targetValue = tabWidth * continuousPage,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "slidingPillOffset"
        )

        // 1. CONTINUOUS SLIDING LIQUID GLASS ACTIVE CAPSULE
        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x4D2563EB),
                            Color(0x2E38BDF8),
                            Color(0x3B1D4ED8)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x8838BDF8),
                            Color(0x2238BDF8)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        )

        // 2. TAB ITEMS ROW (Icons & Labels sit comfortably inside the 56dp inner height)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val distance = abs(continuousPage - index)
                val isSelected = pagerState.currentPage == index

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconScale"
                )

                val iconColor by animateColorAsState(
                    targetValue = if (distance < 0.5f) PrimaryGlow else Color(0xFF8E96AA),
                    label = "iconColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (distance < 0.5f) Color.White else Color(0xFF7B8398),
                    label = "textColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onItemSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = iconColor,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                    translationY = if (isSelected) -2f else 0f
                                }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.title,
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
