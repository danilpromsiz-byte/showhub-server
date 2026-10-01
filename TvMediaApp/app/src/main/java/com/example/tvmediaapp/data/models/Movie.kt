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
    val name: String,
    val episodesCount: Int = 0,
    val source: String = "hdrezka",
    val seasonsEpisodes: Map<Int, Int> = emptyMap()
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

data class SourceInfo(
    val id: String,
    val name: String,
    val episodesCount: Int = 0,
    val seasonsEpisodes: Map<Int, Int> = emptyMap()
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
    val ratingLampa: Double = 0.0,
    val lampaPopularity: Double = 0.0,
    val rankIndex: Int = 0,
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
    val sources: List<SourceInfo> = emptyList(),
    val cast: List<PersonInfo> = emptyList(),
    val directorsList: List<PersonInfo> = emptyList(),
    val episodesSchedule: List<EpisodeScheduleItem> = emptyList(),
    val ageRating: String = "",
    val maxQuality: String = "1080p",
    val isFavorite: Boolean = false,
    val source: String = "",
    val kinopoiskId: String = ""
)

fun getCountryBadge(country: String, genres: List<String> = emptyList(), title: String = ""): String {
    val c = country.lowercase().trim()
    val gStr = genres.joinToString(" ").lowercase()
    val isAsianContent = gStr.contains("дорама") || gStr.contains("аниме") || gStr.contains("аним")
    if (isAsianContent && (c.contains("украин") || c.contains("сша") || c.contains("инди") || c.isEmpty())) {
        return if (gStr.contains("аниме") || gStr.contains("аним")) "🇯🇵 JP" else "🇨🇳 CN"
    }

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
        c.contains("серби") || c.contains("serbia") -> "🇷🇸 RS"
        c.contains("израи") || c.contains("israel") -> "🇮🇱 IL"
        c.contains("аргентин") || c.contains("argentina") -> "🇦🇷 AR"
        c.contains("колумби") || c.contains("colombia") -> "🇨🇴 CO"
        c.contains("португал") || c.contains("portugal") -> "🇵🇹 PT"
        c.contains("исланди") || c.contains("iceland") -> "🇮🇸 IS"
        c.contains("венгри") || c.contains("hungary") -> "🇭🇺 HU"
        c.contains("греци") || c.contains("greece") -> "🇬🇷 GR"
        c.contains("румыни") || c.contains("romania") -> "🇷🇴 RO"
        c.contains("болгари") || c.contains("bulgaria") -> "🇧🇬 BG"
        c.contains("хорвати") || c.contains("croatia") -> "🇭🇷 HR"
        c.contains("словаки") || c.contains("slovakia") -> "🇸🇰 SK"
        c.contains("эстони") || c.contains("estonia") -> "🇪🇪 EE"
        c.contains("латви") || c.contains("latvia") -> "🇱🇻 LV"
        c.contains("литв") || c.contains("lithuania") -> "🇱🇹 LT"
        c.contains("грузи") || c.contains("georgia") -> "🇬🇪 GE"
        c.contains("армени") || c.contains("armenia") -> "🇦🇲 AM"
        c.contains("узбекистан") || c.contains("uzbekistan") -> "🇺🇿 UZ"
        c.contains("новозеланд") || c.contains("new zealand") -> "🇳🇿 NZ"
        c.contains("юар") || c.contains("south africa") -> "🇿🇦 ZA"
        else -> ""
    }
    if (fromCountry.isNotBlank()) return fromCountry

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

fun normalizeMovieTitle(title: String): String {
    if (title.isBlank()) return ""
    var t = title.lowercase().trim()
    t = t.replace(Regex("""\([^)]*\)|\[[^]]*\]|\{[^}]*\}"""), " ")
    t = t.replace(Regex("""\b(\d+)\s*(сезон|сери[йия]|часть)\b"""), " ")
    t = t.replace(Regex("""\b(сезон|серия|часть)\s*(\d+)\b"""), " ")
    t = t.replace(Regex("""\b(сериал|фильм|мультфильм|аниме)\b"""), " ")
    t = t.replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
    return t.replace(Regex("""\s+"""), " ").trim()
}

fun getMovieCanonicalKey(movie: Movie): String {
    val kp = movie.kinopoiskId.trim()
    if (kp.isNotEmpty() && kp != "0" && !kp.equals("null", ignoreCase = true) && !kp.equals("none", ignoreCase = true)) {
        return "kp_$kp"
    }
    val clean = normalizeMovieTitle(movie.title)
    val yr = movie.releaseYear.filter { it.isDigit() }.take(4)
    val sType = if (movie.isSeries) "s" else "m"
    if (clean.isNotEmpty() && yr.isNotEmpty()) {
        return "t_${clean}_${yr}_${sType}"
    }
    if (movie.id.startsWith("tmdb_")) {
        return movie.id
    }
    return if (clean.isNotEmpty()) "t_${clean}_${sType}" else movie.id
}

