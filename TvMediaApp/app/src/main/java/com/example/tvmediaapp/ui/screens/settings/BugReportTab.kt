package com.example.tvmediaapp.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.tvmediaapp.data.api.ShowHubApiClient
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.theme.ChipBackground
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BugReportTab(
    appVersion: String,
    versionCode: Int,
    tabContentFocusRequester: FocusRequester
) {
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current

    var bugReportText by remember { mutableStateOf("") }
    var bugReportCategory by remember { mutableStateOf("Воспроизведение") }
    var isSendingBugReport by remember { mutableStateOf(false) }
    var bugReportStatus by remember { mutableStateOf<String?>(null) }
    var isInputFocused by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Сообщить об ошибке или проблеме",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )
        Text(
            text = "Опишите проблему, с которой вы столкнулись. Отчет автоматически поступит разработчикам ShowHub.",
            fontSize = 13.sp,
            color = TextGray
        )

        // Categories
        val categories = listOf("Воспроизведение", "Поиск", "Торренты", "Качество", "Интерфейс", "Общее")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEachIndexed { index, cat ->
                val isCur = cat == bugReportCategory
                val catMod = if (index == 0) Modifier.focusRequester(tabContentFocusRequester) else Modifier
                Button(
                    onClick = { bugReportCategory = cat },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isCur) accent.copy(alpha = 0.85f) else ChipBackground,
                        focusedContainerColor = LocalFocusColor.current,
                        contentColor = if (isCur) Color.Black else TextWhite,
                        focusedContentColor = Color.Black
                    ),
                    border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp).then(catMod)
                ) {
                    Text(text = cat, fontSize = 11.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // Text Field Box for TV D-pad focus & keyboard input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = if (isInputFocused) 0.10f else 0.05f))
                .border(
                    width = if (isInputFocused) 2.dp else 1.dp,
                    color = if (isInputFocused) accent else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            if (bugReportText.isEmpty()) {
                Text(
                    text = "Опишите ошибку (например: фильм «...» не запускается, звук отстает, не находит серию...)",
                    color = TextGray.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            BasicTextField(
                value = bugReportText,
                onValueChange = { bugReportText = it },
                textStyle = TextStyle(
                    color = TextWhite,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { isInputFocused = it.isFocused }
            )
        }

        // Device info summary
        Text(
            text = "Устройство: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE} • v$appVersion (Сборка $versionCode)",
            fontSize = 11.sp,
            color = TextGray
        )

        // Submit Button
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (bugReportText.isBlank()) {
                        bugReportStatus = "Пожалуйста, введите описание ошибки"
                        return@Button
                    }
                    coroutineScope.launch {
                        isSendingBugReport = true
                        bugReportStatus = "Отправка отчета на сервер ShowHub..."
                        val ok = ShowHubApiClient.sendBugReport(
                            reportText = bugReportText.trim(),
                            category = bugReportCategory,
                            currentScreen = "settings"
                        )
                        isSendingBugReport = false
                        if (ok) {
                            bugReportStatus = "Отчет успешно отправлен на сервер! Спасибо за помощь."
                            bugReportText = ""
                        } else {
                            bugReportStatus = "Ошибка отправки отчета. Проверьте связь с сервером."
                        }
                    }
                },
                colors = ButtonDefaults.colors(
                    containerColor = accent,
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = Color.Black,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = if (isSendingBugReport) "Отправка..." else "Отправить отчет",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                )
            }

            if (bugReportStatus != null) {
                Text(
                    text = bugReportStatus ?: "",
                    fontSize = 12.sp,
                    color = if (bugReportStatus?.contains("успешно") == true) accent else Color(0xFFF87171)
                )
            }
        }
    }
}
