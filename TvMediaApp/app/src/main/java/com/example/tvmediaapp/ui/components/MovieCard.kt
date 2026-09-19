@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)

package com.example.tvmediaapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.ui.screens.player.isDirectVideoStream
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current
    val historyManager = remember { com.example.tvmediaapp.data.history.WatchHistoryManager(context) }
    val newEpisodesCount = remember(movie.id) {
        if (movie.isSeries) historyManager.getNewEpisodesCount(movie.id) else 0
    }
    var isFocused by remember { mutableStateOf(false) }

    // Video preview state
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPreviewBuffering by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var targetTimelineProgress by remember { mutableFloatStateOf(0f) }

    val timelineProgress by animateFloatAsState(
        targetValue = targetTimelineProgress,
        animationSpec = tween(durationMillis = 1500),
        label = "previewProgress"
    )

    // Handle focus preview timer & stream loading
    LaunchedEffect(isFocused) {
        if (isFocused) {
            targetTimelineProgress = 1f
            // Wait 1.1 seconds before starting preview
            delay(1100)
            if (isFocused) {
                isPreviewBuffering = true
                var streamUrl: String? = null
                try {
                    val nativeStreams = RezkaNativeResolver.resolveStreams(
                        title = movie.title,
                        year = movie.releaseYear,
                        isSeries = movie.isSeries
                    )
                    val nonPremium = nativeStreams.filter {
                        val q = it.quality.lowercase()
                        val u = it.url.lowercase()
                        !q.contains("ultra") && !q.contains("4k") && !q.contains("vip") && !q.contains("premium") &&
                        !u.contains("rhtie") && !u.contains("trial") && !u.contains("preview") &&
                        !u.contains("teaser") && !u.contains("promo") && !u.contains("vip") && !u.contains("ultra") &&
                        isDirectVideoStream(it.url)
                    }
                    streamUrl = nonPremium.firstOrNull { it.quality.contains("720") }?.url
                        ?: nonPremium.firstOrNull { it.quality.contains("1080") }?.url
                        ?: nonPremium.firstOrNull { it.quality.contains("480") }?.url
                        ?: nonPremium.firstOrNull()?.url
                } catch (e: Exception) {
                    // fallback to server
                }

                if (streamUrl.isNullOrEmpty()) {
                    val prefs = context.getSharedPreferences("showhub_prefs", android.content.Context.MODE_PRIVATE)
                    val configuredStartMin = prefs.getInt("pref_preview_start_min", if (movie.isSeries) 12 else 22)
                    val candidate = ShowHubApiClient.fetchPreviewStream(movie, configuredStartMin)
                    if (candidate != null && isDirectVideoStream(candidate)) {
                        streamUrl = candidate
                    }
                }

                if (isFocused && !streamUrl.isNullOrEmpty() && isDirectVideoStream(streamUrl)) {
                    val prefs = context.getSharedPreferences("showhub_prefs", android.content.Context.MODE_PRIVATE)
                    val baseStartMin = prefs.getInt("pref_preview_start_min", if (movie.isSeries) 12 else 22)
                    val baseSeekMs = baseStartMin * 60 * 1000L

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
                                setMediaItem(MediaItem.fromUri(streamUrl))
                                volume = 0f // strictly silent
                                repeatMode = Player.REPEAT_MODE_ALL
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        if (state == Player.STATE_READY) {
                                            if (!hasSeeked) {
                                                hasSeeked = true
                                                if (duration > 0 && duration > baseSeekMs + 20_000L) {
                                                    seekTo(baseSeekMs)
                                                } else if (duration > 0) {
                                                    seekTo((duration * 0.25).toLong())
                                                }
                                            }
                                            isPreviewBuffering = false
                                            isPreviewPlaying = true
                                        } else if (state == Player.STATE_BUFFERING) {
                                            isPreviewBuffering = true
                                        } else if (state == Player.STATE_ENDED) {
                                            seekTo(baseSeekMs)
                                            play()
                                        }
                                    }

                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        isPreviewBuffering = false
                                        isPreviewPlaying = false
                                    }
                                })
                                prepare()
                                playWhenReady = true
                            }
                        previewPlayer = player

                        // Triple sequential preview: Phase 0 (T), Phase 1 (T+10m), Phase 2 (T+20m) cycling every 12s
                        var currentPhase = 0
                        while (isFocused && previewPlayer != null) {
                            delay(12_000)
                            if (!isFocused) break
                            currentPhase = (currentPhase + 1) % 3
                            val nextSeekMin = baseStartMin + (currentPhase * 10)
                            val nextSeekMs = nextSeekMin * 60 * 1000L
                            previewPlayer?.let { p ->
                                if (p.duration > 0 && p.duration > nextSeekMs + 15_000L) {
                                    p.seekTo(nextSeekMs)
                                } else if (p.duration > 0) {
                                    p.seekTo(baseSeekMs.coerceAtMost((p.duration * 0.5).toLong()))
                                    currentPhase = 0
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isPreviewBuffering = false
                    }
                } else {
                    isPreviewBuffering = false
                }
            }
        } else {
            // Cancel preview & release player immediately
            targetTimelineProgress = 0f
            isPreviewBuffering = false
            isPreviewPlaying = false
            previewPlayer?.let { player ->
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            previewPlayer = null
        }
    }

    // Cleanup player when card leaves composition or app goes to background
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, previewPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                previewPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isPreviewBuffering = false
            isPreviewPlaying = false
            previewPlayer?.let { player ->
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            previewPlayer = null
        }
    }

    StandardCardContainer(
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, focusColor)
                    )
                ),
                scale = CardDefaults.scale(
                    scale = 1.0f,
                    focusedScale = 1.0f
                ),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(cardModifier)
                    .focusedGlow(isFocused = isFocused, color = focusColor, radius = 10.dp, shapeRadius = 8.dp)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            onFocus()
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Fallback background in case image fails or is loading
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A)
                                )
                            )
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = movie.title,
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            val cleanYear = movie.releaseYear.replace("null", "").trim()
                            if (cleanYear.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cleanYear,
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Async Image with Coil
                    SubcomposeAsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Card Video Preview (ExoPlayer surface - smoothly appears once ready)
                    if (isPreviewPlaying && previewPlayer != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = previewPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    // Top-Left: Series / Episodes Info or New Episodes Alert
                    if (movie.isSeries) {
                        val parsedInfo = remember(movie.id, movie.episodesInfo, movie.seasons.size) {
                            var raw = movie.episodesInfo.trim()
                            if (raw.isBlank() && movie.isSeries) {
                                val knownTotal = historyManager.getKnownTotalEpisodes(movie.id)
                                if (knownTotal > 0) {
                                    raw = "$knownTotal сер."
                                } else {
                                    val hist = historyManager.getProgress(movie.id)
                                    if (hist != null && hist.episode > 0) {
                                        raw = if (hist.season > 1) "S${hist.season} E${hist.episode}" else "${hist.episode} сер."
                                    }
                                }
                            }
                            var sNum = 1
                            var epNum = 1
                            var totalEps: Int? = null
                            var badgeLabel = "Сериал"

                            if (raw.isNotBlank()) {
                                val sMatch = Regex("""(\d+)\s*(?:сезон|сез)""", RegexOption.IGNORE_CASE).find(raw)
                                if (sMatch != null) {
                                    sNum = sMatch.groupValues[1].toIntOrNull() ?: 1
                                }
                                val ratioMatch = Regex("""(\d+)\s*/\s*(\d+)""", RegexOption.IGNORE_CASE).find(raw)
                                if (ratioMatch != null) {
                                    epNum = ratioMatch.groupValues[1].toIntOrNull() ?: 1
                                    totalEps = ratioMatch.groupValues[2].toIntOrNull()
                                } else {
                                    val epMatch = Regex("""(\d+)\s*(?:сери|сер)""", RegexOption.IGNORE_CASE).find(raw)
                                    if (epMatch != null) {
                                        epNum = epMatch.groupValues[1].toIntOrNull() ?: 1
                                    } else {
                                        val anyNum = Regex("""\b(\d+)\b""").findAll(raw).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
                                        if (anyNum.size >= 2) {
                                            epNum = anyNum.last()
                                        } else if (anyNum.size == 1) {
                                            epNum = anyNum.first()
                                        }
                                    }
                                }

                                badgeLabel = when {
                                    raw.contains("завершен", ignoreCase = true) || raw.contains("все серии", ignoreCase = true) -> "Все серии"
                                    totalEps != null -> "$epNum/$totalEps сер."
                                    epNum > 0 -> "$epNum сер."
                                    else -> raw
                                }
                            } else if (movie.seasons.isNotEmpty()) {
                                val total = movie.seasons.sumOf { it.episodes.size }
                                epNum = total
                                badgeLabel = "$total сер."
                            }
                            Triple(sNum, epNum, badgeLabel)
                        }

                        val (seasonNum, epNum, epText) = parsedInfo
                        val isLatestUnwatched = remember(movie.id, seasonNum, epNum) {
                            !historyManager.isEpisodeWatched(movie.id, seasonNum, epNum)
                        }

                        if (newEpisodesCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE53935).copy(alpha = 0.95f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "+$newEpisodesCount новых",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Top-Right: Rating Badges (Age limit + KP or IMDb or general rating)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (movie.ageRating.isNotBlank()) {
                                val cleanAge = movie.ageRating.trim()
                                val (ageBg, ageFg) = when {
                                    cleanAge.contains("18") -> Color(0xFFD32F2F) to Color.White
                                    cleanAge.contains("16") -> Color(0xFFF57C00) to Color.White
                                    cleanAge.contains("12") -> Color(0xFF1976D2) to Color.White
                                    cleanAge.contains("6") || cleanAge.contains("0") -> Color(0xFF388E3C) to Color.White
                                    else -> Color.Black.copy(alpha = 0.85f) to Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ageBg.copy(alpha = 0.92f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = cleanAge,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ageFg
                                    )
                                }
                            }
                            if (movie.ratingKp > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFF6600).copy(alpha = 0.92f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "КП ${String.format(java.util.Locale.US, "%.1f", movie.ratingKp)}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else if (movie.ratingImdb > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE5A00D).copy(alpha = 0.92f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "IMDb ${String.format(java.util.Locale.US, "%.1f", movie.ratingImdb)}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            } else if (movie.rating > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.80f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f", movie.rating),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }

                    // Bottom-left: Country flag + Max Non-Premium Quality badge
                    val countryBadge = com.example.tvmediaapp.data.models.getCountryBadge(movie.country, movie.genres, movie.title)
                    val badgeQuality = movie.maxQuality.ifEmpty { "1080p" }
                    val badgeText = if (countryBadge.isNotBlank()) "$countryBadge • $badgeQuality" else badgeQuality
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Preview Timeline Bar (animates across top while waiting for preview)
                    if (isFocused && !isPreviewPlaying && timelineProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(timelineProgress)
                                    .height(3.dp)
                                    .background(accent)
                            )
                        }
                    }
                }
            }
        },
        title = {
            // Rigid fixed-height title container with marquee scroll on focus
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val marqueeMod = if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp
                    ),
                    color = TextWhite,
                    maxLines = if (isFocused) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(marqueeMod)
                )
            }
        },
        subtitle = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cleanYear = movie.releaseYear.replace("null", "").trim()
                val seriesOrMovieInfo = if (movie.isSeries) {
                    val hist = historyManager.getProgress(movie.id)
                    if (hist != null && hist.episode > 0) {
                        "${hist.season} сезон ${hist.episode} сер."
                    } else if (movie.episodesInfo.isNotBlank()) {
                        movie.episodesInfo
                    } else {
                        "Сериал"
                    }
                } else {
                    if (movie.genres.isNotEmpty()) "Фильм • ${movie.genres.first()}" else "Фильм"
                }

                val subText = if (cleanYear.isNotEmpty()) "$cleanYear • $seriesOrMovieInfo" else seriesOrMovieInfo
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (isFocused) accent else TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = modifier.padding(4.dp)
    )
}
