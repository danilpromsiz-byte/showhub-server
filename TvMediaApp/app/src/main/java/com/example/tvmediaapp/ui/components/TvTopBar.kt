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
                    containerColor = if (isSearch) accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = accent,
                    contentColor = if (isSearch) accent else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isSearch) accent else Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
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
                        tint = if (isSearch) accent else TextWhite,
                        size = 13.dp
                    )
                    Text(
                        text = "Поиск",
                        fontWeight = FontWeight.SemiBold,
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
                    containerColor = if (isFav) accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = accent,
                    contentColor = if (isFav) accent else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isFav) accent else Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
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
                        tint = if (isFav) accent else TextWhite,
                        size = 13.dp
                    )
                    Text(
                        text = "Избранное",
                        fontWeight = FontWeight.SemiBold,
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
                    containerColor = if (isHist) accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = accent,
                    contentColor = if (isHist) accent else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isHist) accent else Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
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
                        tint = if (isHist) accent else TextWhite,
                        size = 13.dp
                    )
                    Text(
                        text = "История",
                        fontWeight = FontWeight.SemiBold,
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
                        containerColor = if (isSettings) accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = accent,
                        contentColor = if (isSettings) accent else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (isSettings) accent else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
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
                            tint = if (isSettings) accent else TextWhite,
                            size = 13.dp
                        )
                        Text(
                            text = "Настройки",
                            fontWeight = FontWeight.SemiBold,
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
                        containerColor = if (hasUpdateAvailable) accent else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = accent,
                        contentColor = if (hasUpdateAvailable) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (hasUpdateAvailable) accent else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
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
                            tint = if (hasUpdateAvailable) Color.Black else TextWhite,
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
        // Neon Emblem: Glowing Cinema Prism / Play Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accent,
                            Color(0xFF8A2BE2),
                            Color(0xFF4A00E0)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                resId = R.drawable.ic_play_arrow,
                tint = Color.White,
                size = 16.dp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Styled Typography
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SHOW",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = TextWhite,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "HUB",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Neon TV Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "TV",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                letterSpacing = 0.5.sp
            )
        }

        if (appVersion.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "v$appVersion",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextGray
            )
        }
    }
}
