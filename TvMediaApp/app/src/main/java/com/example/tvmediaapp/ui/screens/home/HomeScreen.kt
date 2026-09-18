@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)

package com.example.tvmediaapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.FilterBar
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.components.TvTopBar
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieSelect: (Movie) -> Unit,
    onWatchClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    onCheckUpdateClick: (() -> Unit)? = null,
    hasUpdateAvailable: Boolean = false,
    appVersion: String = "",
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()

    val displayMovies = remember(categories) {
        categories.firstOrNull()?.movies ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // TOP NAVIGATION BAR: Logo + Search + Favorites + History + Settings + Update
        TvTopBar(
            onSearchClick = onSearchClick,
            onFavoritesClick = onFavoritesClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick,
            onCheckUpdateClick = onCheckUpdateClick,
            hasUpdateAvailable = hasUpdateAvailable,
            appVersion = appVersion,
            currentScreenName = "home"
        )

        // FILTER & SORT RIBBON
        FilterBar(
            selectedType = selectedType,
            onTypeSelected = { viewModel.selectType(it) },
            selectedSort = selectedSort,
            onSortSelected = { viewModel.selectSort(it) },
            selectedGenre = selectedGenre,
            onGenreSelected = { viewModel.selectGenre(it) },
            selectedYear = selectedYear,
            onYearSelected = { viewModel.selectYear(it) },
            selectedCountry = selectedCountry,
            onCountrySelected = { viewModel.selectCountry(it) },
            onResetFilters = { viewModel.resetFilters() }
        )

        // 6-COLUMN VERTICAL GRID (2 rows of 6 cards on screen at a time, scrolling down)
        if (displayMovies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "По выбранным фильтрам ничего не найдено",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Попробуйте изменить категорию, жанр или сбросить фильтры",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayMovies) { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = { onMovieSelect(movie) },
                        onFocus = { /* card focused */ }
                    )
                }
            }
        }
    }
}

