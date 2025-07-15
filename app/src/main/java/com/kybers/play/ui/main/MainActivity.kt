package com.kybers.play.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.kybers.play.MainApplication
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.theme.IPTVAppTheme
import kotlinx.coroutines.launch

/**
 * MainActivity es el contenedor principal de la aplicación después del login.
 * Alberga el NavHost con todas las pantallas principales (Inicio, Canales, etc.).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Recuperamos el ID del usuario que fue pasado desde LoginActivity.
        val userId = intent.getIntExtra("USER_ID", 1)

        // 2. Obtenemos acceso a nuestro contenedor de dependencias.
        val appContainer = (application as MainApplication).container

        // 3. Usamos una coroutina para realizar operaciones de base de datos de forma asíncrona.
        lifecycleScope.launch {
            // 4. Buscamos el perfil completo del usuario en la base de datos usando su ID.
            val user = appContainer.userRepository.getUserById(userId)

            // 5. Si por alguna razón el usuario no se encuentra, cerramos la actividad.
            if (user == null) {
                finish()
                return@launch
            }

            // 6. Creamos la fábrica de ViewModels para el contenido.
            val contentViewModelFactory = ContentViewModelFactory(
                contentRepository = appContainer.createContentRepository(user.url),
                currentUser = user
            )

            // 7. Establecemos el contenido de la actividad.
            setContent {
                IPTVAppTheme {
                    MainScreen(contentViewModelFactory = contentViewModelFactory)
                }
            }
        }
    }
}
