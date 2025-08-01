package com.kybers.play.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A composable that shows a preview of a theme
 */
@Composable
fun ThemePreview(
    themeName: String,
    isLightTheme: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = if (isLightTheme) {
        mapOf(
            "primary" to BlueTheme.Primary,
            "secondary" to BlueTheme.Secondary,
            "background" to BlueTheme.BackgroundLight,
            "surface" to BlueTheme.SurfaceLight
        )
    } else {
        mapOf(
            "primary" to BlueTheme.Primary,
            "secondary" to BlueTheme.Secondary,
            "background" to BlueTheme.BackgroundDark,
            "surface" to BlueTheme.SurfaceDark
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = themeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
            
            // Color preview swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                colors.values.forEach { color ->
                    Surface(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = color
                    ) {}
                }
            }
            
            // Mini UI preview
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = colors["background"]!!
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Simulated app bar
                    Surface(
                        modifier = Modifier
                            .size(8.dp, 24.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors["primary"]!!
                    ) {}
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Simulated text lines
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = colors["primary"]!!.copy(alpha = 0.8f)
                        ) {}
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = colors["primary"]!!.copy(alpha = 0.5f)
                        ) {}
                    }
                    
                    // Simulated button
                    Surface(
                        modifier = Modifier
                            .size(16.dp, 12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = colors["secondary"]!!
                    ) {}
                }
            }
        }
    }
}

/**
 * A dialog that allows users to select a theme with preview
 */
@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Seleccionar Tema",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Elige el tema que prefieras para la aplicación",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                ThemePreview(
                    themeName = "Claro",
                    isLightTheme = true,
                    isSelected = currentTheme == ThemeMode.LIGHT,
                    onClick = { 
                        onThemeSelected(ThemeMode.LIGHT)
                        onDismiss()
                    }
                )
                
                ThemePreview(
                    themeName = "Oscuro",
                    isLightTheme = false,
                    isSelected = currentTheme == ThemeMode.DARK,
                    onClick = { 
                        onThemeSelected(ThemeMode.DARK)
                        onDismiss()
                    }
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            onThemeSelected(ThemeMode.SYSTEM)
                            onDismiss()
                        }
                        .then(
                            if (currentTheme == ThemeMode.SYSTEM) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp)
                                )
                            } else {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                            }
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Automático (Sistema)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (currentTheme == ThemeMode.SYSTEM) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp)
                            ) {}
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