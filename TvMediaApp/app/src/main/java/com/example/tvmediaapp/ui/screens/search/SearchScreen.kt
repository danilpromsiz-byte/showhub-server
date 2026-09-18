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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.MovieCard
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.launch
import org.json.JSONArray

val POPULAR_QUERIES = listOf(
    "Пожиратель звёзд",
    "Слово пацана",
    "Мастер и Маргарита",
    "Дюна",
    "Интерстеллар",
    "Оппенгеймер",
    "Джентльмены",
    "Триггер",
    "Головоломка 2"
)

val RU_KEYBOARD_ROW1 = listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К", "Л", "М", "Н", "О", "П", "Р")
val RU_KEYBOARD_ROW2 = listOf("С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Э", "Ю", "Я", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieSelect: (Movie) -> Unit,
    onBackClick: () -> Unit,
    initialMovies: List<Movie> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
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
        saveQuery(q)
        recentQueries = loadHistory()
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
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Поиск",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = accent,
                    contentColor = TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border(border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                    focusedBorder = Border(border = BorderStroke(2.dp, TextWhite))
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.tvmediaapp.ui.components.AppIcon(
                        iconResId = com.example.tvmediaapp.R.drawable.ic_arrow_back,
                        contentDescription = "Назад",
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "Назад",
                        fontSize = 13.sp
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
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .border(
                    width = 1.5.dp,
                    color = if (isInputFocused) accent else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp),
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Введите название фильма или сериала...",
                                    color = TextGray,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused }
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(text = "Очистить", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // On-screen TV Remote Keyboard Rows
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(RU_KEYBOARD_ROW1) { char ->
                Button(
                    onClick = { performSearch(query + char) },
                    colors = ButtonDefaults.colors(
                        containerColor = ChipBackground,
                        focusedContainerColor = accent,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = char, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(RU_KEYBOARD_ROW2) { char ->
                Button(
                    onClick = { performSearch(query + char) },
                    colors = ButtonDefaults.colors(
                        containerColor = ChipBackground,
                        focusedContainerColor = accent,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = char, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Button(
                    onClick = { performSearch(query + " ") },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = accent,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = "Пробел", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Button(
                    onClick = {
                        if (query.isNotEmpty()) performSearch(query.dropLast(1))
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = accent,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = "Стереть", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Button(
                    onClick = { performSearch("") },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Red.copy(alpha = 0.3f),
                        focusedContainerColor = Color.Red,
                        contentColor = TextWhite,
                        focusedContentColor = TextWhite
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = "Очистить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Popular Quick Queries
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(POPULAR_QUERIES) { itemQuery ->
                val isSelected = query == itemQuery
                Button(
                    onClick = { performSearch(itemQuery) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) accent else ChipBackground,
                        focusedContainerColor = accent,
                        contentColor = if (isSelected) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = itemQuery,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
            // Results Grid (6 columns)
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(6),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
}
