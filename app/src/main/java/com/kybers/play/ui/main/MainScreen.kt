package com.kybers.play.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.channels.ChannelsScreen
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.home.HomeScreen // ¡NUEVO! Importación para HomeScreen
import com.kybers.play.ui.home.HomeViewModel // ¡NUEVO! Importación para HomeViewModel

// Define las rutas, etiquetas e íconos para cada pantalla principal de la app.
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Channels : Screen("channels", "Canales", Icons.Default.Tv)
    object Movies : Screen("movies", "Películas", Icons.Default.Movie)
    object Series : Screen("series", "Series", Icons.Default.Movie) // Se puede cambiar el ícono
}

// Lista de los ítems que se mostrarán en la barra de navegación inferior.
private val items = listOf(
    Screen.Home,
    Screen.Channels,
    Screen.Movies,
    Screen.Series,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(contentViewModelFactory: ContentViewModelFactory) {
    val navController = rememberNavController()
    // ¡NUEVO! Estado para controlar si ChannelsScreen está en fullscreen
    var isChannelsFullScreen by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            // ¡CAMBIO CLAVE AQUÍ! La barra de navegación solo es visible si NO estamos en fullscreen
            AnimatedVisibility(
                visible = !isChannelsFullScreen,
                enter = slideInVertically { it }, // Animación al aparecer
                exit = slideOutVertically { it } // Animación al desaparecer
            ) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
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
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(factory = contentViewModelFactory)
                HomeScreen(homeViewModel)
            }
            composable(Screen.Channels.route) {
                val channelsViewModel: ChannelsViewModel = viewModel(factory = contentViewModelFactory)
                ChannelsScreen(
                    viewModel = channelsViewModel,
                    onFullScreenToggled = { isFullScreen -> // ¡NUEVO! Recibe el callback
                        isChannelsFullScreen = isFullScreen // Actualiza el estado local
                    }
                )
            }
            composable(Screen.Movies.route) {
                MoviesScreen() // Placeholder
            }
            composable(Screen.Series.route) {
                SeriesScreen() // Placeholder
            }
        }
    }
}

// --- Pantallas de Marcador de Posición (Placeholders) ---

@Composable
fun MoviesScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pantalla de Películas (En construcción)")
    }
}

@Composable
fun SeriesScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pantalla de Series (En construcción)")
    }
}
