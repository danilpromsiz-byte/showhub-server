package com.example.tvmediaapp.data.models

data class StreamOption(
    val quality: String,
    val url: String,
    val isHls: Boolean = true,
    val source: String = ""
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

data class PersonInfo(
    val id: String = "",
    val name: String = "",
    val role: String = "Актер",
    val photoUrl: String = ""
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
    val actors: String = "",
    val episodesInfo: String = "",
    val genres: List<String> = emptyList(),
    val videoUrl: String = "",
    val streams: List<StreamOption> = emptyList(),
    val isSeries: Boolean = false,
    val seasons: List<SeasonInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val cast: List<PersonInfo> = emptyList(),
    val directorsList: List<PersonInfo> = emptyList(),
    val ageRating: String = "",
    val maxQuality: String = "1080p",
    val isFavorite: Boolean = false
)

fun getCountryFlagEmoji(country: String): String {
    val c = country.lowercase().trim()
    return when {
        c.contains("росси") || c.contains("ссср") || c.contains("russia") -> "🇷🇺"
        c.contains("сша") || c.contains("америк") || c.contains("usa") -> "🇺🇸"
        c.contains("коре") || c.contains("korea") -> "🇰🇷"
        c.contains("турц") || c.contains("turkey") -> "🇹🇷"
        c.contains("япон") || c.contains("japan") -> "🇯🇵"
        c.contains("кита") || c.contains("china") -> "🇨🇳"
        c.contains("великобрит") || c.contains("англи") || c.contains("uk") -> "🇬🇧"
        c.contains("франц") || c.contains("france") -> "🇫🇷"
        c.contains("герман") || c.contains("germany") -> "🇩🇪"
        c.contains("итал") || c.contains("italy") -> "🇮🇹"
        c.contains("испан") || c.contains("spain") -> "🇪🇸"
        c.contains("инди") || c.contains("india") -> "🇮🇳"
        c.contains("канад") || c.contains("canada") -> "🇨🇦"
        c.contains("австрал") || c.contains("australia") -> "🇦🇺"
        c.contains("таиланд") || c.contains("тайланд") || c.contains("thailand") -> "🇹🇭"
        c.contains("швеци") || c.contains("sweden") -> "🇸🇪"
        c.contains("мексик") || c.contains("mexico") -> "🇲🇽"
        c.contains("бразил") || c.contains("brazil") -> "🇧🇷"
        c.contains("норвег") || c.contains("norway") -> "🇳🇴"
        c.contains("дани") || c.contains("denmark") -> "🇩🇰"
        c.contains("финлянд") || c.contains("finland") -> "🇫🇮"
        c.contains("польш") || c.contains("poland") -> "🇵🇱"
        c.contains("ирланд") || c.contains("ireland") -> "🇮🇪"
        c.contains("нидерланд") || c.contains("netherlands") || c.contains("голланди") -> "🇳🇱"
        else -> ""
    }
}

data class MovieCategory(
    val id: String,
    val title: String,
    val movies: List<Movie>
)

data class CommentItem(
    val author: String,
    val date: String,
    val rating: String? = null,
    val text: String
)
