package com.kybers.play.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Component to show implemented improvements in the app
 */
@Composable
fun ImprovementsStatusCard(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50), // Green
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Mejoras Implementadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Text(
                text = "Se han aplicado las siguientes mejoras a tu aplicación:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImprovementItem(
                    icon = Icons.Default.Palette,
                    title = "Cambio de Tema Instantáneo",
                    description = "Los temas se aplican inmediatamente sin reiniciar"
                )
                
                ImprovementItem(
                    icon = Icons.Default.Lock,
                    title = "Control Parental Mejorado",
                    description = "Incluye categorías de canales y ocultación inmediata"
                )
                
                ImprovementItem(
                    icon = Icons.Default.Movie,
                    title = "Portadas Mejoradas",
                    description = "Títulos con máximo 3 líneas y calificación con 1 estrella"
                )
                
                ImprovementItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Controles Adaptativos",
                    description = "Optimizados para orientación vertical y horizontal"
                )
                
                ImprovementItem(
                    icon = Icons.Default.List,
                    title = "Indicadores de Scroll",
                    description = "Indicadores visibles para orientación en listas"
                )
                
                ImprovementItem(
                    icon = Icons.Default.Api,
                    title = "Límites de API Optimizados",
                    description = "Control de velocidad para no exceder límites de TMDB"
                )
            }
        }
    }
}

@Composable
private fun ImprovementItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}