package com.kybers.play.ui.channels

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.ui.player.PlayerActivity

@Composable
fun ChannelsScreen(viewModel: ChannelsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Row(modifier = Modifier.fillMaxSize()) {
        // Panel izquierdo: Categorías
        CategoryPanel(
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            isLoading = uiState.isLoadingCategories,
            onCategoryClick = { viewModel.onCategorySelected(it) },
            modifier = Modifier.weight(0.3f)
        )

        // Panel derecho: Canales
        ChannelPanel(
            channels = uiState.channels,
            isLoading = uiState.isLoadingChannels,
            onChannelClick = { channel ->
                // Construimos la URL del stream usando la función del ViewModel
                val streamUrl = viewModel.buildStreamUrl(channel)

                // Creamos el Intent para lanzar la PlayerActivity
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("stream_url", streamUrl)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.weight(0.7f)
        )
    }
}

@Composable
fun CategoryPanel(
    categories: List<Category>,
    selectedCategoryId: String?,
    isLoading: Boolean,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(categories) { category ->
                    CategoryItem(
                        category = category,
                        isSelected = category.categoryId == selectedCategoryId,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryItem(category: Category, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    Text(
        text = category.categoryName,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ChannelPanel(
    channels: List<LiveStream>,
    isLoading: Boolean,
    onChannelClick: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxHeight()
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(channels) { channel ->
                    ChannelItem(
                        channel = channel,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelItem(channel: LiveStream, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.streamIcon)
                    .crossfade(true)
                    .error(android.R.drawable.ic_menu_report_image) // Icono de error por si falla la carga
                    .build(),
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Gray.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = channel.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
