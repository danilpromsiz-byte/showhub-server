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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
                        !q.contains("ultra") && !q.contains("4k") && !q.contains("vip") && isDirectVideoStream(it.url)
                    }
                    streamUrl = nonPremium.firstOrNull { it.quality.contains("720") }?.url
                        ?: nonPremium.firstOrNull { it.quality.contains("1080") }?.url
                        ?: nonPremium.firstOrNull { it.quality.contains("480") }?.url
                        ?: nonPremium.firstOrNull()?.url
                } catch (e: Exception) {
                    // fallback to server
                }

                if (streamUrl.isNullOrEmpty()) {
                    val candidate = ShowHubApiClient.fetchPreviewStream(movie)
                    if (candidate != null && isDirectVideoStream(candidate)) {
                        streamUrl = candidate
                    }
                }

                if (isFocused && !streamUrl.isNullOrEmpty() && isDirectVideoStream(streamUrl)) {
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
                                val targetSeekMs = if (movie.isSeries) 12 * 60 * 1000L else 22 * 60 * 1000L
                                setMediaItem(MediaItem.fromUri(streamUrl))
                                volume = 0f // strictly silent
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
                                            isPreviewBuffering = false
                                            isPreviewPlaying = true
                                        } else if (state == Player.STATE_BUFFERING) {
                                            isPreviewBuffering = true
                                        } else if (state == Player.STATE_ENDED) {
                                            seekTo(0L)
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
                player.release()
            }
            previewPlayer = null
        }
    }

    // Cleanup player when card leaves composition
    DisposableEffect(Unit) {
        onDispose {
            isPreviewBuffering = false
            isPreviewPlaying = false
            previewPlayer?.let { player ->
                player.stop()
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
                        border = BorderStroke(2.5.dp, accent)
                    )
                ),
                scale = CardDefaults.scale(
                    scale = 1.0f,
                    focusedScale = 1.04f
                ),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(cardModifier)
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

                    // Rating Pill Badge (top right)
                    if (movie.rating > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format("%.1f", movie.rating),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
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
                val typeStr = if (movie.isSeries) "Сериал" else "Фильм"
                val subText = if (cleanYear.isNotEmpty()) "$cleanYear • $typeStr" else typeStr
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
