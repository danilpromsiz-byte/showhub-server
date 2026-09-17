package com.example.tvmediaapp.ui.screens.details

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
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.FavoriteGold
import com.example.tvmediaapp.ui.theme.ImdbGold
import com.example.tvmediaapp.ui.theme.KpOrange
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: Movie,
    onPlayClick: (url: String, startPositionMs: Long, season: Int, episode: Int) -> Unit,
    onBackClick: () -> Unit,
    onToggleFavorite: (Movie) -> Unit = {},
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyManager = remember { WatchHistoryManager(context) }
    val savedHistory = remember { historyManager.getProgress(movie.id) }

    val coroutineScope = rememberCoroutineScope()
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

    fun startPlayback(
        targetSeason: Int = selectedSeason,
        targetEpisode: Int = selectedEpisode,
        targetAudioId: String = selectedAudioId,
        startPos: Long = 0L
    ) {
        if (isResolving) return
        isResolving = true
        streamStatus = "\u23f3  \u041f\u043e\u0438\u0441\u043a \u043f\u0440\u044f\u043c\u043e\u0433\u043e HLS \u043f\u043e\u0442\u043e\u043a\u0430..."

        coroutineScope.launch {
            var streams = ShowHubApiClient.fetchStreams(
                movie = currentMovie,
                season = if (currentMovie.isSeries) targetSeason else null,
                episode = if (currentMovie.isSeries) targetEpisode else null,
                audioId = targetAudioId
            )

            if (streams.isEmpty()) {
                streams = RezkaNativeResolver.resolveStreams(
                    title = currentMovie.title,
                    year = currentMovie.releaseYear,
                    isSeries = currentMovie.isSeries,
                    season = targetSeason,
                    episode = targetEpisode
                )
            }

            streamOptions = streams
            isResolving = false

            if (streams.isNotEmpty()) {
                val matched = streams.firstOrNull { it.quality.contains(selectedQuality) } ?: streams.first()
                streamStatus = "\u2705  \u041d\u0430\u0439\u0434\u0435\u043d \u043f\u043e\u0442\u043e\u043a ${matched.quality}! \u0417\u0430\u043f\u0443\u0441\u043a..."
                onPlayClick(matched.url, startPos, targetSeason, targetEpisode)
            } else {
                streamStatus = "\u26a0\ufe0f  \u041f\u043e\u0442\u043e\u043a \u0432 \u043e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0435. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0434\u0440\u0443\u0433\u043e\u0439 \u0444\u0438\u043b\u044c\u043c."
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
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(KpOrange)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "\u041a\u041f \u2605 ${currentMovie.ratingKp}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ImdbGold)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "IMDb \u2605 ${currentMovie.ratingImdb}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (currentMovie.director.isNotEmpty()) {
                    Text(
                        text = "\u0420\u0435\u0436\u0438\u0441\u0441\u0451\u0440: ${currentMovie.director}",
                        fontSize = 13.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (currentMovie.country.isNotEmpty()) {
                    Text(
                        text = "\u0421\u0442\u0440\u0430\u043d\u0430: ${currentMovie.country}",
                        fontSize = 13.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = "${currentMovie.releaseYear} \u2022 ${currentMovie.duration}",
                    fontSize = 13.sp,
                    color = TextGray
                )
            }

            // RIGHT PANE: Title, Actions, Synopsis, Series Navigator, Quality
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = currentMovie.title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite
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
                    text = currentMovie.genres.joinToString(" \u2022 "),
                    fontSize = 14.sp,
                    color = CyanNeon,
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
                            "\u25b6  \u041f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c (S${savedHistory.season} E${savedHistory.episode}, ${mins} \u043c\u0438\u043d)"
                        } else {
                            "\u25b6  \u041f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c (${mins} \u043c\u0438\u043d)"
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
                                containerColor = CyanNeon,
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
                                focusedContainerColor = CyanNeon,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "\u0421 \u043d\u0430\u0447\u0430\u043b\u0430",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { startPlayback(startPos = 0L) },
                            colors = ButtonDefaults.colors(
                                containerColor = CyanNeon,
                                focusedContainerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = if (isResolving) "\u23f3 \u041f\u043e\u0438\u0441\u043a..." else "\u25b6  \u0421\u043c\u043e\u0442\u0440\u0435\u0442\u044c \u043e\u043d\u043b\u0430\u0439\u043d",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
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
                            text = if (isFavorite) "\u2605 \u0412 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u043c" else "\u2606 \u0412 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435",
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
                            text = "\u2190 \u041d\u0430\u0437\u0430\u0434",
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
                        color = CyanNeon
                    )
                }

                // TRANSLATORS / AUDIO TRACKS SELECTION RIBBON
                if (currentMovie.audioTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430 / \u041f\u0435\u0440\u0435\u0432\u043e\u0434:",
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
                                    containerColor = if (isSelected) CyanNeon else ChipBackground,
                                    focusedContainerColor = CyanNeon,
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
                        text = "\u041a\u0430\u0447\u0435\u0441\u0442\u0432\u043e:",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                    listOf("1080p Ultra", "1080p", "720p").forEach { quality ->
                        val isSelected = quality == selectedQuality
                        Button(
                            onClick = { selectedQuality = quality },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) CyanNeon else ChipBackground,
                                focusedContainerColor = CyanNeon,
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
                                    containerColor = if (isSelected) CyanNeon else ChipBackground,
                                    focusedContainerColor = CyanNeon,
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
                        text = "\u0421\u0435\u0440\u0438\u0438 (${activeSeason.episodes.size}):",
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
                                    containerColor = if (isSelected) CyanNeon.copy(alpha = 0.3f) else ChipBackground,
                                    focusedContainerColor = CyanNeon,
                                    contentColor = if (isSelected) CyanNeon else TextWhite,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(
                                        border = BorderStroke(1.dp, if (isSelected) CyanNeon else Color.Transparent)
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
