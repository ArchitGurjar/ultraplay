package com.ultrastream.app

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(context, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(context: Context, throwable: Throwable) {
        try {
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            val stackTrace = stringWriter.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logContent = """
                ========================================
                CRASH LOG - $timestamp
                App Version: ${getAppVersion(context)}
                Exception: ${throwable.localizedMessage}
                ========================================
                
                $stackTrace
                ========================================
            """.trimIndent()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val crashFile = File(downloadsDir, "ultraplay_crash.txt")
            val writer = FileWriter(crashFile, false)
            writer.write(logContent)
            writer.flush()
            writer.close()

            Log.e("CrashLogger", "Crash log written to ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashLogger", "Failed to write crash log", e)
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
