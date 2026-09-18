package com.example.tvmediaapp.data.resolver

import com.example.tvmediaapp.data.models.StreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

object RezkaNativeResolver {
    private val MIRRORS = listOf(
        "https://hdrezka-home.tv",
        "https://rezka.ag",
        "https://hdrezka.me",
        "https://hdrezka-club.com"
    )

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL).also {
        try {
            CookieHandler.setDefault(it)
        } catch (_: Exception) {}
    }

    private val cookieStore = ConcurrentHashMap<String, String>()

    fun isPremiumQuality(quality: String): Boolean {
        val q = quality.lowercase().trim()
        return q.contains("ultra") || q.contains("4k") || q.contains("2160") || q.contains("vip")
    }

    suspend fun resolveStreams(
        title: String,
        year: String? = null,
        isSeries: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        translatorId: String? = null
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val cleanTitle = title.split(":")[0].split(" - ")[0].trim()
        if (cleanTitle.isEmpty()) return@withContext emptyList()

        // Try primary and fallback mirrors
        for (baseUrl in MIRRORS) {
            val result = tryResolveFromMirror(baseUrl, cleanTitle, year, isSeries, season, episode, translatorId)
            if (result.isNotEmpty()) {
                return@withContext result
            }
        }
        emptyList()
    }

    private fun tryResolveFromMirror(
        baseUrl: String,
        cleanTitle: String,
        year: String?,
        isSeries: Boolean,
        season: Int,
        episode: Int,
        translatorId: String? = null
    ): List<StreamOption> {
        val streams = mutableListOf<StreamOption>()
        try {
            // 1. Search HDRezka directly from the Android TV's residential IP
            val searchUrl = "$baseUrl/search/?do=search&subaction=search&q=" + URLEncoder.encode(cleanTitle, "UTF-8")
            val searchHtml = httpGet(searchUrl, "$baseUrl/", baseUrl = baseUrl) ?: return emptyList()

            var dataId: String? = null
            var pageUrl: String? = null

            // Pattern A: Match inside search result items container (avoid top-rated/popular sidebar items)
            val itemMatcher = Pattern.compile("<div class=\"b-content__inline_item\"[^>]*data-id=\"(\\d+)\"[^>]*data-url=\"([^\"]+)\"").matcher(searchHtml)
            if (itemMatcher.find()) {
                dataId = itemMatcher.group(1)
                val rawLink = itemMatcher.group(2) ?: ""
                pageUrl = if (rawLink.startsWith("http")) rawLink else "$baseUrl$rawLink"
            } else {
                // Pattern B: general data-id and data-url
                val genMatcher = Pattern.compile("data-id=\"(\\d+)\"\\s+data-url=\"([^\"]+)\"").matcher(searchHtml)
                if (genMatcher.find()) {
                    dataId = genMatcher.group(1)
                    val rawLink = genMatcher.group(2) ?: ""
                    pageUrl = if (rawLink.startsWith("http")) rawLink else "$baseUrl$rawLink"
                } else {
                    // Pattern C: separate data-id and a-href
                    val idMatcher = Pattern.compile("data-id=\"(\\d+)\"").matcher(searchHtml)
                    val linkMatcher = Pattern.compile("<div class=\"b-content__inline_item-cover\">\\s*<a href=\"([^\"]+)\"").matcher(searchHtml)
                    if (idMatcher.find() && linkMatcher.find()) {
                        dataId = idMatcher.group(1)
                        val rawLink = linkMatcher.group(1) ?: ""
                        pageUrl = if (rawLink.startsWith("http")) rawLink else "$baseUrl$rawLink"
                    }
                }
            }

            if (dataId.isNullOrEmpty() || pageUrl.isNullOrEmpty()) {
                return emptyList()
            }

            // 2. Fetch media page to establish session cookies & discover translator ID
            val pageHtml = httpGet(pageUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
            var transId = translatorId
            if (transId.isNullOrEmpty()) {
                val trMatcher = Pattern.compile("data-translator_id=\"(\\d+)\"").matcher(pageHtml)
                if (trMatcher.find()) {
                    transId = trMatcher.group(1) ?: "56"
                } else {
                    val initMatcher = Pattern.compile("initCDN(?:Movies|Series)Events\\(\\s*\\d+\\s*,\\s*(\\d+)").matcher(pageHtml)
                    if (initMatcher.find()) {
                        transId = initMatcher.group(1) ?: "56"
                    } else {
                        transId = "56"
                    }
                }
            }

            // Determine if the content is a series (by requested flag or page URL)
            val actualIsSeries = isSeries || pageUrl.contains("/series/") || pageUrl.contains("/animation/")

            // 3. Request CDN streams via AJAX
            val tNow = System.currentTimeMillis()
            val ajaxUrl = "$baseUrl/ajax/get_cdn_series/?t=$tNow"
            val postData = StringBuilder()
                .append("id=").append(dataId)
                .append("&translator_id=").append(transId)
                .append("&action=").append(if (actualIsSeries) "get_stream" else "get_movie")

            if (actualIsSeries) {
                postData.append("&season=").append(season).append("&episode=").append(episode)
            }

            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to pageUrl,
                "Content-Type" to "application/x-www-form-urlencoded"
            )

            val ajaxResponse = httpPost(ajaxUrl, postData.toString(), headers, baseUrl = baseUrl) ?: return emptyList()
            if (!ajaxResponse.trim().startsWith("{")) {
                return emptyList()
            }

            val json = JSONObject(ajaxResponse)
            val streamStr = json.optString("url").ifEmpty { json.optString("streams", "") }

            fun parseStreams(raw: String) {
                val parts = raw.split(Regex(",\\s*(?=\\[[^\\]]+\\])"))
                for (part in parts) {
                    val m = Pattern.compile("\\[([^\\]]+)\\](.*)").matcher(part)
                    if (m.find()) {
                        val quality = m.group(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: "HD"
                        val urls = m.group(2)?.split(" or ")?.map { it.trim().replace("\\/", "/") }?.filter { 
                            it.startsWith("http") && !it.contains("rhtie") && !it.contains("trial") && !it.contains("promo")
                        } ?: emptyList()
                        // Prioritize voidboost streams or non-ukrtelcdn direct CDNs
                        val workingUrl = urls.firstOrNull { it.contains("voidboost") }
                            ?: urls.firstOrNull { !it.contains("ukrtelcdn") }
                            ?: urls.firstOrNull()
                        if (workingUrl != null) {
                            val isHls = workingUrl.contains(".m3u8")
                            streams.add(StreamOption(quality = quality, url = workingUrl, isHls = isHls, source = "HDrezka"))
                        }
                    }
                }
            }

            if (streamStr.length > 5) {
                parseStreams(streamStr)
            }

            // Fallback: If translator did not voice this season/episode, retry with default translator "56"
            if (streams.isEmpty() && transId != "56") {
                val fbPostData = StringBuilder()
                    .append("id=").append(dataId)
                    .append("&translator_id=56")
                    .append("&action=").append(if (actualIsSeries) "get_stream" else "get_movie")
                if (actualIsSeries) {
                    fbPostData.append("&season=").append(season).append("&episode=").append(episode)
                }
                val fbResp = httpPost(ajaxUrl, fbPostData.toString(), headers, baseUrl = baseUrl)
                if (fbResp != null && fbResp.trim().startsWith("{")) {
                    val fbJson = JSONObject(fbResp)
                    val fbStreamStr = fbJson.optString("url").ifEmpty { fbJson.optString("streams", "") }
                    if (fbStreamStr.length > 5) {
                        parseStreams(fbStreamStr)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return streams
    }

    private fun httpGet(urlStr: String, referer: String? = null, baseUrl: String, retryAfterAnubis: Boolean = true): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
            if (referer != null) conn.setRequestProperty("Referer", referer)

            // Inject stored cookies
            val cookieHeader = getCookieHeader(urlStr)
            if (cookieHeader.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader)
            }

            conn.connect()
            saveCookies(conn, urlStr)

            val code = conn.responseCode
            val isSuccess = code in 200..399
            val inputStream = if (isSuccess) conn.inputStream else conn.errorStream ?: return null
            val content = readResponseBody(inputStream, conn.contentEncoding)

            // Check if Techaro Anubis challenge was returned
            if (retryAfterAnubis && content.contains("anubis_challenge")) {
                val solved = solveAnubis(content, urlStr, baseUrl)
                if (solved) {
                    return httpGet(urlStr, referer, baseUrl, retryAfterAnubis = false)
                }
            }

            if (isSuccess) content else null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpPost(
        urlStr: String,
        postData: String,
        headers: Map<String, String>,
        baseUrl: String,
        retryAfterAnubis: Boolean = true
    ): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")

            val cookieHeader = getCookieHeader(urlStr)
            if (cookieHeader.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader)
            }

            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(postData)
                it.flush()
            }

            saveCookies(conn, urlStr)

            val code = conn.responseCode
            val isSuccess = code in 200..399
            val inputStream = if (isSuccess) conn.inputStream else conn.errorStream ?: return null
            val content = readResponseBody(inputStream, conn.contentEncoding)

            // Check if Anubis challenged the POST request
            if (retryAfterAnubis && content.contains("anubis_challenge")) {
                val solved = solveAnubis(content, urlStr, baseUrl)
                if (solved) {
                    return httpPost(urlStr, postData, headers, baseUrl, retryAfterAnubis = false)
                }
            }

            if (isSuccess) content else null
        } catch (e: Exception) {
            null
        }
    }

    private fun solveAnubis(html: String, targetUrl: String, baseUrl: String): Boolean {
        return try {
            val chMatcher = Pattern.compile("<script[^>]*id=\"anubis_challenge\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html)
            val prefixMatcher = Pattern.compile("<script[^>]*id=\"anubis_base_prefix\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html)

            if (!chMatcher.find()) return false
            val challengeJsonStr = chMatcher.group(1)?.trim() ?: return false
            val basePrefix = if (prefixMatcher.find()) {
                prefixMatcher.group(1)?.trim()?.replace("\"", "") ?: ""
            } else ""

            val chObj = JSONObject(challengeJsonStr)
            val rules = chObj.getJSONObject("rules")
            val difficulty = rules.optInt("difficulty", 2)
            val challenge = chObj.getJSONObject("challenge")
            val randomData = challenge.getString("randomData")
            val challengeId = challenge.getString("id")

            val startT = System.currentTimeMillis()
            val p = difficulty / 2
            val u = (difficulty % 2) != 0
            var nonce = 0L
            var foundHash: String? = null

            val md = MessageDigest.getInstance("SHA-256")

            while (true) {
                val candidate = "$randomData$nonce".toByteArray(Charsets.UTF_8)
                val digest = md.digest(candidate)

                var valid = true
                for (i in 0 until p) {
                    if (digest[i] != 0.toByte()) {
                        valid = false
                        break
                    }
                }
                if (valid && u && ((digest[p].toInt() ushr 4) and 0x0F) != 0) {
                    valid = false
                }

                if (valid) {
                    foundHash = digest.joinToString("") { "%02x".format(it) }
                    break
                }
                nonce++
            }

            val elapsed = System.currentTimeMillis() - startT
            val passUrl = "$baseUrl$basePrefix/.within.website/x/cmd/anubis/api/pass-challenge" +
                    "?id=" + URLEncoder.encode(challengeId, "UTF-8") +
                    "&response=" + URLEncoder.encode(foundHash, "UTF-8") +
                    "&nonce=" + nonce +
                    "&redir=" + URLEncoder.encode(targetUrl, "UTF-8") +
                    "&elapsedTime=" + elapsed

            // Send clearance request - disable auto redirect so Set-Cookie is never stripped!
            val pConn = URL(passUrl).openConnection() as HttpURLConnection
            pConn.instanceFollowRedirects = false
            pConn.requestMethod = "GET"
            pConn.connectTimeout = 8000
            pConn.readTimeout = 8000
            pConn.setRequestProperty("User-Agent", USER_AGENT)
            pConn.setRequestProperty("Referer", targetUrl)
            val cookieHeader = getCookieHeader(passUrl)
            if (cookieHeader.isNotEmpty()) {
                pConn.setRequestProperty("Cookie", cookieHeader)
            }
            pConn.connect()
            saveCookies(pConn, passUrl)
            pConn.responseCode in 200..399
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun readResponseBody(stream: InputStream, encoding: String?): String {
        val inStream = if (encoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(stream)
        } else {
            stream
        }
        return BufferedReader(InputStreamReader(inStream, "UTF-8")).use { it.readText() }
    }

    private fun saveCookies(conn: HttpURLConnection, requestUrl: String) {
        val headerFields = conn.headerFields ?: return
        try {
            cookieManager.put(URI.create(requestUrl), headerFields)
        } catch (_: Exception) {}

        for ((key, values) in headerFields) {
            if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                for (cookie in values) {
                    val part = cookie.split(";")[0].trim()
                    val eqIdx = part.indexOf('=')
                    if (eqIdx > 0) {
                        val name = part.substring(0, eqIdx).trim()
                        val value = part.substring(eqIdx + 1).trim()
                        cookieStore[name] = value
                    }
                }
            }
        }
    }

    private fun getCookieHeader(requestUrl: String): String {
        val manualCookies = cookieStore.entries.joinToString("; ") { "${it.key}=${it.value}" }
        try {
            val uri = URI.create(requestUrl)
            val managerMap = cookieManager.get(uri, emptyMap())
            val cookieList = managerMap["Cookie"] ?: managerMap["cookie"]
            if (!cookieList.isNullOrEmpty()) {
                val mgrStr = cookieList.joinToString("; ")
                return if (manualCookies.isNotEmpty()) "$manualCookies; $mgrStr" else mgrStr
            }
        } catch (_: Exception) {}
        return manualCookies
    }
}
