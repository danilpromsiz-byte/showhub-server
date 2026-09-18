package com.example.tvmediaapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
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

        // Top Navigation items: Search, Favorites, History, Settings, Update
        val downMod = if (focusDownRequester != null) Modifier.focusProperties { down = focusDownRequester } else Modifier
        val searchFocusMod = if (topBarFocusRequester != null) Modifier.focusRequester(topBarFocusRequester) else Modifier

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search
            val isSearch = currentScreenName == "search"
            Button(
                onClick = onSearchClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isSearch) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White,
                    contentColor = if (isSearch) Color.Black else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = if (isSearch) Border(BorderStroke(2.dp, accent)) else Border.None,
                    focusedBorder = if (isSearch) Border(BorderStroke(2.5.dp, accent)) else Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .then(searchFocusMod)
                    .then(downMod)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppIcon(
                        resId = com.example.tvmediaapp.R.drawable.ic_search,
                        size = 13.dp
                    )
                    Text(
                        text = "Поиск",
                        fontWeight = if (isSearch) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // Favorites
            val isFav = currentScreenName == "favorites"
            Button(
                onClick = onFavoritesClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isFav) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White,
                    contentColor = if (isFav) Color.Black else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = if (isFav) Border(BorderStroke(2.dp, accent)) else Border.None,
                    focusedBorder = if (isFav) Border(BorderStroke(2.5.dp, accent)) else Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .then(downMod)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppIcon(
                        resId = com.example.tvmediaapp.R.drawable.ic_star,
                        size = 13.dp
                    )
                    Text(
                        text = "Избранное",
                        fontWeight = if (isFav) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // History
            val isHist = currentScreenName == "history"
            Button(
                onClick = onHistoryClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isHist) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White,
                    contentColor = if (isHist) Color.Black else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = if (isHist) Border(BorderStroke(2.dp, accent)) else Border.None,
                    focusedBorder = if (isHist) Border(BorderStroke(2.5.dp, accent)) else Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .then(downMod)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppIcon(
                        resId = com.example.tvmediaapp.R.drawable.ic_history,
                        size = 13.dp
                    )
                    Text(
                        text = "История",
                        fontWeight = if (isHist) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // Settings
            if (onSettingsClick != null) {
                val isSettings = currentScreenName == "settings"
                Button(
                    onClick = onSettingsClick,
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSettings) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White,
                        contentColor = if (isSettings) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = if (isSettings) Border(BorderStroke(2.dp, accent)) else Border.None,
                        focusedBorder = if (isSettings) Border(BorderStroke(2.5.dp, accent)) else Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(downMod)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcon(
                            resId = com.example.tvmediaapp.R.drawable.ic_settings,
                            size = 13.dp
                        )
                        Text(
                            text = "Настройки",
                            fontWeight = if (isSettings) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Update Check / Action Button
            if (onCheckUpdateClick != null) {
                Button(
                    onClick = onCheckUpdateClick,
                    colors = ButtonDefaults.colors(
                        containerColor = if (hasUpdateAvailable) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White,
                        contentColor = if (hasUpdateAvailable) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = if (hasUpdateAvailable) Border(BorderStroke(2.dp, accent)) else Border.None,
                        focusedBorder = if (hasUpdateAvailable) Border(BorderStroke(2.5.dp, accent)) else Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(downMod)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppIcon(
                            resId = com.example.tvmediaapp.R.drawable.ic_refresh,
                            size = 13.dp
                        )
                        Text(
                            text = if (hasUpdateAvailable) "Обновить" else "Обновления",
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
        // Obsidian Ceramic / Slate Emblem Tile
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF0F172A))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(7.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                resId = R.drawable.ic_play_arrow,
                tint = Color.White,
                size = 17.dp
            )
        }

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
