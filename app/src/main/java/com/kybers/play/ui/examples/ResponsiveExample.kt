package com.kybers.play.ui.examples

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kybers.play.ui.theme.*
import com.kybers.play.ui.components.AccessibleHeading
import com.kybers.play.ui.components.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsiveExampleScreen() {
    val deviceSize = LocalDeviceSize.current
    
    ResponsiveScaffold(
        topBar = {
            TopAppBar(
                title = { 
                    AccessibleHeading(
                        text = "Responsive Demo",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { paddingValues ->
        ResponsiveColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device size indicator
            ResponsiveCard {
                Column {
                    Text(
                        text = "Device Size: ${deviceSize.name}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = when (deviceSize) {
                            DeviceSize.COMPACT -> "Teléfonos (<600dp)"
                            DeviceSize.MEDIUM -> "Tablets pequeñas (<840dp)"
                            DeviceSize.EXPANDED -> "Tablets grandes/escritorio (>=840dp)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Typography example
            ResponsiveCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Typography Scale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text("Body Large", style = MaterialTheme.typography.bodyLarge)
                    Text("Title Large", style = MaterialTheme.typography.titleLarge)
                    Text("Headline Medium", style = MaterialTheme.typography.headlineMedium)
                    Text("Label Small", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            // Grid layout example
            ResponsiveCard {
                Column {
                    Text(
                        text = "Grid Layout (adapts columns based on screen size)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Example grid items
                    val gridItems = listOf(
                        "Home" to Icons.Default.Home,
                        "Movies" to Icons.Default.Movie,
                        "Profile" to Icons.Default.Person,
                        "Settings" to Icons.Default.Settings
                    )
                    
                    val columns = when (deviceSize) {
                        DeviceSize.COMPACT -> 2
                        DeviceSize.MEDIUM -> 3
                        DeviceSize.EXPANDED -> 4
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(gridItems.size) { index ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = gridItems[index].second,
                                        contentDescription = gridItems[index].first,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = gridItems[index].first,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Navigation example
            ResponsiveCard {
                Column {
                    Text(
                        text = "Navigation Pattern",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = when (deviceSize) {
                            DeviceSize.COMPACT -> "Bottom Navigation (shown at bottom)"
                            DeviceSize.MEDIUM, DeviceSize.EXPANDED -> "Navigation Rail (shown on side)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}