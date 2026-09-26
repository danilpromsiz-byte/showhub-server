package com.example.tvmediaapp.data.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    val changelog: String,
    val isForceUpdate: Boolean = false
)

object UpdateManager {
    @Volatile
    var isPredownloading: Boolean = false
        private set

    fun isApkReady(context: Context, targetVersionCode: Int): Boolean {
        return try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkFile = File(cacheDir, if (targetVersionCode > 0) "ShowHub-update-v$targetVersionCode.apk" else "ShowHub-update.apk")
            if (!apkFile.exists() || apkFile.length() < 5_000_000L) return false
            val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: return false
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archiveInfo.longVersionCode.toInt() else archiveInfo.versionCode
            if (targetVersionCode > 0) code >= targetVersionCode else code > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun predownloadUpdate(context: Context, info: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        if (isPredownloading) return@withContext false
        if (isApkReady(context, info.versionCode)) return@withContext true
        isPredownloading = true
        try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val targetFile = File(cacheDir, if (info.versionCode > 0) "ShowHub-update-v${info.versionCode}.apk" else "ShowHub-update.apk")
            val partFile = File(cacheDir, if (info.versionCode > 0) "ShowHub-update-v${info.versionCode}.apk.part" else "ShowHub-update.apk.part")

            // Clean older updates
            try {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("ShowHub-update") && file.name != targetFile.name && file.name != partFile.name) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}

            val candidateUrls = listOf(
                info.downloadUrl,
                "https://cdn.jsdelivr.net/gh/danilpromsiz-byte/showhub-server@main/mediacenter/static/ShowHub.apk",
                "https://raw.githubusercontent.com/danilpromsiz-byte/showhub-server/main/mediacenter/static/ShowHub.apk",
                "https://showhub-server.onrender.com/ShowHub.apk"
            ).distinct()

            for (currentUrl in candidateUrls) {
                if (partFile.exists()) {
                    try { partFile.delete() } catch (_: Exception) {}
                }
                try {
                    val url = URL(currentUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000
                    conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.8.18")
                    conn.connect()
                    if (conn.responseCode !in 200..299) continue

                    conn.inputStream.use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(32768)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } > 0) {
                                output.write(buffer, 0, bytesRead)
                            }
                            output.flush()
                        }
                    }

                    if (partFile.length() < 1_000_000L) {
                        try { partFile.delete() } catch (_: Exception) {}
                        continue
                    }

                    val archiveInfo = context.packageManager.getPackageArchiveInfo(partFile.absolutePath, 0)
                    val downloadedVersion = if (archiveInfo != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archiveInfo.longVersionCode.toInt() else archiveInfo.versionCode
                    } else 0

                    if (info.versionCode > 0 && downloadedVersion < info.versionCode) {
                        try { partFile.delete() } catch (_: Exception) {}
                        continue
                    }

