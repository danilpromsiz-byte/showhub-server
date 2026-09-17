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

    suspend fun fetchCatalog(category: String = "all"): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val urlStr = "$SERVER_BASE/api/catalog?category=$category&page=1"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.0.0")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val it = arr.getJSONObject(i)
                    val id = it.optString("id", i.toString())
                    val title = it.optString("title", "??? ????????")
                    val desc = it.optString("description", "")
                    val poster = it.optString("poster", "/noposter.png")
                    val posterUrl = if (poster.startsWith("http")) poster else "$SERVER_BASE$poster"
                    val rating = it.optDouble("rating", 7.5)
                    val year = it.optString("year", "2024")
                    val genresList = mutableListOf<String>()
                    val gArr = it.optJSONArray("genres")
                    if (gArr != null) {
                        for (g in 0 until gArr.length()) genresList.add(gArr.getString(g))
                    } else {
                        val gStr = it.optString("genre", "?????")
                        if (gStr.isNotEmpty()) genresList.addAll(gStr.split(",").map { s -> s.trim() })
                    }

                    movies.add(
                        Movie(
                            id = id,
                            title = title,
                            description = desc,
                            posterUrl = posterUrl,
                            backdropUrl = posterUrl,
                            rating = rating,
                            releaseYear = year,
                            duration = "120 ???",
                            genres = if (genresList.isNotEmpty()) genresList else listOf("????"),
                            videoUrl = ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }
}
