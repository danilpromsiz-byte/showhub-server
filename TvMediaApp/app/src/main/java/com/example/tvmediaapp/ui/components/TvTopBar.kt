package com.example.tvmediaapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvmediaapp.R
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onScheduleClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onCheckUpdateClick: (() -> Unit)? = null,
    hasUpdateAvailable: Boolean = false,
    appVersion: String = "",
    currentScreenName: String = "home",
    focusDownRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Modern Cinematic ShowHub TV Logo
        ShowHubLogo(accent = accent, appVersion = appVersion)

        val downMod = if (focusDownRequester != null) Modifier.focusProperties { down = focusDownRequester } else Modifier
        val searchFocusMod = if (topBarFocusRequester != null) Modifier.focusRequester(topBarFocusRequester) else Modifier

        Row(
            modifier = Modifier
                .then(downMod),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search
            val isSearch = currentScreenName == "search"
            var isSearchFocused by remember { mutableStateOf(false) }
            val searchIconColor = if (isSearchFocused || isSearch) Color.Black else TextWhite
            Button(
                onClick = onSearchClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isSearch) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = focusColor,
                    contentColor = if (isSearch) Color.Black else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .then(searchFocusMod)
                    .then(downMod)
                    .onFocusChanged { isSearchFocused = it.isFocused }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppIcon(
                        resId = com.example.tvmediaapp.R.drawable.ic_search,
                        tint = searchIconColor,
                        size = 13.dp
                    )
                    Text(
                        text = "Поиск",
                        color = searchIconColor,
                        fontWeight = if (isSearch) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // Favorites
            val isFav = currentScreenName == "favorites"
            var isFavFocused by remember { mutableStateOf(false) }
            val favIconColor = if (isFavFocused || isFav) Color.Black else TextWhite
            Button(
                onClick = onFavoritesClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isFav) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = focusColor,
                    contentColor = if (isFav) Color.Black else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .then(downMod)
                    .onFocusChanged { isFavFocused = it.isFocused }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppIcon(
                        resId = com.example.tvmediaapp.R.drawable.ic_star,
                        tint = favIconColor,
                        size = 13.dp
                    )
                    Text(
                        text = "Избранное",
                        color = favIconColor,
                        fontWeight = if (isFav) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // History
            val isHist = currentScreenName == "history"
            var isHistFocused by remember { mutableStateOf(false) }
            val histIconColor = if (isHistFocused || isHist) Color.Black else TextWhite
            Button(
                onClick = onHistoryClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isHist) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = focusColor,
                    contentColor = if (isHist) Color.Black else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .then(downMod)
                    .onFocusChanged { isHistFocused = it.isFocused }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppIcon(
                        resId = com.example.tvmediaapp.R.drawable.ic_history,
                        tint = histIconColor,
                        size = 13.dp
                    )
                    Text(
                        text = "История",
                        color = histIconColor,
                        fontWeight = if (isHist) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // Calendar / Schedule
            if (onScheduleClick != null) {
                val isSched = currentScreenName == "schedule"
                var isSchedFocused by remember { mutableStateOf(false) }
                val schedIconColor = if (isSchedFocused || isSched) Color.Black else TextWhite
                Button(
                    onClick = onScheduleClick,
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSched) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = focusColor,
                        contentColor = if (isSched) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(Border.None, Border.None),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(downMod)
                        .onFocusChanged { isSchedFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcon(
                            resId = com.example.tvmediaapp.R.drawable.ic_calendar,
                            tint = schedIconColor,
                            size = 13.dp
                        )
                        Text(
                            text = "Календарь",
                            color = schedIconColor,
                            fontWeight = if (isSched) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Settings
            if (onSettingsClick != null) {
                val isSettings = currentScreenName == "settings"
                var isSettingsFocused by remember { mutableStateOf(false) }
                val settingsIconColor = if (isSettingsFocused || isSettings) Color.Black else TextWhite
                Button(
                    onClick = onSettingsClick,
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSettings) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = focusColor,
                        contentColor = if (isSettings) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border.None,
                        focusedBorder = Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(downMod)
                        .onFocusChanged { isSettingsFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcon(
                            resId = com.example.tvmediaapp.R.drawable.ic_settings,
                            tint = settingsIconColor,
                            size = 13.dp
                        )
                        Text(
                            text = "Настройки",
                            color = settingsIconColor,
                            fontWeight = if (isSettings) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Update Check / Action Button
            if (onCheckUpdateClick != null) {
                var isUpdateFocused by remember { mutableStateOf(false) }
                val updateIconColor = if (isUpdateFocused || hasUpdateAvailable) Color.Black else TextWhite
                Button(
                    onClick = onCheckUpdateClick,
                    colors = ButtonDefaults.colors(
                        containerColor = if (hasUpdateAvailable) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = focusColor,
                        contentColor = if (hasUpdateAvailable) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border.None,
                        focusedBorder = Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(downMod)
                        .onFocusChanged { isUpdateFocused = it.isFocused }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcon(
                            resId = com.example.tvmediaapp.R.drawable.ic_refresh,
                            tint = updateIconColor,
                            size = 13.dp
                        )
                        Text(
                            text = if (hasUpdateAvailable) "Обновить" else "Обновления",
                            color = updateIconColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShowHubLogo(
    accent: Color,
    appVersion: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Official ShowHub TV Icon Emblem (Razor-sharp Vector)
        Image(
            painter = painterResource(id = R.drawable.ic_showhub_logo),
            contentDescription = "ShowHub TV",
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(9.dp))

        // Mature Cinematic Typography
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SHOW",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.8.sp
            )
            Text(
                text = "HUB",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                letterSpacing = 1.8.sp
            )
        }

        Spacer(modifier = Modifier.width(7.dp))

        // Sleek Minimalist TV Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E293B))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 5.dp, vertical = 1.5.dp)
        ) {
            Text(
                text = "TV",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E8F0),
                letterSpacing = 1.sp
            )
        }

        if (appVersion.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "v$appVersion",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        }
    }
}
