@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)

package com.example.tvmediaapp.ui.screens.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.FilterBar
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.components.TvTopBar
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieSelect: (Movie) -> Unit,
    onWatchClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onScheduleClick: (() -> Unit)? = null,
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
    val isLoading by viewModel.isLoading.collectAsState()

    val displayMovies = remember(categories) {
        categories.firstOrNull()?.movies ?: emptyList()
    }

    val coroutineScope = rememberCoroutineScope()
    val topBarSearchFocusRequester = remember { FocusRequester() }
    val filterRow1FocusRequester = remember { FocusRequester() }
    val filterRow2FocusRequester = remember { FocusRequester() }
    val targetCardFocusRequester = remember { FocusRequester() }
    val emptyResetFocusRequester = remember { FocusRequester() }

    // Scroll to top on Back button if user scrolled down in grid
    BackHandler(enabled = viewModel.gridState.firstVisibleItemIndex > 0) {
        coroutineScope.launch {
            try {
                viewModel.gridState.scrollToItem(0)
                viewModel.lastFocusedIndex = 0
                delay(60)
                targetCardFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var initialFocusDone by remember { mutableStateOf(false) }

    // Automatically focus the active card ONCE on initial screen enter when displayMovies is ready
    LaunchedEffect(displayMovies.isNotEmpty()) {
        if (displayMovies.isNotEmpty() && !initialFocusDone) {
            initialFocusDone = true
            delay(150)
            try {
                targetCardFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalBackgroundColor.current)
    ) {
        // TOP NAVIGATION BAR: Logo + Search + Favorites + History + Settings + Update
        TvTopBar(
            onSearchClick = onSearchClick,
            onFavoritesClick = onFavoritesClick,
            onHistoryClick = onHistoryClick,
            onScheduleClick = onScheduleClick,
            onSettingsClick = onSettingsClick,
            onCheckUpdateClick = onCheckUpdateClick,
            hasUpdateAvailable = hasUpdateAvailable,
            appVersion = appVersion,
            currentScreenName = "home",
            topBarFocusRequester = topBarSearchFocusRequester,
            focusDownRequester = filterRow1FocusRequester
        )

        val currentDownRequester = if (displayMovies.isEmpty()) emptyResetFocusRequester else targetCardFocusRequester

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
            onResetFilters = { viewModel.resetFilters() },
            row1FocusRequester = filterRow1FocusRequester,
            row2FocusRequester = filterRow2FocusRequester,
            focusUpRequester = topBarSearchFocusRequester,
            focusDownRequester = currentDownRequester
        )

        // 6-COLUMN VERTICAL GRID (2 rows of 6 cards on screen at a time, scrolling down)
        if (isLoading && displayMovies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                NeonSpinner(size = 56.dp, strokeWidth = 4.dp, message = "Загрузка каталога ShowHub...")
            }
        } else if (displayMovies.isEmpty()) {
            LaunchedEffect(Unit) {
                delay(120)
                try { emptyResetFocusRequester.requestFocus() } catch (_: Exception) {}
            }
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.resetFilters() },
                        colors = ButtonDefaults.colors(
                            containerColor = LocalAccentColor.current,
                            focusedContainerColor = LocalFocusColor.current,
                            contentColor = Color.Black,
                            focusedContentColor = Color.Black
                        ),
                        modifier = Modifier
                            .focusRequester(emptyResetFocusRequester)
                            .focusProperties {
                                up = filterRow2FocusRequester
                            }
                    ) {
                        Text("Сбросить фильтры", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            TvLazyVerticalGrid(
                state = viewModel.gridState,
                columns = TvGridCells.Fixed(6),
                contentPadding = PaddingValues(start = 32.dp, top = 8.dp, end = 32.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(displayMovies, key = { _, movie -> movie.id }) { index, movie ->
                    val isTarget = index == viewModel.lastFocusedIndex.coerceIn(0, (displayMovies.size - 1).coerceAtLeast(0))
                    val targetMod = if (isTarget) Modifier.focusRequester(targetCardFocusRequester) else Modifier

                    val edgePropertiesMod = Modifier.focusProperties {
                        if (index < 6) {
                            up = filterRow2FocusRequester
                        }
                    }

                    MovieCard(
                        movie = movie,
                        onClick = {
                            viewModel.lastFocusedIndex = index
                            onMovieSelect(movie)
                        },
                        onFocus = {
                            viewModel.lastFocusedIndex = index
                        },
                        cardModifier = targetMod.then(edgePropertiesMod)
                    )
                }
            }
        }
    }
}
