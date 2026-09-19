@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)

package com.example.tvmediaapp.ui.screens.search

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.KeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieSelect: (Movie) -> Unit,
    onBackClick: () -> Unit,
    initialMovies: List<Movie> = emptyList(),
    initialQuery: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current
    val bgColor = LocalBackgroundColor.current
    val searchPrefs = remember { context.getSharedPreferences("showhub_search_history", Context.MODE_PRIVATE) }

    fun loadHistory(): List<String> {
        val raw = searchPrefs.getString("queries", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(arr.getString(i))
        } catch (e: Exception) {}
        return list
    }

    fun saveQuery(q: String) {
        if (q.trim().isEmpty()) return
        val list = loadHistory().toMutableList()
        list.removeAll { it.equals(q.trim(), ignoreCase = true) }
        list.add(0, q.trim())
        val arr = JSONArray()
        list.take(10).forEach { arr.put(it) }
        searchPrefs.edit().putString("queries", arr.toString()).apply()
    }

    var recentQueries by remember { mutableStateOf(loadHistory()) }
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf(initialMovies) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val searchInputFocusRequester = remember { FocusRequester() }
    val historyFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            searchInputFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun performSearch(q: String) {
        query = q
        if (q.trim().isEmpty()) {
            results = initialMovies
            return
        }
        saveQuery(q)
        recentQueries = loadHistory()
        isSearching = true
        coroutineScope.launch {
            val res = ShowHubApiClient.searchMovies(q)
            results = if (res.isNotEmpty()) res else initialMovies.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.originalTitle.contains(q, ignoreCase = true) ||
                it.actors.contains(q, ignoreCase = true) ||
                it.director.contains(q, ignoreCase = true)
            }
            isSearching = false
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            performSearch(initialQuery)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (initialQuery.isNotBlank()) "Фильмография: $initialQuery" else "Поиск",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

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
                modifier = Modifier.height(28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.tvmediaapp.ui.components.AppIcon(
                        iconResId = com.example.tvmediaapp.R.drawable.ic_arrow_back,
                        contentDescription = "Назад",
                        modifier = Modifier.padding(end = 4.dp),
                        tint = TextWhite,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input Bar Container
        var isInputFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .border(
                    width = 1.5.dp,
                    color = if (isInputFocused) accent else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { performSearch(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Введите название фильма или сериала...",
                                    color = TextGray,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchInputFocusRequester)
                            .focusProperties {
                                down = if (recentQueries.isNotEmpty()) historyFocusRequester else resultsFocusRequester
                            }
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (recentQueries.isNotEmpty()) {
                                                try { historyFocusRequester.requestFocus(); true } catch (_: Exception) { false }
                                            } else if (results.isNotEmpty()) {
                                                try { resultsFocusRequester.requestFocus(); true } catch (_: Exception) { false }
                                            } else false
                                        }
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_DPAD_CENTER -> {
                                            if (results.isNotEmpty()) {
                                                try { resultsFocusRequester.requestFocus(); true } catch (_: Exception) { false }
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                }

                if (query.isNotEmpty()) {
                    Button(
                        onClick = { performSearch("") },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color.Red,
                            contentColor = TextWhite,
                            focusedContentColor = TextWhite
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(text = "Очистить", fontSize = 11.sp, lineHeight = 13.sp)
                    }
                }
            }
        }

        // Search History Chips
        if (recentQueries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "История:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGray
                )
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(recentQueries) { hIdx, histQuery ->
                        val isSelected = query == histQuery
                        val chipMod = if (hIdx == 0) {
                            Modifier
                                .height(28.dp)
                                .focusRequester(historyFocusRequester)
                                .focusProperties {
                                    up = searchInputFocusRequester
                                    down = resultsFocusRequester
                                }
                        } else {
                            Modifier
                                .height(28.dp)
                                .focusProperties {
                                    up = searchInputFocusRequester
                                    down = resultsFocusRequester
                                }
                        }
                        Button(
                            onClick = { performSearch(histQuery) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent.copy(alpha = 0.85f) else ChipBackground,
                                focusedContainerColor = focusColor,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = chipMod
                        ) {
                            Text(
                                text = histQuery,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                searchPrefs.edit().remove("queries").apply()
                                recentQueries = emptyList()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = focusColor,
                                contentColor = TextGray,
                                focusedContentColor = Color(0xFFE53935)
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Очистить историю",
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                NeonSpinner(size = 48.dp, strokeWidth = 3.5.dp, message = "Поиск по всем источникам ShowHub...")
            }
        } else if (results.isEmpty() && query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "По запросу '$query' ничего не найдено",
                    color = TextGray,
                    fontSize = 15.sp
                )
            }
        } else {
            // Results Grid (6 columns) with generous TV bottom padding
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                contentPadding = PaddingValues(bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results) { rIdx, movie ->
                    val cardMod = if (rIdx == 0) {
                        Modifier
                            .focusRequester(resultsFocusRequester)
                            .focusProperties {
                                up = if (recentQueries.isNotEmpty()) historyFocusRequester else searchInputFocusRequester
                            }
                    } else if (rIdx < 6) {
                        Modifier.focusProperties {
                            up = if (recentQueries.isNotEmpty()) historyFocusRequester else searchInputFocusRequester
                        }
                    } else {
                        Modifier
                    }
                    MovieCard(
                        movie = movie,
                        onClick = { onMovieSelect(movie) },
                        onFocus = {},
                        modifier = cardMod
                    )
                }
            }
        }
    }
}
