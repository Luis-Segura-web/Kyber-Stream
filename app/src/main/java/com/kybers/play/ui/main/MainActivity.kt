package com.kybers.play.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.ui.theme.rememberThemeManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeManager = rememberThemeManager(this@MainActivity)
            IPTVAppTheme(themeManager = themeManager) {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    application = application,
                    themeManager = themeManager
                )
            }
        }
    }
}
