package com.example.tvmediaapp.ui.screens.player

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.RedPrimary
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.focusProperties
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.theme.ChipBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun isDirectVideoStream(url: String): Boolean {
    val clean = url.lowercase().trim()
    if (clean.contains("embed") || clean.contains("allarknow") || clean.contains("bazon.cc") || 
        clean.contains("delivembd") || clean.contains("kinobase") || clean.contains("iframe") || 
        clean.endsWith(".html") || clean.contains(".html?")) {
        return false
    }
    return clean.contains(".m3u8") || clean.contains(".mp4") || clean.contains("voidboost") || 
           clean.contains("/stream/") || clean.contains("/hls/") || clean.contains(".mkv") || clean.contains(".webm")
}

@Composable
fun PlayerScreen(
    movie: Movie,
    startPositionMs: Long = 0L,
    season: Int = 1,
    episode: Int = 1,
    onBackPress: () -> Unit
) {
    if (isDirectVideoStream(movie.videoUrl)) {
        NativeExoPlayerScreen(
            movie = movie,
            startPositionMs = startPositionMs,
            season = season,
            episode = episode,
            onBackPress = onBackPress
        )
    } else {
        EmbedWebViewPlayerScreen(
            movie = movie,
            onBackPress = onBackPress
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebViewPlayerScreen(
    movie: Movie,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        webViewRef?.let { wv ->
            if (wv.canGoBack()) {
                wv.goBack()
                return@BackHandler
            }
        }
        onBackPress()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        allowFileAccess = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    document.body.style.backgroundColor = '#000';
                                    document.body.style.margin = '0';
                                    document.body.style.padding = '0';
                                    document.body.style.overflow = 'hidden';
                                    var f = document.querySelector('iframe');
                                    if (f) {
                                        f.style.width = '100vw';
                                        f.style.height = '100vh';
                                        f.style.border = '0';
                                    }
                                })();
                                """.trimIndent(), null
                            )
                        }
                    }
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                          html, body { margin: 0; padding: 0; width: 100vw; height: 100vh; background: #000; overflow: hidden; }
                          iframe { width: 100%; height: 100%; border: 0; position: absolute; top: 0; left: 0; }
                        </style>
                        </head>
                        <body>
                          <iframe src="${movie.videoUrl}" allow="autoplay; fullscreen" allowfullscreen></iframe>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://showhub-server.onrender.com", html, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NativeExoPlayerScreen(
    movie: Movie,
    startPositionMs: Long = 0L,
    season: Int = 1,
    episode: Int = 1,
    onBackPress: () -> Unit
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val historyManager = remember { WatchHistoryManager(context) }

    var currentSeason by remember { mutableIntStateOf(season) }
    var currentEpisode by remember { mutableIntStateOf(episode) }
    var currentStreamUrl by remember { mutableStateOf(movie.videoUrl) }
    var currentAudioId by remember { mutableStateOf(movie.audioTracks.firstOrNull()?.id ?: "") }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(startPositionMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var activeDrawer by remember { mutableStateOf<String?>(null) } // "audio", "episodes", null

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val prevEpisodeFocusRequester = remember { FocusRequester() }
    val episodesDrawerFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val extPlayerFocusRequester = remember { FocusRequester() }
    val episodesRowFocusRequester = remember { FocusRequester() }

    // Initialize Media3 ExoPlayer with headers and resume support
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://hdrezka.ag/"))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(currentStreamUrl))
                if (startPositionMs > 1000L) {
                    seekTo(startPositionMs)
                }
                prepare()
                playWhenReady = true
            }
    }

    fun switchStream(newSeason: Int, newEpisode: Int, newAudioId: String) {
        val isSameEpisode = (newSeason == currentSeason && newEpisode == currentEpisode)
        currentSeason = newSeason
        currentEpisode = newEpisode
        currentAudioId = newAudioId
        coroutineScope.launch {
            try {
                val savedPos = exoPlayer.currentPosition
                val streams = ShowHubApiClient.fetchStreams(
                    movie = movie,
                    season = if (movie.isSeries) newSeason else null,
                    episode = if (movie.isSeries) newEpisode else null,
                    audioId = newAudioId
                )
                val targetStream = streams.firstOrNull { isDirectVideoStream(it.url) } ?: streams.firstOrNull()
                if (targetStream != null) {
                    currentStreamUrl = targetStream.url
                    exoPlayer.setMediaItem(MediaItem.fromUri(targetStream.url))
                    if (isSameEpisode) {
                        exoPlayer.seekTo(savedPos)
                    } else {
                        exoPlayer.seekTo(0L)
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Monitor playback progress & periodically save to WatchHistoryManager
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            isPlaying = exoPlayer.isPlaying

            if (currentPosition > 3000L && duration > 0L) {
                historyManager.saveProgress(
                    movie = movie,
                    positionMs = currentPosition,
                    durationMs = duration,
                    season = currentSeason,
                    episode = currentEpisode,
                    audioId = currentAudioId
                )
            }
            delay(1000)
        }
    }

    // Auto-hide TV controls after 6 seconds when not in drawer
    LaunchedEffect(isControlsVisible, activeDrawer) {
        if (isControlsVisible && activeDrawer == null) {
            delay(6000)
            isControlsVisible = false
        }
    }

    // Auto-focus play/pause button when controls become visible
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible && activeDrawer == null) {
            delay(50)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Handle Hardware Back button - always exits cleanly or dismisses drawer
    BackHandler {
        if (activeDrawer != null) {
            activeDrawer = null
        } else {
            onBackPress()
        }
    }

    // Cleanup player on screen exit & save final position
    DisposableEffect(Unit) {
        onDispose {
            try {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                if (pos > 3000L && dur > 0L) {
                    historyManager.saveProgress(
                        movie = movie,
                        positionMs = pos,
                        durationMs = dur,
                        season = currentSeason,
                        episode = currentEpisode,
                        audioId = currentAudioId
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Request focus for D-Pad events on launch
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                // Direct hardware back button handling
                if (nativeEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                        if (activeDrawer != null) {
                            activeDrawer = null
                            return@onKeyEvent true
                        }
                        onBackPress()
                        return@onKeyEvent true
                    }
                }
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    if (!isControlsVisible) {
                        isControlsVisible = true
                        try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                        return@onKeyEvent true
                    } else {
                        when (nativeEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                                return@onKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                try { episodesRowFocusRequester.requestFocus() } catch (_: Exception) {}
                                return@onKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (activeDrawer == null) {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                    return@onKeyEvent true
                                }
                            }
                        }
                    }
                }
                false
            }
    ) {
        // ExoPlayer View Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // TV Player Controls Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BackgroundDark.copy(alpha = 0.88f),
                                Color.Transparent,
                                BackgroundDark.copy(alpha = 0.94f)
                            )
                        )
                    )
            ) {
                // Top Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val subText = if (movie.isSeries) {
                            "Сезон $currentSeason • Серия $currentEpisode"
                        } else {
                            "${movie.releaseYear} • ${movie.duration}"
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = accent
                        )
                    }

                    // Live Badge
                    Box(
                        modifier = Modifier
                            .background(RedPrimary, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "ВОСПРОИЗВЕДЕНИЕ" else "ПАУЗА",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                }

                // Middle Drawer Overlay (Audio Tracks or Episodes)
                if (activeDrawer == "audio" && movie.audioTracks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.88f))
                            .padding(horizontal = 48.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Выберите озвучку / перевод:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(movie.audioTracks) { track ->
                                val isSel = track.id == currentAudioId
                                Button(
                                    onClick = {
                                        activeDrawer = null
                                        switchStream(currentSeason, currentEpisode, track.id)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSel) accent else ChipBackground,
                                        focusedContainerColor = accent,
                                        contentColor = if (isSel) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = track.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                } else if (activeDrawer == "episodes" && movie.isSeries && movie.seasons.isNotEmpty()) {
                    val activeSeason = movie.seasons.firstOrNull { it.seasonNumber == currentSeason } ?: movie.seasons.first()
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.88f))
                            .padding(horizontal = 48.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Серии сезона $currentSeason:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(activeSeason.episodes) { ep ->
                                val isSel = ep.episodeNumber == currentEpisode
                                Button(
                                    onClick = {
                                        activeDrawer = null
                                        switchStream(currentSeason, ep.episodeNumber, currentAudioId)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSel) accent else ChipBackground,
                                        focusedContainerColor = accent,
                                        contentColor = if (isSel) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = ep.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Timeline Bar & Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                ) {
                    // Inline Series Episodes Strip
                    if (movie.isSeries) {
                        val activeSeason = movie.seasons.firstOrNull { it.seasonNumber == currentSeason } ?: movie.seasons.firstOrNull()
                        val episodeList = activeSeason?.episodes ?: emptyList()
                        if (episodeList.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text(
                                    text = "Серии сезона $currentSeason:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextGray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TvLazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(episodeList) { ep ->
                                        val isCurrentEp = ep.episodeNumber == currentEpisode
                                        val epReq = if (isCurrentEp) Modifier.focusRequester(episodesRowFocusRequester) else Modifier
                                        Button(
                                            onClick = {
                                                switchStream(currentSeason, ep.episodeNumber, currentAudioId)
                                            },
                                            colors = ButtonDefaults.colors(
                                                containerColor = if (isCurrentEp) accent else Color.White.copy(alpha = 0.12f),
                                                focusedContainerColor = if (isCurrentEp) Color.White else accent,
                                                contentColor = if (isCurrentEp) Color.Black else TextWhite,
                                                focusedContentColor = Color.Black
                                            ),
                                            border = ButtonDefaults.border(
                                                border = Border(BorderStroke(1.dp, if (isCurrentEp) accent else Color.Transparent)),
                                                focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                                            ),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(30.dp)
                                                .then(epReq)
                                                .focusProperties {
                                                    down = playPauseFocusRequester
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (isCurrentEp) {
                                                    AppIcon(
                                                        resId = R.drawable.ic_play_arrow,
                                                        tint = Color.Black,
                                                        size = 12.dp
                                                    )
                                                }
                                                Text(
                                                    text = if (ep.title.isNotBlank() && ep.title != "null") ep.title else "Серия ${ep.episodeNumber}",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isCurrentEp) FontWeight.Bold else FontWeight.Normal,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Progress Bar
                    val progressFraction = if (duration > 0) {
                        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(6.dp)
                                .background(accent, shape = RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Duration Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // TV Remote Interactive Buttons Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Play / Pause Button
                        Button(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = accent,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(playPauseFocusRequester)
                                .focusProperties {
                                    right = rewindFocusRequester
                                    up = episodesRowFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppIcon(
                                    resId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                                    tint = TextWhite,
                                    size = 14.dp
                                )
                                Text(
                                    text = if (isPlaying) "Пауза" else "Старт",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // 2. Rewind -10s
                        Button(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = accent,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(rewindFocusRequester)
                                .focusProperties {
                                    left = playPauseFocusRequester
                                    right = forwardFocusRequester
                                    up = episodesRowFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_replay_10,
                                    tint = TextWhite,
                                    size = 14.dp
                                )
                                Text("10с", fontSize = 12.sp, lineHeight = 14.sp)
                            }
                        }

                        // 3. Forward +10s
                        val afterForwardFocus = if (movie.audioTracks.isNotEmpty()) audioFocusRequester else (if (movie.isSeries) episodesDrawerFocusRequester else extPlayerFocusRequester)
                        Button(
                            onClick = {
                                val maxPos = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxPos)
                                exoPlayer.seekTo(newPos)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = accent,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(forwardFocusRequester)
                                .focusProperties {
                                    left = rewindFocusRequester
                                    right = afterForwardFocus
                                    up = episodesRowFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_forward_10,
                                    tint = TextWhite,
                                    size = 14.dp
                                )
                                Text("10с", fontSize = 12.sp, lineHeight = 14.sp)
                            }
                        }

                        // 4. Audio Tracks Selector Button
                        if (movie.audioTracks.isNotEmpty()) {
                            val curName = movie.audioTracks.firstOrNull { it.id == currentAudioId }?.name ?: "Озвучка"
                            val afterAudioFocus = if (movie.isSeries) {
                                if (currentEpisode > 1) prevEpisodeFocusRequester else episodesDrawerFocusRequester
                            } else extPlayerFocusRequester

                            Button(
                                onClick = {
                                    activeDrawer = if (activeDrawer == "audio") null else "audio"
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (activeDrawer == "audio") accent else Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = accent,
                                    contentColor = if (activeDrawer == "audio") Color.Black else TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .focusRequester(audioFocusRequester)
                                    .focusProperties {
                                        left = forwardFocusRequester
                                        right = afterAudioFocus
                                        up = episodesRowFocusRequester
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AppIcon(
                                        resId = R.drawable.ic_volume_up,
                                        tint = TextWhite,
                                        size = 14.dp
                                    )
                                    Text(curName, fontSize = 12.sp, maxLines = 1, lineHeight = 14.sp)
                                }
                            }
                        }

                        // 5. Series Next / Prev / List Buttons
                        if (movie.isSeries) {
                            val beforeSeriesFocus = if (movie.audioTracks.isNotEmpty()) audioFocusRequester else forwardFocusRequester

                            if (currentEpisode > 1) {
                                Button(
                                    onClick = { switchStream(currentSeason, currentEpisode - 1, currentAudioId) },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.12f),
                                        focusedContainerColor = accent,
                                        contentColor = TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .focusRequester(prevEpisodeFocusRequester)
                                        .focusProperties {
                                            left = beforeSeriesFocus
                                            right = episodesDrawerFocusRequester
                                            up = episodesRowFocusRequester
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        AppIcon(
                                            resId = R.drawable.ic_skip_previous,
                                            tint = TextWhite,
                                            size = 14.dp
                                        )
                                        Text("Пред. серия", fontSize = 12.sp, lineHeight = 14.sp)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    activeDrawer = if (activeDrawer == "episodes") null else "episodes"
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (activeDrawer == "episodes") accent else Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = accent,
                                    contentColor = if (activeDrawer == "episodes") Color.Black else TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .focusRequester(episodesDrawerFocusRequester)
                                    .focusProperties {
                                        left = if (currentEpisode > 1) prevEpisodeFocusRequester else beforeSeriesFocus
                                        right = nextEpisodeFocusRequester
                                        up = episodesRowFocusRequester
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AppIcon(
                                        resId = R.drawable.ic_video_library,
                                        tint = TextWhite,
                                        size = 14.dp
                                    )
                                    Text("Все серии", fontSize = 12.sp, lineHeight = 14.sp)
                                }
                            }

                            Button(
                                onClick = { switchStream(currentSeason, currentEpisode + 1, currentAudioId) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = accent,
                                    contentColor = TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .focusRequester(nextEpisodeFocusRequester)
                                    .focusProperties {
                                        left = episodesDrawerFocusRequester
                                        right = extPlayerFocusRequester
                                        up = episodesRowFocusRequester
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("След. серия", fontSize = 12.sp, lineHeight = 14.sp)
                                    AppIcon(
                                        resId = R.drawable.ic_skip_next,
                                        tint = TextWhite,
                                        size = 14.dp
                                    )
                                }
                            }
                        }

                        // 6. External Player Button
                        val beforeExtFocus = if (movie.isSeries) nextEpisodeFocusRequester else (if (movie.audioTracks.isNotEmpty()) audioFocusRequester else forwardFocusRequester)
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        val uri = Uri.parse(currentStreamUrl)
                                        val mime = if (currentStreamUrl.contains(".m3u8")) "application/x-mpegURL" else "video/*"
                                        setDataAndType(uri, mime)
                                        putExtra("title", movie.title)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Выберите видеоплеер"))
                                } catch (e: Exception) {
                                    try {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentStreamUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(webIntent)
                                    } catch (_: Exception) {}
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
                                focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(extPlayerFocusRequester)
                                .focusProperties {
                                    left = beforeExtFocus
                                    up = episodesRowFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_open_in_new,
                                    tint = TextWhite,
                                    size = 14.dp
                                )
                                Text("Внешний плеер", fontSize = 12.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
