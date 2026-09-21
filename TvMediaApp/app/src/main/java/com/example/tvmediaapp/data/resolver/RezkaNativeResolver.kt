package com.example.tvmediaapp.data.resolver

import com.example.tvmediaapp.data.models.AudioTrackInfo
import com.example.tvmediaapp.data.models.EpisodeInfo
import com.example.tvmediaapp.data.models.SeasonInfo
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

    data class RezkaDetails(
        val audioTracks: List<AudioTrackInfo>,
        val seasons: List<SeasonInfo>,
        val pageUrl: String? = null
    )

    fun cleanTitle(title: String): String {
        var clean = title
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
            .replace(":", " ")
            .replace(" - ", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        clean = clean.replace(Regex("\\b\\d+\\s+(сери[йия]|сезон(а|ов)?)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(сезон|серия)\\s+\\d+\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(19\\d\\d|20\\d\\d)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return clean
    }

    private val KNOWN_ALIASES = mapOf(
        "очень вкусно" to listOf("Восхитительно", "Délicieux", "Delicieux"),
        "восхитительно" to listOf("Очень вкусно", "Délicieux", "Delicieux"),
        "delicieux" to listOf("Восхитительно", "Очень вкусно"),
        "délicieux" to listOf("Восхитительно", "Очень вкусно")
    )

    fun computeSimilarity(s1: String, s2: String): Double {
        val c1 = s1.lowercase().replace(Regex("[^a-zа-яё0-9]"), " ").replace(Regex("\\s+"), " ").trim()
        val c2 = s2.lowercase().replace(Regex("[^a-zа-яё0-9]"), " ").replace(Regex("\\s+"), " ").trim()
        if (c1.isEmpty() || c2.isEmpty()) return 0.0
        if (c1 == c2) return 1.0
        if (c1.contains(c2) || c2.contains(c1)) {
            val ratio = Math.min(c1.length, c2.length).toDouble() / Math.max(c1.length, c2.length)
            return Math.max(0.70, ratio)
        }
        val w1 = c1.split(" ").filter { it.length > 1 }.toSet()
        val w2 = c2.split(" ").filter { it.length > 1 }.toSet()
        if (w1.isEmpty() || w2.isEmpty()) return 0.0
        val intersect = w1.intersect(w2)
        if (intersect.isEmpty()) return 0.0
        val jaccard = intersect.size.toDouble() / w1.union(w2).size
        val overlap = intersect.size.toDouble() / Math.min(w1.size, w2.size)
        return Math.max(jaccard, overlap * 0.8)
    }

    suspend fun resolveMediaDetails(
        title: String,
        year: String? = null,
        isSeries: Boolean = false,
        mediaUrl: String? = null,
        originalTitle: String? = null
    ): RezkaDetails? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(title)
        if (clean.isEmpty() && mediaUrl.isNullOrEmpty()) return@withContext null

        for (baseUrl in MIRRORS) {
            val details = tryResolveDetailsFromMirror(baseUrl, clean, year, isSeries, mediaUrl, rawTitle = title, originalTitle = originalTitle)
            if (details != null && (details.audioTracks.isNotEmpty() || details.seasons.isNotEmpty())) {
                return@withContext details
            }
        }
        null
    }

    private data class PageInfo(val dataId: String, val pageUrl: String)

    private fun findPageUrlAndDataId(
        baseUrl: String,
        cleanTitle: String,
        year: String?,
        isSeries: Boolean,
        mediaUrl: String?,
        rawTitle: String? = null,
        originalTitle: String? = null
    ): PageInfo? {
        var dataId: String? = null
        var pageUrl: String? = null

        // 0. Direct URL bypass if mediaUrl is an HDRezka page
        if (!mediaUrl.isNullOrEmpty() && (mediaUrl.startsWith("http") || mediaUrl.contains("hdrezka") || mediaUrl.startsWith("rezka:"))) {
            val path = mediaUrl.substringAfter(".tv").substringAfter(".me").substringAfter(".ag").substringAfter(".org").substringAfter(".com").substringAfter("rezka:")
            val cleanPath = if (path.startsWith("/")) path else "/$path"
            val idMatch = Pattern.compile("(\\d+)").matcher(cleanPath)
            if (idMatch.find()) {
                dataId = idMatch.group(1)
                pageUrl = if (cleanPath.contains(".html")) "$baseUrl$cleanPath" else null
            }
        }

        if (dataId.isNullOrEmpty() || pageUrl.isNullOrEmpty()) {
            val targetYearInt = year?.toIntOrNull()
            data class RezkaCandidate(val id: String, val url: String, val score: Int)
            val candidates = mutableListOf<RezkaCandidate>()

            fun parseCandidates(html: String, queryTitle: String) {
                val itemPattern = Pattern.compile("class=\"b-content__inline_item\"[^>]*data-id=\"(\\d+)\"[^>]*data-url=\"([^\"]+)\"([\\s\\S]*?)(?=<div class=\"b-content__inline_item\"|$)")
                val itemMatcher = itemPattern.matcher(html)
                while (itemMatcher.find()) {
                    val id = itemMatcher.group(1) ?: continue
                    val rawLink = itemMatcher.group(2) ?: ""
                    val fullUrl = if (rawLink.startsWith("http")) rawLink else "$baseUrl$rawLink"
                    val snippet = itemMatcher.group(3) ?: ""

                    // Candidate title extraction
                    val titleMatcher = Pattern.compile("<div class=\"b-content__inline_item-link\"[^>]*>\\s*<a[^>]*>([^<]+)</a>").matcher(snippet)
                    val candTitle = if (titleMatcher.find()) titleMatcher.group(1).trim() else ""

                    // Extra candidate info (original title, country, year)
                    val extraMatcher = Pattern.compile("<div class=\"b-content__inline_item-link\"[^>]*>[\\s\\S]*?<div>([^<]+)</div>").matcher(snippet)
                    val candExtra = if (extraMatcher.find()) extraMatcher.group(1).trim() else ""

                    // Strict similarity validation
                    val targets = listOfNotNull(
                        queryTitle.ifEmpty { null },
                        cleanTitle.ifEmpty { null },
                        rawTitle?.ifEmpty { null },
                        originalTitle?.ifEmpty { null }
                    )
                    var maxSim = 0.0
                    for (text in listOf(candTitle, candExtra)) {
                        if (text.isEmpty()) continue
                        for (target in targets) {
                            val sim = computeSimilarity(text, target)
                            if (sim > maxSim) maxSim = sim
                        }
                    }

                    // Strict filter: Candidate MUST match the title with similarity >= 0.50!
                    if (maxSim < 0.50) {
                        continue
                    }

                    var score = (maxSim * 200).toInt()

                    // Year matching
                    val textForYear = if (Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b").matcher(snippet).find()) snippet else fullUrl
                    val yearMatcher = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b").matcher(textForYear)
                    if (yearMatcher.find()) {
                        val candYear = yearMatcher.group(1).toIntOrNull()
                        if (targetYearInt != null && candYear != null) {
                            val diff = Math.abs(candYear - targetYearInt)
                            if (diff == 0) score += 100
                            else if (diff == 1) score += 50
                            else score -= diff * 20
                        }
                    }

                    // Series vs Movie matching
                    val isCandSeries = fullUrl.contains("/series/") || fullUrl.contains("/animation/") || (isSeries && fullUrl.contains("/cartoons/")) || snippet.contains("сезон") || snippet.contains("сери")
                    if (isSeries == isCandSeries) {
                        score += 60
                    } else {
                        score -= 50
                    }

                    candidates.add(RezkaCandidate(id, fullUrl, score))
                }
            }

            // 1. Search HDRezka directly with primary cleanTitle
            if (cleanTitle.isNotEmpty()) {
                val searchUrl = "$baseUrl/search/?do=search&subaction=search&q=" + URLEncoder.encode(cleanTitle, "UTF-8")
                val searchHtml = httpGet(searchUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
                if (searchHtml.isNotEmpty()) {
                    parseCandidates(searchHtml, cleanTitle)
                }
            }

            // Fallback 1: ё -> е if no candidates found
            if (candidates.isEmpty() && (cleanTitle.contains("ё") || cleanTitle.contains("Ё"))) {
                val altTitle = cleanTitle.replace("ё", "е").replace("Ё", "Е")
                val altSearchUrl = "$baseUrl/search/?do=search&subaction=search&q=" + URLEncoder.encode(altTitle, "UTF-8")
                val altHtml = httpGet(altSearchUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
                if (altHtml.isNotEmpty()) {
                    parseCandidates(altHtml, altTitle)
                }
            }

            // Fallback 2: base title before colon if original title contained subtitle
            val orig = rawTitle ?: ""
            if (candidates.isEmpty() && orig.contains(":")) {
                val baseTitle = cleanTitle(orig.split(":")[0])
                if (baseTitle.isNotEmpty() && baseTitle != cleanTitle) {
                    val baseSearchUrl = "$baseUrl/search/?do=search&subaction=search&q=" + URLEncoder.encode(baseTitle, "UTF-8")
                    val baseHtml = httpGet(baseSearchUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
                    if (baseHtml.isNotEmpty()) {
                        parseCandidates(baseHtml, baseTitle)
                    }
                }
            }

            // Fallback 3: originalTitle if provided and differs from cleanTitle
            val origClean = originalTitle?.let { cleanTitle(it) } ?: ""
            if (candidates.isEmpty() && origClean.isNotEmpty() && !origClean.equals(cleanTitle, ignoreCase = true)) {
                val origSearchUrl = "$baseUrl/search/?do=search&subaction=search&q=" + URLEncoder.encode(origClean, "UTF-8")
                val origHtml = httpGet(origSearchUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
                if (origHtml.isNotEmpty()) {
                    parseCandidates(origHtml, origClean)
                }
            }

            // Fallback 4: known aliases (e.g. "Очень вкусно" / "Délicieux" -> "Восхитительно")
            if (candidates.isEmpty()) {
                val aliasesToTry = mutableListOf<String>()
                for ((key, aliasList) in KNOWN_ALIASES) {
                    val matchesKey = cleanTitle.contains(key, ignoreCase = true) ||
                            (rawTitle != null && rawTitle.contains(key, ignoreCase = true)) ||
                            (originalTitle != null && originalTitle.contains(key, ignoreCase = true))
                    if (matchesKey) {
                        for (alias in aliasList) {
                            if (!aliasesToTry.contains(alias) && !alias.equals(cleanTitle, ignoreCase = true)) {
                                aliasesToTry.add(alias)
                            }
                        }
                    }
                }
                for (alias in aliasesToTry) {
                    val aliasSearchUrl = "$baseUrl/search/?do=search&subaction=search&q=" + URLEncoder.encode(alias, "UTF-8")
                    val aliasHtml = httpGet(aliasSearchUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
                    if (aliasHtml.isNotEmpty()) {
                        parseCandidates(aliasHtml, alias)
                        if (candidates.isNotEmpty()) break
                    }
                }
            }

            // Pick the verified best candidate (STRICTLY NO blind regex fallback on unmatched html!)
            if (candidates.isNotEmpty()) {
                val best = candidates.maxByOrNull { it.score }!!
                dataId = best.id
                pageUrl = best.url
            }
        }

        return if (!dataId.isNullOrEmpty() && !pageUrl.isNullOrEmpty()) PageInfo(dataId, pageUrl) else null
    }

    private fun tryResolveDetailsFromMirror(
        baseUrl: String,
        cleanTitle: String,
        year: String?,
        isSeries: Boolean,
        mediaUrl: String?,
        rawTitle: String? = null,
        originalTitle: String? = null
    ): RezkaDetails? {
        val pageInfo = findPageUrlAndDataId(baseUrl, cleanTitle, year, isSeries, mediaUrl, rawTitle = rawTitle, originalTitle = originalTitle) ?: return null
        val dataId = pageInfo.dataId
        val pageUrl = pageInfo.pageUrl

        val pageHtml = httpGet(pageUrl, "$baseUrl/", baseUrl = baseUrl) ?: return null

        val audioTracks = mutableListOf<AudioTrackInfo>()
        // 1. Match translators
        val trMatcher = Pattern.compile("data-translator_id=\"(\\d+)\"[^>]*title=\"([^\"]+)\"").matcher(pageHtml)
        while (trMatcher.find()) {
            val tid = trMatcher.group(1) ?: continue
            var tname = trMatcher.group(2)?.trim() ?: ""
            tname = tname.replace(Regex("<[^>]+>"), "").trim()
            if (tname.isNotEmpty() && audioTracks.none { it.id == tid }) {
                audioTracks.add(AudioTrackInfo(id = tid, name = tname, episodesCount = 0, source = "hdrezka"))
            }
        }

        if (audioTracks.isEmpty()) {
            val fallbackMatcher = Pattern.compile("<li[^>]*class=\"[^\"]*b-translator__item[^\"]*\"[^>]*data-translator_id=\"(\\d+)\"[^>]*>([\\s\\S]*?)</li>").matcher(pageHtml)
            while (fallbackMatcher.find()) {
                val tid = fallbackMatcher.group(1) ?: continue
                val rawInner = fallbackMatcher.group(2)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
                if (rawInner.isNotEmpty() && audioTracks.none { it.id == tid }) {
                    audioTracks.add(AudioTrackInfo(id = tid, name = rawInner, episodesCount = 0, source = "hdrezka"))
                }
            }
        }

        if (audioTracks.isEmpty()) {
            val mInit = Pattern.compile("initCDN(?:Movies|Series)Events\\(\\s*\\d+\\s*,\\s*(\\d+)").matcher(pageHtml)
            if (mInit.find()) {
                val tid = mInit.group(1) ?: "56"
                audioTracks.add(AudioTrackInfo(id = tid, name = "Основная озвучка (HDRezka)", episodesCount = 0, source = "hdrezka"))
            }
        }

        val actualIsSeries = isSeries || pageUrl.contains("/series/") || pageUrl.contains("/animation/") || pageUrl.contains("/cartoons/") || pageHtml.contains("initCDNSeriesEvents") || pageHtml.contains("b-simple_episodes__list") || pageHtml.contains("id=\"simple-seasons-tabs\"")

        val seasonsList = mutableListOf<SeasonInfo>()
        if (actualIsSeries) {
            val defaultTid = audioTracks.firstOrNull()?.id ?: "56"
            val tNow = System.currentTimeMillis()
            val ajaxUrl = "$baseUrl/ajax/get_cdn_series/?t=$tNow"
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to pageUrl,
                "Origin" to baseUrl,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
            val postData = "id=$dataId&translator_id=$defaultTid&action=get_episodes"
            val resp = httpPost(ajaxUrl, postData, headers, baseUrl = baseUrl)
            if (resp != null && resp.trim().startsWith("{")) {
                try {
                    val j = JSONObject(resp)
                    if (j.optBoolean("success")) {
                        val epHtml = j.optString("episodes", "")
                        val sHtml = j.optString("seasons", "")
                        val sMatcher = Pattern.compile("data-tab_id=\"(\\d+)\"[^>]*>([^<]+)").matcher(sHtml)
                        val sPairs = mutableListOf<Pair<Int, String>>()
                        while (sMatcher.find()) {
                            val sNum = sMatcher.group(1)?.toIntOrNull() ?: continue
                            val sTitle = sMatcher.group(2)?.trim() ?: "Сезон $sNum"
                            sPairs.add(Pair(sNum, sTitle))
                        }
                        if (sPairs.isEmpty()) {
                            sPairs.add(Pair(1, "Сезон 1"))
                        }
                        for ((sNum, sTitle) in sPairs) {
                            val epPattern = Pattern.compile("data-season_id=\"$sNum\"\\s+data-episode_id=\"(\\d+)\"[^>]*>([^<]*)")
                            var epMatcher = epPattern.matcher(epHtml)
                            val epList = mutableListOf<EpisodeInfo>()
                            while (epMatcher.find()) {
                                val epNum = epMatcher.group(1)?.toIntOrNull() ?: continue
                                val epTitle = epMatcher.group(2)?.trim()?.ifEmpty { "Серия $epNum" } ?: "Серия $epNum"
                                epList.add(EpisodeInfo(episodeNumber = epNum, title = epTitle))
                            }
                            if (epList.isEmpty()) {
                                val genEp = Pattern.compile("data-episode_id=\"(\\d+)\"[^>]*>([^<]*)").matcher(epHtml)
                                while (genEp.find()) {
                                    val epNum = genEp.group(1)?.toIntOrNull() ?: continue
                                    val epTitle = genEp.group(2)?.trim()?.ifEmpty { "Серия $epNum" } ?: "Серия $epNum"
                                    epList.add(EpisodeInfo(episodeNumber = epNum, title = epTitle))
                                }
                            }
                            if (epList.isNotEmpty()) {
                                seasonsList.add(SeasonInfo(seasonNumber = sNum, title = sTitle, episodes = epList))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        if (audioTracks.isNotEmpty() && seasonsList.isNotEmpty()) {
            val totalEps = seasonsList.sumOf { it.episodes.size }
            val sMap = seasonsList.associate { it.seasonNumber to it.episodes.size }
            val updated = audioTracks.mapIndexed { idx, tr ->
                if (idx == 0) tr.copy(episodesCount = totalEps, seasonsEpisodes = sMap) else tr
            }
            return RezkaDetails(audioTracks = updated, seasons = seasonsList, pageUrl = pageUrl)
        }

        return RezkaDetails(audioTracks = audioTracks, seasons = seasonsList, pageUrl = pageUrl)
    }

    suspend fun resolveStreams(
        title: String,
        year: String? = null,
        isSeries: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        translatorId: String? = null,
        mediaUrl: String? = null,
        originalTitle: String? = null
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitle(title)
        if (cleanTitle.isEmpty() && mediaUrl.isNullOrEmpty()) return@withContext emptyList()

        // Try primary and fallback mirrors
        for (baseUrl in MIRRORS) {
            val result = tryResolveFromMirror(baseUrl, cleanTitle, year, isSeries, season, episode, translatorId, mediaUrl, rawTitle = title, originalTitle = originalTitle)
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
        translatorId: String? = null,
        mediaUrl: String? = null,
        rawTitle: String? = null,
        originalTitle: String? = null
    ): List<StreamOption> {
        val streams = mutableListOf<StreamOption>()
        try {
            val pageInfo = findPageUrlAndDataId(baseUrl, cleanTitle, year, isSeries, mediaUrl, rawTitle = rawTitle, originalTitle = originalTitle) ?: return emptyList()
            val dataId = pageInfo.dataId
            val pageUrl = pageInfo.pageUrl

            // 2. Fetch media page to establish session cookies & discover translator ID
            val pageHtml = httpGet(pageUrl, "$baseUrl/", baseUrl = baseUrl) ?: ""
            
            // Extract the real default or active translator on this specific page
            val pageDefaultTransId = run {
                val activeTrMatcher = Pattern.compile("<li[^>]*class=\"[^\"]*active[^\"]*\"[^>]*data-translator_id=\"(\\d+)\"").matcher(pageHtml)
                if (activeTrMatcher.find()) {
                    activeTrMatcher.group(1) ?: "56"
                } else {
                    val anyTrMatcher = Pattern.compile("data-translator_id=\"(\\d+)\"").matcher(pageHtml)
                    if (anyTrMatcher.find()) {
                        anyTrMatcher.group(1) ?: "56"
                    } else {
                        val initMatcher = Pattern.compile("initCDN(?:Movies|Series)Events\\(\\s*\\d+\\s*,\\s*(\\d+)").matcher(pageHtml)
                        if (initMatcher.find()) {
                            initMatcher.group(1) ?: "56"
                        } else {
                            "56"
                        }
                    }
                }
            }

            var transId = translatorId?.takeIf { it.all { ch -> ch.isDigit() } }
            if (transId.isNullOrEmpty()) {
                transId = pageDefaultTransId
            }

            // Determine if the content is a series (by requested flag, cartoon/series page URL, or HTML series markers)
            val actualIsSeries = isSeries || episode > 1 || season > 1 ||
                pageUrl.contains("/series/") || pageUrl.contains("/animation/") || pageUrl.contains("/cartoons/") ||
                pageHtml.contains("initCDNSeriesEvents") ||
                pageHtml.contains("b-simple_episodes__list") ||
                pageHtml.contains("id=\"simple-seasons-tabs\"")

            // Collect all available translator IDs from the page
            val allPageTranslators = mutableListOf<String>()
            val allTrMatcher = Pattern.compile("data-translator_id=\"(\\d+)\"").matcher(pageHtml)
            while (allTrMatcher.find()) {
                val tid = allTrMatcher.group(1)
                if (!tid.isNullOrEmpty() && !allPageTranslators.contains(tid)) {
                    allPageTranslators.add(tid)
                }
            }

            // 3. Request CDN streams via AJAX
            val tNow = System.currentTimeMillis()
            val ajaxUrl = "$baseUrl/ajax/get_cdn_series/?t=$tNow"
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to pageUrl,
                "Origin" to baseUrl,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Content-Type" to "application/x-www-form-urlencoded"
            )

            fun makeAjaxCall(targetTransId: String, sNum: Int, epNum: Int): String {
                val postData = StringBuilder()
                    .append("id=").append(dataId)
                    .append("&translator_id=").append(targetTransId)
                    .append("&action=").append(if (actualIsSeries) "get_stream" else "get_movie")
                if (actualIsSeries) {
                    postData.append("&season=").append(sNum).append("&episode=").append(epNum)
                }
                val resp = httpPost(ajaxUrl, postData.toString(), headers, baseUrl = baseUrl)
                if (resp != null && resp.trim().startsWith("{")) {
                    val j = JSONObject(resp)
                    return j.optString("url").ifEmpty { j.optString("streams", "") }
                }
                return ""
            }

            var streamStr = makeAjaxCall(transId, season, episode)

            // Fallback for series when active translator has no season 1 (e.g. Chebol Coldfilm has only season 2)
            if (streamStr.isEmpty() && translatorId == null) {
                // 1. Try other translators discovered on this page for season 1
                for (otherTid in allPageTranslators) {
                    if (otherTid == transId) continue
                    val s = makeAjaxCall(otherTid, season, episode)
                    if (s.isNotEmpty()) {
                        streamStr = s
                        break
                    }
                }
                // 2. If still empty and season == 1, try season 2 episode 1
                if (streamStr.isEmpty() && actualIsSeries && season == 1) {
                    val s = makeAjaxCall(transId, 2, 1)
                    if (s.isNotEmpty()) {
                        streamStr = s
                    }
                }
            }

            if (streamStr.isEmpty()) {
                return emptyList()
            }

            fun parseStreams(raw: String, isFallback: Boolean = false) {
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
                            val srcLabel = if (isFallback) "HDrezka (дубляж fallback)" else "HDrezka"
                            streams.add(StreamOption(quality = quality, url = workingUrl, isHls = isHls, source = srcLabel))
                        }
                    }
                }
            }

            if (streamStr.length > 5) {
                parseStreams(streamStr, isFallback = false)
            }

            // Fallback: If translator did not voice this season/episode, retry with default translators
            val fallbackIds = listOf(pageDefaultTransId, "56").distinct().filter { it != transId }
            for (fbId in fallbackIds) {
                if (streams.isNotEmpty()) break
                val fbPostData = StringBuilder()
                    .append("id=").append(dataId)
                    .append("&translator_id=").append(fbId)
                    .append("&action=").append(if (actualIsSeries) "get_stream" else "get_movie")
                if (actualIsSeries) {
                    fbPostData.append("&season=").append(season).append("&episode=").append(episode)
                }
                val fbResp = httpPost(ajaxUrl, fbPostData.toString(), headers, baseUrl = baseUrl)
                if (fbResp != null && fbResp.trim().startsWith("{")) {
                    val fbJson = JSONObject(fbResp)
                    val fbStreamStr = fbJson.optString("url").ifEmpty { fbJson.optString("streams", "") }
                    if (fbStreamStr.length > 5) {
                        parseStreams(fbStreamStr, isFallback = true)
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
            val redirTarget = if (targetUrl.contains("/ajax/")) "$baseUrl/" else targetUrl
            val passUrl = "$baseUrl$basePrefix/.within.website/x/cmd/anubis/api/pass-challenge" +
                    "?id=" + URLEncoder.encode(challengeId, "UTF-8") +
                    "&response=" + URLEncoder.encode(foundHash, "UTF-8") +
                    "&nonce=" + nonce +
                    "&redir=" + URLEncoder.encode(redirTarget, "UTF-8") +
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
