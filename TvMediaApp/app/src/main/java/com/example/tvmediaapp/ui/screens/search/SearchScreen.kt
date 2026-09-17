package com.example.tvmediaapp.ui.screens.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.SurfaceDark
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.launch

val POPULAR_QUERIES = listOf(
    "\u041f\u043e\u0436\u0438\u0440\u0430\u0442\u0435\u043b\u044c \u0437\u0432\u0451\u0437\u0434",
    "\u0421\u043b\u043e\u0432\u043e \u043f\u0430\u0446\u0430\u043d\u0430",
    "\u041c\u0430\u0441\u0442\u0435\u0440 \u0438 \u041c\u0430\u0440\u0433\u0430\u0440\u0438\u0442\u0430",
    "\u0414\u044e\u043d\u0430",
    "\u0418\u043d\u0442\u0435\u0440\u0441\u0442\u0435\u043b\u043b\u0430\u0440",
    "\u041e\u043f\u043f\u0435\u043d\u0433\u0435\u0439\u043c\u0435\u0440",
    "\u0414\u0436\u0435\u043d\u0442\u043b\u044c\u043c\u0435\u043d\u044b",
    "\u0422\u0440\u0438\u0433\u0433\u0435\u0440",
    "\u0413\u043e\u043b\u043e\u0432\u043e\u043b\u043e\u043c\u043a\u0430 2"
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieSelect: (Movie) -> Unit,
    onBackClick: () -> Unit,
    initialMovies: List<Movie> = emptyList(),
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(initialMovies) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun performSearch(q: String) {
        query = q
        if (q.trim().isEmpty()) {
            results = initialMovies
            return
        }
        isSearching = true
        coroutineScope.launch {
            val res = ShowHubApiClient.searchMovies(q)
            results = if (res.isNotEmpty()) res else initialMovies.filter {
                it.title.contains(q, ignoreCase = true) || it.originalTitle.contains(q, ignoreCase = true)
            }
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\ud83d\udd0d \u041f\u043e\u0438\u0441\u043a \u0444\u0438\u043b\u044c\u043c\u043e\u0432 \u0438 \u0441\u0435\u0440\u0438\u0430\u043b\u043e\u0432",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = ": \"$query\"",
                        fontSize = 22.sp,
                        color = CyanNeon,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = onBackClick
            ) {
                Text(
                    text = "\u2190 \u041d\u0430\u0437\u0430\u0434",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Popular Quick Queries
        Text(
            text = "\u041f\u043e\u043f\u0443\u043b\u044f\u0440\u043d\u044b\u0435 \u0437\u0430\u043f\u0440\u043e\u0441\u044b:",
            fontSize = 14.sp,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(8.dp))

        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(POPULAR_QUERIES) { itemQuery ->
                val isSelected = query == itemQuery
                Button(
                    onClick = { performSearch(itemQuery) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) CyanNeon else ChipBackground,
                        focusedContainerColor = CyanNeon,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = itemQuery,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isSearching) {
            Text(
                text = "\u23f3 \u041f\u043e\u0438\u0441\u043a \u043f\u043e \u0432\u0441\u0435\u043c \u0431\u0430\u0437\u0430\u043c \u0434\u0430\u043d\u043d\u044b\u0445 ShowHub...",
                color = CyanNeon,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Results Grid
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(results) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onMovieSelect(movie) },
                    onFocus = {}
                )
            }
        }
    }
}
