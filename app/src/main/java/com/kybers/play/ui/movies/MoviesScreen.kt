package com.kybers.play.ui.movies

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.ui.channels.CategoryHeader
import com.kybers.play.ui.channels.CategoryVisibilityScreen
import com.kybers.play.ui.channels.SearchBar as CustomSearchBar
import com.kybers.play.ui.components.ScrollIndicator
import com.kybers.play.ui.player.SortOrder
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onNavigateToDetails: (movieId: Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val deviceSize = com.kybers.play.ui.theme.LocalDeviceSize.current
    var showCategoryVisibilityScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.scrollToItemEvent.collectLatest { categoryId ->
            val categoryIndex = uiState.categories.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex != -1) {
                var targetIndex = 0
                for (i in 0 until categoryIndex) {
                    targetIndex++
                    if (uiState.categories[i].isExpanded) {
                        targetIndex += uiState.categories[i].movies.chunked(3).size
                    }
                }
                lazyListState.animateScrollToItem(targetIndex)
            }
        }
    }

    if (showCategoryVisibilityScreen) {
        CategoryVisibilityScreen(
            allCategories = uiState.masterCategoryList,
            hiddenCategoryIds = uiState.hiddenCategoryIds,
            onBack = { showCategoryVisibilityScreen = false },
            onSave = { ids ->
                viewModel.setHiddenCategories(ids)
                showCategoryVisibilityScreen = false
            },
            contentType = "películas"
        )
    } else {
        com.kybers.play.ui.theme.ResponsiveScaffold(
            topBar = {
                ImprovedMovieTopBar(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshMoviesManually() },
                    onToggleCategoryVisibility = { showCategoryVisibilityScreen = true },
                    onSortMovies = { viewModel.toggleSortMenu(true) }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                CustomSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClear = { viewModel.onSearchQueryChanged("") }
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        uiState.categories.forEach { expandableCategory ->
                            stickyHeader(key = expandableCategory.category.categoryId) {
                                Surface(modifier = Modifier.fillParentMaxWidth()) {
                                    CategoryHeader(
                                        categoryName = expandableCategory.category.categoryName,
                                        isExpanded = expandableCategory.isExpanded,
                                        onHeaderClick = { viewModel.onCategoryToggled(expandableCategory.category.categoryId ?: "") },
                                        itemCount = expandableCategory.movies.size
                                    )
                                }
                            }

                            if (expandableCategory.isExpanded) {
                                val movieRows = expandableCategory.movies.chunked(3)
                                itemsIndexed(
                                    items = movieRows,
                                    key = { index, _ -> "${expandableCategory.category.categoryId}-row-$index" }
                                ) { _, rowMovies ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowMovies.forEach { movie ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                MoviePosterItem(
                                                    viewModel = viewModel,
                                                    movie = movie,
                                                    isFavorite = uiState.favoriteMovieIds.contains(movie.streamId.toString()),
                                                    onPosterClick = { onNavigateToDetails(movie.streamId) },
                                                    onFavoriteClick = { viewModel.toggleFavoriteStatus(movie.streamId) }
                                                )
                                            }
                                        }
                                        repeat(3 - rowMovies.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add scroll indicator for better navigation
                ScrollIndicator(
                    listState = lazyListState,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }

    if (uiState.showSortMenu) {
        SortOptionsDialog(
            currentCategorySortOrder = uiState.categorySortOrder,
            currentItemSortOrder = uiState.movieSortOrder,
            onCategorySortOrderSelected = { order -> viewModel.setCategorySortOrder(order) },
            onItemSortOrderSelected = { order -> viewModel.setMovieSortOrder(order) },
            onDismiss = { viewModel.toggleSortMenu(false) },
            categorySortLabel = "Ordenar Categorías por:",
            itemSortLabel = "Ordenar Películas por:"
        )
    }
}

@Composable
fun MoviePosterItem(
    viewModel: MoviesViewModel,
    movie: Movie,
    isFavorite: Boolean,
    onPosterClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .clickable(onClick = onPosterClick)
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(viewModel.getFinalPosterUrl(movie))
                    .crossfade(true)
                    .fallback(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (movie.rating5Based > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Calificación",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%.1f".format(movie.rating5Based),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Añadir a favoritos",
                        tint = if (isFavorite) Color(0xFFE91E63) else Color.White
                    )
                }
            }

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
fun SortOptionsDialog(
    currentCategorySortOrder: SortOrder,
    currentItemSortOrder: SortOrder,
    onCategorySortOrderSelected: (SortOrder) -> Unit,
    onItemSortOrderSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
    categorySortLabel: String,
    itemSortLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opciones de Ordenación") },
        text = {
            Column {
                Text(categorySortLabel, style = MaterialTheme.typography.titleSmall)
                SortOrder.values().forEach { order ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySortOrderSelected(order) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (order == currentCategorySortOrder),
                            onClick = { onCategorySortOrderSelected(order) }
                        )
                        Text(text = order.toLocalizedName())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(itemSortLabel, style = MaterialTheme.typography.titleSmall)
                SortOrder.values().forEach { order ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onItemSortOrderSelected(order) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (order == currentItemSortOrder),
                            onClick = { onItemSortOrderSelected(order) }
                        )
                        Text(text = order.toLocalizedName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun SortOrder.toLocalizedName(): String {
    return when (this) {
        SortOrder.DEFAULT -> "Por Defecto"
        SortOrder.AZ -> "Alfabético (A-Z)"
        SortOrder.ZA -> "Alfabético (Z-A)"
    }
}

/**
 * Barra superior mejorada para la pantalla de películas, similar a la de canales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImprovedMovieTopBar(
    uiState: MoviesUiState,
    onRefresh: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    onSortMovies: () -> Unit,
) {
    val hasHiddenCategories = uiState.hiddenCategoryIds.isNotEmpty()

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
            // Sección principal - Logo, título y botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo y título más compactos
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = "Logo Películas",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Películas",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.totalMovieCount} películas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }

                // Botones de acción más compactos
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Refresh button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Hide categories button
                    IconButton(
                        onClick = onToggleCategoryVisibility,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = if (hasHiddenCategories) Icons.Filled.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (hasHiddenCategories) "Mostrar categorías ocultas" else "Ocultar categorías",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Sort button
                    IconButton(
                        onClick = onSortMovies,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Ordenar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Sección inferior con info de actualización (solo si es necesaria)
            if (uiState.lastUpdatedTimestamp > 0 || uiState.isRefreshing) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (uiState.lastUpdatedTimestamp > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Últ. Act: ${formatTimestamp(uiState.lastUpdatedTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }

                    if (uiState.isRefreshing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Actualizando...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Nunca"
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
    return sdf.format(Date(timestamp))
}
