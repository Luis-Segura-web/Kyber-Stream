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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.ui.components.DisplayModeToggle
import com.kybers.play.ui.components.DisplayMode as ComponentDisplayMode
import com.kybers.play.ui.player.SortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kybers.play.ui.components.SmartCategoryList
import com.kybers.play.ui.components.CategoryData
import com.kybers.play.ui.components.CategoryManager
import com.kybers.play.ui.components.GlobalCategoryManager
import com.kybers.play.ui.components.categories.ScreenType
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Series
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
                        val columnsCount = when (uiState.displayMode) {
                            ComponentDisplayMode.GRID -> 3
                            ComponentDisplayMode.LIST -> 1
                        }
                        targetIndex += uiState.categories[i].series.chunked(columnsCount).size
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
                    onSortSeries = { viewModel.toggleSortMenu(true) },
                    onDisplayModeChanged = { mode -> viewModel.setDisplayMode(mode) }
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
                    // Convert categories to smart category format
                    val smartCategoryData = uiState.categories.map { expandableCategory ->
                        CategoryData(
                            categoryId = expandableCategory.category.categoryId,
                            categoryName = expandableCategory.category.categoryName,
                            items = expandableCategory.series,
                            isExpanded = expandableCategory.isExpanded
                        )
                    }

                    SmartCategoryList(
                        categories = smartCategoryData,
                        screenType = ScreenType.SERIES,
                        lazyListState = lazyListState,
                        displayMode = uiState.displayMode,
                        gridColumns = 3,
                        onCategoryToggled = { categoryId ->
                            viewModel.onCategoryToggled(categoryId)
                        },
                        onItemClick = { series -> onNavigateToDetails(series.seriesId) },
                        onItemFavoriteToggle = { series -> viewModel.toggleFavoriteStatus(series.seriesId) },
                        isItemFavorite = { series -> uiState.favoriteSeriesIds.contains(series.seriesId.toString()) },
                        itemContent = { series ->
                            when (uiState.displayMode) {
                                ComponentDisplayMode.GRID -> {
                                    SeriesPosterItem(
                                        series = series,
                                        isFavorite = uiState.favoriteSeriesIds.contains(series.seriesId.toString()),
                                        onPosterClick = { onNavigateToDetails(series.seriesId) },
                                        onFavoriteClick = { viewModel.toggleFavoriteStatus(series.seriesId) }
                                    )
                                }
                                ComponentDisplayMode.LIST -> {
                                    SeriesListItem(
                                        series = series,
                                        isFavorite = uiState.favoriteSeriesIds.contains(series.seriesId.toString()),
                                        onSeriesClick = { onNavigateToDetails(series.seriesId) },
                                        onFavoriteClick = { viewModel.toggleFavoriteStatus(series.seriesId) }
                                    )
                                }
                            }
                        },
                        searchQuery = uiState.searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
 * List item for series - shows detailed info with expandable plot
 */
@Composable
fun SeriesListItem(
    series: Series,
    isFavorite: Boolean,
    onSeriesClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeriesClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Mini poster image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(series.cover)
                    .crossfade(true)
                    .error(android.R.drawable.stat_notify_error)
                    .build(),
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Series information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title and year row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = series.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Year and rating
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // Show release date or year
                            series.releaseDate?.let { releaseDate ->
                                if (releaseDate.isNotBlank() && releaseDate.length >= 4) {
                                    val year = releaseDate.substring(0, 4)
                                    Text(
                                        text = year,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                } else {
                                    Text(
                                        text = "N/A",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            } ?: run {
                                Text(
                                    text = "N/A",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            
                            if (series.rating5Based > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Calificación",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "%.1f".format(series.rating5Based),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // Favorite button
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                            tint = if (isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Expandable description - using series plot
                val description = series.plot?.takeIf { it.isNotBlank() }
                    ?: "Haz clic para ver más detalles de esta serie."
                
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2
                    val shouldShowToggle = description.length > 100 // Show toggle for longer descriptions
                    
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    
                    if (shouldShowToggle) {
                        Text(
                            text = if (isDescriptionExpanded) "Leer menos" else "Leer más",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        )
                    }
                }
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
    onDisplayModeChanged: (ComponentDisplayMode) -> Unit,
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
                        onModeChanged = { mode -> onDisplayModeChanged(mode) }
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