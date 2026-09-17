package com.example.tvmediaapp.data.models

data class StreamOption(
    val quality: String,
    val url: String,
    val isHls: Boolean = true
)

data class EpisodeInfo(
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String = ""
)

data class SeasonInfo(
    val seasonNumber: Int,
    val title: String,
    val episodes: List<EpisodeInfo> = emptyList()
)

data class AudioTrackInfo(
    val id: String,
    val name: String
)

data class Movie(
    val id: String,
    val title: String,
    val originalTitle: String = "",
    val description: String,
    val posterUrl: String,
    val backdropUrl: String,
    val rating: Double,
    val ratingKp: Double = rating,
    val ratingImdb: Double = rating,
    val releaseYear: String,
    val duration: String,
    val country: String = "",
    val director: String = "",
    val genres: List<String> = emptyList(),
    val videoUrl: String = "",
    val streams: List<StreamOption> = emptyList(),
    val isSeries: Boolean = false,
    val seasons: List<SeasonInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val isFavorite: Boolean = false
)

data class MovieCategory(
    val id: String,
    val title: String,
    val movies: List<Movie>
)
