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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current

    val historyManager = remember { WatchHistoryManager(context) }
    val savedHistory = remember(movie.id) {
        historyManager.getProgress(movie.id)
    }

    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }
    val rightPaneScrollState = rememberScrollState()
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
    val episodesFocusRequester = remember { FocusRequester() }

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
            val nativeDeferred = async {
                RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = currentMovie.isSeries,
                    season = selectedSeason,
                    episode = selectedEpisode,
                    translatorId = selectedAudioId.ifEmpty { null }
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
                        episode = if (currentMovie.isSeries) selectedEpisode else 1
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
            // Priority 1: Query Rezka directly on TV (residential IP) and server concurrently
            val nativeDeferred = async {
                RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = currentMovie.isSeries,
                    season = targetSeason,
                    episode = targetEpisode,
                    translatorId = targetAudioId.ifEmpty { null }
                )
            }
            val serverDeferred = async {
                ShowHubApiClient.fetchStreams(
                    movie = currentMovie,
                    season = if (currentMovie.isSeries) targetSeason else null,
                    episode = if (currentMovie.isSeries) targetEpisode else null,
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
                    focusedBorder = Border(BorderStroke(2.dp, accent))
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
                                focusedContainerColor = Color.White,
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
                                focusedContainerColor = Color.White,
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
                                focusedContainerColor = Color.White,
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
                                focusedContainerColor = Color.White,
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
                            focusedContainerColor = Color.White,
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
                                            translatorId = selectedAudioId.ifEmpty { null }
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
                            focusedContainerColor = Color.White,
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
                        onClick = { onToggleFavorite(currentMovie) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isFavorite) FavoriteGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color.White,
                            contentColor = if (isFavorite) Color.Black else TextWhite,
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
                                resId = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border,
                                tint = if (isFavorite) FavoriteGold else androidx.tv.material3.LocalContentColor.current,
                                size = 13.dp
                            )
                            Text(
                                text = if (isFavorite) "В избранном" else "В избранное",
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
                            focusedContainerColor = Color.White,
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedDetailTab == index
                        val tabMod = if (index == 0) {
                            Modifier
                                .height(26.dp)
                                .focusRequester(tabsFocusRequester)
                                .focusProperties {
                                    up = favoriteButtonFocusRequester
                                    left = leftPaneFocusRequester
                                }
                        } else {
                            Modifier
                                .height(26.dp)
                                .focusProperties {
                                    up = favoriteButtonFocusRequester
                                }
                        }
                        Button(
                            onClick = { selectedDetailTab = index },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
                                focusedContainerColor = Color.White,
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

                when {
                    activeTabTitle.startsWith("Плеер") -> {
                        // TAB 0: ПЛЕЕР И СЕРИИ
                        // Translators
                        if (currentMovie.audioTracks.isNotEmpty()) {
                            Text(
                                text = "Озвучка / Перевод:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(currentMovie.audioTracks) { track ->
                                    val isSelected = track.id == selectedAudioId
                                    Button(
                                        onClick = {
                                            selectedAudioId = track.id
                                            if (currentMovie.isSeries) {
                                                coroutineScope.launch {
                                                    try {
                                                        val realSeasons = ShowHubApiClient.fetchEpisodes(currentMovie, track.id)
                                                        if (realSeasons.isNotEmpty()) {
                                                            currentMovie = currentMovie.copy(seasons = realSeasons)
                                                            val validSeason = realSeasons.firstOrNull { it.seasonNumber == selectedSeason } ?: realSeasons.first()
                                                            selectedSeason = validSeason.seasonNumber
                                                            val maxEp = validSeason.episodes.maxOfOrNull { it.episodeNumber } ?: 1
                                                            selectedEpisode = selectedEpisode.coerceIn(1, maxEp)
                                                            streamStatus = "Озвучка: «${track.name}» (доступно $maxEp сер.)"
                                                        }
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = Color.White,
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
                                        Text(text = track.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
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
                                            focusedContainerColor = Color.White,
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
                            Text(
                                text = "Серии (${activeSeason.episodes.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(5.dp))

                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(activeSeason.episodes) { ep ->
                                    val isSelected = ep.episodeNumber == selectedEpisode
                                    val isWatched = historyManager.isEpisodeWatched(currentMovie.id, selectedSeason, ep.episodeNumber)
                                    val epProgress = historyManager.getEpisodeProgress(currentMovie.id, selectedSeason, ep.episodeNumber)
                                    Button(
                                        onClick = {
                                            selectedEpisode = ep.episodeNumber
                                            historyManager.markEpisodeWatched(currentMovie.id, selectedSeason, ep.episodeNumber)
                                            if (newEpisodesCount > 0) {
                                                historyManager.clearNewEpisodes(currentMovie.id)
                                                newEpisodesCount = 0
                                            }
                                            startPlayback(targetSeason = selectedSeason, targetEpisode = ep.episodeNumber, startPos = 0L)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = Color.White,
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
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                if (isWatched || epProgress >= 85) {
                                                    Text(
                                                        text = "✓",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) Color.Black else Color(0xFF4ADE80)
                                                    )
                                                }
                                                Text(
                                                    text = ep.title,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            // Progress timeline bar for partially watched episodes (e.g. 50%)
                                            if (epProgress in 5..84 && !isWatched) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.5.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(Color.White.copy(alpha = 0.2f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(epProgress / 100f)
                                                            .background(if (isSelected) Color.Black else accent)
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
                                            focusedContainerColor = LocalFocusColor.current.copy(alpha = 0.22f)
                                        ),
                                        border = CardDefaults.border(
                                            border = Border(BorderStroke(2.dp, Color.Transparent)),
                                            focusedBorder = Border(BorderStroke(2.dp, LocalFocusColor.current))
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
                                                color = if (isActorFocused) LocalFocusColor.current else TextWhite,
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
                                scheduleItems.forEach { item ->
                                    val itemLower = (item.status + " " + item.date).lowercase()
                                    val isAired = itemLower.contains("вышла") || itemLower.contains("доступна") || itemLower.contains("вчера") || itemLower.contains("сегодня")
                                    val statusBg = if (isAired) Color(0xFF1B5E20).copy(alpha = 0.85f) else Color(0xFF0D47A1).copy(alpha = 0.85f)
                                    val statusFg = if (isAired) Color(0xFF81C784) else Color(0xFF90CAF9)

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
                    }

                    activeTabTitle.startsWith("Описание") -> {
                        // TAB: ОПИСАНИЕ И ДЕТАЛИ (С возможностью скролла пультом)
                        var isSynopsisFocused by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSynopsisFocused) accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSynopsisFocused) accent else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusable()
                                .onFocusChanged { isSynopsisFocused = it.isFocused }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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

                            // Director Strip
                            if (displayDirectors.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Режиссёр (нажмите для поиска фильмов):",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TvLazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(displayDirectors) { director ->
                                        var isDirFocused by remember { mutableStateOf(false) }
                                        Card(
                                            onClick = { onSearchClick(director.name) },
                                            colors = CardDefaults.colors(
                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                focusedContainerColor = LocalFocusColor.current.copy(alpha = 0.22f)
                                            ),
                                            border = CardDefaults.border(
                                                border = Border(BorderStroke(2.dp, Color.Transparent)),
                                                focusedBorder = Border(BorderStroke(2.dp, LocalFocusColor.current))
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
                                                    color = if (isDirFocused) LocalFocusColor.current else TextWhite,
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

                            if (displayCast.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "В главных ролях (нажмите для поиска фильмов):",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
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
                                                focusedContainerColor = LocalFocusColor.current.copy(alpha = 0.22f)
                                            ),
                                            border = CardDefaults.border(
                                                border = Border(BorderStroke(2.dp, Color.Transparent)),
                                                focusedBorder = Border(BorderStroke(2.dp, LocalFocusColor.current))
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
                                                    color = if (isActorFocused) LocalFocusColor.current else TextWhite,
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

                            var isMetaBlockFocused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isMetaBlockFocused) LocalFocusColor.current.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                                    .onFocusChanged { isMetaBlockFocused = it.isFocused }
                                    .focusable()
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
                                                color = if (isCommentFocused) accent else Color.White.copy(alpha = 0.08f),
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
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }
}
