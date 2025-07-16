package com.kybers.play.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kybers.play.R

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: (Int) -> Unit
) {
    // Observamos el estado de navegación del ViewModel
    val navigationState by viewModel.navigationState.collectAsState()

    // Cuando el estado cambia, este efecto se dispara y ejecuta la navegación
    LaunchedEffect(navigationState) {
        when (val state = navigationState) {
            is SplashNavigationState.GoToMain -> onNavigateToMain(state.userId)
            SplashNavigationState.GoToLogin -> onNavigateToLogin()
            SplashNavigationState.Loading -> { /* No hacer nada, solo mostrar la UI */ }
        }
    }

    // La UI de la pantalla de bienvenida
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline, // Un ícono de ejemplo
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.app_name), // Usamos el nombre de la app desde los recursos
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
