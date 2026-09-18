package com.example.tvmediaapp.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.MovieCategory
import com.example.tvmediaapp.data.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val repository: CatalogRepository = CatalogRepository(application.applicationContext)

    private val _categories = MutableStateFlow<List<MovieCategory>>(emptyList())
    val categories: StateFlow<List<MovieCategory>> = _categories.asStateFlow()

    private val _selectedType = MutableStateFlow("all")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedSort = MutableStateFlow("newest")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    private val _selectedGenre = MutableStateFlow("\u0412\u0441\u0435 \u0436\u0430\u043d\u0440\u044b")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _selectedYear = MutableStateFlow("all")
    val selectedYear: StateFlow<String> = _selectedYear.asStateFlow()

    private val _selectedCountry = MutableStateFlow("all")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    private val _favoriteChangeTrigger = MutableStateFlow(0)
    val favoriteChangeTrigger: StateFlow<Int> = _favoriteChangeTrigger.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(androidx.tv.foundation.ExperimentalTvFoundationApi::class)
    val gridState = androidx.tv.foundation.lazy.grid.TvLazyGridState()
    var lastFocusedIndex: Int = 0

    init {
        loadCatalog()
    }

    fun selectType(type: String) {
        lastFocusedIndex = 0
        _selectedType.value = type
        loadCatalog()
    }

    fun selectSort(sortBy: String) {
        lastFocusedIndex = 0
        _selectedSort.value = sortBy
        loadCatalog()
    }

    fun selectGenre(genre: String) {
        lastFocusedIndex = 0
        _selectedGenre.value = genre
        loadCatalog()
    }

    fun selectYear(year: String) {
        lastFocusedIndex = 0
        _selectedYear.value = year
        loadCatalog()
    }

    fun selectCountry(country: String) {
        lastFocusedIndex = 0
        _selectedCountry.value = country
        loadCatalog()
    }

    fun resetFilters() {
        lastFocusedIndex = 0
        _selectedType.value = "all"
        _selectedSort.value = "newest"
        _selectedGenre.value = "\u0412\u0441\u0435 \u0436\u0430\u043d\u0440\u044b"
        _selectedYear.value = "all"
        _selectedCountry.value = "all"
        loadCatalog()
    }

    fun isFavorite(movieId: String): Boolean {
        return repository.isFavorite(movieId)
    }

    fun toggleFavorite(movie: Movie): Boolean {
        val res = repository.toggleFavorite(movie)
        _favoriteChangeTrigger.value += 1
        return res
    }

    fun getAllMovies(): List<Movie> {
        val live = _categories.value.flatMap { it.movies }.distinctBy { it.id }
        return if (live.isNotEmpty()) live else repository.sampleMovies
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getCatalog(
                category = _selectedType.value,
                genre = _selectedGenre.value,
                sortBy = _selectedSort.value,
                year = _selectedYear.value,
                country = _selectedCountry.value
            ).collect { data ->
                _categories.value = data
                _isLoading.value = false
            }
        }
    }
}
