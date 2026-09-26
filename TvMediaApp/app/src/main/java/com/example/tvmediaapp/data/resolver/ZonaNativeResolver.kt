package com.example.tvmediaapp.data.resolver

import com.example.tvmediaapp.data.models.StreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ZonaNativeResolver {
    private const val API_BASE = "https://android1.mzona.net/api/v1"
    private const val USER_AGENT = "Zona/1.10.2 (Google/Pixel 5/Android 11)"

    private fun zonaHashCode(s: String): Int {
        var h = 0
        for (c in s) {
            h = (h * 31 + c.code)
        }
        return h
    }

    private suspend fun getClientTime(): Long = withContext(Dispatchers.IO) {
        var dateHdr: String? = null
        try {
            val url = URL("$API_BASE/video/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connect()
            dateHdr = conn.getHeaderField("Date")
            conn.disconnect()
        } catch (_: Exception) {}

        val t: Long = if (!dateHdr.isNullOrEmpty()) {
            try {
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                format.parse(dateHdr)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        } else {
            System.currentTimeMillis()
        }

        val j2 = t / 1000
        val h = Math.abs(zonaHashCode("$j2$USER_AGENT")) % 1000
        (1000 * j2) + h
    }

    suspend fun searchMobiId(title: String, kpId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
            val enc = URLEncoder.encode(cleanTitle, "UTF-8")
            val url = URL("$API_BASE/search/$enc")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(body)
                val items = root.optJSONArray("items") ?: return@withContext null

                if (!kpId.isNullOrBlank()) {
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        val idVal = it.optString("id", "")
                        if (idVal == kpId) {
                            return@withContext it.optString("mobi_link_id", idVal)
                        }
                    }
                }

                if (items.length() > 0) {
                    val first = items.getJSONObject(0)
                    return@withContext first.optString("mobi_link_id", first.optString("id", ""))
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun resolveStreams(mobiId: String): List<StreamOption> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<StreamOption>()
        try {
            val ct = getClientTime()
            val url = URL("$API_BASE/video/$mobiId?client_time=$ct")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connect()
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(body)

                val hqUrl = root.optString("url", "")
                val mqUrl = root.optString("mqUrl", "")
                val lqUrl = root.optString("lqUrl", "")

                if (hqUrl.startsWith("http")) {
                    streams.add(StreamOption(
                        quality = "1080p (Zona HQ)",
                        url = hqUrl,
                        isHls = false,
                        source = "Zona"
                    ))
                }
                if (mqUrl.startsWith("http")) {
                    streams.add(StreamOption(
                        quality = "720p (Zona MQ)",
                        url = mqUrl,
                        isHls = false,
                        source = "Zona"
                    ))
                }
                if (lqUrl.startsWith("http")) {
                    streams.add(StreamOption(
                        quality = "480p (Zona LQ)",
                        url = lqUrl,
                        isHls = false,
                        source = "Zona"
                    ))
                }
            }
        } catch (_: Exception) {}
        streams
    }
}
