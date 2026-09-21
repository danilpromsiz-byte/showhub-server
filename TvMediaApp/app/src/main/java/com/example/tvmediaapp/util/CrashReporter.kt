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
    private const val UPSTASH_REST_URL = "https://pleased-goose-289810.upstash.io"
    private const val UPSTASH_TOKEN = "gQAAAAAABGwSAAIgcDE3YzQ5ODk0NzNhMmI0ZjUwOTdhMDgwMjcxYWEzY2Q3Yg"

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false
    @Volatile
    var lastScreen: String = "INITIAL"

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
                sendThread.join(1500)
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
            put("last_screen", lastScreen)
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
        var upstashSuccess = false
        var serverSuccess = false

        // 1. Direct send to Upstash Redis REST API (guaranteed fast serverless ingestion)
        try {
            val commandArr = org.json.JSONArray().apply {
                put("LPUSH")
                put("showhub:crashes")
                put(json.toString())
            }
            val url = URL(UPSTASH_REST_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Authorization", "Bearer $UPSTASH_TOKEN")

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(commandArr.toString())
                it.flush()
            }
            if (conn.responseCode in 200..299) {
                upstashSuccess = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Upstash crash send failed: ${e.message}")
        }

        // 2. Also send to main server backend endpoint as secondary channel
        try {
            val url = URL("$SERVER_BASE/api/analytics/crash")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "ShowHubTV-CrashReporter/${com.example.tvmediaapp.BuildConfig.VERSION_NAME}")

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(json.toString())
                it.flush()
            }
            if (conn.responseCode in 200..299) {
                serverSuccess = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server crash send failed: ${e.message}")
        }

        return upstashSuccess || serverSuccess
    }
}