                    if (targetFile.exists()) {
                        try { targetFile.delete() } catch (_: Exception) {}
                    }
                    if (partFile.renameTo(targetFile)) {
                        targetFile.setReadable(true, false)
                        return@withContext true
                    }
                } catch (_: Exception) {
                    try { partFile.delete() } catch (_: Exception) {}
                }
            }
            false
        } finally {
            isPredownloading = false
        }
    }

    private val VERSION_URLS = listOf(
        "https://showhub-server.onrender.com/version.json",
        "https://raw.githubusercontent.com/danilpromsiz-byte/showhub-server/main/version.json",
        "https://cdn.jsdelivr.net/gh/danilpromsiz-byte/showhub-server@main/version.json"
    )

    suspend fun checkUpdate(currentVersionCode: Int): UpdateInfo = withContext(Dispatchers.IO) {
        var fallbackInfo: UpdateInfo? = null
        for (baseUrl in VERSION_URLS) {
            for (attempt in 1..2) {
                try {
                    val fullUrl = if (baseUrl.contains("?")) "$baseUrl&t=${System.currentTimeMillis()}" else "$baseUrl?t=${System.currentTimeMillis()}"
                    val url = URL(fullUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.useCaches = false
                    conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.8.18")
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    conn.setRequestProperty("Pragma", "no-cache")
                    conn.connect()
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val sCode = json.optInt("version_code", 0)
                        val sName = json.optString("version_name", "2.8.18")
                        val sUrl = json.optString("download_url", json.optString("apk_url", "https://showhub-server.onrender.com/ShowHub.apk"))
                        val sChangelog = json.optString("changelog", "Новая версия ShowHub TV")
                        val forceUpdateFlag = json.optBoolean("force_update", false)
                        val minVersionCode = json.optInt("min_version_code", 0)
                        val hasUpdate = sCode > currentVersionCode
                        val isForce = hasUpdate && (forceUpdateFlag || currentVersionCode < minVersionCode)
                        val info = UpdateInfo(hasUpdate, sName, sCode, sUrl, sChangelog, isForce)
                        if (hasUpdate) {
                            return@withContext info
                        }
                        if (fallbackInfo == null || sCode > fallbackInfo.versionCode) {
                            fallbackInfo = info
                        }
                        break
                    }
                } catch (e: Exception) {
                    if (attempt < 2) delay(500)
                }
            }
        }
        fallbackInfo ?: UpdateInfo(false, "", 0, "", "", false)
    }

    suspend fun downloadAndInstall(
        activity: Activity,
        apkUrl: String,
        targetVersionCode: Int = 0,
        onProgress: ((status: String, percent: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isPredownloading) {
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Завершение фоновой загрузки...", -1)
                }
                for (i in 1..40) {
                    if (!isPredownloading || isApkReady(activity, targetVersionCode)) break
                    delay(500)
                }
            }

            withContext(Dispatchers.Main) {
                onProgress?.invoke("Подключение к серверу...", 0)
            }

            val cacheDir = activity.externalCacheDir ?: activity.cacheDir
            val apkFile = File(cacheDir, if (targetVersionCode > 0) "ShowHub-update-v$targetVersionCode.apk" else "ShowHub-update.apk")

            // Clean up any stale update files from older versions to prevent storage bloat and version confusion
            try {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("ShowHub-update") && file.name != apkFile.name) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}

            // Check if existing file is truly the valid target version
            val isCachedValid = if (apkFile.exists() && apkFile.length() > 5_000_000L) {
                val archiveInfo = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                val code = if (archiveInfo != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archiveInfo.longVersionCode.toInt() else archiveInfo.versionCode
                } else 0
                if (targetVersionCode > 0) code == targetVersionCode else code > 0
            } else {
                false
            }

            if (isCachedValid) {
                withContext(Dispatchers.Main) {
                    val mb = String.format(java.util.Locale.US, "%.1f", apkFile.length() / (1024.0 * 1024.0))
                    onProgress?.invoke("Файл обновления готов ($mb МБ)", 100)
                }
            } else {
            val candidateUrls = listOf(
                apkUrl,
                "https://cdn.jsdelivr.net/gh/danilpromsiz-byte/showhub-server@main/mediacenter/static/ShowHub.apk",
                "https://raw.githubusercontent.com/danilpromsiz-byte/showhub-server/main/mediacenter/static/ShowHub.apk",
                "https://showhub-server.onrender.com/ShowHub.apk"
            ).distinct()

            var downloadSuccess = false
            for (currentUrl in candidateUrls) {
                if (isCachedValid) {
                    downloadSuccess = true
                    break
                }

                if (apkFile.exists()) {
                    try { apkFile.delete() } catch (_: Exception) {}
                }

                try {
                    val url = URL(currentUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.8.18")
                    conn.connect()

                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        continue
                    }

                    val contentLength = conn.contentLength.toLong()
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

                    apkFile.setReadable(true, false)

                    if (apkFile.length() < 1_000_000L) {
                        try { apkFile.delete() } catch (_: Exception) {}
                        continue
                    }

                    val archiveInfo = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    val downloadedVersion = if (archiveInfo != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archiveInfo.longVersionCode.toInt() else archiveInfo.versionCode
                    } else 0

                    if (targetVersionCode > 0 && downloadedVersion < targetVersionCode) {
                        try { apkFile.delete() } catch (_: Exception) {}
                        continue // Try next mirror!
                    }

                    downloadSuccess = true
                    break
                } catch (_: Exception) {
                    try { apkFile.delete() } catch (_: Exception) {}
                }
            }

            if (!downloadSuccess || !apkFile.exists() || apkFile.length() < 1_000_000L) {
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Не удалось скачать актуальную сборку. Открываем браузер...", -1)
                    openDownloadUrlInBrowser(activity, apkUrl)
                }
                return@withContext false
            }
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
                launchInstallerIntent(activity, apkFile, apkUrl, onProgress)
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

    fun launchInstallerIntent(
        activity: Activity,
        apkFile: File,
        apkUrl: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): Boolean {
        return try {
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
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val resList = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resList) {
                val pkgName = resolveInfo.activityInfo.packageName
                activity.grantUriPermission(pkgName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(intent)
            onProgress?.invoke("Установщик запущен! Нажмите «Установить» на экране TV.", 100)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress?.invoke("Сбой установщика: ${e.message}. Открываем в браузере...", -1)
            openDownloadUrlInBrowser(activity, apkUrl)
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
