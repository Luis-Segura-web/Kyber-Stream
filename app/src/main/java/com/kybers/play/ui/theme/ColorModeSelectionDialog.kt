package com.kybers.play.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Diálogo moderno para selección independiente de color y modo de tema
 * Permite 9 combinaciones: 3 colores × 3 modos
 */
@Composable
fun ColorModeSelectionDialog(
    currentConfig: ThemeConfig,
    onConfigSelected: (ThemeConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentConfig.color) }
    var selectedMode by remember { mutableStateOf(currentConfig.mode) }
    
    // Preview del tema seleccionado
    val previewConfig = ThemeConfig(selectedColor, selectedMode)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Personalizar Tema",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Elige tu color favorito y modo de visualización",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                // Vista previa del tema
                ThemePreviewCard(
                    config = previewConfig,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Sección de selección de color
                ColorSelectionSection(
                    availableColors = listOf(ThemeColor.BLUE, ThemeColor.PURPLE, ThemeColor.PINK),
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )
                
                // Sección de selección de modo
                ModeSelectionSection(
                    availableModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
                    selectedMode = selectedMode,
                    onModeSelected = { selectedMode = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfigSelected(previewConfig)
                    onDismiss()
                }
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Tarjeta de vista previa del tema seleccionado
 */
@Composable
fun ThemePreviewCard(
    config: ThemeConfig,
    modifier: Modifier = Modifier
) {
    val isDark = when (config.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    val colors = when (config.color) {
        ThemeColor.BLUE -> if (isDark) mapOf(
            "primary" to BlueTheme.Primary,
            "background" to BlueTheme.BackgroundDark,
            "surface" to BlueTheme.SurfaceDark,
            "onSurface" to BlueTheme.OnSurfaceDark
        ) else mapOf(
            "primary" to BlueTheme.Primary,
            "background" to BlueTheme.BackgroundLight,
            "surface" to BlueTheme.SurfaceLight,
            "onSurface" to BlueTheme.OnSurfaceLight
        )
        ThemeColor.PURPLE -> if (isDark) mapOf(
            "primary" to PurpleTheme.Primary,
            "background" to PurpleTheme.BackgroundDark,
            "surface" to PurpleTheme.SurfaceDark,
            "onSurface" to PurpleTheme.OnSurfaceDark
        ) else mapOf(
            "primary" to PurpleTheme.Primary,
            "background" to PurpleTheme.BackgroundLight,
            "surface" to PurpleTheme.SurfaceLight,
            "onSurface" to PurpleTheme.OnSurfaceLight
        )
        ThemeColor.PINK -> if (isDark) mapOf(
            "primary" to PinkTheme.Primary,
            "background" to PinkTheme.BackgroundDark,
            "surface" to PinkTheme.SurfaceDark,
            "onSurface" to PinkTheme.OnSurfaceDark
        ) else mapOf(
            "primary" to PinkTheme.Primary,
            "background" to PinkTheme.BackgroundLight,
            "surface" to PinkTheme.SurfaceLight,
            "onSurface" to PinkTheme.OnSurfaceLight
        )
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors["surface"]!!
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Vista Previa",
                style = MaterialTheme.typography.labelMedium,
                color = colors["onSurface"]!!.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Barra de título simulada
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = colors["primary"]!!
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White.copy(alpha = 0.7f)
                    ) {}
                }
            }
            
            // Contenido simulado
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = colors["background"]!!
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = colors["onSurface"]!!.copy(alpha = 0.8f)
                    ) {}
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = colors["onSurface"]!!.copy(alpha = 0.6f)
                    ) {}
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = colors["onSurface"]!!.copy(alpha = 0.4f)
                    ) {}
                }
            }
        }
    }
}

/**
 * Sección de selección de color
 */
@Composable
fun ColorSelectionSection(
    availableColors: List<ThemeColor>,
    selectedColor: ThemeColor,
    onColorSelected: (ThemeColor) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🎨 Color Base",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            availableColors.forEach { color ->
                ColorOption(
                    color = color,
                    isSelected = color == selectedColor,
                    onClick = { onColorSelected(color) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Opción individual de color
 */
@Composable
fun ColorOption(
    color: ThemeColor,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorName, colorIcon, primaryColor) = when (color) {
        ThemeColor.BLUE -> Triple("Azul", "🔵", BlueTheme.Primary)
        ThemeColor.PURPLE -> Triple("Púrpura", "🟣", PurpleTheme.Primary)
        ThemeColor.PINK -> Triple("Rosa", "🌸", PinkTheme.Primary)
    }
    
    Card(
        modifier = modifier
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
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Círculo de color
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = primaryColor
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = colorIcon,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Text(
                text = colorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
            
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(6.dp)
                ) {}
            }
        }
    }
}

/**
 * Sección de selección de modo
 */
@Composable
fun ModeSelectionSection(
    availableModes: List<ThemeMode>,
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🌗 Modo de Visualización",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableModes.forEach { mode ->
                ModeOption(
                    mode = mode,
                    isSelected = mode == selectedMode,
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

/**
 * Opción individual de modo
 */
@Composable
fun ModeOption(
    mode: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (modeName, modeDescription, modeIcon) = when (mode) {
        ThemeMode.LIGHT -> Triple("Claro", "Perfecto para ambientes iluminados", Icons.Default.LightMode)
        ThemeMode.DARK -> Triple("Oscuro", "Ideal para ambientes con poca luz", Icons.Default.DarkMode)
        ThemeMode.SYSTEM -> Triple("Automático", "Sigue la configuración del dispositivo", Icons.Default.SettingsSystemDaydream)
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
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = modeIcon,
                contentDescription = modeName,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = modeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(8.dp)
                ) {}
            }
        }
    }
}