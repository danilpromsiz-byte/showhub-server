package com.example.tvmediaapp.data.torrserver

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object EmbeddedTorrServer {
    private const val TAG = "EmbeddedTorrServer"
    private const val PORT = 8090
    private const val ECHO_URL = "http://127.0.0.1:$PORT"

    @Volatile
    private var serverProcess: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            Runtime.getRuntime().addShutdownHook(Thread {
                stop()
            })
        } catch (_: Exception) {}
    }

    fun isBinaryAvailable(context: Context): Boolean {
        val lib = getBinaryFile(context)
        return lib.exists() && lib.length() > 1_000_000L
    }

    private fun getBinaryFile(context: Context): File {
        return File(context.applicationInfo.nativeLibraryDir, "libtorrserver.so")
    }

    suspend fun ensureRunning(context: Context): Boolean = withContext(Dispatchers.IO) {
        // 1. If already alive, nothing to do
        if (TorrServerManager.checkIsAlive(ECHO_URL)) {
            Log.d(TAG, "TorrServer is already running on port $PORT")
            return@withContext true
        }

        val binary = getBinaryFile(context)
        if (!binary.exists() || binary.length() < 1_000_000L) {
            Log.w(TAG, "libtorrserver.so not found in ${binary.absolutePath}")
            return@withContext false
        }

        try {
            binary.setExecutable(true, false)
            binary.setReadable(true, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set execute permissions on ${binary.absolutePath}: ${e.message}")
        }

        val dataDir = File(context.filesDir, "torrserver_data").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "torrserver_cache").apply { mkdirs() }

        try {
            Log.i(TAG, "Starting embedded TorrServer from ${binary.absolutePath}...")
            val pb = ProcessBuilder(
                binary.absolutePath,
                "-p", PORT.toString(),
                "-d", dataDir.absolutePath,
                "-c", cacheDir.absolutePath
            )
            pb.directory(dataDir)
            pb.redirectErrorStream(true)

            // Setup environment
            val env = pb.environment()
            env["HOME"] = dataDir.absolutePath
            env["TMPDIR"] = cacheDir.absolutePath

            val proc = pb.start()
            serverProcess = proc

            // Drain output stream in background so Go binary doesn't hang on full pipe
            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "[TorrServer] $line")
                    }
                } catch (_: Exception) {}
            }

            // Wait for /echo readiness (up to 4.5 seconds)
            for (i in 1..30) {
                delay(150)
                if (TorrServerManager.checkIsAlive(ECHO_URL)) {
                    Log.i(TAG, "Embedded TorrServer is online and responding to /echo!")
                    return@withContext true
                }
                // Check if process crashed early
                try {
                    val exitVal = proc.exitValue()
                    Log.e(TAG, "Embedded TorrServer process died immediately with code: $exitVal")
                    return@withContext false
                } catch (_: IllegalThreadStateException) {
                    // Process still running, continue waiting
                }
            }

            Log.w(TAG, "TorrServer started but timed out waiting for /echo")
            return@withContext TorrServerManager.checkIsAlive(ECHO_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting embedded TorrServer", e)
            false
        }
    }

    fun stop() {
        try {
            serverProcess?.destroy()
            serverProcess = null
            Log.i(TAG, "Embedded TorrServer stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TorrServer: ${e.message}")
        }
    }
}
