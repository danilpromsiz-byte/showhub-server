package com.example.tvmediaapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.cache.MediaDiskCache
import com.example.tvmediaapp.data.image.CoilSetup
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.updater.UpdateInfo
import com.example.tvmediaapp.data.updater.UpdateManager
import com.example.tvmediaapp.ui.screens.details.DetailsScreen
import com.example.tvmediaapp.data.history.SessionManager
import com.example.tvmediaapp.util.CrashReporter
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
            45
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "2.6.6"
        } catch (e: Exception) {
            "2.6.6"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.init(this)
        MediaDiskCache.init(this)
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

    val restoredSession = remember { SessionManager.restoreSession(activity) }
    var currentScreen by remember {
        mutableStateOf(
            when {
                restoredSession?.screen == Screen.PLAYER && restoredSession.videoUrl.isNotBlank() -> Screen.PLAYER
                restoredSession != null -> Screen.DETAILS
                else -> Screen.HOME
            }
        )
    }
    var selectedMovie by remember { mutableStateOf<Movie?>(restoredSession?.movie) }
    var activeVideoUrl by remember { mutableStateOf(restoredSession?.videoUrl ?: "") }
    var startPositionMs by remember { mutableLongStateOf(restoredSession?.positionMs ?: 0L) }
    var activeSeason by remember { mutableIntStateOf(restoredSession?.season ?: 1) }
    var activeEpisode by remember { mutableIntStateOf(restoredSession?.episode ?: 1) }
    var activeAudioId by remember { mutableStateOf(restoredSession?.audioId ?: "") }
    var searchInitialQuery by remember { mutableStateOf("") }

    // Persist session when in DETAILS or PLAYER, clear when at HOME
    LaunchedEffect(currentScreen, selectedMovie, activeVideoUrl, startPositionMs, activeSeason, activeEpisode, activeAudioId) {
        if (currentScreen == Screen.DETAILS || currentScreen == Screen.PLAYER) {
            selectedMovie?.let { movie ->
                SessionManager.saveSession(
                    context = activity,
                    movie = movie,
                    screen = currentScreen,
                    videoUrl = activeVideoUrl,
                    positionMs = startPositionMs,
                    season = activeSeason,
                    episode = activeEpisode,
                    audioId = activeAudioId
                )
            }
        } else if (currentScreen == Screen.HOME) {
            SessionManager.clearSession(activity)
            homeViewModel.refreshCatalog()
        }
    }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    fun triggerUpdateCheck(isUserClick: Boolean = false) {
        coroutineScope.launch {
            if (isUserClick) {
                Toast.makeText(activity, "Проверка обновлений ShowHub TV...", Toast.LENGTH_SHORT).show()
            }
            val myCode = activity.getInstalledVersionCode()
            val info = UpdateManager.checkUpdate(myCode)
            if (info.hasUpdate && info.versionCode > myCode) {
                updateInfo = info
                if (isUserClick) {
                    Toast.makeText(activity, "Доступно обновление ShowHub TV v${info.versionName}!", Toast.LENGTH_SHORT).show()
                }
            } else {
                updateInfo = null
                if (isUserClick) {
                    if (info.versionCode > 0) {
                        Toast.makeText(activity, "У вас установлена актуальная версия ShowHub TV (v${activity.getInstalledVersionName()})", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(activity, "Не удалось проверить обновления. Проверьте интернет-соединение.", Toast.LENGTH_LONG).show()
                    }
                }
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
                if (!isDownloadingUpdate && updateInfo == null) {
                    triggerUpdateCheck()
                }
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
            Screen.DETAILS -> {
                SessionManager.clearSession(activity)
                currentScreen = Screen.HOME
            }
            Screen.SEARCH -> {
                searchInitialQuery = ""
                currentScreen = Screen.HOME
            }
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
                        searchInitialQuery = ""
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
                    onCheckUpdateClick = { triggerUpdateCheck(isUserClick = true) },
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
                    onSearchClick = {
                        searchInitialQuery = ""
                        currentScreen = Screen.SEARCH
                    },
                    onFavoritesClick = { currentScreen = Screen.FAVORITES },
                    onHistoryClick = { currentScreen = Screen.HISTORY },
                    onCheckUpdateClick = { triggerUpdateCheck(isUserClick = true) },
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
                        searchInitialQuery = ""
                        currentScreen = Screen.HOME
                    },
                    initialMovies = homeViewModel.getAllMovies(),
                    initialQuery = searchInitialQuery
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
                        onPlayClick = { detailedMovie, streamUrl, startPos, season, episode, audioId ->
                            selectedMovie = detailedMovie
                            activeVideoUrl = streamUrl
                            startPositionMs = startPos
                            activeSeason = season
                            activeEpisode = episode
                            activeAudioId = audioId
                            currentScreen = Screen.PLAYER
                        },
                        onBackClick = {
                            SessionManager.clearSession(activity)
                            currentScreen = Screen.HOME
                        },
                        onToggleFavorite = {
                            homeViewModel.toggleFavorite(it)
                        },
                        isFavorite = isFav,
                        onSearchClick = { actorName ->
                            searchInitialQuery = actorName
                            currentScreen = Screen.SEARCH
                        }
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
                        audioId = activeAudioId,
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
            val browserFocusRequester = remember { FocusRequester() }
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
                    .background(Color.Black.copy(alpha = 0.92f))
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

                    // Download Progress Bar
                    if (isDownloadingUpdate && updatePercent >= 0) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .width(460.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((updatePercent / 100f).coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LocalAccentColor.current)
                            )
                        }
                    }

                    // Status Text Message
                    if (updateStatus != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = updateStatus!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (updateStatus!!.contains("Ошибка") || updateStatus!!.contains("Сбой") || updateStatus!!.contains("поврежден")) Color(0xFFF87171) else LocalAccentColor.current,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    val accent = LocalAccentColor.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Button 1: Install Update
                        Button(
                            onClick = {
                                if (!isDownloadingUpdate) {
                                    isDownloadingUpdate = true
                                    coroutineScope.launch {
                                        val success = UpdateManager.downloadAndInstall(activity, update.downloadUrl) { status, percent ->
                                            updateStatus = status
                                            updatePercent = percent
                                        }
                                        isDownloadingUpdate = false
                                        if (success) {
                                            updateInfo = null
                                        }
                                    }
                                }
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(updateFocusRequester)
                                .focusProperties {
                                    right = browserFocusRequester
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                }
                        ) {
                            Text(
                                text = if (isDownloadingUpdate) {
                                    if (updatePercent > 0) "Загрузка: $updatePercent%" else "Загрузка..."
                                } else if (updateStatus != null && (updateStatus!!.contains("Ошибка") || updateStatus!!.contains("Сбой"))) {
                                    "Повторить попытку"
                                } else {
                                    "Обновить сейчас"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Button 2: Browser Fallback Download
                        Button(
                            onClick = {
                                UpdateManager.openDownloadUrlInBrowser(activity, update.downloadUrl)
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color.White,
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(browserFocusRequester)
                                .focusProperties {
                                    left = updateFocusRequester
                                    right = remindLaterFocusRequester
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                }
                        ) {
                            Text(
                                text = "Открыть в браузере",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Button 3: Remind Later
                        Button(
                            onClick = { updateInfo = null },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White,
                                contentColor = Color.LightGray,
                                focusedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .focusRequester(remindLaterFocusRequester)
                                .focusProperties {
                                    left = browserFocusRequester
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                        ) {
                            Text(
                                text = "Напомнить позже",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
