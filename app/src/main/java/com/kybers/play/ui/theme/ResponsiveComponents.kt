package com.kybers.play.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

@Composable
fun ResponsiveRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    val deviceSize = LocalDeviceSize.current
    
    val padding = when (deviceSize) {
        DeviceSize.COMPACT -> 8.dp
        DeviceSize.MEDIUM -> 12.dp
        DeviceSize.EXPANDED -> 16.dp
    }
    
    Row(
        modifier = modifier.padding(horizontal = padding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}

@Composable
fun ResponsiveColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val deviceSize = LocalDeviceSize.current
    
    val padding = when (deviceSize) {
        DeviceSize.COMPACT -> 8.dp
        DeviceSize.MEDIUM -> 12.dp
        DeviceSize.EXPANDED -> 16.dp
    }
    
    Column(
        modifier = modifier.padding(horizontal = padding),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}