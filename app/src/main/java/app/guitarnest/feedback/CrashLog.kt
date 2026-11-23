package app.guitarnest.feedback

import android.content.Context
import android.os.Build
import app.guitarnest.BuildConfig
import java.io.File

/**
 * Keeps the last crash on this phone only, so the player can choose to report it on the next start.
 * Nothing is sent automatically.
 */
object CrashLog {
    private fun file(context: Context) = File(context.noBackupFilesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(
                    "GuitarNest ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}\n" +
                        error.stackTraceToString().lines().take(40).joinToString("\n"),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    /** App version and device, shown to the player before it is added to a report. */
    fun deviceText(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"

    /** For automatic crash reports: the system version only, no phone model. */
    fun systemText(): String = "Android ${Build.VERSION.RELEASE}"
}
