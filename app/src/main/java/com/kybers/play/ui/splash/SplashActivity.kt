package com.kybers.play.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.kybers.play.ui.login.LoginActivity
import com.kybers.play.ui.theme.IPTVAppTheme

/**
 * The initial activity of the application. It displays a splash screen and then
 * navigates to the LoginActivity.
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the UI fullscreen for an immersive experience.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            IPTVAppTheme {
                // We call the simplified SplashScreen composable.
                SplashScreen(
                    // The onNavigate callback now unconditionally goes to LoginActivity.
                    onNavigate = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        // Finish the SplashActivity so the user can't navigate back to it.
                        finish()
                    }
                )
            }
        }
    }
}
