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
                } else {
                    sampleMovies.firstOrNull { it.id == id }?.let { list.add(it) }
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

    private fun generateDefaultEpisodes(seasonNum: Int, count: Int): List<EpisodeInfo> {
        return (1..count).map { ep ->
            EpisodeInfo(
                episodeNumber = ep,
                title = "\u0421\u0435\u0440\u0438\u044f $ep"
            )
        }
    }

    val sampleMovies = listOf(
        Movie(
            id = "4519776",
            title = "\u041f\u043e\u0436\u0438\u0440\u0430\u0442\u0435\u043b\u044c \u0437\u0432\u0451\u0437\u0434",
            originalTitle = "Swallowed Star",
            description = "\u041f\u043e\u0441\u043b\u0435 \u043c\u0430\u0441\u0448\u0442\u0430\u0431\u043d\u043e\u0439 \u043a\u0430\u0442\u0430\u0441\u0442\u0440\u043e\u0444\u044b \u043c\u0438\u0440 \u043d\u0430\u0432\u043e\u0434\u043d\u0438\u043b\u0438 \u043c\u0443\u0442\u0430\u043d\u0442\u044b. \u042e\u043d\u043e\u0448\u0430 \u041b\u043e \u0424\u044d\u043d \u0440\u0435\u0448\u0430\u0435\u0442 \u0441\u0442\u0430\u0442\u044c \u0431\u043e\u0439\u0446\u043e\u043c \u0432\u044b\u0441\u0448\u0435\u0433\u043e \u0440\u0430\u043d\u0433\u0430, \u0447\u0442\u043e\u0431\u044b \u0437\u0430\u0449\u0438\u0442\u0438\u0442\u044c \u0441\u0432\u043e\u044e \u0441\u0435\u043c\u044c\u044e.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/6201401/e9e30a84-88fe-4a57-bbfd-986fc0e2ff9b/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/6201401/e9e30a84-88fe-4a57-bbfd-986fc0e2ff9b/1920x1080",
            rating = 8.6,
            ratingKp = 8.6,
            ratingImdb = 8.2,
            releaseYear = "2020",
            duration = "24 \u043c\u0438\u043d",
            country = "\u041a\u0438\u0442\u0430\u0439",
            director = "\u0428\u044d\u043d \u041b\u044d\u0446\u0437\u044e\u043d\u044c",
            genres = listOf("\u0410\u043d\u0438\u043c\u0435", "\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430", "\u0411\u043e\u0435\u0432\u0438\u043a"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(
                SeasonInfo(1, "\u0421\u0435\u0437\u043e\u043d 1", generateDefaultEpisodes(1, 26)),
                SeasonInfo(2, "\u0421\u0435\u0437\u043e\u043d 2", generateDefaultEpisodes(2, 52)),
                SeasonInfo(3, "\u0421\u0435\u0437\u043e\u043d 3", generateDefaultEpisodes(3, 52))
            ),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "1115471",
            title = "\u041c\u0430\u0441\u0442\u0435\u0440 \u0438 \u041c\u0430\u0440\u0433\u0430\u0440\u0438\u0442\u0430",
            originalTitle = "The Master and Margarita",
            description = "\u041c\u043e\u0441\u043a\u0432\u0430, 1930-\u0435 \u0433\u043e\u0434\u044b. \u0418\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u043f\u0438\u0441\u0430\u0442\u0435\u043b\u044c \u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442\u0441\u044f \u0432 \u0446\u0435\u043d\u0442\u0440\u0435 \u0441\u043a\u0430\u043d\u0434\u0430\u043b\u0430.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/1920x1080",
            rating = 7.8,
            ratingKp = 7.8,
            ratingImdb = 7.4,
            releaseYear = "2024",
            duration = "157 \u043c\u0438\u043d",
            country = "\u0420\u043e\u0441\u0441\u0438\u044f",
            director = "\u041c\u0438\u0445\u0430\u0438\u043b \u041b\u043e\u043a\u0448\u0438\u043d",
            genres = listOf("\u0414\u0440\u0430\u043c\u0430", "\u0424\u044d\u043d\u0442\u0435\u0437\u0438"),
            videoUrl = "",
            isSeries = false,
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "5244522",
            title = "\u0421\u043b\u043e\u0432\u043e \u043f\u0430\u0446\u0430\u043d\u0430. \u041a\u0440\u043e\u0432\u044c \u043d\u0430 \u0430\u0441\u0444\u0430\u043b\u044c\u0442\u0435",
            originalTitle = "The Boy's Word: Blood on the Asphalt",
            description = "\u041a\u043e\u043d\u0435\u0446 1980-\u0445. \u041f\u043e\u043a\u0430 \u0440\u043e\u0434\u0438\u0442\u0435\u043b\u0438 \u0431\u043e\u0440\u044e\u0442\u0441\u044f \u0437\u0430 \u0432\u044b\u0436\u0438\u0432\u0430\u043d\u0438\u0435, \u043f\u043e\u0434\u0440\u043e\u0441\u0442\u043a\u0438 \u0441\u0431\u0438\u0432\u0430\u044e\u0442\u0441\u044f \u0432 \u0443\u043b\u0438\u0447\u043d\u044b\u0435 \u0441\u0442\u0430\u0438.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/a50ef120-cf91-4475-ae90-c08170c0c6fb/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/a50ef120-cf91-4475-ae90-c08170c0c6fb/1920x1080",
            rating = 8.3,
            ratingKp = 8.3,
            ratingImdb = 7.7,
            releaseYear = "2023",
            duration = "52 \u043c\u0438\u043d",
            country = "\u0420\u043e\u0441\u0441\u0438\u044f",
            director = "\u0416\u043e\u0440\u0430 \u041a\u0440\u044b\u0436\u043e\u0432\u043d\u0438\u043a\u043e\u0432",
            genres = listOf("\u0414\u0440\u0430\u043c\u0430", "\u041a\u0440\u0438\u043c\u0438\u043d\u0430\u043b"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(
                SeasonInfo(1, "\u0421\u0435\u0437\u043e\u043d 1", generateDefaultEpisodes(1, 8))
            ),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "404900",
            title = "\u0414\u044e\u043d\u0430: \u0427\u0430\u0441\u0442\u044c \u0432\u0442\u043e\u0440\u0430\u044f",
            originalTitle = "Dune: Part Two",
            description = "\u041f\u043e\u043b \u0410\u0442\u0440\u0435\u0439\u0434\u0435\u0441 \u043e\u0431\u044a\u0435\u0434\u0438\u043d\u044f\u0435\u0442\u0441\u044f \u0441 \u0427\u0430\u043d\u0438 \u0438 \u0444\u0440\u0435\u043c\u0435\u043d\u0430\u043c\u0438, \u0441\u0442\u0440\u0435\u043c\u044f\u0441\u044c \u043e\u0442\u043e\u043c\u0441\u0442\u0438\u0442\u044c \u0437\u0430 \u0441\u0432\u043e\u044e \u0441\u0435\u043c\u044c\u044e.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10893610/b43d3a04-5100-47fa-80e2-7634f19b16ea/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10893610/b43d3a04-5100-47fa-80e2-7634f19b16ea/1920x1080",
            rating = 8.5,
            ratingKp = 8.5,
            ratingImdb = 8.6,
            releaseYear = "2024",
            duration = "166 \u043c\u0438\u043d",
            country = "\u0421\u0428\u0410",
            director = "\u0414\u0435\u043d\u0438 \u0412\u0438\u043b\u044c\u043d\u0451\u0432",
            genres = listOf("\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430", "\u0411\u043e\u0435\u0432\u0438\u043a"),
            videoUrl = "",
            isSeries = false,
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "4664634",
            title = "\u041e\u043f\u043f\u0435\u043d\u0433\u0435\u0439\u043c\u0435\u0440",
            originalTitle = "Oppenheimer",
            description = "\u0418\u0441\u0442\u043e\u0440\u0438\u044f \u0441\u043e\u0437\u0434\u0430\u043d\u0438\u044f \u043f\u0435\u0440\u0432\u043e\u0439 \u0432 \u043c\u0438\u0440\u0435 \u044f\u0434\u0435\u0440\u043d\u043e\u0439 \u0431\u043e\u043c\u0431\u044b \u043f\u043e\u0434 \u0440\u0443\u043a\u043e\u0432\u043e\u0434\u0441\u0442\u0432\u043e\u043c \u0420\u043e\u0431\u0435\u0440\u0442\u0430 \u041e\u043f\u043f\u0435\u043d\u0433\u0435\u0439\u043c\u0435\u0440\u0430.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/985ff0ce-9694-46bb-86e4-3994344db6fb/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/985ff0ce-9694-46bb-86e4-3994344db6fb/1920x1080",
            rating = 8.2,
            ratingKp = 8.2,
            ratingImdb = 8.9,
            releaseYear = "2023",
            duration = "180 \u043c\u0438\u043d",
            country = "\u0421\u0428\u0410",
            director = "\u041a\u0440\u0438\u0441\u0442\u043e\u0444\u0435\u0440 \u041d\u043e\u043b\u0430\u043d",
            genres = listOf("\u0411\u0438\u043e\u0433\u0440\u0430\u0444\u0438\u044f", "\u0414\u0440\u0430\u043c\u0430"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "505898",
            title = "\u0413\u043e\u043b\u043e\u0432\u043e\u043b\u043e\u043c\u043a\u0430 2",
            originalTitle = "Inside Out 2",
            description = "\u0412 \u0433\u043e\u043b\u043e\u0432\u0435 \u043f\u043e\u0434\u0440\u043e\u0441\u0442\u043a\u0430 \u0420\u0430\u0439\u043b\u0438 \u043f\u043e\u044f\u0432\u043b\u044f\u044e\u0442\u0441\u044f \u043d\u043e\u0432\u044b\u0435 \u044d\u043c\u043e\u0446\u0438\u0438.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/da7c92b2-f155-46f9-b3a5-1d07c0b05b38/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/da7c92b2-f155-46f9-b3a5-1d07c0b05b38/1920x1080",
            rating = 8.1,
            ratingKp = 8.1,
            ratingImdb = 7.7,
            releaseYear = "2024",
            duration = "96 \u043c\u0438\u043d",
            country = "\u0421\u0428\u0410",
            director = "\u041a\u0435\u043b\u0441\u0438 \u041c\u0430\u043d\u043d",
            genres = listOf("\u041c\u0443\u043b\u044c\u0442\u0444\u0438\u043b\u044c\u043c", "\u0421\u0435\u043c\u0435\u0439\u043d\u044b\u0439"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "1318972",
            title = "\u0427\u0435\u0431\u0443\u0440\u0430\u0448\u043a\u0430",
            originalTitle = "Cheburashka",
            description = "\u041f\u0440\u0438\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f \u043c\u043e\u0445\u043d\u0430\u0442\u043e\u0433\u043e \u0437\u0432\u0435\u0440\u044c\u043a\u0430 \u0438\u0437 \u0434\u0430\u043b\u0435\u043a\u043e\u0439 \u0430\u043f\u0435\u043b\u044c\u0441\u0438\u043d\u043e\u0432\u043e\u0439 \u0441\u0442\u0440\u0430\u043d\u044b.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4486454/3d0f7725-3b91-4df2-a386-8adffc9d6eb4/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4486454/3d0f7725-3b91-4df2-a386-8adffc9d6eb4/1920x1080",
            rating = 7.3,
            ratingKp = 7.3,
            ratingImdb = 6.2,
            releaseYear = "2023",
            duration = "113 \u043c\u0438\u043d",
            country = "\u0420\u043e\u0441\u0441\u0438\u044f",
            director = "\u0414\u043c\u0438\u0442\u0440\u0438\u0439 \u0414\u044c\u044f\u0447\u0435\u043d\u043a\u043e",
            genres = listOf("\u0421\u0435\u043c\u0435\u0439\u043d\u044b\u0439", "\u041a\u043e\u043c\u0435\u0434\u0438\u044f"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "258687",
            title = "\u0418\u043d\u0442\u0435\u0440\u0441\u0442\u0435\u043b\u043b\u0430\u0440",
            originalTitle = "Interstellar",
            description = "\u041a\u043e\u043b\u043b\u0435\u043a\u0442\u0438\u0432 \u0438\u0441\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u0442\u0435\u043b\u0435\u0439 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u0441\u043a\u0432\u043e\u0437\u044c \u0447\u0435\u0440\u0432\u043e\u0442\u043e\u0447\u0438\u043d\u0443.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1600647/430042eb-ee69-4818-aed0-a31235fa9a2b/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1600647/430042eb-ee69-4818-aed0-a31235fa9a2b/1920x1080",
            rating = 8.6,
            ratingKp = 8.6,
            ratingImdb = 8.7,
            releaseYear = "2014",
            duration = "169 \u043c\u0438\u043d",
            country = "\u0421\u0428\u0410",
            director = "\u041a\u0440\u0438\u0441\u0442\u043e\u0444\u0435\u0440 \u041d\u043e\u043b\u0430\u043d",
            genres = listOf("\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430", "\u0414\u0440\u0430\u043c\u0430"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "1143242",
            title = "\u0414\u0436\u0435\u043d\u0442\u043b\u044c\u043c\u0435\u043d\u044b",
            originalTitle = "The Gentlemen",
            description = "\u041e\u043a\u0441\u0444\u043e\u0440\u0434\u0441\u043a\u0438\u0439 \u0432\u044b\u043f\u0443\u0441\u043a\u043d\u0438\u043a \u043f\u0440\u0438\u0434\u0443\u043c\u044b\u0432\u0430\u0435\u0442 \u0445\u0438\u0442\u0440\u0443\u044e \u0441\u0445\u0435\u043c\u0443 \u043d\u0435\u043b\u0435\u0433\u0430\u043b\u044c\u043d\u043e\u0433\u043e \u0431\u0438\u0437\u043d\u0435\u0441\u0430.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1599028/637f7170-e547-4152-8802-8323e63ab328/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1599028/637f7170-e547-4152-8802-8323e63ab328/1920x1080",
            rating = 8.6,
            ratingKp = 8.6,
            ratingImdb = 7.8,
            releaseYear = "2019",
            duration = "113 \u043c\u0438\u043d",
            country = "\u0412\u0435\u043b\u0438\u043a\u043e\u0431\u0440\u0438\u0442\u0430\u043d\u0438\u044f",
            director = "\u0413\u0430\u0439 \u0420\u0438\u0447\u0438",
            genres = listOf("\u041a\u0440\u0438\u043c\u0438\u043d\u0430\u043b", "\u041a\u043e\u043c\u0435\u0434\u0438\u044f"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "1100777",
            title = "\u0422\u0440\u0438\u0433\u0433\u0435\u0440",
            originalTitle = "Trigger",
            description = "\u041f\u0441\u0438\u0445\u043e\u043b\u043e\u0433 \u0410\u0440\u0442\u0451\u043c \u0421\u0442\u0440\u0435\u043b\u0435\u0446\u043a\u0438\u0439 \u043f\u0440\u0430\u043a\u0442\u0438\u043a\u0443\u0435\u0442 \u0448\u043e\u043a\u043e\u0432\u0443\u044e \u0442\u0435\u0440\u0430\u043f\u0438\u044e.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4303601/447a111a-7b3b-483a-86fa-b0548ca783f9/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4303601/447a111a-7b3b-483a-86fa-b0548ca783f9/1920x1080",
            rating = 8.5,
            ratingKp = 8.5,
            ratingImdb = 7.4,
            releaseYear = "2020",
            duration = "52 \u043c\u0438\u043d",
            country = "\u0420\u043e\u0441\u0441\u0438\u044f",
            director = "\u0414\u043c\u0438\u0442\u0440\u0438\u0439 \u0422\u044e\u0440\u0438\u043d",
            genres = listOf("\u0414\u0440\u0430\u043c\u0430", "\u0414\u0435\u0442\u0435\u043a\u0442\u0438\u0432"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(
                SeasonInfo(1, "\u0421\u0435\u0437\u043e\u043d 1", generateDefaultEpisodes(1, 16)),
                SeasonInfo(2, "\u0421\u0435\u0437\u043e\u043d 2", generateDefaultEpisodes(2, 16))
            ),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "kodik_serial-60205",
            title = "Чеболь против детектива",
            originalTitle = "Flex X Cop",
            description = "Избалованный наследник огромной корпорации попадает на службу в полицию и использует свои миллионы и связи для раскрытия преступлений.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10809707/dcfdb788-b2ca-4cfa-8186-53fa3857ee13/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10809707/dcfdb788-b2ca-4cfa-8186-53fa3857ee13/1920x1080",
            rating = 8.2,
            ratingKp = 8.2,
            ratingImdb = 7.9,
            releaseYear = "2024",
            duration = "65 мин",
            country = "Корея Южная",
            director = "Ким Джэ-хон",
            genres = listOf("Детектив", "Комедия", "Криминал", "Дорама"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(SeasonInfo(1, "Сезон 1", generateDefaultEpisodes(1, 16))),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "1309570",
            title = "Игра в кальмара",
            originalTitle = "Squid Game",
            description = "Сотни игроков в долгах принимают странное приглашение поучаствовать в детских играх ради огромного приза с фатальными последствиями.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/e3f6b4ee-bf85-48fa-869a-6945532ab1a0/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/e3f6b4ee-bf85-48fa-869a-6945532ab1a0/1920x1080",
            rating = 8.1,
            ratingKp = 8.1,
            ratingImdb = 8.0,
            releaseYear = "2021",
            duration = "55 мин",
            country = "Корея Южная",
            director = "Хван Дон-хёк",
            genres = listOf("Триллер", "Драма", "Дорама"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(SeasonInfo(1, "Сезон 1", generateDefaultEpisodes(1, 9))),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "1043758",
            title = "Паразиты",
            originalTitle = "Parasite",
            description = "Бедная семья Ким обманом устраивается на работу в дом богачей Пак, запуская череду непредсказуемых событий.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1599028/0b76b2a2-d1c7-4f04-a284-80ff7bb709a4/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1599028/0b76b2a2-d1c7-4f04-a284-80ff7bb709a4/1920x1080",
            rating = 8.6,
            ratingKp = 8.6,
            ratingImdb = 8.5,
            releaseYear = "2019",
            duration = "131 мин",
            country = "Корея Южная",
            director = "Пон Джун-хо",
            genres = listOf("Триллер", "Драма", "Комедия"),
            videoUrl = "",
            isSeries = false,
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "1355137",
            title = "Постучись в мою дверь",
            originalTitle = "Sen Çal Kapımı",
            description = "Флористка Эда и архитектор Серкан Болат заключают договор о фиктивной помолвке, который перерастает в настоящую любовь.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1900788/e0214c77-cecb-456a-a0bb-a72eb56f082e/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1900788/e0214c77-cecb-456a-a0bb-a72eb56f082e/1920x1080",
            rating = 8.2,
            ratingKp = 8.2,
            ratingImdb = 7.3,
            releaseYear = "2020",
            duration = "45 мин",
            country = "Турция",
            director = "Алтан Дёнмез",
            genres = listOf("Мелодрама", "Комедия"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(SeasonInfo(1, "Сезон 1", generateDefaultEpisodes(1, 161))),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        ),
        Movie(
            id = "748554",
            title = "Атака титанов",
            originalTitle = "Attack on Titan",
            description = "Человечество живёт за огромными стенами, защищающими от гигантских титанов-людоедов.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4303601/bb816ea8-fa24-4f46-95ea-656da6c84c17/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4303601/bb816ea8-fa24-4f46-95ea-656da6c84c17/1920x1080",
            rating = 8.7,
            ratingKp = 8.7,
            ratingImdb = 9.1,
            releaseYear = "2013",
            duration = "24 мин",
            country = "Япония",
            director = "Тэцуро Араки",
            genres = listOf("Аниме", "Боевик", "Фэнтези"),
            videoUrl = "",
            isSeries = true,
            seasons = listOf(SeasonInfo(1, "Сезон 1", generateDefaultEpisodes(1, 25))),
            audioTracks = listOf(AudioTrackInfo("1", "Original"))
        )
    )

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

        // Genre filter
        if (!genre.isNullOrEmpty() && genre != "all" && genre != "Все жанры") {
            res = res.filter { m -> m.genres.any { g -> g.contains(genre, ignoreCase = true) } }
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

        // Sorting
        return when (sortBy) {
            "rating" -> res.sortedByDescending { it.rating }
            "popular" -> res.sortedByDescending { it.ratingKp }
            "year" -> res.sortedByDescending { it.releaseYear }
            else -> res.sortedByDescending { it.releaseYear }
        }
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
            val all = if (base.isNotEmpty()) base else sampleMovies
            val filtered = filterAndSort(all, category, genre, sortBy, year, country)
            emit(listOf(MovieCategory(id = "favorites", title = "Избранное", movies = filtered)))
            return@flow
        }

        val excludedCountriesStr = prefs?.getString("pref_excluded_countries", "") ?: ""
        val onlyWithPoster = prefs?.getBoolean("pref_only_with_poster", true) ?: true

        fun hasValidPoster(url: String): Boolean {
            if (url.isBlank()) return false
            val lower = url.lowercase()
            return !lower.contains("no_image") &&
                   !lower.contains("noposter") &&
                   !lower.contains("kinopoiskapiunofficial.tech") &&
                   !lower.contains("st.kp.yandex.net") &&
                   !lower.contains("10592371/4c676451")
        }

        fun buildCategories(movies: List<Movie>): List<MovieCategory> {
            var effective = movies.distinctBy { it.id }
            if (onlyWithPoster) {
                effective = effective.filter { m -> hasValidPoster(m.posterUrl) }
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
            // Immediately enrich movies with cached details from disk
            effective = effective.map { m ->
                com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(m.id, m.title, m.releaseYear) ?: m
            }
            if (onlyWithPoster) {
                effective = effective.filter { m -> hasValidPoster(m.posterUrl) }
            }
            effective = filterAndSort(effective, category, genre, sortBy, year, country)
            if (effective.isEmpty()) return emptyList()

            val isPureDefault = category == "all" &&
                (genre.isNullOrEmpty() || genre == "Все жанры") &&
                (country.isNullOrEmpty() || country == "all") &&
                (year.isNullOrEmpty() || year == "all")

            return if (isPureDefault) {
                listOf(
                    MovieCategory(id = "popular", title = "Популярные новинки", movies = effective),
                    MovieCategory(id = "top_rated", title = "Топ рейтинга", movies = effective.sortedByDescending { it.rating }),
                    MovieCategory(id = "series", title = "Сериалы", movies = effective.filter { it.isSeries }),
                    MovieCategory(id = "movies", title = "Фильмы", movies = effective.filter { !it.isSeries })
                )
            } else {
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
                listOf(MovieCategory(id = category, title = displayTitle, movies = effective))
            }
        }

        // STEP 1: Fast progressive initial emit from local disk cache or sample catalog (0-50 ms)!
        val cachedCatalog = com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedCatalog()
        val initialMovies = if (!cachedCatalog.isNullOrEmpty()) cachedCatalog else sampleMovies
        val initialCats = buildCategories(initialMovies)
        if (initialCats.isNotEmpty()) {
            emit(initialCats)
        } else {
            // Emit empty list so the grid clears stale items and displays loading state immediately
            emit(emptyList())
        }

        // STEP 2: Live API fetch in background to get fresh releases
        try {
            val liveMovies = ShowHubApiClient.fetchCatalog(
                category = category,
                genre = genre,
                sortBy = sortBy,
                year = year,
                country = country,
                excludedCountries = if (excludedCountriesStr.isNotBlank()) excludedCountriesStr else null
            )
            if (liveMovies.isNotEmpty()) {
                val isDefaultMainCatalog = category == "all" &&
                    (genre.isNullOrEmpty() || genre == "Все жанры" || genre == "all") &&
                    (country.isNullOrEmpty() || country == "all") &&
                    (year.isNullOrEmpty() || year == "all") &&
                    sortBy == "newest"
                if (isDefaultMainCatalog) {
                    com.example.tvmediaapp.data.cache.MediaDiskCache.putCachedCatalog(liveMovies)
                }
                val liveCategories = buildCategories(liveMovies)
                if (liveCategories.isNotEmpty()) {
                    emit(liveCategories)
                }
            } else if (cachedCatalog.isNullOrEmpty() && initialCats.isEmpty()) {
                val fallback = buildCategories(sampleMovies)
                if (fallback.isNotEmpty()) {
                    emit(fallback)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (cachedCatalog.isNullOrEmpty() && initialCats.isEmpty()) {
                val fallback = buildCategories(sampleMovies)
                if (fallback.isNotEmpty()) {
                    emit(fallback)
                }
            }
        }
    }
}
