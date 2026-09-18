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
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

// 1. Content Types
val TYPE_OPTIONS = listOf(
    Pair("all", "Все типы"),
    Pair("movies", "Фильмы"),
    Pair("series", "Сериалы"),
    Pair("cartoons", "Мультфильмы"),
    Pair("anime", "Аниме")
)

// 2. Sort Options
val SORT_OPTIONS = listOf(
    Pair("newest", "Новинки"),
    Pair("rating", "По рейтингу"),
    Pair("popular", "По популярности"),
    Pair("year", "По году")
)

// 3. Genres
val GENRES_LIST = listOf(
    "Все жанры",
    "Боевик",
    "Комедия",
    "Фантастика",
    "Драма",
    "Триллер",
    "Ужасы",
    "Приключения",
    "Фэнтези",
    "Детектив",
    "Криминал"
)

// 4. Release Year
val YEAR_OPTIONS = listOf(
    Pair("all", "Все годы"),
    Pair("2026", "2026"),
    Pair("2025", "2025"),
    Pair("2024", "2024"),
    Pair("2023", "2023"),
    Pair("2020-2022", "2020–2022"),
    Pair("2010s", "2010–2019"),
    Pair("before_2000", "До 2000")
)

// 5. Country
val COUNTRY_OPTIONS = listOf(
    Pair("all", "Все страны"),
    Pair("США", "США"),
    Pair("Россия", "Россия"),
    Pair("Великобритания", "Великобритания"),
    Pair("Корея", "Южная Корея"),
    Pair("Франция", "Франция"),
    Pair("Япония", "Япония"),
    Pair("Китай", "Китай")
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
    val accent = LocalAccentColor.current

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
                        containerColor = if (isSelected) accent else ChipBackground,
                        focusedContainerColor = accent,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
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
                        containerColor = if (isSelected) accent.copy(alpha = 0.35f) else ChipBackground.copy(alpha = 0.6f),
                        focusedContainerColor = accent,
                        contentColor = if (isSelected) accent else TextGray,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (isSelected) accent else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
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
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Сброс",
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
                        containerColor = if (isSelected) accent.copy(alpha = 0.3f) else ChipBackground,
                        focusedContainerColor = accent,
                        contentColor = if (isSelected) accent else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, if (isSelected) accent else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, TextWhite))
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
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
