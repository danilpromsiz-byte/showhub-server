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

data class NavTabItem(
    val id: String,
    val title: String
)

val NAV_TABS = listOf(
    NavTabItem("all", "\u0412\u0441\u0435"),
    NavTabItem("movies", "\u0424\u0438\u043b\u044c\u043c\u044b"),
    NavTabItem("series", "\u0421\u0435\u0440\u0438\u0430\u043b\u044b"),
    NavTabItem("cartoons", "\u041c\u0443\u043b\u044c\u0442\u0444\u0438\u043b\u044c\u043c\u044b"),
    NavTabItem("anime", "\u0410\u043d\u0438\u043c\u0435"),
    NavTabItem("favorites", "\u2b50 \u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435"),
    NavTabItem("search", "\ud83d\udd0d \u041f\u043e\u0438\u0441\u043a")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    selectedTabId: String,
    onTabSelected: (String) -> Unit,
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
        }

        // Navigation Tabs
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NAV_TABS.forEach { tab ->
                val isSelected = tab.id == selectedTabId
                Button(
                    onClick = { onTabSelected(tab.id) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) CyanNeon.copy(alpha = 0.25f) else Color.Transparent,
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) CyanNeon else TextGray,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) CyanNeon else Color.Transparent
                            )
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, TextWhite)
                        )
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
        }
    }
}
