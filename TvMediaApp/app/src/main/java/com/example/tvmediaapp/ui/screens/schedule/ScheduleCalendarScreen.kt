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
import androidx.compose.ui.focus.focusProperties
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
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
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
            val missing = candidateMovies.filter { it.episodesSchedule.isEmpty() }
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
                    .focusProperties {
                        down = firstItemFocusRequester
                    }
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
            val listState = rememberTvLazyListState()
            TvLazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                itemsIndexed(calendarGroups, key = { _, group -> group.dayTitle }) { gIdx, group ->
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
                            itemsIndexed(
                                group.entries,
                                key = { idx, entry -> "${entry.movie.id}_${entry.scheduleItem.episode}_${entry.scheduleItem.date}_$idx" }
                            ) { eIdx, entry ->
                                val cardMod = if (gIdx == 0 && eIdx == 0) {
                                    Modifier
                                        .focusRequester(firstItemFocusRequester)
                                        .focusProperties {
                                            up = backButtonFocusRequester
                                        }
                                } else if (gIdx == 0) {
                                    Modifier.focusProperties {
                                        up = backButtonFocusRequester
                                    }
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
            border = Border(androidx.compose.foundation.BorderStroke(1.5.dp, if (entry.isSummary) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, focusColor))
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
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
                val isEpisodeAlreadyInMovie = entry.movie.seasons.any { s ->
                    val epMatch = Regex("""(?:(\d+)\s+сезон)?.*?(\d+)\s+серия""").find(entry.scheduleItem.episode)
                    if (epMatch != null) {
                        val sNum = epMatch.groupValues[1].takeIf { it.isNotBlank() }?.toIntOrNull() ?: s.seasonNumber
                        val epNum = epMatch.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                        s.seasonNumber == sNum && s.episodes.any { it.episodeNumber == epNum }
                    } else false
                }
                val isOut = entry.scheduleItem.status.contains("Вышла", ignoreCase = true) ||
                    entry.scheduleItem.status.contains("Доступна", ignoreCase = true) ||
                    entry.scheduleItem.status.contains("✓") ||
                    isEpisodeAlreadyInMovie ||
                    isDatePast(entry.scheduleItem.date)

                val isToday = isDateToday(entry.scheduleItem.status) || isDateToday(entry.scheduleItem.date)
                val isTomorrow = isDateTomorrow(entry.scheduleItem.status) || isDateTomorrow(entry.scheduleItem.date)

                val (badgeText, badgeColor) = when {
                    entry.isSummary -> "Осталось: ${entry.unwatchedCount} сер." to Color(0xFFEA580C).copy(alpha = 0.95f) // Warm Orange
                    isOut -> "Вышла" to Color(0xFF16A34A).copy(alpha = 0.9f) // Green
                    isToday -> "Сегодня" to Color(0xFF0284C7).copy(alpha = 0.9f) // Sky Blue
                    isTomorrow -> "Завтра" to Color(0xFF8B5CF6).copy(alpha = 0.9f) // Purple
                    else -> "Ожидается" to Color(0xFFEAB308).copy(alpha = 0.9f) // Yellow
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
            val nextUpcoming = entry.movie.episodesSchedule.firstOrNull {
                isDateFuture(it.date) || isDateFuture(it.status) || isDateToday(it.date) || isDateToday(it.status) || isDateTomorrow(it.date) || isDateTomorrow(it.status)
            }
            val dateLabel = when {
                entry.isSummary && nextUpcoming != null -> {
                    val datePart = if (nextUpcoming.date.isNotBlank() && nextUpcoming.date != "Дата уточняется") nextUpcoming.date else nextUpcoming.status
                    "След: $datePart"
                }
                entry.isSummary -> "Смотреть сериал →"
                else -> entry.scheduleItem.date
            }
            if (dateLabel.isNotBlank()) {
                Text(
                    text = dateLabel,
                    fontSize = 9.sp,
                    color = if (entry.isSummary && nextUpcoming != null) Color(0xFFFFB800) else if (entry.isSummary) Color(0xFF4ADE80) else TextGray,
                    fontWeight = if (entry.isSummary) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun parseDateCal(dateStr: String): java.util.Calendar? {
    val dLower = dateStr.lowercase()
    val match = Regex("""(\d{1,2})\s+([а-я]+)(?:\s+(\d{4}))?""").find(dLower) ?: return null
    val dayStr = match.groupValues[1]
    val monthRu = match.groupValues[2]
    val yearStr = match.groupValues.getOrNull(3).orEmpty()
    val day = dayStr.toIntOrNull() ?: return null
    val currentCal = java.util.Calendar.getInstance()
    val year = if (yearStr.isNotBlank()) (yearStr.toIntOrNull() ?: currentCal.get(java.util.Calendar.YEAR)) else currentCal.get(java.util.Calendar.YEAR)
    val monthIdx = when {
        monthRu.startsWith("янв") -> 0
        monthRu.startsWith("фев") -> 1
        monthRu.startsWith("мар") -> 2
        monthRu.startsWith("апр") -> 3
        monthRu.startsWith("ма") -> 4
        monthRu.startsWith("июн") -> 5
        monthRu.startsWith("июл") -> 6
        monthRu.startsWith("авг") -> 7
        monthRu.startsWith("сен") -> 8
        monthRu.startsWith("окт") -> 9
        monthRu.startsWith("ноя") -> 10
        monthRu.startsWith("дек") -> 11
        else -> return null
    }
    return java.util.Calendar.getInstance().apply {
        set(year, monthIdx, day, 12, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
}

fun isDatePast(dateStr: String): Boolean {
    val cal = parseDateCal(dateStr)
    val todayCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    if (cal != null) {
        return cal.before(todayCal)
    }
    val dLower = dateStr.lowercase()
    if (dLower.contains("вчера") || dLower.contains("вышла") || dLower.contains("доступна") || dLower.contains("✓")) return true
    return false
}

fun isDateFuture(dateStr: String): Boolean {
    val cal = parseDateCal(dateStr)
    val todayEndCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 23)
        set(java.util.Calendar.MINUTE, 59)
        set(java.util.Calendar.SECOND, 59)
        set(java.util.Calendar.MILLISECOND, 999)
    }
    if (cal != null) {
        return cal.after(todayEndCal)
    }
    val dLower = dateStr.lowercase()
    if (dLower.contains("завтра") || dLower.contains("через") || dLower.contains("ожидается") || dLower.contains("скоро")) return true
    return false
}

fun isDateToday(dateStr: String): Boolean {
    val cal = parseDateCal(dateStr)
    if (cal != null) {
        val today = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
               cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    }
    val dLower = dateStr.lowercase()
    if (dLower.contains("сегодня") && !dLower.contains("завтра") && !dLower.contains("вчера")) return true
    return false
}

fun isDateTomorrow(dateStr: String): Boolean {
    val cal = parseDateCal(dateStr)
    if (cal != null) {
        val tmrw = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        return cal.get(java.util.Calendar.YEAR) == tmrw.get(java.util.Calendar.YEAR) &&
               cal.get(java.util.Calendar.DAY_OF_YEAR) == tmrw.get(java.util.Calendar.DAY_OF_YEAR)
    }
    val dLower = dateStr.lowercase()
    if (dLower.contains("завтра") && !dLower.contains("сегодня")) return true
    return false
}

fun buildCalendarGroups(movies: List<Movie>, historyManager: WatchHistoryManager? = null): List<CalendarDayGroup> {
    val unwatchedSummaryEntries = mutableListOf<CalendarEpisodeEntry>()
    val todayEntries = mutableListOf<CalendarEpisodeEntry>()
    val tomorrowEntries = mutableListOf<CalendarEpisodeEntry>()
    val upcomingEntries = mutableListOf<CalendarEpisodeEntry>()
    val recentEntries = mutableListOf<CalendarEpisodeEntry>()

    for (m in movies) {
        val lastWatched = historyManager?.getLastWatchedEpisode(m.id, m.title)

        // 1. Check unwatched episodes for current/target season (counting strictly forward from last watched episode)
        if (m.seasons.isNotEmpty()) {
            val targetSeason = lastWatched?.first ?: m.seasons.maxOfOrNull { it.seasonNumber } ?: 1
            val targetSeasonObj = m.seasons.firstOrNull { it.seasonNumber == targetSeason }
            val lwEp = if (lastWatched != null && lastWatched.first == targetSeason) lastWatched.second else if (lastWatched != null && lastWatched.first > targetSeason) Int.MAX_VALUE else 0

            val targetUnwatched = targetSeasonObj?.episodes?.filter { ep ->
                ep.episodeNumber > lwEp && !(historyManager?.isEpisodeWatched(m.id, targetSeason, ep.episodeNumber, m.title) ?: false)
            } ?: emptyList()

            if (targetUnwatched.size > 2) {
                val firstUnwatched = targetUnwatched.first()
                unwatchedSummaryEntries.add(
                    CalendarEpisodeEntry(
                        movie = m,
                        scheduleItem = EpisodeScheduleItem(
                            episode = "${targetSeason} сезон, ${firstUnwatched.episodeNumber} серия",
                            title = "${targetSeason} сезон: не просмотрено ${targetUnwatched.size} сер.",
                            date = "В эфире",
                            status = "Не просмотрено"
                        ),
                        isSummary = true,
                        unwatchedCount = targetUnwatched.size
                    )
                )
            } else if (targetUnwatched.isNotEmpty()) {
                for (ep in targetUnwatched) {
                    recentEntries.add(
                        CalendarEpisodeEntry(
                            movie = m,
                            scheduleItem = EpisodeScheduleItem(
                                episode = "${targetSeason} сезон ${ep.episodeNumber} серия",
                                title = ep.title.ifEmpty { "Серия ${ep.episodeNumber}" },
                                date = "Доступна",
                                status = "Вышла"
                            )
                        )
                    )
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

            // Check if this episode is already released or watched
            var isAlreadyReleasedOrWatched = false
            val epMatch = Regex("""(?:(\d+)\s+сезон)?.*?(\d+)\s+серия""").find(item.episode)
            if (epMatch != null) {
                val sNum = epMatch.groupValues[1].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
                val epNum = epMatch.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                val inSeasons = m.seasons.any { s -> s.seasonNumber == sNum && s.episodes.any { it.episodeNumber == epNum } }
                val isWatched = lastWatched != null && (lastWatched.first > sNum || (lastWatched.first == sNum && lastWatched.second >= epNum))
                if (inSeasons || isWatched) {
                    isAlreadyReleasedOrWatched = true
                }
            }

            val cal = parseDateCal(item.date)
            when {
                cal != null && isDatePast(item.date) -> otherSched.add(item)
                isDateToday(item.date) -> todaySched.add(item)
                isDateTomorrow(item.date) -> tomorrowSched.add(item)
                isDateFuture(item.date) -> upcomingSched.add(item)
                cal == null && isDateToday(item.status) -> todaySched.add(item)
                cal == null && isDateTomorrow(item.status) -> tomorrowSched.add(item)
                cal == null && isDateFuture(item.status) -> upcomingSched.add(item)
                !isAlreadyReleasedOrWatched && dLower.contains("ожидается") -> upcomingSched.add(item)
                else -> otherSched.add(item)
            }
        }

        // Add recent/other items if series has no seasons loaded yet
        if (m.seasons.isEmpty() && otherSched.isNotEmpty()) {
            for (item in otherSched.take(2)) {
                recentEntries.add(CalendarEpisodeEntry(m, item))
            }
        }

        // Chronologically sort upcoming episodes (earliest release date and lowest episode number first)
        upcomingSched.sortWith(compareBy<EpisodeScheduleItem> {
            parseDateCal(it.date)?.timeInMillis ?: Long.MAX_VALUE
        }.thenBy {
            val epMatch = Regex("""(?:(\d+)\s+сезон)?.*?(\d+)\s+серия""").find(it.episode)
            val sNum = epMatch?.groupValues?.get(1)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 1
            val epNum = epMatch?.groupValues?.get(2)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 0
            sNum * 10000 + epNum
        })

        todaySched.sortWith(compareBy<EpisodeScheduleItem> {
            val epMatch = Regex("""(?:(\d+)\s+сезон)?.*?(\d+)\s+серия""").find(it.episode)
            val sNum = epMatch?.groupValues?.get(1)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 1
            val epNum = epMatch?.groupValues?.get(2)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 0
            sNum * 10000 + epNum
        })

        tomorrowSched.sortWith(compareBy<EpisodeScheduleItem> {
            val epMatch = Regex("""(?:(\d+)\s+сезон)?.*?(\d+)\s+серия""").find(it.episode)
            val sNum = epMatch?.groupValues?.get(1)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 1
            val epNum = epMatch?.groupValues?.get(2)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 0
            sNum * 10000 + epNum
        })

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
        val sortedUpcoming = upcomingEntries.sortedWith(compareBy<CalendarEpisodeEntry> {
            parseDateCal(it.scheduleItem.date)?.timeInMillis ?: Long.MAX_VALUE
        }.thenBy {
            val epMatch = Regex("""(?:(\d+)\s+сезон)?.*?(\d+)\s+серия""").find(it.scheduleItem.episode)
            val sNum = epMatch?.groupValues?.get(1)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 1
            val epNum = epMatch?.groupValues?.get(2)?.takeIf { v -> v.isNotBlank() }?.toIntOrNull() ?: 0
            sNum * 10000 + epNum
        })
        groups.add(CalendarDayGroup("Скоро выйдут", sortedUpcoming.take(20)))
    }
    if (recentEntries.isNotEmpty()) {
        groups.add(CalendarDayGroup("Недавно вышли / Доступны", recentEntries.take(25)))
    }
    return groups
}
