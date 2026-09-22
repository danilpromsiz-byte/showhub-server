@file:kotlin.OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.tvmediaapp.ui.screens.player

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.tvmediaapp.Screen
import com.example.tvmediaapp.data.history.SessionManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.ui.components.NeonSpinner
import kotlinx.coroutines.async
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.common.C
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.theme.ChipBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun isDirectVideoStream(url: String): Boolean {
    val clean = url.lowercase().trim()
    if (clean.contains("embed") || clean.contains("allarknow") || clean.contains("bayas") || clean.contains("bazon.cc") || 
        clean.contains("delivembd") || clean.contains("kinobase") || clean.contains("iframe") || 
        clean.contains("kodikplayer") || clean.contains("kodik.info") ||
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
    audioId: String = "",
    onPositionChange: ((Long) -> Unit)? = null,
    onBackPress: () -> Unit
) {
    val isEmbedWeb = !isDirectVideoStream(movie.videoUrl) &&
            movie.videoUrl.isNotBlank() &&
            movie.videoUrl.startsWith("http")

    if (isEmbedWeb) {
        EmbedWebViewPlayerScreen(
            movie = movie,
            onBackPress = onBackPress
        )
    } else {
        NativeExoPlayerScreen(
            movie = movie,
            startPositionMs = startPositionMs,
            season = season,
            episode = episode,
            audioId = audioId,
            onPositionChange = onPositionChange,
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
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val accent = LocalAccentColor.current
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(movie.videoUrl) {
        if (movie.videoUrl.isBlank() || !movie.videoUrl.startsWith("http")) {
            hasError = true
            errorMessage = "Поток воспроизведения недоступен"
        }
    }

    LaunchedEffect(hasError) {
        if (hasError) {
            delay(150)
            try { backFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    BackHandler {
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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                AppIcon(
                    resId = R.drawable.ic_movie,
                    tint = accent,
                    size = 56.dp
                )
                Text(
                    text = errorMessage.ifEmpty { "Не удалось воспроизвести видео" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Попробуйте выбрать другую озвучку или источник в карточке фильма",
                    fontSize = 14.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onBackPress,
                    colors = ButtonDefaults.colors(
                        containerColor = accent,
                        focusedContainerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.focusRequester(backFocusRequester)
                ) {
                    Text(text = "Вернуться назад", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        isFocusable = true
                        isFocusableInTouchMode = true
                        try {
                            val cm = android.webkit.CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                        } catch (_: Exception) {}

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                handler?.proceed()
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    // Don't call super for main frame — it renders the default
                                    // Android error page (upside-down robot). We show our own UI.
                                    val errCode = error?.errorCode ?: 0
                                    val errDesc = error?.description?.toString() ?: "unknown"
                                    val errUrl = request.url?.toString() ?: "?"
                                    android.util.Log.e("EmbedPlayer", "Main frame error: code=$errCode desc=$errDesc url=$errUrl")
                                    if (errCode != -1 && errCode != ERROR_CONNECT && errCode != ERROR_TIMEOUT) {
                                        hasError = true
                                        errorMessage = "Не удалось загрузить плеер источника"
                                    }
                                    // Load blank to clear the error page
                                    view?.loadUrl("about:blank")
                                } else {
                                    // Sub-resource errors — let default handling proceed
                                    super.onReceivedError(view, request, error)
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                val statusCode = errorResponse?.statusCode ?: 0
                                val errUrl = request?.url?.toString() ?: "?"
                                android.util.Log.e("EmbedPlayer", "HTTP error: status=$statusCode url=$errUrl")
                                if (request?.isForMainFrame == true && statusCode >= 400) {
                                    hasError = true
                                    errorMessage = "Ошибка источника (HTTP $statusCode)"
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                // Allow all navigation within the WebView (iframes, redirects)
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url == "about:blank") return
                                android.util.Log.d("EmbedPlayer", "Page finished: $url")
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        document.body.style.backgroundColor = '#000';
                                        var iframe = document.querySelector('iframe');
                                        if (iframe && iframe.contentDocument) {
                                            try {
                                                var v = iframe.contentDocument.querySelector('video');
                                                if (v) { v.focus(); v.play(); }
                                            } catch(e) {}
                                        }
                                        var v = document.querySelector('video');
                                        if (v) { v.focus(); v.play(); }
                                    })();
                                    """.trimIndent(), null
                                )
                            }
                        }

                        val targetUrl = movie.videoUrl.trim()
                        android.util.Log.d("EmbedPlayer", "Loading embed URL: $targetUrl")

                        // All embed players are loaded inside an iframe wrapper.
                        // Many players (Kodik, VideoCDN, Bazon, Collaps) check if they're
                        // running inside an iframe and refuse to play if loaded directly.
                        val baseUrl = when {
                            targetUrl.contains("kodik") -> "https://kodikplayer.com/"
                            targetUrl.contains("allarknow") || targetUrl.contains("bayas") || targetUrl.contains("videocdn") -> "https://api.apbugall.org/"
                            targetUrl.contains("bazon") -> "https://bazon.cc/"
                            targetUrl.contains("collaps") -> "https://api.delivembd.ws/"
                            else -> "https://showhub.tv/"
                        }
                        val iframeHtml = """
                            <!DOCTYPE html>
                            <html><head>
                            <meta name="viewport" content="width=device-width,initial-scale=1">
                            <style>*{margin:0;padding:0;overflow:hidden;background:#000}
                            iframe{position:fixed;top:0;left:0;width:100%;height:100%;border:0}</style>
                            </head><body>
                            <iframe src="$targetUrl" width="100%" height="100%"
                                frameborder="0" allowfullscreen
                                allow="autoplay *; fullscreen *; encrypted-media *">
                            </iframe></body></html>
                        """.trimIndent()
                        loadDataWithBaseURL(
                            baseUrl,
                            iframeHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webViewRef = this
                        requestFocus()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun formatTimeRu(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "$h ч. $m м. ${"%02d".format(s)} с."
    } else {
        "$m м. ${"%02d".format(s)} с."
    }
}

private fun formatDeltaSecondsRu(deltaSec: Int): String {
    val sign = if (deltaSec > 0) "+" else if (deltaSec < 0) "-" else ""
    val absSec = kotlin.math.abs(deltaSec)
    return if (absSec >= 60) {
        val m = absSec / 60
        val s = absSec % 60
        "$sign$m м. ${"%02d".format(s)} с."
    } else {
        "$sign${absSec}с"
    }
}

@Composable
private fun NativeExoPlayerScreen(
    movie: Movie,
    startPositionMs: Long = 0L,
    season: Int = 1,
    episode: Int = 1,
    audioId: String = "",
    onPositionChange: ((Long) -> Unit)? = null,
    onBackPress: () -> Unit
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val historyManager = remember { WatchHistoryManager(context) }
    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentSeason by remember { mutableIntStateOf(season) }
    var currentEpisode by remember { mutableIntStateOf(episode) }
    var currentStreamUrl by remember { mutableStateOf(movie.videoUrl) }
    var currentAudioId by remember { mutableStateOf(audioId.ifEmpty { movie.audioTracks.firstOrNull()?.id ?: "" }) }
    var selectedQuality by remember { mutableStateOf(prefs.getString("pref_quality", "1080p") ?: "1080p") }
    var selectedSource by remember { mutableStateOf("HDrezka") }
    var currentMovieState by remember { mutableStateOf(movie) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(startPositionMs) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var activeDrawer by remember { mutableStateOf<String?>(null) } // "audio", "episodes", "quality", "source", null
    var isLoadingStream by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isTimelineFocused by remember { mutableStateOf(false) }

    // Progressive seek acceleration states
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    var seekSpeedLevel by remember { mutableIntStateOf(0) }
    val seekSteps = remember { listOf(10000L, 15000L, 30000L, 60000L, 120000L) }
    var seekDeltaBadge by remember { mutableStateOf<String?>(null) }
    var timelineSeekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingTimelineSeekPos by remember { mutableStateOf<Long?>(null) }
    var lastUserInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(seekDeltaBadge) {
        if (seekDeltaBadge != null) {
            delay(1200)
            seekDeltaBadge = null
        }
    }

    var playbackActionBadge by remember { mutableStateOf<String?>(null) } // "play" or "pause"
    var displayedActionBadge by remember { mutableStateOf("play") }
    LaunchedEffect(playbackActionBadge) {
        if (playbackActionBadge != null) {
            displayedActionBadge = playbackActionBadge!!
            delay(900)
            playbackActionBadge = null
        }
    }

    var translatorNoticeBadge by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(translatorNoticeBadge) {
        if (translatorNoticeBadge != null) {
            delay(4000)
            translatorNoticeBadge = null
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val sourceFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val prevEpisodeFocusRequester = remember { FocusRequester() }
    val episodesDrawerFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val extPlayerFocusRequester = remember { FocusRequester() }
    val episodesRowFocusRequester = remember { FocusRequester() }

    // Fallback: Fetch detailed seasons, episodes and audio tracks in player if missing
    LaunchedEffect(movie.id) {
        if ((currentMovieState.isSeries && currentMovieState.seasons.isEmpty()) || currentMovieState.audioTracks.isEmpty()) {
            try {
                val detailed = ShowHubApiClient.fetchMediaDetails(movie)
                if (detailed.seasons.isNotEmpty() || detailed.audioTracks.isNotEmpty()) {
                    currentMovieState = currentMovieState.copy(
                        seasons = if (detailed.seasons.isNotEmpty()) detailed.seasons else currentMovieState.seasons,
                        audioTracks = if (detailed.audioTracks.isNotEmpty()) detailed.audioTracks else currentMovieState.audioTracks
                    )
                    if (currentAudioId.isEmpty() && detailed.audioTracks.isNotEmpty()) {
                        currentAudioId = detailed.audioTracks.first().id
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Initialize Media3 ExoPlayer with headers, memory management, and resume support
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://hdrezka.ag/"))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                25000, // minBufferMs (25s)
                60000, // maxBufferMs (60s)
                2500,  // bufferForPlaybackMs (2.5s for smooth jitter-free start)
                4000   // bufferForPlaybackAfterRebufferMs (4s)
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15000, true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build().apply {
                try {
                    setWakeMode(C.WAKE_MODE_NETWORK)
                } catch (_: Throwable) {}
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                if (currentStreamUrl.isNotBlank() && isDirectVideoStream(currentStreamUrl)) {
                    try {
                        setMediaItem(MediaItem.fromUri(currentStreamUrl))
                        if (startPositionMs > 1000L) {
                            seekTo(startPositionMs)
                        }
                        prepare()
                        playWhenReady = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = (state == androidx.media3.common.Player.STATE_BUFFERING)
                        if (state == androidx.media3.common.Player.STATE_ENDED) {
                            try {
                                val dur = duration.coerceAtLeast(1L)
                                historyManager.saveProgress(
                                    movie = currentMovieState,
                                    positionMs = dur,
                                    durationMs = dur,
                                    season = currentSeason,
                                    episode = currentEpisode,
                                    audioId = currentAudioId
                                )
                                historyManager.markEpisodeWatched(
                                    currentMovieState.id,
                                    currentSeason,
                                    currentEpisode,
                                    currentMovieState.title
                                )
                            } catch (_: Exception) {}
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        error.printStackTrace()
                        isBuffering = false
                    }
                })
            }
    }

    val mediaSession = remember(exoPlayer) {
        try {
            androidx.media3.session.MediaSession.Builder(context, exoPlayer)
                .setId("ShowHubMediaSession")
                .build()
        } catch (_: Throwable) {
            null
        }
    }

    DisposableEffect(mediaSession) {
        onDispose {
            try {
                mediaSession?.release()
            } catch (_: Throwable) {}
        }
    }

    fun persistCurrentPlaybackProgress() {
        try {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 1000L) {
                historyManager.saveProgress(
                    movie = currentMovieState,
                    positionMs = pos,
                    durationMs = if (dur > 0L) dur else 0L,
                    season = currentSeason,
                    episode = currentEpisode,
                    audioId = currentAudioId
                )
                onPositionChange?.invoke(pos)
                SessionManager.saveSession(
                    context = context,
                    movie = currentMovieState,
                    screen = Screen.PLAYER,
                    videoUrl = currentStreamUrl,
                    positionMs = pos,
                    season = currentSeason,
                    episode = currentEpisode,
                    audioId = currentAudioId
                )
            }
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            playbackActionBadge = "pause"
            isControlsVisible = true
            try { timelineFocusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            exoPlayer.play()
            playbackActionBadge = "play"
        }
    }

    fun matchQuality(streamQuality: String, targetQuality: String): Boolean {
        val s = streamQuality.lowercase()
        val t = targetQuality.lowercase()
        return when {
            t.contains("ultra") || t.contains("4k") || t.contains("2160") ->
                s.contains("ultra") || s.contains("4k") || s.contains("2160")
            t.contains("1080") ->
                s.contains("1080") && !s.contains("ultra") && !s.contains("vip")
            t.contains("720") ->
                s.contains("720")
            t.contains("480") ->
                s.contains("480")
            t.contains("360") ->
                s.contains("360")
            else ->
                s.contains(t)
        }
    }

    fun extractSources(streams: List<com.example.tvmediaapp.data.models.StreamOption>): List<String> {
        val srcSet = linkedSetOf<String>()
        for (st in streams) {
            val s = st.source.trim()
            val cleanName = when {
                s.startsWith("HDrezka", ignoreCase = true) -> "HDrezka"
                s.contains("Torr", ignoreCase = true) -> "Торренты (TorrServe)"
                s.contains("Filmix", ignoreCase = true) -> "Filmix"
                s.contains("Kodik", ignoreCase = true) -> "Kodik"
                s.contains("VideoCDN", ignoreCase = true) -> "VideoCDN"
                s.contains("Collaps", ignoreCase = true) -> "Collaps"
                s.contains("Bazon", ignoreCase = true) -> "Bazon"
                s.isNotEmpty() -> s.replaceFirstChar { it.uppercase() }
                else -> "HDrezka"
            }
            srcSet.add(cleanName)
        }
        if (srcSet.isEmpty()) {
            srcSet.add("HDrezka")
        }
        return srcSet.toList()
    }

    fun extractQualities(streams: List<com.example.tvmediaapp.data.models.StreamOption>): List<String> {
        val qualSet = linkedSetOf<String>()
        val order = listOf("4K", "1080p Ultra", "1080p", "720p", "480p", "360p")
        for (target in order) {
            if (streams.any { matchQuality(it.quality, target) }) {
                qualSet.add(target)
            }
        }
        for (st in streams) {
            val q = st.quality.trim()
            if (q.isNotBlank() && !qualSet.any { it.equals(q, ignoreCase = true) }) {
                qualSet.add(q)
            }
        }
        return if (qualSet.isNotEmpty()) qualSet.toList() else listOf("1080p", "720p", "480p")
    }

    var availableSources by remember { mutableStateOf<List<String>>(listOf("HDrezka")) }
    var availableQualities by remember { mutableStateOf<List<String>>(listOf("1080p", "720p", "480p")) }

    fun switchStream(
        newSeason: Int = currentSeason,
        newEpisode: Int = currentEpisode,
        newAudioId: String = currentAudioId,
        newQuality: String = selectedQuality,
        newSource: String = selectedSource
    ) {
        val isSameEpisode = (newSeason == currentSeason && newEpisode == currentEpisode)
        if (!isSameEpisode) {
            persistCurrentPlaybackProgress()
        }
        currentSeason = newSeason
        currentEpisode = newEpisode
        currentAudioId = newAudioId
        selectedQuality = newQuality
        selectedSource = newSource
        isLoadingStream = true
        coroutineScope.launch {
            try {
                val epToPlay = newEpisode
                if (currentMovieState.isSeries && newAudioId.isNotBlank()) {
                    try {
                        val realSeasons = ShowHubApiClient.fetchEpisodes(currentMovieState, newAudioId, source = newSource)
                        if (realSeasons.isNotEmpty()) {
                            val epCount = realSeasons.sumOf { it.episodes.size }
                            val sMap = realSeasons.associate { it.seasonNumber to it.episodes.size }
                            val updatedTracks = currentMovieState.audioTracks.map {
                                if (it.id == newAudioId) it.copy(episodesCount = epCount, seasonsEpisodes = sMap) else it
                            }
                            currentMovieState = currentMovieState.copy(seasons = realSeasons, audioTracks = updatedTracks)
                        }
                    } catch (_: Exception) {}
                }
                val savedPos = exoPlayer.currentPosition
                val isNonRezkaTrack = newAudioId.startsWith("kodik_") || newAudioId.startsWith("filmix_") || (!newSource.equals("HDrezka", ignoreCase = true) && !newSource.equals("Все", ignoreCase = true))
                val nativeDeferred = async {
                    if (!isNonRezkaTrack && (newSource.equals("HDrezka", ignoreCase = true) || newSource.startsWith("HD", ignoreCase = true) || newSource.equals("Все", ignoreCase = true))) {
                        val rezkaMediaUrl = if (currentMovieState.id.startsWith("http") || currentMovieState.id.contains("hdrezka") || currentMovieState.id.startsWith("rezka:")) {
                            currentMovieState.id
                        } else null
                        RezkaNativeResolver.resolveStreams(
                            title = currentMovieState.title,
                            year = currentMovieState.releaseYear,
                            isSeries = currentMovieState.isSeries,
                            season = newSeason,
                            episode = epToPlay,
                            translatorId = newAudioId.ifEmpty { null },
                            mediaUrl = rezkaMediaUrl,
                            originalTitle = currentMovieState.originalTitle
                        )
                    } else {
                        emptyList()
                    }
                }
                val serverDeferred = async {
                    ShowHubApiClient.fetchStreams(
                        movie = currentMovieState,
                        season = if (currentMovieState.isSeries) newSeason else null,
                        episode = if (currentMovieState.isSeries) epToPlay else null,
                        audioId = newAudioId.ifEmpty { null },
                        source = if (newSource.equals("HDrezka", ignoreCase = true)) null else newSource
                    )
                }
                val nativeStreams = nativeDeferred.await()
                val serverStreams = serverDeferred.await()

                val isNativeFallback = nativeStreams.isNotEmpty() && nativeStreams.all { it.source.contains("fallback", ignoreCase = true) }
                val prioritizeServer = currentMovieState.source == "filmix" || isNonRezkaTrack || isNativeFallback || !newSource.equals("HDrezka", ignoreCase = true)
                val allResolved = if (prioritizeServer && serverStreams.isNotEmpty()) {
                    (serverStreams + nativeStreams).distinctBy { it.url }
                } else {
                    (nativeStreams + serverStreams).distinctBy { it.url }
                }

                val discovered = extractSources(allResolved)
                if (discovered.isNotEmpty()) {
                    availableSources = discovered
                }

                if (newAudioId.isNotBlank() && allResolved.all { it.source.contains("fallback", ignoreCase = true) }) {
                    val trackName = currentMovieState.audioTracks.firstOrNull { it.id == newAudioId }?.name ?: "выбранная озвучка"
                    translatorNoticeBadge = "Озвучка «$trackName» недоступна для этого сезона (включен дубляж)"
                }

                // Filter by source if specific source chosen
                val sourceStreams = if (newSource.isNotBlank() && !newSource.equals("Все", ignoreCase = true)) {
                    val matched = allResolved.filter { s ->
                        when {
                            newSource.contains("Torr", ignoreCase = true) ->
                                s.url.contains(":8090") || s.quality.contains("P2P", ignoreCase = true) || s.source.contains("torrent", ignoreCase = true)
                            newSource.equals("HDrezka", ignoreCase = true) ->
                                s.source.equals("HDrezka", ignoreCase = true) || s.url.contains("voidboost") || s.url.contains("rezka")
                            else ->
                                s.source.contains(newSource, ignoreCase = true) || s.quality.contains(newSource, ignoreCase = true) || s.url.contains(newSource.lowercase())
                        }
                    }
                    if (matched.isNotEmpty()) matched else allResolved
                } else {
                    allResolved
                }

                // Check TorrServe custom host
                val customTorrHost = prefs.getString("pref_torrserve_host", "http://127.0.0.1:8090") ?: "http://127.0.0.1:8090"
                val adjustedStreams = sourceStreams.map { st ->
                    if (st.url.contains("127.0.0.1:8090") && customTorrHost != "http://127.0.0.1:8090") {
                        st.copy(url = st.url.replace("http://127.0.0.1:8090", customTorrHost))
                    } else st
                }

                val targetStream = adjustedStreams.firstOrNull { matchQuality(it.quality, newQuality) && isDirectVideoStream(it.url) }
                    ?: adjustedStreams.firstOrNull { isDirectVideoStream(it.url) }
                    ?: adjustedStreams.firstOrNull()

                if (targetStream != null) {
                    currentStreamUrl = targetStream.url
                    if (isDirectVideoStream(targetStream.url)) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(targetStream.url))
                        val curTrackObj = currentMovieState.audioTracks.firstOrNull { it.id == newAudioId }
                        if (curTrackObj != null) {
                            val tName = curTrackObj.name.lowercase()
                            val langCode = when {
                                tName.contains("англ") || tName.contains("orig") || tName.contains("eng") -> "eng"
                                tName.contains("укр") || tName.contains("ukr") -> "ukr"
                                tName.contains("рус") || tName.contains("дубл") || tName.contains("rus") -> "rus"
                                else -> null
                            }
                            if (langCode != null) {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setPreferredAudioLanguage(langCode)
                                    .build()
                            }
                        }
                        if (isSameEpisode && savedPos > 1000L) {
                            exoPlayer.seekTo(savedPos)
                        } else {
                            exoPlayer.seekTo(0L)
                            currentPosition = 0L
                            bufferedPosition = 0L
                        }
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else {
                        // Embed stream (e.g. Bazon) - pause and clear ExoPlayer so it doesn't crash on HTML
                        try {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        } catch (_: Exception) {}
                    }
                } else {
                    val trackName = currentMovieState.audioTracks.firstOrNull { it.id == newAudioId }?.name ?: "выбранная озвучка"
                    translatorNoticeBadge = "Серия $epToPlay недоступна в «$trackName»"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingStream = false
            }
        }
    }

    // Proactively discover all actual sources and qualities available for this media/episode
    LaunchedEffect(movie.id, currentSeason, currentEpisode) {
        try {
            val serverDeferred = async(Dispatchers.IO) {
                ShowHubApiClient.fetchStreams(
                    movie = currentMovieState,
                    season = if (currentMovieState.isSeries) currentSeason else null,
                    episode = if (currentMovieState.isSeries) currentEpisode else null,
                    audioId = currentAudioId.ifEmpty { null },
                    source = null
                )
            }
            val rezkaDeferred = async(Dispatchers.IO) {
                val rezkaMediaUrl = if (currentMovieState.id.startsWith("http") || currentMovieState.id.contains("hdrezka") || currentMovieState.id.startsWith("rezka:")) {
                    currentMovieState.id
                } else null
                RezkaNativeResolver.resolveStreams(
                    title = currentMovieState.title,
                    year = currentMovieState.releaseYear,
                    isSeries = currentMovieState.isSeries,
                    season = currentSeason,
                    episode = currentEpisode,
                    translatorId = currentAudioId.ifEmpty { null },
                    mediaUrl = rezkaMediaUrl,
                    originalTitle = currentMovieState.originalTitle
                )
            }
            val serverStreams = serverDeferred.await()
            val rezkaStreams = rezkaDeferred.await()
            val combined = (serverStreams + rezkaStreams)
            val extractedSrc = extractSources(combined)
            if (extractedSrc.isNotEmpty()) {
                availableSources = extractedSrc
            }
            val extractedQual = extractQualities(combined)
            if (extractedQual.isNotEmpty()) {
                availableQualities = extractedQual
            }
            if (currentStreamUrl.isBlank() || !isDirectVideoStream(currentStreamUrl)) {
                switchStream(currentSeason, currentEpisode, currentAudioId, selectedQuality, selectedSource)
            }
        } catch (_: Exception) {}
    }

    // Monitor playback progress & periodically save to WatchHistoryManager (throttled to 5s to prevent disk I/O stalls)
    LaunchedEffect(exoPlayer, isControlsVisible) {
        var lastSavedMs = 0L
        while (true) {
            if (!isTimelineFocused && pendingTimelineSeekPos == null) {
                currentPosition = exoPlayer.currentPosition
            }
            duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            bufferedPosition = exoPlayer.bufferedPosition
            isPlaying = exoPlayer.isPlaying

            val now = System.currentTimeMillis()
            if (currentPosition > 3000L && duration > 0L && (now - lastSavedMs >= 5000L)) {
                lastSavedMs = now
                persistCurrentPlaybackProgress()
            }
            delay(if (isControlsVisible) 1000L else 2000L)
        }
    }

    // Auto-hide TV controls after 6 seconds of user inactivity when not in drawer
    LaunchedEffect(isControlsVisible, activeDrawer, lastUserInteractionTime) {
        if (isControlsVisible && activeDrawer == null) {
            delay(6000)
            if (System.currentTimeMillis() - lastUserInteractionTime >= 5800L) {
                isControlsVisible = false
            }
        }
    }

    // Auto-focus TIMELINE when controls become visible
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible && activeDrawer == null) {
            delay(50)
            try {
                timelineFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Handle Hardware Back button: close drawer -> close controls -> double click to exit
    BackHandler {
        if (activeDrawer != null) {
            activeDrawer = null
        } else if (isControlsVisible) {
            isControlsVisible = false
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                persistCurrentPlaybackProgress()
                onBackPress()
            } else {
                lastBackPressTime = now
                android.widget.Toast.makeText(context, "Нажмите «Назад» еще раз для выхода", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Lifecycle-aware cleanup: halt audio immediately when App goes to background (Home button)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    persistCurrentPlaybackProgress()
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    persistCurrentPlaybackProgress()
                    exoPlayer.stop()
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistCurrentPlaybackProgress()
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    var quickSeekBadgeText by remember { mutableStateOf<String?>(null) }
    var quickSeekBadgeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var accumulatedSeekSeconds by remember { mutableIntStateOf(0) }

    // Request focus for D-Pad events on launch and whenever controls hide
    LaunchedEffect(Unit) {
        try {
            rootFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    LaunchedEffect(isControlsVisible) {
        if (!isControlsVisible) {
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
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
                        if (isControlsVisible) {
                            isControlsVisible = false
                            return@onKeyEvent true
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastBackPressTime < 2000L) {
                            persistCurrentPlaybackProgress()
                            onBackPress()
                        } else {
                            lastBackPressTime = now
                            android.widget.Toast.makeText(context, "Нажмите «Назад» еще раз для выхода", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@onKeyEvent true
                    }
                }
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    lastUserInteractionTime = System.currentTimeMillis()

                    // Universal Bluetooth headset and media button handling (active in all UI states)
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            togglePlayPause()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            exoPlayer.play()
                            playbackActionBadge = "play"
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            exoPlayer.pause()
                            playbackActionBadge = "pause"
                            return@onKeyEvent true
                        }
                    }

                    if (!isControlsVisible) {
                        when (nativeEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (accumulatedSeekSeconds > 0) {
                                    accumulatedSeekSeconds = -10
                                } else {
                                    accumulatedSeekSeconds -= 10
                                }
                                val cur = exoPlayer.currentPosition
                                val target = (cur - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                quickSeekBadgeText = formatDeltaSecondsRu(accumulatedSeekSeconds)
                                quickSeekBadgeJob?.cancel()
                                quickSeekBadgeJob = coroutineScope.launch {
                                    delay(1500)
                                    quickSeekBadgeText = null
                                    accumulatedSeekSeconds = 0
                                }
                                return@onKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (accumulatedSeekSeconds < 0) {
                                    accumulatedSeekSeconds = 10
                                } else {
                                    accumulatedSeekSeconds += 10
                                }
                                val cur = exoPlayer.currentPosition
                                val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = (cur + 10000L).coerceAtMost(dur)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                                quickSeekBadgeText = formatDeltaSecondsRu(accumulatedSeekSeconds)
                                quickSeekBadgeJob?.cancel()
                                quickSeekBadgeJob = coroutineScope.launch {
                                    delay(1500)
                                    quickSeekBadgeText = null
                                    accumulatedSeekSeconds = 0
                                }
                                return@onKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (!exoPlayer.isPlaying) {
                                    exoPlayer.play()
                                    playbackActionBadge = "play"
                                    coroutineScope.launch {
                                        delay(1500)
                                        playbackActionBadge = null
                                    }
                                } else {
                                    togglePlayPause()
                                }
                                return@onKeyEvent true
                            }
                            else -> {
                                isControlsVisible = true
                                try { timelineFocusRequester.requestFocus() } catch (_: Exception) {}
                                return@onKeyEvent true
                            }
                        }
                    }
                }
                false
            }
    ) {
        // Video Playback Surface: ExoPlayer for direct streams, WebView for embed players (e.g. Bazon)
        if (isDirectVideoStream(currentStreamUrl)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isClickable = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (currentStreamUrl.isNotBlank() && currentStreamUrl.startsWith("http")) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        document.body.style.background = '#000';
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
                              <iframe src="$currentStreamUrl" allow="autoplay; fullscreen" allowfullscreen></iframe>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://showhub-server.onrender.com", html, "text/html", "UTF-8", null)
                    }
                },
                update = { wv ->
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
                          <iframe src="$currentStreamUrl" allow="autoplay; fullscreen" allowfullscreen></iframe>
                        </body>
                        </html>
                    """.trimIndent()
                    wv.loadDataWithBaseURL("https://showhub-server.onrender.com", html, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Quick Seek Delta Badge Overlay (when controls are hidden)
        if (quickSeekBadgeText != null && !isControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(
                            resId = if (quickSeekBadgeText!!.startsWith("-")) com.example.tvmediaapp.R.drawable.ic_replay_10 else com.example.tvmediaapp.R.drawable.ic_forward_10,
                            tint = LocalAccentColor.current,
                            size = 18.dp
                        )
                        androidx.tv.material3.Text(
                            text = quickSeekBadgeText!!,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Loading or Buffering Spinner Overlay
        if (isLoadingStream || isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                NeonSpinner(
                    size = 52.dp,
                    strokeWidth = 3.5.dp,
                    message = if (isLoadingStream) "Загрузка потока серии..." else "Буферизация..."
                )
            }
        }

        // Center Play / Pause Indicator Badge (clean, without circle)
        AnimatedVisibility(
            visible = playbackActionBadge != null,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 1.15f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    resId = if (displayedActionBadge == "play") R.drawable.ic_play_arrow else R.drawable.ic_pause,
                    tint = Color.White.copy(alpha = 0.95f),
                    size = 64.dp
                )
            }
        }

        // Top Translator / Voiceover Notice Toast
        AnimatedVisibility(
            visible = translatorNoticeBadge != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1C1C1E).copy(alpha = 0.95f))
                    .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppIcon(
                        resId = R.drawable.ic_volume_up,
                        tint = Color(0xFFFFB300),
                        size = 20.dp
                    )
                    Text(
                        text = translatorNoticeBadge ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

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
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentMovieState.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        val subText = if (currentMovieState.isSeries) {
                            "Сезон $currentSeason • Серия $currentEpisode"
                        } else {
                            "${currentMovieState.releaseYear} • ${currentMovieState.duration}"
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
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "ВОСПРОИЗВЕДЕНИЕ" else "ПАУЗА",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                }

                // Middle Drawers Overlays (Quality, Source, Audio Tracks, Episodes)
                if (activeDrawer == "quality") {
                    val qualities = if (availableQualities.isNotEmpty()) availableQualities else listOf("1080p", "720p", "480p")
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.90f))
                            .padding(horizontal = 48.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Выберите качество видео:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(qualities) { qual ->
                                val isSel = qual.contains(selectedQuality, ignoreCase = true)
                                val isVip = qual.contains("Ultra", ignoreCase = true) || qual.contains("4K", ignoreCase = true)
                                Button(
                                    onClick = {
                                        activeDrawer = null
                                        selectedQuality = qual
                                        switchStream(currentSeason, currentEpisode, currentAudioId, qual, selectedSource)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSel) accent.copy(alpha = 0.85f) else ChipBackground,
                                        focusedContainerColor = if (isVip) Color(0xFFFFD700) else Color.White,
                                        contentColor = if (isSel) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isVip) {
                                            Text("★ VIP", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSel) Color.Black else Color(0xFFFFD700))
                                        }
                                        Text(
                                            text = qual,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (activeDrawer == "source") {
                    val sourcesToShow = if (availableSources.isNotEmpty()) availableSources else listOf(selectedSource)
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.90f))
                            .padding(horizontal = 48.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Выберите источник потока:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sourcesToShow) { src ->
                                val isSel = src.equals(selectedSource, ignoreCase = true)
                                Button(
                                    onClick = {
                                        activeDrawer = null
                                        selectedSource = src
                                        switchStream(currentSeason, currentEpisode, currentAudioId, selectedQuality, src)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSel) accent.copy(alpha = 0.85f) else ChipBackground,
                                        focusedContainerColor = Color.White,
                                        contentColor = if (isSel) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = src,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (activeDrawer == "audio" && currentMovieState.audioTracks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.90f))
                            .padding(horizontal = 48.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Выберите озвучку / перевод:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val availableTracks = remember(currentMovieState.audioTracks, currentSeason, currentEpisode, currentMovieState.isSeries) {
                            if (currentMovieState.isSeries) {
                                val filtered = currentMovieState.audioTracks.filter { track ->
                                    if (track.seasonsEpisodes.isEmpty()) {
                                        if (currentMovieState.seasons.size > 1 && currentSeason > 1) {
                                            false
                                        } else {
                                            track.episodesCount == 0 || track.episodesCount >= currentEpisode
                                        }
                                    } else {
                                        val epCount = track.seasonsEpisodes[currentSeason] ?: 0
                                        epCount >= currentEpisode
                                    }
                                }
                                if (filtered.isNotEmpty()) filtered else currentMovieState.audioTracks
                            } else {
                                currentMovieState.audioTracks
                            }
                        }
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableTracks) { track ->
                                val isSel = track.id == currentAudioId
                                Button(
                                    onClick = {
                                        activeDrawer = null
                                        currentAudioId = track.id
                                        val targetSource = if (track.source.isNotBlank()) track.source else selectedSource
                                        switchStream(currentSeason, currentEpisode, track.id, selectedQuality, targetSource)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSel) accent.copy(alpha = 0.85f) else ChipBackground,
                                        focusedContainerColor = Color.White,
                                        contentColor = if (isSel) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = track.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (activeDrawer == "episodes") {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.92f))
                            .padding(horizontal = 48.dp, vertical = 14.dp)
                    ) {
                        if (currentMovieState.seasons.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NeonSpinner(size = 24.dp, strokeWidth = 2.dp)
                                Text(
                                    text = "Загрузка списка серий...",
                                    fontSize = 13.sp,
                                    color = TextWhite
                                )
                            }
                        } else {
                            val activeSeason = currentMovieState.seasons.firstOrNull { it.seasonNumber == currentSeason }
                                ?: currentMovieState.seasons.first()

                            // Season selector if more than 1 season
                            if (currentMovieState.seasons.size > 1) {
                                TvLazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    items(currentMovieState.seasons) { s ->
                                        val isCurrentS = s.seasonNumber == currentSeason
                                        Button(
                                            onClick = { currentSeason = s.seasonNumber },
                                            colors = ButtonDefaults.colors(
                                                containerColor = if (isCurrentS) accent.copy(alpha = 0.85f) else ChipBackground,
                                                focusedContainerColor = Color.White,
                                                contentColor = if (isCurrentS) Color.Black else TextWhite,
                                                focusedContentColor = Color.Black
                                            ),
                                            border = ButtonDefaults.border(
                                                border = Border.None,
                                                focusedBorder = Border.None
                                            ),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(
                                                text = "Сезон ${s.seasonNumber}",
                                                fontSize = 11.sp,
                                                fontWeight = if (isCurrentS) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            val curTrack = currentMovieState.audioTracks.firstOrNull { it.id == currentAudioId }
                            val maxEpForTrack = curTrack?.seasonsEpisodes?.get(currentSeason)
                            val validDrawerEpisodes = remember(activeSeason.episodes, currentAudioId, currentMovieState.audioTracks) {
                                if (maxEpForTrack != null && maxEpForTrack > 0) {
                                    activeSeason.episodes.filter { it.episodeNumber <= maxEpForTrack }
                                } else if (curTrack?.seasonsEpisodes?.isNotEmpty() == true) {
                                    emptyList()
                                } else {
                                    activeSeason.episodes
                                }
                            }

                            Text(
                                text = "Серии сезона $currentSeason (${validDrawerEpisodes.size}):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val activeEpFocusRequester = remember { FocusRequester() }
                            val activeIndex = remember(validDrawerEpisodes, currentEpisode) {
                                val idx = validDrawerEpisodes.indexOfFirst { it.episodeNumber == currentEpisode }
                                if (idx >= 0) idx else 0
                            }
                            val drawerEpisodesListState = rememberTvLazyListState()
                            LaunchedEffect(activeDrawer, currentEpisode, activeSeason, validDrawerEpisodes) {
                                if (activeDrawer == "episodes") {
                                    try {
                                        if (activeIndex >= 0) {
                                            drawerEpisodesListState.scrollToItem((activeIndex - 1).coerceAtLeast(0))
                                        }
                                        activeEpFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            }
                            TvLazyRow(
                                state = drawerEpisodesListState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(start = 8.dp, end = 80.dp),
                                pivotOffsets = PivotOffsets(parentFraction = 0.5f)
                            ) {
                                items(validDrawerEpisodes) { ep ->
                                    val isSel = ep.episodeNumber == currentEpisode
                                    val isWatched = historyManager.isEpisodeWatched(currentMovieState.id, currentSeason, ep.episodeNumber, currentMovieState.title)
                                    val epFocusMod = if (isSel) Modifier.focusRequester(activeEpFocusRequester) else Modifier
                                    Button(
                                        onClick = {
                                            activeDrawer = null
                                            switchStream(currentSeason, ep.episodeNumber, currentAudioId, selectedQuality, selectedSource)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSel) accent.copy(alpha = 0.85f) else if (isWatched) Color.White.copy(alpha = 0.16f) else ChipBackground,
                                            focusedContainerColor = Color.White,
                                            contentColor = if (isSel) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp).then(epFocusMod)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSel) {
                                                AppIcon(
                                                    resId = R.drawable.ic_play_arrow,
                                                    tint = Color.Black,
                                                    size = 12.dp
                                                )
                                            } else if (isWatched) {
                                                Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                                            }
                                            Text(
                                                text = if (ep.title.isNotBlank() && ep.title != "null") ep.title else "Серия ${ep.episodeNumber}",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
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
                        .padding(horizontal = 48.dp, vertical = 20.dp)
                ) {
                    // Inline Series Episodes Strip
                    if (currentMovieState.isSeries) {
                        val activeSeason = currentMovieState.seasons.firstOrNull { it.seasonNumber == currentSeason } ?: currentMovieState.seasons.firstOrNull()
                        val curTrack = currentMovieState.audioTracks.firstOrNull { it.id == currentAudioId }
                        val maxEpForTrack = curTrack?.seasonsEpisodes?.get(currentSeason)
                        val episodeList = remember(activeSeason, currentAudioId, currentMovieState.audioTracks) {
                            val eps = activeSeason?.episodes ?: emptyList()
                            if (maxEpForTrack != null && maxEpForTrack > 0) {
                                eps.filter { it.episodeNumber <= maxEpForTrack }
                            } else if (curTrack?.seasonsEpisodes?.isNotEmpty() == true) {
                                emptyList()
                            } else {
                                eps
                            }
                        }
                        if (episodeList.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                Text(
                                    text = "Серии сезона $currentSeason (${episodeList.size}):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextGray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val inlineIndex = remember(episodeList, currentEpisode) {
                                    val idx = episodeList.indexOfFirst { it.episodeNumber == currentEpisode }
                                    if (idx >= 0) idx else 0
                                }
                                val inlineEpisodesListState = rememberTvLazyListState()
                                LaunchedEffect(currentEpisode, episodeList) {
                                    try {
                                        if (inlineIndex >= 0) {
                                            inlineEpisodesListState.scrollToItem((inlineIndex - 1).coerceAtLeast(0))
                                        }
                                    } catch (_: Exception) {}
                                }
                                TvLazyRow(
                                    state = inlineEpisodesListState,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(start = 4.dp, end = 80.dp),
                                    pivotOffsets = PivotOffsets(parentFraction = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(episodeList) { ep ->
                                        val isCurrentEp = ep.episodeNumber == currentEpisode
                                        val isWatched = historyManager.isEpisodeWatched(currentMovieState.id, currentSeason, ep.episodeNumber)
                                        val epReq = if (isCurrentEp) Modifier.focusRequester(episodesRowFocusRequester) else Modifier
                                        Button(
                                            onClick = {
                                                switchStream(currentSeason, ep.episodeNumber, currentAudioId, selectedQuality, selectedSource)
                                            },
                                            colors = ButtonDefaults.colors(
                                                containerColor = if (isCurrentEp) accent.copy(alpha = 0.85f) else if (isWatched) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                                                focusedContainerColor = Color.White,
                                                contentColor = if (isCurrentEp) Color.Black else TextWhite,
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
                                                .then(epReq)
                                                .focusProperties {
                                                    down = timelineFocusRequester
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
                                                        size = 11.dp
                                                    )
                                                } else if (isWatched) {
                                                    Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                                                }
                                                Text(
                                                    text = if (ep.title.isNotBlank() && ep.title != "null") ep.title else "Серия ${ep.episodeNumber}",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isCurrentEp) FontWeight.Bold else FontWeight.Normal,
                                                    lineHeight = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Progress Bar with Buffered Track & Focusable Remote Scrubbing
                    val progressFraction = if (duration > 0) {
                        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val bufferedFraction = if (duration > 0) {
                        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val trackHeight = if (isTimelineFocused) 10.dp else 4.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .focusRequester(timelineFocusRequester)
                            .onFocusChanged { isTimelineFocused = it.isFocused }
                            .focusable()
                            .padding(horizontal = 4.dp)
                            .focusProperties {
                                down = playPauseFocusRequester
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    lastUserInteractionTime = System.currentTimeMillis()
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            val now = System.currentTimeMillis()
                                            if (now - lastSeekTime < 800L) {
                                                seekSpeedLevel = (seekSpeedLevel + 1).coerceAtMost(seekSteps.lastIndex)
                                            } else {
                                                seekSpeedLevel = 0
                                            }
                                            lastSeekTime = now
                                            val step = seekSteps[seekSpeedLevel]
                                            val basePos = pendingTimelineSeekPos ?: currentPosition
                                            val newPos = (basePos - step).coerceAtLeast(0L)
                                            pendingTimelineSeekPos = newPos
                                            currentPosition = newPos
                                            seekDeltaBadge = formatTimeRu(newPos)
                                            timelineSeekJob?.cancel()
                                            timelineSeekJob = coroutineScope.launch {
                                                delay(120L)
                                                exoPlayer.seekTo(newPos)
                                                pendingTimelineSeekPos = null
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            val now = System.currentTimeMillis()
                                            if (now - lastSeekTime < 800L) {
                                                seekSpeedLevel = (seekSpeedLevel + 1).coerceAtMost(seekSteps.lastIndex)
                                            } else {
                                                seekSpeedLevel = 0
                                            }
                                            lastSeekTime = now
                                            val step = seekSteps[seekSpeedLevel]
                                            val basePos = pendingTimelineSeekPos ?: currentPosition
                                            val maxPos = if (duration > 0) duration else (if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE)
                                            val newPos = (basePos + step).coerceAtMost(maxPos)
                                            pendingTimelineSeekPos = newPos
                                            currentPosition = newPos
                                            seekDeltaBadge = formatTimeRu(newPos)
                                            timelineSeekJob?.cancel()
                                            timelineSeekJob = coroutineScope.launch {
                                                delay(120L)
                                                exoPlayer.seekTo(newPos)
                                                pendingTimelineSeekPos = null
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER -> {
                                            timelineSeekJob?.cancel()
                                            val pPos = pendingTimelineSeekPos
                                            if (pPos != null) {
                                                exoPlayer.seekTo(pPos)
                                                pendingTimelineSeekPos = null
                                            }
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 1. Unplayed background track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight)
                                .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                        )
                        // 2. Buffered / Caching track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferedFraction)
                                .height(trackHeight)
                                .background(Color.White.copy(alpha = 0.35f), shape = RoundedCornerShape(4.dp))
                        )
                        // 3. Played progress track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(trackHeight)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            accent.copy(alpha = 0.75f),
                                            accent
                                        )
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        // 4. Scrubber Thumb & Floating Time Badge when focused
                        if (isTimelineFocused) {
                            Box(
                                modifier = Modifier.fillMaxWidth(progressFraction),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                // Floating Time Badge positioned ABOVE the timeline bar so it never obscures the thumb
                                if (seekDeltaBadge != null) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 10.dp, y = (-26).dp)
                                            .background(accent, shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = seekDeltaBadge ?: "",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }

                                // Scrubber Thumb Dot (Clean, centered on timeline track, no extra outline)
                                Box(
                                    modifier = Modifier
                                        .offset(x = 8.dp)
                                        .size(16.dp)
                                        .background(Color.White, shape = CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Duration Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextWhite
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // TV Remote Interactive Buttons Bar (Height 28dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Play / Pause Button
                        Button(
                            onClick = {
                                togglePlayPause()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color.White,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .focusRequester(playPauseFocusRequester)
                                .focusProperties {
                                    right = rewindFocusRequester
                                    up = timelineFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                                    size = 13.dp
                                )
                                Text(
                                    text = if (isPlaying) "Пауза" else "Старт",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 13.sp
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
                                focusedContainerColor = Color.White,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .focusRequester(rewindFocusRequester)
                                .focusProperties {
                                    left = playPauseFocusRequester
                                    right = forwardFocusRequester
                                    up = timelineFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_replay_10,
                                    size = 13.dp
                                )
                                Text("10с", fontSize = 11.sp, lineHeight = 13.sp)
                            }
                        }

                        // 3. Forward +10s
                        Button(
                            onClick = {
                                val maxPos = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxPos)
                                exoPlayer.seekTo(newPos)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color.White,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .focusRequester(forwardFocusRequester)
                                .focusProperties {
                                    left = rewindFocusRequester
                                    right = qualityFocusRequester
                                    up = timelineFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_forward_10,
                                    size = 13.dp
                                )
                                Text("10с", fontSize = 11.sp, lineHeight = 13.sp)
                            }
                        }

                        // 4. Quality Selector Button
                        val isQualityActive = activeDrawer == "quality"
                        Button(
                            onClick = {
                                activeDrawer = if (activeDrawer == "quality") null else "quality"
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isQualityActive) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color.White,
                                contentColor = if (isQualityActive) Color.Black else TextWhite,
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
                                .focusRequester(qualityFocusRequester)
                                .focusProperties {
                                    left = forwardFocusRequester
                                    right = sourceFocusRequester
                                    up = timelineFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_high_quality,
                                    size = 13.dp
                                )
                                Text(selectedQuality, fontSize = 11.sp, lineHeight = 13.sp)
                            }
                        }

                        // 5. Source Selector Button
                        val afterSourceFocus = if (currentMovieState.audioTracks.isNotEmpty()) audioFocusRequester
                            else if (currentMovieState.isSeries) (if (currentEpisode > 1) prevEpisodeFocusRequester else episodesDrawerFocusRequester)
                            else extPlayerFocusRequester

                        val isSourceActive = activeDrawer == "source"
                        Button(
                            onClick = {
                                activeDrawer = if (activeDrawer == "source") null else "source"
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSourceActive) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color.White,
                                contentColor = if (isSourceActive) Color.Black else TextWhite,
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
                                .focusRequester(sourceFocusRequester)
                                .focusProperties {
                                    left = qualityFocusRequester
                                    right = afterSourceFocus
                                    up = timelineFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_cloud_download,
                                    size = 13.dp
                                )
                                Text(selectedSource, fontSize = 11.sp, lineHeight = 13.sp)
                            }
                        }

                        // 6. Audio Tracks Selector Button
                        if (currentMovieState.audioTracks.isNotEmpty()) {
                            val curName = currentMovieState.audioTracks.firstOrNull { it.id == currentAudioId }?.name ?: "Озвучка"
                            val afterAudioFocus = if (currentMovieState.isSeries) {
                                if (currentEpisode > 1) prevEpisodeFocusRequester else episodesDrawerFocusRequester
                            } else extPlayerFocusRequester

                            val isAudioActive = activeDrawer == "audio"
                            Button(
                                onClick = {
                                    activeDrawer = if (activeDrawer == "audio") null else "audio"
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isAudioActive) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = Color.White,
                                    contentColor = if (isAudioActive) Color.Black else TextWhite,
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
                                    .focusRequester(audioFocusRequester)
                                    .focusProperties {
                                        left = sourceFocusRequester
                                        right = afterAudioFocus
                                        up = timelineFocusRequester
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AppIcon(
                                        resId = R.drawable.ic_volume_up,
                                        size = 13.dp
                                    )
                                    Text(curName, fontSize = 11.sp, maxLines = 1, lineHeight = 13.sp)
                                }
                            }
                        }

                        // 7. Series Next / Prev / List Buttons
                        if (currentMovieState.isSeries) {
                            val beforeSeriesFocus = if (currentMovieState.audioTracks.isNotEmpty()) audioFocusRequester else sourceFocusRequester

                            if (currentEpisode > 1) {
                                Button(
                                    onClick = { switchStream(currentSeason, currentEpisode - 1, currentAudioId, selectedQuality, selectedSource) },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.12f),
                                        focusedContainerColor = Color.White,
                                        contentColor = TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .focusRequester(prevEpisodeFocusRequester)
                                        .focusProperties {
                                            left = beforeSeriesFocus
                                            right = episodesDrawerFocusRequester
                                            up = timelineFocusRequester
                                        }
                                 ) {
                                    Row(
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        AppIcon(
                                            resId = R.drawable.ic_skip_previous,
                                            size = 13.dp
                                        )
                                        Text("Пред.", fontSize = 11.sp, lineHeight = 13.sp)
                                    }
                                }
                            }

                            val isEpisodesActive = activeDrawer == "episodes"
                            Button(
                                onClick = {
                                    activeDrawer = if (activeDrawer == "episodes") null else "episodes"
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isEpisodesActive) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = Color.White,
                                    contentColor = if (isEpisodesActive) Color.Black else TextWhite,
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
                                    .focusRequester(episodesDrawerFocusRequester)
                                    .focusProperties {
                                        left = if (currentEpisode > 1) prevEpisodeFocusRequester else beforeSeriesFocus
                                        right = nextEpisodeFocusRequester
                                        up = timelineFocusRequester
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AppIcon(
                                        resId = R.drawable.ic_video_library,
                                        size = 13.dp
                                    )
                                    Text("Все серии", fontSize = 11.sp, lineHeight = 13.sp)
                                }
                            }

                            Button(
                                onClick = { switchStream(currentSeason, currentEpisode + 1, currentAudioId, selectedQuality, selectedSource) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = Color.White,
                                    contentColor = TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusRequester(nextEpisodeFocusRequester)
                                    .focusProperties {
                                        left = episodesDrawerFocusRequester
                                        right = extPlayerFocusRequester
                                        up = timelineFocusRequester
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("След.", fontSize = 11.sp, lineHeight = 13.sp)
                                    AppIcon(
                                        resId = R.drawable.ic_skip_next,
                                        size = 13.dp
                                    )
                                }
                            }
                        }

                        // 8. External Player Button
                        val beforeExtFocus = if (currentMovieState.isSeries) nextEpisodeFocusRequester
                            else if (currentMovieState.audioTracks.isNotEmpty()) audioFocusRequester
                            else sourceFocusRequester

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        val uri = Uri.parse(currentStreamUrl)
                                        val mime = if (currentStreamUrl.contains(".m3u8")) "application/x-mpegURL" else "video/*"
                                        setDataAndType(uri, mime)
                                        putExtra("title", currentMovieState.title)
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
                                focusedContainerColor = Color.White,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .focusRequester(extPlayerFocusRequester)
                                .focusProperties {
                                    left = beforeExtFocus
                                    up = timelineFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_open_in_new,
                                    size = 13.dp
                                )
                                Text("Внешний", fontSize = 11.sp, lineHeight = 13.sp)
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
