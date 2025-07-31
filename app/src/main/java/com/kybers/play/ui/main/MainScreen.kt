package com.kybers.play.ui.main

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.SeriesDetailsViewModelFactory
import com.kybers.play.ui.SettingsViewModelFactory
import com.kybers.play.ui.LoginViewModelFactory
import com.kybers.play.ui.SyncViewModelFactory
import com.kybers.play.cache.PreloadingManager
import com.kybers.play.cache.CacheVerification
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

// --- ¡ACTUALIZADO! Hacemos label e icon opcionales y añadimos la ruta de Ajustes ---
sealed class Screen(val route: String, val label: String? = null, val icon: ImageVector? = null) {
    object Splash : Screen("splash") // Pantalla de splash inicial
    object Login : Screen("login") // Pantalla de login
    object Home : Screen("home", "Inicio", Icons.Outlined.Home)
    object Channels : Screen("channels", "TV en Vivo", Icons.Outlined.LiveTv)
    object Movies : Screen("movies", "Películas", Icons.Outlined.Movie)
    object Series : Screen("series", "Series", Icons.Outlined.Slideshow)
    object Settings : Screen("settings") // No necesita label ni icon para la barra inferior
    object MovieDetails : Screen("movie_details/{movieId}") {
        fun createRoute(movieId: Int) = "movie_details/$movieId"
    }
    object SeriesDetails : Screen("series_details/{seriesId}") {
        fun createRoute(seriesId: Int) = "series_details/$seriesId"
    }
    object Sync : Screen("sync/{userId}") {
        fun createRoute(userId: Int) = "sync/$userId"
    }
    object Main : Screen("main/{userId}") {
        fun createRoute(userId: Int) = "main/$userId"
    }
}

private val bottomBarItems = listOf(
    Screen.Home,
    Screen.Channels,
    Screen.Movies,
    Screen.Series,
)

@Composable
fun MainScreen(
    contentViewModelFactory: ContentViewModelFactory,
    movieDetailsViewModelFactoryProvider: @Composable (Int) -> MovieDetailsViewModelFactory,
    seriesDetailsViewModelFactoryProvider: @Composable (Int) -> SeriesDetailsViewModelFactory,
    settingsViewModelFactoryProvider: @Composable () -> SettingsViewModelFactory, // --- ¡NUEVO! ---
    loginViewModelFactory: LoginViewModelFactory, // --- ¡NUEVO! ---
    syncViewModelFactoryProvider: @Composable (Int) -> SyncViewModelFactory, // --- ¡NUEVO! ---
    preloadingManager: PreloadingManager, // NUEVO PARÁMETRO
    currentUserId: Int // NUEVO PARÁMETRO PARA USUARIO ACTUAL
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var isPlayerInPipMode by remember { mutableStateOf(false) }

    // NUEVA FUNCIONALIDAD: Inicializar precarga y verificar cache
    LaunchedEffect(Unit) {
        try {
            Log.d("MainScreen", "Iniciando verificación y precarga del sistema")
            
            // Verificar que el sistema de cache funciona
            val cacheVerification = CacheVerification(context)
            cacheVerification.runBasicTests()
            
            preloadingManager.preloadPopularContent()
            
            // Si hay usuario logueado, precargar sus preferencias
            preloadingManager.preloadUserPreferences(currentUserId)
            Log.d("MainScreen", "Sistema de precarga inicializado correctamente")
        } catch (e: Exception) {
            Log.e("MainScreen", "Error inicializando sistema de precarga", e)
        }
    }

    val isBottomBarVisible = bottomBarItems.any { it.route == currentDestination?.route } && !isPlayerFullScreen && !isPlayerInPipMode

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar {
                    bottomBarItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.label) },
                            label = { Text(screen.label!!) },
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
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            contentViewModelFactory = contentViewModelFactory,
            movieDetailsViewModelFactoryProvider = movieDetailsViewModelFactoryProvider,
            seriesDetailsViewModelFactoryProvider = seriesDetailsViewModelFactoryProvider,
            settingsViewModelFactoryProvider = settingsViewModelFactoryProvider,
            loginViewModelFactory = loginViewModelFactory,
            syncViewModelFactoryProvider = syncViewModelFactoryProvider,
            preloadingManager = preloadingManager,
            currentUserId = currentUserId,
            onPlayerUiStateChanged = { isFull, isPip ->
                isPlayerFullScreen = isFull
                isPlayerInPipMode = isPip
            }
        )
    }
}
