package com.example.tvmediaapp.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.torrserver.TorrServerManager
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.LocalSurfaceColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TorrServerHelperDialog(
    host: String,
    streamTitle: String = "2K / 4K P2P",
    magnetOrStreamUrl: String,
    onDismiss: () -> Unit,
    onRetryPlayback: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val focusColor = LocalFocusColor.current

    val isInstalled = remember { TorrServerManager.isTorrServerInstalled(context) }
    var isCheckingAlive by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }

    val firstButtonFocusRequester = remember { FocusRequester() }

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(Unit) {
        delay(80)
        try {
            firstButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Modal background overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalSurfaceColor.current)
                .border(1.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "P2P BITTORENT",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "TorrServer не отвечает",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Description
                Text(
                    text = "Для прямого воспроизведения раздачи «$streamTitle» в 4K/2K без скачивания на диск требуется движок TorrServer.\n\nАдрес подключения: $host недоступен.",
                    color = TextGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (downloadStatus != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = downloadStatus ?: "",
                            color = if (downloadStatus?.contains("Ошибка") == true) Color.Red else accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons column
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isInstalled) {
                        // Launch TorrServer
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingAlive = true
                                    downloadStatus = "Запуск приложения TorrServer..."
                                    TorrServerManager.startTorrServerApp(context)
                                    // Wait and probe server
                                    delay(2500)
                                    val alive = TorrServerManager.checkIsAlive(host)
                                    isCheckingAlive = false
                                    if (alive) {
                                        downloadStatus = "TorrServer успешно запущен! Запуск воспроизведения..."
                                        delay(400)
                                        onDismiss()
                                        onRetryPlayback()
                                    } else {
                                        downloadStatus = "TorrServer запущен. Включите службу в приложении TorrServe и повторите."
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = focusColor,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .focusRequester(firstButtonFocusRequester)
                        ) {
                            Text(
                                text = if (isCheckingAlive) "Проверка подключения..." else "▶ Запустить установленный TorrServer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // 1-Click Install APK
                        Button(
                            onClick = {
                                val act = context as? Activity
                                if (act != null) {
                                    coroutineScope.launch {
                                        isDownloading = true
                                        TorrServerManager.downloadAndInstallApk(act) { status, pct ->
                                            downloadStatus = status
                                            downloadPercent = pct
                                        }
                                        isDownloading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = accent,
                                focusedContainerColor = focusColor,
                                contentColor = Color.Black,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .focusRequester(firstButtonFocusRequester)
                        ) {
                            Text(
                                text = if (isDownloading) "Загрузка: $downloadPercent%..." else "📥 Скачать и установить TorrServer (1 клик)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Open in external player (Vimu / VLC)
                    Button(
                        onClick = {
                            val ok = TorrServerManager.playInExternalPlayer(context, magnetOrStreamUrl)
                            if (ok) onDismiss()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            focusedContainerColor = focusColor,
                            contentColor = TextWhite,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(
                            text = "📺 Открыть во внешнем плеере (VLC / Vimu / TorrServe)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }

                    // Open Settings (if host is on PC / NAS)
                    if (onOpenSettings != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                focusedContainerColor = focusColor,
                                contentColor = TextWhite,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Text(
                                text = "⚙ Настройки TorrServer (указать IP ПК или роутера)",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Cancel
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = focusColor,
                            contentColor = TextGray,
                            focusedContentColor = Color.Black
                        ),
                        border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.03f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    ) {
                        Text(
                            text = "Отмена (выбрать другой источник)",
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
