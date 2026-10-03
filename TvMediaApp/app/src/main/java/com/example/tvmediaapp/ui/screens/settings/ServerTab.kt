package com.example.tvmediaapp.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServerTab(
    tabContentFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }

    var serverUrl by remember { mutableStateOf(prefs.getString("pref_server_url", "https://showhub-server.onrender.com") ?: "https://showhub-server.onrender.com") }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    var torrServeHost by remember { mutableStateOf(prefs.getString("pref_torrserve_host", "http://127.0.0.1:8090") ?: "http://127.0.0.1:8090") }
    var torrPingResult by remember { mutableStateOf<String?>(null) }
    var isTorrPinging by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Адрес сервера ShowHub",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )
        Text(
            text = "Облачный или локальный адрес бэкенд-сервера",
            fontSize = 14.sp,
            color = TextGray
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBackground)
                .padding(14.dp)
        ) {
            Text(
                text = serverUrl,
                fontSize = 15.sp,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isPinging = true
                        pingResult = "Проверка связи..."
                        val start = System.currentTimeMillis()
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                val url = URL("$serverUrl/api/catalog/stats")
                                val conn = url.openConnection() as HttpURLConnection
                                conn.connectTimeout = 5000
                                conn.readTimeout = 5000
                                conn.connect()
                                conn.responseCode == 200
                            } catch (e: Exception) {
                                false
                            }
                        }
                        val elapsed = System.currentTimeMillis() - start
                        isPinging = false
                        pingResult = if (ok) {
                            "Связь установлена (задержка: ${elapsed} мс, HTTP 200 OK)"
                        } else {
                            "Сервер недоступен или тайм-аут"
                        }
                    }
                },
                colors = ButtonDefaults.colors(
                    containerColor = accent,
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = Color.Black
                ),
                border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
            ) {
                Text(text = if (isPinging) "Проверка..." else "Проверить связь (Ping)", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }

        if (pingResult != null) {
            Text(
                text = pingResult ?: "",
                fontSize = 14.sp,
                color = if (pingResult?.contains("установлена") == true) accent else Color.Red
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "TorrServe (P2P / Торренты)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )
        Text(
            text = "Адрес движка TorrServe для стриминга торрент-потоков на ТВ",
            fontSize = 13.sp,
            color = TextGray
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            TORRSERVE_HOST_OPTIONS.forEach { (hostVal, hostTitle) ->
                val isCur = hostVal == torrServeHost
                Button(
                    onClick = {
                        torrServeHost = hostVal
                        prefs.edit().putString("pref_torrserve_host", hostVal).apply()
                        torrPingResult = null
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                        focusedContainerColor = LocalFocusColor.current,
                        contentColor = if (isCur) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(
                        border = Border.None,
                        focusedBorder = Border.None
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(text = hostTitle, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, lineHeight = 13.sp)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isTorrPinging = true
                        torrPingResult = "Проверка TorrServe..."
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                val url = URL("$torrServeHost/echo")
                                val conn = url.openConnection() as HttpURLConnection
                                conn.connectTimeout = 3000
                                conn.readTimeout = 3000
                                conn.connect()
                                conn.responseCode == 200
                            } catch (e: Exception) {
                                false
                            }
                        }
                        isTorrPinging = false
                        torrPingResult = if (ok) {
                            "TorrServe доступен и готов к воспроизведению!"
                        } else {
                            "TorrServe не отвечает по адресу $torrServeHost"
                        }
                    }
                },
                colors = ButtonDefaults.colors(
                    containerColor = accent,
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = Color.Black
                ),
                border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(text = if (isTorrPinging) "Проверка..." else "Тест TorrServe", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }

        if (torrPingResult != null) {
            Text(
                text = torrPingResult ?: "",
                fontSize = 13.sp,
                color = if (torrPingResult?.contains("готов") == true) accent else Color.Red
            )
        }

        var isTorrInfoFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isTorrInfoFocused) LocalFocusColor.current.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                .onFocusChanged { isTorrInfoFocused = it.isFocused }
                .focusable()
                .padding(14.dp)
        ) {
            Text(
                text = "💡 Как работают торренты: ShowHub находит раздачи на открытых трекерах (Rutor и др.) и передает магнет-ссылку в TorrServe. TorrServe кэширует поток в оперативную память (ОЗУ) ТВ и передает прямой HLS/MP4 поток плееру без скачивания на диск. Поиск по торрентам работает автоматически в общем каталоге и поиске (потоки помечены как P2P 1080p / P2P 4K).",
                fontSize = 12.sp,
                color = TextWhite.copy(alpha = 0.85f),
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        var isSourcesFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSourcesFocused) LocalFocusColor.current.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                .border(1.dp, if (isSourcesFocused) LocalFocusColor.current else Color.Transparent, RoundedCornerShape(8.dp))
                .onFocusChanged { isSourcesFocused = it.isFocused }
                .focusable()
                .padding(14.dp)
        ) {
            Text(
                text = "Источники контента: HDRezka (прямой поток на ТВ), Kodik, Filmix PRO, VideoCDN, Collaps, Bazon, Rutor / TorrServe (P2P)",
                fontSize = 13.sp,
                color = if (isSourcesFocused) TextWhite else TextGray
            )
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}
