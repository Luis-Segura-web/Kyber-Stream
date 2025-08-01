package com.kybers.play.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

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
