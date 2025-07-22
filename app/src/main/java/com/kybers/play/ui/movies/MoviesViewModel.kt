package com.kybers.play.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.ui.player.SortOrder
import com.kybers.play.ui.player.toSortOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Representa una categoría de películas que se puede expandir.
 */
data class ExpandableMovieCategory(
    val category: Category,
    val movies: List<Movie> = emptyList(),
    val isExpanded: Boolean = false
)

/**
 * Representa el estado completo de la UI para la pantalla de películas.
 */
data class MoviesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val categories: List<ExpandableMovieCategory> = emptyList(),
    val lastUpdatedTimestamp: Long = 0L,
    val categorySortOrder: SortOrder = SortOrder.DEFAULT,
    val movieSortOrder: SortOrder = SortOrder.DEFAULT,
    val showSortMenu: Boolean = false
)

class MoviesViewModel(
    private val contentRepository: ContentRepository,
    private val syncManager: SyncManager,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    // --- ¡CAMBIO CLAVE! ---
    // Mantenemos listas separadas para las categorías "oficiales" y todas las películas.
    // Esto nos da más flexibilidad para procesar los datos.
    private val _officialCategories = MutableStateFlow<List<Category>>(emptyList())
    private val _allMovies = MutableStateFlow<List<Movie>>(emptyList())
    // Mantiene el estado de expansión de las categorías entre recargas.
    private val _expansionState = mutableMapOf<String, Boolean>()

    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()

    init {
        val savedCategorySortOrder = preferenceManager.getSortOrder("movie_category").toSortOrder()
        val savedMovieSortOrder = preferenceManager.getSortOrder("movie_item").toSortOrder()
        _uiState.update {
            it.copy(
                categorySortOrder = savedCategorySortOrder,
                movieSortOrder = savedMovieSortOrder
            )
        }
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val lastSyncTime = if (syncManager.isSyncNeeded(currentUser.id)) 0L else System.currentTimeMillis()

            // Cargamos las categorías y películas de forma concurrente
            val categoriesJob = launch {
                _officialCategories.value = contentRepository.getMovieCategories(currentUser.username, currentUser.password)
            }
            val moviesJob = launch {
                _allMovies.value = contentRepository.getAllMovies(currentUser.id).first()
            }
            categoriesJob.join()
            moviesJob.join()

            updateUiWithFilteredData()
            _uiState.update { it.copy(isLoading = false, lastUpdatedTimestamp = lastSyncTime) }
        }
    }

    fun refreshMoviesManually() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                contentRepository.cacheMovies(currentUser.username, currentUser.password, currentUser.id)
                syncManager.saveLastSyncTimestamp(currentUser.id)
                _allMovies.value = contentRepository.getAllMovies(currentUser.id).first()
                updateUiWithFilteredData()
                _uiState.update { it.copy(lastUpdatedTimestamp = System.currentTimeMillis()) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateUiWithFilteredData()
    }

    fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            val isCurrentlyExpanded = _expansionState[categoryId] ?: false
            val isNowExpanding = !isCurrentlyExpanded

            // Si estamos expandiendo una nueva categoría, colapsamos las demás.
            if (isNowExpanding) {
                _expansionState.keys.forEach { key ->
                    _expansionState[key] = false
                }
            }
            // Actualizamos el estado de la categoría actual.
            _expansionState[categoryId] = isNowExpanding

            updateUiWithFilteredData()

            if (isNowExpanding) {
                _scrollToItemEvent.emit(categoryId)
            }
        }
    }

    private fun updateUiWithFilteredData() {
        val filteredList = filterAndSort(
            _officialCategories.value,
            _allMovies.value,
            _uiState.value.searchQuery,
            _uiState.value.categorySortOrder,
            _uiState.value.movieSortOrder
        )
        _uiState.update { it.copy(categories = filteredList) }
    }

    // --- ¡NUEVA LÓGICA DE FILTRADO Y CLASIFICACIÓN! ---
    private fun filterAndSort(
        officialCategories: List<Category>,
        allMovies: List<Movie>,
        query: String,
        categorySortOrder: SortOrder,
        movieSortOrder: SortOrder
    ): List<ExpandableMovieCategory> {

        val lowercasedQuery = query.lowercase().trim()

        // 1. Filtrar películas según la búsqueda.
        val searchedMovies = if (lowercasedQuery.isBlank()) {
            allMovies
        } else {
            allMovies.filter { it.name.lowercase().contains(lowercasedQuery) }
        }

        // 2. Agrupar las películas por su categoryId.
        val moviesByCategory = searchedMovies.groupBy { it.categoryId }

        // 3. Crear un mapa para buscar nombres de categoría fácilmente.
        val categoryIdToNameMap = officialCategories.associateBy({ it.categoryId }, { it.categoryName })

        // 4. Construir las categorías expandibles dinámicamente.
        val dynamicallyBuiltCategories = moviesByCategory.mapNotNull { (categoryId, movies) ->
            // Usamos el nombre oficial si existe, si no, usamos el ID como nombre provisional.
            val categoryName = categoryIdToNameMap[categoryId] ?: categoryId
            val category = Category(categoryId = categoryId, categoryName = categoryName, parentId = 0)
            ExpandableMovieCategory(
                category = category,
                movies = movies,
                isExpanded = _expansionState[categoryId] ?: false // Restauramos el estado de expansión
            )
        }.toMutableList()

        // 5. Ordenar las películas dentro de cada categoría.
        val sortedMoviesInCategories = dynamicallyBuiltCategories.map { category ->
            val sortedMovies = when (movieSortOrder) {
                SortOrder.AZ -> category.movies.sortedBy { it.name }
                SortOrder.ZA -> category.movies.sortedByDescending { it.name }
                SortOrder.DEFAULT -> category.movies
            }
            category.copy(movies = sortedMovies)
        }

        // 6. Ordenar la lista final de categorías.
        return when (categorySortOrder) {
            SortOrder.AZ -> sortedMoviesInCategories.sortedBy { it.category.categoryName }
            SortOrder.ZA -> sortedMoviesInCategories.sortedByDescending { it.category.categoryName }
            SortOrder.DEFAULT -> {
                // Para el orden por defecto, intentamos mantener el orden del servidor.
                sortedMoviesInCategories.sortedBy { cat ->
                    officialCategories.indexOfFirst { it.categoryId == cat.category.categoryId }
                        .let { if (it == -1) Int.MAX_VALUE else it } // Las no oficiales van al final.
                }
            }
        }
    }

    fun toggleSortMenu(show: Boolean) {
        _uiState.update { it.copy(showSortMenu = show) }
    }

    fun setCategorySortOrder(order: SortOrder) {
        preferenceManager.saveSortOrder("movie_category", order.name)
        _uiState.update { it.copy(categorySortOrder = order) }
        updateUiWithFilteredData()
    }

    fun setMovieSortOrder(order: SortOrder) {
        preferenceManager.saveSortOrder("movie_item", order.name)
        _uiState.update { it.copy(movieSortOrder = order) }
        updateUiWithFilteredData()
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Nunca"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}