package com.example.tvmediaapp.data.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String
)

object UpdateManager {
    private const val VERSION_URL = "https://showhub-server.onrender.com/version.json"

    suspend fun checkUpdate(currentVersionCode: Int): UpdateInfo = withContext(Dispatchers.IO) {
        for (attempt in 1..3) {
            try {
                val url = URL(VERSION_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.3.0")
                conn.connect()
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val sCode = json.optInt("version_code", 0)
                    val sName = json.optString("version_name", "2.3.0")
                    val sUrl = json.optString("download_url", json.optString("apk_url", "https://showhub-server.onrender.com/ShowHub.apk"))
                    val sChangelog = json.optString("changelog", "\u041d\u043e\u0432\u0430\u044f \u0432\u0435\u0440\u0441\u0438\u044f ShowHub TV")
                    val hasUpdate = sCode > currentVersionCode
                    return@withContext UpdateInfo(hasUpdate, sName, sCode, sUrl, sChangelog)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < 3) delay(1500)
            }
        }
        UpdateInfo(false, "", 0, "", "")
    }

    suspend fun downloadAndInstall(
        activity: Activity,
        apkUrl: String,
        onProgress: ((status: String, percent: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                onProgress?.invoke("Подключение к серверу...", 0)
            }

            val url = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.6.0")
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Ошибка сервера ($responseCode)", -1)
                }
                return@withContext false
            }

            val cacheDir = activity.externalCacheDir ?: activity.cacheDir
            val apkFile = File(cacheDir, "ShowHub-update.apk")

            // If a valid APK was already downloaded in the last 20 minutes (> 5 MB), reuse it directly
            val isCachedValid = apkFile.exists() &&
                apkFile.length() > 5_000_000L &&
                (System.currentTimeMillis() - apkFile.lastModified() < 20 * 60 * 1000L)

            if (isCachedValid) {
                withContext(Dispatchers.Main) {
                    val mb = String.format(java.util.Locale.US, "%.1f", apkFile.length() / (1024.0 * 1024.0))
                    onProgress?.invoke("Файл обновления готов ($mb МБ)", 100)
                }
            } else {
                val contentLength = conn.contentLength.toLong()
                if (apkFile.exists()) {
                    try { apkFile.delete() } catch (_: Exception) {}
                }

                var bytesReadTotal = 0L
                var lastReportTime = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } > 0) {
                            output.write(buffer, 0, bytesRead)
                            bytesReadTotal += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 200 || bytesReadTotal == contentLength) {
                                lastReportTime = now
                                val percent = if (contentLength > 0) {
                                    ((bytesReadTotal * 100) / contentLength).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }
                                val mbRead = String.format(java.util.Locale.US, "%.1f", bytesReadTotal / (1024.0 * 1024.0))
                                val mbTotal = if (contentLength > 0) {
                                    String.format(java.util.Locale.US, "%.1f", contentLength / (1024.0 * 1024.0))
                                } else {
                                    "?"
                                }
                                val statusMsg = if (percent >= 0) {
                                    "Загрузка: $percent% ($mbRead / $mbTotal МБ)"
                                } else {
                                    "Загружено: $mbRead МБ"
                                }
                                withContext(Dispatchers.Main) {
                                    onProgress?.invoke(statusMsg, percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            apkFile.setReadable(true, false)

            if (apkFile.length() < 1_000_000L) {
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Файл обновления поврежден (${apkFile.length()} байт). Попробуйте снова.", -1)
                }
                return@withContext false
            }

            // Check Unknown App Sources permission on Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke("Разрешите установку для ShowHub в Настройках TV и нажмите «Обновить» снова", -1)
                        try {
                            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${activity.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(settingsIntent)
                        } catch (_: Exception) {
                            try {
                                activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        }
                    }
                    return@withContext false
                }
            }

            withContext(Dispatchers.Main) {
                onProgress?.invoke("Запуск установщика пакетов Android...", 100)
                try {
                    val apkUri = FileProvider.getUriForFile(
                        activity,
                        activity.packageName + ".provider",
                        apkFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                    onProgress?.invoke("Установщик запущен! Нажмите «Установить» на экране TV.", 100)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onProgress?.invoke("Сбой установщика: ${e.message}. Открываем в браузере...", -1)
                    openDownloadUrlInBrowser(activity, apkUrl)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onProgress?.invoke("Сбой загрузки: ${e.message ?: "таймаут сети"}", -1)
            }
            false
        }
    }

    fun openDownloadUrlInBrowser(activity: Activity, apkUrl: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(browserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
