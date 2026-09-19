package com.example.tvmediaapp.data.history

import android.content.Context
import android.content.SharedPreferences
import com.example.tvmediaapp.data.models.Movie
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val releaseYear: String,
    val isSeries: Boolean,
    val season: Int,
    val episode: Int,
    val audioId: String,
    val positionMs: Long,
    val durationMs: Long,
    val percentage: Int,
    val timestamp: Long
)

class WatchHistoryManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("showhub_watch_history", Context.MODE_PRIVATE)

    fun saveProgress(
        movie: Movie,
        positionMs: Long,
        durationMs: Long,
        season: Int = 1,
        episode: Int = 1,
        audioId: String = ""
    ) {
        if (movie.id.isEmpty() || positionMs <= 3000L) return
        val percentage = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0

        val item = HistoryItem(
            id = movie.id,
            title = movie.title,
            posterUrl = movie.posterUrl,
            backdropUrl = movie.backdropUrl,
            releaseYear = movie.releaseYear,
            isSeries = movie.isSeries,
            season = season,
            episode = episode,
            audioId = audioId,
            positionMs = positionMs,
            durationMs = durationMs,
            percentage = percentage,
            timestamp = System.currentTimeMillis()
        )

        val list = getHistory().toMutableList()
        list.removeAll { it.id == movie.id }
        list.add(0, item)
        saveList(list.take(60))

        if (movie.isSeries) {
            markEpisodeWatched(movie.id, season, episode)
            // Auto-add started series to favorites so user tracks new episodes
            try {
                val mainPrefs = appContext.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE)
                val currentFavs = mainPrefs.getStringSet("favorite_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (!currentFavs.contains(movie.id)) {
                    currentFavs.add(movie.id)
                    mainPrefs.edit().putStringSet("favorite_ids", currentFavs).apply()
                }
            } catch (_: Exception) {}
        }
    }

    fun markEpisodeWatched(seriesId: String, season: Int, episode: Int) {
        if (seriesId.isBlank()) return
        val key = "watched_episodes_$seriesId"
        val existing = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add("s${season}e${episode}")
        prefs.edit().putStringSet(key, existing).apply()
    }

    fun isEpisodeWatched(seriesId: String, season: Int, episode: Int): Boolean {
        if (seriesId.isBlank()) return false
        val key = "watched_episodes_$seriesId"
        val watched = prefs.getStringSet(key, emptySet()) ?: return false
        return watched.contains("s${season}e${episode}")
    }

    fun getWatchedEpisodes(seriesId: String): Set<String> {
        if (seriesId.isBlank()) return emptySet()
        val key = "watched_episodes_$seriesId"
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    fun updateKnownTotalEpisodes(seriesId: String, currentTotal: Int): Int {
        if (seriesId.isBlank() || currentTotal <= 0) return 0
        val keyKnown = "known_episodes_$seriesId"
        val keyNew = "new_episodes_$seriesId"
        val previousTotal = prefs.getInt(keyKnown, 0)
        
        if (previousTotal in 1 until currentTotal) {
            val newlyAdded = currentTotal - previousTotal
            val existingNew = prefs.getInt(keyNew, 0)
            val updatedNew = (existingNew + newlyAdded).coerceAtLeast(newlyAdded)
            prefs.edit()
                .putInt(keyKnown, currentTotal)
                .putInt(keyNew, updatedNew)
                .apply()
            return updatedNew
        } else if (previousTotal == 0) {
            prefs.edit().putInt(keyKnown, currentTotal).apply()
        }
        return prefs.getInt(keyNew, 0)
    }

    fun getNewEpisodesCount(seriesId: String): Int {
        if (seriesId.isBlank()) return 0
        return prefs.getInt("new_episodes_$seriesId", 0)
    }

    fun hasNewEpisodes(seriesId: String): Boolean {
        return getNewEpisodesCount(seriesId) > 0
    }

    fun clearNewEpisodes(seriesId: String) {
        if (seriesId.isBlank()) return
        prefs.edit().remove("new_episodes_$seriesId").apply()
    }

    fun getProgress(movieId: String): HistoryItem? {
        return getHistory().firstOrNull { it.id == movieId }
    }

    fun getHistory(): List<HistoryItem> {
        val raw = prefs.getString("history_items", "[]") ?: "[]"
        val list = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    HistoryItem(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        posterUrl = obj.optString("posterUrl", ""),
                        backdropUrl = obj.optString("backdropUrl", ""),
                        releaseYear = obj.optString("releaseYear", ""),
                        isSeries = obj.optBoolean("isSeries", false),
                        season = obj.optInt("season", 1),
                        episode = obj.optInt("episode", 1),
                        audioId = obj.optString("audioId", ""),
                        positionMs = obj.optLong("positionMs", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        percentage = obj.optInt("percentage", 0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun clearHistory() {
        prefs.edit().remove("history_items").apply()
    }

    private fun saveList(items: List<HistoryItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("posterUrl", item.posterUrl)
                put("backdropUrl", item.backdropUrl)
                put("releaseYear", item.releaseYear)
                put("isSeries", item.isSeries)
                put("season", item.season)
                put("episode", item.episode)
                put("audioId", item.audioId)
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("percentage", item.percentage)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString("history_items", arr.toString()).apply()
    }
}
