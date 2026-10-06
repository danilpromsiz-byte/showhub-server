package com.example.tvmediaapp.data.torrserver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object TorrServerManager {
    const val DEFAULT_TORRSERVE_HOST = "http://127.0.0.1:8090"
    const val TORRSERVE_APK_URL = "https://github.com/YouROK/TorrServe/releases/download/MatriX.143.Client/TorrServe_MatriX.143.Client.apk"
    private val TORRSERVE_PACKAGES = listOf(
        "ru.yourok.torrserve",
        "ru.yourok.torrserver",
        "is.xyz.torrserve"
    )

    fun getTorrHost(context: Context): String {
        val prefs = context.getSharedPreferences("showhub_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pref_torrserve_host", DEFAULT_TORRSERVE_HOST) ?: DEFAULT_TORRSERVE_HOST
    }

    suspend fun checkIsAlive(host: String = DEFAULT_TORRSERVE_HOST): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanHost = host.trimEnd('/')
            val url = URL("$cleanHost/echo")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 800
            conn.readTimeout = 800
            conn.requestMethod = "GET"
            conn.connect()
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    fun isTorrServerInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in TORRSERVE_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun startTorrServerApp(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in TORRSERVE_PACKAGES) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun playInExternalPlayer(context: Context, magnetOrStreamUrl: String): Boolean {
        return try {
            val uri = Uri.parse(magnetOrStreamUrl)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (magnetOrStreamUrl.startsWith("magnet:")) {
                    setDataAndType(uri, "application/x-bittorrent")
                } else {
                    setDataAndType(uri, "video/*")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                // Fallback without MIME type
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetOrStreamUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun downloadAndInstallApk(
        activity: Activity,
        onProgress: (status: String, percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = activity.externalCacheDir ?: activity.cacheDir
        val apkFile = File(cacheDir, "TorrServe_MatriX.apk")
        val partFile = File(cacheDir, "TorrServe_MatriX.apk.part")

        if (partFile.exists()) {
            try { partFile.delete() } catch (_: Exception) {}
        }

        try {
            withContext(Dispatchers.Main) {
                onProgress("Подключение к серверу загрузки...", 0)
            }

            val candidateUrls = listOf(
                TORRSERVE_APK_URL,
                "https://raw.githubusercontent.com/danilpromsiz-byte/showhub-server/main/mediacenter/static/TorrServe_MatriX.apk"
            )

            var downloadSuccess = false
            for (candidateUrl in candidateUrls) {
                try {
                    var currentUrl = candidateUrl
                    var conn: HttpURLConnection? = null
                    var redirects = 0
                    while (redirects < 5) {
                        val url = URL(currentUrl)
                        val c = url.openConnection() as HttpURLConnection
                        c.connectTimeout = 15000
                        c.readTimeout = 40000
                        c.instanceFollowRedirects = true
                        c.setRequestProperty("User-Agent", "Mozilla/5.0")
                        c.connect()
                        val code = c.responseCode
                        if (code in listOf(301, 302, 303, 307, 308)) {
                            val loc = c.getHeaderField("Location")
                            if (!loc.isNullOrBlank()) {
                                currentUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                                c.disconnect()
                                redirects++
                                continue
                            }
                        }
                        conn = c
                        break
                    }

                    if (conn == null || conn.responseCode !in 200..299) {
                        conn?.disconnect()
                        continue
                    }

                    val contentLength = conn.contentLength.toLong()
                    var bytesTotal = 0L
                    var lastReport = 0L

                    conn.inputStream.use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buf = ByteArray(65536)
                            var read: Int
                            while (input.read(buf).also { read = it } > 0) {
                                output.write(buf, 0, read)
                                bytesTotal += read
                                val now = System.currentTimeMillis()
                                if (now - lastReport > 200L) {
                                    lastReport = now
                                    val percent = if (contentLength > 0) ((bytesTotal * 100) / contentLength).toInt() else -1
                                    val mbRead = String.format(java.util.Locale.US, "%.1f", bytesTotal / (1024.0 * 1024.0))
                                    val mbTotal = String.format(java.util.Locale.US, "%.1f", contentLength / (1024.0 * 1024.0))
                                    val msg = if (percent >= 0) "Загрузка: $percent% ($mbRead / $mbTotal МБ)" else "Загружено: $mbRead МБ"
                                    withContext(Dispatchers.Main) {
                                        onProgress(msg, percent)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (partFile.length() > 5_000_000L) {
                        downloadSuccess = true
                        break
                    }
                } catch (_: Exception) {
                    try { partFile.delete() } catch (_: Exception) {}
                }
            }

            if (!downloadSuccess || !partFile.exists()) {
                withContext(Dispatchers.Main) {
                    onProgress("Ошибка загрузки TorrServer APK", -1)
                }
                return@withContext false
            }

            if (apkFile.exists()) {
                try { apkFile.delete() } catch (_: Exception) {}
            }
            partFile.renameTo(apkFile)
            apkFile.setReadable(true, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        onProgress("Разрешите установку неизвестных приложений в Настройках TV", -1)
                        try {
                            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${activity.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(settingsIntent)
                        } catch (_: Exception) {}
                    }
                    return@withContext false
                }
            }

            withContext(Dispatchers.Main) {
                onProgress("Запуск установщика...", 100)
                val apkUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.provider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val resList = activity.packageManager.queryIntentActivities(installIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resList) {
                    val pkgName = resolveInfo.activityInfo.packageName
                    activity.grantUriPermission(pkgName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(installIntent)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onProgress("Ошибка: ${e.localizedMessage}", -1)
            }
            false
        }
    }
}
