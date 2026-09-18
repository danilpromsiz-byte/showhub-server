package com.example.tvmediaapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCheckUpdateClick: (() -> Unit)? = null,
    hasUpdateAvailable: Boolean = false,
    appVersion: String = "",
    currentScreenName: String = "home",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ShowHub TV Logo
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SHOWHUB",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CyanNeon)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "TV",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
            if (appVersion.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "v$appVersion",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGray
                )
            }
        }

        // Top Navigation items: Search, Favorites, History, Update
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search
            val isSearch = currentScreenName == "search"
            Button(
                onClick = onSearchClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isSearch) CyanNeon.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = CyanNeon,
                    contentColor = if (isSearch) CyanNeon else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isSearch) CyanNeon else Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = "\ud83d\udd0d \u041f\u043e\u0438\u0441\u043a",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // Favorites
            val isFav = currentScreenName == "favorites"
            Button(
                onClick = onFavoritesClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isFav) CyanNeon.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = CyanNeon,
                    contentColor = if (isFav) CyanNeon else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isFav) CyanNeon else Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = "\u2b50 \u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // History
            val isHist = currentScreenName == "history"
            Button(
                onClick = onHistoryClick,
                colors = ButtonDefaults.colors(
                    containerColor = if (isHist) CyanNeon.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = CyanNeon,
                    contentColor = if (isHist) CyanNeon else TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isHist) CyanNeon else Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = "\ud83d\udd52 \u0418\u0441\u0442\u043e\u0440\u0438\u044f",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // Update Check / Action Button
            if (onCheckUpdateClick != null) {
                Button(
                    onClick = onCheckUpdateClick,
                    colors = ButtonDefaults.colors(
                        containerColor = if (hasUpdateAvailable) CyanNeon else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = CyanNeon,
                        contentColor = if (hasUpdateAvailable) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (hasUpdateAvailable) CyanNeon else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = if (hasUpdateAvailable) "\u2b07\ufe0f \u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c" else "\ud83d\udd04",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
