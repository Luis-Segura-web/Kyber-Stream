package com.kybers.play.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kybers.play.R
import androidx.navigation.NavController
import kotlinx.coroutines.delay

/**
 * A simple splash screen that shows a logo and app name.
 * After a delay, it navigates to the login screen.
 *
 * @param navController The NavController to handle navigation.
 */
@Composable
fun SplashScreen(
    navController: NavController
) {
    // This effect will run once when the composable enters the composition.
    LaunchedEffect(Unit) {
        // Wait for 3 seconds to show the splash screen (improved UX timing).
        delay(3000)
        // Navigate to login and clear the splash from back stack
        navController.navigate("login") {
            popUpTo("splash") { inclusive = true }
        }
    }

    // The UI of the splash screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
