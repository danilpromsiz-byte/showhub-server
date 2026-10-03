package com.example.tvmediaapp.ui.screens.settings

import android.view.KeyEvent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.R
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.components.TvTopBar
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.LocalSurfaceColor
import com.example.tvmediaapp.ui.theme.TextWhite

enum class SettingsTab(val title: String) {
    PLAYER("Интерфейс и каталог"),
    FILMIX("Filmix"),
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

val EXCLUDE_GENRE_OPTIONS = listOf(
    "Ужасы", "Триллер", "Боевик", "Комедия", "Мелодрама", "Драма",
    "Фантастика", "Фэнтези", "Детектив", "Криминал", "Военный",
    "Приключения", "Мультфильм", "Аниме", "Документальный", "История", "Семейный"
)

val QUALITY_OPTIONS = listOf(
    Pair("1080p", "1080p (Full HD)"),
    Pair("4k", "4K (Ultra HD)"),
    Pair("720p", "720p (HD)"),
    Pair("480p", "480p (SD)"),
    Pair("max", "Максимальное")
)

val SOURCE_OPTIONS = listOf(
    Pair("Все", "Авто (Все)"),
    Pair("Collaps", "Collaps (Прямой HLS)"),
    Pair("Filmix", "Filmix"),
    Pair("HDRezka", "HDRezka"),
    Pair("VideoCDN", "VideoCDN"),
    Pair("Kodik", "Kodik")
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
    onScheduleClick: (() -> Unit)? = null,
    onCheckUpdateClick: (() -> Unit)? = null,
    hasUpdateAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val tabsFocusRequester = remember { FocusRequester() }
    val tabContentFocusRequester = remember { FocusRequester() }

    var activeTab by remember { mutableStateOf(SettingsTab.PLAYER) }

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
            onScheduleClick = onScheduleClick,
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
                    SettingsTab.PLAYER -> {
                        PlayerAndCatalogTab(
                            tabsFocusRequester = tabsFocusRequester,
                            tabContentFocusRequester = tabContentFocusRequester
                        )
                    }
                    SettingsTab.FILMIX -> {
                        FilmixTab(
                            tabContentFocusRequester = tabContentFocusRequester
                        )
                    }
                    SettingsTab.SERVER -> {
                        ServerTab(
                            tabContentFocusRequester = tabContentFocusRequester
                        )
                    }
                    SettingsTab.HISTORY -> {
                        HistoryTab(
                            tabContentFocusRequester = tabContentFocusRequester
                        )
                    }
                    SettingsTab.BUG_REPORT -> {
                        BugReportTab(
                            appVersion = appVersion,
                            versionCode = versionCode,
                            tabContentFocusRequester = tabContentFocusRequester
                        )
                    }
                    SettingsTab.ABOUT -> {
                        AboutTab(
                            appVersion = appVersion,
                            versionCode = versionCode,
                            tabContentFocusRequester = tabContentFocusRequester,
                            onCheckUpdateClick = onCheckUpdateClick,
                            hasUpdateAvailable = hasUpdateAvailable
                        )
                    }
                }
                // Generous bottom spacer for TV overscroll clearance
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}
