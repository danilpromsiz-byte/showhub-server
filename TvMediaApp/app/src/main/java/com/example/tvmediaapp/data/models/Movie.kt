package com.example.tvmediaapp.data.models

data class StreamOption(
    val quality: String,
    val url: String,
    val isHls: Boolean = true
)

data class Movie(
    val id: String,
    val title: String,
    val description: String,
    val posterUrl: String,
    val backdropUrl: String,
    val rating: Double,
    val releaseYear: String,
    val duration: String,
    val genres: List<String>,
    val videoUrl: String,
    val streams: List<StreamOption> = emptyList(),
    val isSeries: Boolean = false
)

data class MovieCategory(
    val id: String,
    val title: String,
    val movies: List<Movie>
)
