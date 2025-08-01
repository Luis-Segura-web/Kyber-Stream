package com.kybers.play.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.kybers.play.MainApplication
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.ui.theme.rememberThemeManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as MainApplication).container

        setContent {
            val themeManager = rememberThemeManager(this@MainActivity)
            IPTVAppTheme(themeManager = themeManager) {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    appContainer = appContainer,
                    application = application
                )
            }
        }
    }
}
