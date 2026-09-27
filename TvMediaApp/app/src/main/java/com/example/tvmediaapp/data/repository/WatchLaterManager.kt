package com.example.tvmediaapp.data.repository

import android.content.Context

class WatchLaterManager(context: Context) {
    private val prefs = context.getSharedPreferences("showhub_watch_later", Context.MODE_PRIVATE)

    fun isWatchLater(movieId: String): Boolean {
        if (movieId.isBlank()) return false
        val set = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return movieId in set
    }

    fun toggleWatchLater(movieId: String): Boolean {
        if (movieId.isBlank()) return false
        val set = (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        val newState = if (movieId in set) {
            set.remove(movieId)
            false
        } else {
            set.add(movieId)
            true
        }
        prefs.edit().putStringSet("ids", set).apply()
        return newState
    }

    fun getAllIds(): Set<String> {
        return prefs.getStringSet("ids", emptySet()) ?: emptySet()
    }
}
