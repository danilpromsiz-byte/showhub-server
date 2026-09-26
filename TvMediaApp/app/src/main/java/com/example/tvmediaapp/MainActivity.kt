package com.example.tvmediaapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.tv.material3.Border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.tvmediaapp.data.api.ShowHubApiClient
import kotlinx.coroutines.Dispatchers
import com.example.tvmediaapp.data.cache.MediaDiskCache
import com.example.tvmediaapp.data.image.CoilSetup
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.updater.UpdateInfo
import com.example.tvmediaapp.data.updater.UpdateManager
import com.example.tvmediaapp.ui.screens.details.DetailsScreen
import com.example.tvmediaapp.data.history.SessionManager
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.util.CrashReporter
import com.example.tvmediaapp.ui.screens.favorites.FavoritesScreen
import com.example.tvmediaapp.ui.screens.history.HistoryScreen
import com.example.tvmediaapp.ui.screens.home.HomeScreen
import com.example.tvmediaapp.ui.screens.home.HomeViewModel
import com.example.tvmediaapp.ui.screens.player.PlayerScreen
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.KeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import com.example.tvmediaapp.ui.screens.schedule.ScheduleCalendarScreen
import com.example.tvmediaapp.ui.screens.search.SearchScreen
import com.example.tvmediaapp.ui.screens.settings.SettingsScreen
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.ThemeManager
import com.example.tvmediaapp.ui.theme.TvMediaAppTheme
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    DETAILS,
    SEARCH,
    FAVORITES,
    HISTORY,
    SCHEDULE,
    SETTINGS,
    PLAYER
}

data class NavState(
    val screen: Screen,
    val movie: Movie? = null,
    val searchQuery: String = "",
    val searchIsActor: Boolean = false,
    val videoUrl: String = "",
    val positionMs: Long = 0L,
    val season: Int = 1,
    val episode: Int = 1,
    val audioId: String = ""
)

