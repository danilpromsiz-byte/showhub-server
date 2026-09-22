package com.example.tvmediaapp.data.resolver

import android.util.Base64
import com.example.tvmediaapp.data.models.AudioTrackInfo
import com.example.tvmediaapp.data.models.StreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

object FilmixNativeResolver {
    private val MIRRORS = listOf(
        "https://filmix.quest",
        "https://filmix.biz",
        "https://filmix.my",
        "https://filmix.tech",
        "https://filmix.life"
    )

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val DEFAULT_ISHIMURA = "4814fae4fd4df74d11c48ceb23c2693c1c55eeef"

    @Volatile
    private var cachedCookies: String? = null
    @Volatile
    private var lastCookieTime = 0L

    private fun getBaseUrl(): String = MIRRORS[0]

    private fun obtainSessionCookies(base: String): String {
        val now = System.currentTimeMillis()
        val existing = cachedCookies
        if (existing != null && (now - lastCookieTime < 1800000L)) {
            return existing
        }

        var minotaursVal = ""
        var filmixNetVal = ""
        try {
            val url = URL("$base/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8")
            conn.connect()

            val headerFields = conn.headerFields
            val setCookies = headerFields["Set-Cookie"] ?: headerFields["set-cookie"]
            if (setCookies != null) {
                for (sc in setCookies) {
                    if (sc.contains("minotaurs=")) {
                        minotaursVal = sc.substringAfter("minotaurs=").substringBefore(";")
                    }
                    if (sc.contains("FILMIXNET=")) {
                        filmixNetVal = sc.substringAfter("FILMIXNET=").substringBefore(";")
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}

        val sb = StringBuilder()
        if (filmixNetVal.isNotEmpty()) sb.append("FILMIXNET=$filmixNetVal; ")
        if (minotaursVal.isNotEmpty()) {
            sb.append("minotaurs=$minotaursVal; ")
            sb.append("alora=$minotaursVal; ")
        }
        sb.append("ishimura=$DEFAULT_ISHIMURA; ")
        sb.append("x-a-key=sinatra; ")

        val res = sb.toString().trim()
        cachedCookies = res
        lastCookieTime = now
        return res
    }

    fun decodeStreamString(raw: String): String {
        if (!raw.startsWith("#2")) return raw
        val s = raw.substring(2)
        val parts = s.split(":<:")
        if (parts.isEmpty()) return raw

        val sb = StringBuilder(parts[0])
        for (i in 1 until parts.size) {
            val p = parts[i]
            if (p.length > 24) {
                sb.append(p.substring(24))
            }
        }
        var b64 = sb.toString().trim().trimEnd('=')
        val pad = b64.length % 4
        if (pad == 2) b64 += "=="
        else if (pad == 3) b64 += "="
        else if (pad == 1) b64 = b64.substring(0, b64.length - 1)

        return try {
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            raw
        }
    }

    suspend fun findPostId(title: String, year: String): String? = withContext(Dispatchers.IO) {
        val cleanT = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
        if (cleanT.isEmpty()) return@withContext null

        for (base in MIRRORS.take(2)) {
            try {
                val encQuery = URLEncoder.encode(cleanT, "UTF-8")
                val searchUrl = URL("$base/api/movies/search?q=$encQuery")
                val conn = searchUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                conn.connect()

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val id = item.optString("id", "")
                        val itemTitle = item.optString("title", "")
                        val itemYear = item.optString("year", "")
                        if (id.isNotEmpty()) {
                            if (year.isNotEmpty() && itemYear.isNotEmpty() && itemYear == year) {
                                return@withContext id
                            }
                            if (itemTitle.contains(cleanT, ignoreCase = true) || cleanT.contains(itemTitle, ignoreCase = true)) {
                                return@withContext id
                            }
                        }
                    }
                    if (arr.length() > 0) {
                        return@withContext arr.getJSONObject(0).optString("id", null)
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    suspend fun resolveStreams(
        movieId: String,
        title: String,
        year: String,
        isSeries: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        audioId: String = ""
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val resultStreams = mutableListOf<StreamOption>()
        val base = getBaseUrl()

        val postId = if (movieId.all { it.isDigit() } && movieId.toIntOrNull() != null && movieId.toInt() > 1000) {
            movieId
        } else {
            findPostId(title, year)
        } ?: return@withContext emptyList()

        for (mirror in listOf(base) + MIRRORS.filter { it != base }) {
            try {
                val cookies = obtainSessionCookies(mirror)
                val playerUrl = URL("$mirror/api/movies/player-data?t=${System.currentTimeMillis()}")
                val conn = playerUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 7000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Referer", "$mirror/")
                conn.setRequestProperty("Origin", mirror)
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                if (cookies.isNotEmpty()) {
                    conn.setRequestProperty("Cookie", cookies)
                }

                val postParams = "post_id=$postId&showfull=true"
                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(postParams)
                    it.flush()
                }

                if (conn.responseCode == 200) {
                    val respBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(respBody)
                    val message = root.optJSONObject("message") ?: continue
                    val translations = message.optJSONObject("translations") ?: continue
                    val videoObj = translations.optJSONObject("video") ?: continue
                    val isPlaylist = translations.optString("pl") == "yes"

                    val cleanAudioId = audioId.trim()
                    val keys = videoObj.keys()
                    var trackIdx = 0

                    while (keys.hasNext()) {
                        val trackName = keys.next()
                        val rawStream = videoObj.optString(trackName, "")
                        val curIdx = trackIdx++

                        if (cleanAudioId.isNotEmpty() && cleanAudioId.all { it.isDigit() }) {
                            if (curIdx.toString() != cleanAudioId && videoObj.length() > 1) {
                                continue
                            }
                        }

                        if (isPlaylist) {
                            try {
                                val decodedUrl = decodeStreamString(rawStream)
                                if (decodedUrl.startsWith("http")) {
                                    val plConn = URL(decodedUrl).openConnection() as HttpURLConnection
                                    plConn.connectTimeout = 6000
                                    plConn.readTimeout = 6000
                                    plConn.setRequestProperty("User-Agent", USER_AGENT)
                                    plConn.setRequestProperty("Referer", "$mirror/")
                                    if (cookies.isNotEmpty()) plConn.setRequestProperty("Cookie", cookies)
                                    if (plConn.responseCode == 200) {
                                        val plText = plConn.inputStream.bufferedReader().use { it.readText() }
                                        val cleanPlText = plText.replace(Regex("<[^>]*>"), "").trim()
                                        val decodedPl = decodeStreamString(cleanPlText)
                                        val endIdx = decodedPl.lastIndexOf("]")
                                        if (endIdx != -1) {
                                            val plJsonStr = decodedPl.substring(0, endIdx + 1)
                                            val plArray = JSONArray(plJsonStr)
                                            val targetS = (season - 1).coerceAtLeast(0)
                                            val targetE = (episode - 1).coerceAtLeast(0)
                                            if (targetS < plArray.length()) {
                                                val sEntry = plArray.getJSONObject(targetS)
                                                val folder = sEntry.optJSONArray("folder")
                                                if (folder != null && targetE < folder.length()) {
                                                    val epEntry = folder.getJSONObject(targetE)
                                                    val fileStr = epEntry.optString("file", "")
                                                    val matcher = Pattern.compile("\\[(.*?)\\](https?://[^\\s,\\[\\]]+)").matcher(fileStr)
                                                    while (matcher.find()) {
                                                        val qual = matcher.group(1) ?: "HD"
                                                        val sUrl = matcher.group(2) ?: ""
                                                        if (sUrl.startsWith("http")) {
                                                            resultStreams.add(
                                                                StreamOption(
                                                                    quality = "$qual ($trackName)",
                                                                    url = sUrl,
                                                                    isHls = sUrl.contains(".m3u8"),
                                                                    source = "Filmix Direct"
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        } else {
                            val decoded = decodeStreamString(rawStream)
                            val matcher = Pattern.compile("\\[(.*?)\\](https?://[^\\s,\\[\\]]+)").matcher(decoded)
                            while (matcher.find()) {
                                val qual = matcher.group(1) ?: "HD"
                                val sUrl = matcher.group(2) ?: ""
                                if (sUrl.startsWith("http")) {
                                    resultStreams.add(
                                        StreamOption(
                                            quality = "$qual ($trackName)",
                                            url = sUrl,
                                            isHls = sUrl.contains(".m3u8"),
                                            source = "Filmix Direct"
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (resultStreams.isNotEmpty()) {
                        break
                    }
                }
            } catch (_: Exception) {}
        }

        resultStreams
    }
}
