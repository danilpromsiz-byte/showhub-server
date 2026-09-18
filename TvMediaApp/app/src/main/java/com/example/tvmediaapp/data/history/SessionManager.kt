package com.example.tvmediaapp.data.history

import android.content.Context
import com.example.tvmediaapp.Screen
import com.example.tvmediaapp.data.models.Movie
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackSession(
    val movie: Movie,
    val videoUrl: String,
    val positionMs: Long,
    val season: Int,
    val episode: Int,
    val audioId: String,
    val screen: Screen
)

object SessionManager {
    private const val PREFS_NAME = "showhub_session"
    private const val KEY_LAST_MOVIE = "last_movie"
    private const val KEY_LAST_SCREEN = "last_screen"

    fun saveSession(
        context: Context,
        movie: Movie?,
        screen: Screen,
        videoUrl: String = "",
        positionMs: Long = 0L,
        season: Int = 1,
        episode: Int = 1,
        audioId: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (movie == null || screen == Screen.HOME) {
            prefs.edit().remove(KEY_LAST_MOVIE).remove(KEY_LAST_SCREEN).apply()
            return
        }

        try {
            val vUrl = if (videoUrl.isNotEmpty()) videoUrl else movie.videoUrl
            val obj = JSONObject().apply {
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
                put("duration", movie.duration)
                put("country", movie.country)
                put("director", movie.director)
                put("actors", movie.actors)
                put("episodesInfo", movie.episodesInfo)
                put("genres", JSONArray(movie.genres))
                put("videoUrl", vUrl)
                put("isSeries", movie.isSeries)
                put("screen", screen.name)
                put("positionMs", positionMs)
                put("season", season)
                put("episode", episode)
                put("audioId", audioId)
                put("timestamp", System.currentTimeMillis())
            }
            prefs.edit()
                .putString(KEY_LAST_MOVIE, obj.toString())
                .putString(KEY_LAST_SCREEN, screen.name)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreSession(context: Context): PlaybackSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LAST_MOVIE, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val genresList = mutableListOf<String>()
            val gArr = obj.optJSONArray("genres")
            if (gArr != null) {
                for (i in 0 until gArr.length()) {
                    genresList.add(gArr.getString(i))
                }
            }

            val vUrl = obj.optString("videoUrl", "")
            val movie = Movie(
                id = obj.optString("id", ""),
                title = obj.optString("title", ""),
                originalTitle = obj.optString("originalTitle", ""),
                description = obj.optString("description", ""),
                posterUrl = obj.optString("posterUrl", ""),
                backdropUrl = obj.optString("backdropUrl", ""),
                rating = obj.optDouble("rating", 0.0),
                ratingKp = obj.optDouble("ratingKp", 0.0),
                ratingImdb = obj.optDouble("ratingImdb", 0.0),
                releaseYear = obj.optString("releaseYear", ""),
                duration = obj.optString("duration", ""),
                country = obj.optString("country", ""),
                director = obj.optString("director", ""),
                actors = obj.optString("actors", ""),
                episodesInfo = obj.optString("episodesInfo", ""),
                genres = genresList,
                videoUrl = vUrl,
                isSeries = obj.optBoolean("isSeries", false)
            )

            val screenName = obj.optString("screen", Screen.DETAILS.name)
            val screen = try { Screen.valueOf(screenName) } catch (_: Exception) { Screen.DETAILS }

            PlaybackSession(
                movie = movie,
                videoUrl = vUrl,
                positionMs = obj.optLong("positionMs", 0L),
                season = obj.optInt("season", 1),
                episode = obj.optInt("episode", 1),
                audioId = obj.optString("audioId", ""),
                screen = screen
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun restoreLastMovie(context: Context): Movie? {
        return restoreSession(context)?.movie
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_MOVIE)
            .remove(KEY_LAST_SCREEN)
            .apply()
    }
}
