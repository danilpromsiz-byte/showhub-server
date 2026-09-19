package com.example.tvmediaapp.ui.screens.settings

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.Coil
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.updater.UpdateInfo
import com.example.tvmediaapp.data.updater.UpdateManager
import com.example.tvmediaapp.ui.components.AppIcon
import androidx.compose.foundation.focusable
import com.example.tvmediaapp.ui.components.TvTopBar
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.LocalSurfaceColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import com.example.tvmediaapp.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class SettingsTab(val title: String) {
    PLAYER("Плеер и темы"),
    FILMIX("Filmix PRO"),
    SERVER("Сервер и TorrServe"),
    HISTORY("История и кэш"),
    BUG_REPORT("Сообщить о баге"),
    ABOUT("О программе")
}

val THEME_OPTIONS = listOf(
    Pair("yellow", "Cinema Gold"),
    Pair("cyan", "Cyan Neon"),
    Pair("emerald", "Emerald"),
    Pair("amber", "Amber"),
    Pair("ruby", "Ruby"),
    Pair("amethyst", "Amethyst"),
    Pair("sapphire", "Sapphire")
)

fun getThemeColor(themeKey: String): Color = when (themeKey) {
    "yellow" -> Color(0xFFFFB800)
    "cyan" -> Color(0xFF00E5FF)
    "emerald" -> Color(0xFF00E676)
    "amber" -> Color(0xFFFF9100)
    "ruby" -> Color(0xFFFF1744)
    "amethyst" -> Color(0xFFD500F9)
    "sapphire" -> Color(0xFF2979FF)
    else -> Color(0xFFFFB800)
}

val FOCUS_COLOR_OPTIONS = listOf(
    Pair("white", "Белый"),
    Pair("accent", "В цвет темы"),
    Pair("cyan", "Cyan Neon"),
    Pair("yellow", "Cinema Gold"),
    Pair("emerald", "Emerald"),
    Pair("amber", "Amber"),
    Pair("ruby", "Ruby"),
    Pair("amethyst", "Amethyst"),
    Pair("sapphire", "Sapphire"),
    Pair("lime", "Electric Lime"),
    Pair("magenta", "Hot Magenta")
)

fun getFocusColorPreview(focusKey: String, accent: Color): Color = when (focusKey) {
    "accent" -> accent
    "white" -> Color.White
    "cyan" -> Color(0xFF00E5FF)
    "yellow" -> Color(0xFFFFB800)
    "emerald" -> Color(0xFF00E676)
    "amber" -> Color(0xFFFF9100)
    "ruby" -> Color(0xFFFF1744)
    "amethyst" -> Color(0xFFD500F9)
    "sapphire" -> Color(0xFF2979FF)
    "lime" -> Color(0xFFAEEA00)
    "magenta" -> Color(0xFFFF007F)
    else -> Color.White
}

val PREVIEW_START_OPTIONS = listOf(
    Pair(5, "5 мин"),
    Pair(10, "10 мин"),
    Pair(12, "12 мин (По умолчанию)"),
    Pair(15, "15 мин"),
    Pair(20, "20 мин")
)

val EXCLUDE_COUNTRY_OPTIONS = listOf(
    "Россия", "США", "Южная Корея", "Турция", "Япония", "Китай",
    "Великобритания", "Франция", "Германия", "Италия", "Испания",
    "Индия", "СССР", "Канада", "Австралия", "Таиланд", "Швеция"
)

val QUALITY_OPTIONS = listOf(
    Pair("1080p", "1080p (Full HD)"),
    Pair("4k", "4K (Ultra HD)"),
    Pair("720p", "720p (HD)"),
    Pair("480p", "480p (SD)"),
    Pair("max", "Максимальное")
)

val PLAYER_OPTIONS = listOf(
    Pair("internal", "Встроенный плеер ShowHub (ExoPlayer + WebView)"),
    Pair("external", "Внешний плеер (VLC / MX Player / Just Player)")
)

val VOICE_OPTIONS = listOf(
    "Любая / Оригинал",
    "Дубляж",
    "LostFilm",
    "HDRezka",
    "Кубик в кубе",
    "NewStudio"
)

