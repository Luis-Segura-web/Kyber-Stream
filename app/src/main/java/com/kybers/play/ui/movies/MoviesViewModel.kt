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

    private val _originalCategories = MutableStateFlow<List<ExpandableMovieCategory>>(emptyList())
    private val _allMovies = MutableStateFlow<List<Movie>>(emptyList())

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

            val categories = contentRepository.getMovieCategories(currentUser.username, currentUser.password)
            _originalCategories.value = categories.map { ExpandableMovieCategory(category = it) }
            _allMovies.value = contentRepository.getAllMovies(currentUser.id).first()

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
            val currentOriginals = _originalCategories.value
            val category = currentOriginals.find { it.category.categoryId == categoryId } ?: return@launch
            val isNowExpanding = !category.isExpanded

            val updatedOriginals = currentOriginals.map {
                when {
                    it.category.categoryId == categoryId -> it.copy(isExpanded = isNowExpanding)
                    isNowExpanding -> it.copy(isExpanded = false)
                    else -> it
                }
            }
            _originalCategories.value = updatedOriginals

            updateUiWithFilteredData()
            if(isNowExpanding) {
                _scrollToItemEvent.emit(categoryId)
            }
        }
    }

    private fun updateUiWithFilteredData() {
        val filteredList = filterAndSort(
            _originalCategories.value,
            _allMovies.value,
            _uiState.value.searchQuery,
            _uiState.value.categorySortOrder,
            _uiState.value.movieSortOrder
        )
        _uiState.update { it.copy(categories = filteredList) }
    }

    // --- ¡LÓGICA CLAVE MEJORADA! ---
    private fun filterAndSort(
        categories: List<ExpandableMovieCategory>,
        allMovies: List<Movie>,
        query: String,
        categorySortOrder: SortOrder,
        movieSortOrder: SortOrder
    ): List<ExpandableMovieCategory> {
        val lowercasedQuery = query.lowercase()
        val officialCategoryIds = categories.map { it.category.categoryId }.toSet()

        // 1. Filtra todas las películas primero según la búsqueda.
        val searchedMovies = if (query.isBlank()) {
            allMovies
        } else {
            allMovies.filter { it.name.lowercase().contains(lowercasedQuery) }
        }

        // 2. Agrupa las películas filtradas por su ID de categoría.
        val moviesByCat = searchedMovies.groupBy { it.categoryId }

        // 3. Crea la lista de categorías oficiales, asignando las películas filtradas y ordenadas.
        var finalCategories = categories.map { expandableCategory ->
            val moviesForThisCategory = moviesByCat[expandableCategory.category.categoryId] ?: emptyList()
            val sortedMovies = when (movieSortOrder) {
                SortOrder.AZ -> moviesForThisCategory.sortedBy { it.name }
                SortOrder.ZA -> moviesForThisCategory.sortedByDescending { it.name }
                SortOrder.DEFAULT -> moviesForThisCategory
            }
            expandableCategory.copy(movies = sortedMovies)
        }

        // 4. Busca películas "huérfanas" (sin categoría o con una ID no oficial).
        val orphanMovies = searchedMovies.filter { it.categoryId.isBlank() || !officialCategoryIds.contains(it.categoryId) }

        // 5. Si hay películas huérfanas, crea una categoría "Varios" para ellas.
        if (orphanMovies.isNotEmpty()) {
            val sortedOrphans = when (movieSortOrder) {
                SortOrder.AZ -> orphanMovies.sortedBy { it.name }
                SortOrder.ZA -> orphanMovies.sortedByDescending { it.name }
                SortOrder.DEFAULT -> orphanMovies
            }
            val miscCategory = ExpandableMovieCategory(
                category = Category(categoryId = "misc", categoryName = "Varios", parentId = 0),
                movies = sortedOrphans,
                // Mantenemos el estado de expansión si ya existía.
                isExpanded = categories.find { it.category.categoryId == "misc" }?.isExpanded ?: false
            )
            finalCategories = finalCategories + miscCategory
        }

        // 6. Si hay una búsqueda, filtra las categorías que se quedaron vacías.
        if (query.isNotBlank()) {
            finalCategories = finalCategories.filter { it.movies.isNotEmpty() }
        }

        // 7. Finalmente, ordena la lista de categorías.
        return when (categorySortOrder) {
            SortOrder.AZ -> finalCategories.sortedBy { it.category.categoryName }
            SortOrder.ZA -> finalCategories.sortedByDescending { it.category.categoryName }
            SortOrder.DEFAULT -> finalCategories
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