fun mergeTwoMovies(primary: Movie, secondary: Movie): Movie {
    val bestId = when {
        primary.id.startsWith("http") -> primary.id
        secondary.id.startsWith("http") -> secondary.id
        primary.id.startsWith("tmdb_") -> primary.id
        secondary.id.startsWith("tmdb_") -> secondary.id
        else -> primary.id
    }
    val bestPoster = when {
        primary.posterUrl.isNotBlank() && !primary.posterUrl.contains("noposter") && !primary.posterUrl.contains("no_image") -> primary.posterUrl
        secondary.posterUrl.isNotBlank() && !secondary.posterUrl.contains("noposter") && !secondary.posterUrl.contains("no_image") -> secondary.posterUrl
        primary.posterUrl.isNotBlank() -> primary.posterUrl
        else -> secondary.posterUrl
    }
    val bestBackdrop = when {
        primary.backdropUrl.isNotBlank() && !primary.backdropUrl.contains("noposter") -> primary.backdropUrl
        secondary.backdropUrl.isNotBlank() && !secondary.backdropUrl.contains("noposter") -> secondary.backdropUrl
        else -> primary.backdropUrl
    }
    val bestKpId = when {
        primary.kinopoiskId.isNotBlank() && primary.kinopoiskId != "0" && !primary.kinopoiskId.equals("null", ignoreCase = true) -> primary.kinopoiskId
        secondary.kinopoiskId.isNotBlank() && secondary.kinopoiskId != "0" && !secondary.kinopoiskId.equals("null", ignoreCase = true) -> secondary.kinopoiskId
        else -> ""
    }
    val hasCyr = { s: String -> s.any { it in '\u0400'..'\u04FF' } }
    val bestTitle = when {
        hasCyr(primary.title) && !hasCyr(secondary.title) -> primary.title
        !hasCyr(primary.title) && hasCyr(secondary.title) -> secondary.title
        primary.title.isNotBlank() -> primary.title
        else -> secondary.title
    }
    val bestDesc = if (primary.description.length >= secondary.description.length) primary.description else secondary.description
    val bestYear = if (primary.releaseYear.isNotBlank()) primary.releaseYear else secondary.releaseYear
    val bestRating = maxOf(primary.rating, secondary.rating)
    val bestRatingKp = maxOf(primary.ratingKp, secondary.ratingKp)
    val bestRatingImdb = maxOf(primary.ratingImdb, secondary.ratingImdb)
    val bestRatingLampa = maxOf(primary.ratingLampa, secondary.ratingLampa)
    val bestLampaPop = maxOf(primary.lampaPopularity, secondary.lampaPopularity)
    val combinedGenres = (primary.genres + secondary.genres).distinct()
    val combinedStreams = (primary.streams + secondary.streams).distinctBy { it.url }
    val combinedAudio = (primary.audioTracks + secondary.audioTracks).distinctBy { it.id.ifEmpty { it.name } }
    val combinedSources = (primary.sources + secondary.sources).distinctBy { it.id.ifEmpty { it.name } }
    val bestSeasons = if (primary.seasons.sumOf { it.episodes.size } >= secondary.seasons.sumOf { it.episodes.size }) primary.seasons else secondary.seasons
    val bestCast = if (primary.cast.size >= secondary.cast.size) primary.cast else secondary.cast
    val bestDirectorsList = if (primary.directorsList.size >= secondary.directorsList.size) primary.directorsList else secondary.directorsList
    val bestSched = mergeEpisodeSchedules(primary.episodesSchedule, secondary.episodesSchedule)

    return primary.copy(
        id = bestId,
        title = bestTitle,
        originalTitle = if (primary.originalTitle.isNotBlank()) primary.originalTitle else secondary.originalTitle,
        description = bestDesc,
        posterUrl = bestPoster,
        backdropUrl = bestBackdrop,
        rating = bestRating,
        ratingKp = bestRatingKp,
        ratingImdb = bestRatingImdb,
        ratingLampa = bestRatingLampa,
        lampaPopularity = bestLampaPop,
        releaseYear = bestYear,
        duration = if (primary.duration.isNotBlank()) primary.duration else secondary.duration,
        country = if (primary.country.isNotBlank()) primary.country else secondary.country,
        director = if (primary.director.isNotBlank()) primary.director else secondary.director,
        actors = if (primary.actors.isNotBlank()) primary.actors else secondary.actors,
        episodesInfo = if (primary.episodesInfo.isNotBlank()) primary.episodesInfo else secondary.episodesInfo,
        genres = combinedGenres,
        videoUrl = if (primary.videoUrl.isNotBlank()) primary.videoUrl else secondary.videoUrl,
        streams = combinedStreams,
        isSeries = primary.isSeries || secondary.isSeries,
        seasons = bestSeasons,
        audioTracks = combinedAudio,
        sources = combinedSources,
        cast = bestCast,
        directorsList = bestDirectorsList,
        episodesSchedule = bestSched,
        ageRating = if (primary.ageRating.isNotBlank()) primary.ageRating else secondary.ageRating,
        isFavorite = primary.isFavorite || secondary.isFavorite,
        kinopoiskId = bestKpId
    )
}

