package com.example.tvmediaapp.ui.screens.settings

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.Coil
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.updater.UpdateInfo
import com.example.tvmediaapp.data.updater.UpdateManager
import com.example.tvmediaapp.ui.components.TvTopBar
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.SurfaceDark
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
    SERVER("Сервер и связь"),
    HISTORY("История и кэш"),
    ABOUT("О программе")
}

val THEME_OPTIONS = listOf(
    Pair("cyan", "Cyan Neon (Бирюза)"),
    Pair("emerald", "Emerald (Изумруд)"),
    Pair("amber", "Amber (Янтарь)"),
    Pair("ruby", "Ruby (Рубин)"),
    Pair("amethyst", "Amethyst (Аметист)"),
    Pair("sapphire", "Sapphire (Сапфир)")
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

    var activeTab by remember { mutableStateOf(SettingsTab.PLAYER) }

    // Settings States
    var selectedTheme by remember { mutableStateOf(ThemeManager.currentThemeKey) }
    var selectedPlayer by remember { mutableStateOf(prefs.getString("pref_player", "internal") ?: "internal") }
    var selectedQuality by remember { mutableStateOf(prefs.getString("pref_quality", "1080p") ?: "1080p") }
    var selectedVoice by remember { mutableStateOf(prefs.getString("pref_voice", "Любая / Оригинал") ?: "Любая / Оригинал") }

    // Server States
    var serverUrl by remember { mutableStateOf(prefs.getString("pref_server_url", "https://showhub-server.onrender.com") ?: "https://showhub-server.onrender.com") }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    // Filmix States
    var filmixLogin by remember { mutableStateOf(prefs.getString("filmix_login", "") ?: "") }
    var filmixToken by remember { mutableStateOf(prefs.getString("filmix_token", "") ?: "") }
    var isFilmixPro by remember { mutableStateOf(prefs.getBoolean("filmix_is_pro", false)) }

    // Update Status
    var updateCheckResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
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
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(SettingsTab.values()) { tab ->
                val isSelected = tab == activeTab
                Button(
                    onClick = { activeTab = tab },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) accent else ChipBackground,
                        focusedContainerColor = accent,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = tab.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = onBackClick,
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(text = "Назад к каталогу", fontSize = 14.sp)
                }
            }
        }

        // TAB CONTENT CONTAINER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
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
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(THEME_OPTIONS) { (themeKey, themeTitle) ->
                                    val isCur = themeKey == selectedTheme
                                    Button(
                                        onClick = {
                                            selectedTheme = themeKey
                                            ThemeManager.setTheme(themeKey)
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isCur) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(BorderStroke(1.dp, if (isCur) accent else Color.Transparent)),
                                            focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(text = themeTitle, fontSize = 13.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
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
                                text = "Действие по умолчанию при нажатии кнопки «Смотреть онлайн»",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(PLAYER_OPTIONS) { (playerKey, playerTitle) ->
                                    val isCur = playerKey == selectedPlayer
                                    Button(
                                        onClick = {
                                            selectedPlayer = playerKey
                                            prefs.edit().putString("pref_player", playerKey).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isCur) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(text = playerTitle, fontSize = 13.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
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
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(QUALITY_OPTIONS) { (qKey, qTitle) ->
                                    val isCur = qKey == selectedQuality
                                    Button(
                                        onClick = {
                                            selectedQuality = qKey
                                            prefs.edit().putString("pref_quality", qKey).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isCur) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(text = qTitle, fontSize = 13.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
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
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(VOICE_OPTIONS) { voice ->
                                    val isCur = voice == selectedVoice
                                    Button(
                                        onClick = {
                                            selectedVoice = voice
                                            prefs.edit().putString("pref_voice", voice).apply()
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isCur) accent else ChipBackground,
                                            focusedContainerColor = accent,
                                            contentColor = if (isCur) Color.Black else TextWhite,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(text = voice, fontSize = 13.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
                                    }
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
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
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
                                        focusedContainerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text("Активировать Filmix PRO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                        focusedContainerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text(text = if (isPinging) "Проверка..." else "Проверить связь (Ping)", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (pingResult != null) {
                                Text(
                                    text = pingResult ?: "",
                                    fontSize = 14.sp,
                                    color = if (pingResult?.contains("установлена") == true) accent else Color.Red
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Источники контента: HDRezka (прямой поток на ТВ), Bazon, VideoCDN, Delivembd, Filmix",
                                fontSize = 13.sp,
                                color = TextGray
                            )
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
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                                ) {
                                    Text("Очистить историю", fontSize = 13.sp)
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
                                    colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                                ) {
                                    Text("Очистить кэш картинок", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // TAB 5: ABOUT & UPDATES
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
                                        focusedContainerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text("Проверить обновления", fontWeight = FontWeight.Bold)
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
            }
        }
    }
}
