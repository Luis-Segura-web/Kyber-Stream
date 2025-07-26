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
 * --- ¡ACTIVITY CORREGIDA Y ROBUSTA! ---
 * MainActivity es el contenedor principal de la aplicación después del login.
 * Carga los datos del usuario de forma asíncrona dentro de la composición
 * para evitar crashes de ciclo de vida y muestra un indicador de carga mientras tanto.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getIntExtra("USER_ID", -1)
        val appContainer = (application as MainApplication).container

        setContent {
            IPTVAppTheme {
                // Estados para manejar la carga del perfil de usuario de forma segura.
                var user by remember { mutableStateOf<User?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                // LaunchedEffect es la forma correcta en Compose de ejecutar código asíncrono
                // una sola vez, cuando el Composable aparece en pantalla.
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

                // Renderizamos la UI basándonos en el estado actual de la carga.
                when {
                    isLoading -> {
                        // Muestra un indicador de carga mientras se obtiene el perfil.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    user != null -> {
                        // El usuario se ha cargado correctamente. ¡A la carga!
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

                        // --- ¡LLAMADA CORREGIDA! ---
                        // Se elimina el parámetro 'syncManager' que ya no es necesario.
                        MainScreen(
                            contentViewModelFactory = contentViewModelFactory,
                            vodRepository = vodRepository,
                            detailsRepository = detailsRepository,
                            preferenceManager = appContainer.preferenceManager,
                            currentUser = user!!
                        )
                    }
                    else -> {
                        // Si algo salió mal, mostramos un error y cerramos.
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
