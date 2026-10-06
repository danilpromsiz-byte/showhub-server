package com.example.tvmediaapp.ui.screens.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.defaultMinSize
import com.example.tvmediaapp.ui.components.focusedGlow
import com.example.tvmediaapp.util.unescapeHtml
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import androidx.tv.material3.Border
import com.example.tvmediaapp.ui.components.AppButton as Button
import androidx.tv.material3.ButtonDefaults
import com.example.tvmediaapp.ui.components.AppCard as Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.CommentItem
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.SeasonInfo
import com.example.tvmediaapp.data.models.EpisodeInfo
import com.example.tvmediaapp.data.models.StreamOption
import com.example.tvmediaapp.data.models.AudioTrackInfo
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.data.resolver.FilmixNativeResolver
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.components.TorrServerHelperDialog
import com.example.tvmediaapp.data.torrserver.TorrServerManager
import androidx.compose.foundation.focusable
import com.example.tvmediaapp.ui.screens.player.isDirectVideoStream
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.FavoriteGold
import com.example.tvmediaapp.ui.theme.ImdbGold
import com.example.tvmediaapp.ui.theme.KpOrange
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

// UI Data class to hold source display info with type and best quality
data class DetailsSourceItem(val name: String, val isHls: Boolean, val bestQuality: String, val epCount: Int = 0)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: Movie,
    onPlayClick: (detailedMovie: Movie, videoUrl: String, startPositionMs: Long, season: Int, episode: Int, audioId: String) -> Unit,
    onBackClick: () -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    isFavorite: Boolean,
    onSearchClick: (String) -> Unit = {},
    isModalActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current

    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }
    val isFilmixPro = remember(prefs) { prefs.getBoolean("filmix_is_pro", false) }
    val isFilmixProPlus = remember(prefs) { prefs.getString("filmix_tariff", "")?.contains("PRO+", ignoreCase = true) == true }
    val rightPaneScrollState = rememberScrollState()
    var isFav by remember(movie.id, isFavorite) { mutableStateOf(isFavorite) }
    var currentMovie by remember {
        mutableStateOf(
            com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(movie.id, movie.title, movie.releaseYear) ?: movie
        )
    }

    val historyVersion by com.example.tvmediaapp.data.history.WatchHistoryManager.historyVersionFlow.collectAsState()
    val historyManager = remember { WatchHistoryManager(context) }
    val savedHistory = remember(movie.id, currentMovie.id, currentMovie.title, historyVersion) {
        historyManager.getProgress(currentMovie.id.ifEmpty { movie.id }, currentMovie.title)
    }

    var selectedSeason by remember { mutableStateOf(savedHistory?.season ?: 1) }
    var selectedEpisode by remember { mutableStateOf(savedHistory?.episode ?: 1) }
    var selectedAudioId by remember { mutableStateOf(savedHistory?.audioId ?: "") }
    var selectedQuality by remember { mutableStateOf(prefs.getString("pref_quality", "1080p") ?: "1080p") }
    val defaultSourcePref = prefs.getString("pref_source", "Все") ?: "Все"
    val initialSource = remember(movie.source, currentMovie.source, defaultSourcePref) {
        val s = currentMovie.source.takeIf { it.isNotBlank() && !it.equals("Все", ignoreCase = true) && !it.equals("lampa", ignoreCase = true) && !it.equals("tmdb", ignoreCase = true) && !it.equals("all", ignoreCase = true) }
            ?: movie.source.takeIf { it.isNotBlank() && !it.equals("Все", ignoreCase = true) && !it.equals("lampa", ignoreCase = true) && !it.equals("tmdb", ignoreCase = true) && !it.equals("all", ignoreCase = true) }
            ?: defaultSourcePref
        when {
            s.equals("Все", ignoreCase = true) -> "Все"
            s.startsWith("HDrezka", ignoreCase = true) || s.startsWith("Rezka", ignoreCase = true) -> "HDRezka"
            s.contains("Filmix", ignoreCase = true) -> "Filmix"
            s.contains("Kodik", ignoreCase = true) -> "Kodik"
            s.contains("VideoCDN", ignoreCase = true) -> "VideoCDN"
            s.contains("Collaps", ignoreCase = true) || s.contains("Delivembd", ignoreCase = true) -> "Collaps"
            s.contains("Bazon", ignoreCase = true) -> "Bazon"
            else -> "Все"
        }
    }
    var selectedSourceFilter by remember { mutableStateOf(initialSource) }
    var isResolving by remember { mutableStateOf(false) }
    var streamStatus by remember { mutableStateOf<String?>(null) }
    var streamOptions by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var selectedDetailTab by remember { mutableIntStateOf(0) }
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var isLoadingComments by remember { mutableStateOf(false) }
    var isLoadingDetails by remember { mutableStateOf(currentMovie.seasons.isEmpty() && currentMovie.audioTracks.isEmpty()) }
    var isLoadingStreams by remember { mutableStateOf(true) }
    var showTorrServerDialog by remember { mutableStateOf(false) }
    var pendingTorrStream by remember { mutableStateOf<StreamOption?>(null) }

    fun isStreamStub(st: StreamOption): Boolean {
        val su = st.url.lowercase()
        val sq = st.quality.lowercase()
        val resP = sq.substringBefore("(").trim()
        if (su.contains("rhtie") || su.contains("zrkms") || su.contains("/1/4/4/4/3/4/3/") || su.contains("/1/5/3/6/4/2/4/") ||
            su.contains("trial") || su.contains("promo") || su.contains("teaser")) return true
        if ((st.source.contains("rezka", ignoreCase = true) || su.contains("voidboost")) &&
            (resP.contains("ultra") || resP.contains("4k") || resP.contains("2160") || resP.contains("1440") || resP.contains("premium") || resP.contains("vip") || resP.contains("sub") || st.isPremium)) return true
        val isFx = st.source.contains("filmix", ignoreCase = true) || su.contains("cdnsqu.com") || su.contains("werkecdn.me")
        if (isFx) {
            if (!isFilmixPro && (resP.contains("1080") || resP.contains("1440") || resP.contains("4k") || resP.contains("2160") || resP.contains("ultra") || st.isPremium)) return true
            if (!isFilmixProPlus && (resP.contains("1440") || resP.contains("4k") || resP.contains("2160") || resP.contains("ultra"))) return true
        }
        return false
    }

    val availableSourcesInfo = remember(currentMovie.sources, currentMovie.audioTracks, currentMovie.seasons, selectedSeason, streamOptions, currentMovie.isSeries) {
        val detectedSources = linkedSetOf<String>()
        val standardSources = listOf("Collaps", "Filmix", "HDRezka", "Zona", "Торренты (TorrServe)")
        standardSources.forEach { detectedSources.add(it) }
        currentMovie.sources.forEach { if (it.name.isNotBlank()) detectedSources.add(it.name) }
        currentMovie.audioTracks.forEach { trk ->
            val s = trk.source.trim()
            val name = when {
                s.contains("kodik", ignoreCase = true) || trk.id.startsWith("kodik_") -> "Kodik"
                s.contains("filmix", ignoreCase = true) -> "Filmix"
                s.contains("videocdn", ignoreCase = true) -> "VideoCDN"
                s.contains("collaps", ignoreCase = true) || s.contains("delivembd", ignoreCase = true) -> "Collaps"
                s.contains("anilibria", ignoreCase = true) -> "AniLibria"
                s.contains("bazon", ignoreCase = true) -> "Bazon"
                s.contains("zona", ignoreCase = true) -> "Zona"
                s.contains("rezka", ignoreCase = true) -> "HDRezka"
                s.isNotEmpty() -> s.replaceFirstChar { it.uppercase() }
                else -> ""
            }
            if (name.isNotEmpty()) detectedSources.add(name)
        }
        streamOptions.forEach { st ->
            val s = st.source.trim()
            val name = when {
                s.startsWith("HDrezka", ignoreCase = true) || s.startsWith("Rezka", ignoreCase = true) -> "HDRezka"
                s.contains("Filmix", ignoreCase = true) -> "Filmix"
                s.contains("Kodik", ignoreCase = true) -> "Kodik"
                s.contains("VideoCDN", ignoreCase = true) || st.url.contains("allarknow") || st.url.contains("bayas") || st.url.contains("videoframe") -> "VideoCDN"
                s.contains("Collaps", ignoreCase = true) || s.contains("Delivembd", ignoreCase = true) || st.url.contains("interkh") || st.url.contains("namy.ws") -> "Collaps"
                s.contains("AniLibria", ignoreCase = true) || st.url.contains("libria") -> "AniLibria"
                s.contains("Bazon", ignoreCase = true) -> "Bazon"
                s.contains("Zona", ignoreCase = true) -> "Zona"
                s.contains("Торрент", ignoreCase = true) || s.contains("torrent", ignoreCase = true) -> "Торренты (TorrServe)"
                s.isNotEmpty() -> s.replaceFirstChar { it.uppercase() }
                else -> ""
            }
            if (name.isNotEmpty()) detectedSources.add(name)
        }

        val curSeasonEps = if (currentMovie.isSeries) {
            currentMovie.seasons.firstOrNull { it.seasonNumber == selectedSeason }?.episodes?.size
                ?: currentMovie.seasons.sumOf { it.episodes.size }
        } else 0

        val qualityOrder = listOf("4K", "2160", "2K", "1440", "1080p HD", "1080p", "1080", "720p", "720", "480p", "480", "360p", "360")

        val isTv = com.example.tvmediaapp.BuildConfig.PLATFORM_TYPE == "tv"
        val sourceInfoList = detectedSources.mapNotNull { srcName ->
            val sKey = srcName.lowercase()
            if (isTv && sKey.contains("videocdn")) {
                // VideoCDN only provides web iframe embeds that cannot be operated/played by Android TV ExoPlayer
                return@mapNotNull null
            }
            val srcStreams = streamOptions.filter { st ->
                val stSrc = st.source.lowercase()
                when {
                    sKey.contains("kodik") -> stSrc.contains("kodik")
                    sKey.contains("rezka") -> stSrc.contains("rezka")
                    sKey.contains("filmix") -> stSrc.contains("filmix")
                    sKey.contains("videocdn") -> stSrc.contains("videocdn") || st.url.contains("allarknow") || st.url.contains("bayas") || st.url.contains("videoframe")
                    sKey.contains("collaps") || sKey.contains("delivembd") -> stSrc.contains("collaps") || stSrc.contains("delivembd") || st.url.contains("interkh") || st.url.contains("namy.ws")
                    sKey.contains("anilibria") -> stSrc.contains("anilibria") || st.url.contains("libria")
                    sKey.contains("bazon") -> stSrc.contains("bazon")
                    sKey.contains("zona") -> stSrc.contains("zona") || stSrc.contains("torrent") || stSrc.contains("торрент") || stSrc.contains("p2p")
                    sKey.contains("торрент") || sKey.contains("torrent") -> stSrc.contains("torrent") || stSrc.contains("торрент") || stSrc.contains("zona")
                    else -> stSrc.contains(sKey)
                }
            }
            val hasTracks = currentMovie.audioTracks.any { trk ->
                val tSrc = trk.source.lowercase()
                when {
                    sKey.contains("kodik") -> tSrc.contains("kodik") || trk.id.startsWith("kodik_")
                    sKey.contains("rezka") -> tSrc.contains("rezka") || (!trk.id.startsWith("kodik_") && !tSrc.contains("filmix") && !tSrc.contains("videocdn") && !tSrc.contains("bazon"))
                    sKey.contains("filmix") -> tSrc.contains("filmix")
                    sKey.contains("videocdn") -> tSrc.contains("videocdn")
                    sKey.contains("collaps") || sKey.contains("delivembd") -> tSrc.contains("collaps") || tSrc.contains("delivembd")
                    sKey.contains("anilibria") -> tSrc.contains("anilibria")
                    sKey.contains("bazon") -> tSrc.contains("bazon")
                    else -> tSrc.contains(sKey)
                }
            }
            val cleanStreams = srcStreams.filter { !isStreamStub(it) }
            val isStandard = sKey.contains("rezka") || sKey.contains("filmix") || sKey.contains("collaps") || sKey.contains("zona") || sKey.contains("торрент") || sKey.contains("torrent")
            // Never prune standard sources
            if (streamOptions.isNotEmpty() && cleanStreams.isEmpty() && !hasTracks && !isStandard) {
                return@mapNotNull null
            }
            val hasHls = if (srcStreams.isNotEmpty()) {
                cleanStreams.any { isDirectVideoStream(it.url) }
            } else {
                sKey.contains("rezka") || sKey.contains("filmix") || sKey.contains("collaps") || sKey.contains("anilibria") || sKey.contains("zona") || sKey.contains("торрент") || sKey.contains("torrent")
            }
            val bestQ = run {
                for (q in qualityOrder) {
                    val match = cleanStreams.firstOrNull { st ->
                        val resP = st.quality.substringBefore("(").trim().lowercase()
                        resP.contains(q.lowercase()) && (!q.contains("1080") || (!resP.contains("ultra") && !resP.contains("premium")))
                    }
                    if (match != null) return@run match.quality.replace(Regex("\\(.*?\\)"), "").trim()
                }
                val firstClean = cleanStreams.firstOrNull()?.quality?.replace(Regex("\\(.*?\\)"), "")?.trim()
                if (!firstClean.isNullOrBlank()) return@run firstClean
                when {
                    sKey.contains("zona") || sKey.contains("торрент") || sKey.contains("torrent") -> "4K"
                    sKey.contains("filmix") -> if (isFilmixProPlus || isFilmixPro) "4K" else "1080p"
                    sKey.contains("rezka") -> "1080p"
                    sKey.contains("collaps") || sKey.contains("delivembd") -> "1080p"
                    else -> ""
                }
            }
            val epC = if (currentMovie.isSeries) {
                currentMovie.sources.firstOrNull { it.name.equals(srcName, ignoreCase = true) }?.seasonsEpisodes?.get(selectedSeason)
                    ?: currentMovie.audioTracks.filter { it.source.contains(srcName, ignoreCase = true) || (srcName.contains("kodik", ignoreCase = true) && it.id.startsWith("kodik_")) }
                        .mapNotNull { it.seasonsEpisodes[selectedSeason] ?: it.episodesCount.takeIf { c -> c > 0 } }
                        .maxOrNull()
                    ?: if (curSeasonEps > 0) curSeasonEps else 0
            } else 0
            DetailsSourceItem(srcName, hasHls, bestQ, epC)
        }.sortedByDescending { it.isHls }

        sourceInfoList
    }

    LaunchedEffect(availableSourcesInfo) {
        if (availableSourcesInfo.isNotEmpty()) {
            val exists = availableSourcesInfo.any { it.name.equals(selectedSourceFilter, ignoreCase = true) }
            if (!exists || selectedSourceFilter.equals("Все", ignoreCase = true)) {
                val preferred = availableSourcesInfo.firstOrNull { it.name.equals(currentMovie.source, ignoreCase = true) }
                    ?: availableSourcesInfo.firstOrNull { it.isHls }
                    ?: availableSourcesInfo.first()
                selectedSourceFilter = preferred.name
            }
        }
    }

    val availableSources = remember(availableSourcesInfo) {
        val list = mutableListOf("Все")
        availableSourcesInfo.forEach { list.add(it.name) }
        list
    }

    val filteredAudioTracks = remember(currentMovie.audioTracks, selectedSourceFilter, selectedSeason, streamOptions) {
        val sKey = selectedSourceFilter.lowercase()
        val sourceFiltered = if (selectedSourceFilter == "Все" || selectedSourceFilter.startsWith("Все")) {
            currentMovie.audioTracks
        } else {
            val matched = currentMovie.audioTracks.filter { track ->
                val trackSrc = track.source.lowercase()
                when {
                    sKey.contains("kodik") -> trackSrc.contains("kodik") || track.id.startsWith("kodik_")
                    sKey.contains("rezka") -> trackSrc.contains("rezka") || (!track.id.startsWith("kodik_") && !trackSrc.contains("filmix") && !trackSrc.contains("videocdn") && !trackSrc.contains("bazon") && !trackSrc.contains("collaps") && !trackSrc.contains("delivembd") && !trackSrc.contains("anilibria"))
                    sKey.contains("filmix") -> trackSrc.contains("filmix")
                    sKey.contains("videocdn") -> trackSrc.contains("videocdn")
                    sKey.contains("collaps") || sKey.contains("delivembd") -> trackSrc.contains("collaps") || trackSrc.contains("delivembd")
                    sKey.contains("anilibria") -> trackSrc.contains("anilibria")
                    sKey.contains("bazon") -> trackSrc.contains("bazon")
                    sKey.contains("zona") -> trackSrc.contains("zona")
                    else -> trackSrc.contains(sKey)
                }
            }
            if (matched.isNotEmpty()) {
                matched
            } else {
                val sourceStreams = streamOptions.filter { st ->
                    val src = st.source.lowercase()
                    when {
                        sKey.contains("collaps") || sKey.contains("delivembd") -> src.contains("collaps") || src.contains("delivembd") || st.url.contains("interkh") || st.url.contains("namy.ws")
                        sKey.contains("videocdn") -> src.contains("videocdn") || st.url.contains("allarknow") || st.url.contains("bayas")
                        sKey.contains("anilibria") -> src.contains("anilibria") || st.url.contains("libria")
                        sKey.contains("kodik") -> src.contains("kodik")
                        sKey.contains("bazon") -> src.contains("bazon")
                        sKey.contains("zona") -> src.contains("zona")
                        else -> src.contains(sKey)
                    }
                }
                val synthTracks = sourceStreams.mapNotNull { st ->
                    Regex("\\(([^)]+)\\)").findAll(st.quality)
                        .map { it.groupValues[1].trim() }
                        .firstOrNull { v ->
                            val lower = v.lowercase()
                            !lower.contains("плеер") && !lower.contains("player") &&
                            !lower.contains("hls") && !lower.contains("auto") &&
                            !lower.contains("сиды") && !lower.contains("peer") &&
                            !lower.matches(Regex("\\d+p?")) && lower != "hd" && lower != "fhd" && lower != "4k"
                        }?.let { voiceName ->
                            AudioTrackInfo(
                                id = "synth_${selectedSourceFilter}_${voiceName}",
                                name = voiceName,
                                source = selectedSourceFilter
                            )
                        }
                }.distinctBy { it.name }

                if (synthTracks.isNotEmpty()) {
                    synthTracks
                } else {
                    listOf(
                        AudioTrackInfo(
                            id = "src_${sKey}_default",
                            name = "Озвучка (${selectedSourceFilter})",
                            source = selectedSourceFilter
                        )
                    )
                }
            }
        }
        if (currentMovie.isSeries) {
            val seasonFiltered = sourceFiltered.filter { track ->
                if (track.seasonsEpisodes.isEmpty()) {
                    if (currentMovie.seasons.size > 1 && selectedSeason > 1) false else true
                } else {
                    (track.seasonsEpisodes[selectedSeason] ?: 0) > 0
                }
            }
            if (seasonFiltered.isNotEmpty()) seasonFiltered else sourceFiltered
        } else {
            sourceFiltered
        }
    }

    LaunchedEffect(filteredAudioTracks, selectedSeason) {
        if (filteredAudioTracks.isNotEmpty() && filteredAudioTracks.none { it.id == selectedAudioId }) {
            selectedAudioId = filteredAudioTracks.first().id
        }
    }

    val selectedAudioTrackName = remember(filteredAudioTracks, selectedAudioId, currentMovie.audioTracks) {
        filteredAudioTracks.firstOrNull { it.id == selectedAudioId }?.name
            ?: currentMovie.audioTracks.firstOrNull { it.id == selectedAudioId }?.name
            ?: "Озвучка"
    }
    val cleanAudioTrackName = remember(selectedAudioTrackName, selectedSourceFilter) {
        if (!selectedSourceFilter.equals("HDrezka", ignoreCase = true)) {
            selectedAudioTrackName.replace(Regex("(?i)\\s*\\(hdrezka\\)"), "").trim()
        } else {
            selectedAudioTrackName.trim()
        }
    }

    val displayCast = remember(currentMovie.cast, currentMovie.actors) {
        if (currentMovie.cast.isNotEmpty()) {
            currentMovie.cast
        } else if (currentMovie.actors.isNotBlank()) {
            currentMovie.actors.split(",", "•", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                .take(12)
                .map { com.example.tvmediaapp.data.models.PersonInfo(name = it) }
        } else {
            emptyList()
        }
    }

    val displayDirectors = remember(currentMovie.directorsList, currentMovie.director) {
        if (currentMovie.directorsList.isNotEmpty()) {
            currentMovie.directorsList
        } else if (currentMovie.director.isNotBlank()) {
            currentMovie.director.split(",", "•", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                .take(6)
                .map { com.example.tvmediaapp.data.models.PersonInfo(name = it) }
        } else {
            emptyList()
        }
    }

    fun getStreamQualityRank(q: String): Int {
        val ql = q.substringBefore("(").lowercase().trim()
        return when {
            ql.contains("4k") || ql.contains("2160") || ql.contains("ultra") -> 2160
            ql.contains("1440") || ql.contains("2k") -> 1440
            ql.contains("1080") -> 1080
            ql.contains("720") -> 720
            ql.contains("480") -> 480
            ql.contains("360") -> 360
            else -> 0
        }
    }

    fun matchStreamQuality(stream: StreamOption, target: String): Boolean {
        if (!isDirectVideoStream(stream.url)) return false
        if (isStreamStub(stream)) return false
        val sq = stream.quality.lowercase().trim()
        val tq = target.lowercase().trim()
        val resP = sq.substringBefore("(").trim()
        if (tq.contains("max") || tq.contains("макс") || tq.contains("авто") || tq.contains("auto")) {
            return true
        }
        if (tq.contains("ultra")) return resP.contains("ultra")
        if (tq.contains("4k") || tq.contains("2160")) return resP.contains("4k") || resP.contains("2160")
        if (tq.contains("1440") || tq.contains("2k")) return resP.contains("1440") || resP.contains("2k")
        if (tq.contains("1080")) return resP.contains("1080") && !resP.contains("ultra") && !resP.contains("premium")
        if (tq.contains("720")) return resP.contains("720")
        if (tq.contains("480")) return resP.contains("480")
        if (tq.contains("360")) return resP.contains("360")
        return resP.contains(tq)
    }

    val isContentSeries = currentMovie.isSeries || currentMovie.seasons.isNotEmpty() || selectedSeason > 1 || selectedEpisode > 1
    var isSourceStreamsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSourceFilter, selectedSeason, selectedEpisode, selectedAudioId) {
        if (selectedSourceFilter == "Все" || selectedSourceFilter.startsWith("Все")) return@LaunchedEffect
        val sKey = selectedSourceFilter.lowercase()
        val hasStreamsAlready = streamOptions.any { st ->
            val stSrc = st.source.lowercase()
            when {
                sKey.contains("kodik") -> stSrc.contains("kodik")
                sKey.contains("rezka") -> stSrc.contains("rezka")
                sKey.contains("filmix") -> stSrc.contains("filmix")
                sKey.contains("videocdn") -> stSrc.contains("videocdn") || st.url.contains("allarknow") || st.url.contains("bayas") || st.url.contains("videoframe")
                sKey.contains("collaps") || sKey.contains("delivembd") -> stSrc.contains("collaps") || stSrc.contains("delivembd") || st.url.contains("interkh") || st.url.contains("namy.ws")
                sKey.contains("anilibria") -> stSrc.contains("anilibria") || st.url.contains("libria")
                sKey.contains("bazon") -> stSrc.contains("bazon")
                sKey.contains("zona") -> stSrc.contains("zona") || stSrc.contains("torrent") || stSrc.contains("торрент") || stSrc.contains("p2p")
                sKey.contains("торрент") || sKey.contains("torrent") -> stSrc.contains("torrent") || stSrc.contains("торрент") || stSrc.contains("zona")
                else -> stSrc.contains(sKey)
            }
        }
        if (!hasStreamsAlready) {
            isSourceStreamsLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val fetched = when {
                        sKey.contains("filmix") -> {
                            val fx = FilmixNativeResolver.resolveStreams(
                                movieId = currentMovie.id,
                                title = currentMovie.title,
                                year = currentMovie.releaseYear,
                                isSeries = isContentSeries,
                                season = selectedSeason,
                                episode = selectedEpisode,
                                audioId = selectedAudioId,
                                isPro = isFilmixPro,
                                isProPlus = isFilmixProPlus
                            )
                            val srv = ShowHubApiClient.fetchStreams(currentMovie.copy(source = "filmix", isSeries = isContentSeries), season = if (isContentSeries) selectedSeason else null, episode = if (isContentSeries) selectedEpisode else null, audioId = selectedAudioId)
                            (fx + srv).distinctBy { it.url }
                        }
                        sKey.contains("rezka") -> {
                            val rz = RezkaNativeResolver.resolveStreams(
                                title = currentMovie.title,
                                year = currentMovie.releaseYear,
                                isSeries = isContentSeries,
                                season = selectedSeason,
                                episode = selectedEpisode,
                                translatorId = selectedAudioId,
                                mediaUrl = currentMovie.videoUrl.takeIf { it.contains("rezka") }
                            )
                            val srv = ShowHubApiClient.fetchStreams(currentMovie.copy(source = "hdrezka", isSeries = isContentSeries), season = if (isContentSeries) selectedSeason else null, episode = if (isContentSeries) selectedEpisode else null, audioId = selectedAudioId)
                            (rz + srv).distinctBy { it.url }
                        }
                        sKey.contains("zona") -> {
                            ShowHubApiClient.fetchStreams(currentMovie.copy(source = "zona", isSeries = isContentSeries), season = if (isContentSeries) selectedSeason else null, episode = if (isContentSeries) selectedEpisode else null, audioId = selectedAudioId)
                        }
                        sKey.contains("торрент") || sKey.contains("torrent") -> {
                            ShowHubApiClient.fetchStreams(currentMovie.copy(source = "torrents", isSeries = isContentSeries), season = if (isContentSeries) selectedSeason else null, episode = if (isContentSeries) selectedEpisode else null, audioId = selectedAudioId)
                        }
                        sKey.contains("collaps") || sKey.contains("delivembd") -> {
                            ShowHubApiClient.fetchStreams(currentMovie.copy(source = "collaps", isSeries = isContentSeries), season = if (isContentSeries) selectedSeason else null, episode = if (isContentSeries) selectedEpisode else null, audioId = selectedAudioId)
                        }
                        else -> {
                            ShowHubApiClient.fetchStreams(currentMovie.copy(source = selectedSourceFilter, isSeries = isContentSeries), season = if (isContentSeries) selectedSeason else null, episode = if (isContentSeries) selectedEpisode else null, audioId = selectedAudioId)
                        }
                    }
                    if (fetched.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            streamOptions = (fetched + streamOptions).distinctBy { it.url }
                                .sortedWith(compareByDescending<StreamOption> { isDirectVideoStream(it.url) }.thenByDescending { getStreamQualityRank(it.quality) })
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isSourceStreamsLoading = false
                    }
                }
            }
        }
    }

    val availableQualities = remember(streamOptions, selectedSourceFilter, selectedAudioId) {
        val sKey = selectedSourceFilter.lowercase()
        val rawCandidates = if (selectedSourceFilter == "Все" || selectedSourceFilter.startsWith("Все")) {
            streamOptions
        } else {
            streamOptions.filter { st ->
                val stSrc = st.source.lowercase()
                when {
                    sKey.contains("kodik") -> stSrc.contains("kodik")
                    sKey.contains("rezka") -> stSrc.contains("rezka")
                    sKey.contains("filmix") -> stSrc.contains("filmix")
                    sKey.contains("videocdn") -> stSrc.contains("videocdn") || st.url.contains("allarknow") || st.url.contains("bayas") || st.url.contains("videoframe")
                    sKey.contains("collaps") || sKey.contains("delivembd") -> stSrc.contains("collaps") || stSrc.contains("delivembd") || st.url.contains("interkh") || st.url.contains("namy.ws")
                    sKey.contains("anilibria") -> stSrc.contains("anilibria") || st.url.contains("libria")
                    sKey.contains("bazon") -> stSrc.contains("bazon")
                    sKey.contains("zona") -> stSrc.contains("zona") || stSrc.contains("torrent") || stSrc.contains("торрент") || stSrc.contains("p2p")
                    sKey.contains("торрент") || sKey.contains("torrent") -> stSrc.contains("torrent") || stSrc.contains("торрент") || stSrc.contains("zona")
                    else -> stSrc.contains(sKey)
                }
            }
        }
        val candidateStreams = rawCandidates.filter { !isStreamStub(it) }

        val qualSet = linkedSetOf<String>()
        val order = listOf("4K", "2K", "1080p", "720p", "480p", "360p")
        for (target in order) {
            if (candidateStreams.any { matchStreamQuality(it, target) }) {
                qualSet.add(target)
            }
        }
        candidateStreams.forEach { st ->
            val cleanQ = st.quality.replace(Regex("\\(.*?\\)"), "").trim()
            if (cleanQ.isNotEmpty() && !qualSet.contains(cleanQ) && qualSet.none { it.contains(cleanQ, ignoreCase = true) }) {
                qualSet.add(cleanQ)
            }
        }
        qualSet.toList()
    }

    LaunchedEffect(availableQualities) {
        if (availableQualities.isNotEmpty() && !availableQualities.any { it.equals(selectedQuality, ignoreCase = true) }) {
            selectedQuality = availableQualities.first()
        }
    }

    fun pickSafePreviewStream(streams: List<StreamOption>): String? {
        val nonPremium = streams.filter {
            val q = it.quality.lowercase()
            val u = it.url.lowercase()
            val resP = q.substringBefore("(").trim()
            val isFx = it.source.contains("filmix", ignoreCase = true) || u.contains("cdnsqu.com") || u.contains("werkecdn.me")
            if (isFx && !isFilmixPro && (resP.contains("1080") || resP.contains("1440") || resP.contains("4k") || resP.contains("2160") || resP.contains("ultra"))) {
                return@filter false
            }
            !resP.contains("ultra") && !resP.contains("4k") && !resP.contains("2160") && !resP.contains("1440") &&
                !q.contains("vip") && !q.contains("premium") && !q.contains("sub") &&
                !u.contains("rhtie") && !u.contains("zrkms") && !u.contains("/1/4/4/4/3/4/3/") && !u.contains("/1/5/3/6/4/2/4/") &&
                !u.contains("trial") && !u.contains("preview") &&
                !u.contains("teaser") && !u.contains("promo") && !u.contains("vip") &&
                !u.contains("ultra") && !u.contains("premium") &&
                isDirectVideoStream(it.url)
        }
        // Non-Rezka sources (Collaps, Filmix, VideoCDN, AniLibria) never have HDRezka promo ads - prioritize them first
        val nonRezka = nonPremium.filterNot { it.source.contains("rezka", ignoreCase = true) || it.url.contains("voidboost") }
        val pool = if (nonRezka.isNotEmpty()) nonRezka else nonPremium

        return pool.firstOrNull { it.quality.contains("720") }?.url
            ?: pool.firstOrNull {
                val resP = it.quality.substringBefore("(").trim().lowercase()
                val isFx = it.source.contains("filmix", ignoreCase = true) || it.url.lowercase().contains("cdnsqu.com") || it.url.lowercase().contains("werkecdn.me")
                resP.contains("1080") && (!isFx || isFilmixPro) && !it.quality.contains("ultra", true) && !it.quality.contains("premium", true)
            }?.url
            ?: pool.firstOrNull { it.quality.contains("480") }?.url
            ?: pool.firstOrNull()?.url
    }

    // Focus Requesters for instant TV remote control & bidirectional navigation
    val playButtonFocusRequester = remember { FocusRequester() }
    val fromStartButtonFocusRequester = remember { FocusRequester() }
    val trailerButtonFocusRequester = remember { FocusRequester() }
    val externalPlayerFocusRequester = remember { FocusRequester() }
    val favoriteButtonFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val bugReportFocusRequester = remember { FocusRequester() }
    val leftPaneFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val firstSourceFocusRequester = remember { FocusRequester() }
    val firstAudioFocusRequester = remember { FocusRequester() }
    val firstQualityFocusRequester = remember { FocusRequester() }
    val firstSeasonFocusRequester = remember { FocusRequester() }
    val translatorSeasonsCache = remember { mutableStateMapOf<String, List<SeasonInfo>>() }

    // Automatically focus the primary play button as soon as movie card opens, unless a modal dialog is active
    LaunchedEffect(isModalActive) {
        if (!isModalActive) {
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Background video preview state
    var detailsPreviewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isDetailsPreviewPlaying by remember { mutableStateOf(false) }

    // Fetch comments in background
    LaunchedEffect(currentMovie.id, currentMovie.title) {
        isLoadingComments = true
        comments = ShowHubApiClient.fetchComments(currentMovie)
        isLoadingComments = false
    }

    var newEpisodesCount by remember { mutableIntStateOf(0) }

    // Fetch deep metadata (seasons, episodes, translators, KP rating) in background
    LaunchedEffect(movie.id) {
        // Immediately release old preview player when moving to a new movie
        val oldPlayer = detailsPreviewPlayer
        detailsPreviewPlayer = null
        isDetailsPreviewPlaying = false
        oldPlayer?.let { p ->
            try {
                p.clearMediaItems()
                p.stop()
                p.release()
            } catch (_: Exception) {}
        }
        isLoadingDetails = true
        try {
            val detailed = withContext(Dispatchers.IO) {
                val d = ShowHubApiClient.fetchMediaDetails(movie)
                com.example.tvmediaapp.data.cache.MediaDiskCache.putCachedDetails(d)
                d
            }
            currentMovie = detailed
            if (detailed.seasons.isNotEmpty()) {
                if (savedHistory == null || detailed.seasons.none { it.seasonNumber == selectedSeason }) {
                    selectedSeason = detailed.seasons.first().seasonNumber
                }
            }
            if (detailed.audioTracks.isNotEmpty()) {
                if (selectedAudioId.isEmpty() || detailed.audioTracks.none { it.id == selectedAudioId }) {
                    selectedAudioId = detailed.audioTracks.first().id
                }
            }
            if (detailed.isSeries && detailed.seasons.isNotEmpty()) {
                val total = detailed.seasons.sumOf { it.episodes.size }
                val n = historyManager.updateKnownTotalEpisodes(detailed.id, total)
                newEpisodesCount = n
            }
        } finally {
            isLoadingDetails = false
        }
    }

    // Pre-fetch streams in background: query native Rezka and server in parallel
    LaunchedEffect(currentMovie.id, selectedSeason, selectedEpisode, selectedAudioId) {
        isLoadingStreams = true
        withContext(Dispatchers.IO) {
            try {
                val isContentSeries = currentMovie.isSeries || currentMovie.seasons.isNotEmpty() || selectedSeason > 1 || selectedEpisode > 1
                val rezkaMediaUrl = if (currentMovie.id.startsWith("http") || currentMovie.id.contains("hdrezka") || currentMovie.id.startsWith("rezka:")) {
                    currentMovie.id
                } else null
                val nativeDeferred = async {
                    val rzJob = async {
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                RezkaNativeResolver.resolveStreams(
                                    title = currentMovie.title,
                                    year = currentMovie.releaseYear,
                                    isSeries = isContentSeries,
                                    season = selectedSeason,
                                    episode = selectedEpisode,
                                    translatorId = selectedAudioId.ifEmpty { null },
                                    mediaUrl = rezkaMediaUrl,
                                    originalTitle = currentMovie.originalTitle
                                )
                            } ?: emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                    val fxJob = async {
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                FilmixNativeResolver.resolveStreams(
                                    movieId = currentMovie.id,
                                    title = currentMovie.title,
                                    year = currentMovie.releaseYear,
                                    isSeries = isContentSeries,
                                    season = selectedSeason,
                                    episode = selectedEpisode,
                                    audioId = selectedAudioId,
                                    isPro = isFilmixPro,
                                    isProPlus = isFilmixProPlus
                                )
                            } ?: emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                    rzJob.await() + fxJob.await()
                }
                val serverDeferred = async {
                    ShowHubApiClient.fetchStreams(
                        movie = currentMovie.copy(isSeries = isContentSeries),
                        season = if (isContentSeries) selectedSeason else null,
                        episode = if (isContentSeries) selectedEpisode else null,
                        audioId = selectedAudioId
                    )
                }

                // Progressive stream update: emit native streams immediately when available!
                val nativeStreams = nativeDeferred.await()
                if (nativeStreams.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val otherExisting = streamOptions.filterNot { it.source.contains("rezka", ignoreCase = true) || it.source.contains("filmix", ignoreCase = true) }
                        val merged = (nativeStreams + otherExisting).distinctBy { it.url }
                            .sortedWith(compareByDescending<StreamOption> { isDirectVideoStream(it.url) }.thenByDescending { getStreamQualityRank(it.quality) })
                        if (merged.isNotEmpty()) {
                            streamOptions = merged
                        }
                    }
                }

                val serverStreams = serverDeferred.await()
                val isNativeFallback = nativeStreams.isNotEmpty() && nativeStreams.all { it.source.contains("fallback", ignoreCase = true) }
                val hasServerDirect = serverStreams.any { isDirectVideoStream(it.url) && !it.source.contains("torrent", ignoreCase = true) }
                val combined = if (!isNativeFallback && nativeStreams.isNotEmpty()) {
                    val nonRezkaServer = serverStreams.filterNot { it.source.contains("rezka", ignoreCase = true) }
                    val rezkaServer = serverStreams.filter { it.source.contains("rezka", ignoreCase = true) }
                    if ((hasServerDirect || currentMovie.source == "filmix") && nonRezkaServer.isNotEmpty()) {
                        (nonRezkaServer + nativeStreams + rezkaServer).distinctBy { it.url }
                    } else {
                        (nativeStreams + serverStreams).distinctBy { it.url }
                    }
                } else if ((hasServerDirect || currentMovie.source == "filmix" || isNativeFallback) && serverStreams.isNotEmpty()) {
                    (serverStreams + nativeStreams).distinctBy { it.url }
                } else {
                    (nativeStreams + serverStreams).distinctBy { it.url }
                }
                val sorted = combined.sortedWith(compareByDescending<StreamOption> { isDirectVideoStream(it.url) }.thenByDescending { getStreamQualityRank(it.quality) })
                if (sorted.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        // Merge with existing streams to preserve all discovered sources (Collaps, VideoCDN, AniLibria, etc.)
                        val mergedAll = (sorted + streamOptions).distinctBy { it.url }
                            .sortedWith(compareByDescending<StreamOption> { isDirectVideoStream(it.url) }.thenByDescending { getStreamQualityRank(it.quality) })
                        streamOptions = mergedAll
                        if (mergedAll.any { it.source.contains("torrent", ignoreCase = true) || it.url.contains(":8090") }) {
                            coroutineScope.launch(Dispatchers.IO) {
                                TorrServerManager.ensureServerAvailable(context, TorrServerManager.getTorrHost(context))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingStreams = false
                }
            }
        }
    }

    // Video preview in details screen (responsive 1.2s delay with dynamic Referer headers)
    LaunchedEffect(currentMovie.id, streamOptions.size) {
        delay(1200)
        if (detailsPreviewPlayer == null && !isResolving) {
            val previewStartMin = prefs.getInt("pref_preview_start_min", 12)
            val targetSeekMs = previewStartMin * 60 * 1000L

            val streamUrl = withContext(Dispatchers.IO) {
                // Step 1: Server API preview stream FIRST (Delivembd/Collaps direct HLS, unblocked, 200-300ms)
                var sUrl: String? = null
                try {
                    val candidate = kotlinx.coroutines.withTimeoutOrNull(2500) {
                        ShowHubApiClient.fetchPreviewStream(currentMovie, previewStartMin)
                    }
                    if (candidate != null && isDirectVideoStream(candidate)) {
                        sUrl = candidate
                    }
                } catch (_: Exception) {}

                // Step 2: Use already-resolved native streams from streamOptions
                if (sUrl.isNullOrEmpty()) {
                    sUrl = pickSafePreviewStream(streamOptions)
                }
                if (sUrl.isNullOrEmpty() && !currentMovie.videoUrl.isNullOrBlank() && isDirectVideoStream(currentMovie.videoUrl)) {
                    val cu = currentMovie.videoUrl.lowercase()
                    if (!cu.contains("rhtie") && !cu.contains("zrkms") && !cu.contains("trial") && !cu.contains("promo") && !cu.contains("teaser") &&
                        !cu.contains("vip") && !cu.contains("ultra") && !cu.contains("premium") && !cu.contains("/1/4/4/4/3/4/3/") && !cu.contains("/1/5/3/6/4/2/4/")) {
                        sUrl = currentMovie.videoUrl
                    }
                }
                // Step 3: Native Rezka resolver on-device fallback
                if (sUrl.isNullOrEmpty()) {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(2000) {
                            val rezkaMediaUrl = if (currentMovie.id.startsWith("http") || currentMovie.id.contains("hdrezka") || currentMovie.id.startsWith("rezka:")) {
                                currentMovie.id
                            } else null
                            val nativeStreams = RezkaNativeResolver.resolveStreams(
                                title = currentMovie.title,
                                year = currentMovie.releaseYear,
                                isSeries = currentMovie.isSeries,
                                season = if (currentMovie.isSeries) selectedSeason else 1,
                                episode = if (currentMovie.isSeries) selectedEpisode else 1,
                                mediaUrl = rezkaMediaUrl,
                                originalTitle = currentMovie.originalTitle
                            )
                            sUrl = pickSafePreviewStream(nativeStreams)
                        }
                    } catch (_: Exception) {}
                }

                // Step 4: Native Filmix resolver on-device fallback
                if (sUrl.isNullOrEmpty()) {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(2000) {
                            val fxStreams = FilmixNativeResolver.resolveStreams(
                                movieId = currentMovie.id,
                                title = currentMovie.title,
                                year = currentMovie.releaseYear,
                                isSeries = currentMovie.isSeries,
                                season = if (currentMovie.isSeries) selectedSeason else 1,
                                episode = if (currentMovie.isSeries) selectedEpisode else 1,
                                isPro = false,
                                isProPlus = false
                            )
                            sUrl = pickSafePreviewStream(fxStreams)
                        }
                    } catch (_: Exception) {}
                }
                sUrl
            }

            val validStreamUrl = streamUrl
            if (!isResolving && detailsPreviewPlayer == null && !validStreamUrl.isNullOrEmpty() && isDirectVideoStream(validStreamUrl)) {
                try {
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .setConnectTimeoutMs(8000)
                        .setReadTimeoutMs(15000)
                        .setAllowCrossProtocolRedirects(true)
                    val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
                        val u = dataSpec.uri.toString().lowercase()
                        val headers = HashMap<String, String>(dataSpec.httpRequestHeaders)
                        when {
                            u.contains("interkh") || u.contains("namy.ws") || u.contains("embess.ws") || u.contains("nextembed.ws") || u.contains("voidboost") -> {
                                headers["Referer"] = "https://api.namy.ws/"
                                headers["Origin"] = "https://api.namy.ws"
                            }
                            u.contains("filmix") || u.contains("werkecdn") || u.contains("cdnsqu") -> {
                                headers["Referer"] = "https://filmix.my/"
                            }
                            u.contains("bazon") -> {
                                headers["Referer"] = "https://bazon.cc/"
                            }
                            else -> {
                                headers["Referer"] = "https://hdrezka.ag/"
                            }
                        }
                        dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
                    }
                    val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory)

                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            /* minBufferMs = */ 8000,
                            /* maxBufferMs = */ 20000,
                            /* bufferForPlaybackMs = */ 1500,
                            /* bufferForPlaybackAfterRebufferMs = */ 2500
                        )
                        .setBackBuffer(2000, true)
                        .build()
                    val renderersFactory = DefaultRenderersFactory(context)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

                    var hasSeeked = false
                    val player = ExoPlayer.Builder(context, renderersFactory)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setLoadControl(loadControl)
                        .build().apply {
                            setMediaItem(MediaItem.fromUri(validStreamUrl), targetSeekMs)
                            seekTo(targetSeekMs)
                            volume = 0f
                            repeatMode = Player.REPEAT_MODE_ALL
                            addListener(object : Player.Listener {
                                private fun performSafeSeek() {
                                    if (hasSeeked) return
                                    val dur = duration
                                    if (dur > 60_000L) {
                                        hasSeeked = true
                                        val safeSeek = if (targetSeekMs in 1 until dur) {
                                            targetSeekMs
                                        } else {
                                            (dur * 0.20).toLong().coerceAtLeast(0L)
                                        }
                                        if (Math.abs(currentPosition - safeSeek) > 5000L) {
                                            seekTo(safeSeek)
                                        }
                                    }
                                }

                                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                                    // Seek is executed reliably in onPlaybackStateChanged(STATE_READY)
                                }

                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state == Player.STATE_READY) {
                                        val dur = duration
                                        if (dur in 1..60_000L) {
                                            stop()
                                            isDetailsPreviewPlaying = false
                                            return
                                        }
                                        if (!hasSeeked) {
                                            hasSeeked = true
                                            val safeSeek = if (dur > 0 && targetSeekMs in 1 until dur) {
                                                targetSeekMs
                                            } else if (dur > 60_000L) {
                                                (dur * 0.20).toLong()
                                            } else {
                                                targetSeekMs
                                            }
                                            if (Math.abs(currentPosition - safeSeek) > 5000L) {
                                                seekTo(safeSeek)
                                            }
                                        }
                                        isDetailsPreviewPlaying = true
                                    } else if (state == Player.STATE_ENDED) {
                                        val dur = duration
                                        val loopSeek = if (dur > 0 && targetSeekMs in 1 until dur) {
                                            targetSeekMs
                                        } else {
                                            0L
                                        }
                                        seekTo(loopSeek)
                                        play()
                                    }
                                }

                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    isDetailsPreviewPlaying = false
                                }
                            })
                            prepare()
                            playWhenReady = true
                        }
                    detailsPreviewPlayer = player
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, detailsPreviewPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    com.example.tvmediaapp.data.history.WatchHistoryManager.notifyHistoryChanged()
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    detailsPreviewPlayer?.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isDetailsPreviewPlaying = false
            val playerToRelease = detailsPreviewPlayer
            detailsPreviewPlayer = null
            if (playerToRelease != null) {
                try {
                    playerToRelease.clearMediaItems()
                    playerToRelease.stop()
                    playerToRelease.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun startPlayback(
        targetSeason: Int = selectedSeason,
        targetEpisode: Int = selectedEpisode,
        targetAudioId: String = selectedAudioId,
        startPos: Long = 0L
    ) {
        if (isResolving) return
        isResolving = true
        streamStatus = "Поиск прямого HLS потока..."

        // Stop background preview before entering player
        val playerToStop = detailsPreviewPlayer
        detailsPreviewPlayer = null
        isDetailsPreviewPlaying = false
        if (playerToStop != null) {
            try {
                playerToStop.stop()
                playerToStop.clearMediaItems()
                playerToStop.release()
            } catch (_: Exception) {}
        }

        coroutineScope.launch {
            val isContentSeries = currentMovie.isSeries || currentMovie.seasons.isNotEmpty() || targetSeason > 1 || targetEpisode > 1
            // Priority 1: Query Rezka + Filmix directly on TV (residential IP) and server concurrently
            val nativeDeferred = async {
                val rezkaMediaUrl = if (currentMovie.id.startsWith("http") || currentMovie.id.contains("hdrezka") || currentMovie.id.startsWith("rezka:")) {
                    currentMovie.id
                } else null
                val rzJob = async {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(4500L) {
                            RezkaNativeResolver.resolveStreams(
                                title = currentMovie.title,
                                year = currentMovie.releaseYear,
                                isSeries = isContentSeries,
                                season = targetSeason,
                                episode = targetEpisode,
                                translatorId = targetAudioId.ifEmpty { null },
                                mediaUrl = rezkaMediaUrl,
                                originalTitle = currentMovie.originalTitle
                            )
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
                val fxJob = async {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(4500L) {
                            FilmixNativeResolver.resolveStreams(
                                movieId = currentMovie.id,
                                title = currentMovie.title,
                                year = currentMovie.releaseYear,
                                isSeries = isContentSeries,
                                season = targetSeason,
                                episode = targetEpisode,
                                audioId = targetAudioId,
                                isPro = isFilmixPro,
                                isProPlus = isFilmixProPlus
                            )
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
                rzJob.await() + fxJob.await()
            }
            val serverDeferred = async {
                ShowHubApiClient.fetchStreams(
                    movie = currentMovie.copy(isSeries = isContentSeries),
                    season = if (isContentSeries) targetSeason else null,
                    episode = if (isContentSeries) targetEpisode else null,
                    audioId = targetAudioId
                )
            }

            val nativeStreams = nativeDeferred.await()
            val serverStreams = serverDeferred.await()
            val isNativeFallback = nativeStreams.isNotEmpty() && nativeStreams.all { it.source.contains("fallback", ignoreCase = true) }
            val hasServerDirect = serverStreams.any { isDirectVideoStream(it.url) && !it.source.contains("torrent", ignoreCase = true) }
            val combined = if (!isNativeFallback && nativeStreams.isNotEmpty()) {
                val nonRezkaServer = serverStreams.filterNot { it.source.contains("rezka", ignoreCase = true) }
                val rezkaServer = serverStreams.filter { it.source.contains("rezka", ignoreCase = true) }
                if ((hasServerDirect || currentMovie.source == "filmix") && nonRezkaServer.isNotEmpty()) {
                    (nonRezkaServer + nativeStreams + rezkaServer).distinctBy { it.url }
                } else {
                    (nativeStreams + serverStreams).distinctBy { it.url }
                }
            } else if ((hasServerDirect || currentMovie.source == "filmix" || isNativeFallback) && serverStreams.isNotEmpty()) {
                (serverStreams + nativeStreams).distinctBy { it.url }
            } else {
                (nativeStreams + serverStreams).distinctBy { it.url }
            }
            // Sort direct streams (HLS/MP4) first, balancers last
            val streams = combined.sortedByDescending { isDirectVideoStream(it.url) }

            streamOptions = streams
            isResolving = false

            // Diagnostic: log stream counts
            val hlsCount = streams.count { isDirectVideoStream(it.url) }
            val embedCount = streams.size - hlsCount
            val sourceSummary = streams.groupBy { it.source }.entries.joinToString(", ") { (src, list) ->
                val h = list.count { isDirectVideoStream(it.url) }
                val e = list.size - h
                "$src: ${if (h > 0) "${h} HLS" else ""}${if (h > 0 && e > 0) "+" else ""}${if (e > 0) "${e} embed" else ""}"
            }
            android.util.Log.d("StartPlayback", "Found ${streams.size} streams: $hlsCount HLS, $embedCount embed. Sources: $sourceSummary")

            val notFoundMsg = if (isContentSeries) {
                if (currentMovie.audioTracks.size > 1) "Поток недоступен для выбранной серии. Попробуйте другую озвучку."
                else "Поток недоступен для выбранной серии."
            } else {
                if (currentMovie.audioTracks.size > 1) "Поток недоступен для этого фильма. Попробуйте другую озвучку."
                else "Поток недоступен для этого фильма. Попробуйте другой источник."
            }

            if (streams.isNotEmpty()) {
                val cleanStreams = streams.filter { !isStreamStub(it) }
                val targetPool = if (cleanStreams.isNotEmpty()) cleanStreams else streams
                val candidateStreams = if (selectedSourceFilter == "Все" || selectedSourceFilter.startsWith("Все")) {
                    cleanStreams.ifEmpty { targetPool }
                } else {
                    val sKey = selectedSourceFilter.lowercase()
                    val filteredClean = cleanStreams.filter { st ->
                        val stSrc = st.source.lowercase()
                        when {
                            sKey.contains("kodik") -> stSrc.contains("kodik")
                            sKey.contains("rezka") -> stSrc.contains("rezka")
                            sKey.contains("filmix") -> stSrc.contains("filmix")
                            sKey.contains("videocdn") -> stSrc.contains("videocdn") || st.url.contains("allarknow") || st.url.contains("bayas") || st.url.contains("videoframe")
                            sKey.contains("collaps") || sKey.contains("delivembd") -> stSrc.contains("collaps") || stSrc.contains("delivembd") || st.url.contains("interkh") || st.url.contains("namy.ws")
                            sKey.contains("bazon") -> stSrc.contains("bazon")
                            sKey.contains("zona") -> stSrc.contains("zona") || stSrc.contains("torrent") || stSrc.contains("p2p")
                            sKey.contains("торрент") || sKey.contains("torrent") -> stSrc.contains("torrent") || stSrc.contains("zona")
                            else -> stSrc.contains(sKey)
                        }
                    }
                    if (filteredClean.isEmpty()) {
                        streamStatus = "⚠️ Источник «$selectedSourceFilter» не содержит доступных потоков. Выберите другой источник или «Все»."
                        return@launch
                    }
                    filteredClean
                }

                val matched = candidateStreams.firstOrNull { matchStreamQuality(it, selectedQuality) && !isStreamStub(it) }
                    ?: candidateStreams.firstOrNull { matchStreamQuality(it, selectedQuality) }
                    ?: candidateStreams.filter { isDirectVideoStream(it.url) && !isStreamStub(it) }
                        .maxByOrNull { getStreamQualityRank(it.quality) }
                    ?: candidateStreams.filter { isDirectVideoStream(it.url) && !it.quality.contains("ultra", ignoreCase = true) && !it.quality.contains("4k", ignoreCase = true) && !it.quality.contains("premium", ignoreCase = true) }
                        .maxByOrNull { getStreamQualityRank(it.quality) }
                    ?: candidateStreams.filter { isDirectVideoStream(it.url) }
                        .maxByOrNull { getStreamQualityRank(it.quality) }
                    ?: candidateStreams.maxByOrNull { getStreamQualityRank(it.quality) }
                    ?: candidateStreams.firstOrNull()

                if (matched != null && isStreamStub(matched)) {
                    streamStatus = "⚠️ Выбранный поток требует подписку (PRO/Премиум). Выберите 720p или другой источник."
                    return@launch
                }

                val isHls = matched != null && isDirectVideoStream(matched.url)
                if (matched != null) {
                    android.util.Log.d("StartPlayback", "Selected: ${matched.quality} from ${matched.source} (${if (isHls) "HLS" else "EMBED"}) url=${matched.url.take(80)}")
                }

                if (matched != null && matched.url.isNotBlank() && matched.url.startsWith("http")) {
                    val isTorrent = matched.source.contains("torrent", ignoreCase = true) ||
                            matched.url.contains(":8090") ||
                            matched.url.contains("/stream?link=") ||
                            matched.quality.contains("P2P", ignoreCase = true)
                    val torrHost = TorrServerManager.getTorrHost(context)
                    val adjustedUrl = if (isTorrent && matched.url.contains("127.0.0.1:8090") && torrHost != "http://127.0.0.1:8090") {
                        matched.url.replace("http://127.0.0.1:8090", torrHost)
                    } else matched.url

                    if (isTorrent) {
                        val isAlive = TorrServerManager.ensureServerAvailable(context, torrHost)
                        if (!isAlive) {
                            pendingTorrStream = matched.copy(url = adjustedUrl)
                            showTorrServerDialog = true
                            return@launch
                        }
                    }

                    streamStatus = "▶ ${matched.quality} (${matched.source}, ${if (isHls) "HLS" else "IFRAME"}) | Всего: $hlsCount HLS, $embedCount embed"
                    val movieToPlay = (if (isContentSeries) currentMovie.copy(isSeries = true) else currentMovie)
                        .copy(source = matched.source, videoUrl = adjustedUrl, streams = streams)
                    onPlayClick(movieToPlay, adjustedUrl, startPos, targetSeason, targetEpisode, targetAudioId)
                } else {
                    streamStatus = notFoundMsg
                    // Auto-report: streams expected but nothing playable found
                    ShowHubApiClient.sendBugReport(
                        movie = currentMovie,
                        type = "streams_unavailable",
                        description = "Playback failed: matched stream URL blank/invalid. Sources found: $sourceSummary. Filter: $selectedSourceFilter",
                        autoReport = true
                    )
                }
            } else {
                streamStatus = notFoundMsg
                // Auto-report: no streams at all for this media
                if (currentMovie.sources.isNotEmpty() || availableSourcesInfo.isNotEmpty()) {
                    ShowHubApiClient.sendBugReport(
                        movie = currentMovie,
                        type = "streams_unavailable",
                        description = "Zero streams returned despite sources listed in card. Filter: $selectedSourceFilter",
                        autoReport = true
                    )
                }
            }
        }
    }

    val screenBg = LocalBackgroundColor.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // High-res backdrop — vivid but not overwhelming
        AsyncImage(
            model = currentMovie.backdropUrl.ifEmpty { currentMovie.posterUrl },
            contentDescription = currentMovie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.40f)
        )

        // Multi-layer gradient overlay for depth and readability
        // Horizontal: left side darker (content area), right lighter (backdrop visible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            screenBg.copy(alpha = 0.94f),
                            screenBg.copy(alpha = 0.82f),
                            screenBg.copy(alpha = 0.60f)
                        )
                    )
                )
        )
        // Vertical: bottom darker for controls readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            screenBg.copy(alpha = 0.30f),
                            screenBg.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Two-pane Layout (More breathing room and spacious composition)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 42.dp, top = 30.dp, end = 42.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp)
        ) {
            // LEFT PANE: Focusable Poster & Metadata Card
            var isLeftPaneFocused by remember { mutableStateOf(false) }
            val leftPaneScrollState = rememberScrollState()
            Card(
                onClick = { /* keep focus */ },
                colors = CardDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.04f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f)
                ),
                border = CardDefaults.border(
                    border = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.1f))),
                    focusedBorder = Border(BorderStroke(2.dp, focusColor))
                ),
                scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .focusRequester(leftPaneFocusRequester)
                    .focusProperties {
                        right = playButtonFocusRequester
                    }
                    .onFocusChanged { isLeftPaneFocused = it.isFocused }
                    .onPreviewKeyEvent { evt ->
                        if (evt.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            if (evt.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                coroutineScope.launch { leftPaneScrollState.animateScrollTo(leftPaneScrollState.value + 220) }
                                true
                            } else if (evt.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && leftPaneScrollState.value > 0) {
                                coroutineScope.launch { leftPaneScrollState.animateScrollTo((leftPaneScrollState.value - 220).coerceAtLeast(0)) }
                                true
                            } else false
                        } else false
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(leftPaneScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, if (isLeftPaneFocused) accent else accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = currentMovie.posterUrl,
                            contentDescription = currentMovie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Details Poster Video Preview (smooth crossfade)
                        if (isDetailsPreviewPlaying && detailsPreviewPlayer != null) {
                            val previewAlpha by animateFloatAsState(
                                targetValue = if (isDetailsPreviewPlaying) 1f else 0f,
                                animationSpec = tween(durationMillis = 600),
                                label = "detailsPreviewFade"
                            )
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = detailsPreviewPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                        isClickable = false
                                    }
                                },
                                update = { view ->
                                    view.player = detailsPreviewPlayer
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(previewAlpha)
                                    .clip(RoundedCornerShape(8.dp))
                                    .focusProperties { canFocus = false }
                            )

                            // Preview Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AppIcon(
                                        resId = R.drawable.ic_play_arrow,
                                        tint = accent,
                                        size = 12.dp
                                    )
                                    Text(
                                        text = "ПРЕДПРОСМОТР",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ratings Row (Age limit, KP, IMDb)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentMovie.ageRating.isNotBlank()) {
                            val rawAge = currentMovie.ageRating.trim()
                            val cleanAge = when {
                                rawAge.contains("18") -> "18+"
                                rawAge.contains("16") -> "16+"
                                rawAge.contains("12") -> "12+"
                                rawAge.contains("6") -> "6+"
                                rawAge.contains("0") -> "0+"
                                else -> rawAge.take(6)
                            }
                            val (ageBg, ageFg) = when {
                                cleanAge.contains("18") -> Color(0xFFD32F2F) to Color.White
                                cleanAge.contains("16") -> Color(0xFFF57C00) to Color.White
                                cleanAge.contains("12") -> Color(0xFF1976D2) to Color.White
                                cleanAge.contains("6") || cleanAge.contains("0") -> Color(0xFF388E3C) to Color.White
                                else -> Color.Black.copy(alpha = 0.85f) to Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ageBg)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cleanAge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ageFg,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (currentMovie.ratingLampa > 0) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "★ ${String.format(java.util.Locale.US, "%.1f", currentMovie.ratingLampa)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (currentMovie.ratingKp > 0) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KpOrange)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "КП ${String.format(java.util.Locale.US, "%.1f", currentMovie.ratingKp)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (currentMovie.ratingImdb > 0) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ImdbGold)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "IMDb ${String.format(java.util.Locale.US, "%.1f", currentMovie.ratingImdb)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (currentMovie.ratingKp <= 0 && currentMovie.ratingImdb <= 0 && currentMovie.ratingLampa <= 0 && currentMovie.rating > 0) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "★ ${String.format(java.util.Locale.US, "%.1f", currentMovie.rating)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Metadata Badges
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentMovie.director.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Режиссёр:", fontSize = 12.sp, color = TextGray)
                                Text(text = currentMovie.director, fontSize = 12.sp, color = TextWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (currentMovie.actors.isNotBlank()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "В ролях:", fontSize = 12.sp, color = TextGray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentMovie.actors,
                                    fontSize = 11.sp,
                                    color = TextWhite,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (currentMovie.country.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Страна:", fontSize = 12.sp, color = TextGray)
                                Text(text = currentMovie.country, fontSize = 12.sp, color = TextWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (currentMovie.releaseYear.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Год:", fontSize = 12.sp, color = TextGray)
                                Text(text = currentMovie.releaseYear, fontSize = 12.sp, color = TextWhite)
                            }
                        }
                        if (currentMovie.duration.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Длительность:", fontSize = 12.sp, color = TextGray)
                                Text(text = currentMovie.duration, fontSize = 12.sp, color = TextWhite)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Тип:", fontSize = 12.sp, color = TextGray)
                            Text(
                                text = if (currentMovie.isSeries) {
                                    when {
                                        currentMovie.genres.any { it.contains("аниме", ignoreCase = true) } -> "Аниме-сериал"
                                        currentMovie.genres.any { it.contains("мульт", ignoreCase = true) } -> "Мультсериал"
                                        else -> "Сериал"
                                    }
                                } else {
                                    when {
                                        currentMovie.genres.any { it.contains("аниме", ignoreCase = true) } -> "Аниме"
                                        currentMovie.genres.any { it.contains("мульт", ignoreCase = true) } -> "Мультфильм"
                                        else -> "Фильм"
                                    }
                                },
                                fontSize = 12.sp,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Источник:", fontSize = 12.sp, color = TextGray)
                            Text(
                                text = selectedSourceFilter,
                                fontSize = 12.sp,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Качество:", fontSize = 12.sp, color = TextGray)
                            Text(
                                text = selectedQuality,
                                fontSize = 12.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (cleanAudioTrackName.isNotBlank() && !cleanAudioTrackName.equals("Озвучка", ignoreCase = true)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Озвучка:", fontSize = 12.sp, color = TextGray)
                                Text(
                                    text = cleanAudioTrackName,
                                    fontSize = 12.sp,
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (currentMovie.isSeries) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Серия:", fontSize = 12.sp, color = TextGray)
                                Text(
                                    text = "S$selectedSeason E$selectedEpisode",
                                    fontSize = 12.sp,
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT PANE: Details, Translators, Seasons, Episodes & Actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rightPaneScrollState)
            ) {
                val cleanTitle = currentMovie.title.unescapeHtml()
                val titleWithYear = buildString {
                    append(cleanTitle)
                    val cleanYear = currentMovie.releaseYear.replace("null", "").trim()
                    if (cleanYear.isNotEmpty() && !cleanTitle.contains(cleanYear)) {
                        append(" ($cleanYear)")
                    }
                }
                Text(
                    text = titleWithYear,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    lineHeight = 36.sp
                )

                val origTitle = currentMovie.originalTitle.unescapeHtml()
                if (origTitle.isNotBlank() && !origTitle.equals("null", ignoreCase = true) && origTitle != cleanTitle) {
                    Text(
                        text = origTitle,
                        fontSize = 15.sp,
                        color = TextGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentMovie.genres.joinToString(" • "),
                    fontSize = 13.sp,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )

                if (newEpisodesCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val targetSeason = currentMovie.seasons.lastOrNull()?.seasonNumber ?: selectedSeason
                            val targetEp = currentMovie.seasons.lastOrNull()?.episodes?.lastOrNull()?.episodeNumber ?: selectedEpisode
                            selectedSeason = targetSeason
                            selectedEpisode = targetEp
                            historyManager.clearNewEpisodes(currentMovie.id)
                            newEpisodesCount = 0
                            startPlayback(targetSeason = targetSeason, targetEpisode = targetEp, startPos = 0L)
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFE53935),
                            focusedContainerColor = Color(0xFFFF5252),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚡ Доступно +$newEpisodesCount новых серий — Смотреть новейшую!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Prominent Synopsis directly under Title & Genres
                if (currentMovie.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentMovie.description.unescapeHtml(),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = TextWhite.copy(alpha = 0.88f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(10.dp)
                    )
                }

                if (currentMovie.actors.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "В ролях: ${currentMovie.actors}",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextWhite.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quality Selector Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Качество:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextGray
                    )
                    availableQualities.forEach { q ->
                        val isSelected = selectedQuality.equals(q, ignoreCase = true) ||
                                (q == "4K Ultra" && (selectedQuality.contains("ultra", ignoreCase = true) || selectedQuality.contains("4k", ignoreCase = true)))
                        Button(
                            onClick = {
                                selectedQuality = q
                                prefs.edit().putString("pref_quality", q).apply()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = focusColor,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Text(
                                text = q,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Options Indicator Bar (Source, Quality, Audio, Episode)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    // Source Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "Источник:", fontSize = 11.sp, color = TextGray)
                            Text(text = selectedSourceFilter, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quality Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "Качество:", fontSize = 11.sp, color = TextGray)
                            Text(text = selectedQuality, fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Audio Track Chip
                    if (cleanAudioTrackName.isNotBlank() && !cleanAudioTrackName.equals("Озвучка", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "Озвучка:", fontSize = 11.sp, color = TextGray)
                                Text(
                                    text = cleanAudioTrackName,
                                    fontSize = 11.sp,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Series / Episode Chip
                    if (currentMovie.isSeries) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "Серия:", fontSize = 11.sp, color = TextGray)
                                Text(
                                    text = "S$selectedSeason E$selectedEpisode",
                                    fontSize = 11.sp,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Action Buttons - Row 1 (Playback Actions)
                val scrollUpMod = Modifier.onPreviewKeyEvent { evt ->
                    if (evt.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && evt.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (rightPaneScrollState.value > 0) {
                            coroutineScope.launch { rightPaneScrollState.animateScrollTo(0) }
                            true
                        } else false
                    } else false
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasResume = savedHistory != null && savedHistory.positionMs > 10_000L
                    if (hasResume) {
                        val mins = savedHistory!!.positionMs / 60000L
                        val isContentSeries = currentMovie.isSeries || currentMovie.seasons.isNotEmpty() || (savedHistory != null && (savedHistory.season > 1 || savedHistory.episode > 1))
                        val resumeLabel = if (isContentSeries) {
                            "Продолжить (S${savedHistory.season} E${savedHistory.episode} · $selectedSourceFilter · $selectedQuality)"
                        } else {
                            "Продолжить ($mins мин · $selectedSourceFilter · $selectedQuality)"
                        }

                        Button(
                            onClick = {
                                startPlayback(
                                    targetSeason = savedHistory.season,
                                    targetEpisode = savedHistory.episode,
                                    targetAudioId = savedHistory.audioId.ifEmpty { selectedAudioId },
                                    startPos = savedHistory.positionMs
                                )
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = accent.copy(alpha = 0.70f),
                                focusedContainerColor = focusColor,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(25.dp)
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties {
                                    left = leftPaneFocusRequester
                                    right = fromStartButtonFocusRequester
                                    down = favoriteButtonFocusRequester
                                }
                                .then(scrollUpMod)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_play_arrow,
                                    tint = Color.Black,
                                    size = 14.dp
                                )
                                Text(
                                    text = resumeLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }

                        Button(
                            onClick = { startPlayback(startPos = 0L) },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = focusColor,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(25.dp)
                                .focusRequester(fromStartButtonFocusRequester)
                                .focusProperties {
                                    left = playButtonFocusRequester
                                    right = trailerButtonFocusRequester
                                    down = favoriteButtonFocusRequester
                                }
                                .then(scrollUpMod)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_replay_10,
                                    tint = androidx.tv.material3.LocalContentColor.current,
                                    size = 13.dp
                                )
                                Text(
                                    text = "С начала",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { startPlayback(startPos = 0L) },
                            colors = ButtonDefaults.colors(
                                containerColor = accent.copy(alpha = 0.70f),
                                focusedContainerColor = focusColor,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(25.dp)
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties {
                                    left = leftPaneFocusRequester
                                    right = trailerButtonFocusRequester
                                    down = favoriteButtonFocusRequester
                                }
                                .then(scrollUpMod)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_play_arrow,
                                    tint = Color.Black,
                                    size = 14.dp
                                )
                                Text(
                                    text = if (isResolving) "Поиск потока..." else buildString {
                                        append("Смотреть")
                                        append(" ($selectedSourceFilter · $selectedQuality")
                                        if (currentMovie.isSeries) {
                                            append(" · S$selectedSeason E$selectedEpisode")
                                        }
                                        append(")")
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }

                    // TRAILER — icon with tooltip
                    IconActionButton(
                        iconResId = R.drawable.ic_movie,
                        tooltip = "Трейлер",
                        focusColor = focusColor,
                        onClick = {
                            coroutineScope.launch {
                                streamStatus = "Поиск трейлера..."
                                val trailerUrl = ShowHubApiClient.fetchTrailerUrl(currentMovie)
                                if (!trailerUrl.isNullOrEmpty()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        streamStatus = null
                                    } catch (e: Exception) {
                                        streamStatus = "Ошибка запуска видео: ${e.message}"
                                    }
                                } else {
                                    streamStatus = "Трейлер не найден"
                                }
                            }
                        },
                        modifier = Modifier
                            .focusRequester(trailerButtonFocusRequester)
                            .focusProperties {
                                left = if (hasResume) fromStartButtonFocusRequester else playButtonFocusRequester
                                right = externalPlayerFocusRequester
                                down = favoriteButtonFocusRequester
                            }
                    )

                    // EXTERNAL PLAYER — icon with tooltip
                    IconActionButton(
                        iconResId = R.drawable.ic_open_in_new,
                        tooltip = "Внешний плеер",
                        focusColor = focusColor,
                        onClick = {
                            coroutineScope.launch {
                                streamStatus = "Получение ссылки для стороннего плеера..."
                                var streams = streamOptions
                                if (streams.isEmpty()) {
                                    val rezkaMediaUrl = if (currentMovie.id.startsWith("http") || currentMovie.id.contains("hdrezka") || currentMovie.id.startsWith("rezka:")) {
                                        currentMovie.id
                                    } else null
                                    val nativeDeferred = async {
                                        val rzJob = async {
                                            try {
                                                RezkaNativeResolver.resolveStreams(
                                                    title = currentMovie.title,
                                                    year = currentMovie.releaseYear,
                                                    isSeries = currentMovie.isSeries,
                                                    season = selectedSeason,
                                                    episode = selectedEpisode,
                                                    translatorId = selectedAudioId.ifEmpty { null },
                                                    mediaUrl = rezkaMediaUrl,
                                                    originalTitle = currentMovie.originalTitle
                                                )
                                            } catch (_: Exception) { emptyList() }
                                        }
                                        val fxJob = async {
                                            try {
                                                FilmixNativeResolver.resolveStreams(
                                                    movieId = currentMovie.id,
                                                    title = currentMovie.title,
                                                    year = currentMovie.releaseYear,
                                                    isSeries = currentMovie.isSeries,
                                                    season = selectedSeason,
                                                    episode = selectedEpisode,
                                                    audioId = selectedAudioId,
                                                    isPro = isFilmixPro,
                                                    isProPlus = isFilmixProPlus
                                                )
                                            } catch (_: Exception) { emptyList() }
                                        }
                                        rzJob.await() + fxJob.await()
                                    }
                                    val serverDeferred = async {
                                        ShowHubApiClient.fetchStreams(
                                            movie = currentMovie,
                                            season = if (currentMovie.isSeries) selectedSeason else null,
                                            episode = if (currentMovie.isSeries) selectedEpisode else null,
                                            audioId = selectedAudioId
                                        )
                                    }
                                    val nativeStreams = nativeDeferred.await()
                                    val serverStreams = serverDeferred.await()
                                    val isNativeFallback = nativeStreams.isNotEmpty() && nativeStreams.all { it.source.contains("fallback", ignoreCase = true) }
                                    streams = if ((currentMovie.source == "filmix" || isNativeFallback) && serverStreams.isNotEmpty()) {
                                        (serverStreams + nativeStreams).distinctBy { it.url }
                                    } else {
                                        (nativeStreams + serverStreams).distinctBy { it.url }
                                    }
                                }
                                if (streams.isNotEmpty()) {
                                    val matched = streams.firstOrNull { matchStreamQuality(it, selectedQuality) }
                                        ?: streams.firstOrNull { isDirectVideoStream(it.url) && !it.quality.contains("ultra", ignoreCase = true) && !it.quality.contains("4k", ignoreCase = true) }
                                        ?: streams.firstOrNull { isDirectVideoStream(it.url) }
                                        ?: streams.first()
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            val uri = Uri.parse(matched.url)
                                            val mime = if (matched.url.contains(".m3u8")) "application/x-mpegURL" else "video/*"
                                            setDataAndType(uri, mime)
                                            putExtra("title", currentMovie.title)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Выберите видеоплеер"))
                                        streamStatus = null
                                    } catch (e: Exception) {
                                        try {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(matched.url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(webIntent)
                                            streamStatus = null
                                        } catch (e2: Exception) {
                                            streamStatus = "Не найден внешний плеер"
                                        }
                                    }
                                } else {
                                    streamStatus = "Потоки не найдены"
                                }
                            }
                        },
                        modifier = Modifier
                            .focusRequester(externalPlayerFocusRequester)
                            .focusProperties {
                                left = trailerButtonFocusRequester
                                right = favoriteButtonFocusRequester
                                down = backButtonFocusRequester
                            }
                    )

                    // FAVORITE — icon with tooltip
                    IconActionButton(
                        iconResId = if (isFav) R.drawable.ic_star else R.drawable.ic_star_border,
                        tooltip = if (isFav) "В избранном" else "В избранное",
                        focusColor = focusColor,
                        iconTintOverride = if (isFav) FavoriteGold else null,
                        onClick = {
                            isFav = !isFav
                            onToggleFavorite(currentMovie)
                        },
                        modifier = Modifier
                            .focusRequester(favoriteButtonFocusRequester)
                            .focusProperties {
                                left = externalPlayerFocusRequester
                                up = playButtonFocusRequester
                                right = backButtonFocusRequester
                                down = tabsFocusRequester
                            }
                    )

                    // BACK — icon with tooltip
                    IconActionButton(
                        iconResId = R.drawable.ic_arrow_back,
                        tooltip = "Назад",
                        focusColor = focusColor,
                        onClick = onBackClick,
                        modifier = Modifier
                            .focusRequester(backButtonFocusRequester)
                            .focusProperties {
                                left = favoriteButtonFocusRequester
                                up = externalPlayerFocusRequester
                                right = bugReportFocusRequester
                                down = tabsFocusRequester
                            }
                    )

                    // BUG REPORT — icon with tooltip
                    var showBugReportMenu by remember { mutableStateOf(false) }
                    var bugReportSent by remember { mutableStateOf(false) }
                    IconActionButton(
                        iconResId = R.drawable.ic_flag,
                        tooltip = if (bugReportSent) "Отправлено ✓" else "Сообщить о проблеме",
                        focusColor = focusColor,
                        onClick = { showBugReportMenu = !showBugReportMenu },
                        modifier = Modifier
                            .focusRequester(bugReportFocusRequester)
                            .focusProperties {
                                left = backButtonFocusRequester
                                up = externalPlayerFocusRequester
                                down = tabsFocusRequester
                            }
                    )

                    // Bug report inline submenu
                    if (showBugReportMenu) {
                        val bugTypes = listOf(
                            "streams_unavailable" to "Потоки недоступны",
                            "wrong_metadata" to "Неверные данные",
                            "other" to "Другая проблема"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bugTypes.forEach { (type, label) ->
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            ShowHubApiClient.sendBugReport(
                                                movie = currentMovie,
                                                type = type,
                                                description = label,
                                                autoReport = false
                                            )
                                        }
                                        bugReportSent = true
                                        showBugReportMenu = false
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        focusedContainerColor = focusColor,
                                        contentColor = TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Informative Card Data & Stream Status message with NeonSpinner
                val isCardDataLoading = isLoadingDetails || isLoadingStreams
                if (isCardDataLoading || isResolving || streamStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isResolving || isCardDataLoading) {
                            NeonSpinner(size = 18.dp, strokeWidth = 2.2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val statusText = when {
                            isResolving -> streamStatus ?: "Поиск наилучшего видеопотока..."
                            isLoadingDetails -> "Подгрузка данных карточки (сезоны, озвучки, описание)..."
                            isLoadingStreams && availableSourcesInfo.isNotEmpty() -> "Поиск потоков... Доступно источников: ${availableSourcesInfo.size} (${availableSourcesInfo.joinToString(", ") { it.name }})"
                            isLoadingStreams -> "Поиск источников и видеопотоков (Collaps, HDRezka, Kodik, Filmix, AniLibria)..."
                            streamStatus != null -> streamStatus!!
                            availableSourcesInfo.isNotEmpty() -> "✓ Доступно источников: ${availableSourcesInfo.size} (${availableSourcesInfo.joinToString(", ") { it.name }})"
                            else -> null
                        }
                        if (statusText != null) {
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = if (isResolving || isCardDataLoading) accent else if (statusText.startsWith("▶") || statusText.startsWith("✓") || statusText.contains("Найден")) accent else Color(0xFFF87171)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))



                // Detail Section Tabs: «Плеер и серии» (сериалы) / «Плеер» (фильмы), «График серий», «Описание и детали», «Отзывы (N)»
                val playerTabTitle = if (currentMovie.isSeries) "Плеер и серии" else "Плеер"
                val tabs = remember(currentMovie.isSeries, currentMovie.episodesSchedule.size, comments.size) {
                    val list = mutableListOf(playerTabTitle)
                    if (currentMovie.isSeries) {
                        list.add("График серий" + if (currentMovie.episodesSchedule.isNotEmpty()) " (${currentMovie.episodesSchedule.size})" else "")
                    }
                    list.add("Описание и детали")
                    list.add("Отзывы" + if (comments.isNotEmpty()) " (${comments.size})" else "")
                    list
                }
                val activeTabTitle = tabs.getOrNull(selectedDetailTab) ?: tabs.firstOrNull() ?: playerTabTitle


                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedDetailTab == index
                        val tabMod = Modifier
                            .height(24.dp)
                            .then(if (index == 0) Modifier.focusRequester(tabsFocusRequester) else Modifier)
                            .focusProperties {
                                up = favoriteButtonFocusRequester
                                if (index == 0) {
                                    left = leftPaneFocusRequester
                                }
                            }
                        Button(
                            onClick = { selectedDetailTab = index },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = focusColor,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            modifier = tabMod.onFocusChanged {
                                if (it.isFocused) {
                                    selectedDetailTab = index
                                }
                            }
                        ) {
                            Text(
                                text = tabTitle,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when {
                    activeTabTitle.startsWith("Плеер") -> {
                        // TAB 0: ПЛЕЕР И СЕРИИ
                        // Resource / Source selector
                        // === 1. SOURCE SELECTOR (HLS first, with type badge + best quality) ===
                        if (availableSourcesInfo.isNotEmpty()) {
                            Text(
                                text = "Источник:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // "Все" chip + source chips
                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // "Все" chip at index 0
                                item {
                                    val isAllSelected = selectedSourceFilter.equals("Все", ignoreCase = true)
                                    val allMod = Modifier.focusRequester(firstSourceFocusRequester).focusProperties {
                                        up = tabsFocusRequester
                                        down = if (availableQualities.isNotEmpty()) firstQualityFocusRequester
                                               else if (filteredAudioTracks.isNotEmpty()) firstAudioFocusRequester
                                               else if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) firstSeasonFocusRequester
                                               else if (currentMovie.isSeries) episodesFocusRequester
                                               else FocusRequester.Default
                                    }
                                    Button(
                                        onClick = { selectedSourceFilter = "Все"; streamStatus = "Источник: Все" },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isAllSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor,
                                            contentColor = if (isAllSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(Border.None, Border.None),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp).then(allMod)
                                    ) {
                                        Text(text = "Все", fontSize = 10.sp, fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                                 // Source chips with type badge and best quality
                                itemsIndexed(availableSourcesInfo) { _, srcInfo ->
                                    val isSrcSelected = selectedSourceFilter.equals(srcInfo.name, ignoreCase = true)
                                    val sKey = srcInfo.name.lowercase()
                                    val isTorrentP2P = sKey.contains("zona") || sKey.contains("торрент") || sKey.contains("torrent")
                                    val typeBadge = if (isTorrentP2P) "P2P" else if (srcInfo.isHls) "HLS" else "IFRAME"
                                    val typeColor = if (isTorrentP2P) Color(0xFF38BDF8) else if (srcInfo.isHls) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                                    val chipLabel = buildString {
                                        append(srcInfo.name)
                                        if (srcInfo.bestQuality.isNotEmpty()) append(" · ${srcInfo.bestQuality}")
                                        if (srcInfo.epCount > 0) append(" (${srcInfo.epCount} сер.)")
                                    }
                                    Button(
                                        onClick = {
                                            selectedSourceFilter = srcInfo.name
                                            val newTracks = run {
                                                val sKey = srcInfo.name.lowercase()
                                                currentMovie.audioTracks.filter { track ->
                                                    val trackSrc = track.source.lowercase()
                                                    when {
                                                        sKey.contains("kodik") -> trackSrc.contains("kodik") || track.id.startsWith("kodik_")
                                                        sKey.contains("rezka") -> trackSrc.contains("rezka") || (!track.id.startsWith("kodik_") && !trackSrc.contains("filmix") && !trackSrc.contains("videocdn") && !trackSrc.contains("bazon"))
                                                        sKey.contains("filmix") -> trackSrc.contains("filmix")
                                                        sKey.contains("videocdn") -> trackSrc.contains("videocdn")
                                                        sKey.contains("bazon") -> trackSrc.contains("bazon")
                                                        else -> trackSrc.contains(sKey)
                                                    }
                                                }
                                            }
                                            val targetTrack = newTracks.firstOrNull { it.id == selectedAudioId } ?: newTracks.firstOrNull()
                                            if (targetTrack != null) {
                                                selectedAudioId = targetTrack.id
                                                if (currentMovie.isSeries) {
                                                    val cached = translatorSeasonsCache[targetTrack.id]
                                                    if (cached != null && cached.isNotEmpty()) {
                                                        currentMovie = currentMovie.copy(seasons = cached)
                                                        val validSeason = cached.firstOrNull { it.seasonNumber == selectedSeason } ?: cached.first()
                                                        selectedSeason = validSeason.seasonNumber
                                                        val maxEp = validSeason.episodes.maxOfOrNull { it.episodeNumber } ?: validSeason.episodes.size
                                                        if (selectedEpisode > maxEp) selectedEpisode = maxEp
                                                    } else {
                                                        coroutineScope.launch {
                                                            try {
                                                                val realSeasons = ShowHubApiClient.fetchEpisodes(currentMovie, targetTrack.id, targetTrack.source)
                                                                if (realSeasons.isNotEmpty()) {
                                                                    translatorSeasonsCache[targetTrack.id] = realSeasons
                                                                    val epCount = realSeasons.sumOf { it.episodes.size }
                                                                    val sMap = realSeasons.associate { it.seasonNumber to it.episodes.size }
                                                                    val updatedTracks = currentMovie.audioTracks.map {
                                                                        if (it.id == targetTrack.id) it.copy(episodesCount = epCount, seasonsEpisodes = sMap) else it
                                                                    }
                                                                    currentMovie = currentMovie.copy(seasons = realSeasons, audioTracks = updatedTracks)
                                                                    val validSeason = realSeasons.firstOrNull { it.seasonNumber == selectedSeason } ?: realSeasons.first()
                                                                    selectedSeason = validSeason.seasonNumber
                                                                    val maxEp = validSeason.episodes.maxOfOrNull { it.episodeNumber } ?: validSeason.episodes.size
                                                                    if (selectedEpisode > maxEp) selectedEpisode = maxEp
                                                                }
                                                            } catch (_: Exception) {}
                                                        }
                                                    }
                                                }
                                            } else {
                                                val sKey = srcInfo.name.lowercase()
                                                selectedAudioId = "src_${sKey}_default"
                                            }
                                            streamStatus = "Источник: ${srcInfo.name} ($typeBadge)"
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSrcSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor,
                                            contentColor = if (isSrcSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(Border.None, Border.None),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = chipLabel, fontSize = 10.sp, fontWeight = if (isSrcSelected) FontWeight.Bold else FontWeight.Normal)
                                            // Type badge (P2P = cyan, HLS = green, IFRAME = yellow)
                                            Box(
                                                modifier = Modifier
                                                    .background(typeColor.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            ) {
                                                Text(text = typeBadge, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = typeColor)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // === 2. QUALITY SELECTOR ===
                        if (availableQualities.isNotEmpty()) {
                            Text(
                                text = "Качество видео:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(availableQualities) { qIdx, qual ->
                                    val isQSelected = selectedQuality.equals(qual, ignoreCase = true)
                                    val qMod = if (qIdx == 0) {
                                        Modifier.focusRequester(firstQualityFocusRequester).focusProperties {
                                            up = firstSourceFocusRequester
                                            down = if (filteredAudioTracks.isNotEmpty()) firstAudioFocusRequester
                                                   else if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) firstSeasonFocusRequester
                                                   else if (currentMovie.isSeries) episodesFocusRequester
                                                   else FocusRequester.Default
                                        }
                                    } else Modifier
                                    Button(
                                        onClick = {
                                            selectedQuality = qual
                                            prefs.edit().putString("pref_quality", qual).apply()
                                            streamStatus = "Выбрано качество: $qual"
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isQSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor,
                                            contentColor = if (isQSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(Border.None, Border.None),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp).then(qMod)
                                    ) {
                                        Text(text = qual, fontSize = 10.sp, fontWeight = if (isQSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (isSourceStreamsLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeonSpinner(size = 14.dp, strokeWidth = 2.dp)
                                Text(text = "Загрузка качеств для $selectedSourceFilter...", fontSize = 11.sp, color = TextGray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (!selectedSourceFilter.equals("Все", ignoreCase = true)) {
                            Text(text = "Для «$selectedSourceFilter» нет отдельных потоков", fontSize = 11.sp, color = TextGray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // === 3. AUDIO / DUBBING SELECTOR ===
                        if (filteredAudioTracks.isNotEmpty()) {
                            Text(
                                text = "Озвучка / Перевод:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(filteredAudioTracks) { trkIdx, track ->
                                    val isSelected = track.id == selectedAudioId
                                    val audioMod = if (trkIdx == 0) {
                                        Modifier.focusRequester(firstAudioFocusRequester).focusProperties {
                                            up = if (availableQualities.isNotEmpty()) firstQualityFocusRequester
                                                 else firstSourceFocusRequester
                                            down = if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) firstSeasonFocusRequester
                                                   else if (currentMovie.isSeries) episodesFocusRequester
                                                   else FocusRequester.Default
                                        }
                                    } else Modifier
                                    Button(
                                        onClick = {
                                            selectedAudioId = track.id
                                            if (currentMovie.isSeries) {
                                                val cached = translatorSeasonsCache[track.id]
                                                if (cached != null && cached.isNotEmpty()) {
                                                    currentMovie = currentMovie.copy(seasons = cached)
                                                    val validSeason = cached.firstOrNull { it.seasonNumber == selectedSeason } ?: cached.first()
                                                    selectedSeason = validSeason.seasonNumber
                                                    val maxEp = validSeason.episodes.maxOfOrNull { it.episodeNumber } ?: validSeason.episodes.size
                                                    if (selectedEpisode > maxEp) selectedEpisode = maxEp
                                                    streamStatus = "Озвучка: «${track.name}» (${validSeason.episodes.size} сер.)"
                                                } else {
                                                    coroutineScope.launch {
                                                        try {
                                                            val realSeasons = ShowHubApiClient.fetchEpisodes(currentMovie, track.id, track.source)
                                                            if (realSeasons.isNotEmpty()) {
                                                                translatorSeasonsCache[track.id] = realSeasons
                                                                val epCount = realSeasons.sumOf { it.episodes.size }
                                                                val sMap = realSeasons.associate { it.seasonNumber to it.episodes.size }
                                                                val updatedTracks = currentMovie.audioTracks.map {
                                                                    if (it.id == track.id) it.copy(episodesCount = epCount, seasonsEpisodes = sMap) else it
                                                                }
                                                                currentMovie = currentMovie.copy(seasons = realSeasons, audioTracks = updatedTracks)
                                                                val validSeason = realSeasons.firstOrNull { it.seasonNumber == selectedSeason } ?: realSeasons.first()
                                                                selectedSeason = validSeason.seasonNumber
                                                                val maxEp = validSeason.episodes.maxOfOrNull { it.episodeNumber } ?: validSeason.episodes.size
                                                                if (selectedEpisode > maxEp) selectedEpisode = maxEp
                                                                streamStatus = "Озвучка: «${track.name}» (${validSeason.episodes.size} сер.)"
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor,
                                            contentColor = if (isSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp).then(audioMod)
                                    ) {
                                        val cachedSeasonEps = translatorSeasonsCache[track.id]?.firstOrNull { it.seasonNumber == selectedSeason }?.episodes?.size
                                        val seasonEpCount = cachedSeasonEps
                                            ?: track.seasonsEpisodes[selectedSeason]
                                            ?: (if (track.seasonsEpisodes.isNotEmpty()) 0 else if (currentMovie.seasons.size <= 1) track.episodesCount else 0)
                                        val countSuffix = if (seasonEpCount > 0) " ($seasonEpCount сер.)" else ""
                                        val cleanTrackName = if (!selectedSourceFilter.equals("HDrezka", ignoreCase = true)) {
                                            track.name.replace(Regex("(?i)\\s*\\(hdrezka\\)"), "").trim()
                                        } else {
                                            track.name
                                        }
                                        Text(text = "$cleanTrackName$countSuffix", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // SERIES: Seasons & Episodes
                        if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Сезоны:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Spacer(modifier = Modifier.height(5.dp))

                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(currentMovie.seasons) { sIdx, season ->
                                    val isSelected = season.seasonNumber == selectedSeason
                                    val seasonMod = if (sIdx == 0) {
                                        Modifier.focusRequester(firstSeasonFocusRequester).focusProperties {
                                            up = if (filteredAudioTracks.isNotEmpty()) firstAudioFocusRequester
                                                   else if (availableQualities.isNotEmpty()) firstQualityFocusRequester
                                                   else if (availableSourcesInfo.isNotEmpty()) firstSourceFocusRequester
                                                   else tabsFocusRequester
                                            down = episodesFocusRequester
                                        }
                                    } else Modifier
                                    Button(
                                        onClick = {
                                            selectedSeason = season.seasonNumber
                                            selectedEpisode = 1
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor,
                                            contentColor = if (isSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp).then(seasonMod)
                                    ) {
                                        Text(text = season.title, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val activeSeason = currentMovie.seasons.firstOrNull { it.seasonNumber == selectedSeason } ?: currentMovie.seasons.first()
                            val activeEpisodes = remember(activeSeason, selectedAudioId, currentMovie.audioTracks, translatorSeasonsCache[selectedAudioId]) {
                                val cachedEps = translatorSeasonsCache[selectedAudioId]?.firstOrNull { it.seasonNumber == selectedSeason }?.episodes
                                if (cachedEps != null && cachedEps.isNotEmpty()) {
                                    cachedEps
                                } else {
                                    val curTrack = currentMovie.audioTracks.firstOrNull { it.id == selectedAudioId }
                                    val maxEpForTrack = curTrack?.seasonsEpisodes?.get(selectedSeason)
                                    if (maxEpForTrack != null && maxEpForTrack > 0) {
                                        if (maxEpForTrack > activeSeason.episodes.size) {
                                            (1..maxEpForTrack).map { epNum ->
                                                activeSeason.episodes.firstOrNull { it.episodeNumber == epNum }
                                                    ?: EpisodeInfo(
                                                        episodeNumber = epNum,
                                                        title = "Серия $epNum"
                                                    )
                                            }
                                        } else {
                                            activeSeason.episodes.filter { it.episodeNumber <= maxEpForTrack }
                                        }
                                    } else if (curTrack?.seasonsEpisodes?.isNotEmpty() == true) {
                                        emptyList()
                                    } else {
                                        activeSeason.episodes
                                    }
                                }
                            }
                            LaunchedEffect(activeEpisodes.size) {
                                if (selectedEpisode > activeEpisodes.size && activeEpisodes.isNotEmpty()) {
                                    selectedEpisode = activeEpisodes.size
                                }
                            }
                            Text(
                                text = "Серии (${activeEpisodes.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(5.dp))

                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(activeEpisodes) { epIdx, ep ->
                                    val isSelected = ep.episodeNumber == selectedEpisode
                                    val histProgress = if (savedHistory != null && savedHistory.season == selectedSeason && savedHistory.episode == ep.episodeNumber) {
                                        if (savedHistory.percentage > 0) {
                                            savedHistory.percentage
                                        } else if (savedHistory.positionMs > 10_000L) {
                                            val calcPct = if (savedHistory.durationMs > 0L) {
                                                ((savedHistory.positionMs * 100) / savedHistory.durationMs).toInt()
                                            } else {
                                                ((savedHistory.positionMs * 100) / (60 * 60 * 1000L)).toInt()
                                            }
                                            calcPct.coerceIn(1, 100)
                                        } else 0
                                    } else 0
                                    val epProgress = maxOf(
                                        historyManager.getEpisodeProgressRaw(currentMovie.id, selectedSeason, ep.episodeNumber, currentMovie.title),
                                        histProgress
                                    )
                                    val isEpWatched = if (epProgress in 1..84) false else (epProgress >= 85 || historyManager.isEpisodeWatched(currentMovie.id, selectedSeason, ep.episodeNumber, currentMovie.title))
                                    val progressPct = if (isEpWatched) 1.0f
                                                      else if (epProgress > 0) (epProgress / 100f).coerceIn(0.08f, 0.95f)
                                                      else 0f

                                    var isButtonFocused by remember { mutableStateOf(false) }
                                    val epFocusMod = if (epIdx == 0) {
                                        Modifier.focusRequester(episodesFocusRequester).focusProperties {
                                            up = if (currentMovie.seasons.isNotEmpty()) firstSeasonFocusRequester
                                                 else if (availableQualities.isNotEmpty()) firstQualityFocusRequester
                                                 else if (filteredAudioTracks.isNotEmpty()) firstAudioFocusRequester
                                                 else if (availableSources.size > 1) firstSourceFocusRequester
                                                 else tabsFocusRequester
                                        }
                                    } else Modifier
                                    Button(
                                        onClick = {
                                            selectedEpisode = ep.episodeNumber
                                            if (newEpisodesCount > 0) {
                                                historyManager.clearNewEpisodes(currentMovie.id)
                                                newEpisodesCount = 0
                                            }
                                            startPlayback(targetSeason = selectedSeason, targetEpisode = ep.episodeNumber, startPos = 0L)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor,
                                            contentColor = if (isSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .defaultMinSize(minWidth = 64.dp)
                                            .then(epFocusMod)
                                            .onFocusChanged { isButtonFocused = it.isFocused }
                                            .focusedGlow(isFocused = isButtonFocused, color = focusColor, radius = 6.dp, shapeRadius = 6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .drawWithContent {
                                                drawContent()
                                                val barHeight = 4.dp.toPx()
                                                val y = size.height - barHeight
                                                val w = size.width

                                                // Background slot/track across the entire bottom edge
                                                drawRect(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    topLeft = Offset(0f, y),
                                                    size = Size(w, barHeight)
                                                )
                                                drawRect(
                                                    color = Color.White.copy(alpha = 0.20f),
                                                    topLeft = Offset(0f, y),
                                                    size = Size(w, barHeight)
                                                )

                                                // Active progress fill - always high contrast
                                                if (progressPct > 0f) {
                                                    val fillCol = if (isEpWatched) Color(0xFF22C55E) else Color(0xFFFF9800)
                                                    drawRect(
                                                        color = fillCol,
                                                        topLeft = Offset(0f, y),
                                                        size = Size(w * progressPct, barHeight)
                                                    )
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)
                                        ) {
                                            if (isEpWatched) {
                                                Text(
                                                    text = "✓ ",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) Color.Black else Color(0xFF22C55E)
                                                )
                                            }
                                            Text(
                                                text = ep.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tab 0: Cast Strip
                        if (displayCast.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "В главных ролях (нажмите для поиска фильмов):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(displayCast) { actor ->
                                    var isActorFocused by remember { mutableStateOf(false) }
                                    Card(
                                        onClick = { onSearchClick(actor.name) },
                                        colors = CardDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = focusColor.copy(alpha = 0.22f)
                                        ),
                                        border = CardDefaults.border(
                                            border = Border(BorderStroke(2.dp, Color.Transparent)),
                                            focusedBorder = Border(BorderStroke(2.dp, focusColor))
                                        ),
                                        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        modifier = Modifier
                                            .width(84.dp)
                                            .onFocusChanged { isActorFocused = it.isFocused }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(96.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White.copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (actor.photoUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = actor.photoUrl,
                                                        contentDescription = actor.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    AppIcon(
                                                        resId = R.drawable.ic_person,
                                                        tint = TextGray,
                                                        size = 32.dp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = actor.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isActorFocused) focusColor else TextWhite,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(340.dp))
                    }

                    activeTabTitle.startsWith("График") -> {
                        // TAB: ГРАФИК ВЫХОДА СЕРИЙ
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "График выхода серий:",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            val scheduleItems = remember(currentMovie.episodesSchedule, currentMovie.seasons) {
                                if (currentMovie.episodesSchedule.isNotEmpty()) {
                                    currentMovie.episodesSchedule
                                } else {
                                    val list = mutableListOf<com.example.tvmediaapp.data.models.EpisodeScheduleItem>()
                                    val defaultDate = if (currentMovie.releaseYear.isNotBlank()) "${currentMovie.releaseYear} г." else "Дата уточняется"
                                    currentMovie.seasons.forEach { s ->
                                        s.episodes.forEach { ep ->
                                            list.add(
                                                com.example.tvmediaapp.data.models.EpisodeScheduleItem(
                                                    episode = "${s.seasonNumber} сезон ${ep.episodeNumber} серия",
                                                    title = ep.title,
                                                    date = defaultDate,
                                                    status = "Вышла"
                                                )
                                            )
                                        }
                                    }
                                    list
                                }
                            }

                            if (scheduleItems.isEmpty()) {
                                Text(
                                    text = "График выхода серий формируется...",
                                    fontSize = 13.sp,
                                    color = TextGray
                                )
                            } else {
                                scheduleItems.forEachIndexed { itemIdx, item ->
                                    val itemLower = (item.status + " " + item.date).lowercase()
                                    val isAired = itemLower.contains("вышла") || itemLower.contains("доступна") || itemLower.contains("вчера") || itemLower.contains("сегодня")
                                    val statusBg = if (isAired) Color(0xFF1B5E20).copy(alpha = 0.85f) else Color(0xFF0D47A1).copy(alpha = 0.85f)
                                    val statusFg = if (isAired) Color(0xFF81C784) else Color(0xFF90CAF9)

                                    val displayDate = remember(item.date, currentMovie.releaseYear) {
                                        val d = item.date.trim()
                                        when {
                                            d.isEmpty() || d.equals("вышла", ignoreCase = true) || d.equals("доступна", ignoreCase = true) -> {
                                                if (currentMovie.releaseYear.isNotBlank()) "${currentMovie.releaseYear} г." else "Дата уточняется"
                                            }
                                            Regex("""^\d{4}-\d{2}-\d{2}$""").matches(d) -> {
                                                val parts = d.split("-")
                                                val y = parts[0]
                                                val m = parts[1].toIntOrNull() ?: 1
                                                val day = parts[2].toIntOrNull() ?: 1
                                                val mRu = when (m) {
                                                    1 -> "янв"; 2 -> "фев"; 3 -> "мар"; 4 -> "апр"; 5 -> "мая"; 6 -> "июн"
                                                    7 -> "июл"; 8 -> "авг"; 9 -> "сен"; 10 -> "окт"; 11 -> "ноя"; 12 -> "дек"
                                                    else -> ""
                                                }
                                                if (mRu.isNotEmpty()) "$day $mRu $y г." else "$day.$m.$y"
                                            }
                                            else -> d
                                        }
                                    }

                                    var isRowFocused by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isRowFocused) accent.copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.03f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isRowFocused) accent else Color.White.copy(alpha = 0.06f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .focusable()
                                            .onFocusChanged { isRowFocused = it.isFocused }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.episode,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRowFocused) Color.White else TextWhite
                                            )
                                            if (item.title.isNotBlank() && item.title != item.episode) {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 11.sp,
                                                    color = if (isRowFocused) TextWhite.copy(alpha = 0.85f) else TextGray
                                                )
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (displayDate.isNotBlank()) {
                                                Text(
                                                    text = displayDate,
                                                    fontSize = 12.sp,
                                                    color = if (isRowFocused) TextWhite.copy(alpha = 0.9f) else TextGray
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(statusBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.status.ifBlank { if (isAired) "Вышла" else "Ожидается" },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = statusFg
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(340.dp))
                    }

                    activeTabTitle.startsWith("Описание") -> {
                        // TAB: ОПИСАНИЕ И ДЕТАЛИ (Прямой переход фокуса на режиссёра и актёров)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Synopsis Block
                            var isSynopsisFocused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSynopsisFocused) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = if (isSynopsisFocused) 1.5.dp else 1.dp,
                                        color = if (isSynopsisFocused) focusColor else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(16.dp)
                                    .focusable()
                                    .onFocusChanged { isSynopsisFocused = it.isFocused },
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Сюжет фильма / сериала:",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = currentMovie.description.ifEmpty { "Описание пока не добавлено" },
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = TextWhite
                                )
                            }

                            // Director Strip
                            if (displayDirectors.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Режиссёр (нажмите для поиска фильмов):",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                    TvLazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        itemsIndexed(displayDirectors) { dirIdx, director ->
                                            var isDirFocused by remember { mutableStateOf(false) }
                                            Card(
                                                onClick = { onSearchClick(director.name) },
                                                colors = CardDefaults.colors(
                                                    containerColor = Color.White.copy(alpha = 0.08f),
                                                    focusedContainerColor = focusColor.copy(alpha = 0.22f)
                                                ),
                                                border = CardDefaults.border(
                                                    border = Border(BorderStroke(2.dp, Color.Transparent)),
                                                    focusedBorder = Border(BorderStroke(2.dp, focusColor))
                                                ),
                                                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                                                scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                                modifier = Modifier
                                                    .width(84.dp)
                                                    .onFocusChanged { isDirFocused = it.isFocused }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(96.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.White.copy(alpha = 0.08f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (director.photoUrl.isNotBlank()) {
                                                            AsyncImage(
                                                                model = director.photoUrl,
                                                                contentDescription = director.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            AppIcon(
                                                                resId = R.drawable.ic_director,
                                                                tint = TextGray,
                                                                size = 32.dp
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = director.name,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isDirFocused) focusColor else TextWhite,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center,
                                                        lineHeight = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Cast Strip
                            if (displayCast.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "В главных ролях (нажмите для поиска фильмов):",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                    TvLazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        itemsIndexed(displayCast) { actorIdx, actor ->
                                            var isActorFocused by remember { mutableStateOf(false) }
                                            Card(
                                                onClick = { onSearchClick(actor.name) },
                                                colors = CardDefaults.colors(
                                                    containerColor = Color.White.copy(alpha = 0.08f),
                                                    focusedContainerColor = focusColor.copy(alpha = 0.22f)
                                                ),
                                                border = CardDefaults.border(
                                                    border = Border(BorderStroke(2.dp, Color.Transparent)),
                                                    focusedBorder = Border(BorderStroke(2.dp, focusColor))
                                                ),
                                                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                                                scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                                modifier = Modifier
                                                    .width(84.dp)
                                                    .onFocusChanged { isActorFocused = it.isFocused }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(96.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.White.copy(alpha = 0.08f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (actor.photoUrl.isNotBlank()) {
                                                            AsyncImage(
                                                                model = actor.photoUrl,
                                                                contentDescription = actor.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            AppIcon(
                                                                resId = R.drawable.ic_person,
                                                                tint = TextGray,
                                                                size = 32.dp
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = actor.name,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isActorFocused) focusColor else TextWhite,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center,
                                                        lineHeight = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Metadata block
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (displayDirectors.isEmpty() && currentMovie.director.isNotEmpty()) {
                                    Text(text = "Режиссёр: ${currentMovie.director}", fontSize = 13.sp, color = TextWhite)
                                }
                                if (currentMovie.country.isNotEmpty()) {
                                    Text(text = "Страна производства: ${currentMovie.country}", fontSize = 13.sp, color = TextWhite)
                                }
                                if (currentMovie.releaseYear.isNotEmpty()) {
                                    Text(text = "Год премьеры: ${currentMovie.releaseYear}", fontSize = 13.sp, color = TextWhite)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(340.dp))
                    }

                    else -> {
                        // TAB: ОТЗЫВЫ ЗРИТЕЛЕЙ (С фокусом на каждом отзыве и скроллом)
                        if (isLoadingComments) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NeonSpinner(size = 40.dp, message = "Загрузка отзывов зрителей...")
                            }
                        } else if (comments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(28.dp)
                                    .focusable(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Отзывов зрителей пока нет",
                                    color = TextGray,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                comments.forEachIndexed { cIdx, c ->
                                    var isCommentFocused by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = if (isCommentFocused) 0.12f else 0.06f))
                                            .border(
                                                width = if (isCommentFocused) 2.dp else 1.dp,
                                                color = if (isCommentFocused) focusColor else Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(14.dp)
                                            .focusable()
                                            .onFocusChanged { isCommentFocused = it.isFocused }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = c.author,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = accent
                                            )
                                            Text(
                                                text = c.date,
                                                fontSize = 12.sp,
                                                color = TextGray
                                            )
                                        }
                                        if (!c.rating.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Рейтинг: ${c.rating}",
                                                fontSize = 11.sp,
                                                color = ImdbGold,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = c.text,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = TextWhite.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Smooth bottom clearance for TV bezels and overscan
                Spacer(modifier = Modifier.height(360.dp))
            }
        }

        if (showTorrServerDialog && pendingTorrStream != null) {
            val torrHost = TorrServerManager.getTorrHost(context)
            TorrServerHelperDialog(
                host = torrHost,
                streamTitle = pendingTorrStream?.quality ?: "4K / 2K P2P",
                magnetOrStreamUrl = pendingTorrStream?.url ?: "",
                onDismiss = {
                    showTorrServerDialog = false
                    pendingTorrStream = null
                },
                onRetryPlayback = {
                    val streamToPlay = pendingTorrStream
                    showTorrServerDialog = false
                    pendingTorrStream = null
                    if (streamToPlay != null) {
                        val movieToPlay = (if (currentMovie.isSeries) currentMovie.copy(isSeries = true) else currentMovie)
                            .copy(source = streamToPlay.source, videoUrl = streamToPlay.url, streams = streamOptions)
                        onPlayClick(movieToPlay, streamToPlay.url, 0L, selectedSeason, selectedEpisode, selectedAudioId)
                    }
                }
            )
        }
    }
}

/**
 * Icon-only action button with floating tooltip on focus.
 * Shows a small label above the button when focused via D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconActionButton(
    iconResId: Int,
    tooltip: String,
    onClick: () -> Unit,
    focusColor: Color,
    modifier: Modifier = Modifier,
    iconTintOverride: Color? = null,
    focusedIconTintOverride: Color? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.TopCenter) {
        // Floating tooltip above button
        androidx.compose.animation.AnimatedVisibility(
            visible = isFocused,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(100))
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (-22).dp)
                    .background(
                        color = Color(0xE6202020),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(1.dp, focusColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = tooltip,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = focusColor,
                contentColor = TextWhite,
                focusedContentColor = Color.Black
            ),
            border = ButtonDefaults.border(
                border = Border.None,
                focusedBorder = Border.None
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.05f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = modifier
                .height(28.dp)
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            AppIcon(
                resId = iconResId,
                tint = if (isFocused) (focusedIconTintOverride ?: Color.Black)
                       else (iconTintOverride ?: androidx.tv.material3.LocalContentColor.current),
                size = 16.dp
            )
        }
    }
}
