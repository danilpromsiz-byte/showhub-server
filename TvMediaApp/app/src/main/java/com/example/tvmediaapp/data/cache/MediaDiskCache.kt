package com.example.tvmediaapp.data.cache

import android.content.Context
import android.util.Log
import com.example.tvmediaapp.data.models.AudioTrackInfo
import com.example.tvmediaapp.data.models.EpisodeInfo
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.PersonInfo
import com.example.tvmediaapp.data.models.SeasonInfo
import com.example.tvmediaapp.data.models.StreamOption
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MediaDiskCache {
    private const val TAG = "MediaDiskCache"
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000L // 30 days
    private var cacheDir: File? = null

    fun init(context: Context) {
        val base = File(context.applicationContext.filesDir, "showhub_media_cache")
        if (!base.exists()) {
            base.mkdirs()
        }
        cacheDir = base
    }

    private fun getDetailsDir(): File {
        val dir = File(cacheDir, "details")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getStreamsDir(): File {
        val dir = File(cacheDir, "streams")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTitleKey(title: String, year: String?): String {
        val clean = (title + "_" + (year ?: "")).lowercase().replace(Regex("[^a-zа-я0-9]"), "_")
        return "t_${clean.take(80)}"
    }

    fun getCachedDetails(movieId: String, title: String? = null, year: String? = null): Movie? {
        return try {
            val safeId = movieId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            var file = File(getDetailsDir(), "$safeId.json")
            if (!file.exists() && !title.isNullOrBlank()) {
                val tKey = getTitleKey(title, year)
                file = File(getDetailsDir(), "$tKey.json")
            }
            if (!file.exists()) return null
            if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
                file.delete()
                return null
            }
            val content = file.readText()
            val obj = JSONObject(content)
            deserializeMovie(obj)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached details for $movieId: ${e.message}")
            null
        }
    }

    fun putCachedDetails(movie: Movie) {
        try {
            val safeId = movie.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val json = serializeMovie(movie)
            val jsonStr = json.toString()
            File(getDetailsDir(), "$safeId.json").writeText(jsonStr)
            if (movie.title.isNotBlank()) {
                val tKey = getTitleKey(movie.title, movie.releaseYear)
                File(getDetailsDir(), "$tKey.json").writeText(jsonStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write cached details for ${movie.id}: ${e.message}")
        }
    }

    fun getCachedStreams(movieId: String, season: Int?, episode: Int?, audioId: String?): List<StreamOption>? {
        return try {
            val safeId = movieId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val key = "${safeId}_s${season ?: 0}_e${episode ?: 0}_a${audioId ?: "def"}"
            val file = File(getStreamsDir(), "$key.json")
            if (!file.exists()) return null
            val content = file.readText()
            val arr = JSONArray(content)
            val list = mutableListOf<StreamOption>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    StreamOption(
                        quality = o.optString("quality", "HD"),
                        url = o.optString("url", ""),
                        isHls = o.optBoolean("isHls", true),
                        source = o.optString("source", "")
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (e: Exception) {
            null
        }
    }

    fun putCachedStreams(movieId: String, season: Int?, episode: Int?, audioId: String?, streams: List<StreamOption>) {
        try {
            if (streams.isEmpty()) return
            val safeId = movieId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val key = "${safeId}_s${season ?: 0}_e${episode ?: 0}_a${audioId ?: "def"}"
            val file = File(getStreamsDir(), "$key.json")
            val arr = JSONArray()
            for (s in streams) {
                arr.put(
                    JSONObject().apply {
                        put("quality", s.quality)
                        put("url", s.url)
                        put("isHls", s.isHls)
                        put("source", s.source)
                    }
                )
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache streams: ${e.message}")
        }
    }

    private fun serializeMovie(movie: Movie): JSONObject {
        return JSONObject().apply {
            put("id", movie.id)
            put("title", movie.title)
            put("originalTitle", movie.originalTitle)
            put("description", movie.description)
            put("posterUrl", movie.posterUrl)
            put("backdropUrl", movie.backdropUrl)
            put("rating", movie.rating)
            put("ratingKp", movie.ratingKp)
            put("ratingImdb", movie.ratingImdb)
            put("releaseYear", movie.releaseYear)
            put("duration", movie.duration)
            put("country", movie.country)
            put("director", movie.director)
            put("actors", movie.actors)
            put("episodesInfo", movie.episodesInfo)
            put("isSeries", movie.isSeries)
            put("ageRating", movie.ageRating)

            val gArr = JSONArray()
            movie.genres.forEach { gArr.put(it) }
            put("genres", gArr)

            val sArr = JSONArray()
            movie.seasons.forEach { s ->
                val so = JSONObject().apply {
                    put("seasonNumber", s.seasonNumber)
                    put("title", s.title)
                    val epArr = JSONArray()
                    s.episodes.forEach { ep ->
                        epArr.put(
                            JSONObject().apply {
                                put("episodeNumber", ep.episodeNumber)
                                put("title", ep.title)
                                put("streamUrl", ep.streamUrl)
                            }
                        )
                    }
                    put("episodes", epArr)
                }
                sArr.put(so)
            }
            put("seasons", sArr)

            val tArr = JSONArray()
            movie.audioTracks.forEach { t ->
                val to = JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("episodesCount", t.episodesCount)
                    put("source", t.source)
                    if (t.seasonsEpisodes.isNotEmpty()) {
                        val seObj = JSONObject()
                        t.seasonsEpisodes.forEach { (sNum, epCount) ->
                            seObj.put(sNum.toString(), epCount)
                        }
                        put("seasonsEpisodes", seObj)
                    }
                }
                tArr.put(to)
            }
            put("audioTracks", tArr)

            val srcArr = JSONArray()
            movie.sources.forEach { s ->
                val so = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("episodesCount", s.episodesCount)
                    if (s.seasonsEpisodes.isNotEmpty()) {
                        val seObj = JSONObject()
                        s.seasonsEpisodes.forEach { (sNum, epCount) ->
                            seObj.put(sNum.toString(), epCount)
                        }
                        put("seasonsEpisodes", seObj)
                    }
                }
                srcArr.put(so)
            }
            put("sources", srcArr)

            val cArr = JSONArray()
            movie.cast.forEach { c ->
                cArr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("role", c.role)
                        put("photoUrl", c.photoUrl)
                    }
                )
            }
            put("cast", cArr)

            val dArr = JSONArray()
            movie.directorsList.forEach { d ->
                dArr.put(
                    JSONObject().apply {
                        put("id", d.id)
                        put("name", d.name)
                        put("role", d.role)
                        put("photoUrl", d.photoUrl)
                    }
                )
            }
            put("directorsList", dArr)

            val schArr = JSONArray()
            movie.episodesSchedule.forEach { s ->
                schArr.put(
                    JSONObject().apply {
                        put("episode", s.episode)
                        put("title", s.title)
                        put("date", s.date)
                        put("status", s.status)
                    }
                )
            }
            put("episodesSchedule", schArr)
        }
    }

    private fun deserializeMovie(obj: JSONObject): Movie {
        val genres = mutableListOf<String>()
        val gArr = obj.optJSONArray("genres")
        if (gArr != null) {
            for (i in 0 until gArr.length()) genres.add(gArr.optString(i))
        }

        val seasons = mutableListOf<SeasonInfo>()
        val sArr = obj.optJSONArray("seasons")
        if (sArr != null) {
            for (i in 0 until sArr.length()) {
                val so = sArr.getJSONObject(i)
                val eps = mutableListOf<EpisodeInfo>()
                val epArr = so.optJSONArray("episodes")
                if (epArr != null) {
                    for (j in 0 until epArr.length()) {
                        val eo = epArr.getJSONObject(j)
                        eps.add(
                            EpisodeInfo(
                                episodeNumber = eo.optInt("episodeNumber", j + 1),
                                title = eo.optString("title", ""),
                                streamUrl = eo.optString("streamUrl", "")
                            )
                        )
                    }
                }
                seasons.add(
                    SeasonInfo(
                        seasonNumber = so.optInt("seasonNumber", i + 1),
                        title = so.optString("title", "Сезон ${i + 1}"),
                        episodes = eps
                    )
                )
            }
        }

        val audioTracks = mutableListOf<AudioTrackInfo>()
        val tArr = obj.optJSONArray("audioTracks")
        if (tArr != null) {
            for (i in 0 until tArr.length()) {
                val to = tArr.getJSONObject(i)
                val seMap = mutableMapOf<Int, Int>()
                val seObj = to.optJSONObject("seasonsEpisodes")
                if (seObj != null) {
                    val keys = seObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val sNum = k.toIntOrNull()
                        if (sNum != null) {
                            seMap[sNum] = seObj.optInt(k, 0)
                        }
                    }
                }
                audioTracks.add(
                    AudioTrackInfo(
                        id = to.optString("id", i.toString()),
                        name = to.optString("name", ""),
                        episodesCount = to.optInt("episodesCount", 0),
                        source = to.optString("source", "hdrezka"),
                        seasonsEpisodes = seMap
                    )
                )
            }
        }

        val sources = mutableListOf<com.example.tvmediaapp.data.models.SourceInfo>()
        val srcArr = obj.optJSONArray("sources")
        if (srcArr != null) {
            for (i in 0 until srcArr.length()) {
                val so = srcArr.getJSONObject(i)
                val seMap = mutableMapOf<Int, Int>()
                val seObj = so.optJSONObject("seasonsEpisodes")
                if (seObj != null) {
                    val keys = seObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val sNum = k.toIntOrNull()
                        if (sNum != null) {
                            seMap[sNum] = seObj.optInt(k, 0)
                        }
                    }
                }
                sources.add(
                    com.example.tvmediaapp.data.models.SourceInfo(
                        id = so.optString("id", i.toString()),
                        name = so.optString("name", ""),
                        episodesCount = so.optInt("episodesCount", 0),
                        seasonsEpisodes = seMap
                    )
                )
            }
        }

        val cast = mutableListOf<PersonInfo>()
        val cArr = obj.optJSONArray("cast")
        if (cArr != null) {
            for (i in 0 until cArr.length()) {
                val co = cArr.getJSONObject(i)
                cast.add(
                    PersonInfo(
                        id = co.optString("id", i.toString()),
                        name = co.optString("name", ""),
                        role = co.optString("role", "Актер"),
                        photoUrl = co.optString("photoUrl", "")
                    )
                )
            }
        }

        val directors = mutableListOf<PersonInfo>()
        val dArr = obj.optJSONArray("directorsList")
        if (dArr != null) {
            for (i in 0 until dArr.length()) {
                val dObj = dArr.getJSONObject(i)
                directors.add(
                    PersonInfo(
                        id = dObj.optString("id", i.toString()),
                        name = dObj.optString("name", ""),
                        role = dObj.optString("role", "Режиссер"),
                        photoUrl = dObj.optString("photoUrl", "")
                    )
                )
            }
        }

        val schedule = mutableListOf<com.example.tvmediaapp.data.models.EpisodeScheduleItem>()
        val schArr = obj.optJSONArray("episodesSchedule")
        if (schArr != null) {
            for (i in 0 until schArr.length()) {
                val so = schArr.getJSONObject(i)
                schedule.add(
                    com.example.tvmediaapp.data.models.EpisodeScheduleItem(
                        episode = so.optString("episode", ""),
                        title = so.optString("title", ""),
                        date = so.optString("date", ""),
                        status = so.optString("status", "")
                    )
                )
            }
        }

        return Movie(
            id = obj.optString("id", ""),
            title = obj.optString("title", ""),
            originalTitle = obj.optString("originalTitle", ""),
            description = obj.optString("description", ""),
            posterUrl = obj.optString("posterUrl", ""),
            backdropUrl = obj.optString("backdropUrl", ""),
            rating = obj.optDouble("rating", 7.0),
            ratingKp = obj.optDouble("ratingKp", 7.0),
            ratingImdb = obj.optDouble("ratingImdb", 7.0),
            releaseYear = obj.optString("releaseYear", "2024"),
            duration = obj.optString("duration", "Фильм"),
            country = obj.optString("country", ""),
            director = obj.optString("director", ""),
            actors = obj.optString("actors", ""),
            episodesInfo = obj.optString("episodesInfo", ""),
            genres = genres,
            isSeries = obj.optBoolean("isSeries", false),
            seasons = seasons,
            audioTracks = audioTracks,
            sources = sources,
            cast = cast,
            directorsList = directors,
            episodesSchedule = schedule,
            ageRating = obj.optString("ageRating", "12+")
        )
    }
}
