package com.example.tvmediaapp.data.api

import com.example.tvmediaapp.data.models.EpisodeInfo
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.SeasonInfo
import com.example.tvmediaapp.data.models.StreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ShowHubApiClient {
    private const val SERVER_BASE = "https://showhub-server.onrender.com"

    suspend fun fetchPopular(): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val url = URL("$SERVER_BASE/api/popular")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.1")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                parseMoviesJson(arr, movies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun fetchCatalog(
        category: String = "all",
        genre: String? = null,
        sortBy: String = "newest",
        page: Int = 1
    ): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val sb = StringBuilder("$SERVER_BASE/api/catalog?category=$category&page=$page")
            if (!genre.isNullOrEmpty() && genre != "all" && genre != "\u0412\u0441\u0435 \u0436\u0430\u043d\u0440\u044b") {
                sb.append("&genre=").append(URLEncoder.encode(genre, "UTF-8"))
            }
            if (sortBy.isNotEmpty()) {
                sb.append("&sort_by=").append(sortBy)
            }
            val url = URL(sb.toString())
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.1")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                parseMoviesJson(arr, movies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun searchMovies(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        if (query.trim().isEmpty()) return@withContext movies
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("$SERVER_BASE/api/search?q=$q")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 18000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.1")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                parseMoviesJson(arr, movies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun fetchMediaDetails(movie: Movie): Movie = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val urlStr = "$SERVER_BASE/api/media/details?source=hdrezka&media_id=${movie.id}&title=$q&year=${movie.releaseYear}&is_series=${if (movie.isSeries) "1" else "0"}"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.1")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val obj = JSONObject(body)

                val seasonsList = mutableListOf<SeasonInfo>()
                val sArr = obj.optJSONArray("seasons")
                if (sArr != null) {
                    for (sIdx in 0 until sArr.length()) {
                        val sObj = sArr.getJSONObject(sIdx)
                        val sNum = sObj.optInt("season_number", sObj.optInt("season_id", sIdx + 1))
                        val sTitle = "\u0421\u0435\u0437\u043e\u043d $sNum"
                        val episodesList = mutableListOf<EpisodeInfo>()
                        val epArr = sObj.optJSONArray("episodes")
                        if (epArr != null) {
                            for (eIdx in 0 until epArr.length()) {
                                val epObj = epArr.getJSONObject(eIdx)
                                val eNum = epObj.optInt("episode_number", epObj.optInt("episode_id", eIdx + 1))
                                val eTitle = epObj.optString("title", "\u0421\u0435\u0440\u0438\u044f $eNum")
                                episodesList.add(EpisodeInfo(episodeNumber = eNum, title = eTitle))
                            }
                        }
                        seasonsList.add(SeasonInfo(seasonNumber = sNum, title = sTitle, episodes = episodesList))
                    }
                }

                val audioList = mutableListOf<String>()
                val trArr = obj.optJSONArray("translators")
                if (trArr != null) {
                    for (tIdx in 0 until trArr.length()) {
                        val trObj = trArr.optJSONObject(tIdx)
                        val name = trObj?.optString("name", "") ?: trArr.optString(tIdx, "")
                        if (name.isNotEmpty()) audioList.add(name)
                    }
                }

                val kpRating = obj.optDouble("rating_kp", movie.ratingKp)
                val imdbRating = obj.optDouble("rating_imdb", movie.ratingImdb)
                val director = obj.optString("director", movie.director)
                val country = obj.optString("country", movie.country)
                val desc = obj.optString("description", movie.description).ifEmpty { movie.description }

                return@withContext movie.copy(
                    ratingKp = if (kpRating > 0) kpRating else movie.ratingKp,
                    ratingImdb = if (imdbRating > 0) imdbRating else movie.ratingImdb,
                    director = director,
                    country = country,
                    description = desc,
                    seasons = if (seasonsList.isNotEmpty()) seasonsList else movie.seasons,
                    audioTracks = if (audioList.isNotEmpty()) audioList else movie.audioTracks
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movie
    }

    suspend fun fetchStreams(
        movie: Movie,
        season: Int? = null,
        episode: Int? = null
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<StreamOption>()
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val sb = StringBuilder("$SERVER_BASE/api/media/streams?source=hdrezka&media_id=${movie.id}&title=$q")
            if (season != null) sb.append("&season=$season")
            if (episode != null) sb.append("&episode=$episode")
            sb.append("&year=${movie.releaseYear}")

            val conn = URL(sb.toString()).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 18000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.1")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(body)
                val sources = listOf("hdrezka", "filmix", "delivembd", "bazon")
                for (src in sources) {
                    if (root.has(src)) {
                        val srcObj = root.optJSONObject(src) ?: continue
                        val strArr = srcObj.optJSONArray("streams") ?: continue
                        for (i in 0 until strArr.length()) {
                            val s = strArr.getJSONObject(i)
                            val qStr = s.optString("quality", "HD")
                            val uStr = s.optString("url", "")
                            if (uStr.startsWith("http") && !uStr.contains("rhtie.mp4")) {
                                streams.add(
                                    StreamOption(
                                        quality = qStr,
                                        url = uStr,
                                        isHls = uStr.contains(".m3u8")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        streams
    }

    private fun parseMoviesJson(arr: JSONArray, outList: MutableList<Movie>) {
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val id = it.optString("id", i.toString())
            var title = it.optString("title", "").ifEmpty { it.optString("original_title", "") }
            val orig = it.optString("original_title", "")
            if (title.contains("\ufffd") || title.trim().isEmpty() || title.contains("???")) {
                if (orig.isNotEmpty() && !orig.contains("\ufffd") && !orig.contains("???")) {
                    title = orig
                } else {
                    continue
                }
            }

            val desc = it.optString("description", "")
            val poster = it.optString("poster", "")
            val posterUrl = if (poster.startsWith("http")) poster else if (poster.isNotEmpty()) "$SERVER_BASE$poster" else "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/600x900"
            val kpRating = if (it.has("rating_kp") && !it.isNull("rating_kp")) it.optDouble("rating_kp", 7.5) else 0.0
            val imdbRating = if (it.has("rating_imdb") && !it.isNull("rating_imdb")) it.optDouble("rating_imdb", 7.2) else 0.0
            val rating = if (kpRating > 0) kpRating else if (imdbRating > 0) imdbRating else it.optDouble("rating", 7.5)
            val year = it.optString("year", "2024").replace("null", "2024").ifEmpty { "2024" }
            val isSeries = it.optBoolean("is_series", false)

            val extraObj = it.optJSONObject("extra_data")
            val country = extraObj?.optString("country", "") ?: it.optString("country", "")
            val director = extraObj?.optString("director", "") ?: it.optString("director", "")

            val genresList = mutableListOf<String>()
            val gArr = it.optJSONArray("genres")
            if (gArr != null) {
                for (g in 0 until gArr.length()) genresList.add(gArr.getString(g))
            } else {
                val gStr = extraObj?.optString("genre", "") ?: it.optString("genre", "")
                if (gStr.isNotEmpty()) genresList.addAll(gStr.split(",").map { s -> s.trim() })
            }

            outList.add(
                Movie(
                    id = id,
                    title = title,
                    originalTitle = orig,
                    description = desc,
                    posterUrl = posterUrl,
                    backdropUrl = posterUrl,
                    rating = rating,
                    ratingKp = if (kpRating > 0) kpRating else rating,
                    ratingImdb = if (imdbRating > 0) imdbRating else rating,
                    releaseYear = year,
                    duration = if (isSeries) "\u0421\u0435\u0440\u0438\u0430\u043b" else "\u0424\u0438\u043b\u044c\u043c",
                    country = country,
                    director = director,
                    genres = if (genresList.isNotEmpty()) genresList else listOf("\u041a\u0438\u043d\u043e"),
                    videoUrl = "",
                    isSeries = isSeries
                )
            )
        }
    }
}

