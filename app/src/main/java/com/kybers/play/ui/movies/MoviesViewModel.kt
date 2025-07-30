package com.kybers.play.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.player.SortOrder
import com.kybers.play.ui.player.toSortOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

data class ExpandableMovieCategory(
    val category: Category,
    val movies: List<Movie> = emptyList(),
    val isExpanded: Boolean = false
)

data class MoviesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val categories: List<ExpandableMovieCategory> = emptyList(),
    val lastUpdatedTimestamp: Long = 0L,
    val totalMovieCount: Int = 0,
    val categorySortOrder: SortOrder = SortOrder.DEFAULT,
    val movieSortOrder: SortOrder = SortOrder.DEFAULT,
    val showSortMenu: Boolean = false,
    val favoriteMovieIds: Set<String> = emptySet(),
    val enrichedPosters: Map<Int, String?> = emptyMap()
)

class MoviesViewModel(
    private val vodRepository: VodRepository,
    private val detailsRepository: DetailsRepository,
    private val syncManager: SyncManager,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User,
    private val parentalControlManager: ParentalControlManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private var allMovies: List<Movie> = emptyList()
    private var officialCategories: List<Category> = emptyList()
    private val expansionState = mutableMapOf<String, Boolean>()
    private var cachedDetailsMap: Map<Int, MovieDetailsCache> = emptyMap()

    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()

    init {
        val savedCategorySortOrder = preferenceManager.getSortOrder("movie_category").toSortOrder()
        val savedMovieSortOrder = preferenceManager.getSortOrder("movie_item").toSortOrder()
        val favoriteIds = preferenceManager.getFavoriteMovieIds()

        _uiState.update {
            it.copy(
                categorySortOrder = savedCategorySortOrder,
                movieSortOrder = savedMovieSortOrder,
                favoriteMovieIds = favoriteIds
            )
        }
        loadInitialData()
        
        // React to parental control changes
        viewModelScope.launch {
            parentalControlManager.blockedCategoriesState.collect { _ ->
                // Re-filter content when blocked categories change
                updateUiWithFilteredData()
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val lastSyncTime = syncManager.getLastSyncTimestamp(currentUser.id, SyncManager.ContentType.MOVIES)

            val moviesJob = async { allMovies = vodRepository.getAllMovies(currentUser.id).first() }
            val categoriesJob = async { officialCategories = vodRepository.getMovieCategories(currentUser.username, currentUser.password, currentUser.id) }
            val cacheJob = async { cachedDetailsMap = detailsRepository.getAllCachedMovieDetailsMap() }

            awaitAll(moviesJob, categoriesJob, cacheJob)

            updateUiWithFilteredData()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastUpdatedTimestamp = lastSyncTime,
                    totalMovieCount = allMovies.size
                )
            }
        }
    }

    fun getFinalPosterUrl(movie: Movie): String? {
        if (_uiState.value.enrichedPosters.containsKey(movie.streamId)) {
            return _uiState.value.enrichedPosters[movie.streamId]
        }
        val cachedPoster = cachedDetailsMap[movie.streamId]?.posterUrl
        if (!cachedPoster.isNullOrBlank()) {
            return cachedPoster
        }
        return movie.streamIcon
    }

    private fun enrichMoviePosters(movies: List<Movie>) {
        viewModelScope.launch {
            val moviesToEnrich = movies.filter { movie ->
                val finalUrl = getFinalPosterUrl(movie)
                finalUrl.isNullOrBlank() && movie.tmdbId != null
            }

            if (moviesToEnrich.isEmpty()) return@launch

            val newPosters = mutableMapOf<Int, String?>()
            moviesToEnrich.forEach { movie ->
                val details = detailsRepository.getMovieDetails(movie)
                newPosters[movie.streamId] = details.details?.posterUrl
            }

            _uiState.update {
                it.copy(enrichedPosters = it.enrichedPosters + newPosters)
            }
        }
    }

    fun refreshMoviesManually() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                vodRepository.cacheMovies(currentUser.username, currentUser.password, currentUser.id)
                syncManager.saveLastSyncTimestamp(currentUser.id, SyncManager.ContentType.MOVIES)

                val moviesJob = async { allMovies = vodRepository.getAllMovies(currentUser.id).first() }
                val cacheJob = async { cachedDetailsMap = detailsRepository.getAllCachedMovieDetailsMap() }
                awaitAll(moviesJob, cacheJob)

                val newTimestamp = syncManager.getLastSyncTimestamp(currentUser.id, SyncManager.ContentType.MOVIES)
                updateUiWithFilteredData()
                _uiState.update {
                    it.copy(
                        lastUpdatedTimestamp = newTimestamp,
                        totalMovieCount = allMovies.size
                    )
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun toggleFavoriteStatus(movieId: Int) {
        val currentFavorites = preferenceManager.getFavoriteMovieIds().toMutableSet()
        val movieStringId = movieId.toString()
        if (currentFavorites.contains(movieStringId)) {
            currentFavorites.remove(movieStringId)
        } else {
            currentFavorites.add(movieStringId)
        }
        preferenceManager.saveFavoriteMovieIds(currentFavorites)
        _uiState.update { it.copy(favoriteMovieIds = currentFavorites) }
        updateUiWithFilteredData()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateUiWithFilteredData()
    }

    fun onCategoryToggled(categoryId: String) {
        viewModelScope.launch {
            val isNowExpanding = !(expansionState[categoryId] ?: false)
            if (isNowExpanding) {
                expansionState.keys.forEach { expansionState[it] = false }
            }
            expansionState[categoryId] = isNowExpanding
            updateUiWithFilteredData()

            if (isNowExpanding) {
                _scrollToItemEvent.emit(categoryId)
                val category = _uiState.value.categories.find { it.category.categoryId == categoryId }
                category?.let { enrichMoviePosters(it.movies) }
            }
        }
    }

    private fun updateUiWithFilteredData() {
        val filteredList = filterAndSort(
            officialCategories,
            allMovies,
            _uiState.value.searchQuery,
            _uiState.value.categorySortOrder,
            _uiState.value.movieSortOrder
        )
        _uiState.update { it.copy(categories = filteredList) }
    }

    private fun filterAndSort(
        officialCategories: List<Category>,
        allMovies: List<Movie>,
        query: String,
        categorySortOrder: SortOrder,
        movieSortOrder: SortOrder
    ): List<ExpandableMovieCategory> {
        // --- ¡LÓGICA DE CONTROL PARENTAL APLICADA! ---
        // Use ParentalControlManager for filtering
        val categoriesToDisplay = parentalControlManager.filterCategories(officialCategories) { it.categoryId }
        // --- FIN DE LA LÓGICA ---

        val lowercasedQuery = query.lowercase().trim()

        // Apply parental control filtering to all movies first
        val parentalFilteredMovies = parentalControlManager.filterContentByCategory(allMovies) { it.categoryId }

        val moviesToDisplay = if (lowercasedQuery.isBlank()) parentalFilteredMovies else parentalFilteredMovies.filter {
            it.name.lowercase().contains(lowercasedQuery)
        }

        val specialCategories = mutableListOf<ExpandableMovieCategory>()
        val regularCategories = mutableListOf<ExpandableMovieCategory>()

        if (lowercasedQuery.isBlank()) {
            val favoriteIds = _uiState.value.favoriteMovieIds
            if (favoriteIds.isNotEmpty()) {
                val favoriteMovies = parentalFilteredMovies.filter { favoriteIds.contains(it.streamId.toString()) }
                if (favoriteMovies.isNotEmpty()) {
                    specialCategories.add(
                        ExpandableMovieCategory(
                            category = Category(categoryId = "favorites", categoryName = "Favoritos", parentId = 0),
                            movies = favoriteMovies,
                            isExpanded = expansionState.getOrPut("favorites") { true }
                        )
                    )
                }
            }

            val playbackPositions = preferenceManager.getAllPlaybackPositions()
            if (playbackPositions.isNotEmpty()) {
                val resumeMovies = parentalFilteredMovies.filter { movie ->
                    (playbackPositions[movie.streamId.toString()] ?: 0L) > 10000
                }.sortedByDescending { playbackPositions[it.streamId.toString()] }
                if (resumeMovies.isNotEmpty()) {
                    specialCategories.add(
                        ExpandableMovieCategory(
                            category = Category(categoryId = "continue_watching", categoryName = "Continuar Viendo", parentId = 0),
                            movies = resumeMovies,
                            isExpanded = expansionState.getOrPut("continue_watching") { true }
                        )
                    )
                }
            }
        }
        val moviesByCategoryId = moviesToDisplay.groupBy { it.categoryId.takeIf { !it.isNullOrBlank() } ?: "misc" }

        // Usamos la lista de categorías ya filtrada
        categoriesToDisplay.forEach { category ->
            moviesByCategoryId[category.categoryId]?.let {
                regularCategories.add(
                    ExpandableMovieCategory(
                        category = category,
                        movies = it,
                        isExpanded = expansionState[category.categoryId] ?: false
                    )
                )
            }
        }

        moviesByCategoryId["misc"]?.let {
            if (regularCategories.none { it.category.categoryId == "misc" }) {
                regularCategories.add(
                    ExpandableMovieCategory(
                        category = Category(categoryId = "misc", categoryName = "Varios", parentId = 0),
                        movies = it,
                        isExpanded = expansionState["misc"] ?: false
                    )
                )
            }
        }

        val sortedRegularCategories = when (categorySortOrder) {
            SortOrder.AZ -> regularCategories.sortedBy { it.category.categoryName }
            SortOrder.ZA -> regularCategories.sortedByDescending { it.category.categoryName }
            SortOrder.DEFAULT -> regularCategories
        }

        val allCategories = specialCategories + sortedRegularCategories

        return allCategories.map { expandableCategory ->
            val sortedMovies = when (movieSortOrder) {
                SortOrder.AZ -> expandableCategory.movies.sortedBy { it.name }
                SortOrder.ZA -> expandableCategory.movies.sortedByDescending { it.name }
                SortOrder.DEFAULT -> expandableCategory.movies
            }
            expandableCategory.copy(movies = sortedMovies)
        }
    }

    fun toggleSortMenu(show: Boolean) = _uiState.update { it.copy(showSortMenu = show) }
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
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
        return sdf.format(Date(timestamp))
    }
}
