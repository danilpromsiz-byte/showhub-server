package com.example.tvmediaapp.data.api

import com.example.tvmediaapp.data.models.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ShowHubApiClient {
    private const val SERVER_BASE = "https://showhub-server.onrender.com"

    suspend fun fetchPopular(): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val url = URL("$SERVER_BASE/api/popular")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.0")
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

    suspend fun fetchCatalog(category: String = "all"): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val url = URL("$SERVER_BASE/api/catalog?category=$category&page=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.0")
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

    private fun parseMoviesJson(arr: JSONArray, outList: MutableList<Movie>) {
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val id = it.optString("id", i.toString())
            var title = it.optString("title", "").ifEmpty { it.optString("original_title", "") }
            val orig = it.optString("original_title", "")
            if (title.contains("\ufffd") || title.trim().isEmpty() || title == "??? ????????") {
                if (orig.isNotEmpty() && !orig.contains("\ufffd")) {
                    title = orig
                } else {
                    continue
                }
            }

            val desc = it.optString("description", "")
            val poster = it.optString("poster", "")
            val posterUrl = if (poster.startsWith("http")) poster else if (poster.isNotEmpty()) "$SERVER_BASE$poster" else "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/600x900"
            val rating = if (it.has("rating_kp") && !it.isNull("rating_kp")) it.optDouble("rating_kp", 7.5) else if (it.has("rating_imdb") && !it.isNull("rating_imdb")) it.optDouble("rating_imdb", 7.2) else it.optDouble("rating", 7.5)
            val year = it.optString("year", "2024").replace("null", "2024").ifEmpty { "2024" }
            val isSeries = it.optBoolean("is_series", false)

            val genresList = mutableListOf<String>()
            val gArr = it.optJSONArray("genres")
            if (gArr != null) {
                for (g in 0 until gArr.length()) genresList.add(gArr.getString(g))
            } else {
                val gStr = it.optJSONObject("extra_data")?.optString("genre", "") ?: it.optString("genre", "")
                if (gStr.isNotEmpty()) genresList.addAll(gStr.split(",").map { s -> s.trim() })
            }

            outList.add(
                Movie(
                    id = id,
                    title = title,
                    description = desc,
                    posterUrl = posterUrl,
                    backdropUrl = posterUrl,
                    rating = rating,
                    releaseYear = year,
                    duration = if (isSeries) "??????" else "120 ???",
                    genres = if (genresList.isNotEmpty()) genresList else listOf("????"),
                    videoUrl = "",
                    isSeries = isSeries
                )
            )
        }
    }
}
