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

    private val _selectedTab = MutableStateFlow("all")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _selectedGenre = MutableStateFlow("\u0412\u0441\u0435 \u0436\u0430\u043d\u0440\u044b")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _selectedSort = MutableStateFlow("newest")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    private val _favoriteChangeTrigger = MutableStateFlow(0)
    val favoriteChangeTrigger: StateFlow<Int> = _favoriteChangeTrigger.asStateFlow()

    init {
        loadCatalog()
    }

    fun selectTab(tabId: String) {
        _selectedTab.value = tabId
        loadCatalog()
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        loadCatalog()
    }

    fun selectSort(sortBy: String) {
        _selectedSort.value = sortBy
        loadCatalog()
    }

    fun isFavorite(movieId: String): Boolean {
        return repository.isFavorite(movieId)
    }

    fun toggleFavorite(movie: Movie): Boolean {
        val res = repository.toggleFavorite(movie)
        _favoriteChangeTrigger.value += 1
        if (_selectedTab.value == "favorites") {
            loadCatalog()
        }
        return res
    }

    fun getAllMovies(): List<Movie> {
        return repository.sampleMovies
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            repository.getCatalog(
                category = _selectedTab.value,
                genre = _selectedGenre.value,
                sortBy = _selectedSort.value
            ).collect { data ->
                _categories.value = data
            }
        }
    }
}
