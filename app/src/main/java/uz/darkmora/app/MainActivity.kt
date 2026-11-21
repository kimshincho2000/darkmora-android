package uz.darkmora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
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

    val showBottomBar = currentRoute in listOf("home", "catalog", "favorites")

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color(0xEE0F121C),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder)
                ) {
                    val items = listOf(
                        Triple("home", "Asosiy", Icons.Default.Home),
                        Triple("catalog", "Katalog", Icons.Default.Layers),
                        Triple("favorites", "Sevimlilar", Icons.Default.Bookmark)
                    )

                    items.forEach { (route, label, icon) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) PrimaryGlow else TextDim
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else TextDim,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0x332563EB)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onAnimeClick = { slug ->
                        navController.navigate("detail/$slug")
                    },
                    onSeeAllClick = {
                        navController.navigate("catalog")
                    }
                )
            }

            composable("catalog") {
                CatalogScreen(
                    onAnimeClick = { slug ->
                        navController.navigate("detail/$slug")
                    }
                )
            }

            composable("favorites") {
                FavoritesScreen(
                    favorites = emptyList(),
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
                    onPlayEpisode = { videoUrl, title ->
                        navController.navigate("player/$videoUrl/$title")
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
    }
}
