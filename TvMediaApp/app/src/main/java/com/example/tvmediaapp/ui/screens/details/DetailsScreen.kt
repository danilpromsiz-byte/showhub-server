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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
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
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.components.NeonSpinner
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: Movie,
    onPlayClick: (detailedMovie: Movie, videoUrl: String, startPositionMs: Long, season: Int, episode: Int, audioId: String) -> Unit,
    onBackClick: () -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    isFavorite: Boolean,
    onSearchClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current

    val historyManager = remember { WatchHistoryManager(context) }
    val savedHistory = remember(movie.id) {
        historyManager.getProgress(movie.id)
    }

    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }
    val rightPaneScrollState = rememberScrollState()
    var isFav by remember(movie.id, isFavorite) { mutableStateOf(isFavorite) }
    var currentMovie by remember {
        mutableStateOf(
            com.example.tvmediaapp.data.cache.MediaDiskCache.getCachedDetails(movie.id, movie.title, movie.releaseYear) ?: movie
        )
    }
    var selectedSeason by remember { mutableStateOf(savedHistory?.season ?: 1) }
    var selectedEpisode by remember { mutableStateOf(savedHistory?.episode ?: 1) }
    var selectedAudioId by remember { mutableStateOf(savedHistory?.audioId ?: "") }
    var selectedQuality by remember { mutableStateOf(prefs.getString("pref_quality", "1080p") ?: "1080p") }
    var isResolving by remember { mutableStateOf(false) }
    var streamStatus by remember { mutableStateOf<String?>(null) }
    var streamOptions by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var selectedDetailTab by remember { mutableIntStateOf(0) }
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var isLoadingComments by remember { mutableStateOf(false) }

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

    fun matchStreamQuality(stream: StreamOption, target: String): Boolean {
        if (!isDirectVideoStream(stream.url)) return false
        val sq = stream.quality.lowercase().trim()
        val tq = target.lowercase().trim()
        if (tq.contains("ultra")) return sq.contains("ultra")
        if (tq.contains("4k") || tq.contains("2160")) return sq.contains("4k") || sq.contains("2160")
        if (tq == "1080p" || tq == "1080") return sq.contains("1080") && !sq.contains("ultra")
        return sq.contains(tq)
    }

    val availableQualities = remember(streamOptions) {
        if (streamOptions.isNotEmpty()) {
            val qualSet = linkedSetOf<String>()
            val order = listOf("4K Ultra", "1080p", "720p", "480p", "360p")
            for (target in order) {
                if (streamOptions.any { matchStreamQuality(it, target) }) {
                    qualSet.add(target)
                }
            }
            if (qualSet.isNotEmpty()) qualSet.toList() else listOf("1080p", "720p", "480p")
        } else {
            listOf("1080p", "720p", "480p")
        }
    }

    LaunchedEffect(availableQualities) {
        if (availableQualities.isNotEmpty() && !availableQualities.any { it.equals(selectedQuality, ignoreCase = true) }) {
            selectedQuality = availableQualities.first()
        }
    }

    fun pickSafePreviewStream(streams: List<StreamOption>): String? {
        val nonPremium = streams.filter {
            val q = it.quality.lowercase()
            !q.contains("ultra") && !q.contains("4k") && !q.contains("2160") && !q.contains("vip") && isDirectVideoStream(it.url)
        }
        return nonPremium.firstOrNull { it.quality.contains("720") }?.url
            ?: nonPremium.firstOrNull { it.quality.contains("1080") }?.url
            ?: nonPremium.firstOrNull { it.quality.contains("480") }?.url
            ?: nonPremium.firstOrNull()?.url
    }

    // Focus Requesters for instant TV remote control & bidirectional navigation
    val playButtonFocusRequester = remember { FocusRequester() }
    val fromStartButtonFocusRequester = remember { FocusRequester() }
    val trailerButtonFocusRequester = remember { FocusRequester() }
    val externalPlayerFocusRequester = remember { FocusRequester() }
    val favoriteButtonFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val leftPaneFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val scheduleTabFocusRequester = remember { FocusRequester() }
    val firstScheduleItemFocusRequester = remember { FocusRequester() }
    val descriptionTabFocusRequester = remember { FocusRequester() }
    val synopsisBlockFocusRequester = remember { FocusRequester() }
    val firstDirectorFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val translatorSeasonsCache = remember { mutableStateMapOf<String, List<SeasonInfo>>() }

    // Automatically focus the primary play button as soon as movie card opens
    LaunchedEffect(Unit) {
        delay(150)
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
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
        val detailed = ShowHubApiClient.fetchMediaDetails(movie)
        currentMovie = detailed
        com.example.tvmediaapp.data.cache.MediaDiskCache.putCachedDetails(detailed)
        if (detailed.seasons.isNotEmpty() && savedHistory == null) {
            selectedSeason = detailed.seasons.first().seasonNumber
        }
        if (detailed.audioTracks.isNotEmpty() && selectedAudioId.isEmpty()) {
            selectedAudioId = detailed.audioTracks.first().id
        }
        if (detailed.isSeries && detailed.seasons.isNotEmpty()) {
            val total = detailed.seasons.sumOf { it.episodes.size }
            val n = historyManager.updateKnownTotalEpisodes(detailed.id, total)
            newEpisodesCount = n
        }
    }

    // Pre-fetch streams in background: query native Rezka and server in parallel
    LaunchedEffect(currentMovie.id, selectedSeason, selectedEpisode, selectedAudioId) {
        try {
            val isContentSeries = currentMovie.isSeries || currentMovie.seasons.isNotEmpty() || selectedSeason > 1 || selectedEpisode > 1
            val nativeDeferred = async {
                RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = isContentSeries,
                    season = selectedSeason,
                    episode = selectedEpisode,
                    translatorId = selectedAudioId.ifEmpty { null },
                    mediaUrl = currentMovie.id
                )
            }
            val serverDeferred = async {
                ShowHubApiClient.fetchStreams(
                    movie = currentMovie.copy(isSeries = isContentSeries),
                    season = if (isContentSeries) selectedSeason else null,
                    episode = if (isContentSeries) selectedEpisode else null,
                    audioId = selectedAudioId
                )
            }
            val nativeStreams = nativeDeferred.await()
            val serverStreams = serverDeferred.await()
            val combined = (nativeStreams + serverStreams).distinctBy { it.url }
            val sorted = combined.sortedByDescending { isDirectVideoStream(it.url) }
            if (sorted.isNotEmpty()) {
                streamOptions = sorted
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Video preview in details screen (starts after 2.2s delay, ensuring text metadata is fully rendered first)
    LaunchedEffect(currentMovie.id, streamOptions.isNotEmpty()) {
        delay(2200)
        if (detailsPreviewPlayer == null) {
            var streamUrl: String? = pickSafePreviewStream(streamOptions)
            if (streamUrl.isNullOrEmpty()) {
                try {
                    val nativeStreams = RezkaNativeResolver.resolveStreams(
                        title = currentMovie.title,
                        year = currentMovie.releaseYear,
                        isSeries = currentMovie.isSeries,
                        season = if (currentMovie.isSeries) selectedSeason else 1,
                        episode = if (currentMovie.isSeries) selectedEpisode else 1,
                        mediaUrl = currentMovie.id
                    )
                    streamUrl = pickSafePreviewStream(nativeStreams)
                } catch (_: Exception) {}
            }
            if (streamUrl.isNullOrEmpty()) {
                val candidate = ShowHubApiClient.fetchPreviewStream(currentMovie)
                if (candidate != null && isDirectVideoStream(candidate)) {
                    streamUrl = candidate
                }
            }

            if (!streamUrl.isNullOrEmpty() && isDirectVideoStream(streamUrl)) {
                try {
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(mapOf("Referer" to "https://hdrezka.ag/"))
                        .setConnectTimeoutMs(8000)
                        .setReadTimeoutMs(15000)
                        .setAllowCrossProtocolRedirects(true)
                    val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            /* minBufferMs = */ 3000,
                            /* maxBufferMs = */ 6000,
                            /* bufferForPlaybackMs = */ 1000,
                            /* bufferForPlaybackAfterRebufferMs = */ 1500
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
                            val targetSeekMs = if (currentMovie.isSeries) 12 * 60 * 1000L else 22 * 60 * 1000L
                            setMediaItem(MediaItem.fromUri(streamUrl))
                            volume = 0f
                            repeatMode = Player.REPEAT_MODE_ALL
                            addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state == Player.STATE_READY) {
                                        if (!hasSeeked) {
                                            hasSeeked = true
                                            if (duration > 0 && duration > targetSeekMs + 20_000L) {
                                                seekTo(targetSeekMs)
                                            } else if (duration > 0) {
                                                seekTo((duration * 0.25).toLong())
                                            }
                                        }
                                        isDetailsPreviewPlaying = true
                                    } else if (state == Player.STATE_ENDED) {
                                        seekTo(0L)
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
            detailsPreviewPlayer?.stop()
            detailsPreviewPlayer?.clearMediaItems()
            detailsPreviewPlayer?.release()
            detailsPreviewPlayer = null
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
        detailsPreviewPlayer?.stop()
        detailsPreviewPlayer?.clearMediaItems()
        detailsPreviewPlayer?.release()
        detailsPreviewPlayer = null
        isDetailsPreviewPlaying = false

        coroutineScope.launch {
            val isContentSeries = currentMovie.isSeries || currentMovie.seasons.isNotEmpty() || targetSeason > 1 || targetEpisode > 1
            // Priority 1: Query Rezka directly on TV (residential IP) and server concurrently
            val nativeDeferred = async {
                RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = isContentSeries,
                    season = targetSeason,
                    episode = targetEpisode,
                    translatorId = targetAudioId.ifEmpty { null },
                    mediaUrl = currentMovie.id
                )
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
            val combined = if (isNativeFallback && serverStreams.isNotEmpty()) {
                (serverStreams + nativeStreams).distinctBy { it.url }
            } else {
                (nativeStreams + serverStreams).distinctBy { it.url }
            }
            // Sort direct streams (HLS/MP4) first, balancers last
            val streams = combined.sortedByDescending { isDirectVideoStream(it.url) }

            streamOptions = streams
            isResolving = false

            if (streams.isNotEmpty()) {
                val matched = streams.firstOrNull { matchStreamQuality(it, selectedQuality) }
                    ?: streams.firstOrNull { isDirectVideoStream(it.url) && !it.quality.contains("ultra", ignoreCase = true) && !it.quality.contains("4k", ignoreCase = true) }
                    ?: streams.firstOrNull { isDirectVideoStream(it.url) }
                    ?: streams.first()

                if (matched.url.isNotBlank() && matched.url.startsWith("http")) {
                    streamStatus = "Найден поток ${matched.quality}! Запуск..."
                    onPlayClick(currentMovie, matched.url, startPos, targetSeason, targetEpisode, targetAudioId)
                } else {
                    streamStatus = "Поток недоступен для выбранной серии. Попробуйте другую озвучку."
                }
            } else {
                streamStatus = "Поток недоступен для выбранной серии. Попробуйте другую озвучку."
            }
        }
    }

    val screenBg = LocalBackgroundColor.current
    Box(modifier = modifier.fillMaxSize().background(screenBg)) {
        // High-res backdrop
        AsyncImage(
            model = currentMovie.backdropUrl.ifEmpty { currentMovie.posterUrl },
            contentDescription = currentMovie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.30f)
        )

        // Dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            screenBg.copy(alpha = 0.96f),
                            screenBg.copy(alpha = 0.88f),
                            screenBg.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // Two-pane Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 36.dp, end = 48.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
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
                    .width(260.dp)
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
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(previewAlpha)
                                    .clip(RoundedCornerShape(8.dp))
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
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentMovie.ageRating.isNotBlank()) {
                            val cleanAge = currentMovie.ageRating.trim()
                            val (ageBg, ageFg) = when {
                                cleanAge.contains("18") -> Color(0xFFD32F2F) to Color.White
                                cleanAge.contains("16") -> Color(0xFFF57C00) to Color.White
                                cleanAge.contains("12") -> Color(0xFF1976D2) to Color.White
                                cleanAge.contains("6") || cleanAge.contains("0") -> Color(0xFF388E3C) to Color.White
                                else -> Color.Black.copy(alpha = 0.85f) to Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ageBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cleanAge,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ageFg
                                )
                            }
                        }

                        if (currentMovie.ratingKp > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KpOrange)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "КП ${currentMovie.ratingKp}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        if (currentMovie.ratingImdb > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ImdbGold)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "IMDb ${currentMovie.ratingImdb}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
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
                                text = if (currentMovie.isSeries) "Сериал" else "Фильм",
                                fontSize = 12.sp,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
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
                val titleWithYear = buildString {
                    append(currentMovie.title)
                    val cleanYear = currentMovie.releaseYear.replace("null", "").trim()
                    if (cleanYear.isNotEmpty() && !currentMovie.title.contains(cleanYear)) {
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

                val origTitle = currentMovie.originalTitle.trim()
                if (origTitle.isNotBlank() && !origTitle.equals("null", ignoreCase = true) && origTitle != currentMovie.title.trim()) {
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFFE53935), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🔥 Вышли новые серии! (+$newEpisodesCount новых)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }

                // Prominent Synopsis directly under Title & Genres
                if (currentMovie.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentMovie.description,
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
                                containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
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
                            modifier = Modifier.height(24.dp)
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
                        val resumeLabel = if (currentMovie.isSeries) {
                            "Продолжить (S${savedHistory.season} E${savedHistory.episode}, $mins мин)"
                        } else {
                            "Продолжить ($mins мин)"
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
                                containerColor = accent,
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
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
                                containerColor = Color.White.copy(alpha = 0.12f),
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
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
                                containerColor = accent,
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
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
                                    text = if (isResolving) "Поиск потока..." else "Смотреть",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }

                    // TRAILER BUTTON
                    Button(
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
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .focusRequester(trailerButtonFocusRequester)
                            .focusProperties {
                                left = if (hasResume) fromStartButtonFocusRequester else playButtonFocusRequester
                                right = externalPlayerFocusRequester
                                down = favoriteButtonFocusRequester
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AppIcon(
                                resId = R.drawable.ic_movie,
                                tint = androidx.tv.material3.LocalContentColor.current,
                                size = 14.dp
                            )
                            Text(
                                text = "Трейлер",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // EXTERNAL PLAYER BUTTON
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                streamStatus = "Получение ссылки для стороннего плеера..."
                                var streams = streamOptions
                                if (streams.isEmpty()) {
                                    val nativeDeferred = async {
                                        RezkaNativeResolver.resolveStreams(
                                            title = currentMovie.title,
                                            year = currentMovie.releaseYear,
                                            isSeries = currentMovie.isSeries,
                                            season = selectedSeason,
                                            episode = selectedEpisode,
                                            translatorId = selectedAudioId.ifEmpty { null },
                                            mediaUrl = currentMovie.id
                                        )
                                    }
                                    val serverDeferred = async {
                                        ShowHubApiClient.fetchStreams(
                                            movie = currentMovie,
                                            season = if (currentMovie.isSeries) selectedSeason else null,
                                            episode = if (currentMovie.isSeries) selectedEpisode else null,
                                            audioId = selectedAudioId
                                        )
                                    }
                                    streams = (nativeDeferred.await() + serverDeferred.await()).distinctBy { it.url }
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
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .focusRequester(externalPlayerFocusRequester)
                            .focusProperties {
                                left = trailerButtonFocusRequester
                                down = backButtonFocusRequester
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AppIcon(
                                resId = R.drawable.ic_open_in_new,
                                tint = androidx.tv.material3.LocalContentColor.current,
                                size = 13.dp
                            )
                            Text(
                                text = "Внешний плеер",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons - Row 2 (Library & Navigation Actions)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isFav = !isFav
                            onToggleFavorite(currentMovie)
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isFav) FavoriteGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = focusColor,
                            contentColor = if (isFav) Color.Black else TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border.None,
                            focusedBorder = Border.None
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .focusRequester(favoriteButtonFocusRequester)
                            .focusProperties {
                                left = leftPaneFocusRequester
                                up = playButtonFocusRequester
                                right = backButtonFocusRequester
                                down = tabsFocusRequester
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AppIcon(
                                resId = if (isFav) R.drawable.ic_star else R.drawable.ic_star_border,
                                tint = if (isFav) FavoriteGold else androidx.tv.material3.LocalContentColor.current,
                                size = 13.dp
                            )
                            Text(
                                text = if (isFav) "В избранном" else "В избранное",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .focusRequester(backButtonFocusRequester)
                            .focusProperties {
                                left = favoriteButtonFocusRequester
                                up = externalPlayerFocusRequester
                                down = tabsFocusRequester
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AppIcon(
                                resId = R.drawable.ic_arrow_back,
                                tint = androidx.tv.material3.LocalContentColor.current,
                                size = 13.dp
                            )
                            Text(
                                text = "Назад",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // Stream status message with NeonSpinner (only while resolving)
                if (streamStatus != null || isResolving) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isResolving) {
                            NeonSpinner(size = 20.dp, strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = streamStatus ?: "Поиск наилучшего видеопотока...",
                            fontSize = 13.sp,
                            color = if (isResolving) accent else if (streamStatus?.contains("Найден") == true) accent else Color(0xFFF87171)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detail Section Tabs: «Плеер и серии», «График серий», «Описание и детали», «Отзывы (N)»
                val tabs = remember(currentMovie.isSeries, currentMovie.episodesSchedule.size, comments.size) {
                    val list = mutableListOf("Плеер и серии")
                    if (currentMovie.isSeries) {
                        list.add("График серий" + if (currentMovie.episodesSchedule.isNotEmpty()) " (${currentMovie.episodesSchedule.size})" else "")
                    }
                    list.add("Описание и детали")
                    list.add("Отзывы" + if (comments.isNotEmpty()) " (${comments.size})" else "")
                    list
                }
                val activeTabTitle = tabs.getOrNull(selectedDetailTab) ?: tabs.firstOrNull() ?: "Плеер и серии"

                LaunchedEffect(selectedDetailTab) {
                    delay(60)
                    try {
                        when (selectedDetailTab) {
                            0 -> tabsFocusRequester.requestFocus()
                            1 -> if (currentMovie.isSeries) scheduleTabFocusRequester.requestFocus() else descriptionTabFocusRequester.requestFocus()
                            2 -> if (currentMovie.isSeries) descriptionTabFocusRequester.requestFocus() else tabsFocusRequester.requestFocus()
                            else -> tabsFocusRequester.requestFocus()
                        }
                    } catch (_: Exception) {}
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedDetailTab == index
                        val tabMod = when (index) {
                            0 -> Modifier
                                .height(26.dp)
                                .focusRequester(tabsFocusRequester)
                                .focusProperties {
                                    up = favoriteButtonFocusRequester
                                    left = leftPaneFocusRequester
                                    down = episodesFocusRequester
                                }
                            1 -> if (currentMovie.isSeries) {
                                Modifier
                                    .height(26.dp)
                                    .focusRequester(scheduleTabFocusRequester)
                                    .focusProperties {
                                        up = favoriteButtonFocusRequester
                                        down = firstScheduleItemFocusRequester
                                    }
                            } else {
                                Modifier
                                    .height(26.dp)
                                    .focusRequester(descriptionTabFocusRequester)
                                    .focusProperties {
                                        up = favoriteButtonFocusRequester
                                        down = synopsisBlockFocusRequester
                                    }
                            }
                            2 -> if (currentMovie.isSeries) {
                                Modifier
                                    .height(26.dp)
                                    .focusRequester(descriptionTabFocusRequester)
                                    .focusProperties {
                                        up = favoriteButtonFocusRequester
                                        down = synopsisBlockFocusRequester
                                    }
                            } else {
                                Modifier.height(26.dp).focusProperties { up = favoriteButtonFocusRequester }
                            }
                            else -> Modifier.height(26.dp).focusProperties { up = favoriteButtonFocusRequester }
                        }
                        Button(
                            onClick = { selectedDetailTab = index },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = tabMod
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

                var selectedSourceFilter by remember { mutableStateOf("Все") }
                val availableSources = remember(currentMovie.sources, currentMovie.audioTracks, currentMovie.seasons, selectedSeason) {
                    val list = mutableListOf<String>()
                    val curSeasonEps = currentMovie.seasons.firstOrNull { it.seasonNumber == selectedSeason }?.episodes?.size
                        ?: currentMovie.seasons.sumOf { it.episodes.size }
                    list.add(if (curSeasonEps > 0) "Все ($curSeasonEps сер.)" else "Все")
                    if (currentMovie.sources.isNotEmpty()) {
                        currentMovie.sources.forEach { s ->
                            val epC = s.seasonsEpisodes[selectedSeason]
                                ?: currentMovie.audioTracks.filter { it.source.contains(s.name, ignoreCase = true) || (s.name.contains("kodik", ignoreCase = true) && it.id.startsWith("kodik_")) }
                                    .mapNotNull { it.seasonsEpisodes[selectedSeason] ?: it.episodesCount.takeIf { c -> c > 0 } }
                                    .maxOrNull()
                                ?: if (s.episodesCount > 0) s.episodesCount else curSeasonEps
                            val label = if (epC > 0) "${s.name} ($epC сер.)" else s.name
                            list.add(label)
                        }
                    } else {
                        val kdEps = currentMovie.audioTracks.filter { it.source.contains("kodik", ignoreCase = true) || it.id.startsWith("kodik_") }
                            .mapNotNull { it.seasonsEpisodes[selectedSeason] ?: it.episodesCount.takeIf { c -> c > 0 } }
                            .maxOrNull() ?: 0
                        val rzEps = currentMovie.audioTracks.filter { !it.id.startsWith("kodik_") && !it.source.contains("filmix", ignoreCase = true) }
                            .mapNotNull { it.seasonsEpisodes[selectedSeason] ?: it.episodesCount.takeIf { c -> c > 0 } }
                            .maxOrNull() ?: curSeasonEps
                        val fxEps = currentMovie.audioTracks.filter { it.source.contains("filmix", ignoreCase = true) }
                            .mapNotNull { it.seasonsEpisodes[selectedSeason] ?: it.episodesCount.takeIf { c -> c > 0 } }
                            .maxOrNull() ?: 0

                        val srcNames = currentMovie.audioTracks.map { it.source.lowercase() }.distinct()
                        if (srcNames.any { it.contains("kodik") } || currentMovie.audioTracks.any { it.id.startsWith("kodik_") }) {
                            list.add(if (kdEps > 0) "Kodik ($kdEps сер.)" else "Kodik")
                        }
                        if (srcNames.any { it.contains("rezka") } || currentMovie.audioTracks.any { !it.id.startsWith("kodik_") && !it.source.contains("filmix", ignoreCase = true) }) {
                            list.add(if (rzEps > 0) "HDRezka ($rzEps сер.)" else "HDRezka")
                        }
                        if (srcNames.any { it.contains("filmix") }) {
                            list.add(if (fxEps > 0) "Filmix ($fxEps сер.)" else "Filmix")
                        }
                    }
                    list
                }
                val filteredAudioTracks = remember(currentMovie.audioTracks, selectedSourceFilter, selectedSeason) {
                    val sourceFiltered = if (selectedSourceFilter == "Все" || selectedSourceFilter.startsWith("Все")) {
                        currentMovie.audioTracks
                    } else {
                        val sKey = selectedSourceFilter.lowercase()
                        val matched = currentMovie.audioTracks.filter { track ->
                            val trackSrc = track.source.lowercase()
                            when {
                                sKey.contains("kodik") -> trackSrc.contains("kodik") || track.id.startsWith("kodik_")
                                sKey.contains("rezka") -> trackSrc.contains("rezka") || (!track.id.startsWith("kodik_") && !trackSrc.contains("filmix"))
                                sKey.contains("filmix") -> trackSrc.contains("filmix")
                                else -> true
                            }
                        }
                        if (matched.isNotEmpty()) matched else currentMovie.audioTracks
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

                when {
                    activeTabTitle.startsWith("Плеер") -> {
                        // TAB 0: ПЛЕЕР И СЕРИИ
                        // Resource / Source selector
                        if (availableSources.size > 2) {
                            Text(
                                text = "Ресурс / Источник:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(availableSources) { srcLabel ->
                                    val isSrcSelected = selectedSourceFilter == srcLabel
                                    Button(
                                        onClick = {
                                            selectedSourceFilter = srcLabel
                                            val newTracks = if (srcLabel.startsWith("Все")) currentMovie.audioTracks else {
                                                val sKey = srcLabel.lowercase()
                                                currentMovie.audioTracks.filter { track ->
                                                    val trackSrc = track.source.lowercase()
                                                    when {
                                                        sKey.contains("kodik") -> trackSrc.contains("kodik") || track.id.startsWith("kodik_")
                                                        sKey.contains("rezka") -> trackSrc.contains("rezka") || (!track.id.startsWith("kodik_") && !trackSrc.contains("filmix"))
                                                        sKey.contains("filmix") -> trackSrc.contains("filmix")
                                                        else -> true
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
                                                          streamStatus = "Ресурс: $srcLabel | Озвучка: «${targetTrack.name}» (${validSeason.episodes.size} сер.)"
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
                                                                      streamStatus = "Ресурс: $srcLabel | Озвучка: «${targetTrack.name}» (${validSeason.episodes.size} сер.)"
                                                                  }
                                                              } catch (_: Exception) {}
                                                          }
                                                      }
                                                  }
                                              }
                                         },
                                         colors = ButtonDefaults.colors(
                                             containerColor = if (isSrcSelected) accent.copy(alpha = 0.85f) else ChipBackground,
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
                                         Text(text = srcLabel, fontSize = 10.sp, fontWeight = if (isSrcSelected) FontWeight.Bold else FontWeight.Normal)
                                     }
                                 }
                             }
                             Spacer(modifier = Modifier.height(8.dp))
                         }

                         // Translators
                         if (filteredAudioTracks.isNotEmpty()) {
                             Text(
                                 text = "Озвучка / Перевод:",
                                 fontSize = 13.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = TextWhite
                             )
                             Spacer(modifier = Modifier.height(6.dp))
                             TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                 items(filteredAudioTracks) { track ->
                                     val isSelected = track.id == selectedAudioId
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
                                             containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
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
                                         modifier = Modifier.height(26.dp)
                                     ) {
                                         val cachedSeasonEps = translatorSeasonsCache[track.id]?.firstOrNull { it.seasonNumber == selectedSeason }?.episodes?.size
                                         val seasonEpCount = cachedSeasonEps
                                             ?: track.seasonsEpisodes[selectedSeason]
                                             ?: (if (track.seasonsEpisodes.isNotEmpty()) 0 else if (currentMovie.seasons.size <= 1) track.episodesCount else 0)
                                         val countSuffix = if (seasonEpCount > 0) " ($seasonEpCount сер.)" else ""
                                         Text(text = "${track.name}$countSuffix", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                     }
                                 }
                             }
                         }

                        // SERIES: Seasons & Episodes
                        if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Сезоны:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Spacer(modifier = Modifier.height(5.dp))

                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(currentMovie.seasons) { season ->
                                    val isSelected = season.seasonNumber == selectedSeason
                                    Button(
                                        onClick = {
                                            selectedSeason = season.seasonNumber
                                            selectedEpisode = 1
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
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
                                        modifier = Modifier.height(24.dp)
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
                                    val hVer = com.example.tvmediaapp.data.history.WatchHistoryManager.historyVersion
                                    val isSelected = ep.episodeNumber == selectedEpisode
                                    val isWatched = historyManager.isEpisodeWatched(currentMovie.id, selectedSeason, ep.episodeNumber, currentMovie.title)
                                    val epProgress = historyManager.getEpisodeProgress(currentMovie.id, selectedSeason, ep.episodeNumber, currentMovie.title)
                                    var isButtonFocused by remember { mutableStateOf(false) }
                                    val epFocusMod = if (epIdx == 0) {
                                        Modifier.focusRequester(episodesFocusRequester).focusProperties { up = tabsFocusRequester }
                                    } else Modifier
                                    Button(
                                        onClick = {
                                            selectedEpisode = ep.episodeNumber
                                            historyManager.markEpisodeWatched(currentMovie.id, selectedSeason, ep.episodeNumber, currentMovie.title)
                                            if (newEpisodesCount > 0) {
                                                historyManager.clearNewEpisodes(currentMovie.id)
                                                newEpisodesCount = 0
                                            }
                                            startPlayback(targetSeason = selectedSeason, targetEpisode = ep.episodeNumber, startPos = 0L)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
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
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .defaultMinSize(minWidth = 60.dp)
                                            .then(epFocusMod)
                                            .onFocusChanged { isButtonFocused = it.isFocused }
                                            .focusedGlow(isFocused = isButtonFocused, color = focusColor, radius = 6.dp, shapeRadius = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(bottom = 5.dp, start = 4.dp, end = 4.dp)
                                            ) {
                                                if (isWatched || epProgress >= 85) {
                                                    Text(
                                                        text = "✓",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isSelected) Color.Black else Color(0xFF22C55E)
                                                    )
                                                }
                                                Text(
                                                    text = ep.title,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            // Visible Timeline Progress Track & Fill anchored along entire button bottom edge
                                            val progressPct = if (isWatched || epProgress >= 85) 1.0f else if (epProgress > 0) (epProgress.coerceIn(5, 100) / 100f) else 0f
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .height(3.5.dp)
                                                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                                    .background(Color.White.copy(alpha = 0.22f))
                                            ) {
                                                if (progressPct > 0f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(progressPct)
                                                            .background(
                                                                if (isSelected) Color(0xFF0F172A)
                                                                else if (isWatched || epProgress >= 85) Color(0xFF22C55E)
                                                                else Color(0xFFFFB300)
                                                            )
                                                    )
                                                }
                                            }
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
                                    currentMovie.seasons.forEach { s ->
                                        s.episodes.forEach { ep ->
                                            list.add(
                                                com.example.tvmediaapp.data.models.EpisodeScheduleItem(
                                                    episode = "${s.seasonNumber} сезон ${ep.episodeNumber} серия",
                                                    title = ep.title,
                                                    date = "Вышла",
                                                    status = "Доступна"
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

                                    var isRowFocused by remember { mutableStateOf(false) }
                                    val itemFocusMod = if (itemIdx == 0) {
                                        Modifier.focusRequester(firstScheduleItemFocusRequester).focusProperties { up = scheduleTabFocusRequester }
                                    } else Modifier

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
                                            .then(itemFocusMod)
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
                                            if (item.date.isNotBlank()) {
                                                Text(
                                                    text = item.date,
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
                                            val dirMod = if (dirIdx == 0) {
                                                Modifier
                                                    .focusRequester(firstDirectorFocusRequester)
                                                    .focusProperties {
                                                        up = descriptionTabFocusRequester
                                                    }
                                            } else Modifier
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
                                                    .then(dirMod)
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
                                            val castMod = if (actorIdx == 0) {
                                                Modifier
                                                    .focusRequester(firstCastFocusRequester)
                                                    .focusProperties {
                                                        up = if (displayDirectors.isNotEmpty()) firstDirectorFocusRequester else descriptionTabFocusRequester
                                                    }
                                            } else Modifier
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
                                                    .then(castMod)
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
                                    .padding(28.dp),
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
    }
}
