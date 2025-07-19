package com.kybers.play.ui.movies

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.* // Importa todo de Material3 para incluir ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.ui.channels.CategoryHeader
import com.kybers.play.ui.channels.SearchBar
import com.kybers.play.ui.channels.SortOrder
import kotlinx.coroutines.flow.collectLatest

// Anotación necesaria para usar APIs experimentales de Material3 (como TopAppBarDefaults)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onNavigateToDetails: (movieId: Int) -> Unit // Callback de navegación a detalles de película
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()

    // Efecto para desplazar la lista a una categoría específica
    LaunchedEffect(Unit) {
        viewModel.scrollToItemEvent.collectLatest { categoryId ->
            val categoryIndex = uiState.categories.indexOfFirst { it.category.categoryId == categoryId }
            if (categoryIndex != -1) {
                var targetIndex = 0
                // Calcula el índice real en la LazyColumn, considerando encabezados y elementos expandidos
                for (i in 0 until categoryIndex) {
                    targetIndex++ // Por el encabezado de la categoría
                    if (uiState.categories[i].isExpanded) {
                        // Suma el número de filas de películas (cada fila tiene 3 películas)
                        targetIndex += uiState.categories[i].movies.chunked(3).size
                    }
                }
                lazyListState.animateScrollToItem(targetIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Movies Icon",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Películas",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Últ. act.: ${viewModel.formatTimestamp(uiState.lastUpdatedTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.refreshMoviesManually() },
                                enabled = !uiState.isRefreshing
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Actualizar películas",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.toggleSortMenu(true) }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Opciones de ordenación",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                },
                // Uso de TopAppBarDefaults.topAppBarColors que requiere @OptIn(ExperimentalMaterial3Api::class)
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                onClear = { viewModel.onSearchQueryChanged("") }
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    uiState.categories.forEach { expandableCategory ->
                        // Encabezado de categoría pegajoso
                        stickyHeader(key = expandableCategory.category.categoryId) {
                            Surface(modifier = Modifier.fillParentMaxWidth()) {
                                CategoryHeader(
                                    categoryName = expandableCategory.category.categoryName,
                                    isExpanded = expandableCategory.isExpanded,
                                    onHeaderClick = { viewModel.onCategoryToggled(expandableCategory.category.categoryId) },
                                    itemCount = expandableCategory.movies.size
                                )
                            }
                        }

                        if (expandableCategory.isExpanded) {
                            val movieRows = expandableCategory.movies.chunked(3) // Divide las películas en filas de 3
                            items(
                                items = movieRows,
                                key = { row -> row.joinToString { it.streamId.toString() } } // Clave única para cada fila
                            ) { rowMovies ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Espaciado entre películas
                                ) {
                                    rowMovies.forEach { movie ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            MoviePosterItem(
                                                movie = movie,
                                                onClick = { onNavigateToDetails(movie.streamId) } // Navega a los detalles
                                            )
                                        }
                                    }
                                    // Rellena los espacios si la última fila tiene menos de 3 películas
                                    repeat(3 - rowMovies.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de opciones de ordenación
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
    movie: Movie,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(2f / 3f) // Relación de aspecto de póster
            .clickable(onClick = onClick)
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(movie.streamIcon)
                    .crossfade(true)
                    .error(android.R.drawable.stat_notify_error) // Imagen de error si no carga
                    .build(),
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradiente para mejorar la legibilidad del título
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
            Text(
                text = movie.name,
                color = Color.White,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.BottomStart)
            )
        }
    }
}

// Diálogo de opciones de ordenación (reutilizado de ChannelsScreen pero con etiquetas personalizables)
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

// Extensión para obtener el nombre localizado de la opción de ordenación
@Composable
fun SortOrder.toLocalizedName(): String {
    return when (this) {
        SortOrder.DEFAULT -> "Por Defecto"
        SortOrder.AZ -> "Alfabético (A-Z)"
        SortOrder.ZA -> "Alfabético (Z-A)"
    }
}
