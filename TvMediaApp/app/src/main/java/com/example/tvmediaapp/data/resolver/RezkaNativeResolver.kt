package com.example.tvmediaapp.data.resolver

import com.example.tvmediaapp.data.models.StreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

object RezkaNativeResolver {
    private const val BASE_URL = "https://hdrezka-home.tv"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    suspend fun resolveStreams(
        title: String,
        year: String? = null,
        isSeries: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<StreamOption>()
        try {
            val cleanTitle = title.split(":")[0].split(" - ")[0].trim()
            if (cleanTitle.isEmpty()) return@withContext emptyList()

            // 1. Search HDRezka directly from the Android TV's residential IP
            val searchUrl = "$BASE_URL/search/?do=search&subaction=search&q=" + URLEncoder.encode(cleanTitle, "UTF-8")
            val searchHtml = httpGet(searchUrl, "$BASE_URL/") ?: return@withContext emptyList()

            val idMatcher = Pattern.compile("data-id=\"(\\d+)\"").matcher(searchHtml)
            val linkMatcher = Pattern.compile("class=\"b-content__inline_item-link\"[^>]*><a href=\"([^\"]+)\"").matcher(searchHtml)

            if (!idMatcher.find() || !linkMatcher.find()) {
                return@withContext emptyList()
            }

            val dataId = idMatcher.group(1) ?: return@withContext emptyList()
            val rawLink = linkMatcher.group(1) ?: return@withContext emptyList()
            val pageUrl = if (rawLink.startsWith("http")) rawLink else "$BASE_URL$rawLink"

            // 2. Fetch media page to discover translator ID
            val pageHtml = httpGet(pageUrl, "$BASE_URL/") ?: ""
            var transId = "56"
            val trMatcher = Pattern.compile("data-translator_id=\"(\\d+)\"").matcher(pageHtml)
            if (trMatcher.find()) {
                transId = trMatcher.group(1) ?: "56"
            } else {
                val initMatcher = Pattern.compile("initCDN(?:Movies|Series)Events\\(\\s*\\d+\\s*,\\s*(\\d+)").matcher(pageHtml)
                if (initMatcher.find()) {
                    transId = initMatcher.group(1) ?: "56"
                }
            }

            // 3. Request CDN streams via AJAX
            val tNow = System.currentTimeMillis()
            val ajaxUrl = "$BASE_URL/ajax/get_cdn_series/?t=$tNow"
            val postData = StringBuilder()
                .append("id=").append(dataId)
                .append("&translator_id=").append(transId)
                .append("&action=").append(if (isSeries) "get_stream" else "get_movie")

            if (isSeries) {
                postData.append("&season=").append(season).append("&episode=").append(episode)
            }

            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to pageUrl,
                "Content-Type" to "application/x-www-form-urlencoded"
            )

            val ajaxResponse = httpPost(ajaxUrl, postData.toString(), headers) ?: return@withContext emptyList()
            val json = JSONObject(ajaxResponse)
            val streamStr = json.optString("url").ifEmpty { json.optString("streams", "") }

            if (streamStr.length > 5) {
                val parts = streamStr.split(Regex(",\\s*(?=\\[[^\\]]+\\])"))
                for (part in parts) {
                    val m = Pattern.compile("\\[([^\\]]+)\\](.*)").matcher(part)
                    if (m.find()) {
                        val quality = m.group(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: "HD"
                        val urls = m.group(2)?.split(" or ")?.map { it.trim().replace("\\/", "/") }?.filter { it.startsWith("http") } ?: emptyList()
                        val workingUrl = urls.firstOrNull { !it.contains("ukrtelcdn") } ?: urls.firstOrNull()
                        if (workingUrl != null) {
                            val isHls = workingUrl.contains(".m3u8")
                            streams.add(StreamOption(quality = quality, url = workingUrl, isHls = isHls))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        streams
    }

    private fun httpGet(urlStr: String, referer: String? = null): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (referer != null) conn.setRequestProperty("Referer", referer)
            conn.connect()
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpPost(urlStr: String, postData: String, headers: Map<String, String>): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(postData)
                it.flush()
            }
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
