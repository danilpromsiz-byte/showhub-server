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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
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

enum class FilterCategory {
    GENRES,
    SORT,
    YEAR,
    COUNTRY
}

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
    row1FocusRequester: FocusRequester? = null,
    row2FocusRequester: FocusRequester? = null,
    focusUpRequester: FocusRequester? = null,
    focusDownRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var activeCategory by remember { mutableStateOf(FilterCategory.GENRES) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ROW 1: Content Type + Category Switchers (Sort, Genres, Year, Country) + Reset
        val row1DirectionMod = Modifier.focusProperties {
            if (focusUpRequester != null) up = focusUpRequester
            if (row2FocusRequester != null) down = row2FocusRequester
        }

        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Type Chips
            itemsIndexed(TYPE_OPTIONS) { index, (typeKey, typeLabel) ->
                val isSelected = typeKey == selectedType
                val firstMod = if (index == 0 && row1FocusRequester != null) Modifier.focusRequester(row1FocusRequester) else Modifier
                Button(
                    onClick = { onTypeSelected(typeKey) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) accent.copy(alpha = 0.75f) else ChipBackground,
                        focusedContainerColor = Color.White,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = if (isSelected) Border(BorderStroke(2.dp, accent)) else Border.None,
                        focusedBorder = if (isSelected) Border(BorderStroke(2.5.dp, accent)) else Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(firstMod)
                        .then(row1DirectionMod)
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = 13.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 2. Category Switcher Buttons
            val categories = listOf(
                FilterCategory.SORT,
                FilterCategory.GENRES,
                FilterCategory.YEAR,
                FilterCategory.COUNTRY
            )

            items(categories) { category ->
                val isCategoryActive = activeCategory == category
                val (hasCustomFilter, label) = when (category) {
                    FilterCategory.SORT -> {
                        val isCustom = selectedSort != "newest"
                        val name = SORT_OPTIONS.find { it.first == selectedSort }?.second ?: "Сортировка"
                        Pair(isCustom, if (isCustom) "⚡ $name" else "⚡ Сортировка")
                    }
                    FilterCategory.GENRES -> {
                        val isCustom = selectedGenre != "Все жанры"
                        Pair(isCustom, if (isCustom) "🎭 $selectedGenre" else "🎭 Жанры")
                    }
                    FilterCategory.YEAR -> {
                        val isCustom = selectedYear != "all"
                        val name = YEAR_OPTIONS.find { it.first == selectedYear }?.second ?: selectedYear
                        Pair(isCustom, if (isCustom) "📅 $name" else "📅 Год")
                    }
                    FilterCategory.COUNTRY -> {
                        val isCustom = selectedCountry != "all"
                        val name = COUNTRY_OPTIONS.find { it.first == selectedCountry }?.second ?: selectedCountry
                        Pair(isCustom, if (isCustom) "🌍 $name" else "🌍 Страна")
                    }
                }

                Button(
                    onClick = { activeCategory = category },
                    colors = ButtonDefaults.colors(
                        containerColor = when {
                            isCategoryActive -> accent.copy(alpha = 0.75f)
                            hasCustomFilter -> accent.copy(alpha = 0.25f)
                            else -> ChipBackground.copy(alpha = 0.6f)
                        },
                        focusedContainerColor = Color.White,
                        contentColor = when {
                            isCategoryActive -> Color.Black
                            hasCustomFilter -> accent
                            else -> TextGray
                        },
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = when {
                            isCategoryActive -> Border(BorderStroke(2.dp, accent))
                            hasCustomFilter -> Border(BorderStroke(1.5.dp, accent))
                            else -> Border.None
                        },
                        focusedBorder = when {
                            isCategoryActive || hasCustomFilter -> Border(BorderStroke(2.5.dp, accent))
                            else -> Border.None
                        }
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(row1DirectionMod)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isCategoryActive || hasCustomFilter) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = 13.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.width(6.dp))
                // 3. Reset Button
                val hasAnyFilter = selectedType != "all" ||
                        selectedSort != "newest" ||
                        selectedGenre != "Все жанры" ||
                        selectedYear != "all" ||
                        selectedCountry != "all"

                Button(
                    onClick = {
                        onResetFilters()
                        activeCategory = FilterCategory.GENRES
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (hasAnyFilter) Color(0xFFE53935).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White,
                        contentColor = if (hasAnyFilter) Color(0xFFFF8A80) else TextGray,
                        focusedContentColor = Color(0xFFE53935)
                    ),
                    border = ButtonDefaults.border(
                        border = if (hasAnyFilter) Border(BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))) else Border.None,
                        focusedBorder = Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .then(row1DirectionMod)
                ) {
                    Text(
                        text = "✖ Сброс",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // ROW 2: Dynamic Category Ribbon (Genres, Sort, Year, Country)
        val row2DirectionMod = Modifier.focusProperties {
            if (row1FocusRequester != null) up = row1FocusRequester
            if (focusDownRequester != null) down = focusDownRequester
        }

        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (activeCategory) {
                FilterCategory.GENRES -> {
                    itemsIndexed(GENRES_LIST) { index, genre ->
                        val isSelected = genre == selectedGenre
                        val firstMod = if (index == 0 && row2FocusRequester != null) Modifier.focusRequester(row2FocusRequester) else Modifier
                        Button(
                            onClick = { onGenreSelected(genre) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.75f) else ChipBackground,
                                focusedContainerColor = Color.White,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = if (isSelected) Border(BorderStroke(2.dp, accent)) else Border.None,
                                focusedBorder = if (isSelected) Border(BorderStroke(2.5.dp, accent)) else Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .then(firstMod)
                                .then(row2DirectionMod)
                        ) {
                            Text(
                                text = genre,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
                FilterCategory.SORT -> {
                    itemsIndexed(SORT_OPTIONS) { index, (sortKey, sortLabel) ->
                        val isSelected = sortKey == selectedSort
                        val firstMod = if (index == 0 && row2FocusRequester != null) Modifier.focusRequester(row2FocusRequester) else Modifier
                        Button(
                            onClick = { onSortSelected(sortKey) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.75f) else ChipBackground,
                                focusedContainerColor = Color.White,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = if (isSelected) Border(BorderStroke(2.dp, accent)) else Border.None,
                                focusedBorder = if (isSelected) Border(BorderStroke(2.5.dp, accent)) else Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .then(firstMod)
                                .then(row2DirectionMod)
                        ) {
                            Text(
                                text = sortLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
                FilterCategory.YEAR -> {
                    itemsIndexed(YEAR_OPTIONS) { index, (yearKey, yearLabel) ->
                        val isSelected = yearKey == selectedYear
                        val firstMod = if (index == 0 && row2FocusRequester != null) Modifier.focusRequester(row2FocusRequester) else Modifier
                        Button(
                            onClick = { onYearSelected(yearKey) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.75f) else ChipBackground,
                                focusedContainerColor = Color.White,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = if (isSelected) Border(BorderStroke(2.dp, accent)) else Border.None,
                                focusedBorder = if (isSelected) Border(BorderStroke(2.5.dp, accent)) else Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .then(firstMod)
                                .then(row2DirectionMod)
                        ) {
                            Text(
                                text = yearLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
                FilterCategory.COUNTRY -> {
                    itemsIndexed(COUNTRY_OPTIONS) { index, (countryKey, countryLabel) ->
                        val isSelected = countryKey == selectedCountry
                        val firstMod = if (index == 0 && row2FocusRequester != null) Modifier.focusRequester(row2FocusRequester) else Modifier
                        Button(
                            onClick = { onCountrySelected(countryKey) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.75f) else ChipBackground,
                                focusedContainerColor = Color.White,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = if (isSelected) Border(BorderStroke(2.dp, accent)) else Border.None,
                                focusedBorder = if (isSelected) Border(BorderStroke(2.5.dp, accent)) else Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .then(firstMod)
                                .then(row2DirectionMod)
                        ) {
                            Text(
                                text = countryLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
