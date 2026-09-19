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

data class EpisodeScheduleItem(
    val episode: String = "",
    val title: String = "",
    val date: String = "",
    val status: String = ""
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
    val episodesSchedule: List<EpisodeScheduleItem> = emptyList(),
    val ageRating: String = "",
    val maxQuality: String = "1080p",
    val isFavorite: Boolean = false
)

fun getCountryBadge(country: String, genres: List<String> = emptyList(), title: String = ""): String {
    val c = country.lowercase().trim()
    val fromCountry = when {
        c.contains("росси") || c.contains("ссср") || c.contains("russia") -> "🇷🇺 RU"
        c.contains("сша") || c.contains("америк") || c.contains("usa") -> "🇺🇸 US"
        c.contains("коре") || c.contains("korea") -> "🇰🇷 KR"
        c.contains("турц") || c.contains("turkey") -> "🇹🇷 TR"
        c.contains("япон") || c.contains("japan") -> "🇯🇵 JP"
        c.contains("кита") || c.contains("china") -> "🇨🇳 CN"
        c.contains("великобрит") || c.contains("англи") || c.contains("uk") -> "🇬🇧 UK"
        c.contains("франц") || c.contains("france") -> "🇫🇷 FR"
        c.contains("герман") || c.contains("germany") -> "🇩🇪 DE"
        c.contains("итал") || c.contains("italy") -> "🇮🇹 IT"
        c.contains("испан") || c.contains("spain") -> "🇪🇸 ES"
        c.contains("инди") || c.contains("india") -> "🇮🇳 IN"
        c.contains("канад") || c.contains("canada") -> "🇨🇦 CA"
        c.contains("австрал") || c.contains("australia") -> "🇦🇺 AU"
        c.contains("таиланд") || c.contains("тайланд") || c.contains("thailand") -> "🇹🇭 TH"
        c.contains("швеци") || c.contains("sweden") -> "🇸🇪 SE"
        c.contains("мексик") || c.contains("mexico") -> "🇲🇽 MX"
        c.contains("бразил") || c.contains("brazil") -> "🇧🇷 BR"
        c.contains("норвег") || c.contains("norway") -> "🇳🇴 NO"
        c.contains("дани") || c.contains("denmark") -> "🇩🇰 DK"
        c.contains("финлянд") || c.contains("finland") -> "🇫🇮 FI"
        c.contains("польш") || c.contains("poland") -> "🇵🇱 PL"
        c.contains("ирланд") || c.contains("ireland") -> "🇮🇪 IE"
        c.contains("нидерланд") || c.contains("netherlands") || c.contains("голланди") -> "🇳🇱 NL"
        c.contains("бельги") || c.contains("belgium") -> "🇧🇪 BE"
        c.contains("швейцар") || c.contains("switzerland") -> "🇨🇭 CH"
        c.contains("австри") || c.contains("austria") -> "🇦🇹 AT"
        c.contains("чехи") || c.contains("czech") -> "🇨🇿 CZ"
        c.contains("украин") || c.contains("ukraine") -> "🇺🇦 UA"
        c.contains("казах") || c.contains("kazakhstan") -> "🇰🇿 KZ"
        c.contains("беларус") || c.contains("belarus") -> "🇧🇾 BY"
        country.isNotBlank() && country.length >= 2 -> country.take(3).uppercase()
        else -> ""
    }
    if (fromCountry.isNotBlank()) return fromCountry

    val gStr = genres.joinToString(" ").lowercase()
    val tStr = title.lowercase()
    return when {
        gStr.contains("аним") || tStr.contains("титан") || tStr.contains("клинок") || tStr.contains("магическ") || tStr.contains("перекур") -> "🇯🇵 JP"
        gStr.contains("дорама") -> "🇰🇷 KR"
        gStr.contains("турецк") -> "🇹🇷 TR"
        gStr.contains("индийск") -> "🇮🇳 IN"
        tStr.contains("богатыр") || tStr.contains("чебурашка") -> "🇷🇺 RU"
        else -> ""
    }
}

fun getCountryFlagEmoji(country: String, genres: List<String> = emptyList(), title: String = ""): String {
    val badge = getCountryBadge(country, genres, title)
    return if (badge.length >= 2 && badge[0].isSurrogate()) {
        badge.take(2)
    } else ""
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
