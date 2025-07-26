package com.kybers.play.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.repository.VodRepository
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

// Data class para representar una categoría de series que se puede expandir.
data class ExpandableSeriesCategory(
    val category: Category,
    val series: List<Series> = emptyList(),
    val isExpanded: Boolean = false
)

// --- ¡ESTADO DE LA UI ACTUALIZADO! ---
data class SeriesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val categories: List<ExpandableSeriesCategory> = emptyList(),
    val lastUpdatedTimestamp: Long = 0L,
    val totalSeriesCount: Int = 0,
    val categorySortOrder: SortOrder = SortOrder.DEFAULT,
    val seriesSortOrder: SortOrder = SortOrder.DEFAULT,
    val showSortMenu: Boolean = false,
    // --- ¡NUEVO! ---
    val favoriteSeriesIds: Set<String> = emptySet()
)

/**
 * --- ¡VIEWMODEL ACTUALIZADO! ---
 * ViewModel para la pantalla de Series.
 * Ahora gestiona la lógica de favoritos.
 */
class SeriesViewModel(
    private val vodRepository: VodRepository,
    private val syncManager: SyncManager,
    private val preferenceManager: PreferenceManager,
    private val currentUser: User
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState())
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    private var allSeries: List<Series> = emptyList()
    private var officialCategories: List<Category> = emptyList()
    private val expansionState = mutableMapOf<String, Boolean>()

    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()

    init {
        val savedCategorySortOrder = preferenceManager.getSortOrder("series_category").toSortOrder()
        val savedSeriesSortOrder = preferenceManager.getSortOrder("series_item").toSortOrder()
        // --- ¡NUEVO! Cargamos los favoritos al iniciar ---
        val favoriteIds = preferenceManager.getFavoriteSeriesIds()

        _uiState.update {
            it.copy(
                categorySortOrder = savedCategorySortOrder,
                seriesSortOrder = savedSeriesSortOrder,
                favoriteSeriesIds = favoriteIds
            )
        }
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val lastSyncTime = syncManager.getLastSyncTimestamp(currentUser.id)

            val seriesJob = async { allSeries = vodRepository.getAllSeries(currentUser.id).first() }
            val categoriesJob = async { officialCategories = vodRepository.getSeriesCategories(currentUser.username, currentUser.password) }
            awaitAll(seriesJob, categoriesJob)

            updateUiWithFilteredData()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastUpdatedTimestamp = lastSyncTime,
                    totalSeriesCount = allSeries.size
                )
            }
        }
    }

    fun refreshSeriesManually() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                vodRepository.cacheSeries(currentUser.username, currentUser.password, currentUser.id)
                syncManager.saveLastSyncTimestamp(currentUser.id)

                allSeries = vodRepository.getAllSeries(currentUser.id).first()
                val newTimestamp = syncManager.getLastSyncTimestamp(currentUser.id)
                updateUiWithFilteredData()
                _uiState.update {
                    it.copy(
                        lastUpdatedTimestamp = newTimestamp,
                        totalSeriesCount = allSeries.size
                    )
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // --- ¡NUEVA FUNCIÓN PARA GESTIONAR FAVORITOS! ---
    fun toggleFavoriteStatus(seriesId: Int) {
        val currentFavorites = preferenceManager.getFavoriteSeriesIds().toMutableSet()
        val seriesStringId = seriesId.toString()
        if (currentFavorites.contains(seriesStringId)) {
            currentFavorites.remove(seriesStringId)
        } else {
            currentFavorites.add(seriesStringId)
        }
        preferenceManager.saveFavoriteSeriesIds(currentFavorites)
        _uiState.update { it.copy(favoriteSeriesIds = currentFavorites) }
        // Actualizamos la UI para que refleje el cambio inmediatamente
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
                // Si es una categoría normal, contraemos las demás (incluida favoritos)
                if (categoryId != "favorites") {
                    expansionState.keys.forEach { expansionState[it] = false }
                }
            }
            expansionState[categoryId] = isNowExpanding
            updateUiWithFilteredData()
            if (isNowExpanding) _scrollToItemEvent.emit(categoryId)
        }
    }

    private fun updateUiWithFilteredData() {
        val filteredList = filterAndSort(
            officialCategories,
            allSeries,
            _uiState.value.searchQuery,
            _uiState.value.categorySortOrder,
            _uiState.value.seriesSortOrder
        )
        _uiState.update { it.copy(categories = filteredList) }
    }

    // --- ¡LÓGICA DE FILTRADO Y ORDENACIÓN ACTUALIZADA! ---
    private fun filterAndSort(
        officialCategories: List<Category>,
        allSeries: List<Series>,
        query: String,
        categorySortOrder: SortOrder,
        seriesSortOrder: SortOrder
    ): List<ExpandableSeriesCategory> {
        val lowercasedQuery = query.lowercase().trim()

        val seriesToDisplay = if (lowercasedQuery.isBlank()) allSeries else allSeries.filter {
            it.name.lowercase().contains(lowercasedQuery)
        }

        val specialCategories = mutableListOf<ExpandableSeriesCategory>()
        val regularCategories = mutableListOf<ExpandableSeriesCategory>()

        // 1. Crear categoría de Favoritos si aplica
        if (lowercasedQuery.isBlank()) {
            val favoriteIds = _uiState.value.favoriteSeriesIds
            if (favoriteIds.isNotEmpty()) {
                val favoriteSeries = allSeries.filter { favoriteIds.contains(it.seriesId.toString()) }
                if (favoriteSeries.isNotEmpty()) {
                    specialCategories.add(
                        ExpandableSeriesCategory(
                            category = Category(categoryId = "favorites", categoryName = "Favoritos", parentId = 0),
                            series = favoriteSeries,
                            isExpanded = expansionState.getOrPut("favorites") { true }
                        )
                    )
                }
            }
        }

        // 2. Agrupar series por su ID de categoría
        val seriesByCategoryId = seriesToDisplay.groupBy { it.categoryId }

        // 3. Crear la lista de categorías regulares
        val categoriesWithContent = officialCategories.mapNotNull { category ->
            seriesByCategoryId[category.categoryId]?.let { seriesInCategory ->
                ExpandableSeriesCategory(
                    category = category,
                    series = seriesInCategory,
                    isExpanded = expansionState[category.categoryId] ?: false
                )
            }
        }

        // 4. Ordenar las categorías regulares
        val sortedRegularCategories = when (categorySortOrder) {
            SortOrder.AZ -> categoriesWithContent.sortedBy { it.category.categoryName }
            SortOrder.ZA -> categoriesWithContent.sortedByDescending { it.category.categoryName }
            SortOrder.DEFAULT -> categoriesWithContent
        }

        // 5. Unir las listas y ordenar las series dentro de cada categoría
        val allCategories = specialCategories + sortedRegularCategories
        return allCategories.map { expandableCategory ->
            val sortedSeries = when (seriesSortOrder) {
                SortOrder.AZ -> expandableCategory.series.sortedBy { it.name }
                SortOrder.ZA -> expandableCategory.series.sortedByDescending { it.name }
                SortOrder.DEFAULT -> expandableCategory.series
            }
            expandableCategory.copy(series = sortedSeries)
        }
    }

    fun toggleSortMenu(show: Boolean) = _uiState.update { it.copy(showSortMenu = show) }
    fun setCategorySortOrder(order: SortOrder) {
        preferenceManager.saveSortOrder("series_category", order.name)
        _uiState.update { it.copy(categorySortOrder = order) }
        updateUiWithFilteredData()
    }
    fun setSeriesSortOrder(order: SortOrder) {
        preferenceManager.saveSortOrder("series_item", order.name)
        _uiState.update { it.copy(seriesSortOrder = order) }
        updateUiWithFilteredData()
    }
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Nunca"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
