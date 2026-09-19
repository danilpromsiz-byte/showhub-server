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
import androidx.tv.foundation.lazy.list.itemsIndexed
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
import com.example.tvmediaapp.data.models.SeasonInfo
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.components.NeonSpinner
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalBackgroundColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarEpisodeEntry(
    val movie: Movie,
    val scheduleItem: EpisodeScheduleItem,
    val isSummary: Boolean = false,
    val unwatchedCount: Int = 0
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

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading, calendarGroups) {
        if (!isLoading) {
            repeat(4) {
                delay(60)
                try {
                    if (calendarGroups.isNotEmpty()) {
                        firstItemFocusRequester.requestFocus()
                    } else {
                        backButtonFocusRequester.requestFocus()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val historyManager = WatchHistoryManager(context)
            val candidateMovies = mutableListOf<Movie>()

            // 1. Gather ONLY favorite series with disk cache check
            val seenIds = mutableSetOf<String>()
            for (m in trackedMovies) {
                if (m.isSeries && !seenIds.contains(m.id)) {
                    seenIds.add(m.id)
                    val cached = MediaDiskCache.getCachedDetails(m.id, m.title, m.releaseYear)
                    candidateMovies.add(cached ?: m)
                }
            }

            // Immediately display cached or existing schedule without waiting for network!
            val initialGroups = buildCalendarGroups(candidateMovies, historyManager)
            if (initialGroups.isNotEmpty()) {
                calendarGroups = initialGroups
                isLoading = false
            }

            // Concurrently fetch missing details for series that don't have schedule yet
            val missing = candidateMovies.filter { it.episodesSchedule.isEmpty() && it.seasons.isEmpty() }
            if (missing.isNotEmpty()) {
                val updatedMovies = candidateMovies.toMutableList()
                val fetched = coroutineScope {
                    missing.map { m ->
                        async(Dispatchers.IO) {
                            try {
                                ShowHubApiClient.fetchMediaDetails(m)
                            } catch (_: Exception) {
                                m
                            }
                        }
                    }.awaitAll()
                }

                for (item in fetched) {
                    val idx = updatedMovies.indexOfFirst { it.id == item.id }
                    if (idx != -1) {
                        updatedMovies[idx] = item
                        MediaDiskCache.putCachedDetails(item)
                    }
                }

                calendarGroups = buildCalendarGroups(updatedMovies, historyManager)
            }

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
                        text = "В избранном пока нет сериалов",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Добавляйте любимые сериалы в Избранное (кнопка со звездой), чтобы отслеживать график выхода серий.",
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
                itemsIndexed(calendarGroups) { gIdx, group ->
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
                            itemsIndexed(group.entries) { eIdx, entry ->
                                val cardMod = if (gIdx == 0 && eIdx == 0) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else Modifier
                                CalendarEpisodeCard(
                                    entry = entry,
                                    onClick = { onMovieSelect(entry.movie) },
                                    modifier = cardMod
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
            containerColor = if (entry.isSummary) Color(0xFF1E293B).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = focusColor.copy(alpha = 0.18f)
        ),
        border = CardDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, if (entry.isSummary) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f))),
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
                val badgeColor = when {
                    entry.isSummary -> Color(0xFFEA580C).copy(alpha = 0.95f) // Warm Orange
                    isOut -> Color(0xFF16A34A).copy(alpha = 0.9f) // Green
                    else -> Color(0xFFEAB308).copy(alpha = 0.9f) // Yellow
                }
                val badgeText = when {
                    entry.isSummary -> "Осталось: ${entry.unwatchedCount} сер."
                    isOut -> "Вышла"
                    else -> "Ожидается"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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

            // Episode info or next to watch
            Text(
                text = if (entry.isSummary) "Далее: ${entry.scheduleItem.episode}" else entry.scheduleItem.episode.ifEmpty { entry.scheduleItem.title },
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Date / action label
            val dateLabel = if (entry.isSummary) "Смотреть сериал →" else entry.scheduleItem.date
            if (dateLabel.isNotBlank()) {
                Text(
                    text = dateLabel,
                    fontSize = 9.sp,
                    color = if (entry.isSummary) Color(0xFF4ADE80) else TextGray,
                    fontWeight = if (entry.isSummary) FontWeight.SemiBold else FontWeight.Normal,
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

fun buildCalendarGroups(movies: List<Movie>, historyManager: WatchHistoryManager? = null): List<CalendarDayGroup> {
    val unwatchedSummaryEntries = mutableListOf<CalendarEpisodeEntry>()
    val todayEntries = mutableListOf<CalendarEpisodeEntry>()
    val tomorrowEntries = mutableListOf<CalendarEpisodeEntry>()
    val upcomingEntries = mutableListOf<CalendarEpisodeEntry>()
    val recentEntries = mutableListOf<CalendarEpisodeEntry>()

    for (m in movies) {
        // 1. Check unwatched episodes in seasons
        val unwatchedInSeasons = mutableListOf<Pair<SeasonInfo, com.example.tvmediaapp.data.models.EpisodeInfo>>()
        if (m.seasons.isNotEmpty()) {
            for (s in m.seasons) {
                for (ep in s.episodes) {
                    val isWatched = historyManager?.isEpisodeWatched(m.id, s.seasonNumber, ep.episodeNumber) ?: false
                    if (!isWatched) {
                        unwatchedInSeasons.add(s to ep)
                    }
                }
            }
        }

        // 2. Schedule items
        val sched = m.episodesSchedule
        val upcomingSched = mutableListOf<EpisodeScheduleItem>()
        val todaySched = mutableListOf<EpisodeScheduleItem>()
        val tomorrowSched = mutableListOf<EpisodeScheduleItem>()
        val otherSched = mutableListOf<EpisodeScheduleItem>()

        for (item in sched) {
            val dLower = (item.date + " " + item.status).lowercase()
            when {
                dLower.contains("сегодня") -> todaySched.add(item)
                dLower.contains("завтра") -> tomorrowSched.add(item)
                dLower.contains("ожидается") || anyFutureMonth(dLower) -> upcomingSched.add(item)
                else -> otherSched.add(item)
            }
        }

        // Smart aggregation: if > 2 unwatched episodes, single summary card
        if (unwatchedInSeasons.size > 2) {
            val firstUnwatched = unwatchedInSeasons.first()
            unwatchedSummaryEntries.add(
                CalendarEpisodeEntry(
                    movie = m,
                    scheduleItem = EpisodeScheduleItem(
                        episode = "${firstUnwatched.first.seasonNumber} сезон, ${firstUnwatched.second.episodeNumber} серия",
                        title = "Не просмотрено ещё ${unwatchedInSeasons.size} серий",
                        date = "В эфире",
                        status = "Не просмотрено"
                    ),
                    isSummary = true,
                    unwatchedCount = unwatchedInSeasons.size
                )
            )
        } else if (unwatchedInSeasons.isNotEmpty()) {
            // <= 2 unwatched episodes: schedule individually
            for (pair in unwatchedInSeasons) {
                recentEntries.add(
                    CalendarEpisodeEntry(
                        movie = m,
                        scheduleItem = EpisodeScheduleItem(
                            episode = "${pair.first.seasonNumber} сезон ${pair.second.episodeNumber} серия",
                            title = pair.second.title.ifEmpty { "Серия ${pair.second.episodeNumber}" },
                            date = "Доступна",
                            status = "Вышла"
                        )
                    )
                )
            }
        } else if (m.seasons.isEmpty() && otherSched.isNotEmpty()) {
            for (item in otherSched.take(2)) {
                recentEntries.add(CalendarEpisodeEntry(m, item))
            }
        }

        // Smart aggregation for upcoming: if > 2 upcoming, aggregate; else individual
        if (upcomingSched.size > 2) {
            val nextUp = upcomingSched.first()
            upcomingEntries.add(
                CalendarEpisodeEntry(
                    movie = m,
                    scheduleItem = EpisodeScheduleItem(
                        episode = nextUp.episode,
                        title = "Ожидается ещё ${upcomingSched.size} серий",
                        date = nextUp.date,
                        status = "Ожидается"
                    ),
                    isSummary = true,
                    unwatchedCount = upcomingSched.size
                )
            )
        } else {
            for (item in upcomingSched) {
                upcomingEntries.add(CalendarEpisodeEntry(m, item))
            }
        }

        for (item in todaySched) {
            todayEntries.add(CalendarEpisodeEntry(m, item))
        }
        for (item in tomorrowSched) {
            tomorrowEntries.add(CalendarEpisodeEntry(m, item))
        }
    }

    val groups = mutableListOf<CalendarDayGroup>()
    if (unwatchedSummaryEntries.isNotEmpty()) {
        groups.add(CalendarDayGroup("Не просмотрено в избранном", unwatchedSummaryEntries))
    }
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
        groups.add(CalendarDayGroup("Недавно вышли / Доступны", recentEntries.take(25)))
    }
    return groups
}
