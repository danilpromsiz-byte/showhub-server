package com.example.tvmediaapp.data.repository

import android.content.Context

class HiddenMoviesManager(context: Context) {
    private val prefs = context.getSharedPreferences("showhub_hidden_movies", Context.MODE_PRIVATE)

    fun isHidden(movieId: String): Boolean {
        if (movieId.isBlank()) return false
        val set = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return movieId in set
    }

    fun hideMovie(movieId: String) {
        if (movieId.isBlank()) return
        val set = (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        set.add(movieId)
        prefs.edit().putStringSet("ids", set).apply()
    }

    fun unhideMovie(movieId: String) {
        if (movieId.isBlank()) return
        val set = (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        set.remove(movieId)
        prefs.edit().putStringSet("ids", set).apply()
    }

    fun getHiddenIds(): Set<String> {
        return prefs.getStringSet("ids", emptySet()) ?: emptySet()
    }
}
