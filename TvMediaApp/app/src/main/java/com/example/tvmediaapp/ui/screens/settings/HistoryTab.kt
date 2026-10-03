package com.example.tvmediaapp.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.Coil
import com.example.tvmediaapp.data.history.WatchHistoryManager
import com.example.tvmediaapp.ui.components.AppButton as Button
import com.example.tvmediaapp.ui.theme.LocalFocusColor
import com.example.tvmediaapp.ui.theme.TextGray
import com.example.tvmediaapp.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryTab(
    tabContentFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Управление историей и кэшем ТВ",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )

        // Clear Watch History
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("История просмотров", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Text("Удаляет сохраненные таймкоды и прогресс серий", fontSize = 13.sp, color = TextGray)
            }
            Button(
                onClick = {
                    WatchHistoryManager(context).clearHistory()
                    Toast.makeText(context, "История просмотров очищена", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.colors(containerColor = Color.Red.copy(alpha = 0.8f)),
                border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
            ) {
                Text("Очистить историю", fontSize = 11.sp, lineHeight = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Clear Poster Cache
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Кэш обложек и постеров", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Text("Освобождает внутреннюю память ТВ от загруженных картинок", fontSize = 13.sp, color = TextGray)
            }
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val imageLoader = Coil.imageLoader(context)
                            imageLoader.diskCache?.clear()
                            imageLoader.memoryCache?.clear()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    Toast.makeText(context, "Кэш постеров очищен", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = LocalFocusColor.current,
                    contentColor = TextWhite,
                    focusedContentColor = Color.Black
                ),
                border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Очистить кэш картинок", fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
    }
}
