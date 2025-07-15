package com.kybers.play.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.ui.PlayerViewModelFactory // ¡NUEVO! Importación de la fábrica

/**
 * La Activity que alberga nuestro reproductor de video.
 * Su única responsabilidad es preparar el entorno y lanzar la UI de Compose.
 */
class PlayerActivity : ComponentActivity() {

    // Obtenemos una instancia del PlayerViewModel usando la fábrica.
    private val playerViewModel: PlayerViewModel by viewModels {
        PlayerViewModelFactory(application) // ¡CORREGIDO! Usando la nueva fábrica
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Recuperamos la URL del stream que nos envió la pantalla anterior.
        val streamUrl = intent.getStringExtra("stream_url")

        // 2. Comprobación de seguridad: Si no llega la URL, cerramos la actividad.
        if (streamUrl == null) {
            finish()
            return
        }

        // 3. Configuramos nuestra UI de Jetpack Compose.
        setContent {
            IPTVAppTheme {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    streamUrl = streamUrl
                )
            }
        }
    }
}