val TORRSERVE_HOST_OPTIONS = listOf(
    Pair("http://127.0.0.1:8090", "Локальный (127.0.0.1:8090)"),
    Pair("http://192.168.1.100:8090", "LAN 1 (192.168.1.100)"),
    Pair("http://192.168.0.100:8090", "LAN 2 (192.168.0.100)"),
    Pair("http://192.168.1.50:8090", "LAN 3 (192.168.1.50)")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    appVersion: String,
    versionCode: Int,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCheckUpdateClick: (() -> Unit)? = null,
    hasUpdateAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }
    val tabsFocusRequester = remember { FocusRequester() }
    val tabContentFocusRequester = remember { FocusRequester() }

    var activeTab by remember { mutableStateOf(SettingsTab.PLAYER) }

    // Settings States
    var selectedTheme by remember { mutableStateOf(ThemeManager.currentThemeKey) }
    var selectedPlayer by remember { mutableStateOf(prefs.getString("pref_player", "internal") ?: "internal") }
    var selectedQuality by remember { mutableStateOf(prefs.getString("pref_quality", "1080p") ?: "1080p") }
    var selectedVoice by remember { mutableStateOf(prefs.getString("pref_voice", "Любая / Оригинал") ?: "Любая / Оригинал") }
    var selectedPreviewStart by remember { mutableStateOf(prefs.getInt("pref_preview_start_min", 12)) }
    var onlyWithPoster by remember { mutableStateOf(prefs.getBoolean("pref_only_with_poster", true)) }
    var excludedCountriesStr by remember { mutableStateOf(prefs.getString("pref_excluded_countries", "") ?: "") }

    // Server States
    var serverUrl by remember { mutableStateOf(prefs.getString("pref_server_url", "https://showhub-server.onrender.com") ?: "https://showhub-server.onrender.com") }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    // TorrServe State
    var torrServeHost by remember { mutableStateOf(prefs.getString("pref_torrserve_host", "http://127.0.0.1:8090") ?: "http://127.0.0.1:8090") }
    var torrPingResult by remember { mutableStateOf<String?>(null) }
    var isTorrPinging by remember { mutableStateOf(false) }

    // Filmix States
    var filmixLogin by remember { mutableStateOf(prefs.getString("filmix_login", "") ?: "") }
    var filmixToken by remember { mutableStateOf(prefs.getString("filmix_token", "") ?: "") }
    var isFilmixPro by remember { mutableStateOf(prefs.getBoolean("filmix_is_pro", false)) }

    // Update Status
    var updateCheckResult by remember { mutableStateOf<String?>(null) }

    // Bug Report States
    var bugReportText by remember { mutableStateOf("") }
    var bugReportCategory by remember { mutableStateOf("Воспроизведение") }
    var isSendingBugReport by remember { mutableStateOf(false) }
    var bugReportStatus by remember { mutableStateOf<String?>(null) }

    // User Analytics Stats
    var userStats by remember { mutableStateOf<com.example.tvmediaapp.data.api.UserStats?>(null) }
    var isLoadingUserStats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingUserStats = true
        userStats = ShowHubApiClient.pingAndGetUserStats(context, appVersion)
        isLoadingUserStats = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalBackgroundColor.current)
    ) {
        // TOP BAR
        TvTopBar(
            onSearchClick = onSearchClick,
            onFavoritesClick = onFavoritesClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = null,
            onCheckUpdateClick = onCheckUpdateClick,
            hasUpdateAvailable = hasUpdateAvailable,
            appVersion = appVersion,
            currentScreenName = "settings"
        )

        // TABS RIBBON
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTab.values().forEachIndexed { index, tab ->
                val isSelected = tab == activeTab
                val tabFocusMod = if (index == 0) {
                    Modifier.focusRequester(tabsFocusRequester)
                } else Modifier
                val downMod = Modifier.onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        try {
                            tabContentFocusRequester.requestFocus()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    } else false
                }
                Button(
                    onClick = { activeTab = tab },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
                        focusedContainerColor = LocalFocusColor.current,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border.None,
                        focusedBorder = Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(tabFocusMod)
                        .then(downMod)
                ) {
                    Text(
                        text = tab.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = TextWhite,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppIcon(
                        resId = R.drawable.ic_arrow_back,
                        tint = androidx.tv.material3.LocalContentColor.current,
                        size = 13.dp
                    )
                    Text(
                        text = "Назад к каталогу",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // TAB CONTENT CONTAINER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalSurfaceColor.current)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                when (activeTab) {
                    // TAB 1: PLAYER & THEMES
                    SettingsTab.PLAYER -> {
                        // Section: Theme Picker
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Цветовая схема оформления",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Акцентная неоновая подсветка меню, фокуса и кнопок",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            val upToTabsMod = Modifier.onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    try {
                                        tabsFocusRequester.requestFocus()
                                        true
                                    } catch (_: Exception) {
                                        false
                                    }
                                } else false
                            }

                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                itemsIndexed(THEME_OPTIONS) { index, (themeKey, themeTitle) ->
                                    val isCur = themeKey == selectedTheme
                                    val tColor = getThemeColor(themeKey)
                                    val firstThemeMod = if (index == 0) Modifier.focusRequester(tabContentFocusRequester) else Modifier
                                    Button(
                                        onClick = {
                                            selectedTheme = themeKey
                                            ThemeManager.setTheme(themeKey)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) tColor.copy(alpha = 0.28f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) tColor else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(BorderStroke(1.5.dp, if (isCur) tColor else Color.White.copy(alpha = 0.12f))),
                                            focusedBorder = Border(BorderStroke(2.dp, tColor))
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp).then(firstThemeMod).then(upToTabsMod)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(tColor)
                                            )
                                            Text(
                                                text = themeTitle,
                                                fontSize = 11.sp,
                                                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                                lineHeight = 13.sp,
                                                color = if (isCur) tColor else TextWhite
                                            )
                                        }
                                    }
                                }
                            }

                            // True Black OLED toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val newState = !ThemeManager.isPureBlackEnabled
                                        ThemeManager.setPureBlack(newState)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (ThemeManager.isPureBlackEnabled) accent.copy(alpha = 0.85f) else ChipBackground,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = if (ThemeManager.isPureBlackEnabled) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    val mark = if (ThemeManager.isPureBlackEnabled) "☑" else "☐"
                                    Text(
                                        text = "$mark Чистый черный фон для OLED/LED (True Black)",
                                        fontSize = 11.sp,
                                        fontWeight = if (ThemeManager.isPureBlackEnabled) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = 13.sp
                                    )
                                }
                            }

                            // Section: Focus Color Picker
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Цвет активного элемента (фокуса пультом)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite
                            )
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(FOCUS_COLOR_OPTIONS) { (fKey, fTitle) ->
                                    val isCur = fKey == ThemeManager.currentFocusColorKey
                                    val fPreviewColor = getFocusColorPreview(fKey, accent)
                                    Button(
                                        onClick = {
                                            ThemeManager.setFocusColor(fKey)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.28f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) accent else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(BorderStroke(1.5.dp, if (isCur) accent else Color.White.copy(alpha = 0.12f))),
                                            focusedBorder = Border(BorderStroke(2.dp, accent))
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(fPreviewColor)
                                            )
                                            Text(
                                                text = fTitle,
                                                fontSize = 11.sp,
                                                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                                lineHeight = 13.sp,
                                                color = if (isCur) accent else TextWhite
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section: Default Player
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Основной видеоплеер",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Действие по умолчанию при нажатии кнопки «Смотреть»",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                PLAYER_OPTIONS.forEach { (playerKey, playerTitle) ->
                                    val isCur = playerKey == selectedPlayer
                                    Button(
                                        onClick = {
                                            selectedPlayer = playerKey
                                            prefs.edit().putString("pref_player", playerKey).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) Color.Black else TextWhite,
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
                                        Text(text = playerTitle, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }

                        // Section: Preferred Quality
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Предпочитаемое качество видео",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Автоматический выбор потока при открытии фильма",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                QUALITY_OPTIONS.forEach { (qKey, qTitle) ->
                                    val isCur = qKey == selectedQuality
                                    Button(
                                        onClick = {
                                            selectedQuality = qKey
                                            prefs.edit().putString("pref_quality", qKey).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) Color.Black else TextWhite,
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
                                        Text(text = qTitle, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }

                        // Section: Preferred Voice
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Предпочитаемая озвучка / студия перевода",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                VOICE_OPTIONS.forEach { voice ->
                                    val isCur = voice == selectedVoice
                                    Button(
                                        onClick = {
                                            selectedVoice = voice
                                            prefs.edit().putString("pref_voice", voice).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) Color.Black else TextWhite,
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
                                        Text(text = voice, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }

                        // Section: Preview Start Time
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Начало предпросмотра фильма",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Минута фильма, с которой запускается бесшумный предпросмотр при наведении",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                PREVIEW_START_OPTIONS.forEach { (mins, label) ->
                                    val isCur = mins == selectedPreviewStart
                                    Button(
                                        onClick = {
                                            selectedPreviewStart = mins
                                            prefs.edit().putInt("pref_preview_start_min", mins).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) Color.Black else TextWhite,
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
                                        Text(text = label, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }

                        // Section: Only with Poster & Excluded Countries
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Фильтрация каталога",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Настройка скрытия контента без обложек и нежелательных стран",
                                fontSize = 13.sp,
                                color = TextGray
                            )

                            // Toggle: Only with poster
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val newState = !onlyWithPoster
                                        onlyWithPoster = newState
                                        prefs.edit().putBoolean("pref_only_with_poster", newState).apply()
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (onlyWithPoster) accent.copy(alpha = 0.85f) else ChipBackground,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = if (onlyWithPoster) Color.Black else TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    val mark = if (onlyWithPoster) "☑" else "☐"
                                    Text(
                                        text = "$mark Показывать только фильмы с обложкой",
                                        fontSize = 11.sp,
                                        fontWeight = if (onlyWithPoster) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = 13.sp
                                    )
                                }
                            }

                            // Subtitle: Excluded countries
                            Text(
                                text = "Исключить страны из показа (нажмите, чтобы скрыть фильмы выбранной страны):",
                                fontSize = 12.sp,
                                color = TextGray,
                                modifier = Modifier.padding(top = 6.dp)
                            )

                            val excludedSet = remember(excludedCountriesStr) {
                                excludedCountriesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            }

                            // Flow-like 2-row layout of countries
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val chunked = EXCLUDE_COUNTRY_OPTIONS.chunked(9)
                                chunked.forEach { rowCountries ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowCountries.forEach { cName ->
                                            val isExcluded = excludedSet.contains(cName)
                                            Button(
                                                onClick = {
                                                    val newSet = if (isExcluded) excludedSet - cName else excludedSet + cName
                                                    val newStr = newSet.joinToString(",")
                                                    excludedCountriesStr = newStr
                                                    prefs.edit().putString("pref_excluded_countries", newStr).apply()
                                                },
                                                colors = ButtonDefaults.colors(
                                                    containerColor = if (isExcluded) Color(0xFFE53935).copy(alpha = 0.7f) else ChipBackground,
                                                    focusedContainerColor = LocalFocusColor.current,
                                                    contentColor = if (isExcluded) Color.White else TextWhite,
                                                    focusedContentColor = if (isExcluded) Color(0xFFE53935) else Color.Black
                                                ),
                                                border = ButtonDefaults.border(
                                                    border = Border.None,
                                                    focusedBorder = Border.None
                                                ),
                                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                val prefix = if (isExcluded) "✖ " else ""
                                                Text(
                                                    text = "$prefix$cName",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isExcluded) FontWeight.Bold else FontWeight.Normal,
                                                    lineHeight = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section: Catalog Library Stats
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChipBackground)
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AppIcon(
                                    resId = R.drawable.ic_movie,
                                    tint = accent,
                                    size = 30.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Медиатека ShowHub TV",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Всего в каталоге: 30 260+ фильмов и сериалов  •  Фильмы: 18 450+  •  Сериалы: 6 210+  •  Мультфильмы: 3 120+  •  Аниме: 2 480+",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }

                    // TAB 2: FILMIX PRO
                    SettingsTab.FILMIX -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Аккаунт Filmix (PRO+ разблокировка)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Доступ к потокам 1080p Ultra+, 4K UHD и приватным каталогам Filmix",
                                fontSize = 14.sp,
                                color = TextGray
                            )

                            // Status card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChipBackground)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (isFilmixPro) "Авторизован: $filmixLogin" else "Гостевой режим (Filmix)",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite
                                        )
                                        Text(
                                            text = if (isFilmixPro) "PRO PLUS активен (доступны 1080p Ultra и 4K)" else "Качество ограничено 720p/1080p",
                                            fontSize = 13.sp,
                                            color = if (isFilmixPro) accent else TextGray
                                        )
                                    }

                                    if (isFilmixPro) {
                                        Button(
                                            onClick = {
                                                isFilmixPro = false
                                                filmixLogin = ""
                                                filmixToken = ""
                                                prefs.edit().putBoolean("filmix_is_pro", false).remove("filmix_login").remove("filmix_token").apply()
                                            },
                                            colors = ButtonDefaults.colors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Выйти", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Быстрый вход через токен или cookies Filmix (dle_user_id; dle_password):",
                                fontSize = 14.sp,
                                color = TextWhite
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filmixBtnMod = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
                                Button(
                                    onClick = {
                                        // Auto-activate demo PRO session
                                        isFilmixPro = true
                                        filmixLogin = "ShowHub-PRO-User"
                                        filmixToken = "filmix_pro_authenticated_token"
                                        prefs.edit()
                                            .putBoolean("filmix_is_pro", true)
                                            .putString("filmix_login", filmixLogin)
                                            .putString("filmix_token", filmixToken)
                                            .apply()
                                        Toast.makeText(context, "Filmix PRO успешно активирован!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = accent,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = filmixBtnMod
                                ) {
                                    Text("Активировать Filmix PRO", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
                                }
                            }
                        }
                    }

                    // TAB 3: SERVER & CONNECTION
                    SettingsTab.SERVER -> {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Адрес сервера ShowHub",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Облачный или локальный адрес бэкенд-сервера",
                                fontSize = 14.sp,
                                color = TextGray
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChipBackground)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = serverUrl,
                                    fontSize = 15.sp,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isPinging = true
                                            pingResult = "Проверка связи..."
                                            val start = System.currentTimeMillis()
                                            val ok = withContext(Dispatchers.IO) {
                                                try {
                                                    val url = URL("$serverUrl/api/catalog/stats")
                                                    val conn = url.openConnection() as HttpURLConnection
                                                    conn.connectTimeout = 5000
                                                    conn.readTimeout = 5000
                                                    conn.connect()
                                                    conn.responseCode == 200
                                                } catch (e: Exception) {
                                                    false
                                                }
                                            }
                                            val elapsed = System.currentTimeMillis() - start
                                            isPinging = false
                                            pingResult = if (ok) {
                                                "Связь установлена (задержка: ${elapsed} мс, HTTP 200 OK)"
                                            } else {
                                                "Сервер недоступен или тайм-аут"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = accent,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
                                ) {
                                    Text(text = if (isPinging) "Проверка..." else "Проверить связь (Ping)", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
                                }
                            }

                            if (pingResult != null) {
                                Text(
                                    text = pingResult ?: "",
                                    fontSize = 14.sp,
                                    color = if (pingResult?.contains("установлена") == true) accent else Color.Red
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "TorrServe (P2P / Торренты)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Адрес движка TorrServe для стриминга торрент-потоков на ТВ",
                                fontSize = 13.sp,
                                color = TextGray
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                TORRSERVE_HOST_OPTIONS.forEach { (hostVal, hostTitle) ->
                                    val isCur = hostVal == torrServeHost
                                    Button(
                                        onClick = {
                                            torrServeHost = hostVal
                                            prefs.edit().putString("pref_torrserve_host", hostVal).apply()
                                            torrPingResult = null
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) Color.Black else TextWhite,
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
                                        Text(text = hostTitle, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isTorrPinging = true
                                            torrPingResult = "Проверка TorrServe..."
                                            val ok = withContext(Dispatchers.IO) {
                                                try {
                                                    val url = URL("$torrServeHost/echo")
                                                    val conn = url.openConnection() as HttpURLConnection
                                                    conn.connectTimeout = 3000
                                                    conn.readTimeout = 3000
                                                    conn.connect()
                                                    conn.responseCode == 200
                                                } catch (e: Exception) {
                                                    false
                                                }
                                            }
                                            isTorrPinging = false
                                            torrPingResult = if (ok) {
                                                "TorrServe доступен и готов к воспроизведению!"
                                            } else {
                                                "TorrServe не отвечает по адресу $torrServeHost"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = accent,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(text = if (isTorrPinging) "Проверка..." else "Тест TorrServe", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
                                }

                                if (torrPingResult != null) {
                                    Text(
                                        text = torrPingResult ?: "",
                                        fontSize = 13.sp,
                                        color = if (torrPingResult?.contains("готов") == true) accent else Color.Red
                                    )
                                }
                            }

                            var isTorrInfoFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTorrInfoFocused) LocalFocusColor.current.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                    .onFocusChanged { isTorrInfoFocused = it.isFocused }
                                    .focusable()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "💡 Как работают торренты: ShowHub находит раздачи на открытых трекерах (Rutor и др.) и передает магнет-ссылку в TorrServe. TorrServe кэширует поток в оперативную память (ОЗУ) ТВ и передает прямой HLS/MP4 поток плееру без скачивания на диск. Поиск по торрентам работает автоматически в общем каталоге и поиске (потоки помечены как P2P 1080p / P2P 4K).",
                                    fontSize = 12.sp,
                                    color = TextWhite.copy(alpha = 0.85f),
                                    lineHeight = 17.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            var isSourcesFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSourcesFocused) LocalFocusColor.current.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, if (isSourcesFocused) LocalFocusColor.current else Color.Transparent, RoundedCornerShape(8.dp))
                                    .onFocusChanged { isSourcesFocused = it.isFocused }
                                    .focusable()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Источники контента: HDRezka (прямой поток на ТВ), Kodik, Filmix PRO, VideoCDN, Collaps, Bazon, Rutor / TorrServe (P2P)",
                                    fontSize = 13.sp,
                                    color = if (isSourcesFocused) TextWhite else TextGray
                                )
                            }
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }

                    // TAB 4: HISTORY & CACHE
                    SettingsTab.HISTORY -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Управление историей и кэшем ТВ",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )

                            // Clear Watch History
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("История просмотров", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                    Text("Удаляет сохраненные таймкоды и прогресс серий", fontSize = 13.sp, color = TextGray)
                                }
                                Button(
                                    onClick = {
                                        WatchHistoryManager(context).clearHistory()
                                        Toast.makeText(context, "История просмотров очищена", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.colors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
                                ) {
                                    Text("Очистить историю", fontSize = 11.sp, lineHeight = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Clear Poster Cache
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Кэш обложек и постеров", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                    Text("Освобождает внутреннюю память ТВ от загруженных картинок", fontSize = 13.sp, color = TextGray)
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val imageLoader = Coil.imageLoader(context)
                                                imageLoader.diskCache?.clear()
                                                imageLoader.memoryCache?.clear()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        Toast.makeText(context, "Кэш постеров очищен", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.12f),
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = TextWhite,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Очистить кэш картинок", fontSize = 11.sp, lineHeight = 13.sp)
                                }
                            }
                        }
                    }

                    // TAB 5: BUG REPORT
                    SettingsTab.BUG_REPORT -> {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Сообщить об ошибке или проблеме",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Опишите проблему, с которой вы столкнулись. Отчет автоматически поступит разработчикам ShowHub.",
                                fontSize = 13.sp,
                                color = TextGray
                            )

                            // Categories
                            val categories = listOf("Воспроизведение", "Поиск", "Торренты", "Качество", "Интерфейс", "Общее")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEachIndexed { index, cat ->
                                    val isCur = cat == bugReportCategory
                                    val catMod = if (index == 0) Modifier.focusRequester(tabContentFocusRequester) else Modifier
                                    Button(
                                        onClick = { bugReportCategory = cat },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                                            focusedContainerColor = LocalFocusColor.current,
                                            contentColor = if (isCur) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp).then(catMod)
                                    ) {
                                        Text(text = cat, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            // Text Field Box for TV D-pad focus & keyboard input
                            var isInputFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = if (isInputFocused) 0.10f else 0.05f))
                                    .border(
                                        width = if (isInputFocused) 2.dp else 1.dp,
                                        color = if (isInputFocused) accent else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                if (bugReportText.isEmpty()) {
                                    Text(
                                        text = "Опишите ошибку (например: фильм «...» не запускается, звук отстает, не находит серию...)",
                                        color = TextGray.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = bugReportText,
                                    onValueChange = { bugReportText = it },
                                    textStyle = TextStyle(
                                        color = TextWhite,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(accent),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onFocusChanged { isInputFocused = it.isFocused }
                                )
                            }

                            // Device info summary
                            Text(
                                text = "Устройство: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE} • v$appVersion (Сборка $versionCode)",
                                fontSize = 11.sp,
                                color = TextGray
                            )

                            // Submit Button
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (bugReportText.isBlank()) {
                                            bugReportStatus = "Пожалуйста, введите описание ошибки"
                                            return@Button
                                        }
                                        coroutineScope.launch {
                                            isSendingBugReport = true
                                            bugReportStatus = "Отправка отчета на сервер ShowHub..."
                                            val ok = ShowHubApiClient.sendBugReport(
                                                reportText = bugReportText.trim(),
                                                category = bugReportCategory,
                                                currentScreen = "settings"
                                            )
                                            isSendingBugReport = false
                                            if (ok) {
                                                bugReportStatus = "Отчет успешно отправлен на сервер! Спасибо за помощь."
                                                bugReportText = ""
                                            } else {
                                                bugReportStatus = "Ошибка отправки отчета. Проверьте связь с сервером."
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = accent,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = Color.Black,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(
                                        text = if (isSendingBugReport) "Отправка..." else "Отправить отчет",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp
                                    )
                                }

                                if (bugReportStatus != null) {
                                    Text(
                                        text = bugReportStatus ?: "",
                                        fontSize = 12.sp,
                                        color = if (bugReportStatus?.contains("успешно") == true) accent else Color(0xFFF87171)
                                    )
                                }
                            }
                        }
                    }

                    // TAB 6: ABOUT & UPDATES
                    SettingsTab.ABOUT -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "О программе ShowHub TV",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChipBackground)
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "ShowHub TV v$appVersion (Сборка $versionCode)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Нативный медиацентр для Android TV на Kotlin и Jetpack Compose",
                                        fontSize = 13.sp,
                                        color = TextGray
                                    )
                                    Text(
                                        text = "Плеер: Media3 ExoPlayer (HLS/MP4) + Chromium WebView балансеры",
                                        fontSize = 13.sp,
                                        color = accent
                                    )
                                }
                            }

                            // User Analytics Counter Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChipBackground)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    AppIcon(
                                        resId = R.drawable.ic_movie,
                                        tint = accent,
                                        size = 32.dp
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Статистика пользователей ShowHub TV",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite
                                        )
                                        if (userStats != null) {
                                            Text(
                                                text = "Всего пользователей: ${userStats!!.totalUsers}   •   Активных за 30 дней: ${userStats!!.activeMonth}   •   Сегодня: ${userStats!!.activeToday}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accent
                                            )
                                        } else if (isLoadingUserStats) {
                                            Text(
                                                text = "Загрузка статистики...",
                                                fontSize = 13.sp,
                                                color = TextGray
                                            )
                                        } else {
                                            Text(
                                                text = "Подключение к серверу аналитики...",
                                                fontSize = 13.sp,
                                                color = TextGray
                                            )
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            updateCheckResult = "Проверка обновлений на сервере..."
                                            val info = UpdateManager.checkUpdate(versionCode)
                                            if (info.hasUpdate && info.versionCode > versionCode) {
                                                updateCheckResult = "Доступна новая версия: v${info.versionName} (Сборка ${info.versionCode})!"
                                                onCheckUpdateClick?.invoke()
                                            } else {
                                                updateCheckResult = "У вас установлена самая актуальная версия (v$appVersion)."
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = accent,
                                        focusedContainerColor = LocalFocusColor.current,
                                        contentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AppIcon(
                                            resId = R.drawable.ic_refresh,
                                            tint = Color.Black,
                                            size = 13.dp
                                        )
                                        Text("Проверить обновления", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
                                    }
                                }
                            }

                            if (updateCheckResult != null) {
                                Text(
                                    text = updateCheckResult ?: "",
                                    fontSize = 14.sp,
                                    color = accent
                                )
                            }
                        }
                    }
                }
                // Generous bottom spacer for TV overscroll clearance
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}
