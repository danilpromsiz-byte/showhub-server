package com.example.tvmediaapp.data.api

import com.example.tvmediaapp.data.cache.MediaDiskCache
import com.example.tvmediaapp.data.models.AudioTrackInfo
import com.example.tvmediaapp.data.models.CommentItem
import com.example.tvmediaapp.data.models.EpisodeInfo
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.PersonInfo
import com.example.tvmediaapp.data.models.SeasonInfo
import com.example.tvmediaapp.data.models.StreamOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ShowHubApiClient {
    private const val SERVER_BASE = "https://showhub-server.onrender.com"

    suspend fun fetchPopular(): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val url = URL("$SERVER_BASE/api/popular")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.3.0")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                parseMoviesJson(arr, movies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun fetchCatalog(
        category: String = "all",
        genre: String? = null,
        sortBy: String = "newest",
        year: String? = null,
        country: String? = null,
        page: Int = 1,
        excludedCountries: String? = null
    ): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val sb = StringBuilder("$SERVER_BASE/api/catalog?category=$category&page=$page")
            if (!genre.isNullOrEmpty() && genre != "all" && genre != "\u0412\u0441\u0435 \u0436\u0430\u043d\u0440\u044b") {
                sb.append("&genre=").append(URLEncoder.encode(genre, "UTF-8"))
            }
            if (sortBy.isNotEmpty()) {
                sb.append("&sort_by=").append(sortBy)
            }
            if (!year.isNullOrEmpty() && year != "all") {
                sb.append("&year=").append(URLEncoder.encode(year, "UTF-8"))
            }
            if (!country.isNullOrEmpty() && country != "all") {
                sb.append("&country=").append(URLEncoder.encode(country, "UTF-8"))
            }
            if (!excludedCountries.isNullOrEmpty()) {
                sb.append("&excluded_countries=").append(URLEncoder.encode(excludedCountries, "UTF-8"))
            }
            val url = URL(sb.toString())
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.3.0")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                parseMoviesJson(arr, movies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun searchMovies(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        if (query.trim().isEmpty()) return@withContext movies
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("$SERVER_BASE/api/search?q=$q")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 18000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.3.0")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                parseMoviesJson(arr, movies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movies
    }

    suspend fun fetchMediaDetails(movie: Movie): Movie = withContext(Dispatchers.IO) {
        val cached = MediaDiskCache.getCachedDetails(movie.id)
        if (cached != null && (cached.seasons.isNotEmpty() || cached.audioTracks.isNotEmpty() || cached.cast.isNotEmpty())) {
            // Instant 0 ms load from TV disk cache!
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val refreshed = fetchMediaDetailsFromNetwork(movie)
                    MediaDiskCache.putCachedDetails(refreshed)
                } catch (_: Exception) {}
            }
            return@withContext cached
        }
        val result = fetchMediaDetailsFromNetwork(movie)
        MediaDiskCache.putCachedDetails(result)
        result
    }

    private suspend fun fetchMediaDetailsFromNetwork(movie: Movie): Movie = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val urlStr = "$SERVER_BASE/api/media/details?source=hdrezka&media_id=${movie.id}&title=$q&year=${movie.releaseYear}&is_series=${if (movie.isSeries) "1" else "0"}"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/${com.example.tvmediaapp.BuildConfig.VERSION_NAME}")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val obj = JSONObject(body)

                val seasonsList = mutableListOf<SeasonInfo>()
                val sArr = obj.optJSONArray("seasons")
                if (sArr != null) {
                    for (sIdx in 0 until sArr.length()) {
                        val sObj = sArr.getJSONObject(sIdx)
                        val sNum = sObj.optInt("season_number", sObj.optInt("season_id", sIdx + 1))
                        val sTitle = "\u0421\u0435\u0437\u043e\u043d $sNum"
                        val episodesList = mutableListOf<EpisodeInfo>()
                        val epArr = sObj.optJSONArray("episodes")
                        if (epArr != null) {
                            for (eIdx in 0 until epArr.length()) {
                                val epObj = epArr.getJSONObject(eIdx)
                                val eNum = epObj.optInt("episode_number", epObj.optInt("episode_id", eIdx + 1))
                                val eTitle = epObj.optString("title", "\u0421\u0435\u0440\u0438\u044f $eNum")
                                episodesList.add(EpisodeInfo(episodeNumber = eNum, title = eTitle))
                            }
                        }
                        seasonsList.add(SeasonInfo(seasonNumber = sNum, title = sTitle, episodes = episodesList))
                    }
                }

                val audioList = mutableListOf<AudioTrackInfo>()
                val trArr = obj.optJSONArray("translators")
                if (trArr != null) {
                    for (tIdx in 0 until trArr.length()) {
                        val trObj = trArr.optJSONObject(tIdx)
                        if (trObj != null) {
                            val id = trObj.optString("id", tIdx.toString())
                            val name = trObj.optString("name", "")
                            if (name.isNotEmpty()) audioList.add(AudioTrackInfo(id, name))
                        } else {
                            val name = trArr.optString(tIdx, "")
                            if (name.isNotEmpty()) audioList.add(AudioTrackInfo(tIdx.toString(), name))
                        }
                    }
                }

                val castList = mutableListOf<PersonInfo>()
                val cArr = obj.optJSONArray("actors_list") ?: obj.optJSONArray("cast")
                if (cArr != null) {
                    for (cIdx in 0 until cArr.length()) {
                        val cObj = cArr.getJSONObject(cIdx)
                        castList.add(
                            PersonInfo(
                                id = cObj.optString("id", cIdx.toString()),
                                name = cObj.optString("name", ""),
                                role = cObj.optString("role", "\u0410\u043a\u0442\u0435\u0440"),
                                photoUrl = cObj.optString("photo", cObj.optString("photoUrl", ""))
                            )
                        )
                    }
                }

                val dirList = mutableListOf<PersonInfo>()
                val dArr = obj.optJSONArray("directors_list")
                if (dArr != null) {
                    for (dIdx in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(dIdx)
                        dirList.add(
                            PersonInfo(
                                id = dObj.optString("id", dIdx.toString()),
                                name = dObj.optString("name", ""),
                                role = dObj.optString("role", "Режиссёр"),
                                photoUrl = dObj.optString("photo", dObj.optString("photoUrl", ""))
                            )
                        )
                    }
                }

                val ageRating = classifyAgeRating(
                    title = movie.title,
                    desc = obj.optString("description", movie.description),
                    genres = movie.genres,
                    rawAge = obj.optString("age_limit", movie.ageRating)
                )
                val rawPoster = obj.optString("poster", "")
                val updatedPoster = if (rawPoster.startsWith("http") && !rawPoster.contains("no_image") && !rawPoster.contains("noposter")) rawPoster else movie.posterUrl

                val kpRating = obj.optDouble("rating_kp", movie.ratingKp)
                val imdbRating = obj.optDouble("rating_imdb", movie.ratingImdb)
                val rawDirector = obj.optString("director", movie.director)
                val director = if (rawDirector.isBlank() || rawDirector.equals("null", ignoreCase = true)) movie.director else rawDirector
                val rawCountry = obj.optString("country", movie.country)
                val country = if (rawCountry.isBlank() || rawCountry.equals("null", ignoreCase = true)) movie.country else rawCountry
                val rawActors = obj.optString("actors", movie.actors)
                val actors = if (rawActors.isBlank() || rawActors.equals("null", ignoreCase = true)) movie.actors else rawActors
                val rawDesc = obj.optString("description", movie.description).ifEmpty { movie.description }
                val desc = if (rawDesc.isBlank() || rawDesc.equals("null", ignoreCase = true)) movie.description else rawDesc

                return@withContext movie.copy(
                    posterUrl = updatedPoster,
                    backdropUrl = updatedPoster,
                    ratingKp = if (kpRating > 0) kpRating else movie.ratingKp,
                    ratingImdb = if (imdbRating > 0) imdbRating else movie.ratingImdb,
                    director = director,
                    country = country,
                    actors = actors,
                    description = desc,
                    seasons = if (seasonsList.isNotEmpty()) seasonsList else movie.seasons,
                    audioTracks = if (audioList.isNotEmpty()) audioList else movie.audioTracks,
                    cast = if (castList.isNotEmpty()) castList else movie.cast,
                    directorsList = if (dirList.isNotEmpty()) dirList else movie.directorsList,
                    ageRating = ageRating
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        movie
    }

    suspend fun fetchStreams(
        movie: Movie,
        season: Int? = null,
        episode: Int? = null,
        audioId: String? = null,
        source: String? = null
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val cached = MediaDiskCache.getCachedStreams(movie.id, season, episode, audioId)
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }
        val directStreams = mutableListOf<StreamOption>()
        val embedStreams = mutableListOf<StreamOption>()
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val isSeriesStr = if (movie.isSeries) "1" else "0"
            val srcParam = if (source.isNullOrEmpty()) "all" else source.lowercase().trim()
            val sb = StringBuilder("$SERVER_BASE/api/media/streams?source=$srcParam&media_id=${movie.id}&kp_id=${movie.id}&title=$q&year=${movie.releaseYear}&is_series=$isSeriesStr")
            if (season != null) sb.append("&season=$season")
            if (episode != null) sb.append("&episode=$episode")
            if (!audioId.isNullOrEmpty()) sb.append("&audio_id=").append(URLEncoder.encode(audioId, "UTF-8"))

            val conn = URL(sb.toString()).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.6.7")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(body)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val src = keys.next()
                    val srcObj = root.optJSONObject(src) ?: continue
                    val strArr = srcObj.optJSONArray("streams")
                    if (strArr != null) {
                        for (i in 0 until strArr.length()) {
                            val s = strArr.getJSONObject(i)
                            val qStr = s.optString("quality", src.uppercase())
                            val uStr = s.optString("url", "")
                            val sType = s.optString("stream_type", "")
                            if (uStr.startsWith("http") && !uStr.contains("rhtie.mp4")) {
                                val isDirect = sType == "hls" || sType == "mp4" || sType == "torrent" ||
                                        uStr.contains(".m3u8") || uStr.contains(".mp4") || uStr.contains("voidboost") ||
                                        uStr.contains("/stream?link=")
                                val sourceName = if (sType == "torrent" || src.equals("torrents", ignoreCase = true)) "Торренты (TorrServe)" else src.replaceFirstChar { it.uppercase() }
                                if (isDirect) {
                                    directStreams.add(
                                        StreamOption(
                                            quality = if (sType == "torrent") "P2P $qStr" else qStr,
                                            url = uStr,
                                            isHls = uStr.contains(".m3u8"),
                                            source = sourceName
                                        )
                                    )
                                } else {
                                    embedStreams.add(
                                        StreamOption(
                                            quality = qStr,
                                            url = uStr,
                                            isHls = false,
                                            source = sourceName
                                        )
                                    )
                                }
                            }
                        }
                    }
                    val embedUrl = srcObj.optString("embed_url", "")
                    if (embedUrl.startsWith("http") && directStreams.none { it.url == embedUrl } && embedStreams.none { it.url == embedUrl }) {
                        val label = when (src) {
                            "videocdn" -> "VideoCDN Player (1080p)"
                            "bazon" -> "Bazon Player (HD)"
                            "delivembd" -> "Delivembd Player"
                            else -> "${src.replaceFirstChar { it.uppercase() }} Player"
                        }
                        embedStreams.add(
                            StreamOption(
                                quality = label,
                                url = embedUrl,
                                isHls = false,
                                source = src.replaceFirstChar { it.uppercase() }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val result = mutableListOf<StreamOption>()
        result.addAll(directStreams)
        result.addAll(embedStreams)
        if (result.isNotEmpty()) {
            MediaDiskCache.putCachedStreams(movie.id, season, episode, audioId, result)
        }
        result
    }

    suspend fun fetchTrailerUrl(movie: Movie): String? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val url = URL("$SERVER_BASE/api/media/trailer?title=$q&year=${movie.releaseYear}&kp_id=${movie.id}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.6.3")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val json = JSONObject(body)
                val target = json.optString("web_url", json.optString("embed_url", json.optString("app_url", "")))
                if (target.isNotEmpty()) return@withContext target
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun fetchPreviewStream(movie: Movie, startMin: Int? = null): String? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val isSeries = if (movie.isSeries) "1" else "0"
            val sb = StringBuilder("$SERVER_BASE/api/media/preview-stream?title=$q&media_id=${movie.id}&kp_id=${movie.id}&year=${movie.releaseYear}&is_series=$isSeries")
            if (startMin != null && startMin > 0) {
                sb.append("&start_min=").append(startMin)
            }
            val url = URL(sb.toString())
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.7.3")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val obj = JSONObject(body)
                if (obj.optBoolean("success", false)) {
                    val streamType = obj.optString("stream_type", "").lowercase()
                    val streamUrl = obj.optString("stream_url", "")
                    if (streamUrl.startsWith("http") && (streamType == "hls" || streamType == "mp4" || streamUrl.contains(".m3u8") || streamUrl.contains(".mp4") || streamUrl.contains("voidboost"))) {
                        if (!streamUrl.contains("youtube") && !streamUrl.contains("embed")) {
                            return@withContext streamUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // silent fallback
        }
        null
    }

    suspend fun fetchComments(movie: Movie): List<CommentItem> = withContext(Dispatchers.IO) {
        val comments = mutableListOf<CommentItem>()
        try {
            val q = URLEncoder.encode(movie.title, "UTF-8")
            val url = URL("$SERVER_BASE/api/media/comments?source=filmix&media_id=${movie.id}&title=$q")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.5.2")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val cObj = arr.getJSONObject(i)
                    val author = cObj.optString("author", "Зритель")
                    val date = cObj.optString("date", "")
                    val text = cObj.optString("text", "")
                    val rating = cObj.optString("rating", "")
                    if (text.isNotEmpty()) {
                        comments.add(
                            CommentItem(
                                author = author,
                                date = date,
                                rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                text = text
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        comments
    }



    fun classifyAgeRating(title: String, desc: String, genres: List<String>, rawAge: String): String {
        val cleanRaw = rawAge.trim().uppercase()
        if (cleanRaw in listOf("18+", "18", "R", "NC-17", "R-18", "X")) return "18+"
        val fullTxt = "$title $desc ${genres.joinToString(" ")}".lowercase()
        val r18 = listOf(
            "18+", "18 плюс", "парфюмер", "история одного убийцы", "убийц", "убийств", "маньяк",
            "расчлен", "потрошител", "кровав", "резня", "снафф", "пытки", "пыток", "бойня",
            "эротик", "порно", "секс", "интим", "разврат", "наркоти", "кокаин", "героин",
            "ужасы", "хоррор", "slasher", "слэшер"
        )
        if (r18.any { fullTxt.contains(it) }) return "18+"
        val r16 = listOf(
            "16+", "16 плюс", "боевик", "детектив", "криминал", "триллер", "война", "военный",
            "мистика", "зомби", "ограблен", "перестрелк", "мафия", "банда", "бандит"
        )
        if (r16.any { fullTxt.contains(it) }) return "16+"
        val r6 = listOf("мультфильм", "детский", "семейный", "сказка", "0+", "6+", "6 плюс")
        if (r6.any { fullTxt.contains(it) }) return "6+"
        val r12 = listOf("12+", "12 плюс", "комедия", "фантастика", "фэнтези", "приключения", "мелодрама", "спорт")
        if (r12.any { fullTxt.contains(it) }) return "12+"
        return if (cleanRaw.contains("+")) cleanRaw else "12+"
    }

    private fun parseMoviesJson(arr: JSONArray, outList: MutableList<Movie>) {
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val id = it.optString("id", i.toString())
            val rawTitle = it.optString("title", "").ifEmpty { it.optString("original_title", "") }
            var title = if (rawTitle.isBlank() || rawTitle.equals("null", ignoreCase = true)) "" else rawTitle
            val rawOrig = it.optString("original_title", "")
            val orig = if (rawOrig.isBlank() || rawOrig.equals("null", ignoreCase = true)) "" else rawOrig
            if (title.contains("\ufffd") || title.trim().isEmpty() || title.contains("???")) {
                if (orig.isNotEmpty() && !orig.contains("\ufffd") && !orig.contains("???")) {
                    title = orig
                } else {
                    continue
                }
            }

            val rawDesc = it.optString("description", "")
            val desc = if (rawDesc.isBlank() || rawDesc.equals("null", ignoreCase = true)) "" else rawDesc
            val poster = it.optString("poster", "")
            val posterUrl = if (poster.startsWith("http")) poster else if (poster.isNotEmpty()) "$SERVER_BASE$poster" else "https://avatars.mds.yandex.net/get-kinopoisk-image/10592371/4c676451-f7ea-4d89-9d5a-bf98b1eb7980/600x900"
            val kpRating = if (it.has("rating_kp") && !it.isNull("rating_kp")) it.optDouble("rating_kp", 7.5) else 0.0
            val imdbRating = if (it.has("rating_imdb") && !it.isNull("rating_imdb")) it.optDouble("rating_imdb", 7.2) else 0.0
            val rating = if (kpRating > 0) kpRating else if (imdbRating > 0) imdbRating else it.optDouble("rating", 7.5)
            val rawYear = it.optString("year", "2024").replace("null", "").trim()
            val year = if (rawYear.isNotEmpty()) rawYear else "2024"
            val isSeries = it.optBoolean("is_series", false)
            val extraObj = it.optJSONObject("extra_data") ?: it.optJSONObject("extra") ?: it.optJSONObject("material_data")

            val rawCountry = it.optString("country", "").ifEmpty {
                extraObj?.optString("country", "") ?: ""
            }.ifEmpty {
                val cArr = extraObj?.optJSONArray("countries") ?: it.optJSONArray("countries")
                if (cArr != null && cArr.length() > 0) cArr.getString(0) else ""
            }
            val country = if (rawCountry.isBlank() || rawCountry.equals("null", ignoreCase = true)) "" else rawCountry

            val rawDirector = it.optString("director", "").ifEmpty {
                extraObj?.optString("director", "") ?: ""
            }.ifEmpty {
                val dArr = extraObj?.optJSONArray("directors") ?: it.optJSONArray("directors")
                if (dArr != null && dArr.length() > 0) dArr.getString(0) else ""
            }
            val director = if (rawDirector.isBlank() || rawDirector.equals("null", ignoreCase = true)) "" else rawDirector

            val rawActors = it.optString("actors", "").ifEmpty {
                extraObj?.optString("actors", "") ?: ""
            }.ifEmpty {
                val aArr = extraObj?.optJSONArray("actors") ?: it.optJSONArray("actors")
                if (aArr != null && aArr.length() > 0) {
                    val sb = StringBuilder()
                    for (ai in 0 until minOf(aArr.length(), 6)) {
                        if (sb.isNotEmpty()) sb.append(", ")
                        sb.append(aArr.getString(ai))
                    }
                    sb.toString()
                } else ""
            }
            val actors = if (rawActors.isBlank() || rawActors.equals("null", ignoreCase = true)) "" else rawActors

            val rawEpisodesInfo = extraObj?.optString("episodes_info", "") ?: it.optString("episodes_info", "")
            val episodesInfo = if (rawEpisodesInfo.isBlank() || rawEpisodesInfo.equals("null", ignoreCase = true)) "" else rawEpisodesInfo

            val genresList = mutableListOf<String>()
            val gArr = it.optJSONArray("genres") ?: extraObj?.optJSONArray("genres")
            if (gArr != null) {
                for (g in 0 until gArr.length()) genresList.add(gArr.getString(g))
            } else {
                val gStr = extraObj?.optString("genre", "") ?: it.optString("genre", "")
                if (gStr.isNotEmpty()) genresList.addAll(gStr.split(",").map { s -> s.trim() })
            }

            val givenAge = it.optString("age_limit", extraObj?.optString("age_limit", "") ?: "")
            val rawAge = classifyAgeRating(title, desc, genresList, givenAge)

            outList.add(
                Movie(
                    id = id,
                    title = title,
                    originalTitle = orig,
                    description = desc,
                    posterUrl = posterUrl,
                    backdropUrl = posterUrl,
                    rating = rating,
                    ratingKp = if (kpRating > 0) kpRating else rating,
                    ratingImdb = if (imdbRating > 0) imdbRating else rating,
                    releaseYear = year,
                    duration = if (isSeries) "\u0421\u0435\u0440\u0438\u0430\u043b" else "\u0424\u0438\u043b\u044c\u043c",
                    country = country,
                    director = director,
                    actors = actors,
                    episodesInfo = episodesInfo,
                    genres = if (genresList.isNotEmpty()) genresList else listOf("\u041a\u0438\u043d\u043e"),
                    videoUrl = "",
                    isSeries = isSeries,
                    ageRating = rawAge
                )
            )
        }
    }

    suspend fun sendBugReport(
        reportText: String,
        category: String = "general",
        currentScreen: String = "settings"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SERVER_BASE/api/feedback/bug-report")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.6.8")

            val payload = JSONObject().apply {
                put("text", reportText.trim())
                put("category", category)
                put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                put("app_version", "2.6.8")
                put("version_code", 47)
                put("current_screen", currentScreen)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getOrCreateDeviceId(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("showhub_prefs", android.content.Context.MODE_PRIVATE)
        var id = prefs.getString("pref_device_id", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("pref_device_id", id).apply()
        }
        return id
    }

    suspend fun pingAndGetUserStats(context: android.content.Context, appVersion: String): UserStats? = withContext(Dispatchers.IO) {
        try {
            val deviceId = getOrCreateDeviceId(context)
            val url = URL("$SERVER_BASE/api/analytics/ping?device_id=$deviceId&version=$appVersion")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/$appVersion")
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val json = JSONObject(body)
                UserStats(
                    totalUsers = json.optInt("total_users", 0),
                    activeToday = json.optInt("active_today", 0),
                    activeMonth = json.optInt("active_month", 0)
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class UserStats(
    val totalUsers: Int = 0,
    val activeToday: Int = 0,
    val activeMonth: Int = 0
)
