package com.example.tvmediaapp.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val LocalAccentColor = compositionLocalOf { CyanNeon }

object ThemeManager {
    private const val PREF_THEME_KEY = "pref_theme"
    private const val PREF_PURE_BLACK_KEY = "pref_pure_black"
    private var prefs: SharedPreferences? = null

    var currentAccentColor by mutableStateOf(CyanNeon)
        private set

    var currentThemeKey by mutableStateOf("cyan")
        private set

    var isPureBlackEnabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE)
        val saved = prefs?.getString(PREF_THEME_KEY, "cyan") ?: "cyan"
        isPureBlackEnabled = prefs?.getBoolean(PREF_PURE_BLACK_KEY, false) ?: false
        setTheme(saved)
    }

    fun setPureBlack(enabled: Boolean) {
        isPureBlackEnabled = enabled
        prefs?.edit()?.putBoolean(PREF_PURE_BLACK_KEY, enabled)?.apply()
    }

    fun setTheme(themeKey: String) {
        currentThemeKey = themeKey
        currentAccentColor = when (themeKey) {
            "emerald" -> ThemeEmerald
            "amber" -> ThemeAmber
            "ruby" -> ThemeRuby
            "amethyst" -> ThemeAmethyst
            "sapphire" -> ThemeSapphire
            else -> CyanNeon
        }
        prefs?.edit()?.putString(PREF_THEME_KEY, themeKey)?.apply()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaAppTheme(content: @Composable () -> Unit) {
    val accent = ThemeManager.currentAccentColor
    val isPureBlack = ThemeManager.isPureBlackEnabled

    val darkColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = TextWhite,
        background = if (isPureBlack) PureBlack else BackgroundDark,
        onBackground = TextWhite,
        surface = if (isPureBlack) PureBlackSurface else SurfaceDark,
        onSurface = TextWhite,
        surfaceVariant = if (isPureBlack) PureBlackSurfaceVariant else SurfaceVariantDark,
        onSurfaceVariant = TextGray,
        border = accent
    )

    CompositionLocalProvider(LocalAccentColor provides accent) {
        MaterialTheme(
            colorScheme = darkColorScheme,
            content = content
        )
    }
}
