package com.example.tvmediaapp.ui.screens.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.MovieCategory
import com.example.tvmediaapp.data.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NewEpisodeAlert(
    val movie: Movie,
    val newCount: Int
)

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

    var currentPage by mutableIntStateOf(1)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var canLoadMore by mutableStateOf(true)
        private set

    fun loadNextPage() {
        if (isLoadingMore || !canLoadMore || _isLoading.value) return
        val nextPage = currentPage + 1
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val nextMovies = repository.fetchCatalogPage(
                    category = _selectedType.value,
                    genre = _selectedGenre.value,
                    sortBy = _selectedSort.value,
                    year = _selectedYear.value,
                    country = _selectedCountry.value,
                    page = nextPage
                )
                if (nextMovies.isEmpty()) {
                    canLoadMore = false
                } else {
                    currentPage = nextPage
                    _categories.update { currentCats ->
                        if (currentCats.isEmpty()) {
                            listOf(MovieCategory(id = _selectedType.value, title = "Каталог", movies = nextMovies))
                        } else {
                            val firstCat = currentCats.first()
                            val existingIds = firstCat.movies.map { it.id }.toSet()
                            val newUnique = nextMovies.filter { it.id !in existingIds }
                            if (newUnique.isEmpty()) {
                                canLoadMore = false
                                currentCats
                            } else {
                                val updatedMovies = firstCat.movies + newUnique
                                val updatedFirst = firstCat.copy(movies = updatedMovies)
                                listOf(updatedFirst) + currentCats.drop(1)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }

    private val _newEpisodeAlerts = MutableStateFlow<List<NewEpisodeAlert>>(emptyList())
    val newEpisodeAlerts: StateFlow<List<NewEpisodeAlert>> = _newEpisodeAlerts.asStateFlow()

    val newEpisodeAlert: StateFlow<NewEpisodeAlert?> = _newEpisodeAlerts
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun dismissCurrentEpisodeAlert() {
        _newEpisodeAlerts.update { if (it.isNotEmpty()) it.drop(1) else emptyList() }
    }

    fun dismissNewEpisodeAlert() {
        dismissCurrentEpisodeAlert()
    }

    fun dismissAllEpisodeAlerts() {
        _newEpisodeAlerts.value = emptyList()
    }

    private val prefs = application.getSharedPreferences("showhub_prefs", android.content.Context.MODE_PRIVATE)
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf("pref_excluded_countries", "pref_excluded_genres", "pref_only_with_poster", "pref_unreleased_movies", "pref_unreleased_series")) {
            loadCatalog()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadCatalog()
    }

    fun selectType(type: String) {
        lastFocusedIndex = 0
        _selectedType.value = type
        viewModelScope.launch { try { gridState.scrollToItem(0) } catch (_: Exception) {} }
        loadCatalog()
    }

    fun selectSort(sortBy: String) {
        lastFocusedIndex = 0
        _selectedSort.value = sortBy
        viewModelScope.launch { try { gridState.scrollToItem(0) } catch (_: Exception) {} }
        loadCatalog()
    }

    fun selectGenre(genre: String) {
        lastFocusedIndex = 0
        _selectedGenre.value = genre
        viewModelScope.launch { try { gridState.scrollToItem(0) } catch (_: Exception) {} }
        loadCatalog()
    }

    fun selectYear(year: String) {
        lastFocusedIndex = 0
        _selectedYear.value = year
        viewModelScope.launch { try { gridState.scrollToItem(0) } catch (_: Exception) {} }
        loadCatalog()
    }

    fun selectCountry(country: String) {
        lastFocusedIndex = 0
        _selectedCountry.value = country
        viewModelScope.launch { try { gridState.scrollToItem(0) } catch (_: Exception) {} }
        loadCatalog()
    }

    fun resetFilters() {
        lastFocusedIndex = 0
        _selectedType.value = "all"
        _selectedSort.value = "newest"
        _selectedGenre.value = "Все жанры"
        _selectedYear.value = "all"
        _selectedCountry.value = "all"
        viewModelScope.launch { try { gridState.scrollToItem(0) } catch (_: Exception) {} }
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
        return _categories.value.flatMap { it.movies }.distinctBy { it.id }
    }

    fun getFavoriteMovies(): List<Movie> {
        val favs = repository.getFavoriteMovies().toMutableList()
        val catFavs = _categories.value.flatMap { it.movies }.filter { isFavorite(it.id) }
        for (m in catFavs) {
            if (!favs.any { it.id == m.id }) favs.add(m)
        }
        return favs
    }

    fun refreshCatalog() {
        loadCatalog()
    }

    private var loadCatalogJob: kotlinx.coroutines.Job? = null
    private var prefetchJob: kotlinx.coroutines.Job? = null

    private fun loadCatalog() {
        loadCatalogJob?.cancel()
        loadCatalogJob = viewModelScope.launch {
            _isLoading.value = true
            currentPage = 1
            canLoadMore = true
            isLoadingMore = false
            val hasCustomFilters = _selectedType.value != "all" ||
                (_selectedGenre.value.isNotEmpty() && _selectedGenre.value != "Все жанры" && _selectedGenre.value != "all") ||
                _selectedSort.value != "newest" ||
                (_selectedYear.value.isNotEmpty() && _selectedYear.value != "all") ||
                (_selectedCountry.value.isNotEmpty() && _selectedCountry.value != "all")

            repository.getCatalog(
                category = _selectedType.value,
                genre = _selectedGenre.value,
                sortBy = _selectedSort.value,
                year = _selectedYear.value,
                country = _selectedCountry.value
            ).collect { data ->
                val processedData = if (!hasCustomFilters) {
                    applyHistoryRanking(data)
                } else {
                    data
                }
                _categories.value = processedData
                _isLoading.value = false
                prefetchVisibleMovieDetails(processedData)
            }
        }
    }

    fun refreshMovieFromCache(movieId: String) {
        val cached = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(movieId) ?: return
        _categories.value = _categories.value.map { cat ->
            cat.copy(movies = cat.movies.map { if (it.id == movieId) cached else it })
        }
    }

    private fun prefetchVisibleMovieDetails(data: List<MovieCategory>) {
        prefetchJob?.cancel()
        val favoriteSeries = getFavoriteMovies().filter { it.isSeries }
        val allMovies = (favoriteSeries + data.flatMap { it.movies }).distinctBy { it.id }
        if (allMovies.isEmpty()) return

        prefetchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val historyManager = WatchHistoryManager(getApplication())
            val targetSeriesIds = favoriteSeries.map { it.id }.toSet()
            val batchUpdates = mutableMapOf<String, Movie>()
            var lastBatchFlush = System.currentTimeMillis()

            for (movie in allMovies) {
                if (!isActive) break
                try {
                    val isTrackedSeries = movie.isSeries && (movie.id in targetSeriesIds || targetSeriesIds.any { id -> id.isNotBlank() && (movie.id.contains(id) || id.contains(movie.id)) })
                    val cached = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(movie.id, movie.title, movie.releaseYear)
                    val detailed = if (isTrackedSeries) {
                        val fetched = com.example.tvmediaapp.data.api.ShowHubApiClient.fetchMediaDetails(movie)
                        if (fetched.seasons.isNotEmpty() || fetched.audioTracks.isNotEmpty() || fetched.cast.isNotEmpty()) {
                            com.example.tvmediaapp.data.cache.MediaDiskCache.putCachedDetails(fetched)
                            fetched
                        } else {
                            cached ?: fetched
                        }
                    } else {
                        cached ?: movie
                    }

                    // Check if newly released episodes appeared for favorite series (strictly favorites only)
                    if (detailed.isSeries && isTrackedSeries) {
                        val seasonTotal = if (detailed.seasons.isNotEmpty()) detailed.seasons.sumOf { it.episodes.size } else 0
                        val trackMax = detailed.audioTracks.mapNotNull { it.seasonsEpisodes.values.maxOrNull() ?: it.episodesCount.takeIf { c -> c > 0 } }.maxOrNull() ?: 0
                        val totalEps = maxOf(seasonTotal, trackMax)
                        if (totalEps > 0) {
                            val newCount = historyManager.updateKnownTotalEpisodes(detailed.id, totalEps)
                            val alertCount = if (newCount > 0) newCount else historyManager.getNewEpisodesCount(detailed.id)
                            if (alertCount > 0 && !historyManager.isSeriesReminderMuted(detailed.id, detailed.title) && !historyManager.isSeriesReminderSnoozed(detailed.id, detailed.title)) {
                                com.example.tvmediaapp.data.notifications.EpisodeNotificationManager.notifyNewEpisodes(
                                    getApplication(),
                                    detailed,
                                    alertCount
                                )
                                val alertItem = NewEpisodeAlert(detailed, alertCount)
                                _newEpisodeAlerts.update { currentList ->
                                    if (currentList.none { it.movie.id == detailed.id }) {
                                        currentList + alertItem
                                    } else {
                                        currentList
                                    }
                                }
                            }
                        }
                    }

                    // Check if an episode airs TODAY
                    if (detailed.isSeries && detailed.episodesSchedule.isNotEmpty()) {
                        val todayItem = detailed.episodesSchedule.firstOrNull {
                            val s = (it.date + " " + it.status).lowercase()
                            s.contains("сегодня")
                        }
                        if (todayItem != null) {
                            com.example.tvmediaapp.data.notifications.EpisodeNotificationManager.notifyTodayEpisode(
                                getApplication(),
                                detailed,
                                todayItem.episode + if (todayItem.title.isNotBlank()) " — ${todayItem.title}" else ""
                            )
                        }
                    }

                    // Buffer enrichment
                    if (detailed.ratingKp > 0 || detailed.country.isNotBlank() || detailed.episodesInfo.isNotBlank() || detailed.ageRating.isNotBlank()) {
                        batchUpdates[detailed.id] = detailed
                    }

                    if (isTrackedSeries) {
                        kotlinx.coroutines.delay(200)
                    }
                } catch (_: Exception) {}
            }

            // Single final flush when prefetch completes
            if (batchUpdates.isNotEmpty()) {
                val toApply = batchUpdates.toMap()
                batchUpdates.clear()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _categories.value = _categories.value.map { cat ->
                        cat.copy(movies = cat.movies.map { m -> toApply[m.id] ?: m })
                    }
                }
            }
        }
    }

    fun hideMovie(movieId: String) {
        val hiddenManager = com.example.tvmediaapp.data.repository.HiddenMoviesManager(getApplication())
        hiddenManager.hideMovie(movieId)
        _categories.value = _categories.value.map { cat ->
            cat.copy(movies = cat.movies.filter { it.id != movieId })
        }
    }

    private fun applyHistoryRanking(data: List<MovieCategory>): List<MovieCategory> {
        // Strictly preserve server and Lampa popularity positions (#1, #2, #3, ...) intact!
        // Do not displace top Lampa items with history or genre re-sorting.
        return data
    }

    override fun onCleared() {
        super.onCleared()
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
    }
}
