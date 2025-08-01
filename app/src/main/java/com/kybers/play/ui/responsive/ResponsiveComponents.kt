package com.kybers.play.ui.responsive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kybers.play.ui.theme.LocalDeviceSize

@Composable
fun ResponsiveCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val deviceSize = LocalDeviceSize.current
    
    val padding = when (deviceSize) {
        DeviceSize.COMPACT -> 8.dp
        DeviceSize.MEDIUM -> 12.dp
        DeviceSize.EXPANDED -> 16.dp
    }
    
    Card(
        modifier = modifier
            .padding(padding)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
fun ResponsiveGridLayout(
    items: List<@Composable () -> Unit>
) {
    val deviceSize = LocalDeviceSize.current
    
    val columns = when (deviceSize) {
        DeviceSize.COMPACT -> 1
        DeviceSize.MEDIUM -> 2
        DeviceSize.EXPANDED -> 3
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items.size) { index ->
            items[index]()
        }
    }
}