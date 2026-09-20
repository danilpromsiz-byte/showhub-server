package com.example.tvmediaapp.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val CRASH_FILE = "crash_dump.json"
    private const val SERVER_BASE = "https://showhub-server.onrender.com"

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 1. Send any pending crash reports from previous crashes
        CoroutineScope(Dispatchers.IO).launch {
            transmitPendingCrash(appContext)
        }

        // 2. Install uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
                val crashJson = buildCrashJson(thread, throwable)
                saveCrashToDisk(appContext, crashJson)

                // Try quick fire-and-forget send before death
                val sendThread = Thread {
                    sendCrashToServer(crashJson)
                }
                sendThread.start()
                sendThread.join(1200)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling uncaught exception", e)
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun buildCrashJson(thread: Thread, throwable: Throwable): JSONObject {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("date", dateFormat.format(Date()))
            put("thread", thread.name)
            put("exception_class", throwable.javaClass.name)
            put("message", throwable.message ?: "No message")
            put("stacktrace", Log.getStackTraceString(throwable))
            put("device_manufacturer", Build.MANUFACTURER)
            put("device_model", Build.MODEL)
            put("device_product", Build.PRODUCT)
            put("android_release", Build.VERSION.RELEASE)
            put("android_sdk", Build.VERSION.SDK_INT)
            put("app_version_name", com.example.tvmediaapp.BuildConfig.VERSION_NAME)
            put("app_version_code", com.example.tvmediaapp.BuildConfig.VERSION_CODE)
        }
    }

    private fun saveCrashToDisk(context: Context, json: JSONObject) {
        try {
            val file = File(context.filesDir, CRASH_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash dump to disk", e)
        }
    }

    private fun transmitPendingCrash(context: Context) {
        try {
            val file = File(context.filesDir, CRASH_FILE)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val json = JSONObject(content)
                    val success = sendCrashToServer(json)
                    if (success) {
                        file.delete()
                        Log.i(TAG, "Successfully reported pending crash and deleted local dump")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transmit pending crash", e)
        }
    }

    private fun sendCrashToServer(json: JSONObject): Boolean {
        return try {
            val url = URL("$SERVER_BASE/api/analytics/crash")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "ShowHubTV-CrashReporter/2.7.2")

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(json.toString())
                it.flush()
            }

            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Crash send failed: ${e.message}")
            false
        }
    }
}
