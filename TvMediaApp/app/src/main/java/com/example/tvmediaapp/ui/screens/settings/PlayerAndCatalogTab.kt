package com.example.tvmediaapp.ui.screens.settings

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.cache.MediaDiskCache
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import com.example.tvmediaapp.ui.theme.ThemeManager

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerAndCatalogTab(
    tabsFocusRequester: FocusRequester,
    tabContentFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }

    var selectedTheme by remember { mutableStateOf(ThemeManager.currentThemeKey) }
    var selectedPlayer by remember { mutableStateOf(prefs.getString("pref_player", "internal") ?: "internal") }
    var selectedQuality by remember { mutableStateOf(prefs.getString("pref_quality", "1080p") ?: "1080p") }
    var selectedPreferredSource by remember { mutableStateOf(prefs.getString("pref_source", "Все") ?: "Все") }
    var selectedVoice by remember { mutableStateOf(prefs.getString("pref_voice", "Любая / Оригинал") ?: "Любая / Оригинал") }
    var selectedPreviewStart by remember { mutableStateOf(prefs.getInt("pref_preview_start_min", 12)) }
    var onlyWithPoster by remember { mutableStateOf(prefs.getBoolean("pref_only_with_poster", true)) }
    var showUnreleasedMovies by remember { mutableStateOf(prefs.getBoolean("pref_unreleased_movies", false)) }
    var showUnreleasedSeries by remember { mutableStateOf(prefs.getBoolean("pref_unreleased_series", true)) }
    var excludedCountriesStr by remember { mutableStateOf(prefs.getString("pref_excluded_countries", "") ?: "") }
    var excludedGenresStr by remember { mutableStateOf(prefs.getString("pref_excluded_genres", "") ?: "") }

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

    // Section: Preferred Source
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Приоритетный источник потока",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )
        Text(
            text = "Источник, открываемый по умолчанию при запуске фильма (Collaps рекомендуется)",
            fontSize = 13.sp,
            color = TextGray
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            SOURCE_OPTIONS.forEach { (srcKey, srcTitle) ->
                val isCur = srcKey == selectedPreferredSource
                Button(
                    onClick = {
                        selectedPreferredSource = srcKey
                        prefs.edit().putString("pref_source", srcKey).apply()
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
                    Text(text = srcTitle, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
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
                    text = "$mark Не показывать на главной фильмы без обложек (доступны в поиске)",
                    fontSize = 11.sp,
                    fontWeight = if (onlyWithPoster) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 13.sp
                )
            }
        }
        Text(
            text = "Фильмы и сериалы без постеров не захламляют главную страницу, но их всегда можно найти и открыть через поиск.",
            fontSize = 11.sp,
            color = TextGray
        )

        // Toggle: Show unreleased movies (Default: OFF / false)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = {
                    val newState = !showUnreleasedMovies
                    showUnreleasedMovies = newState
                    prefs.edit().putBoolean("pref_unreleased_movies", newState).apply()
                },
                colors = ButtonDefaults.colors(
                    containerColor = if (showUnreleasedMovies) accent.copy(alpha = 0.85f) else ChipBackground,
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = if (showUnreleasedMovies) Color.Black else TextWhite,
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
                val markM = if (showUnreleasedMovies) "☑" else "☐"
                Text(
                    text = "$markM Показывать невышедшие фильмы (анонсы, Аватар 4/5 и т.д.)",
                    fontSize = 11.sp,
                    fontWeight = if (showUnreleasedMovies) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 13.sp
                )
            }
        }

        // Toggle: Show unreleased series (Default: ON / true)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = {
                    val newState = !showUnreleasedSeries
                    showUnreleasedSeries = newState
                    prefs.edit().putBoolean("pref_unreleased_series", newState).apply()
                },
                colors = ButtonDefaults.colors(
                    containerColor = if (showUnreleasedSeries) accent.copy(alpha = 0.85f) else ChipBackground,
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = if (showUnreleasedSeries) Color.Black else TextWhite,
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
                val markS = if (showUnreleasedSeries) "☑" else "☐"
                Text(
                    text = "$markS Показывать невышедшие сериалы (будущие релизы и сезоны)",
                    fontSize = 11.sp,
                    fontWeight = if (showUnreleasedSeries) FontWeight.Bold else FontWeight.Normal,
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
                                MediaDiskCache.clearCachedCatalog()
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

        // Subtitle: Excluded genres
        Text(
            text = "Исключить жанры из показа (нажмите, чтобы скрыть фильмы выбранного жанра):",
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.padding(top = 6.dp)
        )

        val excludedGenreSet = remember(excludedGenresStr) {
            excludedGenresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

        // Flow-like 2-row layout of genres
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val chunkedGenres = EXCLUDE_GENRE_OPTIONS.chunked(9)
            chunkedGenres.forEach { rowGenres ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowGenres.forEach { gName ->
                        val isExcluded = excludedGenreSet.contains(gName)
                        Button(
                            onClick = {
                                val newSet = if (isExcluded) excludedGenreSet - gName else excludedGenreSet + gName
                                val newStr = newSet.joinToString(",")
                                excludedGenresStr = newStr
                                prefs.edit().putString("pref_excluded_genres", newStr).apply()
                                MediaDiskCache.clearCachedCatalog()
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
                                text = "$prefix$gName",
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
