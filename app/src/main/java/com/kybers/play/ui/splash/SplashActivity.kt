package com.kybers.play.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.kybers.play.ui.login.LoginActivity
import com.kybers.play.ui.theme.IPTVAppTheme

/**
 * --- ¡ACTIVITY SIMPLIFICADA! ---
 * La actividad inicial de la aplicación. Muestra una pantalla de bienvenida y luego
 * navega directamente a LoginActivity sin necesidad de un ViewModel.
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prepara la UI para una experiencia inmersiva a pantalla completa.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            IPTVAppTheme {
                // Llamamos al Composable SplashScreen, que ahora maneja su propia lógica.
                SplashScreen(
                    // La función de navegación ahora va incondicionalmente a LoginActivity.
                    onNavigate = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        // Finalizamos SplashActivity para que el usuario no pueda volver a ella.
                        finish()
                    }
                )
            }
        }
    }
}
