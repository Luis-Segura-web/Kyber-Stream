package com.kybers.play.ui.main

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.kybers.play.ui.theme.IPTVAppTheme

/**
 * --- ¡ACTIVITY CORREGIDA PARA EL CRASH! ---
 * MainActivity es el contenedor principal de la aplicación después del login.
 * Se ha refactorizado para llamar a setContent inmediatamente y manejar la carga de datos
 * de forma asíncrona dentro de la composición, evitando así el crash de ciclo de vida.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getIntExtra("USER_ID", -1)
        val appContainer = (application as MainApplication).container

        setContent {
            IPTVAppTheme {
                // Estados para manejar la carga del perfil de usuario.
                var user by remember { mutableStateOf<User?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                // LaunchedEffect se ejecuta una vez cuando el composable entra en la composición.
                // Es el lugar ideal para cargar datos de forma asíncrona.
                LaunchedEffect(userId) {
                    if (userId == -1) {
                        error = "ID de usuario no válido."
                        isLoading = false
                        return@LaunchedEffect
                    }
                    // Buscamos el usuario en segundo plano sin bloquear la UI.
                    val loadedUser = appContainer.userRepository.getUserById(userId)
                    if (loadedUser == null) {
                        error = "No se pudo encontrar el perfil del usuario."
                    } else {
                        user = loadedUser
                    }
                    isLoading = false
                }

                // Renderizamos la UI basándonos en el estado actual.
                when {
                    isLoading -> {
                        // Muestra un indicador de carga mientras se obtiene el perfil.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    user != null -> {
                        // El usuario se ha cargado correctamente.
                        // Usamos 'remember' para que estas instancias no se recreen en cada recomposición.
                        val vodRepository = remember(user!!.url) { appContainer.createVodRepository(user!!.url) }
                        val liveRepository = remember(user!!.url) { appContainer.createLiveRepository(user!!.url) }
                        val detailsRepository = remember { appContainer.detailsRepository }

                        val contentViewModelFactory = remember(user!!.id) {
                            ContentViewModelFactory(
                                application = application,
                                vodRepository = vodRepository,
                                liveRepository = liveRepository,
                                detailsRepository = detailsRepository,
                                currentUser = user!!,
                                preferenceManager = appContainer.preferenceManager,
                                syncManager = appContainer.syncManager
                            )
                        }

                        // Mostramos la pantalla principal.
                        MainScreen(
                            contentViewModelFactory = contentViewModelFactory,
                            vodRepository = vodRepository,
                            detailsRepository = detailsRepository,
                            preferenceManager = appContainer.preferenceManager,
                            currentUser = user!!,
                            syncManager = appContainer.syncManager
                        )
                    }
                    else -> {
                        // Ha ocurrido un error. Mostramos un mensaje y cerramos.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(error ?: "Ha ocurrido un error inesperado.")
                        }
                        LaunchedEffect(error) {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                            finish() // Cerramos la actividad de forma segura.
                        }
                    }
                }
            }
        }
    }
}
