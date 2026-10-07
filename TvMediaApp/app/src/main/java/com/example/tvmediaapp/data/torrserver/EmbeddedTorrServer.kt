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
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libtorrserver.so")
        if (nativeLib.exists() && nativeLib.length() > 1_000_000L) {
            return nativeLib
        }

        // Fallback: Check if extracted to filesDir
        val extractedLib = File(context.filesDir, "libtorrserver.so")
        if (extractedLib.exists() && extractedLib.length() > 1_000_000L) {
            return extractedLib
        }

        // Fallback: extract from APK sourceDir directly if Android didn't extract native libs
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (apkFile.exists()) {
                java.util.zip.ZipFile(apkFile).use { zip ->
                    val supportedAbis = android.os.Build.SUPPORTED_ABIS
                    var candidateEntry: java.util.zip.ZipEntry? = null
                    for (abi in supportedAbis) {
                        val entry = zip.getEntry("lib/$abi/libtorrserver.so")
                        if (entry != null) {
                            candidateEntry = entry
                            break
                        }
                    }
                    if (candidateEntry == null) {
                        candidateEntry = zip.getEntry("lib/armeabi-v7a/libtorrserver.so")
                            ?: zip.getEntry("lib/arm64-v8a/libtorrserver.so")
                    }

                    if (candidateEntry != null) {
                        val tempPart = File(context.filesDir, "libtorrserver.so.tmp")
                        zip.getInputStream(candidateEntry).use { input ->
                            java.io.FileOutputStream(tempPart).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempPart.length() > 1_000_000L) {
                            tempPart.renameTo(extractedLib)
                            extractedLib.setExecutable(true, false)
                            extractedLib.setReadable(true, false)
                            Log.i(TAG, "Successfully extracted libtorrserver.so (${extractedLib.length()} bytes) from APK")
                            return extractedLib
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback APK extraction failed: ${e.message}")
        }

        return nativeLib
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
            // Correct TorrServer arguments:
            // -p: web server port (default 8090)
            // -d: database and config dir path
            // -k: don't kill server on signal
            // (Note: -c is NOT a valid TorrServer flag; using it caused immediate crash exit code 2)
            val pb = ProcessBuilder(
                binary.absolutePath,
                "-p", PORT.toString(),
                "-d", dataDir.absolutePath,
                "-k"
            )
            pb.directory(dataDir)
            pb.redirectErrorStream(true)

            // Setup environment
            val env = pb.environment()
            env["HOME"] = dataDir.absolutePath
            env["TMPDIR"] = cacheDir.absolutePath
            env["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir

            val proc = pb.start()
            serverProcess = proc

            val capturedLogs = mutableListOf<String>()

            // Drain output stream in background so Go binary doesn't hang on full pipe
            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            if (capturedLogs.size < 50) capturedLogs.add(it)
                            Log.d(TAG, "[TorrServer] $it")
                        }
                    }
                } catch (_: Exception) {}
            }

            // Wait for /echo readiness (up to 15 seconds for slow TV hardware and db initialization)
            for (i in 1..50) {
                delay(300)
                if (TorrServerManager.checkIsAlive(ECHO_URL)) {
                    Log.i(TAG, "Embedded TorrServer is online and responding to /echo!")
                    return@withContext true
                }
                // Check if process crashed early
                try {
                    val exitVal = proc.exitValue()
                    Log.e(TAG, "Embedded TorrServer process died immediately with code: $exitVal. Logs: ${capturedLogs.joinToString(" | ")}")
                    return@withContext false
                } catch (_: IllegalThreadStateException) {
                    // Process still running, continue waiting
                }
            }

            val alive = TorrServerManager.checkIsAlive(ECHO_URL)
            Log.i(TAG, "TorrServer check completed. Alive: $alive")
            return@withContext alive
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
