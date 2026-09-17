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

    suspend fun downloadAndInstall(activity: Activity, apkUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check unknown sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        val manageIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName)
                        )
                        activity.startActivity(manageIntent)
                    }
                    return@withContext false
                }
            }

            val url = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "ShowHubTV-Native/2.3.0")
            conn.connect()

            val cacheDir = activity.externalCacheDir ?: activity.cacheDir
            val apkFile = File(cacheDir, "ShowHub-update.apk")

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } > 0) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val apkUri = FileProvider.getUriForFile(
                        activity,
                        activity.packageName + ".provider",
                        apkFile
                    )
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
