package com.example.tvmediaapp.ui.screens.player

import android.view.KeyEvent
import android.view.ViewGroup
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
import androidx.media3.common.Player
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
import com.example.tvmediaapp.ui.theme.RedPrimary
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    movie: Movie,
    startPositionMs: Long = 0L,
    season: Int = 1,
    episode: Int = 1,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val historyManager = remember { WatchHistoryManager(context) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(startPositionMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }

    // Initialize Media3 ExoPlayer with resume support
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(movie.videoUrl)
            setMediaItem(mediaItem)
            if (startPositionMs > 1000L) {
                seekTo(startPositionMs)
            }
            prepare()
            playWhenReady = true
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
                    season = season,
                    episode = episode
                )
            }
            delay(1000)
        }
    }

    // Auto-hide TV controls after 4 seconds
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Handle Hardware Back button
    BackHandler {
        onBackPress()
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
                        season = season,
                        episode = episode
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
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    isControlsVisible = true
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            val maxPos = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                            val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxPos)
                            exoPlayer.seekTo(newPos)
                            return@onKeyEvent true
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
                                BackgroundDark.copy(alpha = 0.85f),
                                Color.Transparent,
                                BackgroundDark.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                // Top Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp),
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
                            "\u0421\u0435\u0437\u043e\u043d $season \u2022 \u0421\u0435\u0440\u0438\u044f $episode"
                        } else {
                            "${movie.releaseYear} \u2022 ${movie.duration}"
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanNeon
                        )
                    }
                }

                // Bottom Progress Bar and Time
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 48.dp, vertical = 36.dp)
                ) {
                    // D-Pad Hints
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isPlaying) "\u23f8 OK \u2014 \u041f\u0430\u0443\u0437\u0430" else "\u25b6 OK \u2014 \u0412\u043e\u0441\u043f\u0440\u043e\u0438\u0437\u0432\u0435\u0434\u0435\u043d\u0438\u0435",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            text = "\u25c0 \u0412\u043b\u0435\u0432\u043e / \u0412\u043f\u0440\u0430\u0432\u043e \u25b6 \u2014 \u041f\u0435\u0440\u0435\u043c\u043e\u0442\u043a\u0430 \u00b110 \u0441\u0435\u043a",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextGray
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar track
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(CyanNeon)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time labels
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
