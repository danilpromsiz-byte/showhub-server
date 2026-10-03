package com.example.tvmediaapp.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.R
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.data.api.UserStats
import com.example.tvmediaapp.data.updater.UpdateManager
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.components.AppIcon
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AboutTab(
    appVersion: String,
    versionCode: Int,
    tabContentFocusRequester: FocusRequester,
    onCheckUpdateClick: (() -> Unit)?,
    hasUpdateAvailable: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }

    var updateCheckResult by remember { mutableStateOf<String?>(null) }

    var userStats by remember {
        val cachedInstalls = prefs.getInt("pref_cached_total_installs", 0)
        val cachedUsers = prefs.getInt("pref_cached_total_users", 0)
        val cachedMonth = prefs.getInt("pref_cached_active_month", 0)
        val cachedToday = prefs.getInt("pref_cached_active_today", 0)
        mutableStateOf(if (cachedUsers > 0 || cachedInstalls > 0) UserStats(
            totalInstalls = cachedInstalls,
            totalUsers = cachedUsers,
            activeMonth = cachedMonth,
            activeToday = cachedToday
        ) else null)
    }
    var isLoadingUserStats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingUserStats = true
        userStats = ShowHubApiClient.pingAndGetUserStats(context, appVersion)
        isLoadingUserStats = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "О программе ShowHub TV",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBackground)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ShowHub TV v$appVersion (Сборка $versionCode)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "Нативный медиацентр для Android TV на Kotlin и Jetpack Compose",
                    fontSize = 13.sp,
                    color = TextGray
                )
                Text(
                    text = "Плеер: Media3 ExoPlayer (HLS/MP4) + Chromium WebView балансеры",
                    fontSize = 13.sp,
                    color = accent
                )
            }
        }

        // User Analytics Counter Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBackground)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_showhub_logo),
                    contentDescription = "ShowHub TV",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Статистика пользователей ShowHub TV",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    if (userStats != null) {
                        val totalInstallsDisplay = if (userStats!!.totalInstalls > 0) userStats!!.totalInstalls else maxOf(userStats!!.totalUsers * 4, 128)
                        Text(
                            text = "Установок приложения: $totalInstallsDisplay   •   Пользователей: ${userStats!!.totalUsers}   •   Активных (30 дн): ${userStats!!.activeMonth}   •   Сегодня: ${userStats!!.activeToday}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    } else if (isLoadingUserStats) {
                        Text(
                            text = "Загрузка статистики...",
                            fontSize = 13.sp,
                            color = TextGray
                        )
                    } else {
                        Text(
                            text = "Подключение к серверу аналитики...",
                            fontSize = 13.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        updateCheckResult = "Проверка обновлений на сервере..."
                        val info = UpdateManager.checkUpdate(versionCode)
                        if (info.hasUpdate && info.versionCode > versionCode) {
                            updateCheckResult = "Доступна новая версия: v${info.versionName} (Сборка ${info.versionCode})!"
                            onCheckUpdateClick?.invoke()
                        } else {
                            updateCheckResult = "У вас установлена самая актуальная версия (v$appVersion)."
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppIcon(
                        resId = R.drawable.ic_refresh,
                        tint = Color.Black,
                        size = 13.dp
                    )
                    Text("Проверить обновления", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
                }
            }
        }

        if (updateCheckResult != null) {
            Text(
                text = updateCheckResult ?: "",
                fontSize = 14.sp,
                color = accent
            )
        }
    }
}
