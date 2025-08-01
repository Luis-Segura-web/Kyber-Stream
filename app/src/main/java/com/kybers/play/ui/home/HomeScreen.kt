package com.kybers.play.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kybers.play.R
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.ui.responsive.LoadingScreen
import com.kybers.play.ui.responsive.ResponsiveCard
import com.kybers.play.ui.theme.LocalDeviceSize
import com.kybers.play.ui.responsive.DeviceSize
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val deviceSize = LocalDeviceSize.current

    // Mostrar pantalla de carga responsiva si está cargando
    if (uiState.isLoading) {
        LoadingScreen()
        return
    }

    // Usar Surface en lugar de Scaffold para un diseño más limpio
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // Adaptar padding según tamaño de dispositivo
                horizontal = when (deviceSize) {
                    DeviceSize.COMPACT -> 8.dp
                    DeviceSize.MEDIUM -> 16.dp
                    DeviceSize.EXPANDED -> 24.dp
                },
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Banner principal con diseño responsivo
            if (uiState.bannerContent.isNotEmpty()) {
                item {
                    ResponsiveBannerPager(
                        content = uiState.bannerContent,
                        onMovieClick = { onMovieClick(it.streamId) },
                        deviceSize = deviceSize
                    )
                }
            }

            // Renderiza dinámicamente cada carrusel de la lista con diseño responsivo
            items(uiState.carousels) { carousel ->
                ResponsiveContentRow(
                    title = carousel.title,
                    items = carousel.items,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onChannelClick = onChannelClick,
                    deviceSize = deviceSize
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerPager(content: List<Pair<Movie, String?>>, onMovieClick: (Movie) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { content.size })

    LaunchedEffect(pagerState.pageCount) {
        if (pagerState.pageCount > 1) {
            while (true) {
                delay(5000L)
                val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) { page ->
        val (movie, backdropUrl) = content[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onMovieClick(movie) }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropUrl ?: movie.streamIcon) // Usa el backdrop, o el póster como fallback
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = movie.name,
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
            Text(
                text = movie.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun ContentRow(
    title: String,
    items: List<HomeContentItem>,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is HomeContentItem.MovieItem -> MoviePosterItem(item.movie) { onMovieClick(item.movie.streamId) }
                    is HomeContentItem.SeriesItem -> SeriesPosterItem(item.series) { onSeriesClick(item.series.seriesId) }
                    is HomeContentItem.LiveChannelItem -> LiveChannelCardItem(item.channel) { onChannelClick(item.channel) }
                }
            }
        }
    }
}

@Composable
fun MoviePosterItem(movie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(movie.streamIcon)
                .crossfade(true)
                .fallback(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .build(),
            contentDescription = movie.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SeriesPosterItem(series: Series, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(series.cover)
                .crossfade(true)
                .fallback(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .build(),
            contentDescription = series.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LiveChannelCardItem(channel: LiveStream, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .height(160.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.streamIcon)
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 200f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                channel.currentEpgEvent?.let { event ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.title,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val progress = calculateEpgProgress(event.startTimestamp, event.stopTimestamp)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

private fun calculateEpgProgress(start: Long, end: Long): Float {
    val now = System.currentTimeMillis() / 1000
    if (now < start || start >= end) return 0f
    if (now > end) return 1f
    val totalDuration = (end - start).toFloat()
    val elapsed = (now - start).toFloat()
    return (elapsed / totalDuration).coerceIn(0f, 1f)
}

// ================================
// RESPONSIVE COMPONENTS FOR HOME SCREEN
// ================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResponsiveBannerPager(
    content: List<Pair<Movie, String?>>, 
    onMovieClick: (Movie) -> Unit,
    deviceSize: DeviceSize
) {
    val pagerState = rememberPagerState(pageCount = { content.size })

    LaunchedEffect(pagerState.pageCount) {
        if (pagerState.pageCount > 1) {
            while (true) {
                delay(5000L)
                val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    ResponsiveCard {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    when (deviceSize) {
                        DeviceSize.COMPACT -> 200.dp
                        DeviceSize.MEDIUM -> 250.dp
                        DeviceSize.EXPANDED -> 300.dp
                    }
                )
        ) { page ->
            val (movie, backdropUrl) = content[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMovieClick(movie) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backdropUrl ?: movie.streamIcon)
                        .crossfade(true)
                        .fallback(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .build(),
                    contentDescription = movie.name,
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
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = movie.name,
                        style = when (deviceSize) {
                            DeviceSize.COMPACT -> MaterialTheme.typography.titleMedium
                            DeviceSize.MEDIUM -> MaterialTheme.typography.titleLarge
                            DeviceSize.EXPANDED -> MaterialTheme.typography.headlineMedium
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ResponsiveContentRow(
    title: String,
    items: List<Any>,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    deviceSize: DeviceSize
) {
    if (items.isEmpty()) return

    ResponsiveCard {
        Column {
            // Título de la sección
            Text(
                text = title,
                style = when (deviceSize) {
                    DeviceSize.COMPACT -> MaterialTheme.typography.titleMedium
                    DeviceSize.MEDIUM -> MaterialTheme.typography.titleLarge
                    DeviceSize.EXPANDED -> MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Lista horizontal de elementos
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    ResponsiveContentItem(
                        item = item,
                        onMovieClick = onMovieClick,
                        onSeriesClick = onSeriesClick,
                        onChannelClick = onChannelClick,
                        deviceSize = deviceSize
                    )
                }
            }
        }
    }
}

@Composable
fun ResponsiveContentItem(
    item: Any,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    deviceSize: DeviceSize
) {
    val itemWidth = when (deviceSize) {
        DeviceSize.COMPACT -> 120.dp
        DeviceSize.MEDIUM -> 150.dp
        DeviceSize.EXPANDED -> 180.dp
    }
    
    val itemHeight = when (deviceSize) {
        DeviceSize.COMPACT -> 180.dp
        DeviceSize.MEDIUM -> 225.dp
        DeviceSize.EXPANDED -> 270.dp
    }

    Card(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clickable {
                when (item) {
                    is Movie -> onMovieClick(item.streamId)
                    is Series -> onSeriesClick(item.seriesId)
                    is LiveStream -> onChannelClick(item)
                }
            },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(when (item) {
                        is Movie -> item.streamIcon
                        is Series -> item.cover
                        is LiveStream -> item.streamIcon
                        else -> ""
                    })
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = when (item) {
                    is Movie -> item.name
                    is Series -> item.name
                    is LiveStream -> item.name
                    else -> ""
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Overlay con título
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 200f
                        )
                    )
            )
            
            Text(
                text = when (item) {
                    is Movie -> item.name
                    is Series -> item.name
                    is LiveStream -> item.name
                    else -> ""
                },
                style = when (deviceSize) {
                    DeviceSize.COMPACT -> MaterialTheme.typography.bodySmall
                    DeviceSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                    DeviceSize.EXPANDED -> MaterialTheme.typography.bodyLarge
                },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}
