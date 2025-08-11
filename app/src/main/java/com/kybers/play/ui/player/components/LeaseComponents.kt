package com.kybers.play.ui.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kybers.play.core.player.StreamingLeaseManager
import kotlinx.coroutines.delay

/**
 * Diálogo que se muestra cuando hay un conflicto de lease (single-connection policy)
 */
@Composable
fun LeaseConflictDialog(
    onForcePlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Reproducción activa",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Ya hay una reproducción activa en este dispositivo. ¿Deseas detener la reproducción actual y reproducir este contenido?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    
                    Button(
                        onClick = onForcePlay,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cambiar")
                    }
                }
            }
        }
    }
}

/**
 * Indicador de cooldown que se muestra cuando el sistema está en período de espera
 */
@Composable
fun CooldownIndicator(
    cooldownRemainingMs: Long,
    modifier: Modifier = Modifier
) {
    if (cooldownRemainingMs > 0) {
        var timeRemaining by remember { mutableLongStateOf(cooldownRemainingMs) }
        
        LaunchedEffect(cooldownRemainingMs) {
            timeRemaining = cooldownRemainingMs
            while (timeRemaining > 0) {
                delay(100)
                timeRemaining = maxOf(0, timeRemaining - 100)
            }
        }
        
        val seconds = (timeRemaining / 1000.0).toString().take(3)
        
        Card(
            modifier = modifier.padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Preparando conexión... ${seconds}s",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Indicador de estado del lease para debugging (solo en debug builds)
 */
@Composable
fun LeaseStateIndicator(
    leaseState: StreamingLeaseManager.LeaseState,
    modifier: Modifier = Modifier
) {
    // Solo mostrar en builds de debug
    if (com.kybers.play.BuildConfig.DEBUG) {
        val (color, text) = when (leaseState) {
            is StreamingLeaseManager.LeaseState.None -> 
                MaterialTheme.colorScheme.surface to "Sin lease"
            is StreamingLeaseManager.LeaseState.Acquired -> 
                MaterialTheme.colorScheme.primary to "Lease: ${leaseState.ownerId.take(8)}..."
        }
        
        Card(
            modifier = modifier.padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = color)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}