package com.kybers.play.ui.main

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kybers.play.MainApplication
import com.kybers.play.data.local.model.User
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.SeriesDetailsViewModelFactory
import com.kybers.play.ui.SettingsViewModelFactory
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.ui.theme.rememberThemeManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getIntExtra("USER_ID", -1)
        val appContainer = (application as MainApplication).container

        setContent {
            val themeManager = rememberThemeManager(this@MainActivity)
            IPTVAppTheme(themeManager = themeManager) {
                var user by remember { mutableStateOf<User?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(userId) {
                    if (userId == -1) {
                        error = "ID de usuario no válido."
                        isLoading = false
                        return@LaunchedEffect
                    }
                    val loadedUser = appContainer.userRepository.getUserById(userId)
                    if (loadedUser == null) {
                        error = "No se pudo encontrar el perfil del usuario."
                    } else {
                        user = loadedUser
                    }
                    isLoading = false
                }

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    user != null -> {
                        val vodRepository = remember(user!!.url) { appContainer.createVodRepository(user!!.url) }
                        val liveRepository = remember(user!!.url) { appContainer.createLiveRepository(user!!.url) }

                        val contentViewModelFactory = remember(user!!.id) {
                            ContentViewModelFactory(
                                application = application,
                                vodRepository = vodRepository,
                                liveRepository = liveRepository,
                                detailsRepository = appContainer.detailsRepository,
                                externalApiService = appContainer.tmdbApiService,
                                currentUser = user!!,
                                preferenceManager = appContainer.preferenceManager,
                                syncManager = appContainer.syncManager
                            )
                        }

                        val movieDetailsViewModelFactoryProvider = @Composable { movieId: Int ->
                            remember(movieId) {
                                MovieDetailsViewModelFactory(
                                    application = application,
                                    vodRepository = vodRepository,
                                    detailsRepository = appContainer.detailsRepository,
                                    externalApiService = appContainer.tmdbApiService,
                                    preferenceManager = appContainer.preferenceManager,
                                    currentUser = user!!,
                                    movieId = movieId
                                )
                            }
                        }

                        val seriesDetailsViewModelFactoryProvider = @Composable { seriesId: Int ->
                            remember(seriesId) {
                                SeriesDetailsViewModelFactory(
                                    application = application,
                                    preferenceManager = appContainer.preferenceManager,
                                    vodRepository = vodRepository,
                                    detailsRepository = appContainer.detailsRepository,
                                    externalApiService = appContainer.tmdbApiService,
                                    currentUser = user!!,
                                    seriesId = seriesId
                                )
                            }
                        }

                        // --- ¡NUEVA FÁBRICA PARA AJUSTES! ---
                        val settingsViewModelFactoryProvider = @Composable {
                            remember {
                                SettingsViewModelFactory(
                                    context = this@MainActivity,
                                    liveRepository = liveRepository,
                                    vodRepository = vodRepository,
                                    preferenceManager = appContainer.preferenceManager,
                                    syncManager = appContainer.syncManager,
                                    currentUser = user!!,
                                    themeManager = themeManager
                                )
                            }
                        }

                        MainScreen(
                            contentViewModelFactory = contentViewModelFactory,
                            movieDetailsViewModelFactoryProvider = movieDetailsViewModelFactoryProvider,
                            seriesDetailsViewModelFactoryProvider = seriesDetailsViewModelFactoryProvider,
                            settingsViewModelFactoryProvider = settingsViewModelFactoryProvider // La pasamos a MainScreen
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(error ?: "Ha ocurrido un error inesperado.")
                        }
                        LaunchedEffect(error) {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            }
        }
    }
}
