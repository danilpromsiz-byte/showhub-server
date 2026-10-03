package com.example.tvmediaapp.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
fun FilmixTab(
    tabContentFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val prefs = remember { context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE) }

    var filmixLogin by remember { mutableStateOf(prefs.getString("filmix_login", "") ?: "") }
    var filmixLoginInput by remember { mutableStateOf(prefs.getString("filmix_login", "") ?: "") }
    var filmixPasswordInput by remember { mutableStateOf("") }
    var isFilmixLoggedIn by remember { mutableStateOf(prefs.getBoolean("filmix_is_logged_in", false)) }
    var isFilmixPro by remember { mutableStateOf(prefs.getBoolean("filmix_is_pro", false)) }
    var filmixTariffName by remember { mutableStateOf(prefs.getString("filmix_tariff", "") ?: "") }
    var isLoggingInFilmix by remember { mutableStateOf(false) }
    var filmixLoginMessage by remember { mutableStateOf<String?>(null) }

    var isLoginFieldFocused by remember { mutableStateOf(false) }
    var isPassFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val status = ShowHubApiClient.getFilmixStatus()
        if (status.isLoggedIn) {
            isFilmixLoggedIn = true
            filmixLogin = status.username
            isFilmixPro = status.isPro || status.isProPlus
            filmixTariffName = if (status.isProPlus) "PRO+ (4K / 1080p)" else (if (status.isPro) "PRO (1080p)" else "Базовый (до 720p)")
            prefs.edit()
                .putBoolean("filmix_is_logged_in", true)
                .putBoolean("filmix_is_pro", isFilmixPro)
                .putString("filmix_login", status.username)
                .putString("filmix_tariff", filmixTariffName)
                .apply()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Аккаунт Filmix (до 720p бесплатно / PRO 1080p-4K)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )
        Text(
            text = "Авторизация открывает прямые HLS/MP4 видеопотоки Filmix. Для качества 720p достаточно бесплатного аккаунта.",
            fontSize = 14.sp,
            color = TextGray
        )

        // Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBackground)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isFilmixLoggedIn) "Авторизован: $filmixLogin" else "Гостевой режим (Filmix)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = if (isFilmixLoggedIn) {
                            "Тариф: $filmixTariffName • Прямые видеопотоки активны"
                        } else {
                            "Потоки заблокированы (войдите под своей учетной записью Filmix)"
                        },
                        fontSize = 13.sp,
                        color = if (isFilmixLoggedIn) accent else TextGray
                    )
                }

                if (isFilmixLoggedIn) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                ShowHubApiClient.logoutFilmix()
                                isFilmixLoggedIn = false
                                isFilmixPro = false
                                filmixLogin = ""
                                filmixTariffName = ""
                                prefs.edit()
                                    .putBoolean("filmix_is_logged_in", false)
                                    .putBoolean("filmix_is_pro", false)
                                    .remove("filmix_login")
                                    .remove("filmix_tariff")
                                    .apply()
                                Toast.makeText(context, "Вы вышли из Filmix", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.colors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                        modifier = Modifier.height(28.dp).focusRequester(tabContentFocusRequester)
                    ) {
                        Text("Выйти", fontSize = 13.sp)
                    }
                }
            }
        }

        if (!isFilmixLoggedIn) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Вход в учетную запись Filmix:",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Login Input
                Column(modifier = Modifier.weight(1f)) {
                    Text("Логин или E-mail:", fontSize = 12.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChipBackground)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (isLoginFieldFocused) accent else Color.Transparent
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (filmixLoginInput.isEmpty()) {
                            Text("Введите логин", fontSize = 14.sp, color = TextGray.copy(alpha = 0.6f))
                        }
                        BasicTextField(
                            value = filmixLoginInput,
                            onValueChange = { filmixLoginInput = it },
                            textStyle = TextStyle(color = TextWhite, fontSize = 14.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(tabContentFocusRequester)
                                .onFocusChanged { isLoginFieldFocused = it.isFocused }
                        )
                    }
                }

                // Password Input
                Column(modifier = Modifier.weight(1f)) {
                    Text("Пароль:", fontSize = 12.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChipBackground)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (isPassFieldFocused) accent else Color.Transparent
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (filmixPasswordInput.isEmpty()) {
                            Text("Введите пароль", fontSize = 14.sp, color = TextGray.copy(alpha = 0.6f))
                        }
                        BasicTextField(
                            value = filmixPasswordInput,
                            onValueChange = { filmixPasswordInput = it },
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(color = TextWhite, fontSize = 14.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isPassFieldFocused = it.isFocused }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (filmixLoginInput.isBlank() || filmixPasswordInput.isBlank()) {
                            Toast.makeText(context, "Введите логин и пароль Filmix", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            isLoggingInFilmix = true
                            filmixLoginMessage = "Подключение к серверу Filmix..."
                            val (ok, msg) = ShowHubApiClient.loginFilmix(filmixLoginInput, filmixPasswordInput)
                            isLoggingInFilmix = false
                            filmixLoginMessage = msg
                            if (ok) {
                                val st = ShowHubApiClient.getFilmixStatus()
                                isFilmixLoggedIn = true
                                filmixLogin = st.username.ifEmpty { filmixLoginInput }
                                isFilmixPro = st.isPro || st.isProPlus
                                filmixTariffName = if (st.isProPlus) "PRO+ (4K / 1080p)" else (if (st.isPro) "PRO (1080p)" else "Базовый (до 720p)")
                                prefs.edit()
                                    .putBoolean("filmix_is_logged_in", true)
                                    .putBoolean("filmix_is_pro", isFilmixPro)
                                    .putString("filmix_login", filmixLogin)
                                    .putString("filmix_tariff", filmixTariffName)
                                    .apply()
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        if (isLoggingInFilmix) "Вход..." else "Войти в Filmix",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (filmixLoginMessage != null) {
                    Text(
                        text = filmixLoginMessage ?: "",
                        fontSize = 13.sp,
                        color = if (isFilmixLoggedIn) accent else Color(0xFFFF5252)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "💡 Совет: Для качества 720p достаточно бесплатного аккаунта Filmix (filmix.biz или filmix.ac)! Зарегистрируйтесь на сайте и введите логин и пароль здесь.",
                    fontSize = 12.sp,
                    color = TextGray
                )
            }
        }
    }
}
