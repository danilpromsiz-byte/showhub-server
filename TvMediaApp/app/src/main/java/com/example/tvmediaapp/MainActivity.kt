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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.example.tvmediaapp.ui.screens.search.SearchScreen
import com.example.tvmediaapp.ui.screens.settings.SettingsScreen
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.ThemeManager
import com.example.tvmediaapp.ui.theme.TvMediaAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    DETAILS,
    SEARCH,
    FAVORITES,
    HISTORY,
    SETTINGS,
    PLAYER
}

class MainActivity : ComponentActivity() {
    fun getInstalledVersionCode(): Int {
        return try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            40
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "2.6.1"
        } catch (e: Exception) {
            "2.6.1"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoilSetup.init(this)
        ThemeManager.init(this)

        setContent {
            TvMediaAppTheme {
                TvAppNavHost(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
            val myCode = activity.getInstalledVersionCode()
            val info = UpdateManager.checkUpdate(myCode)
            if (info.hasUpdate && info.versionCode > myCode) {
                updateInfo = info
            } else {
                updateInfo = null
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

    val isUpdateDialogVisible = updateInfo?.let { it.versionCode > activity.getInstalledVersionCode() } == true

    // Hardware Back button handling for Android TV remotes
    BackHandler(enabled = isUpdateDialogVisible) {
        updateInfo = null
    }

    BackHandler(enabled = !isUpdateDialogVisible && currentScreen != Screen.HOME) {
        when (currentScreen) {
            Screen.PLAYER -> currentScreen = Screen.DETAILS
            Screen.DETAILS -> currentScreen = Screen.HOME
            Screen.SEARCH -> currentScreen = Screen.HOME
            Screen.FAVORITES -> currentScreen = Screen.HOME
            Screen.HISTORY -> currentScreen = Screen.HOME
            Screen.SETTINGS -> currentScreen = Screen.HOME
            Screen.HOME -> activity.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .focusProperties {
                canFocus = !isUpdateDialogVisible
            }
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
                    onSettingsClick = {
                        currentScreen = Screen.SETTINGS
                    },
                    onCheckUpdateClick = { triggerUpdateCheck() },
                    hasUpdateAvailable = (updateInfo != null && updateInfo!!.versionCode > activity.getInstalledVersionCode()),
                    appVersion = activity.getInstalledVersionName(),
                    viewModel = homeViewModel
                )
            }

            Screen.SETTINGS -> {
                SettingsScreen(
                    appVersion = activity.getInstalledVersionName(),
                    versionCode = activity.getInstalledVersionCode(),
                    onBackClick = { currentScreen = Screen.HOME },
                    onSearchClick = { currentScreen = Screen.SEARCH },
                    onFavoritesClick = { currentScreen = Screen.FAVORITES },
                    onHistoryClick = { currentScreen = Screen.HISTORY },
                    onCheckUpdateClick = { triggerUpdateCheck() },
                    hasUpdateAvailable = (updateInfo != null && updateInfo!!.versionCode > activity.getInstalledVersionCode())
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
        if (isUpdateDialogVisible) {
            val update = updateInfo!!
            val updateFocusRequester = remember { FocusRequester() }
            val remindLaterFocusRequester = remember { FocusRequester() }
            var updateStatus by remember { mutableStateOf<String?>(null) }
            var updatePercent by remember { mutableIntStateOf(0) }

            LaunchedEffect(update) {
                for (i in 1..5) {
                    delay(120)
                    try {
                        updateFocusRequester.requestFocus()
                        break
                    } catch (_: Exception) {}
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Доступно обновление ShowHub TV v${update.versionName}",
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
                    val accent = LocalAccentColor.current
                    Row {
                        Button(
                            onClick = {
                                if (!isDownloadingUpdate) {
                                    isDownloadingUpdate = true
                                    coroutineScope.launch {
                                        UpdateManager.downloadAndInstall(activity, update.downloadUrl) { status, percent ->
                                            updateStatus = status
                                            updatePercent = percent
                                        }
                                        isDownloadingUpdate = false
                                    }
                                }
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .focusRequester(updateFocusRequester)
                                .focusProperties {
                                    right = remindLaterFocusRequester
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                }
                        ) {
                            Text(
                                text = if (isDownloadingUpdate) {
                                    if (updatePercent > 0) "Загрузка: $updatePercent%" else "Загрузка..."
                                } else {
                                    "Обновить сейчас"
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { updateInfo = null },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            modifier = Modifier
                                .focusRequester(remindLaterFocusRequester)
                                .focusProperties {
                                    left = updateFocusRequester
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                        ) {
                            Text(
                                text = "Напомнить позже",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (updateStatus != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = updateStatus!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
