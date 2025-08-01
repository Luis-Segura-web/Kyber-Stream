package com.kybers.play.ui.responsive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kybers.play.ui.theme.LocalDeviceSize

@Composable
fun ResponsiveScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    navigationRail: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val deviceSize = LocalDeviceSize.current

    when (deviceSize) {
        DeviceSize.COMPACT -> {
            // Diseño para teléfonos: barra superior e inferior
            Scaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                floatingActionButton = floatingActionButton,
                content = content
            )
        }
        DeviceSize.MEDIUM -> {
            // Diseño para tablets medianas: navegación lateral compacta
            Row(modifier = Modifier.fillMaxSize()) {
                navigationRail()
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar,
                    floatingActionButton = floatingActionButton,
                    content = content
                )
            }
        }
        DeviceSize.EXPANDED -> {
            // Diseño para pantallas grandes: navegación lateral + panel secundario
            Row(modifier = Modifier.fillMaxSize()) {
                navigationRail()
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar,
                    floatingActionButton = floatingActionButton,
                    content = content
                )
            }
        }
    }
}