fun deduplicateAndMergeMovies(movies: List<Movie>): List<Movie> {
    if (movies.size <= 1) return movies
    val map = LinkedHashMap<String, Movie>()
    for (m in movies) {
        if (m.id.isBlank() && m.title.isBlank()) continue
        val key = getMovieCanonicalKey(m)
        val existing = map[key]
        if (existing != null) {
            map[key] = mergeTwoMovies(existing, m)
        } else {
            val kp = m.kinopoiskId.trim()
            val existingByKp = if (kp.isNotEmpty() && kp != "0" && !kp.equals("null", ignoreCase = true) && !kp.equals("none", ignoreCase = true)) {
                map.values.firstOrNull { it.kinopoiskId.trim() == kp }
            } else null

            if (existingByKp != null) {
                val oldKey = getMovieCanonicalKey(existingByKp)
                map[oldKey] = mergeTwoMovies(existingByKp, m)
            } else {
                map[key] = m
            }
        }
    }
    return map.values.toList()
}

fun mergeEpisodeSchedules(schedA: List<EpisodeScheduleItem>, schedB: List<EpisodeScheduleItem>): List<EpisodeScheduleItem> {
    if (schedA.isEmpty()) return schedB
    if (schedB.isEmpty()) return schedA

    val parseKey = { epStr: String ->
        val sMatch = Regex("""(?:(\d+)\s*(?:сезон|season|s)|s(\d+))""", RegexOption.IGNORE_CASE).find(epStr)
        val eMatch = Regex("""(?:(\d+)\s*(?:серия|эпизод|ep|e|серии)|e(\d+))""", RegexOption.IGNORE_CASE).find(epStr)
        val season = sMatch?.let { it.groupValues[1].toIntOrNull() ?: it.groupValues[2].toIntOrNull() } ?: 1
        val ep = eMatch?.let { it.groupValues[1].toIntOrNull() ?: it.groupValues[2].toIntOrNull() }
            ?: Regex("""\b(\d+)\b""").find(epStr)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        Pair(season, ep)
    }

    val isGoodDate = { d: String ->
        val clean = d.trim().lowercase()
        clean.isNotEmpty() &&
            clean !in setOf("вышла", "доступна", "дата уточняется", "ожидается", "в эфире", "неизвестно") &&
            (listOf("янв", "фев", "мар", "апр", "ма", "июн", "июл", "авг", "сен", "окт", "ноя", "дек").any { clean.contains(it) } ||
                Regex("""\d{4}""").containsMatchIn(clean))
    }

    val mergedMap = LinkedHashMap<Pair<Int, Int>, EpisodeScheduleItem>()
    val allItems = schedA + schedB

    for (item in allItems) {
        val key = parseKey(item.episode)
        val existing = mergedMap[key]
        if (existing == null) {
            mergedMap[key] = item
        } else {
            val bestDate = when {
                isGoodDate(item.date) && !isGoodDate(existing.date) -> item.date
                !isGoodDate(item.date) && isGoodDate(existing.date) -> existing.date
                item.date.isNotBlank() && existing.date.isBlank() -> item.date
                else -> if (item.date.length > existing.date.length) item.date else existing.date
            }
            val bestTitle = when {
                item.title.isNotBlank() && !item.title.matches(Regex("""(?i)Серия\s*\d+""")) && existing.title.matches(Regex("""(?i)Серия\s*\d+""")) -> item.title
                existing.title.isNotBlank() && !existing.title.matches(Regex("""(?i)Серия\s*\d+""")) -> existing.title
                item.title.isNotBlank() -> item.title
                else -> existing.title
            }
            val bestStatus = if (item.status.isNotBlank()) item.status else existing.status
            val bestEpLabel = if (item.episode.contains("сезон") && item.episode.contains("серия")) item.episode else existing.episode

            mergedMap[key] = existing.copy(
                episode = bestEpLabel,
                title = bestTitle,
                date = bestDate,
                status = bestStatus
            )
        }
    }

    return mergedMap.entries
        .sortedWith(compareBy({ it.key.first }, { it.key.second }))
        .map { it.value }
}
