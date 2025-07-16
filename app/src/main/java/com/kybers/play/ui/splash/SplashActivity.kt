package com.kybers.play.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.kybers.play.MainApplication
import com.kybers.play.ui.SplashViewModelFactory
import com.kybers.play.ui.login.LoginActivity
import com.kybers.play.ui.main.MainActivity
import com.kybers.play.ui.theme.IPTVAppTheme

class SplashActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels {
        SplashViewModelFactory((application as MainApplication).container.userRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hacemos que la UI ocupe toda la pantalla para una experiencia inmersiva
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            IPTVAppTheme {
                SplashScreen(
                    viewModel = splashViewModel,
                    onNavigateToLogin = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish() // Cierra el Splash para que el usuario no pueda volver a él
                    },
                    onNavigateToMain = { userId ->
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                        }
                        startActivity(intent)
                        finish() // Cierra el Splash
                    }
                )
            }
        }
    }
}
