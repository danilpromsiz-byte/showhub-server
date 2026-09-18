package com.example.tvmediaapp.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.data.history.HistoryItem
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.RedPrimary
import com.example.tvmediaapp.ui.theme.SurfaceDark
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(
    onMovieSelect: (Movie) -> Unit,
    onResumePlay: (Movie, Long, Int, Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyManager = remember { WatchHistoryManager(context) }
    var historyItems by remember { mutableStateOf(historyManager.getHistory()) }

    val accent = LocalAccentColor.current

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
                    text = "История просмотров",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                if (historyItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "(${historyItems.size})",
                        fontSize = 20.sp,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (historyItems.isNotEmpty()) {
                    Button(
                        onClick = {
                            historyManager.clearHistory()
                            historyItems = emptyList()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.Red.copy(alpha = 0.8f),
                            contentColor = TextGray,
                            focusedContentColor = TextWhite
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.tvmediaapp.ui.components.AppIcon(
                                iconResId = com.example.tvmediaapp.R.drawable.ic_delete,
                                contentDescription = "Очистить историю",
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = "Очистить историю",
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = accent,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "История просмотров пуста",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Фильмы и сериалы, которые вы начнёте смотреть, появятся здесь с прогрессом.",
                        fontSize = 14.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyItems) { item ->
                    val movie = Movie(
                        id = item.id,
                        title = item.title,
                        description = "",
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backdropUrl,
                        rating = 8.0,
                        releaseYear = item.releaseYear,
                        duration = "",
                        isSeries = item.isSeries
                    )

                    StandardCardContainer(
                        imageCard = { interactionSource ->
                            Card(
                                onClick = {
                                    onResumePlay(movie, item.positionMs, item.season, item.episode)
                                },
                                interactionSource = interactionSource,
                                border = CardDefaults.border(
                                    focusedBorder = Border(BorderStroke(3.dp, accent))
                                ),
                                scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.04f),
                                modifier = Modifier
                                    .width(170.dp)
                                    .aspectRatio(2f / 3f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Poster
                                    AsyncImage(
                                        model = item.posterUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Episode Badge (if series)
                                    if (item.isSeries) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.8f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "S${item.season} E${item.episode}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accent
                                            )
                                        }
                                    }

                                    // Progress Bar at Bottom of Poster
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(item.percentage.coerceIn(0, 100) / 100f)
                                                    .height(5.dp)
                                                    .background(accent)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        title = {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        },
                        subtitle = {
                            val timeStr = formatTime(item.positionMs)
                            Text(
                                text = "Просмотрено ${item.percentage}% ($timeStr)",
                                style = MaterialTheme.typography.bodySmall,
                                color = accent,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        },
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
