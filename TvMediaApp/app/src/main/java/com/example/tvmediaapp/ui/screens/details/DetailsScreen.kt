package com.example.tvmediaapp.ui.screens.details

import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.models.StreamOption
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.ui.screens.player.isDirectVideoStream
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.FavoriteGold
import com.example.tvmediaapp.ui.theme.ImdbGold
import com.example.tvmediaapp.ui.theme.KpOrange
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: Movie,
    onPlayClick: (videoUrl: String, startPositionMs: Long, season: Int, episode: Int) -> Unit,
    onBackClick: () -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current

    val historyManager = remember { WatchHistoryManager(context) }
    val savedHistory = remember(movie.id) {
        historyManager.getProgress(movie.id)
    }

    var currentMovie by remember { mutableStateOf(movie) }
    var selectedSeason by remember { mutableStateOf(savedHistory?.season ?: 1) }
    var selectedEpisode by remember { mutableStateOf(savedHistory?.episode ?: 1) }
    var selectedAudioId by remember { mutableStateOf(savedHistory?.audioId ?: "") }
    var selectedQuality by remember { mutableStateOf("1080p") }
    var isResolving by remember { mutableStateOf(false) }
    var streamStatus by remember { mutableStateOf<String?>(null) }
    var streamOptions by remember { mutableStateOf<List<StreamOption>>(emptyList()) }

    // Fetch deep metadata (seasons, episodes, translators, KP rating) in background
    LaunchedEffect(movie.id) {
        val detailed = ShowHubApiClient.fetchMediaDetails(movie)
        currentMovie = detailed
        if (detailed.seasons.isNotEmpty() && savedHistory == null) {
            selectedSeason = detailed.seasons.first().seasonNumber
        }
        if (detailed.audioTracks.isNotEmpty() && selectedAudioId.isEmpty()) {
            selectedAudioId = detailed.audioTracks.first().id
        }
    }

    // Pre-fetch streams in background: query native Rezka and server in parallel
    LaunchedEffect(currentMovie.id, selectedSeason, selectedEpisode, selectedAudioId) {
        try {
            val nativeDeferred = async {
                RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = currentMovie.isSeries,
                    season = selectedSeason,
                    episode = selectedEpisode
                )
            }
            val serverDeferred = async {
                ShowHubApiClient.fetchStreams(
                    movie = currentMovie,
                    season = if (currentMovie.isSeries) selectedSeason else null,
                    episode = if (currentMovie.isSeries) selectedEpisode else null,
                    audioId = selectedAudioId
                )
            }
            val nativeStreams = nativeDeferred.await()
            val serverStreams = serverDeferred.await()
            val combined = (nativeStreams + serverStreams).distinctBy { it.url }
            val sorted = combined.sortedByDescending { isDirectVideoStream(it.url) }
            if (sorted.isNotEmpty()) {
                streamOptions = sorted
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startPlayback(
        targetSeason: Int = selectedSeason,
        targetEpisode: Int = selectedEpisode,
        targetAudioId: String = selectedAudioId,
        startPos: Long = 0L
    ) {
        if (isResolving) return
        isResolving = true
        streamStatus = "Поиск прямого HLS потока..."

        coroutineScope.launch {
            // Priority 1: Query Rezka directly on TV (residential IP) and server concurrently
            val nativeDeferred = async {
                RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = currentMovie.isSeries,
                    season = targetSeason,
                    episode = targetEpisode
                )
            }
            val serverDeferred = async {
                ShowHubApiClient.fetchStreams(
                    movie = currentMovie,
                    season = if (currentMovie.isSeries) targetSeason else null,
                    episode = if (currentMovie.isSeries) targetEpisode else null,
                    audioId = targetAudioId
                )
            }

            val nativeStreams = nativeDeferred.await()
            val serverStreams = serverDeferred.await()
            val combined = (nativeStreams + serverStreams).distinctBy { it.url }
            // Sort direct streams (HLS/MP4) first, balancers last
            val streams = combined.sortedByDescending { isDirectVideoStream(it.url) }

            streamOptions = streams
            isResolving = false

            if (streams.isNotEmpty()) {
                val matched = streams.firstOrNull { it.quality.contains(selectedQuality) && isDirectVideoStream(it.url) }
                    ?: streams.firstOrNull { isDirectVideoStream(it.url) }
                    ?: streams.first()
                streamStatus = "Найден поток ${matched.quality}! Запуск..."
                onPlayClick(matched.url, startPos, targetSeason, targetEpisode)
            } else {
                streamStatus = "Поток в обработке. Попробуйте другой фильм."
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        // High-res backdrop
        AsyncImage(
            model = currentMovie.backdropUrl.ifEmpty { currentMovie.posterUrl },
            contentDescription = currentMovie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BackgroundDark,
                            BackgroundDark.copy(alpha = 0.95f),
                            BackgroundDark.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        // Two-pane Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 36.dp, end = 48.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            // LEFT PANE: Poster & Metadata Badges
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = currentMovie.posterUrl,
                        contentDescription = currentMovie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ratings Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(KpOrange)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "КП ${currentMovie.ratingKp}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ImdbGold)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "IMDb ${currentMovie.ratingImdb}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Metadata Badges
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (currentMovie.director.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Режиссёр:", fontSize = 12.sp, color = TextGray)
                            Text(text = currentMovie.director, fontSize = 12.sp, color = TextWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (currentMovie.country.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Страна:", fontSize = 12.sp, color = TextGray)
                            Text(text = currentMovie.country, fontSize = 12.sp, color = TextWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Тип:", fontSize = 12.sp, color = TextGray)
                        Text(
                            text = if (currentMovie.isSeries) "Сериал" else "Фильм",
                            fontSize = 12.sp,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // RIGHT PANE: Details, Translators, Seasons, Episodes & Actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = currentMovie.title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    lineHeight = 38.sp
                )

                if (currentMovie.originalTitle.isNotEmpty() && currentMovie.originalTitle != currentMovie.title) {
                    Text(
                        text = currentMovie.originalTitle,
                        fontSize = 16.sp,
                        color = TextGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currentMovie.genres.joinToString(" • "),
                    fontSize = 14.sp,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (savedHistory != null && savedHistory.positionMs > 10_000L) {
                        val mins = savedHistory.positionMs / 60000L
                        val resumeLabel = if (currentMovie.isSeries) {
                            "Продолжить (S${savedHistory.season} E${savedHistory.episode}, $mins мин)"
                        } else {
                            "Продолжить ($mins мин)"
                        }

                        Button(
                            onClick = {
                                startPlayback(
                                    targetSeason = savedHistory.season,
                                    targetEpisode = savedHistory.episode,
                                    targetAudioId = savedHistory.audioId.ifEmpty { selectedAudioId },
                                    startPos = savedHistory.positionMs
                                )
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = resumeLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }

                        Button(
                            onClick = { startPlayback(startPos = 0L) },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = accent,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "С начала",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { startPlayback(startPos = 0L) },
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = if (isResolving) "Поиск..." else "Смотреть онлайн",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    // TRAILER BUTTON
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                streamStatus = "Поиск трейлера..."
                                val trailerUrl = ShowHubApiClient.fetchTrailerUrl(currentMovie)
                                if (!trailerUrl.isNullOrEmpty()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        streamStatus = null
                                    } catch (e: Exception) {
                                        streamStatus = "Ошибка запуска видео: ${e.message}"
                                    }
                                } else {
                                    streamStatus = "Трейлер не найден"
                                }
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = accent,
                            contentColor = TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Трейлер",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    // EXTERNAL PLAYER BUTTON (VLC, MX Player, Nova)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                streamStatus = "Получение ссылки для стороннего плеера..."
                                var streams = streamOptions
                                if (streams.isEmpty()) {
                                    val nativeDeferred = async {
                                        RezkaNativeResolver.resolveStreams(
                                            title = currentMovie.title,
                                            year = currentMovie.releaseYear,
                                            isSeries = currentMovie.isSeries,
                                            season = selectedSeason,
                                            episode = selectedEpisode
                                        )
                                    }
                                    val serverDeferred = async {
                                        ShowHubApiClient.fetchStreams(
                                            movie = currentMovie,
                                            season = if (currentMovie.isSeries) selectedSeason else null,
                                            episode = if (currentMovie.isSeries) selectedEpisode else null,
                                            audioId = selectedAudioId
                                        )
                                    }
                                    streams = (nativeDeferred.await() + serverDeferred.await()).distinctBy { it.url }
                                }
                                if (streams.isNotEmpty()) {
                                    val matched = streams.firstOrNull { it.quality.contains(selectedQuality) && isDirectVideoStream(it.url) }
                                        ?: streams.firstOrNull { isDirectVideoStream(it.url) }
                                        ?: streams.first()
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            val uri = Uri.parse(matched.url)
                                            val mime = if (matched.url.contains(".m3u8")) "application/x-mpegURL" else "video/*"
                                            setDataAndType(uri, mime)
                                            putExtra("title", currentMovie.title)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Выберите видеоплеер"))
                                        streamStatus = null
                                    } catch (e: Exception) {
                                        try {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(matched.url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(webIntent)
                                            streamStatus = null
                                        } catch (e2: Exception) {
                                            streamStatus = "Не найден внешний плеер"
                                        }
                                    }
                                } else {
                                    streamStatus = "Потоки не найдены"
                                }
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = accent,
                            contentColor = TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Внешний плеер",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    Button(
                        onClick = { onToggleFavorite(currentMovie) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isFavorite) FavoriteGold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = FavoriteGold,
                            contentColor = if (isFavorite) FavoriteGold else TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, if (isFavorite) FavoriteGold else Color.Transparent)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, TextWhite)
                            )
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = if (isFavorite) "В избранном" else "В избранное",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onBackClick,
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Назад",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                // Stream status message
                if (streamStatus != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = streamStatus ?: "",
                        fontSize = 14.sp,
                        color = accent
                    )
                }

                // TRANSLATORS / AUDIO TRACKS SELECTION RIBBON
                if (currentMovie.audioTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Озвучка / Перевод:",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentMovie.audioTracks) { track ->
                            val isSelected = track.id == selectedAudioId
                            Button(
                                onClick = {
                                    selectedAudioId = track.id
                                    startPlayback(targetAudioId = track.id, startPos = 0L)
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSelected) accent else ChipBackground,
                                    focusedContainerColor = accent,
                                    contentColor = if (isSelected) Color.Black else TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = track.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quality Selector Ribbon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Качество:",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                    listOf("1080p Ultra", "1080p", "720p").forEach { quality ->
                        val isSelected = quality == selectedQuality
                        Button(
                            onClick = { selectedQuality = quality },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) accent else ChipBackground,
                                focusedContainerColor = accent,
                                contentColor = if (isSelected) Color.Black else TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(text = quality, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                // SERIES OPTIONS: Season selector + Episode horizontal list
                if (currentMovie.isSeries && currentMovie.seasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "\u0421\u0435\u0437\u043e\u043d\u044b:",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentMovie.seasons) { season ->
                            val isSelected = season.seasonNumber == selectedSeason
                            Button(
                                onClick = {
                                    selectedSeason = season.seasonNumber
                                    selectedEpisode = 1
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSelected) accent else ChipBackground,
                                    focusedContainerColor = accent,
                                    contentColor = if (isSelected) Color.Black else TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = season.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val activeSeason = currentMovie.seasons.firstOrNull { it.seasonNumber == selectedSeason } ?: currentMovie.seasons.first()
                    Text(
                        text = "Серии (${activeSeason.episodes.size}):",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeSeason.episodes) { ep ->
                            val isSelected = ep.episodeNumber == selectedEpisode
                            Button(
                                onClick = {
                                    selectedEpisode = ep.episodeNumber
                                    startPlayback(targetSeason = selectedSeason, targetEpisode = ep.episodeNumber, startPos = 0L)
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSelected) accent.copy(alpha = 0.3f) else ChipBackground,
                                    focusedContainerColor = accent,
                                    contentColor = if (isSelected) accent else TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(
                                        border = BorderStroke(1.dp, if (isSelected) accent else Color.Transparent)
                                    ),
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, TextWhite)
                                    )
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(
                                    text = ep.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Synopsis
                Text(
                    text = "\u041e \u0444\u0438\u043b\u044c\u043c\u0435:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentMovie.description,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = TextGray
                )
            }
        }
    }
}
