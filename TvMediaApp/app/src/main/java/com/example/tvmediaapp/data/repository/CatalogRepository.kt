package com.example.tvmediaapp.data.repository

import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.MovieCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CatalogRepository {

    private val sampleMovies = listOf(
        Movie(
            id = "4519776",
            title = "\u041f\u043e\u0436\u0438\u0440\u0430\u0442\u0435\u043b\u044c \u0437\u0432\u0451\u0437\u0434",
            description = "\u041f\u043e\u0441\u043b\u0435 \u043c\u0430\u0441\u0448\u0442\u0430\u0431\u043d\u043e\u0439 \u043a\u0430\u0442\u0430\u0441\u0442\u0440\u043e\u0444\u044b \u043c\u0438\u0440 \u043d\u0430\u0432\u043e\u0434\u043d\u0438\u043b\u0438 \u043c\u0443\u0442\u0430\u043d\u0442\u044b. \u042e\u043d\u043e\u0448\u0430 \u041b\u043e \u0424\u044d\u043d \u0440\u0435\u0448\u0430\u0435\u0442 \u0441\u0442\u0430\u0442\u044c \u0431\u043e\u0439\u0446\u043e\u043c \u0432\u044b\u0441\u0448\u0435\u0433\u043e \u0440\u0430\u043d\u0433\u0430, \u0447\u0442\u043e\u0431\u044b \u0437\u0430\u0449\u0438\u0442\u0438\u0442\u044c \u0441\u0432\u043e\u044e \u0441\u0435\u043c\u044c\u044e.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/6201401/e9e30a84-88fe-4a57-bbfd-986fc0e2ff9b/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/6201401/e9e30a84-88fe-4a57-bbfd-986fc0e2ff9b/1920x1080",
            rating = 8.6,
            releaseYear = "2020",
            duration = "24 \u043c\u0438\u043d",
            genres = listOf("\u0410\u043d\u0438\u043c\u0435", "\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430", "\u0411\u043e\u0435\u0432\u0438\u043a"),
            videoUrl = "",
            isSeries = true
        ),
        Movie(
            id = "1115471",
            title = "\u041c\u0430\u0441\u0442\u0435\u0440 \u0438 \u041c\u0430\u0440\u0433\u0430\u0440\u0438\u0442\u0430",
            description = "\u041c\u043e\u0441\u043a\u0432\u0430, 1930-\u0435 \u0433\u043e\u0434\u044b. \u0418\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u043f\u0438\u0441\u0430\u0442\u0435\u043b\u044c \u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442\u0441\u044f \u0432 \u0446\u0435\u043d\u0442\u0440\u0435 \u0441\u043a\u0430\u043d\u0434\u0430\u043b\u0430. \u0412\u0434\u043e\u0445\u043d\u043e\u0432\u0438\u0432\u0448\u0438\u0441\u044c \u041c\u0430\u0440\u0433\u0430\u0440\u0438\u0442\u043e\u0439, \u043e\u043d \u043f\u0438\u0448\u0435\u0442 \u0440\u043e\u043c\u0430\u043d \u043e \u0412\u043e\u043b\u0430\u043d\u0434\u0435.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/1920x1080",
            rating = 7.8,
            releaseYear = "2024",
            duration = "157 \u043c\u0438\u043d",
            genres = listOf("\u0414\u0440\u0430\u043c\u0430", "\u0424\u044d\u043d\u0442\u0435\u0437\u0438"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "5244522",
            title = "\u0421\u043b\u043e\u0432\u043e \u043f\u0430\u0446\u0430\u043d\u0430. \u041a\u0440\u043e\u0432\u044c \u043d\u0430 \u0430\u0441\u0444\u0430\u043b\u044c\u0442\u0435",
            description = "\u041a\u043e\u043d\u0435\u0446 1980-\u0445. \u041f\u043e\u043a\u0430 \u0440\u043e\u0434\u0438\u0442\u0435\u043b\u0438 \u0431\u043e\u0440\u044e\u0442\u0441\u044f \u0437\u0430 \u0432\u044b\u0436\u0438\u0432\u0430\u043d\u0438\u0435, \u043f\u043e\u0434\u0440\u043e\u0441\u0442\u043a\u0438 \u0441\u0431\u0438\u0432\u0430\u044e\u0442\u0441\u044f \u0432 \u0443\u043b\u0438\u0447\u043d\u044b\u0435 \u0441\u0442\u0430\u0438.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/a50ef120-cf91-4475-ae90-c08170c0c6fb/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/a50ef120-cf91-4475-ae90-c08170c0c6fb/1920x1080",
            rating = 8.3,
            releaseYear = "2023",
            duration = "52 \u043c\u0438\u043d",
            genres = listOf("\u0414\u0440\u0430\u043c\u0430", "\u041a\u0440\u0438\u043c\u0438\u043d\u0430\u043b"),
            videoUrl = "",
            isSeries = true
        ),
        Movie(
            id = "404900",
            title = "\u0414\u044e\u043d\u0430: \u0427\u0430\u0441\u0442\u044c \u0432\u0442\u043e\u0440\u0430\u044f",
            description = "\u041f\u043e\u043b \u0410\u0442\u0440\u0435\u0439\u0434\u0435\u0441 \u043e\u0431\u044a\u0435\u0434\u0438\u043d\u044f\u0435\u0442\u0441\u044f \u0441 \u0427\u0430\u043d\u0438 \u0438 \u0444\u0440\u0435\u043c\u0435\u043d\u0430\u043c\u0438, \u0441\u0442\u0440\u0435\u043c\u044f\u0441\u044c \u043e\u0442\u043e\u043c\u0441\u0442\u0438\u0442\u044c \u0437\u0430 \u0441\u0432\u043e\u044e \u0441\u0435\u043c\u044c\u044e.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10893610/b43d3a04-5100-47fa-80e2-7634f19b16ea/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10893610/b43d3a04-5100-47fa-80e2-7634f19b16ea/1920x1080",
            rating = 8.5,
            releaseYear = "2024",
            duration = "166 \u043c\u0438\u043d",
            genres = listOf("\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430", "\u0411\u043e\u0435\u0432\u0438\u043a"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "4664634",
            title = "\u041e\u043f\u043f\u0435\u043d\u0433\u0435\u0439\u043c\u0435\u0440",
            description = "\u0418\u0441\u0442\u043e\u0440\u0438\u044f \u0441\u043e\u0437\u0434\u0430\u043d\u0438\u044f \u043f\u0435\u0440\u0432\u043e\u0439 \u0432 \u043c\u0438\u0440\u0435 \u044f\u0434\u0435\u0440\u043d\u043e\u0439 \u0431\u043e\u043c\u0431\u044b \u043f\u043e\u0434 \u0440\u0443\u043a\u043e\u0432\u043e\u0434\u0441\u0442\u0432\u043e\u043c \u0420\u043e\u0431\u0435\u0440\u0442\u0430 \u041e\u043f\u043f\u0435\u043d\u0433\u0435\u0439\u043c\u0435\u0440\u0430.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/985ff0ce-9694-46bb-86e4-3994344db6fb/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4774061/985ff0ce-9694-46bb-86e4-3994344db6fb/1920x1080",
            rating = 8.2,
            releaseYear = "2023",
            duration = "180 \u043c\u0438\u043d",
            genres = listOf("\u0411\u0438\u043e\u0433\u0440\u0430\u0444\u0438\u044f", "\u0414\u0440\u0430\u043c\u0430"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "505898",
            title = "\u0413\u043e\u043b\u043e\u0432\u043e\u043b\u043e\u043c\u043a\u0430 2",
            description = "\u0412 \u0433\u043e\u043b\u043e\u0432\u0435 \u043f\u043e\u0434\u0440\u043e\u0441\u0442\u043a\u0430 \u0420\u0430\u0439\u043b\u0438 \u043f\u043e\u044f\u0432\u043b\u044f\u044e\u0442\u0441\u044f \u043d\u043e\u0432\u044b\u0435 \u044d\u043c\u043e\u0446\u0438\u0438: \u0422\u0440\u0435\u0432\u043e\u0436\u043d\u043e\u0441\u0442\u044c, \u0417\u0430\u0432\u0438\u0441\u0442\u044c \u0438 \u0421\u0442\u044b\u0434.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/da7c92b2-f155-46f9-b3a5-1d07c0b05b38/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/da7c92b2-f155-46f9-b3a5-1d07c0b05b38/1920x1080",
            rating = 8.1,
            releaseYear = "2024",
            duration = "96 \u043c\u0438\u043d",
            genres = listOf("\u041c\u0443\u043b\u044c\u0442\u0444\u0438\u043b\u044c\u043c", "\u0421\u0435\u043c\u0435\u0439\u043d\u044b\u0439"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "1318972",
            title = "\u0427\u0435\u0431\u0443\u0440\u0430\u0448\u043a\u0430",
            description = "\u041f\u0440\u0438\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f \u043c\u043e\u0445\u043d\u0430\u0442\u043e\u0433\u043e \u0437\u0432\u0435\u0440\u044c\u043a\u0430 \u0438\u0437 \u0434\u0430\u043b\u0435\u043a\u043e\u0439 \u0430\u043f\u0435\u043b\u044c\u0441\u0438\u043d\u043e\u0432\u043e\u0439 \u0441\u0442\u0440\u0430\u043d\u044b.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4486454/3d0f7725-3b91-4df2-a386-8adffc9d6eb4/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4486454/3d0f7725-3b91-4df2-a386-8adffc9d6eb4/1920x1080",
            rating = 7.3,
            releaseYear = "2023",
            duration = "113 \u043c\u0438\u043d",
            genres = listOf("\u0421\u0435\u043c\u0435\u0439\u043d\u044b\u0439", "\u041a\u043e\u043c\u0435\u0434\u0438\u044f"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "258687",
            title = "\u0418\u043d\u0442\u0435\u0440\u0441\u0442\u0435\u043b\u043b\u0430\u0440",
            description = "\u041a\u043e\u043b\u043b\u0435\u043a\u0442\u0438\u0432 \u0438\u0441\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u0442\u0435\u043b\u0435\u0439 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u0441\u043a\u0432\u043e\u0437\u044c \u0447\u0435\u0440\u0432\u043e\u0442\u043e\u0447\u0438\u043d\u0443 \u0432 \u043f\u043e\u0438\u0441\u043a\u0430\u0445 \u043d\u043e\u0432\u043e\u0433\u043e \u0434\u043e\u043c\u0430.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1600647/430042eb-ee69-4818-aed0-a31235fa9a2b/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1600647/430042eb-ee69-4818-aed0-a31235fa9a2b/1920x1080",
            rating = 8.6,
            releaseYear = "2014",
            duration = "169 \u043c\u0438\u043d",
            genres = listOf("\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430", "\u0414\u0440\u0430\u043c\u0430"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "1143242",
            title = "\u0414\u0436\u0435\u043d\u0442\u043b\u044c\u043c\u0435\u043d\u044b",
            description = "\u041e\u043a\u0441\u0444\u043e\u0440\u0434\u0441\u043a\u0438\u0439 \u0432\u044b\u043f\u0443\u0441\u043a\u043d\u0438\u043a \u043f\u0440\u0438\u0434\u0443\u043c\u044b\u0432\u0430\u0435\u0442 \u0445\u0438\u0442\u0440\u0443\u044e \u0441\u0445\u0435\u043c\u0443 \u043d\u0435\u043b\u0435\u0433\u0430\u043b\u044c\u043d\u043e\u0433\u043e \u0431\u0438\u0437\u043d\u0435\u0441\u0430 \u0432 \u041b\u043e\u043d\u0434\u043e\u043d\u0435.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1599028/637f7170-e547-4152-8802-8323e63ab328/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/1599028/637f7170-e547-4152-8802-8323e63ab328/1920x1080",
            rating = 8.6,
            releaseYear = "2019",
            duration = "113 \u043c\u0438\u043d",
            genres = listOf("\u041a\u0440\u0438\u043c\u0438\u043d\u0430\u043b", "\u041a\u043e\u043c\u0435\u0434\u0438\u044f"),
            videoUrl = "",
            isSeries = false
        ),
        Movie(
            id = "1100777",
            title = "\u0422\u0440\u0438\u0433\u0433\u0435\u0440",
            description = "\u041f\u0441\u0438\u0445\u043e\u043b\u043e\u0433 \u0410\u0440\u0442\u0451\u043c \u0421\u0442\u0440\u0435\u043b\u0435\u0446\u043a\u0438\u0439 \u043f\u0440\u0430\u043a\u0442\u0438\u043a\u0443\u0435\u0442 \u0448\u043e\u043a\u043e\u0432\u0443\u044e \u0442\u0435\u0440\u0430\u043f\u0438\u044e \u0434\u043b\u044f \u043a\u043b\u0438\u0435\u043d\u0442\u043e\u0432.",
            posterUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4303601/447a111a-7b3b-483a-86fa-b0548ca783f9/600x900",
            backdropUrl = "https://avatars.mds.yandex.net/get-kinopoisk-image/4303601/447a111a-7b3b-483a-86fa-b0548ca783f9/1920x1080",
            rating = 8.5,
            releaseYear = "2020",
            duration = "52 \u043c\u0438\u043d",
            genres = listOf("\u0414\u0440\u0430\u043c\u0430", "\u0414\u0435\u0442\u0435\u043a\u0442\u0438\u0432"),
            videoUrl = "",
            isSeries = true
        )
    )

    fun getCatalog(): Flow<List<MovieCategory>> = flow {
        // 1. Instant 0 ms emit of pristine cinema hits with posters
        val initialCategories = listOf(
            MovieCategory(id = "popular", title = "\u041f\u043e\u043f\u0443\u043b\u044f\u0440\u043d\u044b\u0435 \u043d\u043e\u0432\u0438\u043d\u043a\u0438", movies = sampleMovies),
            MovieCategory(id = "top_rated", title = "\u0422\u043e\u043f \u0440\u0435\u0439\u0442\u0438\u043d\u0433\u0430", movies = sampleMovies.sortedByDescending { it.rating }),
            MovieCategory(id = "series", title = "\u0421\u0435\u0440\u0438\u0430\u043b\u044b", movies = sampleMovies.filter { it.isSeries }),
            MovieCategory(id = "movies", title = "\u0424\u0438\u043b\u044c\u043c\u044b", movies = sampleMovies.filter { !it.isSeries })
        )
        emit(initialCategories)

        // 2. Fetch live updates from ShowHub server
        try {
            val livePopular = ShowHubApiClient.fetchPopular()
            if (livePopular.isNotEmpty()) {
                val combined = (livePopular + sampleMovies).distinctBy { it.title.lowercase().trim() }
                val updatedCategories = listOf(
                    MovieCategory(id = "popular", title = "\u041f\u043e\u043f\u0443\u043b\u044f\u0440\u043d\u044b\u0435 \u043d\u043e\u0432\u0438\u043d\u043a\u0438", movies = combined.take(24)),
                    MovieCategory(id = "top_rated", title = "\u0422\u043e\u043f \u0440\u0435\u0439\u0442\u0438\u043d\u0433\u0430", movies = combined.sortedByDescending { it.rating }.take(24)),
                    MovieCategory(id = "series", title = "\u0421\u0435\u0440\u0438\u0430\u043b\u044b", movies = combined.filter { it.isSeries }),
                    MovieCategory(id = "movies", title = "\u0424\u0438\u043b\u044c\u043c\u044b", movies = combined.filter { !it.isSeries })
                )
                emit(updatedCategories)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMovieById(id: String): Movie? {
        return sampleMovies.find { it.id == id }
    }
}
