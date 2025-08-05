package com.kybers.play.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kybers.play.ui.theme.DeviceSize
import com.kybers.play.ui.theme.LocalDeviceSize

enum class KyberDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME("home", "Inicio", Icons.Outlined.Home),
    CHANNELS("channels", "TV", Icons.Outlined.Tv),
    MOVIES("movies", "Películas", Icons.Outlined.LocalMovies),
    SERIES("series", "Series", Icons.Outlined.VideoLibrary)
}

@Composable
fun ResponsiveNavigation(
    navController: NavController,
    currentDestination: androidx.navigation.NavDestination?,
    isVisible: Boolean
) {
    val deviceSize = LocalDeviceSize.current
    
    when (deviceSize) {
        DeviceSize.COMPACT -> {
            if (isVisible) {
                BottomNavigationBar(navController, currentDestination)
            }
        }
        DeviceSize.MEDIUM, DeviceSize.EXPANDED -> {
            // For tablets, we render nothing here as the NavigationRail is handled separately
        }
    }
}

@Composable
fun ResponsiveNavigationRail(
    navController: NavController,
    currentDestination: androidx.navigation.NavDestination?
) {
    val deviceSize = LocalDeviceSize.current
    
    when (deviceSize) {
        DeviceSize.COMPACT -> {
            // No navigation rail for compact devices
        }
        DeviceSize.MEDIUM, DeviceSize.EXPANDED -> {
            NavigationRailBar(navController, currentDestination)
        }
    }
}

@Composable
private fun BottomNavigationBar(
    navController: NavController,
    currentDestination: androidx.navigation.NavDestination?
) {
    NavigationBar {
        KyberDestination.values().forEach { destination ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
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
                label = { 
                    Text(text = destination.label)
                }
            )
        }
    }
}

@Composable
fun NavigationRailBar(
    navController: NavController,
    currentDestination: androidx.navigation.NavDestination?
) {
    NavigationRail {
        KyberDestination.values().forEach { destination ->
            NavigationRailItem(
                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
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
                label = { 
                    Text(text = destination.label)
                }
            )
        }
    }
}