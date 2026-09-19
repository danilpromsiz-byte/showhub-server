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

val LocalAccentColor = compositionLocalOf { ThemeYellow }
val LocalBackgroundColor = compositionLocalOf { BackgroundDark }
val LocalFocusColor = compositionLocalOf { Color.White }
val LocalSurfaceColor = compositionLocalOf { SurfaceDark }
val LocalSurfaceVariantColor = compositionLocalOf { SurfaceVariantDark }

object ThemeManager {
    private const val PREF_THEME_KEY = "pref_theme"
    private const val PREF_PURE_BLACK_KEY = "pref_pure_black"
    private const val PREF_FOCUS_COLOR_KEY = "pref_focus_color"
    private var prefs: SharedPreferences? = null

    var currentAccentColor by mutableStateOf(ThemeYellow)
        private set

    var currentThemeKey by mutableStateOf("yellow")
        private set

    var isPureBlackEnabled by mutableStateOf(false)
        private set

    var currentFocusColorKey by mutableStateOf("white")
        private set

    val currentBackgroundColor: Color
        get() = if (isPureBlackEnabled) PureBlack else BackgroundDark

    val currentSurfaceColor: Color
        get() = if (isPureBlackEnabled) PureBlackSurface else SurfaceDark

    val currentSurfaceVariantColor: Color
        get() = if (isPureBlackEnabled) PureBlackSurfaceVariant else SurfaceVariantDark

    val currentFocusColor: Color
        get() = when (currentFocusColorKey) {
            "accent" -> currentAccentColor
            "white" -> Color.White
            "cyan" -> ThemeCyan
            "yellow" -> ThemeYellow
            "emerald" -> ThemeEmerald
            "amber" -> ThemeAmber
            "ruby" -> ThemeRuby
            "amethyst" -> ThemeAmethyst
            "sapphire" -> ThemeSapphire
            "lime" -> ThemeLime
            "magenta" -> ThemeMagenta
            else -> Color.White
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE)
        val saved = prefs?.getString(PREF_THEME_KEY, "yellow") ?: "yellow"
        isPureBlackEnabled = prefs?.getBoolean(PREF_PURE_BLACK_KEY, false) ?: false
        currentFocusColorKey = prefs?.getString(PREF_FOCUS_COLOR_KEY, "white") ?: "white"
        setTheme(saved)
    }

    fun setPureBlack(enabled: Boolean) {
        isPureBlackEnabled = enabled
        prefs?.edit()?.putBoolean(PREF_PURE_BLACK_KEY, enabled)?.apply()
    }

    fun setFocusColor(key: String) {
        currentFocusColorKey = key
        prefs?.edit()?.putString(PREF_FOCUS_COLOR_KEY, key)?.apply()
    }

    fun setTheme(themeKey: String) {
        currentThemeKey = themeKey
        currentAccentColor = when (themeKey) {
            "yellow" -> ThemeYellow
            "cyan" -> ThemeCyan
            "emerald" -> ThemeEmerald
            "amber" -> ThemeAmber
            "ruby" -> ThemeRuby
            "amethyst" -> ThemeAmethyst
            "sapphire" -> ThemeSapphire
            else -> ThemeYellow
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

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalBackgroundColor provides ThemeManager.currentBackgroundColor,
        LocalFocusColor provides ThemeManager.currentFocusColor,
        LocalSurfaceColor provides ThemeManager.currentSurfaceColor,
        LocalSurfaceVariantColor provides ThemeManager.currentSurfaceVariantColor
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme,
            content = content
        )
    }
}
