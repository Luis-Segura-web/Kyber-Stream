package com.kybers.play.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kybers.play.data.preferences.PreferenceManager

/**
 * Demostración y testing de todas las combinaciones de temas
 * Permite probar las 9 combinaciones: 3 colores × 3 modos
 */
@Composable
fun ThemeTestScreen(
    onThemeChange: (ThemeConfig) -> Unit
) {
    val allColors = listOf(ThemeColor.BLUE, ThemeColor.PURPLE, ThemeColor.PINK)
    val allModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    val allCombinations = allColors.flatMap { color ->
        allModes.map { mode -> ThemeConfig(color, mode) }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Prueba de Temas - 9 Combinaciones",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Text(
                text = "Toca cualquier combinación para aplicarla:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        items(allCombinations) { config ->
            ThemeTestCard(
                config = config,
                onClick = { onThemeChange(config) }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Todas las combinaciones deberían funcionar correctamente:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("✅ Azul Claro", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Azul Oscuro", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Azul Sistema", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Púrpura Claro", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Púrpura Oscuro", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Púrpura Sistema", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Rosa Claro", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Rosa Oscuro", style = MaterialTheme.typography.bodyMedium)
                Text("✅ Rosa Sistema", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ThemeTestCard(
    config: ThemeConfig,
    onClick: () -> Unit
) {
    val colorName = when (config.color) {
        ThemeColor.BLUE -> "Azul"
        ThemeColor.PURPLE -> "Púrpura"
        ThemeColor.PINK -> "Rosa"
    }
    
    val modeName = when (config.mode) {
        ThemeMode.LIGHT -> "Claro"
        ThemeMode.DARK -> "Oscuro"
        ThemeMode.SYSTEM -> "Sistema"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemePreviewCard(
                config = config,
                modifier = Modifier.weight(1f)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$colorName $modeName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Color: ${config.color.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Modo: ${config.mode.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Función de testing para validar migración desde el sistema anterior
 */
fun testThemeMigration(context: android.content.Context): List<String> {
    val preferenceManager = PreferenceManager(context)
    val results = mutableListOf<String>()
    
    // Casos de prueba para migración
    val testCases = mapOf(
        "LIGHT" to ThemeConfig(ThemeColor.BLUE, ThemeMode.LIGHT),
        "DARK" to ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK),
        "BLUE" to ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK),
        "PURPLE" to ThemeConfig(ThemeColor.PURPLE, ThemeMode.DARK),
        "PINK" to ThemeConfig(ThemeColor.PINK, ThemeMode.DARK),
        "SYSTEM" to ThemeConfig(ThemeColor.BLUE, ThemeMode.SYSTEM)
    )
    
    testCases.forEach { (legacy, expected) ->
        // Simular tema anterior
        preferenceManager.saveAppTheme(legacy)
        
        // Crear ThemeManager y verificar migración
        val themeManager = ThemeManager(preferenceManager)
        val migrated = themeManager.currentThemeConfig.value
        
        if (migrated.color == expected.color && migrated.mode == expected.mode) {
            results.add("✅ Migración $legacy → ${expected.color} ${expected.mode}")
        } else {
            results.add("❌ Migración $legacy falló: ${migrated.color} ${migrated.mode} != ${expected.color} ${expected.mode}")
        }
    }
    
    return results
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun ThemeTestScreenPreview() {
    KyberStreamTheme {
        ThemeTestScreen(
            onThemeChange = { }
        )
    }
}