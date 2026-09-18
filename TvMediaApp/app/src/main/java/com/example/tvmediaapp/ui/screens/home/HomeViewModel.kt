package com.example.tvmediaapp.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvmediaapp.data.history.WatchHistoryManager
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

    private val _selectedGenre = MutableStateFlow("Все жанры")
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
        _selectedGenre.value = "Все жанры"
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

    fun refreshCatalog() {
        loadCatalog()
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
                val processedData = if (_selectedType.value == "all" &&
                    (_selectedGenre.value.isEmpty() || _selectedGenre.value == "Все жанры") &&
                    _selectedSort.value == "newest"
                ) {
                    applyHistoryRanking(data)
                } else {
                    data
                }
                _categories.value = processedData
                _isLoading.value = false
            }
        }
    }

    private fun applyHistoryRanking(data: List<MovieCategory>): List<MovieCategory> {
        if (data.isEmpty()) return data
        val historyManager = WatchHistoryManager(getApplication())
        val history = historyManager.getHistory()
        if (history.isEmpty()) return data

        val unfinishedHistory = history.filter { it.percentage in 1..95 }
        val unfinishedMap = unfinishedHistory.associateBy { it.id }

        // Determine user's top watched genres from history
        val allMoviesMap = (data.flatMap { it.movies } + repository.sampleMovies).associateBy { it.id }
        val genreCounts = mutableMapOf<String, Int>()
        for (item in history) {
            val m = allMoviesMap[item.id]
            m?.genres?.forEach { g ->
                if (g.isNotBlank()) {
                    genreCounts[g] = (genreCounts[g] ?: 0) + 1
                }
            }
        }
        val topGenres = genreCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()

        return data.mapIndexed { catIdx, category ->
            if (catIdx == 0) {
                // First category ("Популярные новинки") gets smart ranking
                val existingIds = category.movies.map { it.id }.toSet()

                // Inject any unfinished items not currently in this category
                val missingUnfinishedMovies = unfinishedHistory
                    .filter { it.id !in existingIds }
                    .map { hist ->
                        allMoviesMap[hist.id] ?: Movie(
                            id = hist.id,
                            title = hist.title,
                            originalTitle = "",
                            description = "Продолжить с ${hist.percentage}% (сезон ${hist.season}, серия ${hist.episode})",
                            posterUrl = hist.posterUrl,
                            backdropUrl = hist.backdropUrl.ifEmpty { hist.posterUrl },
                            rating = 0.0,
                            ratingKp = 0.0,
                            ratingImdb = 0.0,
                            releaseYear = hist.releaseYear,
                            duration = "",
                            country = "",
                            director = "",
                            genres = emptyList(),
                            videoUrl = "",
                            isSeries = hist.isSeries
                        )
                    }

                val allCandidates = (missingUnfinishedMovies + category.movies).distinctBy { it.id }
                val totalCount = allCandidates.size

                val rankedMovies = allCandidates.sortedByDescending { movie ->
                    var score = (totalCount - allCandidates.indexOf(movie)).toLong()
                    val unfin = unfinishedMap[movie.id]
                    if (unfin != null) {
                        // Massive boost for unfinished items, weighted by recency
                        score += 100_000L + (unfin.timestamp / 1_000_000L)
                    } else if (history.any { it.id == movie.id }) {
                        // Finished or partially watched
                        score += 500L
                    }

                    if (repository.isFavorite(movie.id)) {
                        score += 300L
                    }

                    val genreMatchCount = movie.genres.count { it in topGenres }
                    score += (genreMatchCount * 150L)

                    score
                }

                category.copy(
                    movies = rankedMovies
                )
            } else {
                category
            }
        }
    }
}
