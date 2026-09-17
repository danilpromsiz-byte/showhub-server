package com.example.tvmediaapp.ui.screens.home

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.FeaturedMovieBanner
import com.example.tvmediaapp.ui.components.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieSelect: (Movie) -> Unit,
    onWatchClick: (Movie) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val categories by viewModel.categories.collectAsState()
    var featuredMovie by remember { mutableStateOf<Movie?>(null) }

    // Initialize featured movie with first available item
    if (featuredMovie == null && categories.isNotEmpty() && categories[0].movies.isNotEmpty()) {
        featuredMovie = categories[0].movies[0]
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // Top Featured Banner
        item {
            FeaturedMovieBanner(
                movie = featuredMovie,
                onWatchClick = { onWatchClick(it) }
            )
        }

        // Horizontal rows for each category
        items(categories) { category ->
            Column(
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
