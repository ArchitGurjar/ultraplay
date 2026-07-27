package com.ultrastream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.navigation.Screen
import com.ultrastream.app.ui.screens.addons.AddonsScreen
import com.ultrastream.app.ui.screens.catalog.CatalogScreen
import com.ultrastream.app.ui.screens.details.DetailsScreen
import com.ultrastream.app.ui.screens.home.HomeScreen
import com.ultrastream.app.ui.screens.library.LibraryScreen
import com.ultrastream.app.ui.screens.player.PlayerScreen
import com.ultrastream.app.ui.screens.profile.ProfileScreen
import com.ultrastream.app.ui.screens.search.SearchScreen
import com.ultrastream.app.ui.theme.AccentBlue
import com.ultrastream.app.ui.theme.UltraStreamTheme
import com.ultrastream.app.ui.theme.premiumGlass
import com.ultrastream.app.utils.NextEpisodeHolder
import com.ultrastream.app.utils.NextEpisodeInfo
import com.ultrastream.app.utils.PlayerContext
import com.ultrastream.app.utils.SearchQueryHolder
import com.ultrastream.app.utils.StreamDataHolder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltraStreamTheme {
                UltraStreamNavHost()
            }
        }
    }

    @Composable
    fun UltraStreamNavHost() {
        val navController = rememberNavController()
        Scaffold(
            // ✅ FIX: Bottom bar is hidden when Player screen is active
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                if (currentRoute != Screen.Player.route) {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .premiumGlass(RoundedCornerShape(32.dp)),
                        color = Color.Transparent
                    ) {
                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraLarge = CircleShape)
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                modifier = Modifier.height(72.dp)
                            ) {
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentDestination = navBackStackEntry?.destination
                                val items = listOf(
                                    Triple(Screen.Home, "Home", Icons.Rounded.Home),
                                    Triple(Screen.Library, "Library", Icons.Rounded.VideoLibrary),
                                    Triple(Screen.Search, "Search", Icons.Rounded.Search),
                                    Triple(Screen.Addons, "Addons", Icons.Rounded.Extension),
                                    Triple(Screen.Profile, "Profile", Icons.Rounded.Person)
                                )
                                items.forEach { (screen, title, iconVector) ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                    NavigationBarItem(
                                        icon = { 
                                            Icon(
                                                imageVector = iconVector, 
                                                contentDescription = title,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            ) 
                                        },
                                        label = { 
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelSmall
                                            ) 
                                        },
                                        selected = selected,
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AccentBlue,
                                            selectedTextColor = AccentBlue,
                                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                            indicatorColor = Color.Transparent
                                        ),
                                        onClick = {
                                            val route = if (screen == Screen.Search) Screen.Search.pass(null) else screen.route
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // When Player is active, bottom bar is not rendered
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onItemClick = { id, type ->
                            navController.navigate(Screen.Details.pass(id, type))
                        },
                        onSeeAll = { rowId, items, title ->
                            CatalogDataHolder.items = items
                            navController.navigate(Screen.Catalog.pass(rowId, title))
                        },
                        onAnalyticsClick = {
                            navController.navigate(Screen.Analytics.route)
                        },
                        onInstallAddon = { url ->
                            // Navigate to Addons screen to install
                            navController.navigate(Screen.Addons.route)
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        onItemClick = { id, type ->
                            navController.navigate(Screen.Details.pass(id, type))
                        },
                        onPlayStream = { streams, title ->
                            StreamDataHolder.setStreams(streams, title)
                            navController.navigate(Screen.Player.pass(title))
                        }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onItemClick = { id, type ->
                            navController.navigate(Screen.Details.pass(id, type))
                        }
                    )
                }

                composable(Screen.Addons.route) {
                    AddonsScreen()
                }

                composable(Screen.Profile.route) {
                    ProfileScreen()
                }

                composable(Screen.Details.route) { backStackEntry ->
                    val id = URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
                    val type = URLDecoder.decode(backStackEntry.arguments?.getString("type") ?: "", "UTF-8")
                    val autoPlayNext = backStackEntry.arguments?.getString("autoPlayNext")?.toBoolean() ?: false

                    DetailsScreen(
                        id = id,
                        type = type,
                        autoPlayNext = autoPlayNext,
                        onBack = { navController.popBackStack() },
                        onPlay = { stream: StreamItem, title: String, next: NextEpisodeInfo? ->
                            StreamDataHolder.setStream(stream, next)
                            navController.navigate(Screen.Player.pass(title))
                        },
                        onCastClick = { actorName ->
                            SearchQueryHolder.query = actorName
                            SearchQueryHolder.shouldAutoSearch = true
                            navController.navigate(Screen.Search.route)
                        }
                    )
                }

                composable(Screen.Player.route) { backStackEntry ->
                    val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
                    val streams = StreamDataHolder.currentStreams
                    if (streams.isNotEmpty()) {
                        PlayerScreen(
                            streams = streams,
                            title = title.ifBlank { "Now Playing" },
                            onBack = {
                                StreamDataHolder.clear()
                                PlayerContext.clear()
                                NextEpisodeHolder.clear()
                                navController.popBackStack()
                            },
                            onPlaybackEnded = {
                                val next = StreamDataHolder.nextEpisode
                                if (next != null) {
                                    // Set the next episode info and pop back to Details
                                    NextEpisodeHolder.set(next)
                                    navController.popBackStack()
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            onNextEpisode = { nextStream, nextTitle ->
                                StreamDataHolder.setStream(nextStream)
                                navController.navigate(Screen.Player.pass(nextTitle)) {
                                    popUpTo(Screen.Player.route) { inclusive = true }
                                }
                            }
                        )
                    } else {
                        navController.popBackStack()
                    }
                }

                composable(Screen.Catalog.route) { backStackEntry ->
                    val rowId = URLDecoder.decode(backStackEntry.arguments?.getString("rowId") ?: "", "UTF-8")
                    val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
                    val items = CatalogDataHolder.items
                    if (items.isNotEmpty()) {
                        CatalogScreen(
                            rowId = rowId,
                            title = title,
                            items = items,
                            onBack = { navController.popBackStack() },
                            onItemClick = { id, type ->
                                navController.navigate(Screen.Details.pass(id, type))
                            }
                        )
                    } else {
                        navController.popBackStack()
                    }
                }

                composable(Screen.Analytics.route) {
                    com.ultrastream.app.ui.screens.analytics.AnalyticsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

// Temporary data holder for Catalog items
object CatalogDataHolder {
    var items: List<MetaItem> = emptyList()
}