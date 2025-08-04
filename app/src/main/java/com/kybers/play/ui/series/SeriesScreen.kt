package com.kybers.play.ui.series

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
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kybers.play.ui.components.DisplayModeToggle
import com.kybers.play.ui.components.ScrollIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Series
import com.kybers.play.ui.channels.CategoryHeader
import com.kybers.play.ui.channels.CategoryVisibilityScreen
import com.kybers.play.ui.channels.SearchBar as CustomSearchBar
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onNavigateToDetails: (seriesId: Int) -> Unit
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
                    targetIndex++ // Por la cabecera de la categoría
                    if (uiState.categories[i].isExpanded) {
                        targetIndex += uiState.categories[i].series.chunked(3).size
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
            contentType = "series"
        )
    } else {
        com.kybers.play.ui.theme.ResponsiveScaffold(
            topBar = {
                ImprovedSeriesTopBar(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshSeriesManually() },
                    onToggleCategoryVisibility = { showCategoryVisibilityScreen = true },
                    onSortSeries = { viewModel.toggleSortMenu(true) }
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
                                        onHeaderClick = { viewModel.onCategoryToggled(expandableCategory.category.categoryId) },
                                        itemCount = expandableCategory.series.size
                                    )
                                }
                            }

                            if (expandableCategory.isExpanded) {
                                val seriesRows = expandableCategory.series.chunked(3)
                                // --- ¡CORRECCIÓN! Usamos itemsIndexed para una clave única ---
                                itemsIndexed(
                                    items = seriesRows,
                                    key = { index, _ -> "${expandableCategory.category.categoryId}-row-$index" }
                                ) { _, rowSeries ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowSeries.forEach { series ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                SeriesPosterItem(
                                                    series = series,
                                                    isFavorite = uiState.favoriteSeriesIds.contains(series.seriesId.toString()),
                                                    onPosterClick = { onNavigateToDetails(series.seriesId) },
                                                    onFavoriteClick = { viewModel.toggleFavoriteStatus(series.seriesId) }
                                                )
                                            }
                                        }
                                        repeat(3 - rowSeries.size) {
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
            currentItemSortOrder = uiState.seriesSortOrder,
            onCategorySortOrderSelected = { order -> viewModel.setCategorySortOrder(order) },
            onItemSortOrderSelected = { order -> viewModel.setSeriesSortOrder(order) },
            onDismiss = { viewModel.toggleSortMenu(false) },
            categorySortLabel = "Ordenar Categorías por:",
            itemSortLabel = "Ordenar Series por:"
        )
    }
}

@Composable
fun SeriesPosterItem(
    series: Series,
    isFavorite: Boolean,
    onPosterClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .clickable(onClick = onPosterClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(series.cover)
                    .crossfade(true)
                    .error(android.R.drawable.stat_notify_error)
                    .build(),
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (series.rating5Based > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Calificación",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%.1f".format(series.rating5Based),
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

/**
 * Barra superior mejorada para la pantalla de series, similar a la de canales y películas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImprovedSeriesTopBar(
    uiState: SeriesUiState,
    onRefresh: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    onSortSeries: () -> Unit,
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
                        imageVector = Icons.Outlined.Slideshow,
                        contentDescription = "Logo Series",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Series",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.totalSeriesCount} series",
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
                    // Display mode toggle button
                    DisplayModeToggle(
                        currentMode = uiState.displayMode,
                        onModeChanged = { mode -> viewModel.setDisplayMode(mode) }
                    )

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
                        onClick = onSortSeries,
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
