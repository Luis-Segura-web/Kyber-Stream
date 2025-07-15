package com.kybers.play.ui.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.LiveStream

@Composable
fun ChannelsScreen(viewModel: ChannelsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val player = viewModel.player
    val lifecycleOwner = LocalLifecycleOwner.current

    // Gestionamos el ciclo de vida del reproductor.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Reproductor en la parte superior
        AndroidView(
            factory = { context ->
                PlayerView(context).apply { this.player = player }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f) // Proporción de video estándar
                .background(Color.Black)
        )

        // 2. Barra de búsqueda
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Buscar canales...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") }
        )

        // 3. Lista de categorías y canales
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                uiState.categories.forEach { expandableCategory ->
                    item {
                        CategoryHeader(
                            categoryName = expandableCategory.category.categoryName,
                            isExpanded = expandableCategory.isExpanded,
                            isLoading = expandableCategory.isLoading,
                            onHeaderClick = { viewModel.onCategoryToggled(expandableCategory.category.categoryId) }
                        )
                    }
                    // Mostramos los canales solo si la categoría está expandida
                    if (expandableCategory.isExpanded) {
                        items(expandableCategory.channels) { channel ->
                            ChannelListItem(
                                channel = channel,
                                onChannelClick = { viewModel.onChannelSelected(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    isLoading: Boolean,
    onHeaderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (isExpanded) "Contraer" else "Expandir",
                modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
            )
        }
    }
}

@Composable
fun ChannelListItem(
    channel: LiveStream,
    onChannelClick: (LiveStream) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChannelClick(channel) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp), // Indentación para los canales
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(channel.streamIcon)
                .crossfade(true)
                .error(android.R.drawable.stat_notify_error)
                .build(),
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = channel.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
