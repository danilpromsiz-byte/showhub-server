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

// 1. Content Types
val TYPE_OPTIONS = listOf(
    Pair("all", "\u0412\u0441\u0435 \u0442\u0438\u043f\u044b"),
    Pair("movies", "\u0424\u0438\u043b\u044c\u043c\u044b"),
    Pair("series", "\u0421\u0435\u0440\u0438\u0430\u043b\u044b"),
    Pair("cartoons", "\u041c\u0443\u043b\u044c\u0442\u0444\u0438\u043b\u044c\u043c\u044b"),
    Pair("anime", "\u0410\u043d\u0438\u043c\u0435")
)

// 2. Sort Options
val SORT_OPTIONS = listOf(
    Pair("newest", "\u041d\u043e\u0432\u0438\u043d\u043a\u0438"),
    Pair("rating", "\u2605 \u041f\u043e \u0440\u0435\u0439\u0442\u0438\u043d\u0433\u0443"),
    Pair("popular", "\ud83d\udd25 \u041f\u043e \u043f\u043e\u043f\u0443\u043b\u044f\u0440\u043d\u043e\u0441\u0442\u0438"),
    Pair("year", "\ud83d\udcc5 \u041f\u043e \u0433\u043e\u0434\u0443")
)

// 3. Genres
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
    "\u0414\u0435\u0442\u0435\u043a\u0442\u0438\u0432",
    "\u041a\u0440\u0438\u043c\u0438\u043d\u0430\u043b"
)

// 4. Release Year
val YEAR_OPTIONS = listOf(
    Pair("all", "\u0412\u0441\u0435 \u0433\u043e\u0434\u044b"),
    Pair("2026", "2026"),
    Pair("2025", "2025"),
    Pair("2024", "2024"),
    Pair("2023", "2023"),
    Pair("2020-2022", "2020\u20132022"),
    Pair("2010s", "2010\u20132019"),
    Pair("before_2000", "\u0414\u043e 2000")
)

// 5. Country
val COUNTRY_OPTIONS = listOf(
    Pair("all", "\u0412\u0441\u0435 \u0441\u0442\u0440\u0430\u043d\u044b"),
    Pair("\u0421\u0428\u0410", "\u0421\u0428\u0410"),
    Pair("\u0420\u043e\u0441\u0441\u0438\u044f", "\u0420\u043e\u0441\u0441\u0438\u044f"),
    Pair("\u0412\u0435\u043b\u0438\u043a\u043e\u0431\u0440\u0438\u0442\u0430\u043d\u0438\u044f", "\u0412\u0435\u043b\u0438\u043a\u043e\u0431\u0440\u0438\u0442\u0430\u043d\u0438\u044f"),
    Pair("\u041a\u043e\u0440\u0435\u044f", "\u042e\u0436\u043d\u0430\u044f \u041a\u043e\u0440\u0435\u044f"),
    Pair("\u0424\u0440\u0430\u043d\u0446\u0438\u044f", "\u0424\u0440\u0430\u043d\u0446\u0438\u044f"),
    Pair("\u042f\u043f\u043e\u043d\u0438\u044f", "\u042f\u043f\u043e\u043d\u0438\u044f"),
    Pair("\u041a\u0438\u0442\u0430\u0439", "\u041a\u0438\u0442\u0430\u0439")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterBar(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    selectedSort: String,
    onSortSelected: (String) -> Unit,
    selectedGenre: String,
    onGenreSelected: (String) -> Unit,
    selectedYear: String,
    onYearSelected: (String) -> Unit,
    selectedCountry: String,
    onCountrySelected: (String) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ROW 1: Content Type + Sort + Reset
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Chips
            items(TYPE_OPTIONS) { (typeKey, typeLabel) ->
                val isSelected = typeKey == selectedType
                Button(
                    onClick = { onTypeSelected(typeKey) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) CyanNeon else ChipBackground,
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Sort Options
            items(SORT_OPTIONS) { (sortKey, sortLabel) ->
                val isSelected = sortKey == selectedSort
                Button(
                    onClick = { onSortSelected(sortKey) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) CyanNeon.copy(alpha = 0.35f) else ChipBackground.copy(alpha = 0.6f),
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) CyanNeon else TextGray,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (isSelected) CyanNeon else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = sortLabel,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(8.dp))
                // Reset Button
                Button(
                    onClick = onResetFilters,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.Red.copy(alpha = 0.8f),
                        contentColor = TextGray,
                        focusedContentColor = TextWhite
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "\u2715 \u0421\u0431\u0440\u043e\u0441",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ROW 2: Genres Ribbon
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
                        containerColor = if (isSelected) CyanNeon.copy(alpha = 0.3f) else ChipBackground,
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) CyanNeon else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (isSelected) CyanNeon else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = genre,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
