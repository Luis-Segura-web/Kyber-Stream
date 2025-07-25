package com.kybers.play.ui.series

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Episode
import com.kybers.play.data.remote.model.Series

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    viewModel: SeriesDetailsViewModel,
    onNavigateUp: () -> Unit,
    onPlayEpisode: (episode: Episode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.seriesInfo?.name ?: "Cargando...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                uiState.seriesInfo?.let { series ->
                    SeriesDetailsContent(
                        series = series,
                        uiState = uiState,
                        onSeasonSelected = { seasonNumber -> viewModel.selectSeason(seasonNumber) },
                        onPlayEpisode = onPlayEpisode
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesDetailsContent(
    series: Series,
    uiState: SeriesDetailsUiState,
    onSeasonSelected: (Int) -> Unit,
    onPlayEpisode: (Episode) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Cabecera con la información de la serie
        item {
            SeriesDetailHeader(series = series)
        }

        // Pestañas para las temporadas
        item {
            SeasonTabs(
                seasons = uiState.seasons,
                selectedSeasonNumber = uiState.selectedSeasonNumber,
                onSeasonSelected = onSeasonSelected
            )
        }

        // Lista de episodios para la temporada seleccionada
        val episodes = uiState.episodesBySeason[uiState.selectedSeasonNumber] ?: emptyList()
        items(episodes, key = { it.id }) { episode ->
            EpisodeListItem(episode = episode, onPlayClick = { onPlayEpisode(episode) })
        }
    }
}

@Composable
fun SeriesDetailHeader(series: Series) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(series.backdropPath?.firstOrNull())
                .crossfade(true)
                .build(),
            contentDescription = "Backdrop de la serie",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 400f
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(series.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = "Póster de la serie",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color.Yellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%.1f / 5.0".format(series.rating5Based),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = series.releaseDate ?: "",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
    Text(
        text = series.plot ?: "Sin descripción disponible.",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun SeasonTabs(
    seasons: List<com.kybers.play.data.remote.model.Season>,
    selectedSeasonNumber: Int,
    onSeasonSelected: (Int) -> Unit
) {
    val selectedTabIndex = seasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }.coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            if (selectedTabIndex < tabPositions.size) {
                // --- ¡CORRECCIÓN! ---
                // Se reemplaza la llamada obsoleta 'Indicator' por 'PrimaryIndicator'.
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
                )
            }
        }
    ) {
        seasons.forEach { season ->
            Tab(
                selected = season.seasonNumber == selectedSeasonNumber,
                onClick = { onSeasonSelected(season.seasonNumber) },
                text = { Text(text = "Temporada ${season.seasonNumber}") }
            )
        }
    }
}

@Composable
fun EpisodeListItem(episode: Episode, onPlayClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Toast.makeText(context, "Reproduciendo: ${episode.title}", Toast.LENGTH_SHORT).show()
                onPlayClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = episode.episodeNum.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = episode.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.duration?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onPlayClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir episodio")
        }
    }
}
