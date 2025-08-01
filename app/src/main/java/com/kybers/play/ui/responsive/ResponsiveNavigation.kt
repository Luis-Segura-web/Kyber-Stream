package com.kybers.play.ui.responsive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kybers.play.ui.main.Screen
import com.kybers.play.ui.theme.LocalDeviceSize

enum class KyberDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME(Screen.Home.route, "Inicio", Icons.Default.Home),
    CHANNELS(Screen.Channels.route, "TV en Vivo", Icons.Default.Tv),
    MOVIES(Screen.Movies.route, "Películas", Icons.Default.Movie),
    SERIES(Screen.Series.route, "Series", Icons.Default.Search),
    SETTINGS(Screen.Settings.route, "Ajustes", Icons.Default.Settings)
}

@Composable
fun ResponsiveNavigation(
    navController: NavController,
    currentDestination: String?,
    modifier: Modifier = androidx.compose.ui.Modifier
) {
    val deviceSize = LocalDeviceSize.current
    
    val bottomBarDestinations = listOf(
        KyberDestination.HOME,
        KyberDestination.CHANNELS,
        KyberDestination.MOVIES,
        KyberDestination.SERIES
    )
    
    when (deviceSize) {
        DeviceSize.COMPACT -> {
            // Navegación inferior para teléfonos
            NavigationBar(modifier = modifier) {
                bottomBarDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination.route,
                        onClick = { 
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { 
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
        DeviceSize.MEDIUM, DeviceSize.EXPANDED -> {
            // Navegación lateral para tablets y pantallas grandes
            NavigationRail(modifier = modifier) {
                bottomBarDestinations.forEach { destination ->
                    NavigationRailItem(
                        selected = currentDestination == destination.route,
                        onClick = { 
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { 
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    }
}