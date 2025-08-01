package com.kybers.play.ui.responsive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun AccessibleHeading(
    text: String,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    Text(
        text = text,
        style = style,
        modifier = Modifier.semantics { 
            heading()
        }
    )
}

@Composable
fun AccessibleImage(
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
fun AccessibilitySettings() {
    val fontScale = remember { mutableStateOf(1.0f) }
    val highContrast = remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Tamaño de fuente",
            style = MaterialTheme.typography.titleMedium
        )
        
        Slider(
            value = fontScale.value,
            onValueChange = { fontScale.value = it },
            valueRange = 0.8f..1.5f,
            steps = 6
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = highContrast.value,
                onCheckedChange = { highContrast.value = it }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text("Alto contraste")
        }
    }
}