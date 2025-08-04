package com.kybers.play.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Botón toggle para cambiar entre modo lista y cuadrícula
 */
@Composable
fun DisplayModeToggle(
    currentMode: DisplayMode,
    onModeChanged: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            val newMode = when (currentMode) {
                DisplayMode.LIST -> DisplayMode.GRID
                DisplayMode.GRID -> DisplayMode.LIST
            }
            onModeChanged(newMode)
        },
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Icon(
            imageVector = currentMode.getIcon(),
            contentDescription = "Cambiar a ${if (currentMode == DisplayMode.LIST) "cuadrícula" else "lista"}",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Diálogo para seleccionar el modo de visualización
 */
@Composable
fun DisplayModeDialog(
    currentMode: DisplayMode,
    onModeSelected: (DisplayMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Modo de Visualización",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DisplayMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Icon(
                            imageVector = mode.getIcon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = mode.toLocalizedName(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = when (mode) {
                                    DisplayMode.LIST -> "Visualización en lista vertical con más detalles"
                                    DisplayMode.GRID -> "Visualización en cuadrícula compacta"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
