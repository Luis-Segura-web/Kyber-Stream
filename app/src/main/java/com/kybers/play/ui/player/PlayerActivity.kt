package com.kybers.play.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kybers.play.ui.theme.IPTVAppTheme

/**
 * La Activity que alberga nuestro reproductor de video.
 * Su única responsabilidad es preparar el entorno y lanzar la UI de Compose.
 */
class PlayerActivity : ComponentActivity() {

    // Obtenemos una instancia del PlayerViewModel.
    private val playerViewModel: PlayerViewModel by viewModels()

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
