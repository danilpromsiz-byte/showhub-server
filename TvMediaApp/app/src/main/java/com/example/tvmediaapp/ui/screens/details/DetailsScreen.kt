package com.example.tvmediaapp.ui.screens.details

import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
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
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.CommentItem
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.StreamOption
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.screens.player.isDirectVideoStream
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.FavoriteGold
import com.example.tvmediaapp.ui.theme.ImdbGold
import com.example.tvmediaapp.ui.theme.KpOrange
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: Movie,
    onPlayClick: (videoUrl: String, startPositionMs: Long, season: Int, episode: Int) -> Unit,
    onBackClick: () -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current

    val historyManager = remember { WatchHistoryManager(context) }
    val savedHistory = remember(movie.id) {
        historyManager.getProgress(movie.id)
    }

    var currentMovie by remember { mutableStateOf(movie) }
    var selectedSeason by remember { mutableStateOf(savedHistory?.season ?: 1) }
    var selectedEpisode by remember { mutableStateOf(savedHistory?.episode ?: 1) }
    var selectedAudioId by remember { mutableStateOf(savedHistory?.audioId ?: "") }
    var selectedQuality by remember { mutableStateOf("1080p") }
    var isResolving by remember { mutableStateOf(false) }
    var streamStatus by remember { mutableStateOf<String?>(null) }
    var streamOptions by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var selectedDetailTab by remember { mutableIntStateOf(0) }
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var isLoadingComments by remember { mutableStateOf(false) }

    // Focus Requesters for instant TV remote control & bidirectional navigation
    val playButtonFocusRequester = remember { FocusRequester() }
    val fromStartButtonFocusRequester = remember { FocusRequester() }
    val trailerButtonFocusRequester = remember { FocusRequester() }
    val externalPlayerFocusRequester = remember { FocusRequester() }
    val favoriteButtonFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val leftPaneFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }

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

    // Fetch deep metadata (seasons, episodes, translators, KP rating) in background
    LaunchedEffect(movie.id) {
        val detailed = ShowHubApiClient.fetchMediaDetails(movie)
        currentMovie = detailed
        if (detailed.seasons.isNotEmpty() && savedHistory == null) {
            selectedSeason = detailed.seasons.first().seasonNumber
        }
        if (detailed.audioTracks.isNotEmpty() && selectedAudioId.isEmpty()) {
            selectedAudioId = detailed.audioTracks.first().id
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
                    episode = selectedEpisode
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

    // Background video preview in details screen (starts after 1.2s, silent clip from 22/12 min)
    LaunchedEffect(currentMovie.id) {
        delay(1200)
        if (detailsPreviewPlayer == null) {
            var streamUrl: String? = streamOptions.firstOrNull { isDirectVideoStream(it.url) }?.url
            if (streamUrl.isNullOrEmpty()) {
                try {
                    val nativeStreams = RezkaNativeResolver.resolveStreams(
                        title = currentMovie.title,
                        year = currentMovie.releaseYear,
                        isSeries = currentMovie.isSeries
                    )
                    streamUrl = nativeStreams.firstOrNull { it.url.contains(".m3u8") || it.url.contains(".mp4") }?.url
                        ?: nativeStreams.firstOrNull()?.url
                } catch (_: Exception) {}
            }
            if (streamUrl.isNullOrEmpty()) {
                streamUrl = ShowHubApiClient.fetchPreviewStream(currentMovie)
            }

            if (!streamUrl.isNullOrEmpty()) {
                try {
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(mapOf("Referer" to "https://hdrezka.ag/"))
                        .setConnectTimeoutMs(8000)
                        .setReadTimeoutMs(15000)
                        .setAllowCrossProtocolRedirects(true)
                    val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

                    var hasSeeked = false
                    val player = ExoPlayer.Builder(context)
                        .setMediaSourceFactory(mediaSourceFactory)
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

    DisposableEffect(Unit) {
        onDispose {
            isDetailsPreviewPlaying = false
            detailsPreviewPlayer?.stop()
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
                    episode = targetEpisode
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
            val combined = (nativeStreams + serverStreams).distinctBy { it.url }
            // Sort direct streams (HLS/MP4) first, balancers last
            val streams = combined.sortedByDescending { isDirectVideoStream(it.url) }

            streamOptions = streams
            isResolving = false

            if (streams.isNotEmpty()) {
                val matched = streams.firstOrNull { it.quality.contains(selectedQuality) && isDirectVideoStream(it.url) }
                    ?: streams.firstOrNull { isDirectVideoStream(it.url) }
                    ?: streams.first()
                streamStatus = "Найден поток ${matched.quality}! Запуск..."
                onPlayClick(matched.url, startPos, targetSeason, targetEpisode)
            } else {
                streamStatus = "Поток в обработке. Попробуйте другой фильм."
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        // High-res backdrop
        AsyncImage(
            model = currentMovie.backdropUrl.ifEmpty { currentMovie.posterUrl },
            contentDescription = currentMovie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Details Background Video Preview (smooth crossfade)
        if (isDetailsPreviewPlaying && detailsPreviewPlayer != null) {
            val previewAlpha by animateFloatAsState(
                targetValue = if (isDetailsPreviewPlaying) 0.65f else 0f,
                animationSpec = tween(durationMillis = 800),
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
            )
        }

        // Dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BackgroundDark,
                            BackgroundDark.copy(alpha = 0.95f),
                            BackgroundDark.copy(alpha = 0.70f)
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
            Card(
                onClick = { /* keep focus */ },
                colors = CardDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.04f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f)
                ),
                border = CardDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))),
                    focusedBorder = Border(BorderStroke(2.5.dp, accent))
                ),
                scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.02f),
                shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .focusRequester(leftPaneFocusRequester)
                    .focusProperties {
                        right = playButtonFocusRequester
                    }
                    .onFocusChanged { isLeftPaneFocused = it.isFocused }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
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
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ratings Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
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
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = currentMovie.title,
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

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons - Row 1 (Playback Actions)
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
                                border = Border(BorderStroke(1.dp, Color.Transparent)),
                                focusedBorder = Border(BorderStroke(2.5.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties {
                                    left = leftPaneFocusRequester
                                    right = fromStartButtonFocusRequester
                                    down = favoriteButtonFocusRequester
                                }
                        ) {
                            Text(
                                text = resumeLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        Button(
                            onClick = { startPlayback(startPos = 0L) },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = accent,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                focusedBorder = Border(BorderStroke(2.5.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(fromStartButtonFocusRequester)
                                .focusProperties {
                                    left = playButtonFocusRequester
                                    right = trailerButtonFocusRequester
                                    down = favoriteButtonFocusRequester
                                }
                        ) {
                            Text(
                                text = "С начала",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
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
                                border = Border(BorderStroke(1.dp, Color.Transparent)),
                                focusedBorder = Border(BorderStroke(2.5.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties {
                                    left = leftPaneFocusRequester
                                    right = trailerButtonFocusRequester
                                    down = favoriteButtonFocusRequester
                                }
                        ) {
                            Text(
                                text = if (isResolving) "Поиск потока..." else "Смотреть онлайн",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
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
                            focusedContainerColor = accent,
                            contentColor = TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                            focusedBorder = Border(BorderStroke(2.5.dp, TextWhite))
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .focusRequester(trailerButtonFocusRequester)
                            .focusProperties {
                                left = if (hasResume) fromStartButtonFocusRequester else playButtonFocusRequester
                                right = externalPlayerFocusRequester
                                down = favoriteButtonFocusRequester
                            }
                    ) {
                        Text(
                            text = "Трейлер",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
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
                                            episode = selectedEpisode
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
                                    val matched = streams.firstOrNull { it.quality.contains(selectedQuality) && isDirectVideoStream(it.url) }
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
                            focusedContainerColor = accent,
                            contentColor = TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                            focusedBorder = Border(BorderStroke(2.5.dp, TextWhite))
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .focusRequester(externalPlayerFocusRequester)
                            .focusProperties {
                                left = trailerButtonFocusRequester
                                down = backButtonFocusRequester
                            }
                    ) {
                        Text(
                            text = "Внешний плеер",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons - Row 2 (Library & Navigation Actions)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onToggleFavorite(currentMovie) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isFavorite) FavoriteGold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = FavoriteGold,
                            contentColor = if (isFavorite) FavoriteGold else TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, if (isFavorite) FavoriteGold else Color.White.copy(alpha = 0.15f))
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.5.dp, TextWhite)
                            )
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .focusRequester(favoriteButtonFocusRequester)
                            .focusProperties {
                                left = leftPaneFocusRequester
                                up = playButtonFocusRequester
                                right = backButtonFocusRequester
                                down = tabsFocusRequester
                            }
                    ) {
                        Text(
                            text = if (isFavorite) "В избранном" else "В избранное",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = accent,
                            contentColor = TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border(border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                            focusedBorder = Border(border = BorderStroke(2.5.dp, TextWhite))
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .focusRequester(backButtonFocusRequester)
                            .focusProperties {
                                left = favoriteButtonFocusRequester
                                up = externalPlayerFocusRequester
                                down = tabsFocusRequester
                            }
                    ) {
                        Text(
                            text = "Назад",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
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

                // Detail Section Tabs: «Плеер и серии», «Описание и детали», «Отзывы (N)»
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        "Плеер и серии",
                        "Описание и детали",
                        "Отзывы" + if (comments.isNotEmpty()) " (${comments.size})" else ""
                    )
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedDetailTab == index
                        val tabMod = if (index == 0) {
                            Modifier
                                .height(34.dp)
                                .focusRequester(tabsFocusRequester)
                                .focusProperties {
                                    up = favoriteButtonFocusRequester
                                    left = leftPaneFocusRequester
                                }
                        } else {
                            Modifier
                                .height(34.dp)
                                .focusProperties {
                                    up = favoriteButtonFocusRequester
                                }
                        }
                        Button(
                            onClick = { selectedDetailTab = index },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent else ChipBackground,
                                focusedContainerColor = accent,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(BorderStroke(1.dp, if (isSelected) accent else Color.Transparent)),
                                focusedBorder = Border(BorderStroke(2.5.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = tabMod
                        ) {
                            Text(
                                text = tabTitle,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedDetailTab) {
                    0 -> {
                        // TAB 0: ПЛЕЕР И СЕРИИ
                        // Translators
                        if (currentMovie.audioTracks.isNotEmpty()) {
                            Text(
                                text = "Озвучка / Перевод:",
                                fontSize = 14.sp,
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
                                            startPlayback(targetAudioId = track.id, startPos = 0L)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(text = track.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Quality Selector Ribbon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Качество:", fontSize = 13.sp, color = TextGray)
                            listOf("1080p Ultra", "1080p", "720p").forEach { quality ->
                                val isSelected = quality == selectedQuality
                                Button(
                                    onClick = { selectedQuality = quality },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSelected) accent else ChipBackground,
                                        focusedContainerColor = accent,
                                        contentColor = if (isSelected) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(text = quality, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        // SERIES: Seasons & Episodes
                        if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "Сезоны:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Spacer(modifier = Modifier.height(6.dp))

                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(currentMovie.seasons) { season ->
                                    val isSelected = season.seasonNumber == selectedSeason
                                    Button(
                                        onClick = {
                                            selectedSeason = season.seasonNumber
                                            selectedEpisode = 1
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isSelected) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(text = season.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val activeSeason = currentMovie.seasons.firstOrNull { it.seasonNumber == selectedSeason } ?: currentMovie.seasons.first()
                            Text(
                                text = "Серии (${activeSeason.episodes.size}):",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(activeSeason.episodes) { ep ->
                                    val isSelected = ep.episodeNumber == selectedEpisode
                                    Button(
                                        onClick = {
                                            selectedEpisode = ep.episodeNumber
                                            startPlayback(targetSeason = selectedSeason, targetEpisode = ep.episodeNumber, startPos = 0L)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) accent.copy(alpha = 0.3f) else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isSelected) accent else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(border = BorderStroke(1.dp, if (isSelected) accent else Color.Transparent)),
                                            focusedBorder = Border(border = BorderStroke(2.dp, TextWhite))
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(text = ep.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: ОПИСАНИЕ И ДЕТАЛИ
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Сюжет:",
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
                            Spacer(modifier = Modifier.height(8.dp))
                            if (currentMovie.director.isNotEmpty()) {
                                Text(text = "Режиссёр: ${currentMovie.director}", fontSize = 13.sp, color = TextGray)
                            }
                            if (currentMovie.country.isNotEmpty()) {
                                Text(text = "Страна производства: ${currentMovie.country}", fontSize = 13.sp, color = TextGray)
                            }
                            if (currentMovie.releaseYear.isNotEmpty()) {
                                Text(text = "Год премьеры: ${currentMovie.releaseYear}", fontSize = 13.sp, color = TextGray)
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: ОТЗЫВЫ ЗРИТЕЛЕЙ
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                comments.forEach { c ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .padding(14.dp)
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
            }
        }
    }
}
