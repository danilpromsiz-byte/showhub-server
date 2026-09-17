package com.example.tvmediaapp.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.resolver.RezkaNativeResolver
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.RedDark
import com.example.tvmediaapp.ui.theme.RedPrimary
import com.example.tvmediaapp.ui.theme.TextGray
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: Movie,
    onPlayClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var streamStatus by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = movie.backdropUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BackgroundDark,
                            BackgroundDark.copy(alpha = 0.90f),
                            BackgroundDark.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, top = 48.dp, end = 56.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(0.65f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\u2605 ${movie.rating}",
                    style = MaterialTheme.typography.titleMedium,
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = movie.releaseYear,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = movie.duration,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = movie.genres.joinToString(" \u2022 "),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextGray
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = movie.description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextGray,
                modifier = Modifier.fillMaxWidth(0.60f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (streamStatus != null) {
                Text(
                    text = streamStatus ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        if (isResolving) return@Button
                        if (movie.videoUrl.isNotEmpty()) {
                            onPlayClick(movie.videoUrl)
                        } else {
                            isResolving = true
                            streamStatus = "\u23f3  \u041f\u043e\u0438\u0441\u043a \u043f\u0440\u044f\u043c\u043e\u0433\u043e HLS \u043f\u043e\u0442\u043e\u043a\u0430 \u043d\u0430 \u0422\u0412..."
                            coroutineScope.launch {
                                val streams = RezkaNativeResolver.resolveStreams(movie.title, movie.releaseYear, movie.isSeries)
                                isResolving = false
                                val bestStream = streams.firstOrNull { it.quality.contains("1080") } ?: streams.firstOrNull()
                                if (bestStream != null) {
                                    streamStatus = "\u2705  \u041d\u0430\u0439\u0434\u0435\u043d \u043f\u043e\u0442\u043e\u043a ${bestStream.quality}! \u0417\u0430\u043f\u0443\u0441\u043a..."
                                    onPlayClick(bestStream.url)
                                } else {
                                    streamStatus = "\u26a0\ufe0f  \u041f\u043e\u0442\u043e\u043a \u0432 \u043e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0435, \u043f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0434\u0440\u0443\u0433\u043e\u0439 \u0444\u0438\u043b\u044c\u043c"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = RedPrimary,
                        focusedContainerColor = RedDark
                    )
                ) {
                    Text(
                        text = if (isResolving) "\u23f3  \u041f\u043e\u0438\u0441\u043a..." else "\u25b6  \u0421\u043c\u043e\u0442\u0440\u0435\u0442\u044c \u043e\u043d\u043b\u0430\u0439\u043d",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onBackClick
                ) {
                    Text(
                        text = "\u2190  \u041d\u0430\u0437\u0430\u0434 \u0432 \u043a\u0430\u0442\u0430\u043b\u043e\u0433",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