data class ModalCardTheme(
    val name: String,
    val primaryColor: Color,
    val bgCardColor: Color,
    val badgeBg: Color,
    val badgeTextColor: Color,
    val buttonContentColor: Color
)

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
            BuildConfig.VERSION_CODE
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CrashReporter.init(this)
        MediaDiskCache.init(this)
        CoilSetup.init(this)
        ThemeManager.init(this)
        ShowHubApiClient.init(this, getInstalledVersionName())

        setContent {
            TvMediaAppTheme {
                TvAppNavHost(activity = this)
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val isNavigationKey = when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                android.view.KeyEvent.KEYCODE_BACK -> true
                else -> false
            }
            if (isNavigationKey) {
                val view = currentFocus
                if (view == null || !view.hasFocus()) {
                    window.decorView.requestFocus()
                }
            }
        }
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Safely caught unhandled focus navigation error", e)
            true
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvAppNavHost(activity: MainActivity) {
    val homeViewModel: HomeViewModel = viewModel()
    val episodeAlerts by homeViewModel.newEpisodeAlerts.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val restoredSession = remember { SessionManager.restoreSession(activity) }
    val historyManager = remember { WatchHistoryManager(activity) }
    val restoredPosition = remember {
        val hItem = restoredSession?.movie?.let { historyManager.getProgress(it.id, it.title) }
        if (hItem != null && hItem.positionMs > 1000L) {
            if (!hItem.isSeries || (hItem.season == restoredSession?.season && hItem.episode == restoredSession?.episode)) {
                hItem.positionMs
            } else {
                restoredSession?.positionMs ?: 0L
            }
        } else {
            restoredSession?.positionMs ?: 0L
        }
    }

    var currentScreen by remember {
        mutableStateOf(
            if (restoredSession != null) Screen.DETAILS else Screen.HOME
        )
    }
    LaunchedEffect(currentScreen) {
        CrashReporter.lastScreen = currentScreen.name
    }
    var selectedMovie by remember { mutableStateOf<Movie?>(restoredSession?.movie) }
    var activeVideoUrl by remember { mutableStateOf(restoredSession?.videoUrl ?: "") }
    var startPositionMs by remember { mutableLongStateOf(restoredPosition) }
    var activeSeason by remember { mutableIntStateOf(restoredSession?.season ?: 1) }
    var activeEpisode by remember { mutableIntStateOf(restoredSession?.episode ?: 1) }
    var activeAudioId by remember { mutableStateOf(restoredSession?.audioId ?: "") }
    var searchInitialQuery by remember { mutableStateOf("") }
    var searchIsActor by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val backStack = remember { mutableStateListOf<NavState>() }

    fun navigateTo(
        newScreen: Screen,
        movie: Movie? = selectedMovie,
        searchQuery: String = searchInitialQuery,
        searchIsActorVal: Boolean = searchIsActor,
        videoUrl: String = activeVideoUrl,
        positionMs: Long = startPositionMs,
        season: Int = activeSeason,
        episode: Int = activeEpisode,
        audioId: String = activeAudioId
    ) {
        // Fast guard: If already on this exact screen with this exact movie, ignore duplicate calls
        if (newScreen == currentScreen && (movie == null || movie.id == selectedMovie?.id)) {
            return
        }

        val currentState = NavState(
            screen = currentScreen,
            movie = selectedMovie,
            searchQuery = searchInitialQuery,
            searchIsActor = searchIsActor,
            videoUrl = activeVideoUrl,
            positionMs = startPositionMs,
            season = activeSeason,
            episode = activeEpisode,
            audioId = activeAudioId
        )
        // Deduplicate: avoid pushing redundant identical screen/movie to backstack
        if (backStack.isEmpty() || backStack.last().screen != currentScreen || backStack.last().movie?.id != selectedMovie?.id) {
            backStack.add(currentState)
        }
        currentScreen = newScreen
        selectedMovie = movie
        searchInitialQuery = searchQuery
        searchIsActor = searchIsActorVal
        activeVideoUrl = videoUrl
        startPositionMs = positionMs
        activeSeason = season
        activeEpisode = episode
        activeAudioId = audioId
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    fun navigateBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 150L) {
            return
        }
        lastBackPressTime = now

        // Direct, instantaneous exit from DETAILS back to HOME or previous screen
        if (currentScreen == Screen.DETAILS) {
            while (backStack.isNotEmpty() && backStack.last().screen == Screen.DETAILS) {
                backStack.removeAt(backStack.size - 1)
            }
            if (backStack.isNotEmpty()) {
                val prev = backStack.removeAt(backStack.size - 1)
                currentScreen = prev.screen
                selectedMovie = prev.movie
                searchInitialQuery = prev.searchQuery
                searchIsActor = prev.searchIsActor
                activeVideoUrl = prev.videoUrl
                startPositionMs = prev.positionMs
                activeSeason = prev.season
                activeEpisode = prev.episode
                activeAudioId = prev.audioId
                if (prev.screen == Screen.HOME) {
                    SessionManager.clearSession(activity)
                }
            } else {
                currentScreen = Screen.HOME
                selectedMovie = null
                SessionManager.clearSession(activity)
            }
            return
        }

        // Pop any trailing redundant states matching current screen
        while (backStack.isNotEmpty() && backStack.last().screen == currentScreen) {
            backStack.removeAt(backStack.size - 1)
        }
        if (backStack.isNotEmpty()) {
            var prev = backStack.removeAt(backStack.size - 1)
            while (backStack.isNotEmpty() && prev.screen == currentScreen) {
                prev = backStack.removeAt(backStack.size - 1)
            }
            if (prev.screen == currentScreen) {
                currentScreen = Screen.HOME
                selectedMovie = null
                SessionManager.clearSession(activity)
                return
            }
            currentScreen = prev.screen
            selectedMovie = prev.movie
            searchInitialQuery = prev.searchQuery
            searchIsActor = prev.searchIsActor
            activeVideoUrl = prev.videoUrl
            val prevMovie = prev.movie
            if (prev.screen == Screen.DETAILS && prevMovie != null) {
                val latestHist = historyManager.getProgress(prevMovie.id, prevMovie.title)
                startPositionMs = latestHist?.positionMs ?: prev.positionMs
                activeSeason = latestHist?.season ?: prev.season
                activeEpisode = latestHist?.episode ?: prev.episode
                activeAudioId = latestHist?.audioId?.ifEmpty { prev.audioId } ?: prev.audioId
            } else {
                startPositionMs = prev.positionMs
                activeSeason = prev.season
                activeEpisode = prev.episode
                activeAudioId = prev.audioId
            }
            if (prev.screen == Screen.HOME) {
                SessionManager.clearSession(activity)
            }
        } else {
            if (currentScreen != Screen.HOME) {
                currentScreen = Screen.HOME
                selectedMovie = null
                SessionManager.clearSession(activity)
            } else {
                showExitDialog = true
            }
        }
    }

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
        }
    }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var dismissedVersionCode by remember { mutableIntStateOf(0) }
    var lastInstallerLaunchTime by remember { mutableLongStateOf(0L) }

    fun triggerUpdateCheck(isUserClick: Boolean = false) {
        coroutineScope.launch {
            if (isUserClick) {
                Toast.makeText(activity, "Проверка обновлений ShowHub TV...", Toast.LENGTH_SHORT).show()
            }
            val myCode = activity.getInstalledVersionCode()
            val info = UpdateManager.checkUpdate(myCode)
            if (info.hasUpdate && info.versionCode > myCode) {
                // Silently pre-download APK in background so install is instant with 0s wait
                coroutineScope.launch(Dispatchers.IO) {
                    UpdateManager.predownloadUpdate(activity.applicationContext, info)
                }
                if (info.isForceUpdate || isUserClick || info.versionCode != dismissedVersionCode) {
                    updateInfo = info
                }
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

    // Auto-check on launch with safety delay and periodic background check every 2 minutes
    LaunchedEffect(Unit) {
        delay(600)
        triggerUpdateCheck()
        // Record active device heartbeat in analytics
        ShowHubApiClient.ping(activity, activity.getInstalledVersionName())
        delay(3000)
        if (updateInfo == null) {
            triggerUpdateCheck()
        }
        while (isActive) {
            delay(120_000L) // every 2 minutes auto-check in background
            if (updateInfo == null && !isDownloadingUpdate) {
                triggerUpdateCheck()
            }
        }
    }

    // Auto-check on ON_RESUME (whenever app returns to foreground)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val timeSinceInstaller = System.currentTimeMillis() - lastInstallerLaunchTime
                if (!isDownloadingUpdate && updateInfo == null && timeSinceInstaller > 45_000L) {
                    triggerUpdateCheck()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isWatchingMovie = (currentScreen == Screen.PLAYER)
    val isUpdateDialogVisible = !isWatchingMovie && updateInfo?.let { it.versionCode > activity.getInstalledVersionCode() } == true
    val isMandatoryUpdate = updateInfo?.isForceUpdate == true
    val isEpisodeAlertVisible = !isWatchingMovie && episodeAlerts.isNotEmpty()

    // Hardware Back button handling for Android TV remotes
    BackHandler(enabled = isUpdateDialogVisible) {
        if (!isMandatoryUpdate) {
            updateInfo?.let { dismissedVersionCode = it.versionCode }
            updateInfo = null
        }
    }


    BackHandler(enabled = showExitDialog && !isUpdateDialogVisible && !isEpisodeAlertVisible) {
        showExitDialog = false
    }

    BackHandler(enabled = !isUpdateDialogVisible && !isEpisodeAlertVisible && !showExitDialog) {
        navigateBack()
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Auto-restore focus when dialogs close
    LaunchedEffect(showExitDialog, isUpdateDialogVisible, isEpisodeAlertVisible) {
        if (!showExitDialog && !isUpdateDialogVisible && !isEpisodeAlertVisible) {
            delay(80)
            try {
                activity.window.decorView.requestFocus()
                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Enter)
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackgroundColor.current)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    if (isUpdateDialogVisible || showExitDialog || isEpisodeAlertVisible) {
                        canFocus = false
                        enter = { FocusRequester.Cancel }
                    }
                }
        ) {
            when (currentScreen) {
            Screen.HOME -> {
                HomeScreen(
                    onMovieSelect = { movie ->
                        navigateTo(Screen.DETAILS, movie = movie)
                    },
                    onWatchClick = { movie ->
                        navigateTo(Screen.DETAILS, movie = movie)
                    },
                    onSearchClick = {
                        navigateTo(Screen.SEARCH, searchQuery = "", searchIsActorVal = false)
                    },
                    onFavoritesClick = {
                        navigateTo(Screen.FAVORITES)
                    },
                    onHistoryClick = {
                        navigateTo(Screen.HISTORY)
                    },
                    onScheduleClick = {
                        navigateTo(Screen.SCHEDULE)
                    },
                    onSettingsClick = {
                        navigateTo(Screen.SETTINGS)
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
                    onBackClick = { navigateBack() },
                    onSearchClick = {
                        navigateTo(Screen.SEARCH, searchQuery = "", searchIsActorVal = false)
                    },
                    onFavoritesClick = { navigateTo(Screen.FAVORITES) },
                    onHistoryClick = { navigateTo(Screen.HISTORY) },
                    onScheduleClick = { navigateTo(Screen.SCHEDULE) },
                    onCheckUpdateClick = { triggerUpdateCheck(isUserClick = true) },
                    hasUpdateAvailable = (updateInfo != null && updateInfo!!.versionCode > activity.getInstalledVersionCode())
                )
            }

            Screen.SEARCH -> {
                SearchScreen(
                    onMovieSelect = { movie ->
                        navigateTo(Screen.DETAILS, movie = movie)
                    },
                    onBackClick = {
                        navigateBack()
                    },
                    initialMovies = homeViewModel.getAllMovies(),
                    initialQuery = searchInitialQuery,
                    isActorSearch = searchIsActor
                )
            }

            Screen.FAVORITES -> {
                FavoritesScreen(
                    onMovieSelect = { movie ->
                        navigateTo(Screen.DETAILS, movie = movie)
                    },
                    onBackClick = {
                        navigateBack()
                    },
                    viewModel = homeViewModel
                )
            }

            Screen.HISTORY -> {
                HistoryScreen(
                    onMovieSelect = { movie ->
                        navigateTo(Screen.DETAILS, movie = movie)
                    },
                    onResumePlay = { movie, pos, s, ep ->
                        navigateTo(Screen.DETAILS, movie = movie, positionMs = pos, season = s, episode = ep)
                    },
                    onBackClick = {
                        navigateBack()
                    }
                )
            }

            Screen.SCHEDULE -> {
                ScheduleCalendarScreen(
                    onMovieSelect = { movie ->
                        navigateTo(Screen.DETAILS, movie = movie)
                    },
                    onBackClick = {
                        navigateBack()
                    },
                    trackedMovies = homeViewModel.getFavoriteMovies().filter { it.isSeries }
                )
            }

            Screen.DETAILS -> {
                selectedMovie?.let { movie ->
                    val isFav = homeViewModel.isFavorite(movie.id)
                    DetailsScreen(
                        movie = movie,
                        onPlayClick = { detailedMovie, streamUrl, startPos, season, episode, audioId ->
                            navigateTo(
                                Screen.PLAYER,
                                movie = detailedMovie,
                                videoUrl = streamUrl,
                                positionMs = startPos,
                                season = season,
                                episode = episode,
                                audioId = audioId
                            )
                        },
                        onBackClick = {
                            navigateBack()
                        },
                        onToggleFavorite = {
                            homeViewModel.toggleFavorite(it)
                        },
                        isFavorite = isFav,
                        onSearchClick = { actorName ->
                            navigateTo(Screen.SEARCH, searchQuery = actorName, searchIsActorVal = true)
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
                        onPositionChange = { newPos ->
                            startPositionMs = newPos
                        },
                        onBackPress = {
                            navigateBack()
                        }
                    )
                } ?: run {
                    currentScreen = Screen.HOME
                }
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
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_showhub_logo),
                        contentDescription = "ShowHub TV",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (update.isForceUpdate) "Обязательное обновление ShowHub TV v${update.versionName}" else "Доступно обновление ShowHub TV v${update.versionName}",
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

                    val isApkReady = remember(update.versionCode, isDownloadingUpdate) {
                        UpdateManager.isApkReady(activity, update.versionCode)
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
                    } else if (isApkReady) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "✓ Файл обновления уже загружен в фоне и готов к установке",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (UpdateManager.isPredownloading && !isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Фоновая загрузка файла обновления...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Normal
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
                                        lastInstallerLaunchTime = System.currentTimeMillis()
                                        val success = UpdateManager.downloadAndInstall(
                                            activity = activity,
                                            apkUrl = update.downloadUrl,
                                            targetVersionCode = update.versionCode
                                        ) { status, percent ->
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
                                } else if (isApkReady) {
                                    "Установить сейчас (готово)"
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
                                    right = if (!update.isForceUpdate) remindLaterFocusRequester else FocusRequester.Default
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

                        // Button 3: Remind Later (hidden when update is mandatory)
                        if (!update.isForceUpdate) {
                            Spacer(modifier = Modifier.width(14.dp))
                            Button(
                                onClick = {
                                    dismissedVersionCode = update.versionCode
                                    updateInfo = null
                                },
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

        // EXIT CONFIRMATION MODAL DIALOG
        if (showExitDialog) {
            val accent = LocalAccentColor.current
            val exitFocusRequester = remember { FocusRequester() }
            val cancelFocusRequester = remember { FocusRequester() }

            LaunchedEffect(showExitDialog) {
                repeat(8) {
                    delay(50)
                    try { cancelFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(440.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_showhub_logo),
                        contentDescription = "ShowHub TV",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Выход из ShowHub TV",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Вы действительно хотите закрыть приложение?",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.focusGroup()
                    ) {
                        Button(
                            onClick = {
                                showExitDialog = false
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(cancelFocusRequester)
                                .focusProperties {
                                    right = exitFocusRequester
                                    left = exitFocusRequester
                                    up = cancelFocusRequester
                                    down = cancelFocusRequester
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                            try { exitFocusRequester.requestFocus(); return@onKeyEvent true } catch (_: Exception) {}
                                        }
                                    }
                                    false
                                }
                        ) {
                            Text(
                                text = "Остаться",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                showExitDialog = false
                                activity.finish()
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color(0xFFE53935),
                                contentColor = Color.White,
                                focusedContentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(exitFocusRequester)
                                .focusProperties {
                                    left = cancelFocusRequester
                                    right = cancelFocusRequester
                                    up = exitFocusRequester
                                    down = exitFocusRequester
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                            try { cancelFocusRequester.requestFocus(); return@onKeyEvent true } catch (_: Exception) {}
                                        }
                                    }
                                    false
                                }
                        ) {
                            Text(
                                text = "Выйти",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // NEW EPISODE STACKED MODAL NOTIFICATIONS (Festive Deck)
        if (isEpisodeAlertVisible) {
            val totalAlerts = episodeAlerts.size
            val activeAlert = episodeAlerts.first()
            val historyManager = remember { com.example.tvmediaapp.data.history.WatchHistoryManager(activity) }
            val playFocusRequester = remember { FocusRequester() }
            val laterFocusRequester = remember { FocusRequester() }
            val checkboxFocusRequester = remember { FocusRequester() }
            var dontRemindAgain by remember { mutableStateOf(false) }

            val userAccent = com.example.tvmediaapp.ui.theme.LocalAccentColor.current
            val userFocus = com.example.tvmediaapp.ui.theme.LocalFocusColor.current

            val primaryCardTheme = remember(userAccent) {
                ModalCardTheme(
                    name = "SettingsAccent",
                    primaryColor = userAccent,
                    bgCardColor = Color(0xFF151923),
                    badgeBg = userAccent.copy(alpha = 0.22f),
                    badgeTextColor = userAccent,
                    buttonContentColor = Color.Black
                )
            }

            // Festive color themes: Amber/Yellow, Pink, Cyan, Green, Violet/Purple, Coral/Orange
            val cardThemes = remember {
                listOf(
                    // 0: Amber / Yellow (Желтая)
                    ModalCardTheme(
                        name = "Yellow",
                        primaryColor = Color(0xFFFBBF24),
                        bgCardColor = Color(0xFF231A05),
                        badgeBg = Color(0x33FBBF24),
                        badgeTextColor = Color(0xFFFDE68A),
                        buttonContentColor = Color(0xFF1E1400)
                    ),
                    // 1: Rose / Hot Pink (Розовая)
                    ModalCardTheme(
                        name = "Pink",
                        primaryColor = Color(0xFFF43F5E),
                        bgCardColor = Color(0xFF280A16),
                        badgeBg = Color(0x33F43F5E),
                        badgeTextColor = Color(0xFFFDA4AF),
                        buttonContentColor = Color(0xFF20010A)
                    ),
                    // 2: Electric Cyan / Sky Blue (Голубая)
                    ModalCardTheme(
                        name = "Cyan",
                        primaryColor = Color(0xFF06B6D4),
                        bgCardColor = Color(0xFF041E28),
                        badgeBg = Color(0x3306B6D4),
                        badgeTextColor = Color(0xFF67E8F9),
                        buttonContentColor = Color(0xFF001720)
                    ),
                    // 3: Bright Emerald / Green (Зеленая)
                    ModalCardTheme(
                        name = "Green",
                        primaryColor = Color(0xFF10B981),
                        bgCardColor = Color(0xFF062217),
                        badgeBg = Color(0x3310B981),
                        badgeTextColor = Color(0xFF6EE7B7),
                        buttonContentColor = Color(0xFF011C11)
                    ),
                    // 4: Electric Violet / Purple (Фиолетовая)
                    ModalCardTheme(
                        name = "Purple",
                        primaryColor = Color(0xFF8B5CF6),
                        bgCardColor = Color(0xFF1D0E34),
                        badgeBg = Color(0x338B5CF6),
                        badgeTextColor = Color(0xFFC4B5FD),
                        buttonContentColor = Color(0xFF150428)
                    ),
                    // 5: Vivid Tangerine / Coral Orange (Оранжевая)
                    ModalCardTheme(
                        name = "Orange",
                        primaryColor = Color(0xFFF97316),
                        bgCardColor = Color(0xFF281205),
                        badgeBg = Color(0x33F97316),
                        badgeTextColor = Color(0xFFFDBA74),
                        buttonContentColor = Color(0xFF200900)
                    )
                )
            }

            fun getThemeForAlert(seriesId: String): ModalCardTheme {
                val seed = kotlin.math.abs(seriesId.hashCode())
                return cardThemes[seed % cardThemes.size]
            }

            val activeTheme = getThemeForAlert(activeAlert.movie.id)

            BackHandler {
                if (dontRemindAgain) {
                    historyManager.setSeriesReminderMuted(activeAlert.movie.id, activeAlert.movie.title, true)
                }
                historyManager.snoozeSeriesReminder(activeAlert.movie.id, activeAlert.movie.title, hours = 24)
                homeViewModel.dismissCurrentEpisodeAlert()
            }

            LaunchedEffect(activeAlert.movie.id) {
                dontRemindAgain = false
                for (i in 0 until 5) {
                    delay(60)
                    try {
                        playFocusRequester.requestFocus()
                        break
                    } catch (_: Exception) {}
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                // Outer Deck Container with Stacked Card Visuals
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Peek Card #3 (deep background, offset to top-right)
                    if (totalAlerts >= 3) {
                        val alert3 = episodeAlerts[2]
                        val theme3 = getThemeForAlert(alert3.movie.id)
                        Box(
                            modifier = Modifier
                                .offset(x = 24.dp, y = (-20).dp)
                                .graphicsLayer {
                                    rotationZ = 4.2f
                                    scaleX = 0.92f
                                    scaleY = 0.92f
                                }
                                .alpha(0.70f)
                                .width(560.dp)
                                .height(280.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(theme3.bgCardColor)
                                .border(2.dp, theme3.primaryColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme3.badgeBg)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = alert3.movie.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme3.badgeTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "3 из $totalAlerts",
                                    fontSize = 11.sp,
                                    color = theme3.primaryColor.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Peek Card #2 (middle background, offset to top-left)
                    if (totalAlerts >= 2) {
                        val alert2 = episodeAlerts[1]
                        val theme2 = getThemeForAlert(alert2.movie.id)
                        Box(
                            modifier = Modifier
                                .offset(x = (-24).dp, y = (-14).dp)
                                .graphicsLayer {
                                    rotationZ = -3.8f
                                    scaleX = 0.96f
                                    scaleY = 0.96f
                                }
                                .alpha(0.85f)
                                .width(560.dp)
                                .height(280.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(theme2.bgCardColor)
                                .border(2.dp, theme2.primaryColor.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme2.badgeBg)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = alert2.movie.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme2.badgeTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "2 из $totalAlerts",
                                    fontSize = 11.sp,
                                    color = theme2.primaryColor.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Active Top Card (interactive, focused)
                    Column(
                        modifier = Modifier
                            .width(560.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        activeTheme.bgCardColor,
                                        Color(0xFF131722)
                                    )
                                )
                            )
                            .border(2.dp, activeTheme.primaryColor, RoundedCornerShape(18.dp))
                            .padding(24.dp)
                            .focusGroup()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                                ) {
                                    if (dontRemindAgain) {
                                        historyManager.setSeriesReminderMuted(activeAlert.movie.id, activeAlert.movie.title, true)
                                    }
                                    historyManager.snoozeSeriesReminder(activeAlert.movie.id, activeAlert.movie.title, hours = 24)
                                    homeViewModel.dismissCurrentEpisodeAlert()
                                    true
                                } else {
                                    false
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(activeTheme.badgeBg)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "ВЫШЛА НОВАЯ СЕРИЯ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.primaryColor,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            if (totalAlerts > 1) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "1 из $totalAlerts",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Poster Cover + Title & Info Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Poster Image
                            Box(
                                modifier = Modifier
                                    .width(115.dp)
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, activeTheme.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = activeAlert.movie.posterUrl,
                                    contentDescription = activeAlert.movie.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = activeAlert.movie.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val episodeCountText = if (activeAlert.newCount > 1) "+${activeAlert.newCount} новых серий" else "Новая серия"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(activeTheme.primaryColor.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = episodeCountText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.primaryColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "В отслеживаемом сериале появились свежие серии. Перейти к просмотру сейчас?",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Checkbox button: "Больше не напоминать об этом сериале"
                        Button(
                            onClick = {
                                dontRemindAgain = !dontRemindAgain
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.02f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (dontRemindAgain) activeTheme.primaryColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                focusedContainerColor = userFocus.copy(alpha = 0.20f),
                                contentColor = Color.LightGray,
                                focusedContentColor = Color.White
                            ),
                            border = ButtonDefaults.border(
                                border = Border(BorderStroke(1.dp, if (dontRemindAgain) activeTheme.primaryColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f))),
                                focusedBorder = Border(BorderStroke(2.dp, userFocus))
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(checkboxFocusRequester)
                                .focusProperties {
                                    down = playFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            1.5.dp,
                                            if (dontRemindAgain) activeTheme.primaryColor else Color.White.copy(alpha = 0.6f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .background(if (dontRemindAgain) activeTheme.primaryColor else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dontRemindAgain) {
                                        Text(
                                            text = "✓",
                                            color = activeTheme.buttonContentColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Больше не напоминать об этом сериале",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Buttons Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (dontRemindAgain) {
                                        historyManager.setSeriesReminderMuted(activeAlert.movie.id, activeAlert.movie.title, true)
                                    }
                                    historyManager.clearNewEpisodes(activeAlert.movie.id)
                                    historyManager.snoozeSeriesReminder(activeAlert.movie.id, activeAlert.movie.title, hours = 24)
                                    val targetMovie = activeAlert.movie
                                    homeViewModel.dismissCurrentEpisodeAlert()
                                    navigateTo(Screen.DETAILS, movie = targetMovie)
                                },
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.05f),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = activeTheme.primaryColor,
                                    focusedContainerColor = userFocus,
                                    contentColor = activeTheme.buttonContentColor,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    border = Border.None,
                                    focusedBorder = Border(BorderStroke(2.dp, userFocus))
                                ),
                                modifier = Modifier
                                    .height(38.dp)
                                    .focusRequester(playFocusRequester)
                                    .focusProperties {
                                        up = checkboxFocusRequester
                                        right = laterFocusRequester
                                    }
                            ) {
                                Text(
                                    text = "Смотреть серию",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (dontRemindAgain) {
                                        historyManager.setSeriesReminderMuted(activeAlert.movie.id, activeAlert.movie.title, true)
                                    }
                                    historyManager.snoozeSeriesReminder(activeAlert.movie.id, activeAlert.movie.title, hours = 24)
                                    homeViewModel.dismissCurrentEpisodeAlert()
                                },
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.05f),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = userFocus.copy(alpha = 0.25f),
                                    contentColor = Color.LightGray,
                                    focusedContentColor = Color.White
                                ),
                                border = ButtonDefaults.border(
                                    border = Border.None,
                                    focusedBorder = Border(BorderStroke(2.dp, userFocus))
                                ),
                                modifier = Modifier
                                    .height(38.dp)
                                    .focusRequester(laterFocusRequester)
                                    .focusProperties {
                                        up = checkboxFocusRequester
                                        left = playFocusRequester
                                    }
                            ) {
                                Text(
                                    text = if (totalAlerts > 1) "Следующий (${totalAlerts - 1})" else "Позже",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
