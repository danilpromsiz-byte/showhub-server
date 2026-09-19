package com.example.tvmediaapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
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
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.example.tvmediaapp.ui.screens.schedule.ScheduleCalendarScreen
import com.example.tvmediaapp.ui.screens.search.SearchScreen
import com.example.tvmediaapp.ui.screens.settings.SettingsScreen
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
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

    fun navigateBack() {
        // Pop any trailing redundant states matching the current view
        while (backStack.isNotEmpty() && (backStack.last().screen == currentScreen && (backStack.last().movie == null || backStack.last().movie?.id == selectedMovie?.id))) {
            backStack.removeAt(backStack.size - 1)
        }
        if (backStack.isNotEmpty()) {
            var prev = backStack.removeAt(backStack.size - 1)
            while (prev.screen == currentScreen && (prev.movie == null || prev.movie?.id == selectedMovie?.id) && backStack.isNotEmpty()) {
                prev = backStack.removeAt(backStack.size - 1)
            }
            if (prev.screen == currentScreen && (prev.movie == null || prev.movie?.id == selectedMovie?.id)) {
                currentScreen = Screen.HOME
                selectedMovie = null
                SessionManager.clearSession(activity)
                homeViewModel.refreshCatalog()
                return
            }
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
                homeViewModel.refreshCatalog()
            }
        } else {
            if (currentScreen != Screen.HOME) {
                currentScreen = Screen.HOME
                selectedMovie = null
                SessionManager.clearSession(activity)
                homeViewModel.refreshCatalog()
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
            homeViewModel.refreshCatalog()
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
                if (isUserClick || info.versionCode != dismissedVersionCode) {
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

    // Auto-check on launch
    LaunchedEffect(Unit) {
        triggerUpdateCheck()
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

    val isUpdateDialogVisible = updateInfo?.let { it.versionCode > activity.getInstalledVersionCode() } == true

    // Hardware Back button handling for Android TV remotes
    BackHandler(enabled = isUpdateDialogVisible) {
        updateInfo?.let { dismissedVersionCode = it.versionCode }
        updateInfo = null
    }

    BackHandler(enabled = showExitDialog) {
        showExitDialog = false
    }

    BackHandler(enabled = !isUpdateDialogVisible && !showExitDialog) {
        navigateBack()
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
                    canFocus = !isUpdateDialogVisible && !showExitDialog
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
                        verticalAlignment = Alignment.CenterVertically
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
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
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
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
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
    }
}
