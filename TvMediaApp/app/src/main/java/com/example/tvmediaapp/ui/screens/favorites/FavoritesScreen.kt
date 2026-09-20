@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
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
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.tv.foundation.lazy.grid.itemsIndexed
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onMovieSelect: (Movie) -> Unit,
    onBackClick: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val favTrigger by viewModel.favoriteChangeTrigger.collectAsState()
    val favoriteMovies = remember(favTrigger) {
        viewModel.getFavoriteMovies()
    }

    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current
    val bgColor = LocalBackgroundColor.current
    val firstCardFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(favoriteMovies) {
        if (favoriteMovies.isNotEmpty()) {
            delay(150)
            try {
                firstCardFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
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
                    text = "Избранное",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                if (favoriteMovies.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "(${favoriteMovies.size})",
                        fontSize = 20.sp,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = focusColor,
                    contentColor = TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusRequester(backButtonFocusRequester)
                    .focusProperties {
                        if (favoriteMovies.isNotEmpty()) {
                            down = firstCardFocusRequester
                        }
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.tvmediaapp.ui.components.AppIcon(
                        iconResId = com.example.tvmediaapp.R.drawable.ic_arrow_back,
                        contentDescription = "Назад",
                        modifier = Modifier.padding(end = 4.dp),
                        tint = Color.Unspecified,
                        size = 13.dp
                    )
                    Text(
                        text = "Назад",
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
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
                        text = "В избранном пока ничего нет",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Нажимайте «В избранное» на карточке фильма, чтобы быстро находить его.",
                        fontSize = 14.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                contentPadding = PaddingValues(bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(favoriteMovies, key = { _, movie -> movie.id }) { index, movie ->
                    val cardFocusMod = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                        .then(if (index < 6) Modifier.focusProperties { up = backButtonFocusRequester } else Modifier)

                    MovieCard(
                        movie = movie,
                        onClick = { onMovieSelect(movie) },
                        onFocus = {},
                        cardModifier = cardFocusMod
                    )
                }
            }
        }
    }
}

