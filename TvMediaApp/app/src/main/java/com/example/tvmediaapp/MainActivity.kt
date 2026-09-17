package com.example.tvmediaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.image.CoilSetup
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.updater.UpdateInfo
import com.example.tvmediaapp.data.updater.UpdateManager
import com.example.tvmediaapp.ui.screens.details.DetailsScreen
import com.example.tvmediaapp.ui.screens.favorites.FavoritesScreen
import com.example.tvmediaapp.ui.screens.history.HistoryScreen
import com.example.tvmediaapp.ui.screens.home.HomeScreen
import com.example.tvmediaapp.ui.screens.home.HomeViewModel
import com.example.tvmediaapp.ui.screens.player.PlayerScreen
import com.example.tvmediaapp.ui.screens.search.SearchScreen
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.TvMediaAppTheme
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    DETAILS,
    SEARCH,
    FAVORITES,
    HISTORY,
    PLAYER
}

class MainActivity : ComponentActivity() {
    companion object {
        const val VERSION_CODE = 33
        const val VERSION_NAME = "2.3.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoilSetup.init(this)

        setContent {
            TvMediaAppTheme {
                TvAppNavHost(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAppNavHost(activity: MainActivity) {
    val homeViewModel: HomeViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var activeVideoUrl by remember { mutableStateOf<String>("") }
    var startPositionMs by remember { mutableLongStateOf(0L) }
    var activeSeason by remember { mutableIntStateOf(1) }
    var activeEpisode by remember { mutableIntStateOf(1) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    fun triggerUpdateCheck() {
        coroutineScope.launch {
            val info = UpdateManager.checkUpdate(MainActivity.VERSION_CODE)
            if (info.hasUpdate) {
                updateInfo = info
            }
        }
    }

    // Auto-check on launch
    LaunchedEffect(Unit) {
        triggerUpdateCheck()
    }

    // Auto-check on ON_RESUME (whenever app returns to foreground)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                triggerUpdateCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Hardware Back button handling for Android TV remotes
    BackHandler(enabled = currentScreen != Screen.HOME) {
        when (currentScreen) {
            Screen.PLAYER -> currentScreen = Screen.DETAILS
            Screen.DETAILS -> currentScreen = Screen.HOME
            Screen.SEARCH -> currentScreen = Screen.HOME
            Screen.FAVORITES -> currentScreen = Screen.HOME
            Screen.HISTORY -> currentScreen = Screen.HOME
            Screen.HOME -> activity.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (currentScreen) {
            Screen.HOME -> {
                HomeScreen(
                    onMovieSelect = { movie ->
                        selectedMovie = movie
                        currentScreen = Screen.DETAILS
                    },
                    onWatchClick = { movie ->
                        selectedMovie = movie
                        currentScreen = Screen.DETAILS
                    },
                    onSearchClick = {
                        currentScreen = Screen.SEARCH
                    },
                    onFavoritesClick = {
                        currentScreen = Screen.FAVORITES
                    },
                    onHistoryClick = {
                        currentScreen = Screen.HISTORY
                    },
                    viewModel = homeViewModel
                )
            }

            Screen.SEARCH -> {
                SearchScreen(
                    onMovieSelect = { movie ->
                        selectedMovie = movie
                        currentScreen = Screen.DETAILS
                    },
                    onBackClick = {
                        currentScreen = Screen.HOME
                    },
                    initialMovies = homeViewModel.getAllMovies()
                )
            }

            Screen.FAVORITES -> {
                FavoritesScreen(
                    onMovieSelect = { movie ->
                        selectedMovie = movie
                        currentScreen = Screen.DETAILS
                    },
                    onBackClick = {
                        currentScreen = Screen.HOME
                    },
                    viewModel = homeViewModel
                )
            }

            Screen.HISTORY -> {
                HistoryScreen(
                    onMovieSelect = { movie ->
                        selectedMovie = movie
                        currentScreen = Screen.DETAILS
                    },
                    onResumePlay = { movie, pos, s, ep ->
                        selectedMovie = movie
                        startPositionMs = pos
                        activeSeason = s
                        activeEpisode = ep
                        currentScreen = Screen.DETAILS
                    },
                    onBackClick = {
                        currentScreen = Screen.HOME
                    }
                )
            }

            Screen.DETAILS -> {
                selectedMovie?.let { movie ->
                    val isFav = homeViewModel.isFavorite(movie.id)
                    DetailsScreen(
                        movie = movie,
                        onPlayClick = { streamUrl, startPos, season, episode ->
                            activeVideoUrl = streamUrl
                            startPositionMs = startPos
                            activeSeason = season
                            activeEpisode = episode
                            currentScreen = Screen.PLAYER
                        },
                        onBackClick = {
                            currentScreen = Screen.HOME
                        },
                        onToggleFavorite = {
                            homeViewModel.toggleFavorite(it)
                        },
                        isFavorite = isFav
                    )
                } ?: run {
                    currentScreen = Screen.HOME
                }
            }

            Screen.PLAYER -> {
                selectedMovie?.let { movie ->
                    val playMovie = if (activeVideoUrl.isNotEmpty()) movie.copy(videoUrl = activeVideoUrl) else movie
                    PlayerScreen(
                        movie = playMovie,
                        startPositionMs = startPositionMs,
                        season = activeSeason,
                        episode = activeEpisode,
                        onBackPress = {
                            currentScreen = Screen.DETAILS
                        }
                    )
                } ?: run {
                    currentScreen = Screen.HOME
                }
            }
        }

        // In-app Update Notification Banner/Dialog
        updateInfo?.let { update ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u043e \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 ShowHub TV v${update.versionName}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = update.changelog,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row {
                        Button(
                            onClick = {
                                if (!isDownloadingUpdate) {
                                    isDownloadingUpdate = true
                                    coroutineScope.launch {
                                        UpdateManager.downloadAndInstall(activity, update.downloadUrl)
                                        isDownloadingUpdate = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = CyanNeon,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (isDownloadingUpdate) "\u23f3  \u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430 APK..." else "\u2b07\ufe0f  \u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c \u0441\u0435\u0439\u0447\u0430\u0441",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { updateInfo = null }
                        ) {
                            Text(
                                text = "\u041d\u0430\u043f\u043e\u043c\u043d\u0438\u0442\u044c \u043f\u043e\u0437\u0436\u0435",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
