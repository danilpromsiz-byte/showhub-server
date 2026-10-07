package com.example.tvmediaapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.models.AudioTrackInfo
import com.example.tvmediaapp.data.models.EpisodeInfo
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.MovieCategory
import com.example.tvmediaapp.data.models.SeasonInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CatalogRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE)
    private val hiddenMoviesManager: HiddenMoviesManager? = context?.let { HiddenMoviesManager(it) }
    private val memoryFavorites = mutableSetOf<String>()

    init {
        prefs?.getStringSet("favorite_ids", emptySet())?.let {
            memoryFavorites.addAll(it)
        }
    }

    fun isFavorite(movieId: String): Boolean {
        return memoryFavorites.contains(movieId)
    }

    fun addFavorite(movieId: String) {
        if (movieId.isBlank()) return
        if (!memoryFavorites.contains(movieId)) {
            memoryFavorites.add(movieId)
            prefs?.edit()?.putStringSet("favorite_ids", memoryFavorites)?.apply()
        }
    }

    fun getFavoriteMovies(): List<Movie> {
        val list = mutableListOf<Movie>()
        val raw = prefs?.getString("favorite_movies_json", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                if (memoryFavorites.contains(id)) {
                    val cached = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(id)
                    list.add(cached ?: Movie(
                        id = id,
                        title = obj.optString("title", "Медиа"),
                        originalTitle = obj.optString("originalTitle", ""),
                        description = obj.optString("description", ""),
                        posterUrl = obj.optString("posterUrl", ""),
                        backdropUrl = obj.optString("backdropUrl", ""),
                        rating = obj.optDouble("rating", 0.0),
                        ratingKp = obj.optDouble("ratingKp", 0.0),
                        ratingImdb = obj.optDouble("ratingImdb", 0.0),
                        releaseYear = obj.optString("releaseYear", ""),
                        duration = obj.optString("duration", ""),
                        genres = emptyList(),
                        isSeries = obj.optBoolean("isSeries", false)
                    ))
                }
            }
        } catch (_: Exception) {}

        val loadedIds = list.map { it.id }.toSet()
        for (id in memoryFavorites) {
            if (!loadedIds.contains(id)) {
                val cached = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(id)
                if (cached != null) {
                    list.add(cached)
                }
            }
        }
        return list
    }

    fun toggleFavorite(movie: Movie): Boolean {
        val newStatus = if (memoryFavorites.contains(movie.id)) {
            memoryFavorites.remove(movie.id)
            false
        } else {
            memoryFavorites.add(movie.id)
            com.example.tvmediaapp.data.cache.MediaDiskCache.putCachedDetails(movie)
            true
        }
        prefs?.edit()?.putStringSet("favorite_ids", memoryFavorites)?.apply()
        saveFavoriteMoviesJson(movie, newStatus)
        return newStatus
    }

    private fun saveFavoriteMoviesJson(movie: Movie, isAdded: Boolean) {
        try {
            val raw = prefs?.getString("favorite_movies_json", "[]") ?: "[]"
            val arr = try { org.json.JSONArray(raw) } catch (_: Exception) { org.json.JSONArray() }
            val newArr = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                if (id != movie.id && memoryFavorites.contains(id)) {
                    newArr.put(obj)
                }
            }
            if (isAdded) {
                val obj = org.json.JSONObject().apply {
                    put("id", movie.id)
                    put("title", movie.title)
                    put("originalTitle", movie.originalTitle)
                    put("description", movie.description)
                    put("posterUrl", movie.posterUrl)
                    put("backdropUrl", movie.backdropUrl)
                    put("rating", movie.rating)
                    put("ratingKp", movie.ratingKp)
                    put("ratingImdb", movie.ratingImdb)
                    put("releaseYear", movie.releaseYear)
                    put("isSeries", movie.isSeries)
                }
                newArr.put(obj)
            }
            prefs?.edit()?.putString("favorite_movies_json", newArr.toString())?.apply()
        } catch (_: Exception) {}
    }

    val sampleMovies: List<Movie> = emptyList()

    private fun matchesCountry(movie: Movie, filterCountry: String): Boolean {
        if (filterCountry.isBlank() || filterCountry == "all" || filterCountry == "Все страны") return true
        val fLow = filterCountry.lowercase().trim()
        val mCountry = movie.country.lowercase().trim()
        val mDesc = movie.description.lowercase()
        val mTitle = movie.title.lowercase()
        val mGenres = movie.genres.map { it.lowercase() }

        val aliases = when {
            fLow.contains("коре") -> listOf("коре", "южная корея", "республика корея", "korea", "дорам")
            fLow.contains("сша") || fLow.contains("usa") -> listOf("сша", "usa", "америк", "соединенные штаты")
            fLow.contains("росси") -> listOf("росси", "рф", "russia")
            fLow.contains("великобрит") || fLow.contains("англи") -> listOf("великобрит", "англи", "uk", "британ")
            fLow.contains("япон") -> listOf("япон", "japan", "аниме")
            fLow.contains("турц") -> listOf("турц", "turkey", "турец")
            fLow.contains("кита") -> listOf("кита", "china", "донгхуа")
            fLow.contains("инди") -> listOf("инди", "india", "болливуд")
            fLow.contains("франц") -> listOf("франц", "france")
            fLow.contains("герман") -> listOf("герман", "germany", "немец")
            fLow.contains("италь") || fLow.contains("итали") -> listOf("италь", "итали", "italy")
            fLow.contains("испан") -> listOf("испан", "spain")
            fLow.contains("ссср") -> listOf("ссср", "советск", "ussr")
            fLow.contains("канад") -> listOf("канад", "canada")
            fLow.contains("австрал") -> listOf("австрал", "australia")
            fLow.contains("таиланд") || fLow.contains("тайланд") -> listOf("таиланд", "тайланд", "thailand", "лакорн")
            fLow.contains("швеци") -> listOf("швеци", "sweden")
            else -> listOf(fLow)
        }

        return aliases.any { alias ->
            mCountry.contains(alias) ||
            (mCountry.isBlank() && (mDesc.contains(alias) || mTitle.contains(alias) || mGenres.any { it.contains(alias) }))
        }
    }

    private fun matchesGenre(movie: Movie, filterGenre: String): Boolean {
        if (filterGenre.isBlank() || filterGenre == "all" || filterGenre == "Все жанры") return true
        val fLow = filterGenre.lowercase().trim()
        val stem = fLow
            .removeSuffix("ия").removeSuffix("ии").removeSuffix("ые").removeSuffix("ий")
            .removeSuffix("ка").removeSuffix("ки").removeSuffix("а").removeSuffix("ы")
            .removeSuffix("и").removeSuffix("я")
        val inGenres = movie.genres.any { g ->
            val gLow = g.lowercase().trim()
            gLow.contains(stem) || stem.contains(gLow) || gLow.contains(fLow) || fLow.contains(gLow)
        }
        if (inGenres) return true
        val descLow = movie.description.lowercase()
        return descLow.contains(stem) || descLow.contains(fLow)
    }

    private fun filterAndSort(
        list: List<Movie>,
        category: String,
        genre: String?,
        sortBy: String,
        year: String?,
        country: String?
    ): List<Movie> {
        var res = list

        // Category filter
        res = when (category) {
            "movies" -> res.filter { !it.isSeries }
            "series" -> res.filter { it.isSeries }
            "cartoons" -> res.filter { it.genres.any { g -> g.contains("Мульт", ignoreCase = true) } }
            "anime" -> res.filter { it.genres.any { g -> g.contains("Аниме", ignoreCase = true) } }
            "favorites" -> res.filter { isFavorite(it.id) }
            else -> res
        }

        // Genre filter with stem matching
        if (!genre.isNullOrEmpty() && genre != "all" && genre != "Все жанры") {
            res = res.filter { m -> matchesGenre(m, genre) }
        }

        // Year filter
        if (!year.isNullOrEmpty() && year != "all" && year != "Все годы") {
            res = when (year) {
                "2020-2022" -> res.filter { it.releaseYear in listOf("2020", "2021", "2022") }
                "2010s" -> res.filter { (it.releaseYear.toIntOrNull() ?: 0) in 2010..2019 }
                "before_2000" -> res.filter { (it.releaseYear.toIntOrNull() ?: 0) < 2000 }
                else -> res.filter { it.releaseYear == year }
            }
        }

        // Country filter
        if (!country.isNullOrEmpty() && country != "all" && country != "Все страны") {
            res = res.filter { matchesCountry(it, country) }
        }

        // Deduplicate & merge movies across different sources into a single card
        res = com.example.tvmediaapp.data.models.deduplicateAndMergeMovies(res)

        // Sorting
        return when (sortBy) {
            "rating" -> res.sortedByDescending { it.rating }
            "popular" -> res.sortedByDescending { it.lampaPopularity.takeIf { p -> p > 0.0 } ?: it.ratingLampa ?: it.ratingKp ?: it.rating }
            "year" -> res.sortedByDescending { it.releaseYear }
            else -> {
                if (category == "all" && (genre.isNullOrEmpty() || genre == "all" || genre == "Все жанры")) res
                else res.sortedByDescending { it.releaseYear }
            }
        }
    }

    private fun hasValidPoster(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return !lower.contains("no_image") &&
               !lower.contains("noposter") &&
               !lower.contains("kinopoiskapiunofficial.tech") &&
               !lower.contains("st.kp.yandex.net")
    }

    private fun hasValidCover(m: Movie): Boolean {
        return hasValidPoster(m.posterUrl) || hasValidPoster(m.backdropUrl)
    }

    private fun hasReadableTitle(title: String): Boolean {
        if (title.isBlank()) return false
        val hasCyrillicOrLatin = title.any { c ->
            (c in 'a'..'z') || (c in 'A'..'Z') || (c in '\u0400'..'\u04FF')
        }
        val hasUntranslatedScript = title.any { c ->
            val code = c.code
            (code in 0x4e00..0x9fff) || (code in 0x3400..0x4dbf) ||
            (code in 0xac00..0xd7af) || (code in 0x1100..0x11ff) ||
            (code in 0x3040..0x309f) || (code in 0x30a0..0x30ff) ||
            (code in 0x0900..0x097f) || (code in 0x0d00..0x0d7f) ||
            (code in 0x0b80..0x0bff) || (code in 0x0c00..0x0c7f) ||
            (code in 0x0e00..0x0e7f) || (code in 0x0600..0x06ff)
        }
        return hasCyrillicOrLatin && !hasUntranslatedScript
    }

    suspend fun fetchCatalogPage(
        category: String = "all",
        genre: String? = null,
        sortBy: String = "newest",
        year: String? = null,
        country: String? = null,
        page: Int
    ): List<Movie> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val excludedCountriesStr = prefs?.getString("pref_excluded_countries", "") ?: ""
        val excludedGenresStr = prefs?.getString("pref_excluded_genres", "") ?: ""
        val onlyWithPoster = prefs?.getBoolean("pref_only_with_poster", true) ?: true
        val includeUnreleasedMovies = prefs?.getBoolean("pref_unreleased_movies", false) ?: false
        val includeUnreleasedSeries = prefs?.getBoolean("pref_unreleased_series", true) ?: true

        val raw = ShowHubApiClient.fetchCatalog(
            category = category,
            genre = genre,
            sortBy = sortBy,
            year = year,
            country = country,
            page = page,
            limit = 60,
            excludedCountries = if (excludedCountriesStr.isNotBlank()) excludedCountriesStr else null,
            excludedGenres = if (excludedGenresStr.isNotBlank()) excludedGenresStr else null,
            includeUnreleasedMovies = includeUnreleasedMovies,
            includeUnreleasedSeries = includeUnreleasedSeries
        )
        if (raw.isEmpty()) return@withContext emptyList()

        val hiddenIds = hiddenMoviesManager?.getHiddenIds() ?: emptySet()
        var effective = raw.filter { hasReadableTitle(it.title) }
        if (hiddenIds.isNotEmpty()) {
            effective = effective.filter { it.id !in hiddenIds }
        }
        if (onlyWithPoster) {
            effective = effective.filter { hasValidCover(it) }
        }
        if (excludedCountriesStr.isNotBlank()) {
            val exList = excludedCountriesStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (exList.isNotEmpty()) {
                effective = effective.filter { m ->
                    val cLow = m.country.lowercase()
                    exList.none { ex -> cLow.contains(ex) }
                }
            }
        }
        if (excludedGenresStr.isNotBlank()) {
            val exGenList = excludedGenresStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (exGenList.isNotEmpty()) {
                effective = effective.filter { m ->
                    val gLow = m.genres.joinToString(" ").lowercase()
                    exGenList.none { ex -> gLow.contains(ex) }
                }
            }
        }
        effective
    }

    fun getCatalog(
        category: String = "all",
        genre: String? = null,
        sortBy: String = "newest",
        year: String? = null,
        country: String? = null
    ): Flow<List<MovieCategory>> = flow {
        if (category == "favorites") {
            val base = try { ShowHubApiClient.fetchCatalog() } catch (e: Exception) { emptyList() }
            val all = base
            val filtered = filterAndSort(all, category, genre, sortBy, year, country)
            emit(listOf(MovieCategory(id = "favorites", title = "Избранное", movies = filtered)))
            return@flow
        }

        val excludedCountriesStr = prefs?.getString("pref_excluded_countries", "") ?: ""
        val excludedGenresStr = prefs?.getString("pref_excluded_genres", "") ?: ""
        val onlyWithPoster = prefs?.getBoolean("pref_only_with_poster", true) ?: true

        fun buildCategories(movies: List<Movie>): List<MovieCategory> {
            var effective = com.example.tvmediaapp.data.models.deduplicateAndMergeMovies(movies)
            val hiddenIds = hiddenMoviesManager?.getHiddenIds() ?: emptySet()
            if (hiddenIds.isNotEmpty()) {
                effective = effective.filter { it.id !in hiddenIds }
            }
            effective = effective.filter { hasReadableTitle(it.title) }
            if (onlyWithPoster) {
                effective = effective.filter { m -> hasValidCover(m) }
            }
            if (excludedCountriesStr.isNotBlank()) {
                val exList = excludedCountriesStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (exList.isNotEmpty()) {
                    effective = effective.filter { m ->
                        val cLow = m.country.lowercase()
                        exList.none { ex -> cLow.contains(ex) }
                    }
                }
            }
            if (excludedGenresStr.isNotBlank()) {
                val exGenList = excludedGenresStr.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (exGenList.isNotEmpty()) {
                    effective = effective.filter { m ->
                        val gLow = m.genres.joinToString(" ").lowercase()
                        exGenList.none { ex -> gLow.contains(ex) }
                    }
                }
            }
            // Immediately enrich movies with cached details from disk, always keeping fresh CDN poster/backdrop
            effective = effective.map { m ->
                val cached = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(m.id, m.title, m.releaseYear)
                if (cached != null) {
                    val bestPoster = if (m.posterUrl.isNotBlank() && m.posterUrl.startsWith("http") && !m.posterUrl.contains("/covers/")) m.posterUrl else cached.posterUrl
                    val bestBackdrop = if (m.backdropUrl.isNotBlank() && m.backdropUrl.startsWith("http") && !m.backdropUrl.contains("/covers/")) m.backdropUrl else cached.backdropUrl
                    cached.copy(posterUrl = bestPoster, backdropUrl = bestBackdrop)
                } else {
                    m
                }
            }
            if (onlyWithPoster) {
                effective = effective.filter { m -> hasValidCover(m) }
            }

            val includeUnreleasedMovies = prefs?.getBoolean("pref_unreleased_movies", false) ?: false
            val includeUnreleasedSeries = prefs?.getBoolean("pref_unreleased_series", true) ?: true
            val currentYear = maxOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1, 2026)

            if (!includeUnreleasedMovies) {
                effective = effective.filter { m ->
                    if (m.isSeries) true
                    else {
                        val yr = m.releaseYear.filter { it.isDigit() }.toIntOrNull()
                        yr == null || yr <= currentYear
                    }
                }
            }
            if (!includeUnreleasedSeries) {
                effective = effective.filter { m ->
                    if (!m.isSeries) true
                    else {
                        val yr = m.releaseYear.filter { it.isDigit() }.toIntOrNull()
                        yr == null || yr <= currentYear
                    }
                }
            }

            effective = filterAndSort(effective, category, genre, sortBy, year, country)
            if (effective.isEmpty()) return emptyList()

            val isPureDefault = category == "all" &&
                (genre.isNullOrEmpty() || genre == "Все жанры") &&
                (country.isNullOrEmpty() || country == "all") &&
                (year.isNullOrEmpty() || year == "all")

            return if (isPureDefault) {
                // Main screen novelty rows are strictly focused on fresh releases (2024-2026)
                val freshMovies = effective.filter { m ->
                    val yr = m.releaseYear.filter { it.isDigit() }.toIntOrNull()
                    yr == null || yr >= 2024
                }
                val rankedPopular = freshMovies.mapIndexed { idx, m ->
                    m.copy(rankIndex = idx + 1)
                }
                listOf(
                    MovieCategory(id = "popular", title = "Популярные новинки", movies = rankedPopular),
                    MovieCategory(id = "top_rated", title = "Топ рейтинга", movies = freshMovies.sortedByDescending { it.rating }.map { it.copy(rankIndex = 0) }),
                    MovieCategory(id = "series", title = "Сериалы", movies = freshMovies.filter { it.isSeries }.map { it.copy(rankIndex = 0) }),
                    MovieCategory(id = "movies", title = "Фильмы", movies = freshMovies.filter { !it.isSeries }.map { it.copy(rankIndex = 0) })
                )
            } else {
                val rankedEffective = if (sortBy == "popular") {
                    effective.mapIndexed { idx, m -> m.copy(rankIndex = idx + 1) }
                } else {
                    effective.map { it.copy(rankIndex = 0) }
                }
                val titleParts = mutableListOf<String>()
                if (!country.isNullOrEmpty() && country != "all") {
                    titleParts.add(if (country.contains("Коре", ignoreCase = true)) "Южная Корея" else country)
                }
                if (!genre.isNullOrEmpty() && genre != "all" && genre != "Все жанры") {
                    titleParts.add(genre)
                }
                if (!year.isNullOrEmpty() && year != "all") {
                    titleParts.add(year)
                }
                val catName = when (category) {
                    "movies" -> "Фильмы"
                    "series" -> "Сериалы"
                    "cartoons" -> "Мультфильмы"
                    "anime" -> "Аниме"
                    else -> if (titleParts.isEmpty()) "Каталог" else ""
                }
                if (catName.isNotEmpty()) titleParts.add(0, catName)

                val displayTitle = if (titleParts.isNotEmpty()) titleParts.joinToString(" • ") else "Каталог"
                listOf(MovieCategory(id = category, title = displayTitle, movies = rankedEffective))
            }
        }

        val isDefaultMainCatalog = category == "all" &&
            (genre.isNullOrEmpty() || genre == "Все жанры" || genre == "all") &&
            (country.isNullOrEmpty() || country == "all") &&
            (year.isNullOrEmpty() || year == "all")

        if (isDefaultMainCatalog) {
            // STEP 1: Instant 0 ms emit from local TV disk cache
            val cachedHome = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedHomeCatalog()
            if (!cachedHome.isNullOrEmpty()) {
                emit(cachedHome)
            }

            // STEP 2: Fetch 3-hour pre-compiled server home catalog (<15ms)
            try {
                val liveHome = ShowHubApiClient.fetchHomeCatalog()
                if (liveHome.isNotEmpty()) {
                    com.example.tvmediaapp.data.cache.MediaDiskCache.putCachedHomeCatalog(liveHome)
                    emit(liveHome)
                    return@flow
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!cachedHome.isNullOrEmpty()) {
                return@flow
            }
        }

        // STEP 1 for filtered categories: Fast progressive initial emit from local disk cache
        val cachedCatalog = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedCatalog()
        val initialCats = if (!cachedCatalog.isNullOrEmpty()) buildCategories(cachedCatalog) else emptyList()
        if (initialCats.isNotEmpty()) {
            emit(initialCats)
        }

        // STEP 2 for filtered categories: Live API fetch
        try {
            val liveMovies = ShowHubApiClient.fetchCatalog(
                category = category,
                genre = genre,
                sortBy = sortBy,
                year = year,
                country = country,
                excludedCountries = if (excludedCountriesStr.isNotBlank()) excludedCountriesStr else null,
                excludedGenres = if (excludedGenresStr.isNotBlank()) excludedGenresStr else null,
                includeUnreleasedMovies = prefs?.getBoolean("pref_unreleased_movies", false) ?: false,
                includeUnreleasedSeries = prefs?.getBoolean("pref_unreleased_series", true) ?: true
            )
            if (liveMovies.isNotEmpty()) {
                val liveCategories = buildCategories(liveMovies)
                if (liveCategories.isNotEmpty()) {
                    emit(liveCategories)
                } else if (initialCats.isEmpty()) {
                    emit(emptyList())
                }
            } else if (cachedCatalog.isNullOrEmpty() && initialCats.isEmpty()) {
                emit(emptyList())
            } else if (initialCats.isEmpty()) {
                emit(emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (cachedCatalog.isNullOrEmpty() && initialCats.isEmpty()) {
                emit(emptyList())
            } else if (initialCats.isEmpty()) {
                emit(emptyList())
            }
        }
    }
}
