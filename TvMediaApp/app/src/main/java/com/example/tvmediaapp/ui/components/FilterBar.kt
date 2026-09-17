package com.example.tvmediaapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

val GENRES_LIST = listOf(
    "\u0412\u0441\u0435 \u0436\u0430\u043d\u0440\u044b",
    "\u0411\u043e\u0435\u0432\u0438\u043a",
    "\u041a\u043e\u043c\u0435\u0434\u0438\u044f",
    "\u0424\u0430\u043d\u0442\u0430\u0441\u0442\u0438\u043a\u0430",
    "\u0414\u0440\u0430\u043c\u0430",
    "\u0422\u0440\u0438\u043b\u043b\u0435\u0440",
    "\u0423\u0436\u0430\u0441\u044b",
    "\u041f\u0440\u0438\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f",
    "\u0424\u044d\u043d\u0442\u0435\u0437\u0438",
    "\u041a\u0440\u0438\u043c\u0438\u043d\u0430\u043b"
)

val SORT_OPTIONS = listOf(
    Pair("newest", "\u041d\u043e\u0432\u0438\u043d\u043a\u0438"),
    Pair("rating", "\u2605 \u041f\u043e \u0440\u0435\u0439\u0442\u0438\u043d\u0433\u0443"),
    Pair("popular", "\ud83d\udd25 \u041f\u043e\u043f\u0443\u043b\u044f\u0440\u043d\u043e\u0435")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterBar(
    selectedGenre: String,
    onGenreSelected: (String) -> Unit,
    selectedSort: String,
    onSortSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Genres Horizontal Ribbon
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(GENRES_LIST) { genre ->
                val isSelected = genre == selectedGenre
                Button(
                    onClick = { onGenreSelected(genre) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) CyanNeon else ChipBackground,
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CyanNeon else Color.Transparent
                            )
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, TextWhite)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(16.dp)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = genre,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Sort Options inline
            items(SORT_OPTIONS) { (sortKey, sortTitle) ->
                val isSelected = sortKey == selectedSort
                Button(
                    onClick = { onSortSelected(sortKey) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) CyanNeon.copy(alpha = 0.3f) else ChipBackground.copy(alpha = 0.6f),
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) CyanNeon else TextGray,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CyanNeon else Color.Transparent
                            )
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, TextWhite)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(16.dp)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = sortTitle,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
