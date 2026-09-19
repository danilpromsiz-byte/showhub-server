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
import com.example.tvmediaapp.ui.theme.LocalAccentColor
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

    val accent = LocalAccentColor.current

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
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.85f),
                            BackgroundDark
                        ),
                        startY = 180f,
                        endY = 550f
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

            val origTitle = movie.originalTitle.trim()
            if (origTitle.isNotBlank() && !origTitle.equals("null", ignoreCase = true) && origTitle != movie.title.trim()) {
                Text(
                    text = origTitle,
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
                        color = accent,
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
                        containerColor = accent,
                        focusedContainerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = "▶  Смотреть",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                Button(
                    onClick = { onDetailsClick(movie) },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.White,
                        contentColor = TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = "ℹ️  О фильме",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Button(
                    onClick = { onToggleFavorite(movie) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isFavorite) FavoriteGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
                        focusedContainerColor = Color.White,
                        contentColor = if (isFavorite) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border.None,
                        focusedBorder = Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = if (isFavorite) "★ В избранном" else "☆ В избранное",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}
