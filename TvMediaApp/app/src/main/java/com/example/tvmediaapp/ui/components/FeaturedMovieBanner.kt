package com.example.tvmediaapp.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.ui.theme.BackgroundDark
import com.example.tvmediaapp.ui.theme.CyanDark
import com.example.tvmediaapp.ui.theme.CyanNeon
import com.example.tvmediaapp.ui.theme.FavoriteGold
import com.example.tvmediaapp.ui.theme.ImdbGold
import com.example.tvmediaapp.ui.theme.KpOrange
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeaturedMovieBanner(
    movie: Movie?,
    onWatchClick: (Movie) -> Unit,
    onDetailsClick: (Movie) -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (movie == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(370.dp)
    ) {
        // High-res backdrop
        AsyncImage(
            model = movie.backdropUrl.ifEmpty { movie.posterUrl },
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlays for pristine readability on TV screens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BackgroundDark,
                            BackgroundDark.copy(alpha = 0.92f),
                            BackgroundDark.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1400f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BackgroundDark.copy(alpha = 0.75f),
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.70f),
                            BackgroundDark
                        ),
                        startY = 0f,
                        endY = 370f
                    )
                )
        )

        // Hero Info Content
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.62f)
                .padding(start = 48.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = movie.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (movie.originalTitle.isNotEmpty() && movie.originalTitle != movie.title) {
                Text(
                    text = movie.originalTitle,
                    fontSize = 15.sp,
                    color = TextGray,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rating and Meta Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Kinopoisk Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(KpOrange)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\u041a\u041f \u2605 ${movie.ratingKp}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // IMDb Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ImdbGold)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "IMDb \u2605 ${movie.ratingImdb}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Text(
                    text = movie.releaseYear,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextWhite
                )

                Text(
                    text = movie.duration,
                    fontSize = 13.sp,
                    color = TextGray
                )

                if (movie.country.isNotEmpty()) {
                    Text(
                        text = movie.country,
                        fontSize = 13.sp,
                        color = TextGray
                    )
                }

                if (movie.genres.isNotEmpty()) {
                    Text(
                        text = movie.genres.take(3).joinToString(" \u2022 "),
                        fontSize = 13.sp,
                        color = CyanNeon,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = movie.description,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = TextGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onWatchClick(movie) },
                    colors = ButtonDefaults.colors(
                        containerColor = CyanNeon,
                        focusedContainerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        text = "\u25b6  \u0421\u043c\u043e\u0442\u0440\u0435\u0442\u044c",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                Button(
                    onClick = { onDetailsClick(movie) },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = CyanNeon,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        text = "\u2139\ufe0f  \u041e \u0444\u0438\u043b\u044c\u043c\u0435",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Button(
                    onClick = { onToggleFavorite(movie) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isFavorite) FavoriteGold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
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
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        text = if (isFavorite) "\u2605 \u0412 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u043c" else "\u2606 \u0412 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}
