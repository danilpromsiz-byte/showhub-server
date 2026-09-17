package com.example.tvmediaapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.FeaturedMovieBanner
import com.example.tvmediaapp.ui.components.FilterBar
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.components.TvTopBar
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieSelect: (Movie) -> Unit,
    onWatchClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val favTrigger by viewModel.favoriteChangeTrigger.collectAsState()

    var featuredMovie by remember { mutableStateOf<Movie?>(null) }

    // Auto-select first movie for hero spotlight if none or if category switched
    if (featuredMovie == null || categories.none { cat -> cat.movies.any { it.id == featuredMovie?.id } }) {
        val firstAvailable = categories.firstOrNull { it.movies.isNotEmpty() }?.movies?.firstOrNull()
        if (firstAvailable != null) {
            featuredMovie = firstAvailable
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // TOP NAVIGATION BAR
        TvTopBar(
            selectedTabId = selectedTab,
            onTabSelected = { tabId ->
                if (tabId == "search") {
                    onSearchClick()
                } else {
                    viewModel.selectTab(tabId)
                }
            }
        )

        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // 1. Hero Spotlight Preview Banner
            item {
                val isFav = featuredMovie?.let { viewModel.isFavorite(it.id) } ?: false
                FeaturedMovieBanner(
                    movie = featuredMovie,
                    onWatchClick = { onWatchClick(it) },
                    onDetailsClick = { onMovieSelect(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    isFavorite = isFav
                )
            }

            // 2. Filter & Sorting Ribbon (only when not in favorites)
            if (selectedTab != "favorites") {
                item {
                    FilterBar(
                        selectedGenre = selectedGenre,
                        onGenreSelected = { viewModel.selectGenre(it) },
                        selectedSort = selectedSort,
                        onSortSelected = { viewModel.selectSort(it) }
                    )
                }
            }

            // 3. Movie Rows / Categories
            if (categories.isEmpty() || categories.all { it.movies.isEmpty() }) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp, start = 48.dp)
                    ) {
                        Text(
                            text = if (selectedTab == "favorites") "\u0423 \u0432\u0430\u0441 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442 \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u043d\u044b\u0445 \u0444\u0438\u043b\u044c\u043c\u043e\u0432 \u0432 \u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435." else "\u041f\u043e \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u044b\u043c \u0444\u0438\u043b\u044c\u0442\u0440\u0430\u043c \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e.",
                            fontSize = 18.sp,
                            color = TextWhite
                        )
                    }
                }
            } else {
                items(categories) { category ->
                    if (category.movies.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(
                                text = category.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                            )

                            TvLazyRow(
                                contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(category.movies) { movie ->
                                    MovieCard(
                                        movie = movie,
                                        onClick = { onMovieSelect(movie) },
                                        onFocus = { featuredMovie = movie }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
