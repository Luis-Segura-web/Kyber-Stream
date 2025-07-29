package com.kybers.play.ui.player

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.kybers.play.ui.PlayerViewModelFactory
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.ui.theme.rememberThemeManager
import android.util.Log // Importación necesaria para Log

class PlayerActivity : ComponentActivity() {

    // Usamos la fábrica para crear el PlayerViewModel
    private val playerViewModel: PlayerViewModel by viewModels {
        PlayerViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obtenemos la URL del stream desde el Intent que inició esta actividad
        val streamUrl = intent.getStringExtra("STREAM_URL")
        val streamTitle = intent.getStringExtra("STREAM_TITLE") ?: "Stream Desconocido" // Valor por defecto mejorado

        Log.d("PlayerActivity", "onCreate: Recibida STREAM_URL: $streamUrl")
        Log.d("PlayerActivity", "onCreate: Recibida STREAM_TITLE: $streamTitle")

        // Si no hay URL, no podemos hacer nada, así que cerramos la actividad.
        if (streamUrl.isNullOrEmpty()) {
            Log.e("PlayerActivity", "onCreate: STREAM_URL es nula o vacía. Terminando actividad.")
            finish()
            return
        }

        // Configuramos la ventana para una experiencia inmersiva
        hideSystemUI()

        setContent {
            val themeManager = rememberThemeManager(this@PlayerActivity)
            IPTVAppTheme(themeManager = themeManager) {
                // Lanzamos el PlayerScreen, que ahora contiene toda la lógica de la UI
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    streamUrl = streamUrl,
                    streamTitle = streamTitle // Pasamos el título también
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Nos aseguramos de que la UI siga oculta al volver a la app
        hideSystemUI()
    }

    /**
     * Helper para ocultar las barras de sistema y entrar en modo inmersivo.
     */
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
        } else {
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}