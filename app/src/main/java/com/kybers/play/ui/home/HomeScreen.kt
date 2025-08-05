package com.kybers.play.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.delay

/**
 * Enhanced top bar for Home screen with its own distinctive style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedHomeTopBar(
    userName: String,
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Main section - Logo, title and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo and title section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Logo Inicio",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Inicio",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                        
                        // Smart greeting based on time of day
                        val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                        val greeting = when (currentHour) {
                            in 5..11 -> "Buenos días"
                            in 12..17 -> "Buenas tardes"
                            else -> "Buenas noches"
                        }
                        
                        Text(
                            text = "$greeting, $userName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // User profile button
                    IconButton(
                        onClick = { /* Profile action */ },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Settings button
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

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
    val deviceSize = com.kybers.play.ui.theme.LocalDeviceSize.current

    com.kybers.play.ui.theme.ResponsiveScaffold(
        topBar = {
            EnhancedHomeTopBar(
                userName = uiState.userName,
                onSettingsClick = onSettingsClick
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Contenido que se adapta al tamaño de pantalla
            when (deviceSize) {
                com.kybers.play.ui.theme.DeviceSize.COMPACT -> CompactHomeContent(
                    uiState = uiState,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onChannelClick = onChannelClick,
                    paddingValues = paddingValues
                )
                com.kybers.play.ui.theme.DeviceSize.MEDIUM -> MediumHomeContent(
                    uiState = uiState,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onChannelClick = onChannelClick,
                    paddingValues = paddingValues
                )
                com.kybers.play.ui.theme.DeviceSize.EXPANDED -> ExpandedHomeContent(
                    uiState = uiState,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onChannelClick = onChannelClick,
                    paddingValues = paddingValues
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
        Box(modifier = Modifier.fillMaxSize()) {
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
            
            // Movie badge
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Movie",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            
            // Rating badge
            if (movie.rating5Based > 0) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⭐",
                            fontSize = 10.sp
                        )
                        Text(
                            text = String.format("%.1f", movie.rating5Based),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Title overlay - updated to match Movies/Series screen style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                Text(
                    text = movie.name,
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
        Box(modifier = Modifier.fillMaxSize()) {
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
            
            // Series badge
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Serie",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            
            // Rating badge
            if (series.rating5Based > 0) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⭐",
                            fontSize = 10.sp
                        )
                        Text(
                            text = String.format("%.1f", series.rating5Based),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Title overlay - updated to match Movies/Series screen style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                Text(
                    text = series.name,
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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

@Composable
private fun CompactHomeContent(
    uiState: com.kybers.play.ui.home.HomeUiState,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    paddingValues: PaddingValues
) {
    // Diseño para teléfonos: lista vertical simple
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (uiState.bannerContent.isNotEmpty()) {
            item {
                BannerPager(
                    content = uiState.bannerContent,
                    onMovieClick = { onMovieClick(it.streamId) }
                )
            }
        }

        // Renderiza dinámicamente cada carrusel de la lista.
        items(uiState.carousels) { carousel ->
            ContentRow(
                title = carousel.title,
                items = carousel.items,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onChannelClick = onChannelClick
            )
        }
    }
}

@Composable
private fun MediumHomeContent(
    uiState: com.kybers.play.ui.home.HomeUiState,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    paddingValues: PaddingValues
) {
    // Diseño para tablets medianas: contenido similar pero con más espacio
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (uiState.bannerContent.isNotEmpty()) {
            item {
                BannerPager(
                    content = uiState.bannerContent,
                    onMovieClick = { onMovieClick(it.streamId) }
                )
            }
        }

        // Renderiza dinámicamente cada carrusel de la lista.
        items(uiState.carousels) { carousel ->
            ContentRow(
                title = carousel.title,
                items = carousel.items,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onChannelClick = onChannelClick
            )
        }
    }
}

@Composable
private fun ExpandedHomeContent(
    uiState: com.kybers.play.ui.home.HomeUiState,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    paddingValues: PaddingValues
) {
    // Diseño para pantallas grandes: contenido con más espacio y posible diseño de dos columnas
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
    ) {
        if (uiState.bannerContent.isNotEmpty()) {
            item {
                BannerPager(
                    content = uiState.bannerContent,
                    onMovieClick = { onMovieClick(it.streamId) }
                )
            }
        }

        // Renderiza dinámicamente cada carrusel de la lista.
        items(uiState.carousels) { carousel ->
            ContentRow(
                title = carousel.title,
                items = carousel.items,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onChannelClick = onChannelClick
            )
        }
    }
}