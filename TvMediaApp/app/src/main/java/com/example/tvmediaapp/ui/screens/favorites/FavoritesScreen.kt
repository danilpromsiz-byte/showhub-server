package com.example.tvmediaapp.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.screens.home.HomeViewModel
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.FavoriteGold
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onMovieSelect: (Movie) -> Unit,
    onBackClick: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val favTrigger by viewModel.favoriteChangeTrigger.collectAsState()
    val allMovies = remember(favTrigger) { viewModel.getAllMovies() }
    val favoriteMovies = remember(favTrigger, allMovies) {
        allMovies.filter { viewModel.isFavorite(it.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\u2b50 \u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                if (favoriteMovies.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "(${favoriteMovies.size})",
                        fontSize = 20.sp,
                        color = FavoriteGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = onBackClick,
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = "\u2190 \u041d\u0430\u0437\u0430\u0434",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (favoriteMovies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u2b50",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\u0412 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u043c \u043f\u043e\u043a\u0430 \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435\u0442",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\u041d\u0430\u0436\u0438\u043c\u0430\u0439\u0442\u0435 \u00ab\u0412 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435\u00bb \u043d\u0430 \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0435 \u0444\u0438\u043b\u044c\u043c\u0430, \u0447\u0442\u043e\u0431\u044b \u0431\u044b\u0441\u0442\u0440\u043e \u043d\u0430\u0445\u043e\u0434\u0438\u0442\u044c \u0435\u0433\u043e.",
                        fontSize = 14.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favoriteMovies) { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = { onMovieSelect(movie) },
                        onFocus = {}
                    )
                }
            }
        }
    }
}
