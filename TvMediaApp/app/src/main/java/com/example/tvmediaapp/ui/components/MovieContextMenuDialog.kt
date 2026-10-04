package com.example.tvmediaapp.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import com.example.tvmediaapp.ui.components.AppButton as Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.data.models.Movie
import com.example.tvmediaapp.data.repository.CatalogRepository
import com.example.tvmediaapp.data.repository.HiddenMoviesManager
import com.example.tvmediaapp.data.repository.WatchLaterManager
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import com.example.tvmediaapp.util.unescapeHtml
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieContextMenuDialog(
    movie: Movie,
    onDismiss: () -> Unit,
    onSelectMovie: (Movie) -> Unit,
    onMovieHidden: ((Movie) -> Unit)? = null,
    onWatchLaterToggled: ((Movie, Boolean) -> Unit)? = null,
    onFavoriteToggled: ((Movie, Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current

    val watchLaterManager = remember { WatchLaterManager(context) }
    val hiddenMoviesManager = remember { HiddenMoviesManager(context) }
    val historyManager = remember { WatchHistoryManager(context) }
    val catalogRepo = remember { CatalogRepository(context) }

    var isWatchLater by remember(movie.id) { mutableStateOf(watchLaterManager.isWatchLater(movie.id)) }
    var isFavorite by remember(movie.id) { mutableStateOf(catalogRepo.isFavorite(movie.id)) }
    val historyItem = remember(movie.id) { historyManager.getProgress(movie.id, movie.title) }
    var hasWatchHistory by remember(movie.id) { mutableStateOf(historyItem != null && (historyItem.percentage > 0 || historyItem.positionMs > 0)) }

    val firstButtonFocusRequester = remember { FocusRequester() }

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(movie.id) {
        delay(60)
        try {
            firstButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Full screen overlay with dim background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Modal Card Container
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E2536),
                            Color(0xFF0F1420)
                        )
                    )
                )
                .border(1.5.dp, focusColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Prevent click-through to background
                .padding(22.dp)
        ) {
            // Header Row: Poster thumbnail + Title + Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (movie.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = movie.title.unescapeHtml(),
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val year = movie.releaseYear.replace("null", "").trim()
                        if (year.isNotEmpty()) {
                            Text(
                                text = year,
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " • ",
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = if (movie.isSeries) {
                                when {
                                    movie.genres.any { it.contains("аниме", ignoreCase = true) } -> "Аниме-сериал"
                                    movie.genres.any { it.contains("мульт", ignoreCase = true) } -> "Мультсериал"
                                    else -> "Сериал"
                                }
                            } else {
                                when {
                                    movie.genres.any { it.contains("аниме", ignoreCase = true) } -> "Аниме"
                                    movie.genres.any { it.contains("мульт", ignoreCase = true) } -> "Мультфильм"
                                    else -> "Фильм"
                                }
                            },
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        val ratingVal = if (movie.ratingKp > 0) movie.ratingKp else movie.rating
                        if (ratingVal > 0) {
                            Text(
                                text = " • ★ ${String.format("%.1f", ratingVal)}",
                                color = Color(0xFFFFB800),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (movie.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = movie.genres.take(3).joinToString(", "),
                            color = TextGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Options List (Column with vertical scroll if screen is small)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Смотреть позже
                ContextMenuItem(
                    icon = "⏱",
                    title = if (isWatchLater) "Удалить из «Смотреть позже»" else "Смотреть позже",
                    subtitle = if (isWatchLater) "В списке отложенного просмотра" else "Добавить в список отложенных",
                    isActive = isWatchLater,
                    focusColor = focusColor,
                    modifier = Modifier.focusRequester(firstButtonFocusRequester),
                    onClick = {
                        val newState = watchLaterManager.toggleWatchLater(movie.id)
                        isWatchLater = newState
                        onWatchLaterToggled?.invoke(movie, newState)
                        val msg = if (newState) "Добавлено в «Смотреть позже»" else "Удалено из «Смотреть позже»"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )

                // 2. В избранное
                ContextMenuItem(
                    icon = if (isFavorite) "★" else "☆",
                    title = if (isFavorite) "Удалить из избранного" else "В избранное",
                    subtitle = if (isFavorite) "В вашей коллекции избранного" else "Сохранить в избранное",
                    isActive = isFavorite,
                    focusColor = focusColor,
                    onClick = {
                        val newState = catalogRepo.toggleFavorite(movie)
                        isFavorite = newState
                        onFavoriteToggled?.invoke(movie, newState)
                        val msg = if (newState) "Добавлено в избранное" else "Удалено из избранного"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )

                // 3. Отметить просмотренным / Сбросить историю
                if (hasWatchHistory) {
                    ContextMenuItem(
                        icon = "🔄",
                        title = "Сбросить прогресс просмотра",
                        subtitle = "Удалить отметку просмотра и начать с начала",
                        isActive = false,
                        focusColor = focusColor,
                        onClick = {
                            historyManager.removeHistory(movie.id)
                            hasWatchHistory = false
                            Toast.makeText(context, "История просмотра сброшена", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    ContextMenuItem(
                        icon = "✓",
                        title = "Отметить как просмотренное",
                        subtitle = "Отметить фильм полностью просмотренным (100%)",
                        isActive = false,
                        focusColor = focusColor,
                        onClick = {
                            historyManager.saveProgress(
                                movie = movie,
                                positionMs = 100_000L,
                                durationMs = 100_000L,
                                season = 1,
                                episode = 1
                            )
                            hasWatchHistory = true
                            Toast.makeText(context, "Отмечено как просмотренное", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // 4. Убрать из видимых
                ContextMenuItem(
                    icon = "👁",
                    title = "Убрать из видимых",
                    subtitle = "Скрыть этот фильм из каталога и рекомендаций",
                    isActive = false,
                    isDanger = true,
                    focusColor = Color(0xFFEF4444),
                    onClick = {
                        hiddenMoviesManager.hideMovie(movie.id)
                        Toast.makeText(context, "Фильм убран из видимых", Toast.LENGTH_SHORT).show()
                        onMovieHidden?.invoke(movie)
                        onDismiss()
                    }
                )

                // 5. О фильме (Подробнее)
                ContextMenuItem(
                    icon = "ℹ",
                    title = "О фильме (Подробнее)",
                    subtitle = "Открыть страницу фильма, описание и плеер",
                    isActive = false,
                    focusColor = focusColor,
                    onClick = {
                        onDismiss()
                        onSelectMovie(movie)
                    }
                )

                // 6. Сообщить о проблеме (Багрепорт)
                var showReportSubmenu by remember { mutableStateOf(false) }
                if (!showReportSubmenu) {
                    ContextMenuItem(
                        icon = "⚠",
                        title = "Сообщить о проблеме",
                        subtitle = "Фильм недоступен, неверные данные и т.д.",
                        isActive = false,
                        focusColor = Color(0xFFF59E0B),
                        onClick = { showReportSubmenu = true }
                    )
                } else {
                    ContextMenuItem(
                        icon = "📡",
                        title = "Видео недоступно",
                        subtitle = "Не воспроизводится или нет потоков",
                        isActive = false,
                        focusColor = Color(0xFFF59E0B),
                        onClick = {
                            com.example.tvmediaapp.data.api.ShowHubApiClient.sendBugReport(
                                movie = movie,
                                type = "streams_unavailable",
                                description = "Пользователь вручную сообщил: видео недоступно"
                            )
                            Toast.makeText(context, "Отчёт отправлен. Спасибо!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                    ContextMenuItem(
                        icon = "🎭",
                        title = "Неверные данные",
                        subtitle = "Обложка, актёры или описание не от этого фильма",
                        isActive = false,
                        focusColor = Color(0xFFF59E0B),
                        onClick = {
                            com.example.tvmediaapp.data.api.ShowHubApiClient.sendBugReport(
                                movie = movie,
                                type = "wrong_metadata",
                                description = "Пользователь вручную сообщил: неверные метаданные (обложка/актёры/описание)"
                            )
                            Toast.makeText(context, "Отчёт отправлен. Спасибо!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                    ContextMenuItem(
                        icon = "❓",
                        title = "Другая проблема",
                        subtitle = "Иная ошибка или замечание",
                        isActive = false,
                        focusColor = Color(0xFFF59E0B),
                        onClick = {
                            com.example.tvmediaapp.data.api.ShowHubApiClient.sendBugReport(
                                movie = movie,
                                type = "other",
                                description = "Пользователь вручную сообщил о проблеме"
                            )
                            Toast.makeText(context, "Отчёт отправлен. Спасибо!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }

                // 7. Отмена
                ContextMenuItem(
                    icon = "✕",
                    title = "Отмена",
                    subtitle = "Закрыть это меню",
                    isActive = false,
                    focusColor = Color.White.copy(alpha = 0.5f),
                    onClick = { onDismiss() }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContextMenuItem(
    icon: String,
    title: String,
    subtitle: String,
    isActive: Boolean,
    focusColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.02f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (isActive) focusColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = if (isDanger) Color(0xFF7F1D1D) else focusColor.copy(alpha = 0.25f),
            contentColor = if (isDanger) Color(0xFFFCA5A5) else Color.LightGray,
            focusedContentColor = Color.White
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, if (isActive) focusColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f))),
            focusedBorder = Border(BorderStroke(2.dp, if (isDanger) Color(0xFFEF4444) else focusColor))
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusedGlow(isFocused = isFocused, color = if (isDanger) Color(0xFFEF4444) else focusColor, radius = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isFocused) (if (isDanger) Color(0xFFEF4444) else focusColor)
                        else (if (isActive) focusColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) Color.White else (if (isActive) focusColor else Color.LightGray)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) Color.White else (if (isDanger) Color(0xFFFCA5A5) else TextWhite),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = if (isFocused) Color.White.copy(alpha = 0.85f) else TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
