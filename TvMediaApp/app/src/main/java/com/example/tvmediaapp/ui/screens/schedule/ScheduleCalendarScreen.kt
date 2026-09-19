package com.example.tvmediaapp.ui.screens.schedule

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.cache.MediaDiskCache
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.EpisodeScheduleItem
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarEpisodeEntry(
    val movie: Movie,
    val scheduleItem: EpisodeScheduleItem
)

data class CalendarDayGroup(
    val dayTitle: String,
    val entries: List<CalendarEpisodeEntry>
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScheduleCalendarScreen(
    onMovieSelect: (Movie) -> Unit,
    onBackClick: () -> Unit,
    trackedMovies: List<Movie> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current
    val bgColor = LocalBackgroundColor.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var calendarGroups by remember { mutableStateOf<List<CalendarDayGroup>>(emptyList()) }
    val backButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val historyManager = WatchHistoryManager(context)
            val historyItems = historyManager.getHistory()
            val candidateMovies = mutableListOf<Movie>()

            // 1. Gather movies from history and trackedMovies
            val seenIds = mutableSetOf<String>()
            for (m in trackedMovies) {
                if (m.isSeries && !seenIds.contains(m.id)) {
                    seenIds.add(m.id)
                    candidateMovies.add(m)
                }
            }
            for (h in historyItems) {
                if (h.isSeries && !seenIds.contains(h.id)) {
                    seenIds.add(h.id)
                    val cached = MediaDiskCache.getCachedDetails(h.id, h.title, h.releaseYear)
                    if (cached != null) {
                        candidateMovies.add(cached)
                    } else {
                        candidateMovies.add(
                            Movie(
                                id = h.id,
                                title = h.title,
                                description = "",
                                posterUrl = h.posterUrl,
                                backdropUrl = h.backdropUrl,
                                rating = 0.0,
                                releaseYear = h.releaseYear,
                                duration = "",
                                isSeries = true
                            )
                        )
                    }
                }
            }

            // 2. Fetch rich details with schedule for candidate series if missing
            val fullSeries = candidateMovies.map { m ->
                if (m.episodesSchedule.isNotEmpty()) {
                    m
                } else {
                    try {
                        ShowHubApiClient.fetchMediaDetails(m)
                    } catch (_: Exception) {
                        m
                    }
                }
            }

            // 3. Group by schedule status and date
            val todayEntries = mutableListOf<CalendarEpisodeEntry>()
            val tomorrowEntries = mutableListOf<CalendarEpisodeEntry>()
            val upcomingEntries = mutableListOf<CalendarEpisodeEntry>()
            val recentEntries = mutableListOf<CalendarEpisodeEntry>()

            for (m in fullSeries) {
                val sched = m.episodesSchedule
                if (sched.isNotEmpty()) {
                    for (item in sched) {
                        val entry = CalendarEpisodeEntry(m, item)
                        val dLower = (item.date + " " + item.status).lowercase()
                        when {
                            dLower.contains("сегодня") -> todayEntries.add(entry)
                            dLower.contains("завтра") -> tomorrowEntries.add(entry)
                            dLower.contains("ожидается") || anyFutureMonth(dLower) -> upcomingEntries.add(entry)
                            else -> recentEntries.add(entry)
                        }
                    }
                } else if (m.seasons.isNotEmpty()) {
                    // Fallback from seasons
                    val lastSeason = m.seasons.lastOrNull()
                    if (lastSeason != null) {
                        val lastEps = lastSeason.episodes.takeLast(3)
                        for (ep in lastEps) {
                            recentEntries.add(
                                CalendarEpisodeEntry(
                                    movie = m,
                                    scheduleItem = EpisodeScheduleItem(
                                        episode = "${lastSeason.seasonNumber} сезон ${ep.episodeNumber} серия",
                                        title = ep.title,
                                        date = "Недавно",
                                        status = "Вышла"
                                    )
                                )
                            )
                        }
                    }
                }
            }

            val groups = mutableListOf<CalendarDayGroup>()
            if (todayEntries.isNotEmpty()) {
                groups.add(CalendarDayGroup("Сегодня", todayEntries.take(15)))
            }
            if (tomorrowEntries.isNotEmpty()) {
                groups.add(CalendarDayGroup("Завтра", tomorrowEntries.take(15)))
            }
            if (upcomingEntries.isNotEmpty()) {
                groups.add(CalendarDayGroup("Скоро выйдут", upcomingEntries.take(20)))
            }
            if (recentEntries.isNotEmpty()) {
                groups.add(CalendarDayGroup("Недавно вышли", recentEntries.take(25)))
            }

            calendarGroups = groups
            isLoading = false
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(
                    resId = R.drawable.ic_calendar,
                    tint = accent,
                    size = 28.dp
                )
                Text(
                    text = "Календарь выхода серий",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }

            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = focusColor,
                    contentColor = TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(Border.None, Border.None),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .focusRequester(backButtonFocusRequester)
            ) {
                Text(text = "Назад", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                NeonSpinner(size = 48.dp)
            }
        } else if (calendarGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(
                        resId = R.drawable.ic_calendar,
                        tint = TextGray,
                        size = 56.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "График серий пока пуст",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Добавляйте сериалы в избранное или начинайте просмотр, чтобы отслеживать новые серии.",
                        fontSize = 12.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                items(calendarGroups) { group ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .background(accent, RoundedCornerShape(2.dp))
                            )
                            Text(
                                text = group.dayTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TvLazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(group.entries) { entry ->
                                CalendarEpisodeCard(
                                    entry = entry,
                                    onClick = { onMovieSelect(entry.movie) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CalendarEpisodeCard(
    entry: CalendarEpisodeEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = focusColor.copy(alpha = 0.18f)
        ),
        border = CardDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, focusColor))
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
        modifier = modifier
            .width(180.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                if (entry.movie.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = entry.movie.posterUrl,
                        contentDescription = entry.movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Status Badge
                val isOut = entry.scheduleItem.status.contains("Вышла", ignoreCase = true) || entry.scheduleItem.status.contains("Доступна", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(
                            if (isOut) Color(0xFF16A34A).copy(alpha = 0.9f) else Color(0xFFEAB308).copy(alpha = 0.9f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isOut) "Вышла" else "Ожидается",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOut) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Movie title
            Text(
                text = entry.movie.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) focusColor else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Episode info
            Text(
                text = entry.scheduleItem.episode.ifEmpty { entry.scheduleItem.title },
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Date
            if (entry.scheduleItem.date.isNotBlank()) {
                Text(
                    text = entry.scheduleItem.date,
                    fontSize = 9.sp,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun anyFutureMonth(s: String): Boolean {
    val months = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")
    return months.any { s.contains(it) }
}
