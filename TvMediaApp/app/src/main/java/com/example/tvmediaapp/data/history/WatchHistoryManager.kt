package com.example.tvmediaapp.data.history

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
    companion object {
        var historyVersion by mutableIntStateOf(0)
            private set

        fun notifyHistoryChanged() {
            historyVersion++
        }

        fun normalizeTitle(title: String?): String {
            if (title.isNullOrBlank()) return ""
            return title.trim().lowercase()
                .replace("ё", "е")
                .replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
                .replace(Regex("[^a-zа-я0-9]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
        }
    }

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
        saveList(list.take(200))

        if (movie.isSeries || season > 1 || episode > 1) {
            saveEpisodeProgress(movie.id, season, episode, positionMs, durationMs, movie.title)
            if (percentage >= 85) {
                markEpisodeWatched(movie.id, season, episode, movie.title)
            }
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
        notifyHistoryChanged()
    }

    fun saveEpisodeProgress(seriesId: String, season: Int, episode: Int, positionMs: Long, durationMs: Long, title: String? = null) {
        if (durationMs <= 0L || positionMs <= 3000L) return
        val percentage = ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100)
        if (seriesId.isNotBlank()) {
            val key = "ep_pct_${seriesId}_s${season}e${episode}"
            prefs.edit().putInt(key, percentage).apply()
        }
        val cleanT = normalizeTitle(title)
        if (cleanT.isNotBlank()) {
            val key = "ep_pct_t_${cleanT}_s${season}e${episode}"
            prefs.edit().putInt(key, percentage).apply()
        }
        if (percentage >= 85) {
            markEpisodeWatched(seriesId, season, episode, title)
        }
        notifyHistoryChanged()
    }

    fun getEpisodeProgress(seriesId: String, season: Int, episode: Int, title: String? = null): Int {
        if (isEpisodeWatched(seriesId, season, episode, title)) return 100
        if (seriesId.isNotBlank()) {
            val key = "ep_pct_${seriesId}_s${season}e${episode}"
            val p = prefs.getInt(key, 0)
            if (p > 0) return p
        }
        val cleanT = normalizeTitle(title)
        if (cleanT.isNotBlank()) {
            val tKey = "ep_pct_t_${cleanT}_s${season}e${episode}"
            val tp = prefs.getInt(tKey, 0)
            if (tp > 0) return tp

            val histItem = getHistory().firstOrNull { normalizeTitle(it.title) == cleanT }
            if (histItem != null && histItem.id != seriesId) {
                val key = "ep_pct_${histItem.id}_s${season}e${episode}"
                val p = prefs.getInt(key, 0)
                if (p > 0) return p
            }
        }
        return 0
    }

    fun markEpisodeWatched(seriesId: String, season: Int, episode: Int, title: String? = null) {
        if (seriesId.isNotBlank()) {
            val key = "watched_episodes_$seriesId"
            val existing = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
            existing.add("s${season}e${episode}")
            prefs.edit().putStringSet(key, existing).apply()
        }
        val cleanT = normalizeTitle(title)
        if (cleanT.isNotBlank()) {
            val tKey = "watched_episodes_t_$cleanT"
            val tSet = prefs.getStringSet(tKey, emptySet())?.toMutableSet() ?: mutableSetOf()
            tSet.add("s${season}e${episode}")
            prefs.edit().putStringSet(tKey, tSet).apply()

            val histItem = getHistory().firstOrNull { normalizeTitle(it.title) == cleanT }
            if (histItem != null && histItem.id != seriesId) {
                val key = "watched_episodes_${histItem.id}"
                val existing = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                existing.add("s${season}e${episode}")
                prefs.edit().putStringSet(key, existing).apply()
            }
        }
        notifyHistoryChanged()
    }

    fun isEpisodeWatched(seriesId: String, season: Int, episode: Int, title: String? = null): Boolean {
        if (seriesId.isNotBlank()) {
            val key = "watched_episodes_$seriesId"
            val watched = prefs.getStringSet(key, emptySet()) ?: emptySet()
            if (watched.contains("s${season}e${episode}")) return true
        }
        val cleanT = normalizeTitle(title)
        if (cleanT.isNotBlank()) {
            val tKey = "watched_episodes_t_$cleanT"
            val tWatched = prefs.getStringSet(tKey, emptySet()) ?: emptySet()
            if (tWatched.contains("s${season}e${episode}")) return true

            val histItem = getHistory().firstOrNull { normalizeTitle(it.title) == cleanT }
            if (histItem != null && histItem.id != seriesId) {
                val key = "watched_episodes_${histItem.id}"
                val watched = prefs.getStringSet(key, emptySet()) ?: emptySet()
                if (watched.contains("s${season}e${episode}")) return true
            }
        }
        return false
    }

    fun getLastWatchedEpisode(seriesId: String, title: String? = null): Pair<Int, Int>? {
        val candidates = mutableListOf<HistoryItem>()
        val hist = getHistory()
        hist.firstOrNull { it.id == seriesId }?.let { candidates.add(it) }
        if (!title.isNullOrBlank()) {
            hist.firstOrNull { it.title.equals(title, ignoreCase = true) }?.let {
                if (!candidates.contains(it)) candidates.add(it)
            }
        }

        var maxSeason = 0
        var maxEp = 0

        for (item in candidates) {
            val s = item.season ?: 1
            val e = item.episode ?: 1
            if (s > maxSeason || (s == maxSeason && e > maxEp)) {
                maxSeason = s
                maxEp = e
            }
        }

        val ids = candidates.map { it.id }.toMutableSet().apply { if (seriesId.isNotBlank()) add(seriesId) }
        for (id in ids) {
            val watchedSet = getWatchedEpisodes(id)
            for (w in watchedSet) {
                val match = Regex("""s(\d+)e(\d+)""").find(w)
                if (match != null) {
                    val s = match.groupValues[1].toIntOrNull() ?: 1
                    val e = match.groupValues[2].toIntOrNull() ?: 1
                    if (s > maxSeason || (s == maxSeason && e > maxEp)) {
                        maxSeason = s
                        maxEp = e
                    }
                }
            }
        }

        return if (maxSeason > 0 && maxEp > 0) Pair(maxSeason, maxEp) else null
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

    fun getKnownTotalEpisodes(seriesId: String): Int {
        if (seriesId.isBlank()) return 0
        return prefs.getInt("known_episodes_$seriesId", 0)
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
        notifyHistoryChanged()
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
