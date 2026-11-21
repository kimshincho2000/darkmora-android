package uz.darkmora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import uz.darkmora.app.data.UpdateInfo
import uz.darkmora.app.data.UserSessionManager
import uz.darkmora.app.network.DarkmoraApi
import uz.darkmora.app.ui.screens.*
import uz.darkmora.app.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTopLevel = currentRoute == null || currentRoute == "main_tabs"

    var autoUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val res = DarkmoraApi.checkUpdate()
            if (res.ok && res.data != null && res.data.versionCode > 12) {
                autoUpdateInfo = res.data
                showAutoUpdateDialog = true
            }
        } catch (_: Exception) {}
    }

    Scaffold(
        containerColor = BgDark
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "main_tabs",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("main_tabs") {
                MainTabsScreen(
                    onAnimeClick = { slug ->
                        navController.navigate("detail/$slug")
                    },
                    onNavigateToCatalog = {
                        // Handled via pager
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
                    onPlayEpisode = { b64Url, b64Title ->
                        navController.navigate("player/$b64Url/$b64Title")
                    }
                )
            }

            composable(
                route = "player/{videoUrl}/{title}",
                arguments = listOf(
                    navArgument("videoUrl") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                PlayerScreen(
                    videoUrlEncoded = videoUrl,
                    titleEncoded = title,
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
    onAnimeClick: (String) -> Unit,
    onNavigateToCatalog: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
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
                    }
                )
                1 -> CatalogScreen(
                    onAnimeClick = onAnimeClick
                )
                2 -> {
                    // Favorites screen with real user favorites
                    FavoritesScreen(
                        favorites = emptyList(),
                        onAnimeClick = onAnimeClick
                    )
                }
                3 -> ProfileScreen(
                    onNavigateToFavorites = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
            }
        }

        // iOS Telegram Liquid Glass Floating Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            LiquidGlassBottomBar(
                items = navItems,
                selectedIndex = pagerState.currentPage,
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
// iOS TELEGRAM "LIQUID GLASS" FLOATING PILL COMPONENT
// ============================================================================

@Composable
fun LiquidGlassBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xD9121626), // Frosted translucent obsidian
                        Color(0xE60A0D17)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x40FFFFFF), // Glossy top refraction highlight
                        Color(0x15FFFFFF),
                        Color(0x05FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                val interactionSource = remember { MutableInteractionSource() }

                // Animated scale/alpha
                val pillAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(stiffness = 400f),
                    label = "pillAlpha"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onItemSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Active Sliding Liquid Pill Behind the Icon
                    if (pillAlpha > 0.05f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0x332563EB),
                                            Color(0x223B82F6)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x553B82F6),
                                            Color(0x103B82F6)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (selected) PrimaryGlow else Color(0xFF8E95A8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            color = if (selected) Color.White else Color(0xFF6B7280),
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
